package com.sofodev.mobsettings;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;

/** Applies registry-ID-based despawn-range overrides to natural mob despawning. */
public final class MobDespawn {
    private MobDespawn() {}

    public static void onMobDespawn(MobDespawnEvent event) {
        Mob mob = event.getEntity();
        // Name-tagged and never-despawn mobs are left to vanilla; ALLOW would discard them outright.
        if (mob.isPersistenceRequired() || mob.requiresCustomPersistence()) return;
        // The most specific rule wins, wherever they are in the list: this mob, then a tag, then a mod, then *, and
        // for equally narrow mobs a rule for this dimension beats one for every dimension.
        // Between equally specific rules the first one wins.
        Identifier dimension = mob.level().dimension().identifier();
        Rules.DespawnRange match = null;
        for (Rules.DespawnRange rule : Rules.DESPAWN_RANGES.get()) {
            if (!Rules.matchesMob(rule.entity(), mob.getType()) || !Rules.matches(rule.dimension(), dimension)) continue;
            if (match == null || specificity(rule) > specificity(match)) match = rule;
        }
        if (match == null) return;
        Player nearest = mob.level().getNearestPlayer(mob, -1);
        boolean withinRange = nearest != null && nearest.distanceTo(mob) <= match.range();
        event.setResult(withinRange ? MobDespawnEvent.Result.DENY : MobDespawnEvent.Result.ALLOW);
    }

    private static int specificity(Rules.DespawnRange rule) {
        return rule.entity().specificity() * 2 + (rule.dimension() != null ? 1 : 0);
    }
}
