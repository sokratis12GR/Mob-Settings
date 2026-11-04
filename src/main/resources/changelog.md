## Changes

- `addSpawns` mobs now ignore their usual spawn rules (such as ground type and light level) wherever the line applies, so a chicken added to the Nether really spawns there. This also applies to that mob's existing spawns in those places. End a line with `T` to keep the usual rules: `entity_id|[dimension_id]|[biome]|weight|[min_group]|[max_group]|T`.
  - Example: `minecraft:cow|minecraft:overworld|#minecraft:is_forest|10|2|4|T`
- Long rule text in the config screen now wraps instead of being cut off, with each row growing to fit its text.
- Light Level Spawn Restrictions is now called Light Spawn Restrictions, so the label fits on the main config screen.

- The entity in a rule can now be an entity tag (`#c:bosses`) or every mob from a mod (`othermod:*`), as well as one mob or `*`. This works everywhere except the mob in `addSpawns`. Where several lines could apply to a mob, the narrowest wins: its own ID, then a tag, then `modid:*`, then `*`.

## Fixes

- Drop rules no longer affect players. Before, `*` could remove a player's items on death.
- Reducing a spawn by a percent no longer removes a mob with a low spawn weight entirely.

- The update checker now reads the mod's update URL, so new versions are reported in-game.
