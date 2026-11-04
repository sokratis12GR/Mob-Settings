## Changes

- `addSpawns` mobs now ignore their usual spawn rules (such as ground type and light level) wherever the line applies, so a chicken added to the Nether really spawns there. This also applies to that mob's existing spawns in those places. End a line with `T` to keep the usual rules: `entity_id|[dimension_id]|[biome]|weight|[min_group]|[max_group]|T`.
  - Example: `minecraft:cow|minecraft:overworld|#minecraft:is_forest|10|2|4|T`
- `*` in `attributeOverrides` no longer affects players. Add a `minecraft:player` line to change their attributes.
- Config files are upgraded once when loaded: spawn and attribute lines without a dimension are rewritten with a `*` dimension. They mean the same as before.
- A `removeSpawns` line that is only `*` is ignored with a warning. Write `*|*|*` to remove every spawn.
- A warning is logged when `minecraft:overworld` or `minecraft:the_nether` is used as a biome, since that matches nothing.
- Long rule text in the config screen now wraps instead of being cut off, with each row growing to fit its text.
- Light Level Spawn Restrictions is now called Light Spawn Restrictions, so the label fits on the main config screen.

## Fixes

- The update checker now reads the mod's update URL, so new versions are reported in-game.
