package com.sofodev.mobsettings.client;

import com.sofodev.mobsettings.SpawnLines;
import com.sofodev.thedragonlib.client.config.RuleFormat;
import com.sofodev.thedragonlib.client.config.RuleConversions;
import com.sofodev.thedragonlib.client.config.RuleListContext;
import com.sofodev.thedragonlib.client.config.RuleType;
import com.sofodev.thedragonlib.client.config.RuleType.Field;
import com.sofodev.thedragonlib.client.config.RuleType.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Field layout of each pipe-delimited config list, for TheDragonLib's rule screens; ranges mirror the server-side parsers. */
final class MobSettingsRules {
    static final String UI_PREFIX = "mobsettings.ui.";
    static final String CONFIG_PREFIX = "mobsettings.configuration.";
    /** The {@code CUSTOM} source of dimension fields. */
    static final String DIMENSION = "dimension";
    /** Every rule starts with the entity, which the list screens group by. */
    static final int ENTITY_FIELD = 0;
    /** How many lines an entity needs before the list shows them as one group. */
    static final int GROUP_THRESHOLD = 2;

    static final RuleListContext CONTEXT = new RuleListContext(UI_PREFIX, CONFIG_PREFIX, new MobSettingsSuggestions(), new DataExtension());

    private MobSettingsRules() {}

    private static Field picker(String name, Kind kind, boolean wildcard) {
        return RuleType.picker(name, kind, wildcard);
    }

    private static Field optionalBiome() {
        return RuleType.picker("biome", Kind.BIOME, true).asOptional();
    }

    private static Field optionalDimension() {
        return new Field(DIMENSION, Kind.CUSTOM, true, true, 0, 0, false, DIMENSION, List.of());
    }

    private static Field optionalInteger(String name, double min, double max, @Nullable String fallback) {
        Field field = RuleType.integer(name, min, max).asOptional();
        return fallback == null ? field : field.withFallback(fallback);
    }

    private static Field decimal(String name, double min, double max) {
        return RuleType.decimal(name, min, max, false);
    }

    private static Field optionalDecimal(String name, double min, double max, String fallback) {
        return RuleType.decimal(name, min, max, true).withFallback(fallback);
    }

    private static Field optionalChance() {
        return RuleType.chance(true).withFallback("1");
    }

    /** Saved as T/F, which the server reads; left out while off. */
    private static Field optionalFlag(String name) {
        return RuleType.toggle(name).withToggleValues("T", "F").asOptional().withFallback("F");
    }

    private static RuleType type(String key, int minIndex, int maxIndex, Field... fields) {
        return new RuleType(key, List.of(fields), minIndex, maxIndex, new LineFormat(key));
    }

    static final Map<String, RuleType> TYPES = Map.of(
            "extraDrops", type("extraDrops", -1, -1,
                    picker("entity", Kind.ENTITY, true), picker("item", Kind.ITEM, false),
                    optionalInteger("count", 1, 100000, "1"), optionalChance(), optionalFlag("looting"), optionalDimension()),
            "removeDrops", type("removeDrops", -1, -1,
                    picker("entity", Kind.ENTITY, true), picker("item", Kind.ITEM, true), optionalChance(), optionalDimension()),
            "addSpawns", type("addSpawns", 4, 5,
                    picker("entity", Kind.SPAWNABLE_ENTITY, false), optionalDimension(), optionalBiome(),
                    RuleType.integer("weight", 1, 100000), optionalInteger("minGroup", 1, 1000, "1"),
                    optionalInteger("maxGroup", 1, 1000, null), optionalFlag("useSpawnRules")),
            "removeSpawns", type("removeSpawns", -1, -1,
                    picker("entity", Kind.SPAWNABLE_ENTITY, true), optionalDimension(), optionalBiome(),
                    optionalDecimal("percent", 0, 100, "100")),
            "spawnGroupSizes", type("spawnGroupSizes", 3, 4,
                    picker("entity", Kind.SPAWNABLE_ENTITY, true), optionalDimension(), optionalBiome(),
                    RuleType.integer("minGroup", 1, 1000), optionalInteger("maxGroup", 1, 1000, null)),
            "attributeOverrides", type("attributeOverrides", -1, -1,
                    picker("entity", Kind.ENTITY, true), optionalDimension(), picker("attribute", Kind.ATTRIBUTE, false),
                    new Field("value", Kind.ATTRIBUTE_VALUE, false, false, -Double.MAX_VALUE, Double.MAX_VALUE, false, "", List.of())),
            "damageModifiers", type("damageModifiers", -1, -1,
                    picker("entity", Kind.ENTITY, true), optionalDecimal("dealt", 0, 1000, "1"),
                    optionalDecimal("taken", 0, 1000, "1"), optionalDimension()),
            "experienceModifiers", type("experienceModifiers", -1, -1,
                    picker("entity", Kind.ENTITY, true), optionalDimension(), decimal("multiplier", 0, 1000)),
            "despawnRanges", type("despawnRanges", -1, -1,
                    picker("entity", Kind.ENTITY, true), decimal("range", 1, 1024), optionalDimension()),
            "lightSpawnRestrictions", type("lightSpawnRestrictions", 1, 2,
                    picker("entity", Kind.ENTITY, true), optionalInteger("minLight", 0, 15, "0"),
                    optionalInteger("maxLight", 0, 15, "15"), optionalDimension(), optionalBiome()));

