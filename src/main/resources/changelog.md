## Changes

- The entity in a rule can now be an entity tag (`#c:bosses`) or every mob from a mod (`othermod:*`), as well as one mob or `*`. This works everywhere except the mob in `addSpawns`. Where several lines could apply to a mob, the narrowest wins: its own ID, then a tag, then `modid:*`, then `*`.
- `removeSpawns` now also stops mobs that code spawns instead of a biome's spawn list: phantoms, cats, pillager patrols, the wandering trader, village sieges and raids, as well as structure spawns (fortresses, monuments and so on) for every-dimension lines. A percent also cuts patrols, the trader, sieges and raids. Mobs you add with `addSpawns` are never removed. Spawner blocks and trial spawners are still unaffected.
- `attributeOverrides` now apply as a modifier instead of replacing the base value, so removing a line takes the change off a mob the next time it loads. Mobs saved by an older version keep the value they were given.
- `removeDrops` and `extraDrops` take an optional dimension at the end, where the mob dies. `despawnRanges` takes an optional dimension, and `lightSpawnRestrictions` an optional dimension and biome. Existing lines keep working.
  - Example: `minecraft:skeleton|minecraft:bone|?|minecraft:the_nether`
- New `spawnGroupSizes` list changes how many of a mob spawn together in the spawns that already exist: `entity_id|[dimension_id]|[biome]|min_group|[max_group]`.
  - Example: `minecraft:zombie|1|1`
- New `damageModifiers` list multiplies the damage a mob deals and takes: `entity_id|[dealt]|[taken]|[dimension_id]`. Arrows and other projectiles count for whoever fired them.
  - Example: `minecraft:zombie|?|0.5|minecraft:the_nether`

## Fixes

- Drop rules no longer affect players. Before, `*` could remove a player's items on death.
- Reducing a spawn by a percent no longer removes a mob with a low spawn weight entirely.
