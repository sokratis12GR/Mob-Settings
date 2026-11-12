package com.sofodev.mobsettings;

import com.sofodev.mobsettings.client.ClientLookups;
import com.sofodev.mobsettings.client.ClientView;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The config screen asks the server what a mob drops and where it spawns. Only operators get an answer, so players
 * can't use it to spoil drops; the payloads are optional, so either side can be without Mob Settings.
 */
public final class MobNetwork {
    private MobNetwork() {}

    public enum Status { OK, NO_PERMISSION, UNKNOWN_ENTITY }

    public record Request(boolean spawns, String entity) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MobSettings.MODID, "lookup_request"));
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Request::spawns, ByteBufCodecs.STRING_UTF8, Request::entity, Request::new);

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /** One place the mob spawns; the biome and dimension are IDs so the client needs no registry for them. */
    public record SpawnEntry(String dimension, String biome, int weight, int min, int max) {
        static final StreamCodec<ByteBuf, SpawnEntry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SpawnEntry::dimension, ByteBufCodecs.STRING_UTF8, SpawnEntry::biome,
                ByteBufCodecs.VAR_INT, SpawnEntry::weight, ByteBufCodecs.VAR_INT, SpawnEntry::min,
                ByteBufCodecs.VAR_INT, SpawnEntry::max, SpawnEntry::new);
    }

    /** Asking for this entity gets {@link Result#counts()} for every mob instead of one mob's details. */
    public static final String ALL = "*";

    /**
     * The answer to a {@link Request}: drops holds item IDs, spawns the spawn entries; only the asked one is filled.
     * For {@link #ALL}, counts holds the number of drops or biomes per mob instead.
     */
    public record Result(boolean spawns, String entity, int status, List<String> drops, List<SpawnEntry> spawnList, Map<String, Integer> counts)
            implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MobSettings.MODID, "lookup_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Result::spawns, ByteBufCodecs.STRING_UTF8, Result::entity, ByteBufCodecs.VAR_INT, Result::status,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Result::drops,
                SpawnEntry.CODEC.apply(ByteBufCodecs.list()), Result::spawnList,
                ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), Result::counts, Result::new);

        public Status statusValue() {
            return Status.values()[Math.clamp(status, 0, Status.values().length - 1)];
        }

        @Override
        public Type<Result> type() {
            return TYPE;
        }
    }

    /**
     * EXPERIMENTAL. One version of a mob for the view screen. A live mob has one, with line empty and index -1. Spawn
     * data has a plain one plus one per addSpawns line for the type, with that line and its index in the config list.
     */
    public record ViewVariant(String label, String line, int index, CompoundTag nbt) {
        static final StreamCodec<ByteBuf, ViewVariant> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ViewVariant::label, ByteBufCodecs.STRING_UTF8, ViewVariant::line,
                ByteBufCodecs.VAR_INT, ViewVariant::index, ByteBufCodecs.COMPOUND_TAG, ViewVariant::nbt, ViewVariant::new);
    }

    /** EXPERIMENTAL. A spawn entry after every rule, with what changed it: e.g. "added", "reduced to 50%", "group 2-4". */
    public record SpawnRow(String dimension, String biome, int weight, int min, int max, String tags) {
        static final StreamCodec<ByteBuf, SpawnRow> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SpawnRow::dimension, ByteBufCodecs.STRING_UTF8, SpawnRow::biome,
                ByteBufCodecs.VAR_INT, SpawnRow::weight, ByteBufCodecs.VAR_INT, SpawnRow::min,
                ByteBufCodecs.VAR_INT, SpawnRow::max, ByteBufCodecs.STRING_UTF8, SpawnRow::tags, SpawnRow::new);
    }

    /**
     * EXPERIMENTAL. One drop. removedPercent is -1 when no removeDrops rule covers it, else how often it is removed
     * (100 = never drops). extra marks an extraDrops line; detail is its count and chance.
     */
    public record DropRow(String item, int removedPercent, boolean extra, String detail) {
        static final StreamCodec<ByteBuf, DropRow> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, DropRow::item, ByteBufCodecs.VAR_INT, DropRow::removedPercent,
                ByteBufCodecs.BOOL, DropRow::extra, ByteBufCodecs.STRING_UTF8, DropRow::detail, DropRow::new);
    }

    /** EXPERIMENTAL. Config line states for {@link RuleRow#state()}. */
    public static final int ACTIVE = 0, PENDING_RESTART = 1, REMOVED_PENDING_RESTART = 2, INVALID = 3;

    /** EXPERIMENTAL. A config line covering the mob. index is its place in the saved list, -1 if it's no longer there. */
    public record RuleRow(String list, int index, String line, int state) {
        static final StreamCodec<ByteBuf, RuleRow> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, RuleRow::list, ByteBufCodecs.VAR_INT, RuleRow::index,
                ByteBufCodecs.STRING_UTF8, RuleRow::line, ByteBufCodecs.VAR_INT, RuleRow::state, RuleRow::new);
    }

    /** EXPERIMENTAL. The inspect screen's read-only tabs. overview is what the chat inspect prints. */
    public record ViewInfo(List<Component> overview, List<SpawnRow> spawns, List<DropRow> drops, List<RuleRow> rules) {
        static final StreamCodec<RegistryFriendlyByteBuf, ViewInfo> CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), ViewInfo::overview,
                SpawnRow.CODEC.apply(ByteBufCodecs.list()), ViewInfo::spawns,
                DropRow.CODEC.apply(ByteBufCodecs.list()), ViewInfo::drops,
                RuleRow.CODEC.apply(ByteBufCodecs.list()), ViewInfo::rules, ViewInfo::new);
    }

    /** EXPERIMENTAL. Opens the inspect screen. networkId is the live mob's ID on the client, or -1 for spawn data. */
    public record ViewData(boolean live, String entity, int networkId, String dimension, List<ViewVariant> variants, ViewInfo info)
            implements CustomPacketPayload {
        public static final Type<ViewData> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MobSettings.MODID, "view_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ViewData> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ViewData::live, ByteBufCodecs.STRING_UTF8, ViewData::entity, ByteBufCodecs.VAR_INT, ViewData::networkId,
                ByteBufCodecs.STRING_UTF8, ViewData::dimension, ViewVariant.CODEC.apply(ByteBufCodecs.list()), ViewData::variants,
                ViewInfo.CODEC, ViewData::info, ViewData::new);

        @Override
        public Type<ViewData> type() {
            return TYPE;
        }
    }

    /**
     * EXPERIMENTAL. An edit from the mob view screen. Saving puts {@code line} into addSpawns: at {@code index} if
     * that line still reads {@code expected}, or at the end for -1. Applying sets the gear of the live mob with
     * {@code networkId}; {@code equipment} maps slot names to saved stacks, an empty tag meaning an empty slot.
     */
    public record ViewEdit(boolean apply, int index, String expected, String line, int networkId, Map<String, CompoundTag> equipment)
            implements CustomPacketPayload {
        public static final Type<ViewEdit> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MobSettings.MODID, "view_edit"));
        public static final StreamCodec<ByteBuf, ViewEdit> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ViewEdit::apply, ByteBufCodecs.VAR_INT, ViewEdit::index, ByteBufCodecs.STRING_UTF8, ViewEdit::expected,
                ByteBufCodecs.STRING_UTF8, ViewEdit::line, ByteBufCodecs.VAR_INT, ViewEdit::networkId,
                ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.COMPOUND_TAG), ViewEdit::equipment, ViewEdit::new);

        @Override
        public Type<ViewEdit> type() {
            return TYPE;
        }
    }

    /** EXPERIMENTAL. The answer to a {@link ViewEdit}. After a save, index is where the line now is. */
    public record ViewEditResult(boolean apply, boolean ok, String message, int index, String line) implements CustomPacketPayload {
        public static final Type<ViewEditResult> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MobSettings.MODID, "view_edit_result"));
        public static final StreamCodec<ByteBuf, ViewEditResult> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ViewEditResult::apply, ByteBufCodecs.BOOL, ViewEditResult::ok, ByteBufCodecs.STRING_UTF8, ViewEditResult::message,
                ByteBufCodecs.VAR_INT, ViewEditResult::index, ByteBufCodecs.STRING_UTF8, ViewEditResult::line, ViewEditResult::new);

        @Override
        public Type<ViewEditResult> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(Request.TYPE, Request.CODEC, MobNetwork::handleRequest);
        // Only runs on a client; ClientLookups touches client classes inside its method only.
        registrar.playToClient(Result.TYPE, Result.CODEC, (result, context) -> ClientLookups.receive(result));
        registrar.playToClient(ViewData.TYPE, ViewData.CODEC, (data, context) -> ClientView.receive(data));
        registrar.playToServer(ViewEdit.TYPE, ViewEdit.CODEC, MobViewCommand::handleEdit);
        registrar.playToClient(ViewEditResult.TYPE, ViewEditResult.CODEC, (result, context) -> ClientView.receive(result));
    }

    /** How many lookups or edits one player may send per second, so a modified client can't flood the server. */
    private static final int PACKETS_PER_SECOND = 10;
    /** Per player: {start of the current one-second window, packets sent in it}. */
    private static final Map<UUID, long[]> PACKET_WINDOWS = new ConcurrentHashMap<>();

    /** Whether the player may send another lookup or edit now; counts it if so. Runs on the server thread. */
    static boolean tryUse(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long[] window = PACKET_WINDOWS.computeIfAbsent(player.getUUID(), id -> new long[] {now, 0});
        if (now - window[0] >= 1000) {
            window[0] = now;
            window[1] = 0;
        }
        return ++window[1] <= PACKETS_PER_SECOND;
    }

    static void forget(Player player) {
        PACKET_WINDOWS.remove(player.getUUID());
    }

    static void clearCooldowns() {
        PACKET_WINDOWS.clear();
    }

    private static void handleRequest(Request request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(Commands.LEVEL_GAMEMASTERS)) {
            context.reply(new Result(request.spawns(), request.entity(), Status.NO_PERMISSION.ordinal(), List.of(), List.of(), Map.of()));
            return;
        }
        // Over the budget: dropped. The screen asks once per opened list or click, so a real client never gets near it.
        if (!tryUse(player)) return;
        if (request.entity().equals(ALL)) {
            Map<String, Integer> counts = new HashMap<>();
            (request.spawns() ? MobLookups.cachedSpawnCounts(player.server) : MobLookups.cachedDropCounts(player.server))
                    .forEach((type, count) -> counts.put(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), count));
            context.reply(new Result(request.spawns(), ALL, Status.OK.ordinal(), List.of(), List.of(), counts));
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(request.entity());
        EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) {
            context.reply(new Result(request.spawns(), request.entity(), Status.UNKNOWN_ENTITY.ordinal(), List.of(), List.of(), Map.of()));
            return;
        }
        if (request.spawns()) {
            List<SpawnEntry> spawns = MobLookups.spawns(player.server, type).stream()
                    .map(s -> new SpawnEntry(s.dimension().toString(), s.biomeId(), s.data().getWeight().asInt(), s.data().minCount, s.data().maxCount))
                    .toList();
            context.reply(new Result(true, request.entity(), Status.OK.ordinal(), List.of(), spawns, Map.of()));
        } else {
            List<String> drops = MobLookups.drops(player.server, type).stream().map(ResourceLocation::toString).toList();
            context.reply(new Result(false, request.entity(), Status.OK.ordinal(), drops, List.of(), Map.of()));
        }
    }
}
