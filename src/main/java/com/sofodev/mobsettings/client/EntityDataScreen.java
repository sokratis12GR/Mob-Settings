package com.sofodev.mobsettings.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Edits the data an added spawn's mob gets: its worn equipment, and whatever else entity data can set. */
final class EntityDataScreen extends DataScreen {
    private EntityLine line;
    private final Consumer<String> onDone;

    EntityDataScreen(Screen parent, String data, Consumer<String> onDone) {
        super(parent, Component.translatable("mobsettings.ui.data.entityTitle"));
        EntityLine parsed = EntityLine.parse(data);
        this.line = parsed != null ? parsed : new EntityLine();
        this.onDone = onDone;
    }

    @Override
    protected void buildRows() {
        row(Component.translatable("mobsettings.ui.data.equipment"), Component.translatable("mobsettings.ui.data.help.equipment"));
        for (String slot : EntityLine.SLOTS) slotRow(slot);

        row(Component.translatable("mobsettings.ui.data.mob"), null);
        Component nameHelp = Component.translatable("mobsettings.ui.data.help.mobName");
        row(Component.translatable("mobsettings.ui.data.name"), nameHelp,
                box(Component.translatable("mobsettings.ui.data.name"), nameHelp, line.name, 0, value -> line.name = value));
        row(Component.translatable("mobsettings.ui.data.persistent"), Component.translatable("mobsettings.ui.data.help.persistent"),
                Button.builder(line.persistent ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF, b -> {
                    line.persistent = !line.persistent;
                    b.setMessage(line.persistent ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
                    revalidate();
                }).size(0, 20).build());
        Component otherHelp = Component.translatable("mobsettings.ui.data.help.otherNbt");
        EditBox other = box(Component.translatable("mobsettings.ui.data.otherNbt"), otherHelp, line.other, 0, value -> line.other = value);
        other.setHint(hint("mobsettings.ui.data.hint.otherNbt"));
        row(Component.translatable("mobsettings.ui.data.otherNbt"), otherHelp, other);
    }

    private void slotRow(String slot) {
        Component label = Component.translatable("mobsettings.ui.slot." + slot);
        EditBox item = box(label, Component.translatable("mobsettings.ui.data.help.slot"), line.items.getOrDefault(slot, ""), 0, value -> {
            line.items.put(slot, value);
            line.counts.remove(slot);
        });
        item.setHint(hint("mobsettings.ui.data.hint.slot"));
        picker(item, () -> Suggestions.options(RuleType.Kind.ITEM, null));
        Button edit = button(Component.literal("..."), Component.translatable("mobsettings.ui.data.help.editSlot"), 20, () ->
                minecraft.gui.setScreen(new ItemDataScreen(this, line.items.getOrDefault(slot, ""), false, text -> line.items.put(slot, text))));
        EditBox chance = box(Component.translatable("mobsettings.ui.data.dropChance"), Component.translatable("mobsettings.ui.data.help.dropChance"),
                line.chances.getOrDefault(slot, ""), 34, value -> line.chances.put(slot, value));
        chance.setHint(hint("mobsettings.ui.data.hint.dropChance"));
        Button copy = button(Component.translatable("mobsettings.ui.data.copyShort"), Component.translatable("mobsettings.ui.data.help.copySlot"), 20, () -> {
            minecraft.keyboardHandler.setClipboard(line.items.getOrDefault(slot, ""));
            setNotice(Component.translatable("mobsettings.ui.data.copied"));
        });
        Button paste = button(Component.translatable("mobsettings.ui.data.pasteShort"), Component.translatable("mobsettings.ui.data.help.pasteSlot"), 20, () -> pasteSlot(slot));
        Button clear = button(Component.literal("x"), Component.translatable("mobsettings.ui.data.help.clearSlot"), 20, () -> {
            line.items.remove(slot);
            line.chances.remove(slot);
            line.counts.remove(slot);
            rebuild();
        });
        row(label, null, item, edit, chance, copy, paste, clear);
    }

    /** A slot takes the whole copied item, since it has no item of its own worth keeping. */
    private void pasteSlot(String slot) {
        String text = ItemLine.fromClipboard(minecraft.keyboardHandler.getClipboard());
        ItemLine pasted = ItemLine.parse(text);
        String problem = pasted == null ? Component.translatable("mobsettings.ui.error.badData").getString() : pasted.problem();
        if (problem != null) {
            setNotice(Component.literal(problem).withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }
        line.items.put(slot, pasted.text());
        line.counts.remove(slot);
        rebuild();
        setNotice(Component.translatable("mobsettings.ui.data.pasted"));
    }

    @Override
    protected String text() {
        try {
            String text = line.text();
            return text.isEmpty() ? "{}" : text;
        } catch (CommandSyntaxException ex) {
            return "{}";
        }
    }

    @Override
    protected @Nullable String problem() {
        return line.problem();
    }

    @Override
    protected @Nullable String paste(String clipboard) {
        EntityLine pasted = EntityLine.parse(EntityLine.fromClipboard(clipboard));
        if (pasted == null) return Component.translatable("mobsettings.ui.error.badData").getString();
        String problem = pasted.problem();
        if (problem != null) return problem;
        line = pasted;
        return null;
    }

    @Override
    protected void clear() {
        line = new EntityLine();
    }

    @Override
    protected void finish() {
        String text = text();
        onDone.accept(text.equals("{}") ? "" : text);
    }
}
