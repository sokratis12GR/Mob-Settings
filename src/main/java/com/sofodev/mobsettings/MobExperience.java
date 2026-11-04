package com.sofodev.mobsettings;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

/** Multiplies the experience a mob drops, based on its registry ID and the dimension it dies in. */
public final class MobExperience {
    private MobExperience() {}

    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity entity = event.getEntity();
        ResourceLocation dimension = entity.level().dimension().location();
        Rules.ExperienceModifier best = null;
        int bestSpecificity = -1;
        for (Rules.ExperienceModifier rule : Rules.EXPERIENCE_MODIFIERS.get()) {
            if (!Rules.matchesMob(rule.entity(), entity.getType()) || !Rules.matches(rule.dimension(), dimension)) continue;
            int specificity = rule.entity().specificity() * 2 + (rule.dimension() != null ? 1 : 0);
            if (specificity >= bestSpecificity) {
                best = rule;
                bestSpecificity = specificity;
            }
        }
        if (best == null) return;
        // The fraction becomes a chance of one more point, so 1 XP at 1.5x averages 1.5 instead of always rounding.
        double scaled = event.getDroppedExperience() * best.multiplier();
        int whole = (int) scaled;
        event.setDroppedExperience(whole + (entity.getRandom().nextDouble() < scaled - whole ? 1 : 0));
    }
}
