package com.sofodev.mobsettings;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Applies registry-ID-based base attribute overrides when a living entity joins the world. */
public final class MobAttributes {
    private MobAttributes() {}

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        boolean maxHealthChanged = false;
        // Wildcard rules go first so a rule naming this mob wins over a * rule for the same attribute.
        for (boolean wildcardPass : new boolean[] {true, false}) {
            for (Rules.AttributeOverride rule : Rules.ATTRIBUTE_OVERRIDES.get()) {
                if ((rule.entity() == null) != wildcardPass || !Rules.matches(rule.entity(), living.getType())) continue;
                AttributeInstance instance = living.getAttribute(rule.attribute());
                if (instance == null) continue;
                instance.setBaseValue(rule.attribute().value().sanitizeValue(rule.value()));
                if (rule.attribute().value() == Attributes.MAX_HEALTH.value()) maxHealthChanged = true;
            }
        }
        // A new mob starts with its normal health, so fill it up to the new maximum (saved mobs keep their health).
        if (maxHealthChanged && !event.loadedFromDisk()) living.setHealth(living.getMaxHealth());
    }
}
