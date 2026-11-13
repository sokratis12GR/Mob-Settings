package com.sofodev.mobsettings.client;

import com.sofodev.mobsettings.SpawnLines;
import com.sofodev.thedragonlib.client.config.RuleListScreen;
import com.sofodev.thedragonlib.client.config.RuleType;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.Nullable;

/** NeoForge's config section screen, with the rule lists opening the searchable editor instead of raw text lists. */
public final class MobSettingsConfigScreen extends ConfigurationScreen.ConfigurationSectionScreen {
    private static final String SECTION = "neoforge.configuration.uitext.section";
    private static final String SECTION_TEXT = "neoforge.configuration.uitext.sectiontext";

    public MobSettingsConfigScreen(Screen parent, ModConfig.Type type, ModConfig config, Component title) {
        super(parent, type, config, title);
    }

    /** The config version is for upgrading the file, not for players to change. */
    @Override
    protected @Nullable Element createIntegerValue(String key, ModConfigSpec.ValueSpec spec, Supplier<Integer> source, Consumer<Integer> target) {
        return key.equals("configVersion") ? null : super.createIntegerValue(key, spec, source, target);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> @Nullable Element createList(String key, ModConfigSpec.ListValueSpec spec, ModConfigSpec.ConfigValue<List<T>> list) {
        RuleType type = MobSettingsRules.TYPES.get(key);
        if (type == null) return super.createList(key, spec, list);
        ModConfigSpec.ConfigValue<List<String>> rules = (ModConfigSpec.ConfigValue<List<String>>) (Object) list;
        Component tooltip = getTooltipComponent(key, null);
        Button button = Button.builder(Component.translatable(SECTION, Component.translatable(SECTION_TEXT)),
                        // getRaw: world-restart values keep their old cached value after set() until the world reloads.
                        b -> minecraft.setScreen(new RuleListScreen(this, type, MobSettingsRules.CONTEXT, rules.getRaw(), LookupPicker.forKey(key), updated -> {
                            if (!updated.equals(rules.getRaw())) {
                                rules.set(updated);
                                onChanged(key);
                            }
                        }).withConversions(MobSettingsRules.CONVERSIONS)
                                .withGrouping(MobSettingsRules.ENTITY_FIELD, MobSettingsRules.GROUP_THRESHOLD, SpawnLines::fieldId)))
                .tooltip(Tooltip.create(tooltip))
                .build();
        return new Element(Component.translatable(SECTION, getTranslationComponent(key)), tooltip, button, false);
    }
}
