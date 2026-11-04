package com.sofodev.mobsettings;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Parsed config rules. Each list is parsed the first time it's used after a config (re)load, and invalid lines are
 * logged once and skipped. A null item or dimension means the rule used {@code *}, and so does an {@link EntityRule} with nothing set.
 */
final class Rules {
    private static final Logger LOGGER = LogUtils.getLogger();

    record RemoveDrop(EntityRule entity, @Nullable Item item, double chance, @Nullable Identifier dimension) {}
    record ExtraDrop(EntityRule entity, Item item, int count, double chance, boolean looting, @Nullable Identifier dimension) {}
    record RemoveSpawn(EntityRule entity, @Nullable Identifier dimension, BiomeRule biome, double keep) {}
    record AddSpawn(EntityType<?> entity, @Nullable Identifier dimension, BiomeRule biome, int weight, int min, int max, boolean useSpawnRules) {}
    record GroupSize(EntityRule entity, @Nullable Identifier dimension, BiomeRule biome, int min, int max) {}
    record DamageModifier(EntityRule entity, @Nullable Double dealt, @Nullable Double taken, @Nullable Identifier dimension) {}
    record AttributeOverride(EntityRule entity, @Nullable Identifier dimension, Holder<Attribute> attribute, double value) {}
    record ExperienceModifier(EntityRule entity, @Nullable Identifier dimension, double multiplier) {}
    record DespawnRange(EntityRule entity, double range, @Nullable Identifier dimension) {}
    record LightRestriction(EntityRule entity, int min, int max, @Nullable Identifier dimension, BiomeRule biome) {}

    /**
     * Which entities a rule covers: one entity type, an {@code #entity_type_tag}, every entity of a mod
     * ({@code modid:*}), or all of them ({@code *}, all fields null).
     */
    record EntityRule(@Nullable EntityType<?> type, @Nullable TagKey<EntityType<?>> tag, @Nullable String namespace) {
        static final EntityRule ANY = new EntityRule(null, null, null);

        boolean matches(EntityType<?> actual) {
            if (type != null) return type == actual;
            if (tag != null) return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(actual).is(tag);
            return namespace == null || namespace.equals(BuiltInRegistries.ENTITY_TYPE.getKey(actual).getNamespace());
        }

        /** Players only match a rule that names them, never a tag, mod or {@code *}. */
        boolean matchesMob(EntityType<?> actual) {
            return actual == EntityTypes.PLAYER ? type == actual : matches(actual);
        }

        /** How narrowly the rule aims, for picking between rules: a named entity beats a tag, a tag beats a mod, a mod beats {@code *}. */
        int specificity() {
            return type != null ? 3 : tag != null ? 2 : namespace != null ? 1 : 0;
        }
    }

    /** A biome ID, a {@code #tag}, or {@code *}. */
    record BiomeRule(@Nullable Identifier id, @Nullable TagKey<Biome> tag) {
        boolean matches(Holder<Biome> biome) {
            if (tag != null) return biome.is(tag);
            return id == null || biome.is(id);
        }
    }

