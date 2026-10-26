package com.sofodev.mobsettings;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;

/** Applies registry-ID-based despawn-range overrides to natural mob despawning. */
public final class MobDespawn {
    private MobDespawn() {}

    public static void onMobDespawn(MobDespawnEvent event) {
        Mob mob = event.getEntity();
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        for (String line : MSConfig.DESPAWN_RANGES.get()) {
            String[] fields = line.split("\\|", -1);
            if (fields.length != 2 || !(fields[0].equals("*") || fields[0].equals(entityId))) continue;
            final double range;
            try {
                range = Double.parseDouble(fields[1]);
            } catch (NumberFormatException ex) { continue; }
            if (!Double.isFinite(range) || range <= 0) continue;
            Player nearest = mob.level().getNearestPlayer(mob, -1);
            boolean withinRange = nearest != null && nearest.distanceTo(mob) <= range;
            event.setResult(withinRange ? MobDespawnEvent.Result.DENY : MobDespawnEvent.Result.ALLOW);
            return;
        }
    }
}
