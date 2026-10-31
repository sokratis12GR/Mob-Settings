package com.sofodev.mobsettings.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import org.jspecify.annotations.Nullable;

/** Searchable option sources for the rule editor's picker fields. */
final class Suggestions {
    record Option(String id, String name) {
        boolean matches(String query) {
            return query.isEmpty() || id.contains(query) || name.toLowerCase(Locale.ROOT).contains(query);
        }
    }

    private static @Nullable List<Option> entities, spawnableEntities, items, attributes, vanillaBiomes;

    private Suggestions() {}

    static List<Option> options(RuleType.Kind kind, @Nullable String entityContext) {
        return switch (kind) {
            case ENTITY -> entities != null ? entities : (entities = entityOptions(false));
            case SPAWNABLE_ENTITY -> spawnableEntities != null ? spawnableEntities : (spawnableEntities = entityOptions(true));
            case ITEM -> items != null ? items : (items = itemOptions());
            case ATTRIBUTE -> attributeOptions(entityContext);
            case DIMENSION -> dimensionOptions();
            case BIOME -> biomeOptions();
            case INTEGER, DECIMAL, ATTRIBUTE_VALUE -> List.of();
        };
    }

    static boolean isKnown(RuleType.Kind kind, String id) {
        Identifier parsed = Identifier.tryParse(kind == RuleType.Kind.BIOME && id.startsWith("#") ? id.substring(1) : id);
        if (parsed == null) return false;
        return switch (kind) {
            case ENTITY, SPAWNABLE_ENTITY -> options(kind, null).stream().anyMatch(o -> o.id().equals(parsed.toString()));
            case ITEM -> BuiltInRegistries.ITEM.containsKey(parsed);
            case ATTRIBUTE -> BuiltInRegistries.ATTRIBUTE.containsKey(parsed);
            // Biomes, biome tags and dimensions are datapack-driven, so modded ones can't be listed from the title screen.
            case DIMENSION, BIOME -> true;
            case INTEGER, DECIMAL, ATTRIBUTE_VALUE -> false;
        };
    }

