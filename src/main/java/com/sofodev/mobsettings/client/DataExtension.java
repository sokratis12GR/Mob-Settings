package com.sofodev.mobsettings.client;

import com.sofodev.mobsettings.SpawnLines;
import com.sofodev.thedragonlib.client.config.FieldExtension;
import com.sofodev.thedragonlib.client.config.RuleType;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Item data on the extraDrops item ({@code id[components]}) and entity data on the addSpawns mob ({@code id{nbt}}). */
final class DataExtension implements FieldExtension {

    @Override
    public boolean appliesTo(RuleType type, int field) {
        return MobSettingsRules.dataField(type.key()) == field;
    }

    @Override
    public String id(String value) {
        return SpawnLines.fieldId(value);
    }

    @Override
    public String withId(String value, String id) {
        return id + SpawnLines.fieldData(value);
    }

    @Override
    public @Nullable String validateData(RuleType type, int field, String value) {
        String data = SpawnLines.fieldData(value);
        if (data.isEmpty()) return null;
        boolean valid;
        if (type.key().equals("extraDrops")) {
            ItemLine item = ItemLine.parse(value);
            valid = item != null && item.problem() == null;
        } else {
            EntityLine entity = data.startsWith("{") ? EntityLine.parse(data) : null;
            valid = entity != null && entity.problem() == null;
        }
        return valid ? null : "mobsettings.ui.error.badData";
    }

    @Override
    public Component buttonLabel(RuleType type, int field) {
        return Component.translatable("mobsettings.ui.data.button");
    }

    @Override
    public Component buttonTooltip(RuleType type, int field) {
        return Component.translatable("mobsettings.ui.help.data." + type.key());
    }

    @Override
    public void open(Screen parent, RuleType type, int field, String value, Consumer<String> onDone) {
        Minecraft minecraft = Minecraft.getInstance();
        if (type.key().equals("extraDrops")) {
            minecraft.setScreen(new ItemDataScreen(parent, value, true, onDone));
        } else {
            String id = SpawnLines.fieldId(value);
            minecraft.setScreen(new EntityDataScreen(parent, SpawnLines.fieldData(value), data -> onDone.accept(id + data)));
        }
    }
}
