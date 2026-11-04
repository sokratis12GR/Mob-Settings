## Mob Settings 1.1.0 for Minecraft 1.21.1 (NeoForge)

Brings 1.21.1 up to the 1.4.0 (26.1.2) feature set. Existing config lines keep working.

## New features

- **Item data on extra drops**: `extraDrops` items can carry data, written the way `/give` does: `item_id[component=value,...]`. The config screen has a Data menu for enchantments, durability, name, unbreakable and any other component, and items can be copied and pasted as text. Pasting onto a drop that has an item keeps that item and takes only the data, and whole `/give` commands can be pasted too.
  - Example: `minecraft:zombie|minecraft:diamond_sword[damage=100,enchantments={sharpness:5}]|1|0.5`
- **Entity data on added spawns**: `addSpawns` mobs can carry data: `entity_id{key:value,...}`. The config screen has a Data menu for each equipment slot and its drop chance, the mob's name, never despawning and any other NBT, and both a single slot and all of the data can be copied and pasted.
  - Example: `minecraft:zombie{equipment:{head:{id:'minecraft:iron_helmet'}},drop_chances:{head:0.5}}|minecraft:overworld|?|20`
  - A spawned mob can't be traced back to its line, so data is rolled using the line's share of that mob's spawn weight where it spawns. Only natural and chunk-generation spawns get data.
- **Entity tags and whole mods**: the entity in a rule can be an entity tag (`#c:bosses`) or every mob from a mod (`othermod:*`), as well as one mob or `*`. This works everywhere except the mob in `addSpawns`. Where several lines could apply to a mob, the narrowest wins: its own ID, then a tag, then `modid:*`, then `*`.
- **Spawn group sizes**: the new `spawnGroupSizes` list changes how many of a mob spawn together in the spawns that already exist: `entity_id|[dimension_id]|[biome]|min_group|[max_group]`.
  - Example: `minecraft:zombie|1|1`
- **Damage modifiers**: the new `damageModifiers` list multiplies the damage a mob deals and takes: `entity_id|[dealt]|[taken]|[dimension_id]`. Arrows and other projectiles count for whoever fired them.
  - Example: `minecraft:zombie|?|0.5|minecraft:the_nether`
- **Experience modifiers**: the new `experienceModifiers` list multiplies the experience a mob drops: `entity_id|[dimension_id]|multiplier`. The dimension is where the mob dies.
  - Example: `minecraft:zombie|minecraft:the_nether|2.5`
- **Per-dimension attributes**: `attributeOverrides` takes an optional dimension after the entity: `entity_id|[dimension_id]|attribute_id|value`. The dimension is where the mob first spawned, and its stats don't change if it travels.
  - Example: `minecraft:zombie|minecraft:the_nether|minecraft:generic.attack_damage|6`
- **More dimensions and biomes**: `removeDrops` and `extraDrops` take an optional dimension at the end, where the mob dies. `despawnRanges` takes an optional dimension, and `lightSpawnRestrictions` an optional dimension and biome.
  - Example: `minecraft:skeleton|minecraft:bone|?|minecraft:the_nether`
- **Looting on extra drops**: `extraDrops` lines can end with `T` to let Looting add 0 to 1 extra items per level. `F` or leaving it out means no bonus.
  - Example: `minecraft:zombie|minecraft:diamond|2|0.5|T`
- **Optional fields**: many fields can be left off the end of a line, or skipped with `?` to set a later one. The config screen lets you leave them blank too.
- **Default namespace**: IDs without a namespace use `minecraft`, so `zombie` means `minecraft:zombie`.

## Changes

- `removeSpawns` also stops mobs that code spawns instead of a biome's spawn list: phantoms, cats, pillager patrols, the wandering trader, village sieges and raids, as well as structure spawns (fortresses, monuments and so on) for every-dimension lines. A percent also cuts patrols, the trader, sieges and raids. Mobs you add with `addSpawns` are never removed. Spawner blocks and trial spawners are still unaffected.
- `addSpawns` mobs ignore their usual spawn rules (such as ground type and light level) wherever the line applies, so a chicken added to the Nether really spawns there. This also applies to that mob's existing spawns in those places. End a line with `T` to keep the usual rules.
- `attributeOverrides` apply as a modifier instead of replacing the base value, so removing a line takes the change off a mob the next time it loads. Mobs saved by an older version keep the value they were given.
- `*` in `attributeOverrides` no longer affects players. Add a `minecraft:player` line to change their attributes.
- Config files are upgraded once when loaded: spawn and attribute lines without a dimension are rewritten with a `*` dimension. They mean the same as before.
- A `removeSpawns` line that is only `*` is ignored with a warning. Write `*|*|*` to remove every spawn.
- A warning is logged when `minecraft:overworld` or `minecraft:the_nether` is used as a biome, since that matches nothing.
- Long rule text in the config screen wraps instead of being cut off.
- Light Level Spawn Restrictions is now called Light Spawn Restrictions, so the label fits on the main config screen.

## Fixes

- Drop rules no longer affect players. Before, `*` could remove a player's items on death.
- Reducing a spawn by a percent no longer removes a mob with a low spawn weight entirely.
- Mobs and players with a `minecraft:generic.max_health` override no longer heal fully when they change dimension.

## Notes for 1.21.1

- Attribute IDs include their category prefix, e.g. `minecraft:generic.max_health` or `minecraft:zombie.spawn_reinforcements`.
- Equipment slots are head, chest, legs, feet, mainhand, offhand and body. There's no saddle slot on this version.
- Names are JSON text, the same as in `/give` and `/summon`: `custom_name='"Big Sword"'` and `CustomName:'"Big Bob"'`. The Data menus handle this for you.
- All rows of a rule list have the same height on this version, so one long rule makes every row taller.
