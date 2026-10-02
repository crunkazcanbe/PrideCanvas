# PrideCanvas

Custom screens for Minecraft 1.12.2: a native boot splash and world-loading screen, a new main menu, pause menu, world and server lists, world creation, a mod browser, an in-game web browser, themed sub-menus, animations, UI sounds and music.

## About

PrideCanvas (mod id `dpcanvas`, shown in-game as "Pride UI") replaces the look of Minecraft's menus with one shared house style: a centred dark panel with a trans-flag accent bar, translucent square tiles, a moving wallpaper on the title screen, and a clear view of the game behind in-game menus. Every replaced screen keeps vanilla's own logic underneath, so options, sliders, mod-added buttons and other mods' handlers keep working.

It was built for the Pride modpack, a Minecraft 1.12.2 pack of around 750 mods, and is tuned for very large packs — the loading screen shows memory use and a live log, and several fixes target problems that show up with hundreds of mods. Nothing in it depends on the Pride pack, and every big feature can be switched off in the config, so it works in other 1.12.2 packs too.

It is client-side only.

## Features

### Loading

- **Native boot splash.** A coremod rewrites Forge's splash render loop to draw the animated wallpaper, the logo, a memory bar, a forward-only game-load bar (it never bounces or jumps to 100 %), a scrolling log of mod-loading status, and a loading ETA when the Zoomies mod is installed.
- **No white flash.** The splash background is forced dark, and on Cleanroom the game window stays hidden until the splash is ready to draw. Works alongside CustomLoadingScreen (its white clear is forced dark).
- **World loading screen.** The same wallpaper, spinner, "Loading World" / "Creating World" title, memory and load bars and live log, drawn at real screen resolution.
- **Startup questions stay visible.** When Forge asks something during loading (missing registries, yes/no, error screens), the overlay steps aside.
- **Loading music** starts from the very first loading bar.
- **Auto-load world.** Optionally go straight from loading into your last (or a named) world, once per launch; hold Shift to skip.

### Main menu

- Eight tiles in a centred panel under the logo: Singleplayer, Multiplayer, Mods, Options, Mod Browser, Browser, Config (opens OneConfig if installed) and Quit (with a confirm popup).
- Animated wallpaper with mouse parallax, drifting hearts and stars, a bobbing figure of your own skin, a logo that drops in, tiles that cascade in, and a rotating line of Pride quotes (your configured subtitle first).
- Live memory bar along the bottom of every title-screen menu.

### Pause menu

- Built on a real off-screen vanilla pause menu, so **every mod's Esc-menu button** becomes a tile here and runs that mod's own handler.
- Big "Back to Game" and "Save and Quit" / "Disconnect" bars; leaving asks to confirm (Save & Quit, Quit Game, Cancel) and shows how long you've played this session.
- Info card: your real 3D character (drag to spin, scroll to zoom), position, biome, dimension, day and time, health, session time, FPS and memory.
- Shortcut tiles for other Pride mods, shown only when they're installed (Pride Hub, Store, Commands, Land Map, Permissions, Block History, Crash Reports, Quest Book, Tails & Ears editor), plus Browser.
- The game stays visible behind it with a soft tint; the tile grid scrolls when there are many mod buttons.

### Worlds and servers

- **World list:** a scrolling card grid with world icons, game mode and last-played date; Play, New World, Edit, Delete (with confirm), Re-Create, Back.
- **Create World:** two tabs — *Basics* (name, save folder, game mode with its description) and *More Options* (seed, structures, world type, cheats, bonus chest, customize). Vanilla's rules and state drive everything; mod-added buttons get their own row.
- **Multiplayer:** server cards with icon, coloured MOTD, player count and version from a live ping; Join, Direct, Add, Edit, Delete (with confirm), Refresh, Back.

### Options and sub-menus

- **Options** as a scrollable tile grid, with extra buttons when the mods are present: **Distant Horizons** and **Celeritas** settings, and **Shaders** on/off for the AUSM shader engine (switches it for the next start by renaming its jar, since it costs FPS even with no shader pack loaded).
- **Video Settings** rebuilt as tiles and sliders.
- **Themed sub-menus** (vanilla and Forge): Controls, Sounds, Language, Snooper, Skin, Resource Packs, Chat, Open to LAN, Stats, Advancements, Mod List, mod config screens, world-generation screens, yes/no and error screens, and more. Each is laid out inside a centred box the size of the pause menu, with lists on frosted glass instead of dirt. Containers, chat, books, signs and command blocks keep their own look.
- **Menu GUI scale:** optionally use a bigger GUI scale only while a menu is open; your HUD keeps your normal scale.

