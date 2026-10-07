#!/usr/bin/env python3
"""Pride Canvas UI sounds, synthesized from scratch (ours, no licence strings attached).
Run: python3 tools/make_ui_sounds.py  -> src/main/resources/assets/dpcanvas/sounds/ui/*.ogg"""
import numpy as np, subprocess, os, tempfile, wave
SR = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/dpcanvas/sounds/ui')

def t(sec): return np.arange(int(SR * sec)) / SR

def bell(freq, dur, decay, partials=((1, 1), (2.0, .35), (3.01, .12), (4.2, .05)), attack=.004):
    x = t(dur); s = np.zeros_like(x)
    for mult, amp in partials:
        s += amp * np.sin(2 * np.pi * freq * mult * x) * np.exp(-x / (decay / mult ** .5))
    env = np.minimum(1, x / attack)
    return s * env

def place(buf, sig, at):
    i = int(at * SR); buf[i:i + len(sig)] += sig[:max(0, len(buf) - i)]

def noise_sweep(dur, f0, f1, amp):
    """band-passed noise whose centre glides f0 -> f1 (a soft whoosh)"""
    n = np.random.default_rng(7).standard_normal(int(SR * dur))
    spec = np.fft.rfft(n); freqs = np.fft.rfftfreq(len(n), 1 / SR)
    out = np.zeros(len(n)); chunks = 24; L = len(n) // chunks
    for c in range(chunks):
        fc = f0 * (f1 / f0) ** (c / (chunks - 1))
        g = np.exp(-((np.log(freqs + 1) - np.log(fc)) ** 2) / .35)
        seg = np.fft.irfft(spec * g, len(n))[c * L:(c + 1) * L]
        out[c * L:(c + 1) * L] = seg
    x = np.linspace(0, 1, len(out))
    return amp * out / (np.abs(out).max() + 1e-9) * np.sin(np.pi * x) ** 1.5

def write(name, s, gain_db=-3):
    s = s / (np.abs(s).max() + 1e-9) * 10 ** (gain_db / 20)
    fade = min(len(s), int(SR * .02)); s[-fade:] *= np.linspace(1, 0, fade)
    pcm = (s * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f: wav = f.name
    with wave.open(wav, 'wb') as w: w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(wav); print(name, f'{len(s) / SR:.2f}s')

N = lambda semis: 440 * 2 ** ((semis - 9) / 12 + 1)   # N(0) = C5 (523 Hz), N(12) = C6

# hover: one tiny glassy tick-chime, very short and soft
write('hover', bell(N(19), .16, .045, ((1, 1), (2.0, .2), (3.9, .06))), -9)

# click: a sparkly rising two-note ding (C6 -> G6) with a little shimmer
b = np.zeros(int(SR * .55))
place(b, bell(N(12), .5, .16), 0)
place(b, bell(N(19), .5, .2) * .9, .055)
place(b, bell(N(31), .3, .06) * .25, .06)      # sparkle an octave up
write('click', b, -4)

# back / cancel: the same, falling (G5 -> C5), softer
b = np.zeros(int(SR * .5))
place(b, bell(N(7), .45, .15), 0); place(b, bell(N(0), .45, .18) * .9, .06)
write('back', b, -6)

# open: upward whoosh + a soft major chord blooming (C5 E5 G5 C6)
b = np.zeros(int(SR * .9)); place(b, noise_sweep(.32, 400, 3200, .35), 0)
for i, s in enumerate((0, 4, 7, 12)): place(b, bell(N(s), .8, .35) * .45, .06 + i * .035)
write('open', b, -6)

# close: downward whoosh + falling two notes
b = np.zeros(int(SR * .6)); place(b, noise_sweep(.3, 2800, 350, .35), 0)
place(b, bell(N(12), .45, .2) * .4, .02); place(b, bell(N(7), .45, .22) * .4, .08)
write('close', b, -7)

# popup: a little bell triad (G5 B5 D6)
b = np.zeros(int(SR * .7))
for i, s in enumerate((7, 11, 14)): place(b, bell(N(s), .6, .22) * .6, i * .045)
write('popup', b, -5)

# confirm (the big actions: Save & Quit, Back to game): bright arpeggio up
b = np.zeros(int(SR * .8))
for i, s in enumerate((0, 4, 7, 12, 16)): place(b, bell(N(s + 12), .6, .2) * .55, i * .045)
write('confirm', b, -5)
