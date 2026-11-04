## Changes

- `addSpawns` mobs now ignore their usual spawn rules (such as ground type and light level) wherever the line applies, so a chicken added to the Nether really spawns there. This also applies to that mob's existing spawns in those places. End a line with `T` to keep the usual rules: `entity_id|[dimension_id]|[biome]|weight|[min_group]|[max_group]|T`.
  - Example: `minecraft:cow|minecraft:overworld|#minecraft:is_forest|10|2|4|T`
- Long rule text in the config screen now wraps instead of being cut off, with each row growing to fit its text.
- Light Level Spawn Restrictions is now called Light Spawn Restrictions, so the label fits on the main config screen.

## Fixes

- The update checker now reads the mod's update URL, so new versions are reported in-game.