    /** Pasting lines copied from another list: picked fields carry over, and these fill in the numbers it needs. */
    static final RuleConversions CONVERSIONS = new RuleConversions() {
        @Override
        public @Nullable RuleType type(String key) {
            return TYPES.get(key);
        }

        @Override
        public @Nullable String defaultValue(RuleType type, Field field) {
            return switch (type.key() + "." + field.name()) {
                case "addSpawns.weight" -> "10";
                case "addSpawns.minGroup", "spawnGroupSizes.minGroup" -> "1";
                case "addSpawns.maxGroup", "spawnGroupSizes.maxGroup" -> "4";
                case "experienceModifiers.multiplier" -> "1";
                case "despawnRanges.range" -> "128";
                default -> null;
            };
        }
    };

    /** The field that can carry item or entity data after its ID, or -1 for rules without one. */
    static int dataField(String key) {
        return switch (key) {
            case "extraDrops" -> 1;
            case "addSpawns" -> 0;
            default -> -1;
        };
    }

    static boolean isLocation(Field field) {
        return field.kind() == Kind.BIOME || (field.kind() == Kind.CUSTOM && field.source().equals(DIMENSION));
    }

    /**
     * Mob Settings lines: {@code ?} stands for a skipped field, and lines saved before the dimension field existed are read
     * with it in place, the same way the server reads them.
     */
    private record LineFormat(String key) implements RuleFormat {
        private String layout(String line, RuleType type) {
            return switch (key) {
                case "addSpawns" -> SpawnLines.addSpawn(line);
                case "removeSpawns" -> SpawnLines.removeSpawn(line);
                case "spawnGroupSizes" -> SpawnLines.groupSize(line);
                case "attributeOverrides" -> SpawnLines.attribute(line);
                case "experienceModifiers" -> SpawnLines.experience(line);
                default -> SpawnLines.pad(line, type.fields().size());
            };
        }

        @Override
        public boolean matches(String line, RuleType type) {
            String[] raw = SpawnLines.split(layout(line, type));
            long required = type.fields().stream().filter(f -> !f.optional()).count();
            return raw.length >= required && raw.length <= type.fields().size();
        }

        /** One value per field; a skipped optional field is empty. */
        @Override
        public String[] split(String line, RuleType type) {
            String[] parts = SpawnLines.split(layout(line, type));
            String[] values = new String[type.fields().size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = i < parts.length && !SpawnLines.isDefault(parts[i]) ? parts[i].trim() : "";
            }
            return values;
        }

        /** Empty fields in the middle become {@code ?}. Ones at the end are dropped unless that would change the meaning. */
        @Override
        public String join(String[] values, RuleType type) {
            List<String> parts = new ArrayList<>();
            for (String value : values) parts.add(value.isEmpty() ? SpawnLines.DEFAULT : value);
            while (parts.size() > 1 && values[parts.size() - 1].isEmpty() && type.fields().get(parts.size() - 1).optional()
                    && Arrays.equals(split(String.join("|", parts.subList(0, parts.size() - 1)), type), values)) {
                parts.removeLast();
            }
            return String.join("|", parts);
        }

        @Override
        public @Nullable String validate(String[] values, RuleType type, RuleListContext ctx) {
            if (key.equals("removeSpawns") && SpawnLines.removesEverything(join(values, type))) return "mobsettings.ui.error.removesEverything";
            if (key.equals("damageModifiers") && values[1].isEmpty() && values[2].isEmpty()) return "mobsettings.ui.error.noDamage";
            return null;
        }

        /** A readable sentence for a line, e.g. "Zombie drops 1 Diamond (5% chance)". */
        @Override
        public Component describe(String[] values, RuleType type, RuleListContext ctx) {
            List<Field> fields = type.fields();
            Object[] args = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                Field field = fields.get(i);
                String value = values[i].isEmpty() ? (field.isPicker() ? "*" : field.fallback()) : values[i];
                args[i] = value == null ? ""
                        : field.isPicker() ? pickerName(type, ctx, i, value)
                        : field.percent() ? RuleType.format(Double.parseDouble(value) * 100) + "%"
                        : value;
            }
            int minIndex = type.minIndex(), maxIndex = type.maxIndex();
            if (minIndex >= 0 && values[maxIndex].isEmpty() && fields.get(maxIndex).fallback() == null) args[maxIndex] = args[minIndex];
            // Some rules read differently when their last field is set. An explicit F means the same as leaving the flag out.
            // Dimension and biome only say where, so the field before them decides.
            int last = values.length - 1;
            while (last > 0 && isLocation(fields.get(last))) last--;
            String lastValue = values[last];
            boolean off = fields.get(last).kind() == Kind.TOGGLE && !fields.get(last).isOn(lastValue);
            String sentence = UI_PREFIX + "describe." + key;
            boolean variant = !lastValue.isEmpty() && !off && I18n.exists(sentence + ".optional");
            return Component.translatable(variant ? sentence + ".optional" : sentence, args);
        }

        private String pickerName(RuleType type, RuleListContext ctx, int index, String value) {
            Field field = type.fields().get(index);
            if (index != dataField(key) || SpawnLines.fieldData(value).isEmpty()) return ctx.suggestions().displayName(field, value);
            return Component.translatable("mobsettings.ui.note.withData",
                    ctx.suggestions().displayName(field, SpawnLines.fieldId(value))).getString();
        }
    }
}
