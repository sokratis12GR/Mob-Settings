package com.sofodev.mobsettings;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public final class MSConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DROP_ONLY_ON_PLAYER_KILL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_DROPS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADD_SPAWNS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REMOVE_SPAWNS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REMOVE_DROPS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ATTRIBUTE_OVERRIDES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DESPAWN_RANGES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LIGHT_SPAWN_RESTRICTIONS;

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
                "Entities in the MISC category (e.g. armor stands, boats) cannot naturally spawn and are skipped.",
                "Note: removeSpawns runs after these additions, so a matching removeSpawns line also affects them.",
                "Example: othermod:forest_beast|minecraft:forest|20|1|2")
                .worldRestart()
                .defineListAllowEmpty("addSpawns", List.of(), () -> "", o -> o instanceof String);
        REMOVE_SPAWNS = b.comment(
                "Remove natural spawn entries: entity_id|biome_id or entity_id|biome_id|percent.",
                "Without percent the entry is removed entirely; with percent (0-100) its spawn weight is reduced by that much.",
                "Use * as biome ID for every biome, or entity ID for every entity.",
                "Applies to vanilla, datapack, and other mods' biome spawns, plus addSpawns entries above.",
                "Does not affect structure spawns (fortresses, monuments, witch huts, outposts) or spawner blocks.",
                "Example: minecraft:creeper|minecraft:plains",
                "Example: minecraft:zombie|*|50")
                .worldRestart()
                .defineListAllowEmpty("removeSpawns", List.of(), () -> "", o -> o instanceof String);
        REMOVE_DROPS = b.comment(
                "Remove existing loot-table drops before they spawn: entity_id|item_id or entity_id|item_id|chance.",
                "Chance (0.0 to 1.0) is optional and defaults to 1.0 (always remove).",
                "Use * as the entity ID to affect ALL living entities, or as the item ID to strip every drop.",
                "Example: minecraft:zombie|minecraft:rotten_flesh",
                "Example: minecraft:skeleton|minecraft:bone|0.5")
                .defineListAllowEmpty("removeDrops", List.of(), () -> "", o -> o instanceof String);
        ATTRIBUTE_OVERRIDES = b.comment(
                "Override a base attribute value when an entity joins the world: entity_id|attribute_id|value.",
                "Only applies if the entity already supports the given attribute; values are clamped to the attribute's range.",
                "Modded attributes work too, by their registry ID.",
                "",
                "Vanilla attributes every mob has (valid range):",
                "  minecraft:max_health (1 to 1024)",
                "  minecraft:movement_speed (0 to 1024)",
                "  minecraft:armor (0 to 30)",
                "  minecraft:armor_toughness (0 to 20)",
                "  minecraft:knockback_resistance (0 to 1)",
                "  minecraft:explosion_knockback_resistance (0 to 1)",
                "  minecraft:attack_knockback (0 to 5)",
                "  minecraft:follow_range (0 to 2048)",
                "  minecraft:max_absorption (0 to 2048)",
                "  minecraft:scale (0.0625 to 16)",
                "  minecraft:step_height (0 to 10)",
                "  minecraft:jump_strength (0 to 32)",
                "  minecraft:gravity (-1 to 1)",
                "  minecraft:safe_fall_distance (-1024 to 1024)",
                "  minecraft:fall_damage_multiplier (0 to 100)",
                "  minecraft:burning_time (0 to 1024)",
                "  minecraft:oxygen_bonus (0 to 1024)",
                "  minecraft:movement_efficiency (0 to 1)",
                "  minecraft:water_movement_efficiency (0 to 1)",
                "  minecraft:entity_interaction_range (0 to 64)",
                "  minecraft:camera_distance (0 to 32)",
                "  minecraft:waypoint_transmit_range (0 to 60000000)",
                "Vanilla attributes only some mobs have:",
                "  minecraft:attack_damage (0 to 2048) - mobs that melee attack",
                "  minecraft:flying_speed (0 to 1024) - flying mobs (bee, parrot, allay, ghast, wither)",
                "  minecraft:spawn_reinforcements (0 to 1) - zombies and zombified piglins",
                "  minecraft:tempt_range (0 to 2048) - animals that follow food",
                "",
                "Example: minecraft:zombie|minecraft:max_health|40")
                .defineListAllowEmpty("attributeOverrides", List.of(), () -> "", o -> o instanceof String);
        DESPAWN_RANGES = b.comment(
                "Override natural despawn distance (in blocks from the nearest player): entity_id|range.",
                "Entities closer than range are kept loaded; entities farther are force-despawned.",
                "Example: minecraft:zombie|32")
                .defineListAllowEmpty("despawnRanges", List.of(), () -> "", o -> o instanceof String);
        LIGHT_SPAWN_RESTRICTIONS = b.comment(
                "Restrict natural spawning to a light level range: entity_id|minLight|maxLight (0-15).",
                "Cancels the spawn if the block's light level falls outside the range.",
                "Example: minecraft:zombie|0|7")
                .defineListAllowEmpty("lightSpawnRestrictions", List.of(), () -> "", o -> o instanceof String);
        SPEC = b.build();
    }
}
