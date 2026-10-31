package com.sofodev.mobsettings;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Parsed config rules. Each list is parsed the first time it's used after a config (re)load, and invalid lines are
 * logged once and skipped. A null entity, item or dimension means the rule used {@code *}.
 */
final class Rules {
    private static final Logger LOGGER = LogUtils.getLogger();

    record RemoveDrop(@Nullable EntityType<?> entity, @Nullable Item item, double chance) {}
    record ExtraDrop(@Nullable EntityType<?> entity, Item item, int count, double chance) {}
    record RemoveSpawn(@Nullable EntityType<?> entity, @Nullable ResourceLocation dimension, BiomeRule biome, double keep) {}
    record AddSpawn(EntityType<?> entity, @Nullable ResourceLocation dimension, BiomeRule biome, int weight, int min, int max) {}
    record AttributeOverride(@Nullable EntityType<?> entity, Holder<Attribute> attribute, double value) {}
    record DespawnRange(@Nullable EntityType<?> entity, double range) {}
    record LightRestriction(@Nullable EntityType<?> entity, int min, int max) {}

    /** A biome ID, a {@code #tag}, or {@code *}. */
    record BiomeRule(@Nullable ResourceLocation id, @Nullable TagKey<Biome> tag) {
        boolean matches(Holder<Biome> biome) {
            if (tag != null) return biome.is(tag);
            return id == null || biome.is(id);
        }
    }

