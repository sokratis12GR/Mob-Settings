## New features

- **Per-dimension attributes**: `attributeOverrides` now takes an optional dimension after the entity: `entity_id|[dimension_id]|attribute_id|value`. The dimension is where the mob first spawned, and its stats don't change if it travels.
  - Example: `minecraft:zombie|minecraft:the_nether|minecraft:attack_damage|6`
- **Experience modifiers**: the new `experienceModifiers` list multiplies the experience a mob drops: `entity_id|[dimension_id]|multiplier`. The dimension is where the mob dies.
  - Example: `minecraft:zombie|minecraft:the_nether|2.5`
- **Looting on extra drops**: `extraDrops` lines can end with an optional `T` to let Looting add 0 to 1 extra items per level: `entity_id|item_id|count|chance|T`. `F` or leaving it out means no bonus, so existing lines behave the same.
  - Example: `minecraft:zombie|minecraft:diamond|2|0.5|T`
- **Optional fields**: many fields can now be left off the end of a line, or skipped with `?` to set a later one. The config screen lets you leave them blank too.
  - Defaults: dimensions and biomes apply to all of them, `extraDrops` count is 1 and chance is 100%, `addSpawns` min group is 1 and max group matches it, and light levels are 0 to 15.
  - A single ID after the entity in a spawn line is the biome. To target a dimension, skip the biome: `minecraft:zombie|minecraft:the_nether|?`
  - `extraDrops` example: `minecraft:zombie|minecraft:diamond|?|0.5` drops 1 diamond at 50%
  - `experienceModifiers` example: `minecraft:zombie|2` doubles zombie experience in every dimension
  - `lightSpawnRestrictions` example: `minecraft:zombie|?|7` only lets zombies spawn at light level 7 or below
- **Default namespace**: IDs without a namespace use `minecraft`, so `zombie` means `minecraft:zombie`.

## Changes

- `*` in `attributeOverrides` no longer affects players. Add a `minecraft:player` line to change their attributes.
- Config files are upgraded once when loaded: spawn and attribute lines without a dimension are rewritten with a `*` dimension. They mean the same as before.
- A `removeSpawns` line that is only `*` is ignored with a warning. Write `*|*|*` to remove every spawn.
- A warning is logged when `minecraft:overworld` or `minecraft:the_nether` is used as a biome, since that matches nothing.

## Fixes

- Mobs and players with a `minecraft:max_health` override no longer heal fully when they change dimension.
