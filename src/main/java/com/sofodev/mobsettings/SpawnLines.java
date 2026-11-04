package com.sofodev.mobsettings;

/**
 * Lays out rule lines whose optional fields were skipped, so the server and the config screen read them the same way.
 * Optional fields can be left off the end or written as {@code ?}, and a skipped field comes back as {@code ?}.
 */
public final class SpawnLines {
    public static final String DEFAULT = "?";

    private SpawnLines() {}

    /** Whether a field asks for its default, by being {@code ?} or empty. */
    public static boolean isDefault(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.equals(DEFAULT);
    }

    /** {@code entity|[dimension]|[biome]|weight|[min]|[max]|[useSpawnRules]} */
    public static String addSpawn(String line) {
        return withLocation(line, 7);
    }

    /** {@code entity|[dimension]|[biome]|[percent]} */
    public static String removeSpawn(String line) {
        return withLocation(line, 4);
    }

    /** {@code entity|[dimension]|[biome]|min_group|[max_group]} */
    public static String groupSize(String line) {
        return withLocation(line, 5);
    }

    /** {@code entity|[dimension]|attribute|value} */
    public static String attribute(String line) {
        return pad(fieldCount(line) == 3 ? insertDimension(line, DEFAULT) : line, 4);
    }

    /** {@code entity|[dimension]|multiplier} */
    public static String experience(String line) {
        return pad(fieldCount(line) == 2 ? insertDimension(line, DEFAULT) : line, 3);
    }

    /** Fills in skipped fields at the end of a line. */
    public static String pad(String line, int fields) {
        return line + ("|" + DEFAULT).repeat(Math.max(0, fields - fieldCount(line)));
    }

    /** A lone {@code *} removal is more likely a slip than a wish to clear all spawns, so it has to be spelled out. */
    public static boolean removesEverything(String line) {
        String[] fields = removeSpawn(line).split("\\|", -1);
        return fields.length == 4 && fields[0].trim().equals("*")
                && isDefault(fields[1]) && isDefault(fields[2]) && isDefault(fields[3]);
    }

    /**
     * Dimension and biome are told apart from the numbers after them by not being numbers. A single ID is the biome,
     * which is how lines from before the dimension field was added are written.
     */
    private static String withLocation(String line, int total) {
        String[] fields = line.split("\\|", -1);
        int locations = 0;
        while (1 + locations < fields.length && !isNumber(fields[1 + locations])) locations++;
        if (locations >= 2) return pad(line, total);
        StringBuilder result = new StringBuilder(fields[0]);
        for (int i = locations; i < 2; i++) result.append('|').append(DEFAULT);
        for (int i = 1; i < fields.length; i++) result.append('|').append(fields[i]);
        return pad(result.toString(), total);
    }

    /** {@code entity|biome|weight|min|max} becomes {@code entity|*|biome|weight|min|max}. */
    static String upgradeAdd(String line) {
        return fieldCount(line) == 5 ? insertDimension(line, "*") : line;
    }

    /** {@code entity|biome} and {@code entity|biome|percent} gain a {@code *} dimension. */
    static String upgradeRemove(String line) {
        String[] fields = line.split("\\|", -1);
        boolean old = fields.length == 2 || (fields.length == 3 && isNumber(fields[2]));
        return old ? insertDimension(line, "*") : line;
    }

    /** {@code entity|attribute|value} becomes {@code entity|*|attribute|value}. */
    static String upgradeAttribute(String line) {
        return fieldCount(line) == 3 ? insertDimension(line, "*") : line;
    }

    private static int fieldCount(String line) {
        return line.split("\\|", -1).length;
    }

    private static String insertDimension(String line, String dimension) {
        int bar = line.indexOf('|');
        return line.substring(0, bar) + "|" + dimension + line.substring(bar);
    }

    private static boolean isNumber(String value) {
        try {
            Double.parseDouble(value.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
