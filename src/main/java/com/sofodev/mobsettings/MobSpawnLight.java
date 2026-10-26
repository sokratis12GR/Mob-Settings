package com.sofodev.mobsettings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/** Restricts natural spawning of registry-ID-matched entities to a configured light-level range. */
public final class MobSpawnLight {
    private MobSpawnLight() {}

    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        // The event also fires for spawn eggs, commands, spawners, breeding etc., which should stay unaffected.
        EntitySpawnReason reason = event.getSpawnType();
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return;
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
        for (String line : MSConfig.LIGHT_SPAWN_RESTRICTIONS.get()) {
            String[] fields = line.split("\\|", -1);
            if (fields.length != 3 || !(fields[0].equals("*") || fields[0].equals(entityId))) continue;
            final int minLight, maxLight;
            try {
                minLight = Integer.parseInt(fields[1]);
                maxLight = Integer.parseInt(fields[2]);
            } catch (NumberFormatException ex) { continue; }
            if (minLight < 0 || maxLight > 15 || maxLight < minLight) continue;
            BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
            int light = event.getLevel().getMaxLocalRawBrightness(pos);
            if (light < minLight || light > maxLight) {
                event.setSpawnCancelled(true);
                return;
            }
        }
    }
}
