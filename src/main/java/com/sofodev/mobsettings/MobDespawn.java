package com.sofodev.mobsettings;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;

/** Applies registry-ID-based despawn-range overrides to natural mob despawning. */
public final class MobDespawn {
    private MobDespawn() {}

    public static void onMobDespawn(MobDespawnEvent event) {
        Mob mob = event.getEntity();
        // The most specific rule wins, wherever they are in the list: this mob, then a tag, then a mod, then *.
        // Between equally specific rules the first one wins.
        Rules.DespawnRange match = null;
        for (Rules.DespawnRange rule : Rules.DESPAWN_RANGES.get()) {
            if (!Rules.matchesMob(rule.entity(), mob.getType())) continue;
            if (match == null || rule.entity().specificity() > match.entity().specificity()) match = rule;
        }
        if (match == null) return;
        Player nearest = mob.level().getNearestPlayer(mob, -1);
        boolean withinRange = nearest != null && nearest.distanceTo(mob) <= match.range();
        event.setResult(withinRange ? MobDespawnEvent.Result.DENY : MobDespawnEvent.Result.ALLOW);
    }
}
