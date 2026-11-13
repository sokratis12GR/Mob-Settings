package com.sofodev.mobsettings.client;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Edits one item and its data: the extra drop of a rule, or what a mob wears in a slot. */
final class ItemDataScreen extends DataScreen {
    private ItemLine line;
    /** On a drop, pasting data onto a chosen item keeps that item, so an enchantment set can move between items. */
    private final boolean keepItemOnPaste;
    private final Consumer<String> onDone;
    private @Nullable EditBox damageBox;

    ItemDataScreen(Screen parent, String text, boolean keepItemOnPaste, Consumer<String> onDone) {
        this(parent, Component.translatable("mobsettings.ui.data.itemTitle"), text, keepItemOnPaste, onDone);
    }

    /** @param title what the breadcrumb calls this item, such as the equipment slot it's for */
    ItemDataScreen(Screen parent, Component title, String text, boolean keepItemOnPaste, Consumer<String> onDone) {
        super(parent, title);
        ItemLine parsed = ItemLine.parse(text);
        this.line = parsed != null ? parsed : new ItemLine();
        this.keepItemOnPaste = keepItemOnPaste;
        this.onDone = onDone;
    }

    @Override
    protected void buildRows() {
        Component itemHelp = Component.translatable("mobsettings.ui.data.help.item");
        row(Component.translatable("mobsettings.ui.field.item"), itemHelp, itemIcon(() -> line.item), picker(box(Component.translatable("mobsettings.ui.field.item"), itemHelp, line.item, 0, value -> {
            line.item = value.trim();
            updateDamageHint();
        }), () -> Suggestions.options(Suggestions.Kind.ITEM, null)));

        Component damageHelp = Component.translatable("mobsettings.ui.data.help.damage");
        damageBox = box(Component.translatable("mobsettings.ui.data.damage"), damageHelp, line.damage, 0, value -> line.damage = value);
        updateDamageHint();
        row(Component.translatable("mobsettings.ui.data.damage"), damageHelp, damageBox);

        Component nameHelp = Component.translatable("mobsettings.ui.data.help.name");
        row(Component.translatable("mobsettings.ui.data.name"), nameHelp,
                box(Component.translatable("mobsettings.ui.data.name"), nameHelp, line.name, 0, value -> line.name = value));

        row(Component.translatable("mobsettings.ui.data.unbreakable"), Component.translatable("mobsettings.ui.data.help.unbreakable"),
                Button.builder(toggleLabel(line.unbreakable), b -> {
                    line.unbreakable = !line.unbreakable;
                    b.setMessage(toggleLabel(line.unbreakable));
                    revalidate();
                }).size(0, 20).build());

        row(Component.translatable("mobsettings.ui.data.enchantments"), null);
        for (ItemLine.Entry entry : line.enchantments) {
            EditBox id = box(Component.translatable("mobsettings.ui.data.enchantment"), null, entry.key, 0, value -> entry.key = value.trim());
            id.setHint(hint("mobsettings.ui.data.hint.enchantment"));
            picker(id, Suggestions::enchantments);
            EditBox level = box(Component.translatable("mobsettings.ui.data.level"), Component.translatable("mobsettings.ui.data.help.level"), entry.value, 34, value -> entry.value = value);
            level.setHint(hint("mobsettings.ui.data.hint.level"));
            entryRow(line.enchantments, entry, id, level);
        }
        row(CommonComponents.EMPTY, null, button(Component.translatable("mobsettings.ui.data.addEnchantment"), null, 0, () -> {
            line.enchantments.add(new ItemLine.Entry("", "1"));
            rebuild();
        }));

        row(Component.translatable("mobsettings.ui.data.others"), Component.translatable("mobsettings.ui.data.help.others"));
        for (ItemLine.Entry entry : line.others) {
            EditBox id = box(Component.translatable("mobsettings.ui.data.component"), null, entry.key, 0, value -> entry.key = value.trim());
            id.setHint(hint("mobsettings.ui.data.hint.component"));
            picker(id, Suggestions::dataComponents);
            EditBox value = box(Component.translatable("mobsettings.ui.data.value"), Component.translatable("mobsettings.ui.data.help.value"), entry.value, 0, text -> entry.value = text);
            value.setHint(hint("mobsettings.ui.data.hint.value"));
            entryRow(line.others, entry, id, value);
        }
        row(CommonComponents.EMPTY, null, button(Component.translatable("mobsettings.ui.data.addComponent"), null, 0, () -> {
            line.others.add(new ItemLine.Entry("", ""));
            rebuild();
        }));
    }

