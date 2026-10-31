package com.sofodev.mobsettings;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;

/** Applies registry-ID-based despawn-range overrides to natural mob despawning. */
public final class MobDespawn {
    private MobDespawn() {}

    public static void onMobDespawn(MobDespawnEvent event) {
        Mob mob = event.getEntity();
        // A rule naming this mob wins over a * rule, wherever they are in the list.
        Rules.DespawnRange match = null;
        for (Rules.DespawnRange rule : Rules.DESPAWN_RANGES.get()) {
            if (rule.entity() == mob.getType()) {
                match = rule;
                break;
            }
            if (rule.entity() == null && match == null) match = rule;
        }
        if (match == null) return;
        Player nearest = mob.level().getNearestPlayer(mob, -1);
        boolean withinRange = nearest != null && nearest.distanceTo(mob) <= match.range();
        event.setResult(withinRange ? MobDespawnEvent.Result.DENY : MobDespawnEvent.Result.ALLOW);
    }
}
