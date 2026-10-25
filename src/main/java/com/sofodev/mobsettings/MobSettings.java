package com.sofodev.mobsettings;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(MobSettings.MODID)
public final class MobSettings {
    public static final String MODID = "mobsettings";

    public MobSettings(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, MSConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(MobDrops::onLivingDrops);
        MobSpawns.register(modBus);
    }
}
