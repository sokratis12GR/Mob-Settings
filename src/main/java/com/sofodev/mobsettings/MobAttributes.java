package com.sofodev.mobsettings;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Applies registry-ID-based base attribute overrides when a living entity joins the world. */
public final class MobAttributes {
    private static final String SPAWN_DIMENSION = MobSettings.MODID + ":spawn_dimension";

    private MobAttributes() {}

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // The server syncs attributes to clients, and only it has the entity's saved spawn dimension.
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living)) return;
        // Rules match where the mob first appeared, so its stats don't change when it travels between dimensions.
        CompoundTag data = living.getPersistentData();
        boolean firstJoin = !data.contains(SPAWN_DIMENSION);
        if (firstJoin) data.putString(SPAWN_DIMENSION, living.level().dimension().identifier().toString());
        String spawnDimension = data.getStringOr(SPAWN_DIMENSION, "");
        boolean maxHealthChanged = false;
        // Less specific rules go first so the most specific rule wins for the same attribute: *, then a mod, then a
        // tag, then this mob, and within those, every dimension then this dimension.
        for (int pass = 0; pass < 8; pass++) {
            for (Rules.AttributeOverride rule : Rules.ATTRIBUTE_OVERRIDES.get()) {
                if (specificity(rule) != pass || !Rules.matchesMob(rule.entity(), living.getType())) continue;
                if (rule.dimension() != null && !rule.dimension().toString().equals(spawnDimension)) continue;
                AttributeInstance instance = living.getAttribute(rule.attribute());
                if (instance == null) continue;
                instance.setBaseValue(rule.attribute().value().sanitizeValue(rule.value()));
                if (rule.attribute().value() == Attributes.MAX_HEALTH.value()) maxHealthChanged = true;
            }
        }
        // A new mob starts with its normal health, so fill it up to the new maximum. Saved mobs keep their health,
        // and so do mobs changing dimension, which also arrive with loadedFromDisk false.
        if (maxHealthChanged && firstJoin && !event.loadedFromDisk()) living.setHealth(living.getMaxHealth());
    }

    private static int specificity(Rules.AttributeOverride rule) {
        return rule.entity().specificity() * 2 + (rule.dimension() != null ? 1 : 0);
    }
}
