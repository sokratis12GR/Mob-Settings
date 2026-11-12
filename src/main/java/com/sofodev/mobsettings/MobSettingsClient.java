package com.sofodev.mobsettings;

import com.sofodev.mobsettings.client.MobSettingsConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MobSettings.MODID, dist = Dist.CLIENT)
public final class MobSettingsClient {
    public MobSettingsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> {
            if (remoteNonOp()) {
                return new AlertScreen(() -> Minecraft.getInstance().setScreen(parent),
                        Component.translatable("mobsettings.ui.serverManaged"),
                        Component.translatable("mobsettings.ui.serverManaged.message"));
            }
            return new ConfigurationScreen(mod, parent, MobSettingsConfigScreen::new);
        });
    }

    /** In a world hosted elsewhere (not single player or a LAN world this client hosts) without operator permission. */
    private static boolean remoteNonOp() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.getSingleplayerServer() == null
                && !minecraft.player.hasPermissions(Commands.LEVEL_GAMEMASTERS);
    }
}