    static final Cache<RemoveDrop> REMOVE_DROPS = new Cache<>("removeDrops", MSConfig.REMOVE_DROPS, line -> {
        String[] f = fields(SpawnLines.pad(line, 4), 4, 4);
        Item item = f[1].equals("*") ? null : registered(BuiltInRegistries.ITEM, f[1], "item");
        return new RemoveDrop(entity(f[0]), item, SpawnLines.isDefault(f[2]) ? 1 : decimal(f[2], "chance", 0, 1), dimension(f[3]));
    });
    static final Cache<ExtraDrop> EXTRA_DROPS = new Cache<>("extraDrops", MSConfig.EXTRA_DROPS, line -> {
        String[] f = fields(SpawnLines.pad(line, 6), 6, 6);
        return new ExtraDrop(entity(f[0]), registered(BuiltInRegistries.ITEM, f[1], "item"),
                SpawnLines.isDefault(f[2]) ? 1 : integer(f[2], "count", 1, 100000),
                SpawnLines.isDefault(f[3]) ? 1 : decimal(f[3], "chance", 0, 1),
                !SpawnLines.isDefault(f[4]) && flag(f[4], "looting"), dimension(f[5]));
    });
    static final Cache<RemoveSpawn> REMOVE_SPAWNS = new Cache<>("removeSpawns", MSConfig.REMOVE_SPAWNS, line -> {
        String[] f = fields(SpawnLines.removeSpawn(line), 4, 4);
        if (SpawnLines.removesEverything(line)) throw new Invalid("this would remove every spawn; write *|*|* if that's intended");
        double percent = SpawnLines.isDefault(f[3]) ? 100 : decimal(f[3], "percent", 0, 100);
        return new RemoveSpawn(entity(f[0]), dimension(f[1]), biome(f[2]), 1 - percent / 100);
    });
    static final Cache<AddSpawn> ADD_SPAWNS = new Cache<>("addSpawns", MSConfig.ADD_SPAWNS, line -> {
        String[] f = fields(SpawnLines.addSpawn(line), 7, 7);
        EntityType<?> type = registered(BuiltInRegistries.ENTITY_TYPE, f[0], "entity");
        // Vanilla's SpawnerData ctor silently swaps MISC-category types to PIG; reject instead of spawning the wrong mob.
        if (type.getCategory() == MobCategory.MISC) throw new Invalid(f[0] + " can't spawn naturally");
        int min = SpawnLines.isDefault(f[4]) ? 1 : integer(f[4], "min group", 1, 1000);
        int max = SpawnLines.isDefault(f[5]) ? min : integer(f[5], "max group", 1, 1000);
        if (max < min) throw new Invalid("max group is lower than min group");
        return new AddSpawn(type, dimension(f[1]), biome(f[2]), integer(f[3], "weight", 1, 100000), min, max,
                !SpawnLines.isDefault(f[6]) && flag(f[6], "use spawn rules"));
    });
    static final Cache<GroupSize> GROUP_SIZES = new Cache<>("spawnGroupSizes", MSConfig.GROUP_SIZES, line -> {
        String[] f = fields(SpawnLines.groupSize(line), 5, 5);
        int min = integer(f[3], "min group", 1, 1000);
        int max = SpawnLines.isDefault(f[4]) ? min : integer(f[4], "max group", 1, 1000);
        if (max < min) throw new Invalid("max group is lower than min group");
        return new GroupSize(entity(f[0]), dimension(f[1]), biome(f[2]), min, max);
    });
    static final Cache<AttributeOverride> ATTRIBUTE_OVERRIDES = new Cache<>("attributeOverrides", MSConfig.ATTRIBUTE_OVERRIDES, line -> {
        String[] f = fields(SpawnLines.attribute(line), 4, 4);
        Attribute attribute = registered(BuiltInRegistries.ATTRIBUTE, f[2], "attribute");
        return new AttributeOverride(entity(f[0]), dimension(f[1]), BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute),
                decimal(f[3], "value", -Double.MAX_VALUE, Double.MAX_VALUE));
    });
    static final Cache<DamageModifier> DAMAGE_MODIFIERS = new Cache<>("damageModifiers", MSConfig.DAMAGE_MODIFIERS, line -> {
        String[] f = fields(SpawnLines.pad(line, 4), 4, 4);
        Double dealt = SpawnLines.isDefault(f[1]) ? null : decimal(f[1], "dealt", 0, 1000);
        Double taken = SpawnLines.isDefault(f[2]) ? null : decimal(f[2], "taken", 0, 1000);
        if (dealt == null && taken == null) throw new Invalid("sets neither damage dealt nor damage taken");
        return new DamageModifier(entity(f[0]), dealt, taken, dimension(f[3]));
    });
    static final Cache<ExperienceModifier> EXPERIENCE_MODIFIERS = new Cache<>("experienceModifiers", MSConfig.EXPERIENCE_MODIFIERS, line -> {
        String[] f = fields(SpawnLines.experience(line), 3, 3);
        return new ExperienceModifier(entity(f[0]), dimension(f[1]), decimal(f[2], "multiplier", 0, 1000));
    });
    static final Cache<DespawnRange> DESPAWN_RANGES = new Cache<>("despawnRanges", MSConfig.DESPAWN_RANGES, line -> {
        String[] f = fields(SpawnLines.pad(line, 3), 3, 3);
        double range = decimal(f[1], "range", 0, Double.MAX_VALUE);
        if (range <= 0) throw new Invalid("range must be above 0");
        return new DespawnRange(entity(f[0]), range, dimension(f[2]));
    });
    static final Cache<LightRestriction> LIGHT_SPAWN_RESTRICTIONS = new Cache<>("lightSpawnRestrictions", MSConfig.LIGHT_SPAWN_RESTRICTIONS, line -> {
        String[] f = fields(SpawnLines.pad(line, 5), 5, 5);
        int min = SpawnLines.isDefault(f[1]) ? 0 : integer(f[1], "min light", 0, 15);
        int max = SpawnLines.isDefault(f[2]) ? 15 : integer(f[2], "max light", 0, 15);
        if (max < min) throw new Invalid("max light is lower than min light");
        return new LightRestriction(entity(f[0]), min, max, dimension(f[3]), biome(f[4]));
    });

    private Rules() {}

    static boolean matches(EntityRule rule, EntityType<?> actual) { return rule.matches(actual); }
    /** Like {@link #matches(EntityRule, EntityType)}, but players only match a rule that names them. */
    static boolean matchesMob(EntityRule rule, EntityType<?> actual) { return rule.matchesMob(actual); }
    static boolean matches(@Nullable Identifier rule, Identifier actual) { return rule == null || rule.equals(actual); }

    private static boolean flag(String value, String name) {
        if (value.equalsIgnoreCase("T")) return true;
        if (value.equalsIgnoreCase("F")) return false;
        throw new Invalid(name + " must be T or F");
    }

    private static String[] fields(String line, int min, int max) {
        String[] fields = line.split("\\|", -1);
        if (fields.length < min || fields.length > max) {
            throw new Invalid(min == max ? "expected " + min + " fields" : "expected " + min + " to " + max + " fields");
        }
        for (int i = 0; i < fields.length; i++) fields[i] = fields[i].trim();
        return fields;
    }

    private static Identifier id(String value, String what) {
        Identifier id = Identifier.tryParse(value);
        if (id == null) throw new Invalid("\"" + value + "\" is not a valid " + what + " ID");
        return id;
    }

    private static <T> T registered(Registry<T> registry, String value, String what) {
        Identifier id = id(value, what);
        if (!registry.containsKey(id)) throw new Invalid("unknown " + what + " " + value);
        return registry.getValue(id);
    }

    private static EntityRule entity(String value) {
        if (value.equals("*")) return EntityRule.ANY;
        if (value.startsWith("#")) {
            // Tags are datapack-driven and may not exist yet when this is parsed, so only the syntax is checked.
            return new EntityRule(null, TagKey.create(Registries.ENTITY_TYPE, id(value.substring(1), "entity tag")), null);
        }
        if (value.endsWith(":*")) {
            String namespace = value.substring(0, value.length() - 2);
            id(namespace + ":x", "mod");
            if (BuiltInRegistries.ENTITY_TYPE.keySet().stream().noneMatch(key -> key.getNamespace().equals(namespace))) {
                throw new Invalid("no entities from " + namespace);
            }
            return new EntityRule(null, null, namespace);
        }
        return new EntityRule(registered(BuiltInRegistries.ENTITY_TYPE, value, "entity"), null, null);
    }

    // Dimensions and biomes are datapack-driven and may not exist yet when this is parsed, so only the syntax is checked.
    private static @Nullable Identifier dimension(String value) {
        return value.equals("*") || SpawnLines.isDefault(value) ? null : id(value, "dimension");
    }

    private static BiomeRule biome(String value) {
        if (value.equals("*") || SpawnLines.isDefault(value)) return new BiomeRule(null, null);
        if (value.startsWith("#")) return new BiomeRule(null, TagKey.create(Registries.BIOME, id(value.substring(1), "biome tag")));
        Identifier id = id(value, "biome");
        // A single ID in a spawn line is the biome. The End has a biome named after it, so only these two are mistakes.
        if (id.equals(Level.OVERWORLD.identifier()) || id.equals(Level.NETHER.identifier())) {
            LOGGER.warn("Spawn rule uses the dimension {} as a biome, so it matches nothing. To target the dimension, write entity|{}|?", id, id);
        }
        return new BiomeRule(id, null);
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
