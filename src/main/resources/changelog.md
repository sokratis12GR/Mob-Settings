## 26.1.2 - 1.6.1

- Make experience modifying happen last so that it can be applied on all mods.

## 26.1.2 - 1.6.0

### Changes

- Mob Settings now needs TheDragonLib 1.5.0 or newer, on both the client and the server. The rule list screens now come from it.
- Every rule list can copy one line, copy all lines, and paste lines from the clipboard. Invalid and duplicate lines are skipped when pasting.
- `extraDrops`, `removeDrops`, `addSpawns` and `removeSpawns` get an "Add from list" button. Pick a mob, then pick lines made from the opposite list (for example, `removeDrops` lines from your `extraDrops`). Operators also get what the server says the mob drops (its loot table) or where it spawns (every dimension, after all rules).
- New `/mobsettings drops <entity>` command lists every item a mob's loot table can drop. It needs operator permission.
- In a world, only operators can use "Add from list". The mob list shows how many drops, biomes and config lines each mob has, and hides mobs with none. `removeSpawns` and `removeDrops` also offer an "Everything" line for the mob.
- `/mobsettings spawns` and `/mobsettings drops` take a dimension (or `all` for spawns). Long spawn lists are grouped and split into pages with clickable Prev/Next buttons.
- The config screens show where you are (for example "Mob Settings › Remove Spawns › Edit line 3"), and a yellow ● when a list has unsaved changes. Leaving with Esc or Cancel after changes asks whether to save, discard or keep editing.
- Copy, Paste and Delete have their own colours. Paste's tooltip previews the clipboard, and pasting reports what was added, what was already there and what was invalid; hover the message to see the skipped lines and why.
- Lines copied from one list can be pasted into another: the entity, item, dimension and biome carry over, and missing numbers get defaults (for example weight 10 and group 1–4 for `addSpawns`). Lines copied straight from the .toml file (with quotes and commas) can be pasted too.
- New `/mobsettings inspect [target]` command shows the mob you're looking at (or a selector's target): its category, dimension, biome, whether it despawns, its attributes against their defaults, and the Mob Settings rules that apply to it. `[Show NBT]` opens the full data. It needs operator permission.
- On a server, players who aren't operators get a notice instead of the config screen: the rules there are the server's, and their local config has no effect on it. Single player, LAN hosts and operators are unchanged.
- `/mobsettings inspect type <entity>` shows a mob type's category and rules without needing a live mob.
- **Experimental** (turn on `experimentalInspectScreen`, off by default): `/mobsettings inspect` opens a screen instead of printing to chat, with the mob in 3D and tabs for its overview, equipment, spawns, drops and rules. Drops taken away by `removeDrops` are struck through and marked removed (or removed N% of the time); `extraDrops` are listed as added. Spawn rule lines saved since the world loaded are marked as taking effect after a restart. On the Equipment tab, edit, clear or reset each slot, copy the mob as an `addSpawns` line or its data, paste gear, save it to the config, or apply it to the live mob. `/mobsettings inspect chat` still prints to chat. Operators only. It may change or break between versions.
- `maxGroupPlacement` now defaults to 64 and goes up to 128 (was 256, up to 1000), and a group gets a limited number of placement tries, so big groups can't stall the server. Configs set above 128 are reset to 64.
- The mob list's drop and spawn counts are worked out once and kept until the config changes or `/reload`, instead of on every open.
- The server limits how many lookups and edits each player's config and inspect screens can send per second, so a modified client can't flood it.