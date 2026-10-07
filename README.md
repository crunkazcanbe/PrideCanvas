# PrideCanvas

Custom screens for Minecraft 1.12.2: a live boot dashboard and world-loading screen, a new main menu, pause menu, world and server lists, world creation, a full-width HUD, a context crosshair, a creative inventory and recipe screen, a Modrinth/CurseForge browser, a system monitor, menu themes, an in-game web browser, animations, UI sounds and music.

## About

PrideCanvas (mod id `dpcanvas`, shown in-game as "Pride UI") replaces the look of Minecraft's menus with one shared house style: a centred dark panel with a trans-flag accent bar, translucent square tiles, a moving wallpaper on the title screen, and a clear view of the game behind in-game menus. Every replaced screen keeps vanilla's own logic underneath, so options, sliders, mod-added buttons and other mods' handlers keep working.

It was built for the Pride modpack, a Minecraft 1.12.2 pack of around 750 mods, and is tuned for very large packs — the loading screen explains what is slow and what went wrong, offers a Safe Mode after failed starts, and several fixes target problems that show up with hundreds of mods. Nothing in it depends on the Pride pack, and every big feature can be switched off in the config, so it works in other 1.12.2 packs too.

It is client-side only.

## Features

### Loading screen

- **Native boot splash.** A coremod rewrites Forge's splash render loop. The splash runs entirely on its own (its own font renderer, every class preloaded on the main thread), so it can't crash or slow the game it is drawing.
- **Boot dashboard:** phase, current step, mods loaded N/M, percent, elapsed time and an ETA learned from your previous launches (or from Zoomies when installed). A forward-only progress bar that never bounces or jumps to 100 %.
- **System panel** (CPU, GPU, VRAM, RAM, heap, disk and network speed, memory pressure), plus **JVM**, **Threads** and **heap/GC graph** tabs and a **disk activity** panel.
- **Live log** with filter chips (All / Errors / Warnings / Info / Debug / Forge / Status), live error and warning counts, a search box (click or press `/`) and mouse-wheel scroll-back.
- **Potential problem cards** above the log for missing dependencies, duplicate IDs, mods that failed to load, exceptions and missing models or textures. Click for details; buttons open the mods or logs folder, or turn the named mod off for the next start.
- **"What's taking so long?"** after 10 seconds without progress: the current step and mod, and which mod class the loading thread is inside, in plain words.
- **Slowest mods** panel, and a **"Why did Minecraft take so long?"** report from a main-menu chip: stages, every mod ranked, comparison with your last, average and fastest launch, and a chart of the last 20 launches.
- **Safe Mode:** after two starts in a row that never reached the menu, choose Start normally, Safe mode (turns off the mods added or updated since the last good start) or Diagnostic (opens the developer view on the Errors tab). Turned-off mods come back with one button.
- **What's New:** pack notes from `config/pride-whats-new.txt` plus mods added, updated or removed since the last launch.
- **Explore:** a mod dependency web, a mod carousel (logo, version, authors, description) that follows the mod being loaded, live registry counters with milestone toasts, a "Now loading" texture gallery, and a developer view (stage times, main-thread stack, exceptions, Forge events, class transformers, memory pools).
- **Play while you wait:** Snake, Tetris, 2048, Minesweeper, Pong, Memory, Clicker and a side-scrolling Runner. Best scores are saved.
- **Music player:** title, progress, previous/next, shuffle, repeat, volume and a track list, with a frequency visualizer. A separate **Music ON/OFF** switch in the top-right corner only silences background music (jukebox discs keep playing).
- **Looks:** animated, still, procedural aurora, slideshow (your own pictures from `config/pride-backgrounds/`) or dark backgrounds; a sky that turns from night to day as loading progresses; 12 colour themes (Pride, Vanilla, Futuristic, Terminal, Cyberpunk, Industrial, Nuclear, Medieval, Minimal, CRT, Matrix, LCARS) plus a custom theme from `config/pride-boot-theme.json`; a Terminal boot mode that prints every step as `[  OK  ]` lines; context-aware tips, a clock and total playtime.
- **Light on resources:** capped at 30 fps, with adaptive quality that drops to 15 or 10 fps and hides panels if the screen gets expensive, and a **Turbo** button that turns everything optional off. A compact layout is used on windows narrower than 1600 px, keeping the top-left corner clear for overlays like MangoHud.
- **No white flash.** The splash background is forced dark, and on Cleanroom the game window stays hidden until the splash is ready to draw. Works alongside CustomLoadingScreen (its white clear is forced dark).
- **World loading screen.** Replaces "Downloading terrain" and the small progress box. Shows a **World card** (chunks loaded, generation rate, entities, tile entities, slowest world-gen mods) and a live biome-coloured **mini map** of generated chunks, with a **Cancel** button that stops a slow world start cleanly and returns to the menu.
- **Joining a server** shows a Connecting card (server, address, ping, version, state, time waiting) and server resource-pack download progress with speed and ETA.
- **Startup questions stay visible.** When Forge asks something during loading (missing registries, yes/no, error screens), the overlay steps aside.
- **Auto-load world.** Optionally go straight from loading into your last (or a named) world, once per launch; hold Shift to skip.
- Every loading-screen setting is under **Options > Loading Screen** (7 pages), saved in `config/pride-boot.txt`.

