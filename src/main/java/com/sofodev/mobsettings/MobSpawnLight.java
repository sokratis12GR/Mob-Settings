package com.sofodev.mobsettings;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/** Restricts natural spawning of registry-ID-matched entities to a configured light-level range. */
public final class MobSpawnLight {
    private MobSpawnLight() {}

    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        // The event also fires for spawn eggs, commands, spawners, breeding etc., which should stay unaffected.
        EntitySpawnReason reason = event.getSpawnType();
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return;
        for (Rules.LightRestriction rule : Rules.LIGHT_SPAWN_RESTRICTIONS.get()) {
            if (!Rules.matches(rule.entity(), event.getEntity().getType())) continue;
            BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
            int light = event.getLevel().getMaxLocalRawBrightness(pos);
            if (light < rule.min() || light > rule.max()) {
                event.setSpawnCancelled(true);
                return;
            }
        }
    }
}
