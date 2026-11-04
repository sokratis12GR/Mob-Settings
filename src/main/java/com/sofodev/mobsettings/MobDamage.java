package com.sofodev.mobsettings;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

/** Scales the damage a mob deals and the damage it takes, based on its registry ID and the dimension it's in. */
public final class MobDamage {
    private MobDamage() {}

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        ResourceLocation dimension = victim.level().dimension().location();
        double factor = 1;
        Double taken = narrowest(victim.getType(), dimension, true);
        if (taken != null) factor *= taken;
        // The source's entity is the one responsible, so an arrow counts for the skeleton that shot it.
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            Double dealt = narrowest(attacker.getType(), dimension, false);
            if (dealt != null) factor *= dealt;
        }
        if (factor != 1) event.setAmount((float) (event.getAmount() * factor));
    }

    /** The multiplier from the narrowest matching line that sets one: this mob, then a tag, a mod, then *, then this dimension over every dimension. */
    private static @Nullable Double narrowest(EntityType<?> type, ResourceLocation dimension, boolean taken) {
        Double best = null;
        int bestSpecificity = -1;
        for (Rules.DamageModifier rule : Rules.DAMAGE_MODIFIERS.get()) {
            Double value = taken ? rule.taken() : rule.dealt();
            if (value == null || !Rules.matchesMob(rule.entity(), type) || !Rules.matches(rule.dimension(), dimension)) continue;
            int specificity = rule.entity().specificity() * 2 + (rule.dimension() != null ? 1 : 0);
            if (specificity > bestSpecificity) {
                best = value;
                bestSpecificity = specificity;
            }
        }
        return best;
    }
}