### Main menu

- Eight tiles in a centred panel under the logo: Singleplayer, Multiplayer, Mods, Options, Mod Browser, Browser, Config (opens OneConfig if installed) and Quit (with a confirm popup).
- Animated wallpaper with mouse parallax, drifting hearts and stars, a bobbing figure of your own skin, a logo that drops in, tiles that pop in one after another with a rising note, and a rotating line of Pride quotes (your configured subtitle first; neutral quotes are available).
- A "Loaded in" chip that opens the boot report (and says when this was your fastest launch).
- Live memory bar along the bottom of every title-screen menu.

### Pause menu

- Built on a real off-screen vanilla pause menu, so **every mod's Esc-menu button** becomes a tile here and runs that mod's own handler.
- Big "Back to Game" and "Save and Quit" / "Disconnect" bars; leaving asks to confirm (Save & Quit, Quit Game, Cancel) and shows how long you've played this session.
- Info card: your real 3D character (drag to spin, scroll to zoom), position, biome, dimension, day and time, health, session time, FPS and memory.
- Tiles for Browser, System Monitor, Themes, HUD Theme, HUD Settings and Crosshair.
- Shortcut tiles for other Pride mods, shown only when they're installed (Pride Hub, Store, Commands, Land Map, Permissions, Block History, Crash Reports, Quest Book, PrideChat's Chat Settings, Tails & Ears editor).
- The game stays visible behind it with a soft tint; the tile grid scrolls when there are many mod buttons.

### Worlds and servers

- **World list:** a scrolling card grid with world icons, game mode and last-played date; Play, New World, Edit, Delete (with confirm), Re-Create, Back. Opens quickly from a cache.
- **Create World:** two tabs — *Basics* (name, save folder, game mode with its description) and *More Options* (seed, structures, world type, cheats, bonus chest, customize). Vanilla's rules and state drive everything; mod-added buttons (Chunk Pregenerator's Preview, OreSpawn…) sit in a tidy row, even when mods add them late. An OTG preset picker appears when Open Terrain Generator is installed, and OTG's and Chunk Pregenerator's preview screens get the Pride look.
- **Multiplayer:** server cards with icon, coloured MOTD, player count and version from a live ping; Join, Direct, Add, Edit, Delete (with confirm), Refresh, Back.

### Options and sub-menus

