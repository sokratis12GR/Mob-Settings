package com.sofodev.mobsettings;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;
import java.util.function.UnaryOperator;

public final class MSConfig {
    private static final int CURRENT_VERSION = 2;
    public static final ModConfigSpec SPEC;
    // Defaults to the oldest version: a file from before this key existed gets the default filled in when it loads.
    private static final ModConfigSpec.IntValue CONFIG_VERSION;
    public static final ModConfigSpec.BooleanValue DROP_ONLY_ON_PLAYER_KILL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_DROPS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADD_SPAWNS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REMOVE_SPAWNS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REMOVE_DROPS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ATTRIBUTE_OVERRIDES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXPERIENCE_MODIFIERS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DESPAWN_RANGES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LIGHT_SPAWN_RESTRICTIONS;

    private MSConfig() {}

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        CONFIG_VERSION = b.comment(
                "Used to upgrade this file when the format changes. Don't edit this.",
                "",
                "In the lists below, fields in [brackets] are optional. Leave them off the end of a line, or write ? to",
                "skip one and set a later field. IDs without a namespace use minecraft, so zombie means minecraft:zombie.")
                .defineInRange("configVersion", 1, 1, Integer.MAX_VALUE);
        REMOVE_DROPS = b.comment(
                "Remove drops from a mob before they spawn: entity_id|item_id|[chance].",
                "Runs before extraDrops, so extra drops are never removed. For example, \"*|*\" plus a few",
                "extraDrops entries replaces every mob's drops with only those items.",
                "Chance (0.0 to 1.0) defaults to 1.0 (always remove).",
                "Use * as the entity ID to affect ALL living entities, or as the item ID to strip every drop.",
                "Note: this also removes equipment and items the mob picked up (e.g. a player's gear a zombie grabbed).",
                "Example: minecraft:zombie|minecraft:rotten_flesh",
                "Example: minecraft:skeleton|minecraft:bone|0.5")
                .defineListAllowEmpty("removeDrops", List.of(), () -> "", o -> o instanceof String);
        DROP_ONLY_ON_PLAYER_KILL = b.comment("Require a player credited with the kill to award configured extra drops.")
                .define("dropOnlyOnPlayerKill", true);
        EXTRA_DROPS = b.comment(
                "Extra drops, added after removeDrops: entity_id|item_id|[count]|[chance]|[looting].",
                "Count defaults to 1 and chance (0.0 to 1.0) to 1.0 (always drop).",
                "Use * as the entity ID to affect ALL living entities (including other mods).",
                "Set looting to T to let Looting affect the drop: each level adds 0 to 1 extra items. F or leaving it out means no bonus.",
                "Example: minecraft:zombie|minecraft:diamond|2|0.5",
                "Example: minecraft:zombie|minecraft:diamond|?|0.5|T",
                "Example: othermod:boss|minecraft:nether_star|1|1.0")
                .defineListAllowEmpty("extraDrops", List.of(), () -> "", o -> o instanceof String);
        REMOVE_SPAWNS = b.comment(
                "Remove natural spawns: entity_id|[dimension_id]|[biome]|[percent].",
                "Without percent the spawn is removed entirely; with percent (0-100) its spawn weight is reduced by that much.",
                "Dimension and biome default to all of them, the same as writing *.",
                "A single ID after the entity is the biome. To target a dimension, write entity_id|dimension_id|?.",
                "Use * as the entity ID for every mob. A line that is only * is ignored; write *|*|* to remove every spawn.",
                "The biome can also be a biome tag, e.g. #minecraft:is_nether or #c:is_forest.",
                "Runs before addSpawns, so added spawns are never removed. For example, \"*|minecraft:the_nether|*\"",
                "plus a few addSpawns entries for the Nether leaves only those mobs spawning there.",
                "Rules for every dimension (*) apply first; rules for one dimension are applied on top of them.",
                "Applies to vanilla, datapack, and other mods' biome spawns. Does not affect spawner blocks.",
                "Rules for every dimension don't affect structure spawns (fortresses, monuments, witch huts, outposts);",
                "rules for one dimension do.",
                "Example: minecraft:creeper|minecraft:plains",
                "Example: minecraft:zombie|50",
                "Example: minecraft:zombie|minecraft:the_nether|?",
                "Example: minecraft:zombie|minecraft:overworld|#minecraft:is_forest")
                .worldRestart()
                .defineListAllowEmpty("removeSpawns", List.of(), () -> "", o -> o instanceof String);
        ADD_SPAWNS = b.comment(
                "Add natural spawns, after removeSpawns: entity_id|[dimension_id]|[biome]|weight|[min_group]|[max_group].",
                "Dimension and biome default to all of them, the same as writing *. Biome tags (#...) work too.",
                "A single ID after the entity is the biome. To target a dimension, write entity_id|dimension_id|?|weight.",
                "Min group defaults to 1, and max group to the min group.",
                "Rules for every dimension (*) edit the biome itself, so they also apply to animals placed when chunks",
                "generate. Rules for one dimension only affect spawning afterwards, since a biome can be used in",
                "several dimensions.",
                "Entities in the MISC category (e.g. armor stands, boats) cannot naturally spawn and are skipped.",
                "Example: othermod:forest_beast|minecraft:forest|20|1|2",
                "Example: minecraft:blaze|minecraft:the_nether|?|10|1|3",
                "Example: minecraft:blaze|minecraft:the_nether|minecraft:nether_wastes|10")
                .worldRestart()
                .defineListAllowEmpty("addSpawns", List.of(), () -> "", o -> o instanceof String);
        ATTRIBUTE_OVERRIDES = b.comment(
                "Override a base attribute value when an entity joins the world: entity_id|[dimension_id]|attribute_id|value.",
                "Only applies if the entity already supports the given attribute; values are clamped to the attribute's range.",
                "Use * as the entity ID for every mob except players (use minecraft:player for them).",
                "Dimension defaults to every dimension, the same as writing *.",
                "The dimension is where the mob first spawned: its stats don't change if it travels to another dimension.",
                "Mobs from before this setting existed count as spawned in the dimension they're next loaded in.",
                "For the same attribute, a line for a specific mob wins over a * line, then a line for one dimension wins",
                "over a line for every dimension.",
                "Raising max_health also fills a newly spawned mob's health to the new maximum.",
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
                "Example: minecraft:zombie|minecraft:max_health|40",
                "Example: minecraft:zombie|minecraft:the_nether|minecraft:attack_damage|6")
                .defineListAllowEmpty("attributeOverrides", List.of(), () -> "", o -> o instanceof String);
        EXPERIENCE_MODIFIERS = b.comment(
                "Multiply the experience a mob drops: entity_id|[dimension_id]|multiplier (0 to 1000; 0 means no experience).",
                "Use * as the entity ID for every mob except players (use minecraft:player for them).",
                "Dimension defaults to every dimension, the same as writing *. The dimension is where the mob dies.",
                "Only one line applies: a line for a specific mob wins over a * line, then a line for one dimension wins",
                "over a line for every dimension.",
                "Only changes experience the mob would already drop, including bonuses from enchantments.",
                "Fractions become a chance of one more point, so 1 experience at 1.5 gives 1 or 2.",
                "Example: minecraft:zombie|minecraft:the_nether|2.5",
                "Example: minecraft:zombie|2",
                "Example: *|minecraft:the_end|0")
                .defineListAllowEmpty("experienceModifiers", List.of(), () -> "", o -> o instanceof String);
        DESPAWN_RANGES = b.comment(
                "Override natural despawn distance (in blocks from the nearest player): entity_id|range.",
                "Entities closer than range are kept loaded; entities farther are force-despawned.",
                "A line for a specific mob wins over a * line.",
                "Example: minecraft:zombie|32")
                .defineListAllowEmpty("despawnRanges", List.of(), () -> "", o -> o instanceof String);
        LIGHT_SPAWN_RESTRICTIONS = b.comment(
                "Restrict natural spawning to a light level range: entity_id|[minLight]|[maxLight] (0-15).",
                "Min light defaults to 0 and max light to 15.",
                "Cancels the spawn if the block's light level falls outside the range.",
                "Example: minecraft:zombie|0|7",
                "Example: minecraft:zombie|?|7")
                .defineListAllowEmpty("lightSpawnRestrictions", List.of(), () -> "", o -> o instanceof String);
        SPEC = b.build();
    }

    /** Rewrites lines from older versions once, so shorthand written afterwards is left as typed. */
    static void upgrade(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC || CONFIG_VERSION.get() >= CURRENT_VERSION) return;
        // Dimensions became a field after the entity in version 2.
        upgrade(ADD_SPAWNS, SpawnLines::upgradeAdd);
        upgrade(REMOVE_SPAWNS, SpawnLines::upgradeRemove);
        upgrade(ATTRIBUTE_OVERRIDES, SpawnLines::upgradeAttribute);
        CONFIG_VERSION.set(CURRENT_VERSION);
        SPEC.save();
    }

    private static void upgrade(ModConfigSpec.ConfigValue<List<? extends String>> value, UnaryOperator<String> upgrader) {
        value.set(value.get().stream().map(line -> upgrader.apply(line)).toList());
    }
}
