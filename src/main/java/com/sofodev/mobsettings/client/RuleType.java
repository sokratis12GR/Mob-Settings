package com.sofodev.mobsettings.client;

import com.sofodev.mobsettings.SpawnLines;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Field layout of each pipe-delimited config list; ranges mirror the server-side parsers. */
record RuleType(String key, List<Field> fields, int minIndex, int maxIndex) {
    /** ATTRIBUTE_VALUE is a decimal whose range comes from the attribute chosen in the same line. */
    enum Kind { ENTITY, SPAWNABLE_ENTITY, ITEM, DIMENSION, BIOME, ATTRIBUTE, INTEGER, DECIMAL, ATTRIBUTE_VALUE }

    /** Decimal fields with {@code percent} set are entered as 0-1 but read better as 0-100%. */
    record Field(String name, Kind kind, boolean wildcard, boolean optional, double min, double max, boolean percent) {
        boolean isPicker() { return kind != Kind.INTEGER && kind != Kind.DECIMAL && kind != Kind.ATTRIBUTE_VALUE; }
    }

    private static Field picker(String name, Kind kind, boolean wildcard) {
        return new Field(name, kind, wildcard, false, 0, 0, false);
    }

    private static Field integer(String name, double min, double max) {
        return new Field(name, Kind.INTEGER, false, false, min, max, false);
    }

    private static Field decimal(String name, double min, double max, boolean optional) {
        return new Field(name, Kind.DECIMAL, false, optional, min, max, false);
    }

    private static Field chance(boolean optional) {
        return new Field("chance", Kind.DECIMAL, false, optional, 0, 1, true);
    }

    static final Map<String, RuleType> TYPES = Map.of(
            "extraDrops", new RuleType("extraDrops", List.of(
                    picker("entity", Kind.ENTITY, true), picker("item", Kind.ITEM, false),
                    integer("count", 1, 100000), chance(false)), -1, -1),
            "removeDrops", new RuleType("removeDrops", List.of(
                    picker("entity", Kind.ENTITY, true), picker("item", Kind.ITEM, true), chance(true)), -1, -1),
            "addSpawns", new RuleType("addSpawns", List.of(
                    picker("entity", Kind.SPAWNABLE_ENTITY, false), picker("dimension", Kind.DIMENSION, true),
                    picker("biome", Kind.BIOME, true),
                    integer("weight", 1, 100000), integer("minGroup", 1, 1000), integer("maxGroup", 1, 1000)), 4, 5),
            "removeSpawns", new RuleType("removeSpawns", List.of(
                    picker("entity", Kind.SPAWNABLE_ENTITY, true), picker("dimension", Kind.DIMENSION, true),
                    picker("biome", Kind.BIOME, true), decimal("percent", 0, 100, true)), -1, -1),
            "attributeOverrides", new RuleType("attributeOverrides", List.of(
                    picker("entity", Kind.ENTITY, true), picker("attribute", Kind.ATTRIBUTE, false),
                    new Field("value", Kind.ATTRIBUTE_VALUE, false, false, -Double.MAX_VALUE, Double.MAX_VALUE, false)), -1, -1),
            "despawnRanges", new RuleType("despawnRanges", List.of(
                    picker("entity", Kind.ENTITY, true), decimal("range", 1, 1024, false)), -1, -1),
            "lightSpawnRestrictions", new RuleType("lightSpawnRestrictions", List.of(
                    picker("entity", Kind.ENTITY, true), integer("minLight", 0, 15), integer("maxLight", 0, 15)), 1, 2));

    /** Lines saved before a field was added, rewritten into the current layout. */
    String upgrade(String line) {
        return switch (key) {
            case "addSpawns" -> SpawnLines.upgradeAdd(line);
            case "removeSpawns" -> SpawnLines.upgradeRemove(line);
            default -> line;
        };
    }

    String[] split(String line) {
        String[] parts = upgrade(line).split("\\|", -1);
        String[] values = new String[fields.size()];
        for (int i = 0; i < values.length; i++) values[i] = i < parts.length ? parts[i].trim() : "";
        return values;
    }

    String join(String[] values) {
        List<String> parts = new ArrayList<>(List.of(values));
        while (!parts.isEmpty() && parts.getLast().isEmpty() && fields.get(parts.size() - 1).optional()) parts.removeLast();
        return String.join("|", parts);
    }

    /** Allowed {min, max} for a number field, or null when it has no meaningful bounds yet. */
    double @Nullable [] range(int index, String[] values) {
        Field field = fields.get(index);
        if (field.kind() == Kind.ATTRIBUTE_VALUE) return Suggestions.attributeRange(attributeValue(values));
        return field.isPicker() ? null : new double[] {field.min(), field.max()};
    }

    private String attributeValue(String[] values) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).kind() == Kind.ATTRIBUTE) return values[i];
        }
        return "";
    }

    /** Returns null when valid, otherwise a translation key describing the first problem. */
    String validate(String line) {
        String[] raw = upgrade(line).split("\\|", -1);
        long required = fields.stream().filter(f -> !f.optional()).count();
        if (raw.length < required || raw.length > fields.size()) return "mobsettings.ui.error.fieldCount";
        String[] values = split(line);
        for (int i = 0; i < values.length; i++) {
            String error = validateField(i, values);
            if (error != null) return error;
        }
        if (minIndex >= 0 && Double.parseDouble(values[maxIndex]) < Double.parseDouble(values[minIndex])) {
            return "mobsettings.ui.error.maxBelowMin";
        }
        return null;
    }

    @Nullable String validateField(int index, String[] values) {
        Field field = fields.get(index);
        String value = values[index];
        if (value.isEmpty()) return field.optional() ? null : "mobsettings.ui.error.required";
        if (field.isPicker()) {
            if (value.equals("*")) return field.wildcard() ? null : "mobsettings.ui.error.noWildcard";
            return Suggestions.isKnown(field.kind(), value) ? null : "mobsettings.ui.error.unknownId";
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
        boolean hasOptional = false;
        for (int i = 0; i < values.length; i++) {
            Field field = fields.get(i);
            if (field.optional() && !values[i].isEmpty()) hasOptional = true;
            args[i] = field.isPicker() ? Suggestions.displayName(field.kind(), values[i])
                    : field.percent() && !values[i].isEmpty() ? format(Double.parseDouble(values[i]) * 100) + "%"
                    : values[i];
        }
        return Component.translatable("mobsettings.ui.describe." + key + (hasOptional ? ".optional" : ""), args);
    }

    static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return String.valueOf((long) value);
        return String.valueOf(Math.round(value * 10000) / 10000.0);
    }
}
