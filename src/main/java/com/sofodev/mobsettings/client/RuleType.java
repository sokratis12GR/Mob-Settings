package com.sofodev.mobsettings.client;

import com.sofodev.mobsettings.SpawnLines;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Field layout of each pipe-delimited config list; ranges mirror the server-side parsers. */
record RuleType(String key, List<Field> fields, int minIndex, int maxIndex) {
    /** ATTRIBUTE_VALUE is a decimal whose range comes from the attribute chosen in the same line. */
    enum Kind { ENTITY, SPAWNABLE_ENTITY, ITEM, DIMENSION, BIOME, ATTRIBUTE, INTEGER, DECIMAL, ATTRIBUTE_VALUE, FLAG }

    /**
     * Decimal fields with {@code percent} set are entered as 0-1 but read better as 0-100%.
     * {@code fallback} is the value an empty number field stands for, when it's a fixed one.
     */
    record Field(String name, Kind kind, boolean wildcard, boolean optional, double min, double max, boolean percent, @Nullable String fallback) {
        boolean isPicker() { return kind != Kind.INTEGER && kind != Kind.DECIMAL && kind != Kind.ATTRIBUTE_VALUE && kind != Kind.FLAG; }
    }

    private static Field picker(String name, Kind kind, boolean wildcard) {
        return new Field(name, kind, wildcard, false, 0, 0, false, null);
    }

    private static Field optionalPicker(String name, Kind kind) {
        return new Field(name, kind, true, true, 0, 0, false, null);
    }

    private static Field integer(String name, double min, double max) {
        return new Field(name, Kind.INTEGER, false, false, min, max, false, null);
    }

    private static Field optionalInteger(String name, double min, double max, @Nullable String fallback) {
        return new Field(name, Kind.INTEGER, false, true, min, max, false, fallback);
    }

    private static Field decimal(String name, double min, double max) {
        return new Field(name, Kind.DECIMAL, false, false, min, max, false, null);
    }

    private static Field optionalDecimal(String name, double min, double max, String fallback) {
        return new Field(name, Kind.DECIMAL, false, true, min, max, false, fallback);
    }

    private static Field optionalChance() {
        return new Field("chance", Kind.DECIMAL, false, true, 0, 1, true, "1");
    }

    private static Field optionalFlag(String name) {
        return new Field(name, Kind.FLAG, false, true, 0, 0, false, "F");
    }

    static final Map<String, RuleType> TYPES = Map.of(
            "extraDrops", new RuleType("extraDrops", List.of(
                    picker("entity", Kind.ENTITY, true), picker("item", Kind.ITEM, false),
                    optionalInteger("count", 1, 100000, "1"), optionalChance(), optionalFlag("looting"),
                    optionalPicker("dimension", Kind.DIMENSION)), -1, -1),
            "removeDrops", new RuleType("removeDrops", List.of(
                    picker("entity", Kind.ENTITY, true), picker("item", Kind.ITEM, true), optionalChance(),
                    optionalPicker("dimension", Kind.DIMENSION)), -1, -1),
            "addSpawns", new RuleType("addSpawns", List.of(
                    picker("entity", Kind.SPAWNABLE_ENTITY, false), optionalPicker("dimension", Kind.DIMENSION),
                    optionalPicker("biome", Kind.BIOME), integer("weight", 1, 100000),
                    optionalInteger("minGroup", 1, 1000, "1"), optionalInteger("maxGroup", 1, 1000, null),
                    optionalFlag("useSpawnRules")), 4, 5),
            "removeSpawns", new RuleType("removeSpawns", List.of(
                    picker("entity", Kind.SPAWNABLE_ENTITY, true), optionalPicker("dimension", Kind.DIMENSION),
                    optionalPicker("biome", Kind.BIOME), optionalDecimal("percent", 0, 100, "100")), -1, -1),
            "spawnGroupSizes", new RuleType("spawnGroupSizes", List.of(
                    picker("entity", Kind.SPAWNABLE_ENTITY, true), optionalPicker("dimension", Kind.DIMENSION),
                    optionalPicker("biome", Kind.BIOME), integer("minGroup", 1, 1000),
                    optionalInteger("maxGroup", 1, 1000, null)), 3, 4),
            "attributeOverrides", new RuleType("attributeOverrides", List.of(
                    picker("entity", Kind.ENTITY, true), optionalPicker("dimension", Kind.DIMENSION),
                    picker("attribute", Kind.ATTRIBUTE, false),
                    new Field("value", Kind.ATTRIBUTE_VALUE, false, false, -Double.MAX_VALUE, Double.MAX_VALUE, false, null)), -1, -1),
            "experienceModifiers", new RuleType("experienceModifiers", List.of(
                    picker("entity", Kind.ENTITY, true), optionalPicker("dimension", Kind.DIMENSION),
                    decimal("multiplier", 0, 1000)), -1, -1),
            "despawnRanges", new RuleType("despawnRanges", List.of(
                    picker("entity", Kind.ENTITY, true), decimal("range", 1, 1024),
                    optionalPicker("dimension", Kind.DIMENSION)), -1, -1),
            "lightSpawnRestrictions", new RuleType("lightSpawnRestrictions", List.of(
                    picker("entity", Kind.ENTITY, true), optionalInteger("minLight", 0, 15, "0"),
                    optionalInteger("maxLight", 0, 15, "15"), optionalPicker("dimension", Kind.DIMENSION),
                    optionalPicker("biome", Kind.BIOME)), 1, 2));