    /** Copy, paste, duplicate and remove for one enchantment or component, copied as {@code key=value}. */
    private void entryRow(List<ItemLine.Entry> list, ItemLine.Entry entry, EditBox key, EditBox value) {
        Button copy = button(Component.translatable("mobsettings.ui.data.copyShort"), Component.translatable("mobsettings.ui.data.help.copyEntry"), 20, () -> {
            minecraft.keyboardHandler.setClipboard(entry.key + "=" + entry.value);
            setNotice(Component.translatable("mobsettings.ui.data.copied"));
        });
        Button paste = button(Component.translatable("mobsettings.ui.data.pasteShort"), Component.translatable("mobsettings.ui.data.help.pasteEntry"), 20, () -> {
            String clipboard = minecraft.keyboardHandler.getClipboard().trim();
            int split = clipboard.indexOf('=');
            if (clipboard.isEmpty() || split == 0) {
                setNotice(Component.translatable("mobsettings.ui.error.badData").withStyle(ChatFormatting.RED));
                return;
            }
            entry.key = (split < 0 ? clipboard : clipboard.substring(0, split)).trim();
            if (split >= 0) entry.value = clipboard.substring(split + 1).trim();
            rebuild();
            setNotice(Component.translatable("mobsettings.ui.data.pasted"));
        });
        Button duplicate = button(Component.literal("+"), Component.translatable("mobsettings.ui.data.help.duplicateEntry"), 20, () -> {
            list.add(list.indexOf(entry) + 1, new ItemLine.Entry(entry.key, entry.value));
            rebuild();
        });
        Button remove = button(Component.literal("x"), Component.translatable("mobsettings.ui.delete"), 20, () -> {
            list.remove(entry);
            rebuild();
        });
        row(CommonComponents.EMPTY, null, key, value, copy, paste, duplicate, remove);
    }

    private static Component toggleLabel(boolean on) {
        return on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }

    /** Item stacks aren't usable on the title screen, so the item's durability only shows in a world. */
    private void updateDamageHint() {
        if (damageBox == null) return;
        Identifier id = Identifier.tryParse(line.item);
        boolean known = Minecraft.getInstance().level != null && id != null && BuiltInRegistries.ITEM.containsKey(id);
        int max = known ? new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getMaxDamage() : 0;
        damageBox.setHint(max > 0 ? Component.translatable("mobsettings.ui.data.hint.damage", max).withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                : hint("mobsettings.ui.hint.optional"));
    }

    @Override
    protected String text() {
        return line.text();
    }

    @Override
    protected @Nullable String problem() {
        return line.problem();
    }

    @Override
    protected @Nullable String paste(String clipboard) {
        ItemLine pasted = ItemLine.parse(ItemLine.fromClipboard(clipboard));
        if (pasted == null) return Component.translatable("mobsettings.ui.error.badData").getString();
        if (pasted.item.isEmpty() || (keepItemOnPaste && !line.item.isEmpty())) pasted.item = line.item;
        String problem = pasted.problem();
        if (problem != null) return problem;
        line = pasted;
        return null;
    }

    @Override
    protected void load(String text) {
        ItemLine loaded = ItemLine.parse(text);
        if (loaded != null) line = loaded;
    }

    @Override
    protected void clear() {
        ItemLine cleared = new ItemLine();
        cleared.item = line.item;
        line = cleared;
    }

    @Override
    protected void finish() {
        onDone.accept(line.text());
    }
}
