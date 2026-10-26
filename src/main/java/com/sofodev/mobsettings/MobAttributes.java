package com.sofodev.mobsettings;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Applies registry-ID-based base attribute overrides when a living entity joins the world. */
public final class MobAttributes {
    private MobAttributes() {}

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString();
        for (String line : MSConfig.ATTRIBUTE_OVERRIDES.get()) {
            String[] fields = line.split("\\|", -1);
            if (fields.length != 3 || !(fields[0].equals("*") || fields[0].equals(entityId))) continue;
            Identifier attributeId = Identifier.tryParse(fields[1]);
            if (attributeId == null || !BuiltInRegistries.ATTRIBUTE.containsKey(attributeId)) continue;
            final double value;
            try {
                value = Double.parseDouble(fields[2]);
            } catch (NumberFormatException ex) { continue; }
            if (!Double.isFinite(value)) continue;
            Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.wrapAsHolder(BuiltInRegistries.ATTRIBUTE.getValue(attributeId));
            AttributeInstance instance = living.getAttribute(attribute);
            if (instance == null) continue;
            instance.setBaseValue(attribute.value().sanitizeValue(value));
        }
    }
}
