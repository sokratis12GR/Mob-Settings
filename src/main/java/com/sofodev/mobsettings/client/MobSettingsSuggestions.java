package com.sofodev.mobsettings.client;

import com.sofodev.thedragonlib.client.config.RuleSuggestionSource;
import com.sofodev.thedragonlib.client.config.RuleType;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Hands TheDragonLib's rule screens the option lists of {@link Suggestions}, which also knows dimensions, biome tags and entity selectors. */
final class MobSettingsSuggestions implements RuleSuggestionSource {

    private static Suggestions.@Nullable Kind kind(RuleType.Field field) {
        return switch (field.kind()) {
            case ENTITY -> Suggestions.Kind.ENTITY;
            case SPAWNABLE_ENTITY -> Suggestions.Kind.SPAWNABLE_ENTITY;
            case ITEM -> Suggestions.Kind.ITEM;
            case BIOME -> Suggestions.Kind.BIOME;
            case ATTRIBUTE -> Suggestions.Kind.ATTRIBUTE;
            case CUSTOM -> field.source().equals(MobSettingsRules.DIMENSION) ? Suggestions.Kind.DIMENSION : null;
            default -> null;
        };
    }

    private static List<Option> convert(List<Suggestions.Option> options) {
        return options.stream().map(o -> new Option(o.id(), o.name())).toList();
    }

    @Override
    public List<Option> options(RuleType.Field field, String[] values) {
        Suggestions.Kind kind = kind(field);
        if (kind == null) return List.of();
        // Attributes are offered for the mob in the line's first field.
        return convert(Suggestions.options(kind, kind == Suggestions.Kind.ATTRIBUTE && values.length > 0 ? values[0] : null));
    }

    @Override
    public boolean isKnown(RuleType.Field field, String id, String[] values) {
        Suggestions.Kind kind = kind(field);
        return kind != null && Suggestions.isKnown(kind, id);
    }

    @Override
    public String displayName(RuleType.Field field, String id) {
        Suggestions.Kind kind = kind(field);
        return kind == null ? id : Suggestions.displayName(kind, id);
    }

    @Override
    public Component anyLabel(RuleType.Field field) {
        Suggestions.Kind kind = kind(field);
        return Suggestions.anyLabel(kind != null ? kind : Suggestions.Kind.ENTITY);
    }

    @Override
    public @Nullable Component help(RuleType.Field field, String id) {
        Suggestions.Kind kind = kind(field);
        return kind == null ? null : Suggestions.help(kind, id);
    }

    @Override
    public List<Option> selectors(RuleType.Field field) {
        Suggestions.Kind kind = kind(field);
        return kind == null ? List.of() : convert(Suggestions.selectors(kind));
    }

    @Override
    public boolean isSelector(RuleType.Field field, String id) {
        Suggestions.Kind kind = kind(field);
        return kind != null && Suggestions.isSelector(kind, id);
    }

    @Override
    public double @Nullable [] attributeRange(String attributeId) {
        return Suggestions.attributeRange(attributeId);
    }

    @Override
    public @Nullable Double attributeDefault(@Nullable String context, String attributeId) {
        return Suggestions.attributeDefault(context, attributeId);
    }
}