    /** The line with every field in place, the same way the server reads it. */
    private String layout(String line) {
        return switch (key) {
            case "addSpawns" -> SpawnLines.addSpawn(line);
            case "removeSpawns" -> SpawnLines.removeSpawn(line);
            case "spawnGroupSizes" -> SpawnLines.groupSize(line);
            case "attributeOverrides" -> SpawnLines.attribute(line);
            case "experienceModifiers" -> SpawnLines.experience(line);
            default -> SpawnLines.pad(line, fields.size());
        };
    }

    /** One value per field; a skipped optional field is empty. */
    String[] split(String line) {
        String[] parts = layout(line).split("\\|", -1);
        String[] values = new String[fields.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = i < parts.length && !SpawnLines.isDefault(parts[i]) ? parts[i].trim() : "";
        }
        return values;
    }

    /** Empty fields in the middle become {@code ?}. Ones at the end are dropped unless that would change the meaning. */
    String join(String[] values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) parts.add(value.isEmpty() ? SpawnLines.DEFAULT : value);
        while (parts.size() > 1 && values[parts.size() - 1].isEmpty() && fields.get(parts.size() - 1).optional()
                && Arrays.equals(split(String.join("|", parts.subList(0, parts.size() - 1))), values)) {
            parts.removeLast();
        }
        return String.join("|", parts);
    }

    /** Allowed {min, max} for a number field, or null when it has no meaningful bounds yet. */
    double @Nullable [] range(int index, String[] values) {
        Field field = fields.get(index);
        if (field.kind() == Kind.ATTRIBUTE_VALUE) return Suggestions.attributeRange(attributeValue(values));
        return field.isPicker() || field.kind() == Kind.FLAG ? null : new double[] {field.min(), field.max()};
    }

    private String attributeValue(String[] values) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).kind() == Kind.ATTRIBUTE) return values[i];
        }
        return "";
    }

    /** Returns null when valid, otherwise a translation key describing the first problem. */
    String validate(String line) {
        String[] raw = layout(line).split("\\|", -1);
        long required = fields.stream().filter(f -> !f.optional()).count();
        if (raw.length < required || raw.length > fields.size()) return "mobsettings.ui.error.fieldCount";
        String[] values = split(line);
        for (int i = 0; i < values.length; i++) {
            String error = validateField(i, values);
            if (error != null) return error;
        }
        if (minIndex >= 0 && !values[minIndex].isEmpty() && !values[maxIndex].isEmpty()
                && Double.parseDouble(values[maxIndex]) < Double.parseDouble(values[minIndex])) {
            return "mobsettings.ui.error.maxBelowMin";
        }
        if (key.equals("removeSpawns") && SpawnLines.removesEverything(line)) return "mobsettings.ui.error.removesEverything";
        return null;
    }

    @Nullable String validateField(int index, String[] values) {
        Field field = fields.get(index);
        String value = values[index];
        if (value.isEmpty()) return field.optional() ? null : "mobsettings.ui.error.required";
        if (field.isPicker()) {
            if (value.equals("*")) return field.wildcard() ? null : "mobsettings.ui.error.noWildcard";
            if (Suggestions.isSelector(field.kind(), value) && !field.wildcard()) return "mobsettings.ui.error.noWildcard";
            return Suggestions.isKnown(field.kind(), value) ? null : "mobsettings.ui.error.unknownId";
        }
        if (field.kind() == Kind.FLAG) {
            return value.equalsIgnoreCase("T") || value.equalsIgnoreCase("F") ? null : "mobsettings.ui.error.notFlag";
        }
        final double number;
        try {
            number = field.kind() == Kind.INTEGER ? Integer.parseInt(value) : Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return "mobsettings.ui.error.notNumber";
        }
        double[] range = range(index, values);
        if (!Double.isFinite(number) || (range != null && (number < range[0] || number > range[1]))) {
            return "mobsettings.ui.error.outOfRange";
        }
        return null;
    }

    /** A readable sentence for a line, e.g. "Zombie drops 1 Diamond (5% chance)"; null if the line is invalid. */
    @Nullable Component describe(String line) {
        if (validate(line) != null) return null;
        String[] values = split(line);
        Object[] args = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            Field field = fields.get(i);
            String value = values[i].isEmpty() ? (field.isPicker() ? "*" : field.fallback()) : values[i];
            args[i] = value == null ? ""
                    : field.isPicker() ? Suggestions.displayName(field.kind(), value)
                    : field.percent() ? format(Double.parseDouble(value) * 100) + "%"
                    : value;
        }
        if (minIndex >= 0 && values[maxIndex].isEmpty() && fields.get(maxIndex).fallback() == null) args[maxIndex] = args[minIndex];
        // Some rules read differently when their last field is set. An explicit F means the same as leaving the flag out.
        // Dimension and biome only say where, so the field before them decides.
        int last = values.length - 1;
        while (last > 0 && (fields.get(last).kind() == Kind.DIMENSION || fields.get(last).kind() == Kind.BIOME)) last--;
        String lastValue = values[last];
        boolean off = fields.get(last).kind() == Kind.FLAG && lastValue.equalsIgnoreCase("F");
        String sentence = "mobsettings.ui.describe." + key;
        boolean variant = !lastValue.isEmpty() && !off && I18n.exists(sentence + ".optional");
        return Component.translatable(variant ? sentence + ".optional" : sentence, args);
    }

    static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return String.valueOf((long) value);
        return String.valueOf(Math.round(value * 10000) / 10000.0);
    }
}
