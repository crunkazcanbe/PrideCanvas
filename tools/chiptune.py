#!/usr/bin/env python3
"""
Pride chiptune: original 8-bit style songs for the Pride pack (her ask 2026-10-04: music "like old classic ROMs and DOS
games"). A tiny 4-channel console in numpy - two pulse leads (duty cycle + vibrato), a triangle bass, a noise drum
channel - plus an AdLib-flavoured 2-op FM pad for the DOS feel. Every song is composed here from chords + motifs,
so the music is ours and free to share.

    python3 tools/chiptune.py out_dir        -> out_dir/<name>.ogg for every song (needs ffmpeg)
"""
import os, subprocess, sys
import numpy as np

SR = 44100
NOTE = {'C': 0, 'C#': 1, 'Db': 1, 'D': 2, 'D#': 3, 'Eb': 3, 'E': 4, 'F': 5, 'F#': 6, 'Gb': 6, 'G': 7, 'G#': 8,
        'Ab': 8, 'A': 9, 'A#': 10, 'Bb': 10, 'B': 11}
SCALES = {'major': [0, 2, 4, 5, 7, 9, 11], 'minor': [0, 2, 3, 5, 7, 8, 10], 'dorian': [0, 2, 3, 5, 7, 9, 10],
          'phrygian': [0, 1, 3, 5, 7, 8, 10], 'mixolydian': [0, 2, 4, 5, 7, 9, 10]}


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


# ------------------------------------------------------------------ voices

def env(n, a=0.005, d=0.08, s=0.6, r=0.05):
    """ADSR over n samples (release inside the note)"""
    t = np.arange(n) / SR
    e = np.ones(n) * s
    an, dn, rn = int(a * SR), int(d * SR), int(r * SR)
    an = min(an, n); e[:an] = np.linspace(0, 1, an, endpoint=False) if an else e[:an]
    dn = min(dn, n - an)
    if dn > 0: e[an:an + dn] = np.linspace(1, s, dn, endpoint=False)
    rn = min(rn, n)
    if rn > 0: e[n - rn:] *= np.linspace(1, 0, rn)
    return e


def phase(f, n, vib=0.0, vib_rate=5.5, vib_delay=0.15, slide_from=None):
    t = np.arange(n) / SR
    fr = np.full(n, f, dtype=float)
    if slide_from:
        k = np.minimum(1, t / 0.04)
        fr = slide_from + (f - slide_from) * k
    if vib:
        depth = np.clip((t - vib_delay) / 0.2, 0, 1) * vib
        fr = fr * (2 ** (depth * np.sin(2 * np.pi * vib_rate * t) / 12))
    return np.cumsum(fr) / SR


def pulse(f, n, duty=0.5, **kw):
    ph = phase(f, n, **kw) % 1.0
    w = np.where(ph < duty, 1.0, -1.0)
    return w - w.mean() * 0          # keep the NES-ish hard edge


def triangle(f, n, **kw):
    ph = phase(f, n, **kw) % 1.0
    tri = 4 * np.abs(ph - 0.5) - 1
    return np.round(tri * 8) / 8   # 4-bit stepped triangle like the NES


def fm(f, n, ratio=2.0, index=1.6, decay=1.2):
    """AdLib-ish 2-operator FM"""
    t = np.arange(n) / SR
    mod = np.sin(2 * np.pi * f * ratio * t) * index * np.exp(-t * decay)
    return np.sin(2 * np.pi * f * t + mod)


_noise_lfsr = None


def noise(n, short=False):
    """NES LFSR noise (long or metallic short mode), sample-and-hold"""
    reg, out = 1, np.empty(n)
    step = 6 if short else 1
    hold = 3
    v = 1.0
    for i in range(0, n, hold):
        bit = ((reg >> 0) ^ (reg >> step)) & 1
        reg = (reg >> 1) | (bit << 14)
        v = 1.0 if reg & 1 else -1.0
        out[i:i + hold] = v
    return out


DRUMS = {}


def drum(kind):
    if kind in DRUMS: return DRUMS[kind]
    if kind == 'k':      # kick: pitch-dropping triangle + click
        n = int(0.16 * SR); t = np.arange(n) / SR
        f = 150 * np.exp(-t * 28) + 45
        w = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 18)
        w[:200] += noise(200) * 0.4
    elif kind == 's':    # snare: noise burst + body
        n = int(0.14 * SR); t = np.arange(n) / SR
        w = noise(n) * np.exp(-t * 22) * 0.8 + np.sin(2 * np.pi * 190 * t) * np.exp(-t * 30) * 0.4
    elif kind == 'h':    # hat: short metallic noise
        n = int(0.04 * SR); t = np.arange(n) / SR
        w = noise(n, short=True) * np.exp(-t * 110) * 0.28
    else:                # open hat / crash
        n = int(0.35 * SR); t = np.arange(n) / SR
        w = noise(n, short=True) * np.exp(-t * 9) * 0.45
    DRUMS[kind] = w
    return w


# ------------------------------------------------------------------ composition helpers

