package com.sofodev.mobsettings;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public final class MSConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DROP_ONLY_ON_PLAYER_KILL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_DROPS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADD_SPAWNS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REMOVE_SPAWNS;

    private MSConfig() {}

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        DROP_ONLY_ON_PLAYER_KILL = b.comment("Require a player credited with the kill to award configured extra drops.")
                .define("dropOnlyOnPlayerKill", true);
        EXTRA_DROPS = b.comment(
                "Extra drops: entity_id|item_id|count|chance (chance 0.0 to 1.0).",
                "Use * as the entity ID to affect ALL living entities (including other mods).",
                "Example: minecraft:zombie|minecraft:diamond|2|0.5",
                "Example: othermod:boss|minecraft:nether_star|1|1.0")
                .defineListAllowEmpty("extraDrops", List.of(), () -> "", o -> o instanceof String);
        ADD_SPAWNS = b.comment(
                "Add natural spawns: entity_id|biome_id|weight|min_group|max_group.",
                "Entity types are resolved against the live entity registry, including modded ones.",
                "Use * as the biome ID to affect all loaded biomes.",
                "Example: othermod:forest_beast|minecraft:forest|20|1|2")
                .defineListAllowEmpty("addSpawns", List.of(), () -> "", o -> o instanceof String);
        REMOVE_SPAWNS = b.comment(
                "Remove natural spawn entries: entity_id|biome_id.",
                "Use * as biome ID for every biome, or entity ID for every entity.",
                "Example: minecraft:creeper|minecraft:plains")
                .defineListAllowEmpty("removeSpawns", List.of(), () -> "", o -> o instanceof String);
        SPEC = b.build();
    }
}
