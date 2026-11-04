package com.sofodev.mobsettings;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/** Restricts natural spawning of registry-ID-matched entities to a configured light-level range, optionally per dimension and biome. */
public final class MobSpawnLight {
    private MobSpawnLight() {}

    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        // The event also fires for spawn eggs, commands, spawners, breeding etc., which should stay unaffected.
        MobSpawnType reason = event.getSpawnType();
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) return;
        ResourceLocation dimension = event.getLevel().getLevel().dimension().location();
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        for (Rules.LightRestriction rule : Rules.LIGHT_SPAWN_RESTRICTIONS.get()) {
            if (!Rules.matchesMob(rule.entity(), event.getEntity().getType()) || !Rules.matches(rule.dimension(), dimension)) continue;
            if (!rule.biome().matches(event.getLevel().getBiome(pos))) continue;
            int light = event.getLevel().getMaxLocalRawBrightness(pos);
            if (light < rule.min() || light > rule.max()) {
                event.setSpawnCancelled(true);
                return;
            }
        }
    }
}