### Mod Browser

- **Left half:** every mod in the folder PrideCanvas was loaded from, with search, scrollbar and keyboard scrolling. Click a row to enable/disable it (`.jar` ↔ `.jar.disabled`), click X to move it to a `.removed` folder.
- **Right half:** search Modrinth or CurseForge for 1.12.2 Forge mods (sortable by relevance, downloads, follows, newest, updated / popularity, name, featured) and click a result to download it into that folder. Uses the public Modrinth API and a keyless CurseForge mirror; no account needed.
- Closing after changes offers Restart Now (Linux; relaunches the same command line), Quit Game or Keep Editing.

### Browser

- Opens a web page inside the game when MCEF (in-game Chromium) is installed, with back, forward, reload, home and an address bar (a URL, a domain, or words to search). Without MCEF it falls back to an external browser.
- The home page is a built-in start page with useful links, copied to `config/pridecanvas/start.html` so you can edit it; or set your own home page in the config.

### Animation and sound

- Tiles lift, glow, squish and ripple; menus slide and pop in; every screen (including other mods') glides in when it opens. All optional.
- Original UI sounds (hover, click, back, open, close, popup, confirm); hover chimes climb a scale across a row and are rate-limited.
- **Music:** a loading/menu playlist that keeps playing into the main menu and fades out when a world loads, following your Master × Music volume. In game, Pride songs replace a share of vanilla's music, picked by where you are: caves, night, day, near a village, Nether, End.

### Fixes and extras

- **Resize black screen fix:** after you resize the window, the chunk renderers are rebuilt once the drag stops (what F3+A does) — fixes the world staying black with Celeritas/Sodium-style renderers and shader packs.
- **Window icon and title:** a block-P icon in trans stripes and the title "Pride".
- **PolyPatcher / OneConfig key:** a normal Minecraft key binding that runs `/patcher`, for packs where OneConfig's own hotkey doesn't fire.
- **Mine and Slash HUD position:** move Mine and Slash's level badge and bars as one block (needs MixinBooter; skipped if Mine and Slash isn't installed).
- **Prompt reporting for automation:** when a question screen is open (yes/no, errors, Forge startup questions), its text, buttons and a screenshot are written to `pride-prompts/prompt.txt`; writing a button label or index to `pride-prompts/answer` presses it.

## Commands

| Command | What it does |
|---|---|
| `/pridecanvas [screen]` | Client-side. Opens a screen the normal way so the Pride version appears. `screen` is one of `pause` (default), `options`, `video`, `controls`, `sounds`, `language`, `packs`, `skin`, `chat`, `lan`, `stats`, `mods`, `create`, `web`. Tab-completes. |

## Key bindings

| Name | Default | Category | What it does |
|---|---|---|---|
| Open PolyPatcher / OneConfig menu | Right Shift | Pride UI | Runs `/patcher` when no screen is open. Rebind in Controls. |

## Config

