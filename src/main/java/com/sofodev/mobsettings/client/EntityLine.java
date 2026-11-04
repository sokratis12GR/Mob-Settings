package com.sofodev.mobsettings.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * An entity's data as the editor sees it, read from and written back to {@code {key:value,...}}.
 * Everything without a dedicated input stays in {@link #other}, so nothing is lost by editing.
 */
final class EntityLine {
    static final List<String> SLOTS = List.of("head", "chest", "legs", "feet", "mainhand", "offhand", "body");
    private static final String EQUIPMENT = "equipment";
    private static final String DROP_CHANCES = "drop_chances";
    private static final String NAME = "CustomName";
    private static final String PERSISTENT = "PersistenceRequired";

    /** Item text per slot, in the same form as a drop's item field. */
    final Map<String, String> items = new HashMap<>();
    final Map<String, String> chances = new HashMap<>();
    /** Stacks above 1 have no input, but survive an edit as long as the slot isn't replaced. */
    final Map<String, Integer> counts = new HashMap<>();
    String name = "";
    boolean persistent;
    String other = "";

    /** Reads the {@code {...}} part of a field, or returns null when it isn't valid SNBT. Empty means no data. */
    static @Nullable EntityLine parse(String data) {
        EntityLine line = new EntityLine();
        if (data.isBlank()) return line;
        CompoundTag tag;
        try {
            tag = TagParser.parseTag(data);
        } catch (CommandSyntaxException ex) {
            return null;
        }
        if (tag.contains(EQUIPMENT, Tag.TAG_COMPOUND)) {
            CompoundTag equipment = tag.getCompound(EQUIPMENT);
            for (String slot : SLOTS) {
                if (!equipment.contains(slot, Tag.TAG_COMPOUND)) continue;
                CompoundTag saved = equipment.getCompound(slot);
                ItemLine item = ItemLine.fromTag(saved);
                if (item == null) continue;
                line.items.put(slot, item.text());
                if (saved.getInt("count") > 1) line.counts.put(slot, saved.getInt("count"));
                equipment.remove(slot);
            }
            if (equipment.isEmpty()) tag.remove(EQUIPMENT);
        }
        if (tag.contains(DROP_CHANCES, Tag.TAG_COMPOUND)) {
            CompoundTag chances = tag.getCompound(DROP_CHANCES);
            for (String slot : SLOTS) {
                if (!chances.contains(slot, Tag.TAG_ANY_NUMERIC)) continue;
                line.chances.put(slot, RuleType.format(chances.getFloat(slot)));
                chances.remove(slot);
            }
            if (chances.isEmpty()) tag.remove(DROP_CHANCES);
        }
        String name = tag.contains(NAME, Tag.TAG_STRING) ? ItemLine.nameFromJson(tag.getString(NAME)) : null;
        if (name != null) {
            line.name = name;
            tag.remove(NAME);
        }
        if (tag.getBoolean(PERSISTENT)) {
            line.persistent = true;
            tag.remove(PERSISTENT);
        }
        if (!tag.isEmpty()) line.other = tag.toString();
        return line;
    }

    /** The {@code {...}} text of a valid line, or an empty string when there's no data. */
    String text() throws CommandSyntaxException {
        CompoundTag tag = new CompoundTag();
        CompoundTag equipment = new CompoundTag();
        for (String slot : SLOTS) {
            String text = items.get(slot);
            if (text != null && !text.isBlank()) equipment.put(slot, ItemLine.parse(text).toTag(counts.getOrDefault(slot, 1)));
        }
        if (!equipment.isEmpty()) tag.put(EQUIPMENT, equipment);
        CompoundTag dropChances = new CompoundTag();
        for (String slot : SLOTS) {
            String chance = chances.get(slot);
            if (chance != null && !chance.isBlank()) dropChances.putFloat(slot, Float.parseFloat(chance.trim()));
        }
        if (!dropChances.isEmpty()) tag.put(DROP_CHANCES, dropChances);
        if (!name.isEmpty()) tag.putString(NAME, ItemLine.nameToJson(name));
        if (persistent) tag.putBoolean(PERSISTENT, true);
        if (!other.isBlank()) tag.merge(TagParser.parseTag(other));
        return tag.isEmpty() ? "" : tag.toString();
    }

    /** What's wrong with the line, ready to show, or null. */
    @Nullable String problem() {
        for (String slot : SLOTS) {
            Component label = Component.translatable("mobsettings.ui.slot." + slot);
            String text = items.get(slot);
            if (text != null && !text.isBlank()) {
                ItemLine item = ItemLine.parse(text);
                String problem = item == null ? Component.translatable("mobsettings.ui.error.badData").getString() : item.problem();
                if (problem != null) return label.getString() + ": " + problem;
            }
            String chance = chances.get(slot);
            if (chance != null && !chance.isBlank() && !validChance(chance)) {
                return label.getString() + ": " + Component.translatable("mobsettings.ui.data.error.chance").getString();
            }
        }
        if (!other.isBlank()) {
            try {
                TagParser.parseTag(other);
            } catch (CommandSyntaxException ex) {
                return ex.getMessage();
            }
        }
        return null;
    }

    private static boolean validChance(String value) {
        try {
            float chance = Float.parseFloat(value.trim());
            return chance >= 0 && chance <= 2;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /** The data in text copied from elsewhere, such as a rule's entity field or a whole {@code /summon} command. */
    static String fromClipboard(String text) {
        int start = text.indexOf('{');
        return start < 0 ? text.trim() : text.substring(start).trim();
    }
}
