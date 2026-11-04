package com.sofodev.mobsettings.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sofodev.mobsettings.SpawnLines;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * An item with its data as the editor sees it, read from and written back to {@code id[component=value,...]}.
 * Components without a dedicated input are kept as text, so anything the editor doesn't understand survives an edit.
 */
final class ItemLine {
    /** One editable pair. Removed components ({@code !id}) have no value. */
    static final class Entry {
        String key;
        String value;

        Entry(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    static final String DAMAGE = "minecraft:damage";
    static final String NAME = "minecraft:custom_name";
    static final String UNBREAKABLE = "minecraft:unbreakable";
    static final String ENCHANTMENTS = "minecraft:enchantments";

    String item = "";
    String damage = "";
    String name = "";
    boolean unbreakable;
    final List<Entry> enchantments = new ArrayList<>();
    final List<Entry> others = new ArrayList<>();

    /** Reads a field, or returns null when the brackets don't make sense. An empty field is an item not chosen yet. */
    static @Nullable ItemLine parse(String text) {
        ItemLine line = new ItemLine();
        line.item = SpawnLines.fieldId(text);
        String data = SpawnLines.fieldData(text);
        if (data.isEmpty()) return line;
        if (!data.startsWith("[") || !data.endsWith("]")) return null;
        for (String part : SpawnLines.splitComponents(data.substring(1, data.length() - 1))) {
            String component = part.trim();
            if (component.isEmpty()) continue;
            if (component.startsWith("!")) {
                line.others.add(new Entry("!" + fullId(component.substring(1)), ""));
                continue;
            }
            int equals = component.indexOf('=');
            if (equals < 0) return null;
            line.read(fullId(component.substring(0, equals).trim()), component.substring(equals + 1).trim());
        }
        return line;
    }

    /** Fills the dedicated input for a component if its value fits one. */
    private void read(String key, String value) {
        CompoundTag tag = wrapped(value);
        if (tag != null) {
            if (key.equals(DAMAGE) && tag.contains("v", Tag.TAG_ANY_NUMERIC)) {
                damage = String.valueOf(tag.getInt("v"));
                return;
            }
            String text = key.equals(NAME) && tag.contains("v", Tag.TAG_STRING) ? nameFromJson(tag.getString("v")) : null;
            if (text != null) {
                name = text;
                return;
            }
            if (key.equals(UNBREAKABLE) && tag.contains("v", Tag.TAG_COMPOUND) && tag.getCompound("v").isEmpty()) {
                unbreakable = true;
                return;
            }
            CompoundTag levels = tag.getCompound("v");
            if (key.equals(ENCHANTMENTS) && tag.contains("v", Tag.TAG_COMPOUND)
                    && levels.getAllKeys().stream().allMatch(id -> levels.contains(id, Tag.TAG_ANY_NUMERIC))) {
                levels.getAllKeys().forEach(id -> enchantments.add(new Entry(fullId(id), String.valueOf(levels.getInt(id)))));
                return;
            }
        }
        others.add(new Entry(key, value));
    }

    /** Reads an equipment slot's saved form, {@code {id:...,components:{...}}}. */
    static @Nullable ItemLine fromTag(CompoundTag tag) {
        if (!tag.contains("id", Tag.TAG_STRING)) return null;
        String id = tag.getString("id");
        CompoundTag components = tag.getCompound("components");
        List<String> parts = new ArrayList<>();
        for (String key : components.getAllKeys()) {
            parts.add(key.startsWith("!") ? key : key + "=" + components.get(key));
        }
        return parse(parts.isEmpty() ? id : id + "[" + String.join(",", parts) + "]");
    }

    /** The saved form of a valid line. */
    CompoundTag toTag(int count) throws CommandSyntaxException {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", fullId(item));
        if (count > 1) tag.putInt("count", count);
        CompoundTag components = new CompoundTag();
        for (String part : components()) {
            if (part.startsWith("!")) {
                components.put(part, new CompoundTag());
            } else {
                int equals = part.indexOf('=');
                components.put(fullId(part.substring(0, equals)), TagParser.parseTag("{v:" + part.substring(equals + 1) + "}").get("v"));
            }
        }
        if (!components.isEmpty()) tag.put("components", components);
        return tag;
    }

    private List<String> components() {
        List<String> parts = new ArrayList<>();
        if (!damage.isBlank()) parts.add("damage=" + damage.trim());
        if (!name.isEmpty()) parts.add("custom_name=" + StringTag.quoteAndEscape(nameToJson(name)));
        if (unbreakable) parts.add("unbreakable={}");
        if (!enchantments.isEmpty()) {
            List<String> levels = new ArrayList<>();
            for (Entry entry : enchantments) levels.add(StringTag.quoteAndEscape(entry.key.trim()) + ":" + entry.value.trim());
            parts.add("enchantments={" + String.join(",", levels) + "}");
        }
        for (Entry entry : others) parts.add(entry.key.startsWith("!") ? entry.key : entry.key.trim() + "=" + entry.value.trim());
        return parts;
    }

    /** The {@code [...]} part, or an empty string when there's no data. */
    String dataText() {
        List<String> parts = components();
        return parts.isEmpty() ? "" : "[" + String.join(",", parts) + "]";
    }

    String text() {
        return item.trim() + dataText();
    }

    /** What's wrong with the line, ready to show, or null. Without a world only what doesn't depend on datapacks is checked. */
    @Nullable String problem() {
        ResourceLocation id = ResourceLocation.tryParse(item.trim());
        if (item.isBlank()) return message("mobsettings.ui.data.error.noItem");
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return message("mobsettings.ui.error.unknownId") + " (" + item.trim() + ")";
        var level = Minecraft.getInstance().level;
        if (level != null) {
            try {
                StringReader reader = new StringReader(text());
                new ItemParser(level.registryAccess()).parse(reader);
                return reader.canRead() ? message("mobsettings.ui.error.badData") : null;
            } catch (CommandSyntaxException ex) {
                return ex.getMessage();
            }
        }
        for (Entry entry : enchantments) {
            if (entry.key.isBlank() || ResourceLocation.tryParse(entry.key.trim()) == null) return message("mobsettings.ui.error.unknownId") + " (" + entry.key.trim() + ")";
            if (!validLevel(entry.value)) return message("mobsettings.ui.data.error.level");
        }
        if (!damage.isBlank() && !validDamage(damage)) return message("mobsettings.ui.data.error.damage");
        for (Entry entry : others) {
            String key = entry.key.startsWith("!") ? entry.key.substring(1) : entry.key;
            ResourceLocation component = ResourceLocation.tryParse(key.trim());
            if (component == null || !BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(component)) {
                return message("mobsettings.ui.error.unknownId") + " (" + key.trim() + ")";
            }
            if (!entry.key.startsWith("!") && wrapped(entry.value) == null) return message("mobsettings.ui.error.badData") + " (" + key.trim() + ")";
        }
        return null;
    }

    private static boolean validLevel(String value) {
        try {
            int level = Integer.parseInt(value.trim());
            return level >= 1 && level <= 255;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean validDamage(String value) {
        try {
            return Integer.parseInt(value.trim()) >= 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /** Parses a component value, wrapped so that any kind of value can be read. */
    private static @Nullable CompoundTag wrapped(String value) {
        try {
            return TagParser.parseTag("{v:" + value + "}");
        } catch (CommandSyntaxException ex) {
            return null;
        }
    }

    /**
     * The text of a saved name, which is JSON on this version. Text that isn't JSON is taken as the name itself, and
     * null means JSON with more than plain text, which only the generic component input can keep.
     */
    static @Nullable String nameFromJson(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
        } catch (JsonParseException ex) {
            return json;
        }
    }

    static String nameToJson(String name) {
        return new JsonPrimitive(name).toString();
    }

    /**
     * The item in text copied from elsewhere: a field, or a whole {@code /give} command with its target and count.
     * Text starting with {@code [} is only data.
     */
    static String fromClipboard(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        if (!trimmed.startsWith("give ")) return trimmed;
        // The target can have spaces inside its own brackets, so find where it ends the way fields are split.
        String afterGive = trimmed.substring(5).trim();
        int depth = 0;
        int end = -1;
        for (int i = 0; i < afterGive.length() && end < 0; i++) {
            char c = afterGive.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            else if (c == ' ' && depth == 0) end = i;
        }
        return end < 0 ? trimmed : afterGive.substring(end).trim().replaceFirst("(?<=[\\]\\w])\\s+\\d+$", "");
    }

    private static String message(String key) {
        return Component.translatable(key).getString();
    }

    /** Lines may leave out minecraft:, but the editor compares IDs with their namespace. */
    static String fullId(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id.trim());
        return parsed == null ? id.trim() : parsed.toString();
    }
}