File: `config/dpcanvas.cfg` (Forge `@Config`; also editable from the mod list's Config button). Changing the wallpaper folder takes effect without a restart.

| Key | Default | What it does |
|---|---|---|
| `Auto-Load World On Start` | `true` | Go straight from loading into a world, once per launch. Hold Shift while loading finishes to skip. |
| `Auto-Load Which World` | *(empty)* | Folder or display name of the world. Empty = the most recently played (worlds ending in `-test` are skipped). |
| `Enable Custom Main Menu` | `true` | Replace the main menu (also Options and Video Settings). |
| `Enable Custom World List` | `true` | Replace the world-select screen. |
| `Enable Custom Multiplayer` | `true` | Replace the multiplayer screen. |
| `Custom Pause Menu` | `true` | Replace the Esc menu (every mod's button kept as a tile). |
| `Menu Title` | `Pride` | Title text for the main menu. Currently unused (the logo image is drawn instead). |
| `Menu Subtitle` | `v3` | Shown first in the rotating quote line (`v3` or empty = quotes only). |
| `Enable Custom Loading Screen` | `true` | The custom boot splash and world-loading screen. |
| `Loading Screen Text` | `Creating World` | Label for the world create/load screen. Currently unused (the label is picked from the log: "Loading World" or "Creating World"). |
| `Show Log On Loading Screen` | `false` | Meant to toggle the log on the world-load screen. Currently unused (the log is always shown). |
| `Blend All Menu Screens` | `true` | Wallpaper and Pride styling behind the other menu screens, themed lists, and the clear in-game backdrop. |
| `Sub-Menus In A Box` | `true` | Lay sub-menus out inside a centred box the size of the pause menu. |
| `Menu GUI Scale` | `-1` | GUI scale while a menu is open: `-1` off, `0` biggest that fits, `1`–`16` that scale. HUD keeps your normal scale. |
| `Website URL` | *(the Pride site)* | First link on the built-in start page. |
| `Browser Home Page` | *(empty)* | What the Browser button opens. Empty = the built-in start page (`config/pridecanvas/start.html`, editable; delete it to restore). |
| `Wallpaper Frames Folder` | *(empty)* | Absolute path to a folder of PNG frames played in name order. Empty = the built-in wallpaper. |
| `Fix Resize Black Screen` | `true` | Rebuild the chunk renderers after a window resize. |
| `Resize Settle Delay (ms)` | `300` | Wait this long after the last size change before repairing (0–3000). |
| `Animations` | `true` | Tile lift/glow/squish/ripple, menus slide and pop in. |
| `Screen Transitions` | `true` | Every screen glides in when it opens. |
| `Transitions On Inventories` | `false` | Also animate chests, inventories and machines. |
| `Sparkles` | `true` | Hearts and stars drifting behind the menus. |
| `Menu Sounds` | `true` | Click chimes and open/close whooshes. |
| `Hover Sounds` | `true` | A small chime when the mouse moves onto a button. |
| `Menu Sound Volume` | `0.55` | 0 = silent, 1 = full. |
| `Loading Music` | `true` | Play the Pride theme from the first loading bar. |
| `Menu Music` | `true` | Keep it playing on the main menu, and again after quitting to the menu. Off = it fades when the menu shows. |
| `Pride In-Game Music` | `true` | Pride songs while you play, chosen by location. |
| `Pride Music Share` | `0.75` | How often a Pride song plays instead of a vanilla one (0–1). |
| `Mine and Slash Bars Position` | `top_center` | `top_center`, `top_left`, `top_right`, `middle_left`, `middle_right` or `bottom_left`. |
| `Mine and Slash Bars Nudge X` | `0` | Extra sideways shift in GUI pixels (negative = left). |
| `Mine and Slash Bars Nudge Y` | `0` | Extra up/down shift in GUI pixels (negative = up). |

## Optional integrations

All are detected at runtime; nothing is required.

| Mod | Used for |
|---|---|
| MCEF | In-game web browser. |
| OneConfig / PolyPatcher | Main-menu Config tile; the `/patcher` key binding. |
| Celeritas, Distant Horizons, AUSM | Extra Options buttons; AUSM on/off switch. |
| Zoomies | Loading ETA on the splash and world-loading screen. |
| Mine and Slash (+ MixinBooter) | HUD bar position. |
| CustomLoadingScreen | Dark clear instead of white before the splash. |
| Other Pride mods | Pause-menu shortcut tiles. |

## Requirements

- Minecraft 1.12.2
- Cleanroom (recommended) or Forge 14.23.5.2860+. The hidden-window splash and late mixin need Cleanroom (or MixinBooter); on Forge those parts are skipped.
- Client side only

## Install

1. Drop `PrideCanvas-1.12.2-0.1.0.jar` into the client's `mods` folder. It contains a coremod (`FMLCorePlugin`) and loads as a normal mod as well.
2. Start the game once to create `config/dpcanvas.cfg`, then adjust it if you like.
3. To use your own wallpaper, point `Wallpaper Frames Folder` at a folder of PNG frames.

## Building

```
./gradlew build
```

The jar is written to `build/libs/`. The project targets Java 8 (ForgeGradle 3, MCP snapshot `20171003-1.12`). Mixin, the MixinBooter API and Mine and Slash are compile-only dependencies; the last two are expected as jars in `libs/` (`mixinbooter-api.jar`, `mineandslash.jar`).

## License

MIT License — © 2026 crunkazcanbe

## Credits

Made by crunkazcanbe, with Claude.


## Compile-only jars

The build compiles against these jars in `libs/` (other authors' mods / APIs). They are not included in this repo — get them from their official pages and drop them in `libs/` before building:

- `mineandslash.jar`
- `mixinbooter-api.jar`
