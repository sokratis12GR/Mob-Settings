package com.sofodev.mobsettings;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(MobSettings.MODID)
public final class MobSettings {
    public static final String MODID = "mobsettings";

    public MobSettings(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.LOCAL, MSConfig.SPEC, MODID + "-common.toml");
        NeoForge.EVENT_BUS.addListener(MobDrops::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(MobAttributes::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(MobDespawn::onMobDespawn);
        NeoForge.EVENT_BUS.addListener(MobSpawnLight::onFinalizeSpawn);
        // Low priority so dimension rules also see spawns other mods add through this event.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, MobSpawns::onPotentialSpawns);
        MobSpawns.register(modBus);
    }
}
