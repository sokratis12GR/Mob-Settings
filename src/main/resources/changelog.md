## Changes

- Edits to `removeSpawns`, `addSpawns` and `spawnGroupSizes` in the config screen now show up straight away when you reopen the list. Before, the screen kept showing the old lines until the world restarted, so changes looked lost.
- Group sizes from `addSpawns` and `spawnGroupSizes` now actually spawn. Vanilla stopped at about 4 and rarely got past 20, whatever the group size, so Mob Settings now places these groups itself. Spawn rules, removals, light restrictions and spawn data still apply to every member.
- New `maxGroupPlacement` option (default 256, up to 1000) caps the size of these groups. Big groups go past the mob cap and can cause a short lag spike.
- `removeSpawns` lines for one dimension that remove a mob entirely now also stop it appearing when new chunks generate, such as animals in fresh terrain.
- `experienceModifiers`: between two equally specific lines, the first one now wins, the same as the other lists.
- New `/mobsettings spawns <entity>` command lists every biome in your current dimension where a mob spawns naturally, with weight, share of its category and group size, after all rules. `/mobsettings spawns here` lists everything that can spawn where you're standing, including structure spawns. Both need operator permission.
