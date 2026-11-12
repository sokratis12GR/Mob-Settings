package com.sofodev.mobsettings.client;

import com.sofodev.mobsettings.MobNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** EXPERIMENTAL. Opens the mob view screen and carries its edits to the server and the answers back. */
public final class ClientView {
    /** The open view screen, so an answer still reaches it while the item editor is on top. */
    private static @Nullable MobViewScreen open;

    private ClientView() {}

    public static void receive(MobNetwork.ViewData data) {
        if (data.variants().isEmpty()) return;
        open = new MobViewScreen(data);
        Minecraft.getInstance().setScreen(open);
    }

    public static void receive(MobNetwork.ViewEditResult result) {
        if (open != null) open.onEditResult(result);
    }

    static void send(MobNetwork.ViewEdit edit) {
        PacketDistributor.sendToServer(edit);
    }

    static void closed(MobViewScreen screen) {
        if (open == screen) open = null;
    }
}
