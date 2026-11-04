package com.sofodev.mobsettings;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Data carried by a rule's item or entity field. Rules are parsed before a world's registries exist, and data can name
 * datapack content such as enchantments, so each template is resolved on first use and again if the registries change.
 */
final class RuleData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> IGNORED_ENTITY_KEYS = List.of("UUID", "Pos", "Motion", "Rotation", "id");

    private RuleData() {}

    static final class ItemTemplate {
        private final String text;
        private HolderLookup.@Nullable Provider resolvedFor;
        private @Nullable ItemInput input;

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

        private @Nullable ItemInput parse(HolderLookup.Provider registries) {
            try {
                StringReader reader = new StringReader(text);
                ItemInput parsed = new ItemParser(registries).parse(reader);
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
        }

        /** The data to merge onto a mob, or null if its equipment is rejected. Loading bad equipment would strip the mob's gear. */
        synchronized @Nullable CompoundTag data(HolderLookup.Provider registries) {
            if (registries != resolvedFor) {
                resolvedFor = registries;
                valid = data.get("equipment") == null || EntityEquipment.CODEC
                        .parse(registries.createSerializationContext(NbtOps.INSTANCE), data.get("equipment"))
                        .ifError(error -> LOGGER.warn("Ignoring entity data \"{}\": {}", text, error.message()))
                        .isSuccess();
            }
            return valid ? data : null;
        }
    }
}
