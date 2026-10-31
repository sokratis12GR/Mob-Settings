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
        REMOVE_DROPS = b.comment(
                "Remove drops from a mob before they spawn: entity_id|item_id or entity_id|item_id|chance.",
                "Runs before extraDrops, so extra drops are never removed. For example, \"*|*\" plus a few",
                "extraDrops entries replaces every mob's drops with only those items.",
                "Chance (0.0 to 1.0) is optional and defaults to 1.0 (always remove).",
                "Use * as the entity ID to affect ALL living entities, or as the item ID to strip every drop.",
                "Note: this also removes equipment and items the mob picked up (e.g. a player's gear a zombie grabbed).",
                "Example: minecraft:zombie|minecraft:rotten_flesh",
                "Example: minecraft:skeleton|minecraft:bone|0.5")
                .defineListAllowEmpty("removeDrops", List.of(), () -> "", o -> o instanceof String);
        DROP_ONLY_ON_PLAYER_KILL = b.comment("Require a player credited with the kill to award configured extra drops.")
                .define("dropOnlyOnPlayerKill", true);
        EXTRA_DROPS = b.comment(
                "Extra drops, added after removeDrops: entity_id|item_id|count|chance (chance 0.0 to 1.0).",
                "Use * as the entity ID to affect ALL living entities (including other mods).",
                "Example: minecraft:zombie|minecraft:diamond|2|0.5",
                "Example: othermod:boss|minecraft:nether_star|1|1.0")
                .defineListAllowEmpty("extraDrops", List.of(), () -> "", o -> o instanceof String);
        REMOVE_SPAWNS = b.comment(
                "Remove natural spawns: entity_id|dimension_id|biome or entity_id|dimension_id|biome|percent.",
                "Without percent the spawn is removed entirely; with percent (0-100) its spawn weight is reduced by that much.",
                "Use * as the entity ID for every mob, as the dimension ID for every dimension, or as the biome for every biome.",
                "The biome can also be a biome tag, e.g. #minecraft:is_nether or #c:is_forest.",
                "Runs before addSpawns, so added spawns are never removed. For example, \"*|minecraft:the_nether|*\"",
                "plus a few addSpawns entries for the Nether leaves only those mobs spawning there.",
                "Rules for every dimension (*) apply first; rules for one dimension are applied on top of them.",
                "Applies to vanilla, datapack, and other mods' biome spawns. Does not affect spawner blocks.",
                "Rules for every dimension don't affect structure spawns (fortresses, monuments, witch huts, outposts);",
                "rules for one dimension do.",
                "Lines without a dimension (entity_id|biome or entity_id|biome|percent) still work and apply to every dimension.",
                "Example: minecraft:creeper|*|minecraft:plains",
                "Example: minecraft:zombie|*|*|50",
                "Example: minecraft:zombie|minecraft:overworld|#minecraft:is_forest")
                .worldRestart()
                .defineListAllowEmpty("removeSpawns", List.of(), () -> "", o -> o instanceof String);
        ADD_SPAWNS = b.comment(
                "Add natural spawns, after removeSpawns: entity_id|dimension_id|biome|weight|min_group|max_group.",
                "Use * as the dimension ID for every dimension, or as the biome for every biome. Biome tags (#...) work too.",
                "Rules for every dimension (*) edit the biome itself, so they also apply to animals placed when chunks",
                "generate. Rules for one dimension only affect spawning afterwards, since a biome can be used in",
                "several dimensions.",
                "Entities in the MISC category (e.g. armor stands, boats) cannot naturally spawn and are skipped.",
                "Lines without a dimension (entity_id|biome|weight|min_group|max_group) still work and apply to every dimension.",
                "Example: othermod:forest_beast|*|minecraft:forest|20|1|2",
                "Example: minecraft:blaze|minecraft:the_nether|minecraft:nether_wastes|10|1|3")
                .worldRestart()
                .defineListAllowEmpty("addSpawns", List.of(), () -> "", o -> o instanceof String);
        ATTRIBUTE_OVERRIDES = b.comment(
                "Override a base attribute value when an entity joins the world: entity_id|attribute_id|value.",
                "Only applies if the entity already supports the given attribute; values are clamped to the attribute's range.",
                "A line for a specific mob wins over a * line for the same attribute.",
                "Raising generic.max_health also fills a newly spawned mob's health to the new maximum.",
                "Modded attributes work too, by their registry ID.",
                "",
                "Vanilla attributes every mob has (valid range):",
                "  minecraft:generic.max_health (1 to 1024)",
                "  minecraft:generic.movement_speed (0 to 1024)",
                "  minecraft:generic.armor (0 to 30)",
                "  minecraft:generic.armor_toughness (0 to 20)",
                "  minecraft:generic.knockback_resistance (0 to 1)",
                "  minecraft:generic.explosion_knockback_resistance (0 to 1)",
                "  minecraft:generic.attack_knockback (0 to 5)",
                "  minecraft:generic.follow_range (0 to 2048)",
                "  minecraft:generic.max_absorption (0 to 2048)",
                "  minecraft:generic.scale (0.0625 to 16)",
                "  minecraft:generic.step_height (0 to 10)",
                "  minecraft:generic.jump_strength (0 to 32)",
                "  minecraft:generic.gravity (-1 to 1)",
                "  minecraft:generic.safe_fall_distance (-1024 to 1024)",
                "  minecraft:generic.fall_damage_multiplier (0 to 100)",
                "  minecraft:generic.burning_time (0 to 1024)",
                "  minecraft:generic.oxygen_bonus (0 to 1024)",
                "  minecraft:generic.movement_efficiency (0 to 1)",
                "  minecraft:generic.water_movement_efficiency (0 to 1)",
                "Vanilla attributes only some mobs have:",
                "  minecraft:generic.attack_damage (0 to 2048) - mobs that melee attack",
                "  minecraft:generic.flying_speed (0 to 1024) - flying mobs (bee, parrot, allay, ghast, wither)",
                "  minecraft:zombie.spawn_reinforcements (0 to 1) - zombies and zombified piglins",
                "",
                "Example: minecraft:zombie|minecraft:generic.max_health|40")
                .defineListAllowEmpty("attributeOverrides", List.of(), () -> "", o -> o instanceof String);
        DESPAWN_RANGES = b.comment(
                "Override natural despawn distance (in blocks from the nearest player): entity_id|range.",
                "Entities closer than range are kept loaded; entities farther are force-despawned.",
                "A line for a specific mob wins over a * line.",
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