- **Options** as a scrollable tile grid, with extra buttons for **Crosshair**, **System Monitor**, **Loading Screen** and **Themes**, and when the mods are present: **Distant Horizons** (a Pride-style settings screen) and **Celeritas** settings, **Pride Holo** settings, and **Shaders** on/off for the AUSM shader engine (switches it for the next start by renaming its jar, since it costs FPS even with no shader pack loaded).
- **Video Settings** rebuilt as tiles and sliders.
- **Skin Customization** and **Chat Settings** as tile panels like Options.
- **Themed sub-menus** (vanilla and Forge): Controls, Sounds, Language, Snooper, Resource Packs, Open to LAN, Stats, Advancements, Mod List, mod config screens, world-generation screens, yes/no and error screens, and more. Each is laid out inside a centred box the size of the pause menu, with lists on frosted glass instead of dirt. Containers, chat, books, signs and command blocks keep their own look.
- **Menu themes:** 18 palettes (pride, classic, midnight, forest, ocean, sunset, mono, nether, end, cherry, candy, cyber, steampunk, terminal, royal, ice, autumn and more) repaint every Pride menu, picked with live mock-ups from the Esc menu or Options. Logo (Pride / Tinted / Hidden), wallpaper (Animated / Theme / Dark), Pride or neutral messages, and the HUD and loading screen can follow the theme. Other Pride mods read the palette too.
- **Menu GUI scale:** optionally use a bigger GUI scale only while a menu is open; your HUD keeps your normal scale.

### Pride HUD

- One full-width strip above the hotbar (or across the top) that holds **every HUD**: Minecraft's hearts, food, armour and XP in the middle card, and each mod's overlay captured off-screen and placed in its own card. Mods can be set to *In the HUD*, *Draws normally* or *Hidden*.
- Native cards for **Mine and Slash** (level, XP, mana/energy, spells with cooldowns; its own top bars are hidden while the card is on), **Guns RPG**, **Vampirism** and **Serene Seasons**; real hearts, food, thirst and mana from **Scaling Health**, **Scaling Feast**, **AppleSkin**, **Tough As Nails** and **Player Mana**.
- **29 info cards** you can switch on and off: player, world, clock, coordinates, biome, weather, light, FPS, ping, players online, chunk, climate, portal coordinates, day/night timer, speed and more, drawn as status widgets with icons and bars.
- Status effects and achievements inside the HUD, and a large square minimap above the strip.
- **17 HUD themes** (sweets, pride, teddy, candy, picnic, cloud, ribbon, kawaii, paws, classic, bars, potions, pills, rings, numbers, crystals, wings…); the hotbar follows the theme. Calm by default: one colour at 50 % opacity, nothing flashing or sliding.
- Pages flip with the arrow keys when there are more cards than fit. **F9** shows or hides the HUD; **Page Up / Page Down** change its size.
- **HUD Settings** (10 pages) covers every option: strip position and width, colours and opacity, card layout, text size and shadows, hide in menus / third person / creative, minimap size and position, speed units, 24-hour clock and more. Saved in `config/pridecanvas/hud.json`.

### Crosshair, outline and advancements

- **Pride Crosshair:** shows what you can do right now: attack charge, interact, right tool, mining progress, place hint, bow draw, throw arc, eating, shield. 17 situations, each with its own shape, colour and size; 43 shapes (including Pride and trans rings) and 10 themes; hit marker, ready pulse, mob health bar, target name and distance, glow, bounce and sparkles. Steps aside when another mod replaces the crosshair. Settings with live preview under **Options > Crosshair**; saved in `config/pridecanvas/crosshair.json`.
- **Pride block outline:** the outline around the block you look at shimmers through the Pride colours and fills while you mine it.
- **Pride advancement screen:** every advancement by tab with search and filters, requirements with dates, and what comes before and after. Opens with L; the classic tree is one click away.
- **Pop-up switches:** turn advancement, new-recipe, tutorial and other mods' top-right toasts on or off.

### Creative, recipes and items

- **Pride Creative:** the creative inventory rebuilt as one scrolling item list with search, sorting (A–Z, Z–A, mod, rarity, recent), a sidebar of every loaded mod, hover fan-out groups, favourites, recent items and a trash slot. Every mod's inventory buttons (Baubles and others) appear in a strip, and PrideInventory's extra rows scroll under the main inventory. The list is built in small slices while you play, so the first open is instant even with hundreds of mods. Works on servers.
- **Pride Recipes:** press **R** (recipes) or **U** (uses) on a hovered item for a Pride-style recipe screen that reads crafting and machine recipes from EMI. Click an ingredient to dig deeper.
- **Pride Item FX:** items pop, wiggle and chime on hover, rare items glow, enchanted items shimmer, and new items sparkle. Saved in `config/pridecanvas/itemfx.json`.

