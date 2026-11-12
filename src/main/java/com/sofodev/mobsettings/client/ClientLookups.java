package com.sofodev.mobsettings.client;

import com.sofodev.mobsettings.MobNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Asks the server for a mob's drops or spawns. Answers come back on the main thread. */
public final class ClientLookups {
    private static final Map<String, Consumer<MobNetwork.Result>> PENDING = new HashMap<>();

    private ClientLookups() {}

    /** Why the server can't be asked right now, or null if it can. Operator status is left to the server. */
    static @Nullable Component unavailable() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || minecraft.player == null) return Component.translatable("mobsettings.ui.lookup.noWorld");
        if (!connection.hasChannel(MobNetwork.Request.TYPE)) return Component.translatable("mobsettings.ui.lookup.noServerMod");
        return null;
    }

    static void request(boolean spawns, String entity, Consumer<MobNetwork.Result> callback) {
        PENDING.put(key(spawns, entity), callback);
        PacketDistributor.sendToServer(new MobNetwork.Request(spawns, entity));
    }

    public static void receive(MobNetwork.Result result) {
        Consumer<MobNetwork.Result> callback = PENDING.remove(key(result.spawns(), result.entity()));
        if (callback != null) callback.accept(result);
    }

    private static String key(boolean spawns, String entity) {
        return (spawns ? "spawns:" : "drops:") + entity;
    }
}
