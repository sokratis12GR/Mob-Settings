package com.sofodev.mobsettings;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Data carried by a rule's item or entity field. Rules are parsed before a world's registries exist, and data can name
 * datapack content such as enchantments, so each template is resolved on first use and again if the registries change.
 */
final class RuleData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> IGNORED_ENTITY_KEYS = List.of("UUID", "Pos", "Motion", "Rotation", "id");
    private static final String EQUIPMENT = "equipment";
    private static final String DROP_CHANCES = "drop_chances";
    private static final String BODY = "body";
    private static final String BODY_ITEM = "body_armor_item";
    private static final String BODY_CHANCE = "body_armor_drop_chance";
    /** Vanilla's chance for gear a mob spawns with. A mob loading a body item without a chance would never drop it. */
    private static final float DEFAULT_DROP_CHANCE = 0.085F;
    /** The lists a mob saves its gear in, with each slot at its index. The body slot is saved on its own. */
    private static final List<String> ARMOR_SLOTS = List.of("feet", "legs", "chest", "head");
    private static final List<String> HAND_SLOTS = List.of("mainhand", "offhand");

    private RuleData() {}

    /**
     * Merges a rule's data onto a mob's saved data. A mob saves its gear in lists, which a plain merge would replace
     * whole, so {@code equipment} and {@code drop_chances} are written slot by slot and other slots keep their gear.
     */
    static CompoundTag merge(CompoundTag saved, CompoundTag data) {
        CompoundTag rest = data.copy();
        CompoundTag equipment = rest.getCompound(EQUIPMENT);
        CompoundTag chances = rest.getCompound(DROP_CHANCES);
        rest.remove(EQUIPMENT);
        rest.remove(DROP_CHANCES);
        saved.merge(rest);
        writeSlots(saved, "ArmorItems", "ArmorDropChances", ARMOR_SLOTS, equipment, chances);
        writeSlots(saved, "HandItems", "HandDropChances", HAND_SLOTS, equipment, chances);
        if (equipment.contains(BODY, Tag.TAG_COMPOUND)) {
            saved.put(BODY_ITEM, equipment.getCompound(BODY));
            if (!saved.contains(BODY_CHANCE, Tag.TAG_ANY_NUMERIC)) saved.putFloat(BODY_CHANCE, DEFAULT_DROP_CHANCE);
        }
        if (chances.contains(BODY, Tag.TAG_ANY_NUMERIC)) saved.putFloat(BODY_CHANCE, chances.getFloat(BODY));
        return saved;
    }

    private static void writeSlots(CompoundTag saved, String itemsKey, String chancesKey, List<String> slots, CompoundTag equipment, CompoundTag chances) {
        ListTag items = saved.getList(itemsKey, Tag.TAG_COMPOUND);
        ListTag dropChances = saved.getList(chancesKey, Tag.TAG_FLOAT);
        for (int i = 0; i < slots.size(); i++) {
            String slot = slots.get(i);
            if (equipment.contains(slot, Tag.TAG_COMPOUND) && i < items.size()) items.set(i, equipment.getCompound(slot));
            if (chances.contains(slot, Tag.TAG_ANY_NUMERIC) && i < dropChances.size()) dropChances.set(i, FloatTag.valueOf(chances.getFloat(slot)));
        }
    }

    private static boolean isSlot(String slot) {
        return slot.equals(BODY) || ARMOR_SLOTS.contains(slot) || HAND_SLOTS.contains(slot);
    }

    static final class ItemTemplate {
        private final String text;
        private HolderLookup.@Nullable Provider resolvedFor;
        private ItemParser.@Nullable ItemResult input;

        ItemTemplate(String text) {
            this.text = text;
        }

        /** One of the item, or null if the game rejects its data. That's logged once, instead of dropping a plain item. */
        synchronized @Nullable ItemStack create(HolderLookup.Provider registries) {
            if (registries != resolvedFor) {
                resolvedFor = registries;
                input = parse(registries);
            }
            return input == null ? null : new ItemStack(input.item(), 1, input.components());
        }

        private ItemParser.@Nullable ItemResult parse(HolderLookup.Provider registries) {
            try {
                StringReader reader = new StringReader(text);
                ItemParser.ItemResult parsed = new ItemParser(registries).parse(reader);
                if (!reader.canRead()) return parsed;
                LOGGER.warn("Ignoring item data \"{}\": unexpected text at the end", text);
            } catch (CommandSyntaxException ex) {
                LOGGER.warn("Ignoring item data \"{}\": {}", text, ex.getMessage());
            }
            return null;
        }
    }

    static final class EntityTemplate {
        private final String text;
        private final CompoundTag data;
        private HolderLookup.@Nullable Provider resolvedFor;
        private boolean valid;

        EntityTemplate(String text, CompoundTag data) {
            this.text = text;
            this.data = data;
            IGNORED_ENTITY_KEYS.forEach(data::remove);
            // Configs can come from newer versions, where saddles have a slot.
            if (data.getCompound(EQUIPMENT).contains("saddle") || data.getCompound(DROP_CHANCES).contains("saddle")) {
                LOGGER.warn("Ignoring the saddle slot in entity data \"{}\": this version has no saddle slot", text);
                data.getCompound(EQUIPMENT).remove("saddle");
                data.getCompound(DROP_CHANCES).remove("saddle");
            }
        }

        /** The data to merge onto a mob, or null if its equipment is rejected. Loading bad equipment would strip the mob's gear. */
        synchronized @Nullable CompoundTag data(HolderLookup.Provider registries) {
            if (registries != resolvedFor) {
                resolvedFor = registries;
                valid = validEquipment(registries);
            }
            return valid ? data : null;
        }

        private boolean validEquipment(HolderLookup.Provider registries) {
            CompoundTag equipment = data.getCompound(EQUIPMENT);
            for (String slot : equipment.getAllKeys()) {
                if (!isSlot(slot)) {
                    LOGGER.warn("Ignoring entity data \"{}\": unknown equipment slot {}", text, slot);
                    return false;
                }
                boolean parsed = ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), equipment.get(slot))
                        .ifError(error -> LOGGER.warn("Ignoring entity data \"{}\": {}", text, error.message()))
                        .isSuccess();
                if (!parsed) return false;
            }
            return true;
        }

        // Rules are keys in the spawn caches, and a reloaded config makes new templates from the same text.
        @Override
        public boolean equals(Object other) {
            return other instanceof EntityTemplate template && template.text.equals(text);
        }

        @Override
        public int hashCode() {
            return text.hashCode();
        }
    }
}