### System Monitor

- CPU and per-core use, GPU name and load, VRAM, GTT, RAM, swap, game memory, FPS, disk and network, with graphs. Opens from the Esc menu or Options, with an optional small corner readout while playing.
- Linux, macOS (Java and sysctl) and Windows (performance counters, temperatures through LibreHardwareMonitor; untested).

### Mods and Mod Browser

- **Mods** tile: every mod in the folder PrideCanvas was loaded from, with search, scrollbar and keyboard scrolling. Click a row to enable/disable it (`.jar` ↔ `.jar.disabled`), click X to move it to a `.removed` folder. **Browse & Install** opens the Mod Browser.
- **Mod Browser** tile: Modrinth and CurseForge in Pride menus for **mods, resource packs, shaders and worlds**, with categories, sorting and paging. Each project has a page with its description, versions with changelogs and a gallery. Installs go into the right folder with required dependencies; worlds are unpacked safely; installs are recorded in `config/pridecanvas/installed.json`. Uses the public Modrinth API and a keyless CurseForge mirror; no account needed.
- Closing after changes offers Restart Now (Linux; relaunches the same command line), Quit Game or Keep Editing.

### Browser

- Opens a web page inside the game when MCEF (in-game Chromium) is installed, with back, forward, reload, home and an address bar (a URL, a domain, or words to search). Without MCEF it falls back to an external browser.
- The home page is a built-in start page with useful links, copied to `config/pridecanvas/start.html` so you can edit it; or set your own home page in the config.

### Animation and sound

