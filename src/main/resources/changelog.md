## Mob Settings for Minecraft 1.21.1 (NeoForge)

The first release of Mob Settings for 1.21.1. Every option starts empty, so the mod does nothing until you configure it.

## Features

- **Extra drops** (`extraDrops`): add items to a mob's drops.
  - Format: `entity_id|item_id|count|chance` (chance 0.0-1.0).
  - `*` as the entity applies to every mob, including modded ones.
  - `dropOnlyOnPlayerKill` (on by default) only gives extra drops when a player gets the kill.
  - Example: `minecraft:zombie|minecraft:diamond|2|0.5`
- **Remove drops** (`removeDrops`): remove items from a mob's drops.
  - Format: `entity_id|item_id` or `entity_id|item_id|chance`. Chance (0.0-1.0) is optional and defaults to always.
  - `*` works for the entity (every mob) or the item (every drop).
  - Runs before `extraDrops`, so extra drops are never removed. `*|*` plus a few `extraDrops` lines leaves only those items.
  - Example: `minecraft:zombie|minecraft:rotten_flesh`
- **Add spawns** (`addSpawns`): add natural spawns to biomes.
  - Format: `entity_id|dimension_id|biome|weight|min_group|max_group`
  - Example: `minecraft:blaze|minecraft:the_nether|minecraft:nether_wastes|10|1|3`
- **Remove spawns** (`removeSpawns`): remove a mob's natural spawns, or reduce them by a percentage.
  - Format: `entity_id|dimension_id|biome` or `entity_id|dimension_id|biome|percent` (0-100).
  - Runs before `addSpawns`, so your added spawns are never removed. `*|*|*` plus a few `addSpawns` lines leaves only those mobs spawning.
  - Affects spawns from vanilla, datapacks and other mods.
  - Example: `minecraft:zombie|*|*|50` halves zombie spawns everywhere.
- **Spawn rules per dimension and biome tag**:
  - Use `*` as the dimension for every dimension, or a dimension ID to set up the same biome differently in each dimension (e.g. a modded dimension that reuses vanilla biomes). Rules for every dimension are applied first, then rules for one dimension on top.
  - Use `*` as the biome for every biome, or a biome tag such as `#minecraft:is_nether` or `#c:is_forest`.
  - Lines without a dimension (`entity_id|biome|...`) also work and apply to every dimension.
- **Attribute overrides** (`attributeOverrides`): set a mob's base attribute (health, speed, armor, size, etc.) when it spawns or loads.
  - Format: `entity_id|attribute_id|value`. Values are kept within the attribute's valid range.
  - The config file lists every vanilla attribute mobs can have, with its range. Modded attributes work by ID.
  - Raising max health also spawns new mobs at full health.
  - Example: `minecraft:zombie|minecraft:generic.max_health|40`
- **Despawn ranges** (`despawnRanges`): set how far from the nearest player a mob can be before it despawns.
  - Format: `entity_id|range`
  - Example: `minecraft:zombie|32`
- **Light level spawn restrictions** (`lightSpawnRestrictions`): only allow natural spawns within a light level range.
  - Format: `entity_id|minLight|maxLight` (0-15)
  - Example: `minecraft:zombie|0|7`
- In `attributeOverrides` and `despawnRanges`, a line for a specific mob wins over a `*` line, whatever the order.

## In-game config screen

- Every option can be edited from the Mods menu with the Config button.
- List options open an entry editor with an input per field and a searchable list of entities, items, biomes, biome tags and attributes (attributes are filtered to the chosen mob).
- Each entry is shown as a plain sentence (e.g. "Zombie drops 1 Diamond (5% chance)") with the raw config line underneath.
- Hover a field or its label for an explanation, and hover an attribute in the list to see what it does. Number fields show their allowed range, and attribute values also show the mob's normal value.
- Invalid entries are highlighted in red, and the editor won't save a line until it's valid.
- Spawn options show a restart prompt, since biome spawns only update when the world reloads.

## Notes

- Invalid config lines are skipped and logged as a warning with the reason.
- Entities that can't spawn naturally (armor stands, boats and other "misc" entities) are skipped in `addSpawns`.
- Spawn rules for one dimension only apply to spawning after chunks generate, since a biome can be shared by several dimensions. Rules for every dimension (`*`) also affect animals placed during chunk generation.
- Spawn rules for every dimension don't affect structure spawns (fortresses, ocean monuments, witch huts, pillager outposts); rules for one dimension do. Neither affects spawner blocks.
- `removeDrops` also removes equipment and items a mob picked up.
- On 1.21.1, attribute IDs include their category prefix, e.g. `minecraft:generic.max_health` or `minecraft:zombie.spawn_reinforcements`.
