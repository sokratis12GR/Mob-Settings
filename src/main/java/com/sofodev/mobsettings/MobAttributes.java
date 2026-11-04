package com.sofodev.mobsettings;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Applies registry-ID-based attribute overrides when a living entity joins the world. */
public final class MobAttributes {
    private static final String SPAWN_DIMENSION = MobSettings.MODID + ":spawn_dimension";
    /** Set on mobs that carry an override, so only they are searched for one to remove. */
    private static final String OVERRIDDEN = MobSettings.MODID + ":overridden";
    private static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(MobSettings.MODID, "override");

    private MobAttributes() {}

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // The server syncs attributes to clients, and only it has the entity's saved spawn dimension.
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living)) return;
        // Rules match where the mob first appeared, so its stats don't change when it travels between dimensions.
        CompoundTag data = living.getPersistentData();
        boolean firstJoin = !data.contains(SPAWN_DIMENSION);
        if (firstJoin) data.putString(SPAWN_DIMENSION, living.level().dimension().location().toString());
        String spawnDimension = data.getString(SPAWN_DIMENSION);
        // Less specific rules go first so the most specific rule wins for the same attribute: *, then a mod, then a
        // tag, then this mob, and within those, every dimension then this dimension.
        Map<Holder<Attribute>, Double> targets = new LinkedHashMap<>();
        for (int pass = 0; pass < 8; pass++) {
            for (Rules.AttributeOverride rule : Rules.ATTRIBUTE_OVERRIDES.get()) {
                if (specificity(rule) != pass || !Rules.matchesMob(rule.entity(), living.getType())) continue;
                if (rule.dimension() != null && !rule.dimension().toString().equals(spawnDimension)) continue;
                if (living.getAttribute(rule.attribute()) == null) continue;
                targets.put(rule.attribute(), rule.attribute().value().sanitizeValue(rule.value()));
            }
        }
        // The override is a modifier on top of the base value instead of a new base value, so taking a rule out of the
        // config takes it off the mob the next time it loads. Mobs saved before this keep the base value they were given.
        if (data.contains(OVERRIDDEN)) {
            BuiltInRegistries.ATTRIBUTE.holders().forEach(attribute -> {
                AttributeInstance instance = living.getAttribute(attribute);
                if (instance != null) instance.removeModifier(MODIFIER_ID);
            });
            data.remove(OVERRIDDEN);
        }
        boolean maxHealthChanged = false;
        for (Map.Entry<Holder<Attribute>, Double> target : targets.entrySet()) {
            AttributeInstance instance = living.getAttribute(target.getKey());
            double difference = target.getValue() - instance.getBaseValue();
            if (difference == 0) continue;
            instance.addPermanentModifier(new AttributeModifier(MODIFIER_ID, difference, AttributeModifier.Operation.ADD_VALUE));
            data.putBoolean(OVERRIDDEN, true);
            if (target.getKey().value() == Attributes.MAX_HEALTH.value()) maxHealthChanged = true;
        }
        // A new mob starts with its normal health, so fill it up to the new maximum. Saved mobs keep their health,
        // and so do mobs changing dimension, which also arrive with loadedFromDisk false.
        if (maxHealthChanged && firstJoin && !event.loadedFromDisk()) living.setHealth(living.getMaxHealth());
    }

    private static int specificity(Rules.AttributeOverride rule) {
        return rule.entity().specificity() * 2 + (rule.dimension() != null ? 1 : 0);
    }
}
