# Changelog

## 0.2.0 - 2026-10-07

### Added
- **Loading screen overhaul.** The boot splash is now a live dashboard:
  - Boot sequence card (phase, step, mods loaded N/M, percent, elapsed time, learned ETA from previous launches) and a System panel (CPU, GPU, VRAM, RAM, heap, disk and network speed, memory pressure).
  - "Slowest mods" panel and a "Why did Minecraft take so long?" report from a main-menu chip, with a bar chart of the last 20 launches and boot history kept in `config/pride-boot-history.txt`.
  - Log filter chips (All / Errors / Warnings / Info / Debug / Forge / Status) with live counts, a search box and scroll-back.
  - "Potential problem" cards for missing dependencies, duplicate IDs, failed mods, exceptions and missing models or textures, with buttons to open the mods or logs folder or turn a named mod off for the next start.
  - A "What's taking so long?" card after 10 seconds without progress, showing which mod class the loading thread is inside.
  - **Safe Mode:** after two starts in a row that never reached the menu, the game offers to start normally, start with the recently added or updated mods turned off, or open in a diagnostic view.
  - JVM, Threads and heap/GC graph tabs, a disk activity panel, a "Now loading" texture gallery, and a developer view (stage times, main-thread stack, exceptions, Forge events, class transformers, memory pools).
  - A What's New panel (pack notes from `config/pride-whats-new.txt` plus mods added, updated or removed since the last launch), context-aware tips, a clock and total playtime.
  - Explore views: a mod dependency web, a mod carousel with logos and descriptions, and live registry counters (blocks, items, entities, biomes, recipes and more) with milestone toasts.
  - Eight mini-games while you wait: Snake, Tetris, 2048, Minesweeper, Pong, Memory, Clicker and a side-scrolling Runner, with best scores saved.
  - A music player (title, progress, previous/next, shuffle, repeat, volume, track list) with a frequency visualizer, plus a music on/off switch that only affects background music.
  - Background modes (animated, still, procedural aurora, slideshow from `config/pride-backgrounds/`, dark), a sky that moves from night to day with progress, 12 colour themes plus a custom theme file, a Terminal boot mode, and a Turbo button that reduces the screen's own cost.
  - Adaptive quality: the screen lowers its frame rate and hides panels automatically if it becomes expensive. A compact layout is used on windows narrower than 1600 px.
  - Every setting is available under **Options > Loading Screen** (7 pages).
- **World loading:** the Pride loading screen replaces "Downloading terrain" and the small progress box, adds a **Cancel** button that stops a slow world start and returns to the menu, and shows a World card (chunks loaded, generation rate, entities, slowest world-gen mods) with a live biome-coloured mini map. Joining a server shows a Connecting card and resource-pack download progress.
- **Pride HUD:** a full-width strip above the hotbar that gathers every mod's HUD overlay into its own card, with vanilla bars in the middle.
  - Native cards for Mine and Slash, Guns RPG, Vampirism and Serene Seasons; hearts, food and thirst drawn from Scaling Health, Scaling Feast, AppleSkin and Tough As Nails.
  - 29 switchable info cards (clock, coordinates, biome, light, FPS, ping, chunk, climate, portal coordinates, day/night timer and more), status effects and achievements in the HUD, and a large minimap.
  - 17 HUD themes, a solid one-colour strip at 50% opacity by default, no flashing or sliding, and arrow-key paging when cards don't fit.
  - A 10-page HUD Settings screen. **F9** shows or hides the HUD; Page Up / Page Down change its size.
- **Pride Crosshair:** a context crosshair (attack charge, interact, right tool, mining progress, bow draw, throw arc, eating, shield) with 17 situations, 43 shapes, 10 themes, extra effects (hit marker, target name and distance, mob health bar) and a settings screen with live preview. Also a Pride block outline that fills while mining.
- **Pride advancement screen:** every advancement by tab with search, filters, requirements, dates and what each unlocks. Replaces the L screen; the classic tree is one click away.
- **Pride Creative:** a rebuilt creative inventory with one scrolling item list, sorting, mod sidebar, hover fan-out groups, favourites, recent items and trash. The item list is built in small slices during play so the first open is instant. Press **R** / **U** on an item to see recipes or uses.
- **Pride Recipes:** a recipe screen in the Pride style that reads its data from EMI (crafting and machine recipes from every mod).
- **Pride Item FX:** hover pop, wiggle and chime on items, rarity glow, enchantment shimmer and a sparkle on newly picked-up items.
- **Mod Browser:** a full Modrinth and CurseForge browser for mods, resource packs, shaders and worlds, with categories, sorting, project pages (description, versions with changelogs, gallery), installation into the right folder, required dependencies and a restart prompt.
- **System Monitor:** CPU, cores, GPU, VRAM, RAM, swap, game memory, FPS, disk and network with graphs, plus an optional corner readout. Linux, Windows (untested) and macOS.
- **Menu themes:** 18 colour palettes that repaint every Pride menu, with live previews, logo and wallpaper options, and an option for neutral (non-Pride) messages. Other Pride mods follow the chosen theme.
- **Distant Horizons settings** as a Pride-style screen, Pride-style Chunk Pregenerator world preview, and an OTG preset picker from Create World.
- **Pop-up switches** for advancement, recipe, tutorial and other mods' toasts.
- **Pride music:** 7 original chiptune songs.
- Skin Customization and Chat Settings as Pride tile panels; Esc-menu tiles for Themes, HUD Theme, HUD Settings, Crosshair, System Monitor and Chat Settings (with PrideChat).

### Fixed
- Startup crash at "Initializing game" (Mixin `ConcurrentModificationException`): every class the splash uses is now loaded on the main thread before the splash starts.
- Main-menu crash "newPosition > limit": the splash now draws text with its own font renderer instead of sharing the main thread's `BufferBuilder`.
- Mixin re-entrance crash with PolyPatcher/OneConfig: the coremod computes common superclasses by reading class files instead of loading classes.
- Pride HUD capture fixes: no scan starvation, idle mods skip the capture buffer, captures shrink on the GPU, a window resize no longer squashes the minimap, and the strip stays when another mod cancels the whole HUD.
- Window title stays "Pride" when other mods rename the window; resize fix is safe when the window is minimized.
- Reskinned mod buttons keep their own click handlers.

### Changed
- New window icon (a trans-flag heart on a rainbow tile).
- Main menu tiles pop in with a short sound; louder hover chime; solid quit popup on top of the pause tiles.
- The world list loads faster from a cache.
- The **Mods** tile's "Browse & Install" button opens the new Mod Browser.