    private static List<Option> entityOptions(boolean spawnableOnly) {
        return sorted(BuiltInRegistries.ENTITY_TYPE.stream()
                .filter(DefaultAttributes::hasSupplier)
                .filter(type -> !spawnableOnly || type.getCategory() != MobCategory.MISC)
                .map(type -> new Option(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), type.getDescription().getString())));
    }

    private static List<Option> itemOptions() {
        return sorted(BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .map(item -> new Option(BuiltInRegistries.ITEM.getKey(item).toString(), itemName(item))));
    }

    // Item stacks need data components, which aren't bound on the title screen, so translate the ID directly.
    private static String itemName(Item item) {
        return Component.translatable(item.getDescriptionId()).getString();
    }

    private static List<Option> attributeOptions(@Nullable String entityContext) {
        if (attributes == null) {
            attributes = sorted(BuiltInRegistries.ATTRIBUTE.stream().map(attribute -> new Option(
                    BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString(),
                    Component.translatable(attribute.getDescriptionId()).getString())));
        }
        AttributeSupplier supplier = supplier(entityContext);
        if (supplier == null) return attributes;
        return attributes.stream().filter(o -> supplier.hasAttribute(attribute(o.id()))).toList();
    }

    /** Default attributes of a mob, or null when the ID isn't a mob. */
    @SuppressWarnings("unchecked")
    private static @Nullable AttributeSupplier supplier(@Nullable String entityId) {
        Identifier id = entityId == null ? null : Identifier.tryParse(entityId);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        return DefaultAttributes.hasSupplier(type) ? DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type) : null;
    }

    private static Holder<Attribute> attribute(String id) {
        return BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(id)).orElseThrow();
    }

    private static boolean isAttribute(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null && BuiltInRegistries.ATTRIBUTE.containsKey(parsed);
    }

    /** Allowed {min, max} of an attribute, or null if it's unknown or unbounded. */
    static double @Nullable [] attributeRange(String attributeId) {
        if (!isAttribute(attributeId)) return null;
        return attribute(attributeId).value() instanceof RangedAttribute ranged
                ? new double[] {ranged.getMinValue(), ranged.getMaxValue()}
                : null;
    }

    /** The mob's normal value for an attribute, falling back to the attribute's own default. */
    static @Nullable Double attributeDefault(String entityId, String attributeId) {
        if (!isAttribute(attributeId)) return null;
        Holder<Attribute> attribute = attribute(attributeId);
        AttributeSupplier supplier = supplier(entityId);
        if (supplier != null && supplier.hasAttribute(attribute)) return supplier.getBaseValue(attribute);
        return attribute.value().getDefaultValue();
    }

    /** Player-facing name for an ID; wildcards and unknown IDs are described rather than left raw. */
    static String displayName(RuleType.Kind kind, String id) {
        if (id.equals("*")) return anyLabel(kind).getString();
        if (kind == RuleType.Kind.BIOME && id.startsWith("#")) return tagName(id);
        return options(kind, null).stream().filter(o -> o.id().equals(id)).findFirst().map(Option::name).orElse(id);
    }

    static Component anyLabel(RuleType.Kind kind) {
        String suffix = switch (kind) {
            case ITEM -> "item";
            case DIMENSION -> "dimension";
            case BIOME -> "biome";
            default -> "entity";
        };
        return Component.translatable("mobsettings.ui.any." + suffix);
    }

    /** Explanation shown when hovering an option, if this mod or a translation provides one. */
    static @Nullable Component help(RuleType.Kind kind, String id) {
        if (id.equals("*")) return Component.translatable("mobsettings.ui.any.help");
        if (kind != RuleType.Kind.ATTRIBUTE) return null;
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) return null;
        String key = "mobsettings.attribute." + parsed.getNamespace() + "." + parsed.getPath();
        return Language.getInstance().has(key) ? Component.translatable(key) : null;
    }

    /** The dimensions the server told us about when in a world, otherwise the vanilla ones. */
    private static List<Option> dimensionOptions() {
        var connection = Minecraft.getInstance().getConnection();
        Stream<ResourceKey<Level>> keys = connection != null ? connection.levels().stream() : Stream.of(Level.OVERWORLD, Level.NETHER, Level.END);
        return sorted(keys.map(key -> {
            Identifier id = key.identifier();
            String translation = "mobsettings.dimension." + id.getNamespace() + "." + id.getPath();
            return new Option(id.toString(), Language.getInstance().getOrDefault(translation, id.toString()));
        }));
    }

    /** Biomes, then biome tags (written as #tag). */
    private static List<Option> biomeOptions() {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
            return Stream.concat(sorted(biomes.listElementIds().map(key -> biomeOption(key.identifier()))).stream(),
                    sorted(biomes.listTagIds().map(tag -> tagOption(tag.location()))).stream()).toList();
        }
        if (vanillaBiomes == null) {
            List<Option> found = new ArrayList<>();
            List<Option> tags = new ArrayList<>();
            for (Field field : Biomes.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != ResourceKey.class) continue;
                try {
                    found.add(biomeOption(((ResourceKey<?>) field.get(null)).identifier()));
                } catch (IllegalAccessException ignored) {}
            }
            for (Field field : BiomeTags.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != TagKey.class) continue;
                try {
                    tags.add(tagOption(((TagKey<?>) field.get(null)).location()));
                } catch (IllegalAccessException ignored) {}
            }
            vanillaBiomes = Stream.concat(sorted(found.stream()).stream(), sorted(tags.stream()).stream()).toList();
        }
        return vanillaBiomes;
    }

    private static Option biomeOption(Identifier id) {
        return new Option(id.toString(), Component.translatable("biome." + id.getNamespace() + "." + id.getPath()).getString());
    }

    private static Option tagOption(Identifier id) {
        return new Option("#" + id, tagName("#" + id));
    }

    private static String tagName(String tag) {
        return Component.translatable("mobsettings.ui.biomeTag", tag).getString();
    }

    private static List<Option> sorted(Stream<Option> options) {
        return options.sorted(Comparator.comparing(Option::id)).toList();
    }
}
