package com.sofodev.mobsettings;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.criterion.NbtPredicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;

/**
 * EXPERIMENTAL. The screen side of {@code /mobsettings inspect}: sends a mob's data to the player's client, which
 * opens the inspect screen, and handles the screen's edits. Needs operator permission, the experimentalInspectScreen
 * option, and Mob Settings on the client; otherwise inspect prints to chat.
 */
public final class MobViewCommand {
    private MobViewCommand() {}

    /** The player to open the screen for, or null when inspect should print to chat instead. */
    static @Nullable ServerPlayer screenViewer(CommandSourceStack source) {
        if (!MSConfig.EXPERIMENTAL_INSPECT_SCREEN.get()) return null;
        return source.getPlayer() instanceof ServerPlayer player && player.connection.hasChannel(MobNetwork.ViewData.TYPE) ? player : null;
    }

    /** Opens the screen on a live mob; overview is what the chat inspect would print. */
    static void viewLive(ServerPlayer player, LivingEntity entity, List<Component> overview) {
        CompoundTag nbt = save(entity);
        List<MobNetwork.ViewVariant> variants = List.of(new MobNetwork.ViewVariant("Live", "", -1, nbt));
        Identifier dimension = entity.level().dimension().identifier();
        PacketDistributor.sendToPlayer(player, new MobNetwork.ViewData(true, id(entity.getType()), entity.getId(), dimension.toString(),
                variants, InspectInfo.build(player.level().getServer(), entity.getType(), dimension, overview)));
    }

    /** Opens the screen on a type's spawn data. Returns false if the type isn't a mob. */
    static boolean viewSpawn(ServerPlayer player, EntityType<?> type, List<Component> overview) {
        ServerLevel level = player.level();
        if (!(type.create(level, EntitySpawnReason.LOAD) instanceof Mob plain)) return false;
        List<MobNetwork.ViewVariant> variants = new ArrayList<>();
        // Without any line: what the type looks like before vanilla rolls its gear.
        variants.add(new MobNetwork.ViewVariant("No addSpawns line", "", -1, save(plain)));
        plain.discard();
        List<? extends String> lines = MSConfig.ADD_SPAWNS.getRaw();
        for (int i = 0; i < lines.size(); i++) {
            Rules.AddSpawn rule = Rules.ADD_SPAWNS.parse(lines.get(i));
            if (rule == null || rule.entity() != type) continue;
            if (!(type.create(level, EntitySpawnReason.LOAD) instanceof Mob mob)) continue;
            CompoundTag data = rule.template() == null ? null : rule.template().data(level.registryAccess());
            if (data != null) load(mob, NbtPredicate.getEntityTagToCompare(mob).merge(data));
            variants.add(new MobNetwork.ViewVariant("addSpawns line " + (i + 1), lines.get(i), i, save(mob)));
            mob.discard();
        }
        Identifier dimension = level.dimension().identifier();
        PacketDistributor.sendToPlayer(player, new MobNetwork.ViewData(false, id(type), -1, dimension.toString(),
                variants, InspectInfo.build(player.level().getServer(), type, dimension, overview)));
        return true;
    }

    /** How far a live mob can be from the player for an edit to be applied: further than the reach, since it may walk off. */
    private static final double APPLY_RANGE = 64;

    /** Saves or applies an edit from the view screen. Everything is checked again here: the client can't be trusted. */
    static void handleEdit(MobNetwork.ViewEdit edit, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        String error = !MSConfig.EXPERIMENTAL_INSPECT_SCREEN.get() ? "The inspect screen is turned off on this server."
                : !Commands.LEVEL_GAMEMASTERS.check(player.permissions()) ? "Only operators can edit mobs." : null;
        if (error != null) {
            context.reply(new MobNetwork.ViewEditResult(edit.apply(), false, error, edit.index(), edit.line()));
            return;
        }
        if (!MobNetwork.tryUse(player)) {
            context.reply(new MobNetwork.ViewEditResult(edit.apply(), false, "Too fast, try again in a moment.", edit.index(), edit.line()));
            return;
        }
        context.reply(edit.apply() ? apply(player, edit) : save(player, edit));
    }