- Tiles lift, glow, squish and ripple; menus slide and pop in; every screen (including other mods') glides in when it opens. All optional.
- Original UI sounds (hover, click, back, open, close, popup, confirm); hover chimes climb a scale across a row and are rate-limited.
- **Music:** 7 original chiptune songs (pulse, triangle, noise and an FM pad). A loading/menu playlist keeps playing into the main menu and fades out when a world loads, following your Master × Music volume. In game, Pride songs replace a share of vanilla's music, picked by where you are: caves, night, day, near a village, Nether, End.

### Fixes and extras

- **Resize black screen fix:** after you resize the window, the chunk renderers are rebuilt once the drag stops (what F3+A does) — fixes the world staying black with Celeritas/Sodium-style renderers and shader packs.
- **Window icon and title:** a trans-flag heart on a rainbow tile and the title "Pride", kept even when other mods rename the window.
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
| Show / hide the Pride HUD | F9 | Pride UI | Toggles the HUD strip. |
| Pride HUD: previous / next page | Left / Right arrow | Pride UI | Flips HUD pages when cards don't fit. |
| Pride HUD: bigger / smaller | Page Up / Page Down | Pride UI | Changes the HUD size. |

In Pride Creative and other inventories, **R** shows recipes and **U** shows uses for the hovered item (needs EMI).

## Config

File: `config/dpcanvas.cfg` (Forge `@Config`; also editable from the mod list's Config button). Changing the wallpaper folder takes effect without a restart.

The HUD, crosshair, creative inventory and item effects keep their detailed settings in JSON files under `config/pridecanvas/` (edited from their in-game screens). The loading screen keeps its settings in `config/pride-boot.txt`, because it starts before Forge's config system exists.

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
| `Pride HUD` | `true` | The full-width HUD strip. Off = every mod draws where it always did. |
| `Pride HUD Pass-Through Mods` | *(empty)* | Comma-separated mod ids that keep drawing in their own place. |
| `HUD size` | `1` | `1` follows your GUI scale, `2` twice as big, and so on; `0` = automatic. |
| `HUD: hotbar grows with the HUD` | `true` | Draw the hotbar at the HUD's size. |
| `HUD theme` | `sweets` | Look of the hearts / food card (17 themes). |
| `HUD: hotbar matches the HUD theme` | `true` | The hotbar uses the HUD theme's colours. |
| `HUD background opacity %` | `88` | Background opacity of the HUD theme. |
| `HUD: 24-hour clock` | `false` | 24-hour time on the HUD. |
| `HUD: player card` / `HUD: world card` | `true` | Show the player and world cards. |
| `HUD: hearts + food from Scaling Health / Scaling Feast / AppleSkin` | `true` | Use those mods' real bars in the HUD. |
| `Menu theme` | `pride` | Colours of every Pride menu (18 themes). |
| `Menu theme: logo` | `Pride` | `Pride`, `Tinted` or `Hidden`. |
| `Menu theme: wallpaper` | `Animated` | `Animated`, `Theme` or `Dark`. |
| `Menu theme: Pride messages` | `true` | Pride quotes and loading tips; off = neutral ones. |
| `Menu theme: character` | `true` | The small character on the main menu. |
| `Menu theme: HUD matches` | `false` | Picking a menu theme also switches the HUD theme. |
| `Menu theme: loading screen matches` | `true` | Picking a menu theme also recolours the loading screen. |
| `System Monitor corner readout` | `false` | Small FPS / CPU / GPU / VRAM / RAM readout while playing. |
| `Pride Crosshair` | `true` | The context crosshair. |
| `Pride block outline` | `true` | The shimmering outline that fills while mining. |
| `Pride advancement screen` | `true` | L opens the Pride advancement list. |
| `Pop-ups: Advancements` | `false` | "Advancement Made!" toasts. |
| `Pop-ups: New Recipes` | `false` | "New Recipes Unlocked!" toasts. |
| `Pop-ups: Tutorial Hints` | `false` | Minecraft's tutorial hints. |
| `Pop-ups: Other Mods` | `true` | Toasts added by other mods. |

## Optional integrations

All are detected at runtime; nothing is required.

| Mod | Used for |
|---|---|
| MCEF | In-game web browser. |
| OneConfig / PolyPatcher | Main-menu Config tile; the `/patcher` key binding. |
| Celeritas, Distant Horizons, AUSM | Extra Options buttons; AUSM on/off switch. |
| Zoomies | Step-matched loading ETA and world-gen profiling on the loading screens. |
| Mine and Slash (+ MixinBooter) | HUD bar position; native Pride HUD card. |
| Guns RPG, Vampirism, Serene Seasons | Native Pride HUD cards. |
| Scaling Health, Scaling Feast, AppleSkin, Tough As Nails, Player Mana | Real hearts, food, thirst and mana in the Pride HUD. |
| EMI | Recipe data for Pride Recipes (R / U). |
| Open Terrain Generator, Chunk Pregenerator | Preset picker and themed preview screens in Create World. |
| Pride Holo | Settings button in Options. |
| PrideInventory, PrideChat | Extra inventory rows in Pride Creative; Chat Settings tile. |
| CustomLoadingScreen | Dark clear instead of white before the splash. |
| Other Pride mods | Pause-menu shortcut tiles. |

## Requirements

- Minecraft 1.12.2
- Cleanroom (recommended) or Forge 14.23.5.2860+. The hidden-window splash and late mixin need Cleanroom (or MixinBooter); on Forge those parts are skipped.
- Client side only

## Install

1. Drop `PrideCanvas-1.12.2-<version>.jar` into the client's `mods` folder. It contains a coremod (`FMLCorePlugin`) and loads as a normal mod as well.
2. Start the game once to create `config/dpcanvas.cfg`, then adjust it if you like.
3. To use your own wallpaper, point `Wallpaper Frames Folder` at a folder of PNG frames.

## Building

```
./gradlew build
```

The jar is written to `build/libs/`. The music is generated by `tools/chiptune.py`. The project targets Java 8 (ForgeGradle 3, MCP snapshot `20171003-1.12`). Mixin, the MixinBooter API and Mine and Slash are compile-only dependencies; the last two are expected as jars in `libs/` (`mixinbooter-api.jar`, `mineandslash.jar`).

## License

MIT License — © 2026 crunkazcanbe

## Compile-only jars

The build compiles against these jars in `libs/` (other authors' mods / APIs). They are not included in this repo — get them from their official pages and drop them in `libs/` before building:

- `mineandslash.jar`
- `mixinbooter-api.jar`

## Credits

Made with [Claude Code](https://claude.com/claude-code) and [Blockbench](https://www.blockbench.net).
