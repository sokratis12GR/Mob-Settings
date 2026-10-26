package com.sofodev.mobsettings;

import com.sofodev.mobsettings.client.MobSettingsConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MobSettings.MODID, dist = Dist.CLIENT)
public final class MobSettingsClient {
    public MobSettingsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new ConfigurationScreen(mod, parent, MobSettingsConfigScreen::new));
    }
}
