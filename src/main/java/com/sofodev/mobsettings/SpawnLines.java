package com.sofodev.mobsettings;

/**
 * Spawn rules gained a dimension field after the biome. Lines in the old format are read as applying to every
 * dimension; both the server and the config screen upgrade them through here so they agree.
 */
public final class SpawnLines {
    private SpawnLines() {}

    /** {@code entity|biome|weight|min|max} becomes {@code entity|*|biome|weight|min|max}. */
    public static String upgradeAdd(String line) {
        return line.split("\\|", -1).length == 5 ? insertDimension(line) : line;
    }

    /** {@code entity|biome} and {@code entity|biome|percent} gain a {@code *} dimension. */
    public static String upgradeRemove(String line) {
        String[] fields = line.split("\\|", -1);
        boolean old = fields.length == 2 || (fields.length == 3 && isNumber(fields[2]));
        return old ? insertDimension(line) : line;
    }

    private static String insertDimension(String line) {
        int bar = line.indexOf('|');
        return line.substring(0, bar) + "|*" + line.substring(bar);
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
