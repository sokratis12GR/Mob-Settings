## Changes

- `despawnRanges` no longer removes persistent mobs, such as name-tagged ones or ones set to never despawn.
- `addSpawns` mobs now ignore their usual spawn rules (such as ground type and light level) wherever the line applies, so a chicken added to the Nether really spawns there. This also applies to that mob's existing spawns in those places. End a line with `T` to keep the usual rules: `entity_id|[dimension_id]|[biome]|weight|[min_group]|[max_group]|T`.
  - Example: `minecraft:cow|minecraft:overworld|#minecraft:is_forest|10|2|4|T`
- `*` in `attributeOverrides` no longer affects players. Add a `minecraft:player` line to change their attributes.
- Config files are upgraded once when loaded: spawn and attribute lines without a dimension are rewritten with a `*` dimension. They mean the same as before.
- A `removeSpawns` line that is only `*` is ignored with a warning. Write `*|*|*` to remove every spawn.
- A warning is logged when `minecraft:overworld` or `minecraft:the_nether` is used as a biome, since that matches nothing.
- Long rule text in the config screen now wraps instead of being cut off, with each row growing to fit its text.
- Light Level Spawn Restrictions is now called Light Spawn Restrictions, so the label fits on the main config screen.
- `extraDrops` items can now carry data, written the way `/give` does: `item_id[component=value,...]`. The config screen has a Data menu for enchantments, durability, name, unbreakable and any other component, and items can be copied and pasted as text. Pasting onto a drop that has an item keeps that item and takes only the data, and whole `/give` commands can be pasted too.
  - Example: `minecraft:zombie|minecraft:diamond_sword[damage=100,enchantments={sharpness:5}]|1|0.5`
- `addSpawns` mobs can now carry data, written the way `/summon` does: `entity_id{key:value,...}`. The config screen has a Data menu for each equipment slot and its drop chance, the mob's name, never despawning and any other NBT, and both a single slot and all of the data can be copied and pasted.
  - Example: `minecraft:zombie{equipment:{head:{id:'minecraft:iron_helmet'}},drop_chances:{head:0.5}}|minecraft:overworld|?|20`
  - A spawned mob can't be traced back to its line, so data is rolled using the line's share of that mob's spawn weight where it spawns. Only natural and chunk-generation spawns get data.
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
- The update checker now reads the mod's update URL, so new versions are reported in-game.
