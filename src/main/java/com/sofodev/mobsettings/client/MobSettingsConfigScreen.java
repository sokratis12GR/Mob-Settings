package com.sofodev.mobsettings.client;

import com.sofodev.mobsettings.MSConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Config hub for versions without NeoForge's ConfigurationScreen: one button per rule list, opening the searchable editor. */
public final class MobSettingsConfigScreen extends Screen {
    private static final String PREFIX = "mobsettings.configuration.";

    private final Screen parent;
    private final Map<String, ModConfigSpec.ConfigValue<List<? extends String>>> lists = new LinkedHashMap<>();

    public MobSettingsConfigScreen(Screen parent) {
        super(Component.translatable(PREFIX + "title"));
        this.parent = parent;
        lists.put("removeDrops", MSConfig.REMOVE_DROPS);
        lists.put("extraDrops", MSConfig.EXTRA_DROPS);
        lists.put("removeSpawns", MSConfig.REMOVE_SPAWNS);
        lists.put("addSpawns", MSConfig.ADD_SPAWNS);
        lists.put("attributeOverrides", MSConfig.ATTRIBUTE_OVERRIDES);
        lists.put("despawnRanges", MSConfig.DESPAWN_RANGES);
        lists.put("lightSpawnRestrictions", MSConfig.LIGHT_SPAWN_RESTRICTIONS);
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = 36;
        addRenderableWidget(CycleButton.onOffBuilder(MSConfig.DROP_ONLY_ON_PLAYER_KILL.get())
                .withTooltip(on -> Tooltip.create(Component.translatable(PREFIX + "dropOnlyOnPlayerKill.tooltip")))
                .create(x, y, 200, 20, Component.translatable(PREFIX + "dropOnlyOnPlayerKill"), (b, value) -> {
                    MSConfig.DROP_ONLY_ON_PLAYER_KILL.set(value);
                    MSConfig.DROP_ONLY_ON_PLAYER_KILL.save();
                }));
        y += 24;
        for (Map.Entry<String, ModConfigSpec.ConfigValue<List<? extends String>>> entry : lists.entrySet()) {
            String key = entry.getKey();
            ModConfigSpec.ConfigValue<List<? extends String>> value = entry.getValue();
            RuleType type = RuleType.TYPES.get(key);
            addRenderableWidget(Button.builder(Component.translatable(PREFIX + key),
                            b -> minecraft.setScreen(new RuleListScreen(this, type, List.copyOf(value.get()), updated -> {
                                if (!updated.equals(value.get())) {
                                    value.set(updated);
                                    value.save();
                                }
                            })))
                    .tooltip(Tooltip.create(Component.translatable(PREFIX + key + ".tooltip")))
                    .bounds(x, y, 200, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x, height - 28, 200, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        renderBackground(graphics, mouseX, mouseY, a);
        super.render(graphics, mouseX, mouseY, a);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
    }
}