    private static MobNetwork.ViewEditResult save(ServerPlayer player, MobNetwork.ViewEdit edit) {
        Rules.AddSpawn rule = Rules.ADD_SPAWNS.parse(edit.line());
        if (rule == null) return failed(edit, "That isn't a valid addSpawns line.");
        if (rule.template() != null && rule.template().data(player.registryAccess()) == null) {
            return failed(edit, "The line's equipment isn't valid on this server.");
        }
        List<String> lines = new ArrayList<>(MSConfig.ADD_SPAWNS.getRaw());
        int index = edit.index();
        if (index >= 0) {
            // Someone may have edited the config since the screen opened; don't overwrite a different line.
            if (index >= lines.size() || !lines.get(index).equals(edit.expected())) {
                return failed(edit, "addSpawns line " + (index + 1) + " changed since the screen opened. Open it again.");
            }
            lines.set(index, edit.line());
        } else {
            if (lines.contains(edit.line())) return failed(edit, "That line is already in addSpawns.");
            lines.add(edit.line());
            index = lines.size() - 1;
        }
        MSConfig.ADD_SPAWNS.set(lines);
        MSConfig.SPEC.save();
        MobLookups.clearCache();
        LogUtils.getLogger().info("{} saved addSpawns line {}: {}", player.getPlainTextName(), index + 1, edit.line());
        return new MobNetwork.ViewEditResult(false, true, "Saved as addSpawns line " + (index + 1), index, edit.line());
    }

    private static MobNetwork.ViewEditResult apply(ServerPlayer player, MobNetwork.ViewEdit edit) {
        Entity entity = player.level().getEntity(edit.networkId());
        if (!(entity instanceof LivingEntity living) || entity instanceof ServerPlayer || !entity.isAlive()) {
            return failed(edit, "The mob is gone.");
        }
        if (entity.distanceToSqr(player) > APPLY_RANGE * APPLY_RANGE) return failed(edit, "The mob is too far away.");
        // Parse every slot first, so a bad stack changes nothing.
        java.util.Map<net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.ItemStack> stacks = new java.util.EnumMap<>(net.minecraft.world.entity.EquipmentSlot.class);
        for (var entry : edit.equipment().entrySet()) {
            net.minecraft.world.entity.EquipmentSlot slot;
            try {
                slot = net.minecraft.world.entity.EquipmentSlot.byName(entry.getKey());
            } catch (IllegalArgumentException ex) {
                return failed(edit, "Unknown slot " + entry.getKey());
            }
            if (!living.canUseSlot(slot) && !entry.getValue().isEmpty()) return failed(edit, "This mob has no " + entry.getKey() + " slot.");
            var stack = entry.getValue().isEmpty() ? net.minecraft.world.item.ItemStack.EMPTY
                    : net.minecraft.world.item.ItemStack.CODEC.parse(player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), entry.getValue()).result().orElse(null);
            if (stack == null) return failed(edit, "The " + entry.getKey() + " item isn't valid on this server.");
            stacks.put(slot, stack);
        }
        stacks.forEach(living::setItemSlot);
        // Edited gear should stay on the mob instead of it despawning with it.
        if (living instanceof Mob mob) mob.setPersistenceRequired();
        return new MobNetwork.ViewEditResult(true, true, "Applied to the mob (it won't despawn now)", -1, "");
    }

    private static MobNetwork.ViewEditResult failed(MobNetwork.ViewEdit edit, String message) {
        return new MobNetwork.ViewEditResult(edit.apply(), false, message, edit.index(), edit.line());
    }

    private static String id(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    /** The entity's saved data, without its ID. */
    private static CompoundTag save(Entity entity) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, entity.registryAccess());
            entity.saveWithoutId(output);
            return output.buildResult();
        }
    }

    private static void load(Entity entity, CompoundTag data) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            entity.load(TagValueInput.create(reporter, entity.registryAccess(), data));
        }
    }
}