    static final Cache<RemoveDrop> REMOVE_DROPS = new Cache<>("removeDrops", MSConfig.REMOVE_DROPS, line -> {
        String[] f = fields(line, 2, 3);
        Item item = f[1].equals("*") ? null : registered(BuiltInRegistries.ITEM, f[1], "item");
        return new RemoveDrop(entity(f[0]), item, f.length == 3 ? decimal(f[2], "chance", 0, 1) : 1);
    });
    static final Cache<ExtraDrop> EXTRA_DROPS = new Cache<>("extraDrops", MSConfig.EXTRA_DROPS, line -> {
        String[] f = fields(line, 4, 4);
        return new ExtraDrop(entity(f[0]), registered(BuiltInRegistries.ITEM, f[1], "item"),
                integer(f[2], "count", 1, 100000), decimal(f[3], "chance", 0, 1));
    });
    static final Cache<RemoveSpawn> REMOVE_SPAWNS = new Cache<>("removeSpawns", MSConfig.REMOVE_SPAWNS, line -> {
        String[] f = fields(SpawnLines.upgradeRemove(line), 3, 4);
        double percent = f.length == 4 ? decimal(f[3], "percent", 0, 100) : 100;
        return new RemoveSpawn(entity(f[0]), dimension(f[1]), biome(f[2]), 1 - percent / 100);
    });
    static final Cache<AddSpawn> ADD_SPAWNS = new Cache<>("addSpawns", MSConfig.ADD_SPAWNS, line -> {
        String[] f = fields(SpawnLines.upgradeAdd(line), 6, 6);
        EntityType<?> type = registered(BuiltInRegistries.ENTITY_TYPE, f[0], "entity");
        // Vanilla's SpawnerData ctor silently swaps MISC-category types to PIG; reject instead of spawning the wrong mob.
        if (type.getCategory() == MobCategory.MISC) throw new Invalid(f[0] + " can't spawn naturally");
        int min = integer(f[4], "min group", 1, 1000);
        int max = integer(f[5], "max group", 1, 1000);
        if (max < min) throw new Invalid("max group is lower than min group");
        return new AddSpawn(type, dimension(f[1]), biome(f[2]), integer(f[3], "weight", 1, 100000), min, max);
    });
    static final Cache<AttributeOverride> ATTRIBUTE_OVERRIDES = new Cache<>("attributeOverrides", MSConfig.ATTRIBUTE_OVERRIDES, line -> {
        String[] f = fields(line, 3, 3);
        Attribute attribute = registered(BuiltInRegistries.ATTRIBUTE, f[1], "attribute");
        return new AttributeOverride(entity(f[0]), BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute),
                decimal(f[2], "value", -Double.MAX_VALUE, Double.MAX_VALUE));
    });
    static final Cache<DespawnRange> DESPAWN_RANGES = new Cache<>("despawnRanges", MSConfig.DESPAWN_RANGES, line -> {
        String[] f = fields(line, 2, 2);
        double range = decimal(f[1], "range", 0, Double.MAX_VALUE);
        if (range <= 0) throw new Invalid("range must be above 0");
        return new DespawnRange(entity(f[0]), range);
    });
    static final Cache<LightRestriction> LIGHT_SPAWN_RESTRICTIONS = new Cache<>("lightSpawnRestrictions", MSConfig.LIGHT_SPAWN_RESTRICTIONS, line -> {
        String[] f = fields(line, 3, 3);
        int min = integer(f[1], "min light", 0, 15);
        int max = integer(f[2], "max light", 0, 15);
        if (max < min) throw new Invalid("max light is lower than min light");
        return new LightRestriction(entity(f[0]), min, max);
    });

    private Rules() {}

    static boolean matches(@Nullable EntityType<?> rule, EntityType<?> actual) { return rule == null || rule == actual; }
    static boolean matches(@Nullable ResourceLocation rule, ResourceLocation actual) { return rule == null || rule.equals(actual); }

    private static String[] fields(String line, int min, int max) {
        String[] fields = line.split("\\|", -1);
        if (fields.length < min || fields.length > max) {
            throw new Invalid(min == max ? "expected " + min + " fields" : "expected " + min + " to " + max + " fields");
        }
        for (int i = 0; i < fields.length; i++) fields[i] = fields[i].trim();
        return fields;
    }

    private static ResourceLocation id(String value, String what) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new Invalid("\"" + value + "\" is not a valid " + what + " ID");
        return id;
    }

    private static <T> T registered(Registry<T> registry, String value, String what) {
        ResourceLocation id = id(value, what);
        if (!registry.containsKey(id)) throw new Invalid("unknown " + what + " " + value);
        return registry.get(id);
    }

    private static @Nullable EntityType<?> entity(String value) {
        return value.equals("*") ? null : registered(BuiltInRegistries.ENTITY_TYPE, value, "entity");
    }

    // Dimensions and biomes are datapack-driven and may not exist yet when this is parsed, so only the syntax is checked.
    private static @Nullable ResourceLocation dimension(String value) {
        return value.equals("*") ? null : id(value, "dimension");
    }

    private static BiomeRule biome(String value) {
        if (value.equals("*")) return new BiomeRule(null, null);
        if (value.startsWith("#")) return new BiomeRule(null, TagKey.create(Registries.BIOME, id(value.substring(1), "biome tag")));
        return new BiomeRule(id(value, "biome"), null);
    }

    private static int integer(String value, String name, int min, int max) {
        final int number;
        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new Invalid(name + " \"" + value + "\" is not a whole number");
        }
        if (number < min || number > max) throw new Invalid(name + " must be between " + min + " and " + max);
        return number;
    }

    private static double decimal(String value, String name, double min, double max) {
        final double number;
        try {
            number = Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new Invalid(name + " \"" + value + "\" is not a number");
        }
        if (!Double.isFinite(number) || number < min || number > max) {
            throw new Invalid(name + " must be between " + min + " and " + max);
        }
        return number;
    }

    private static final class Invalid extends RuntimeException {
        Invalid(String message) { super(message, null, false, false); }
    }

    static final class Cache<T> {
        private final String key;
        private final ModConfigSpec.ConfigValue<List<? extends String>> value;
        private final Function<String, T> parser;
        private @Nullable List<? extends String> source;
        private List<T> parsed = List.of();

        Cache(String key, ModConfigSpec.ConfigValue<List<? extends String>> value, Function<String, T> parser) {
            this.key = key;
            this.value = value;
            this.parser = parser;
        }

        /** The valid rules in config order. Cheap to call: config values are cached until the file reloads. */
        synchronized List<T> get() {
            List<? extends String> current = value.get();
            if (current == source) return parsed;
            if (!current.equals(source)) {
                List<T> result = new ArrayList<>();
                for (String line : current) {
                    if (line.isBlank()) continue;
                    try {
                        result.add(parser.apply(line));
                    } catch (Invalid ex) {
                        LOGGER.warn("Ignoring {} entry \"{}\": {}", key, line, ex.getMessage());
                    }
                }
                parsed = List.copyOf(result);
            }
            source = current;
            return parsed;
        }
    }
}
