## New features

All new options are off by default (empty lists), so existing configs behave exactly as before.

- **Remove drops** (`removeDrops`): strip items from a mob's normal loot before they drop.
  - Format: `entity_id|item_id` or `entity_id|item_id|chance`. Chance (0.0-1.0) is optional and defaults to always.
  - `*` works for the entity (all mobs) or the item (every drop).
  - Example: `minecraft:zombie|minecraft:rotten_flesh`
- **Attribute overrides** (`attributeOverrides`): set a mob's base attribute (health, speed, armor, size, etc.) when it spawns or loads.
  - Format: `entity_id|attribute_id|value`. Values are kept within the attribute's valid range.
  - The config file lists every vanilla attribute mobs can have, with its range. Modded attributes work by ID.
  - Example: `minecraft:zombie|minecraft:max_health|40`
- **Despawn ranges** (`despawnRanges`): choose how far from the nearest player a mob can be before it despawns.
  - Format: `entity_id|range`
  - Example: `minecraft:zombie|32`
- **Light level spawn restrictions** (`lightSpawnRestrictions`): only allow natural spawns within a light level range.
  - Format: `entity_id|minLight|maxLight` (0-15)
  - Example: `minecraft:zombie|0|7`

- **In-game config screen**: every option can now be edited from the Mods menu via the Config button.
  - List options open an entry editor with a separate input per field and a searchable list of entities, items, biomes and attributes (attributes are filtered to the chosen mob).
  - Each entry is shown as a plain sentence (e.g. "Zombie drops 1 Diamond (5% chance)") with the raw config line underneath.
  - Hover a field or its label for an explanation, and hover an attribute in the list to see what it does. Number fields show their allowed range, and attribute values also show the mob's normal value.
  - Invalid entries are highlighted in red, and the editor won't save a line until it's valid. Entries are still stored in the same format, so existing configs keep working.
  - Spawn options (`addSpawns`, `removeSpawns`) show a restart prompt, since biome spawns only update when the world reloads.

## Changes

- **Partial spawn removal**: `removeSpawns` accepts an optional percentage to reduce a mob's spawn weight instead of removing it entirely.
  - Format: `entity_id|biome_id|percent` (0-100). Existing `entity_id|biome_id` lines still remove the mob completely.
  - Example: `minecraft:zombie|*|50` halves zombie spawns in every biome.
- Clearer config comments for `addSpawns` and `removeSpawns`, including what they can and can't affect.

## Fixes

- `addSpawns` entries for entities that can't naturally spawn (armor stands, boats, and other "misc" entities) are now skipped. Previously they silently spawned pigs instead.

## Notes

- `removeSpawns` affects spawns from vanilla, datapacks, and other mods, as well as your own `addSpawns` entries.
- `removeSpawns` does not affect structure spawns (fortresses, ocean monuments, witch huts, pillager outposts) or spawner blocks.