class Song:
    def __init__(self, name, key, scale, bpm, prog, bars_per_chord=1, swing=0.0):
        self.name, self.bpm, self.scale = name, bpm, SCALES[scale]
        self.root = 48 + NOTE[key]
        self.prog = prog                       # chord degrees, 1-based (e.g. [1,5,6,4])
        self.bpc = bars_per_chord
        self.step = 60 / bpm / 4               # one 16th note
        self.swing = swing
        self.tracks = {}                       # channel -> list of (start16, len16, midi, extra)

    def deg(self, d, octave=0):
        """scale degree (1-based, may exceed 7) -> midi"""
        d0 = d - 1
        return self.root + 12 * (octave + d0 // 7) + self.scale[d0 % 7]

    def chord(self, deg, octave=0):
        return [self.deg(deg + i, octave) for i in (0, 2, 4)]

    def add(self, ch, start, length, midi, **extra):
        self.tracks.setdefault(ch, []).append((start, length, midi, extra))


def compose(song, sections, seed=1):
    """sections: list of (kind, repeats) where kind in intro/a/b/break. Builds bass, arps, lead, drums."""
    rng = np.random.default_rng(seed)
    prog = song.prog
    bar = 16
    # two melodic motifs (rhythm in 16ths, scale steps relative to chord root) - A section and B section
    def motif(length_bars):
        cells = []
        pos = 0
        rhythms = [[4, 2, 2, 4, 4], [2, 2, 4, 2, 2, 4], [6, 2, 4, 4], [3, 3, 2, 4, 4], [2, 2, 2, 2, 8]]
        while pos < bar * length_bars:
            r = rhythms[rng.integers(len(rhythms))]
            for d in r:
                if pos >= bar * length_bars: break
                cells.append((pos, min(d, bar * length_bars - pos)))
                pos += d
        steps = []
        cur = int(rng.choice([0, 2, 4]))
        for _ in cells:
            cur += int(rng.choice([-2, -1, -1, 0, 1, 1, 2, 3, -3], p=[.08, .18, .1, .08, .2, .12, .12, .06, .06]))
            cur = max(-2, min(9, cur))
            steps.append(cur)
        return list(zip(cells, steps))

    motA, motB = motif(2), motif(2)
    t = 0
    for kind, reps in sections:
        for rep in range(reps):
            for ci, c in enumerate(prog):
                start = t
                length = bar * song.bpc
                tri = song.chord(c, -1)
                # ---- bass (triangle): root/fifth octave pump, walks in B
                for s in range(0, length, 4):
                    note = tri[0] if (s // 4) % 2 == 0 else (tri[2] if kind == 'b' else tri[0] + 12)
                    if kind == 'intro' and s % 8: continue
                    song.add('bass', start + s, 3 if kind != 'break' else 4, note)
                # ---- arpeggio (pulse 12.5%): fast chord arps, the C64/NES signature
                if kind in ('a', 'b', 'break'):
                    ch = song.chord(c, 1)
                    pat = [0, 1, 2, 1] if kind != 'b' else [0, 1, 2, 3]
                    for s in range(0, length, 1 if kind == 'break' else 2):
                        k = pat[(s // (1 if kind == 'break' else 2)) % 4]
                        song.add('arp', start + s, 1 if kind == 'break' else 2, (ch + [ch[0] + 12])[k])
                # ---- FM pad (DOS AdLib): whole-chord swell
                if kind in ('intro', 'b', 'break'):
                    for n in song.chord(c, 0):
                        song.add('pad', start, length, n)
                # ---- drums
                if kind != 'intro':
                    for s in range(0, length, 2):
                        beat = s % 16
                        if beat in (0, 8) or (kind == 'b' and beat == 10): song.add('drum', start + s, 1, 'k')
                        elif beat in (4, 12): song.add('drum', start + s, 1, 's')
                        else: song.add('drum', start + s, 1, 'h')
                    if ci == len(prog) - 1 and rep == reps - 1:
                        song.add('drum', start + length - 2, 1, 's'); song.add('drum', start + length - 1, 1, 's')
                t += length
            # ---- lead melody over the whole progression pass (pulse 25/50%)
            if kind in ('a', 'b'):
                mot = motA if kind == 'a' else motB
                pass_len = bar * song.bpc * len(prog)
                for off in range(0, pass_len, bar * 2):
                    chord_i = (off // (bar * song.bpc)) % len(prog)
                    base = prog[chord_i]
                    variation = 1 if (off // (bar * 2)) % 2 == 1 and kind == 'a' else 0
                    for (p, d), st in mot:
                        if variation and p >= bar and rng.random() < 0.35: st += int(rng.choice([-1, 1, 2]))
                        song.add('lead', t - pass_len + off + p, d, song.deg(base + st, 1), duty=0.25 if kind == 'a' else 0.5)
    song.total16 = t
    return song


# ------------------------------------------------------------------ render

def render(song, mix):
    n = int((song.total16 * song.step + 1.2) * SR)
    out = {ch: np.zeros(n) for ch in ('lead', 'arp', 'bass', 'pad', 'drum')}
    for ch, notes in song.tracks.items():
        for start, length, midi, ex in notes:
            st = start * song.step
            if song.swing and start % 2 == 1: st += song.step * song.swing
            s0 = int(st * SR)
            dur = length * song.step
            ln = int(dur * SR)
            if ch == 'drum':
                w = drum(midi)
                e = min(len(w), n - s0)
                out[ch][s0:s0 + e] += w[:e]
                continue
            f = hz(midi)
            if ch == 'lead':
                w = pulse(f, ln, ex.get('duty', 0.25), vib=0.25 if dur > 0.3 else 0) * env(ln, 0.004, 0.12, 0.65, 0.03)
            elif ch == 'arp':
                w = pulse(f, ln, 0.125) * env(ln, 0.002, 0.05, 0.4, 0.01)
            elif ch == 'bass':
                w = triangle(f, ln) * env(ln, 0.003, 0.05, 0.85, 0.02)
            else:
                w = fm(f, ln, ratio=1.0, index=1.2, decay=0.8) * env(ln, 0.25, 0.4, 0.5, 0.3)
            e = min(ln, n - s0)
            out[ch][s0:s0 + e] += w[:e]
    m = sum(out[c] * mix.get(c, 0.2) for c in out)
    # a little room echo (DOS games often had none; the menu songs sound nicer with a touch)
    d = int(song.step * 3 * SR)
    echo = np.zeros_like(m); echo[d:] = m[:-d] * mix.get('echo', 0.18)
    m = m + echo
    # gentle low-pass so the pulse edges aren't harsh on headphones
    k = 0.32
    y = np.empty_like(m); acc = 0.0
    for i in range(0, len(m), 1):
        acc += k * (m[i] - acc); y[i] = acc
    y /= max(1e-9, np.max(np.abs(y))) / 0.89
    # fade the tail so the loop restarts clean
    tail = int(1.0 * SR); y[-tail:] *= np.linspace(1, 0, tail)
    return y


SONGS = [
    # name,          key,  scale,       bpm, progression,   bars/chord, sections,                                         mix
    ('pride_theme',  'C',  'major',      132, [1, 5, 6, 4],  1, [('intro', 1), ('a', 2), ('b', 1), ('a', 2), ('break', 1), ('b', 1), ('a', 1)], {'lead': .30, 'arp': .12, 'bass': .34, 'pad': .10, 'drum': .26}),
    ('day_meadow',   'G',  'major',      118, [1, 4, 1, 5],  1, [('intro', 1), ('a', 2), ('b', 1), ('a', 2), ('b', 1), ('a', 1)],               {'lead': .28, 'arp': .14, 'bass': .32, 'pad': .08, 'drum': .20}),
    ('night_stars',  'A',  'minor',       84, [1, 6, 3, 7],  2, [('intro', 1), ('a', 1), ('b', 1), ('a', 1), ('break', 1), ('a', 1)],            {'lead': .22, 'arp': .12, 'bass': .30, 'pad': .22, 'drum': .10, 'echo': .3}),
    ('caves_deep',   'D',  'dorian',      92, [1, 4, 1, 5],  2, [('intro', 1), ('break', 1), ('a', 1), ('b', 1), ('break', 1), ('a', 1)],        {'lead': .20, 'arp': .16, 'bass': .34, 'pad': .20, 'drum': .12, 'echo': .35}),
    ('nether_fire',  'E',  'phrygian',   152, [1, 2, 1, 7],  1, [('intro', 1), ('a', 2), ('b', 2), ('break', 1), ('a', 2), ('b', 1)],           {'lead': .30, 'arp': .14, 'bass': .36, 'pad': .08, 'drum': .30}),
    ('end_void',     'B',  'minor',       72, [1, 6, 4, 5],  2, [('intro', 1), ('break', 1), ('a', 1), ('b', 1), ('break', 1)],                  {'lead': .20, 'arp': .18, 'bass': .28, 'pad': .26, 'drum': .08, 'echo': .4}),
    ('town_market',  'F',  'mixolydian', 126, [1, 6, 2, 5],  1, [('intro', 1), ('a', 2), ('b', 1), ('a', 2), ('b', 1), ('a', 1)],               {'lead': .28, 'arp': .12, 'bass': .32, 'pad': .10, 'drum': .24}),
]


def main(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    for i, (name, key, scale, bpm, prog, bpc, sections, mix) in enumerate(SONGS):
        s = compose(Song(name, key, scale, bpm, prog, bpc, swing=0.12 if name == 'town_market' else 0), sections, seed=7 + i * 13)
        y = render(s, mix)
        pcm = (y * 32767).astype('<i2').tobytes()
        ogg = os.path.join(out_dir, name + '.ogg')
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 's16le', '-ar', str(SR), '-ac', '1', '-i', '-',
                        '-af', 'loudnorm=I=-20:TP=-1.5:LRA=11', '-ar', str(SR), '-c:a', 'libvorbis', '-q:a', '5', ogg], input=pcm, check=True)
        print(f'{name}: {len(y) / SR:.0f}s -> {ogg}')


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'chiptune_out')
