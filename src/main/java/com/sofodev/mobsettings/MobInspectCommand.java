package com.sofodev.mobsettings;

import static com.sofodev.mobsettings.CommandText.number;
import static com.sofodev.mobsettings.CommandText.shortId;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /mobsettings inspect [target]}: what Mob Settings sees for one mob — the one looked at, or a selector — and
 * which rules apply to it. Ends with a button for the full NBT through {@code /data get entity}.
 */
public final class MobInspectCommand {
    /** How far away a looked-at mob can be. */
    static final double REACH = 20;

    private MobInspectCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(MobSettings.MODID)
                .then(Commands.literal("inspect")
                        .requires(MobSettingsCommand::isOp)
                        .executes(c -> inspect(c.getSource(), lookedAt(c.getSource()), false))
                        .then(Commands.literal("chat")
                                .executes(c -> inspect(c.getSource(), lookedAt(c.getSource()), true))
                                .then(Commands.literal("type")
                                        .then(Commands.argument("entity", ResourceArgument.resource(event.getBuildContext(), Registries.ENTITY_TYPE))
                                                .executes(c -> inspectType(c.getSource(),
                                                        ResourceArgument.getResource(c, "entity", Registries.ENTITY_TYPE).value(), true))))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(c -> inspect(c.getSource(), EntityArgument.getEntity(c, "target"), true))))
                        .then(Commands.literal("type")
                                .then(Commands.argument("entity", ResourceArgument.resource(event.getBuildContext(), Registries.ENTITY_TYPE))
                                        .executes(c -> inspectType(c.getSource(),
                                                ResourceArgument.getResource(c, "entity", Registries.ENTITY_TYPE).value(), false))))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(c -> inspect(c.getSource(), EntityArgument.getEntity(c, "target"), false)))));
    }

    /**
     * A type with no live mob. With the screen on, opens it on the type's spawn data; otherwise prints the type's
     * category and the rules that cover it in the source's dimension.
     */
    private static int inspectType(CommandSourceStack source, EntityType<?> type, boolean chat) {
        ResourceLocation dimension = source.getLevel().dimension().location();
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty().append(type.getDescription()).append(Component.literal(" (" + shortId(entityId) + ")"))
                .withStyle(ChatFormatting.GOLD));
        lines.add(info("Category", type.getCategory().getName()));
        lines.add(info("Dimension", shortId(dimension)));
        addRules(lines, type, dimension);
        ServerPlayer viewer = chat ? null : MobViewCommand.screenViewer(source);
        if (viewer != null && MobViewCommand.viewSpawn(viewer, type, lines)) return 1;
        lines.add(CommandText.mobButtons(entityId));
        for (Component line : lines) source.sendSuccess(() -> line, false);
        return 1;
    }

    private static void addRules(List<Component> lines, EntityType<?> type, ResourceLocation dimension) {
        List<Component> rules = rules(type, dimension);
        if (!rules.isEmpty()) {
            lines.add(Component.literal("Rules that apply here:").withStyle(ChatFormatting.GOLD));
            lines.addAll(rules);
        } else {
            lines.add(Component.literal("No Mob Settings rules apply to it here.").withStyle(ChatFormatting.GRAY));
        }
    }

    static @Nullable Entity lookedAt(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1).scale(REACH));
        // Stop at the first block so mobs behind walls aren't picked.
        var block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        end = block.getLocation();
        AABB area = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, area,
                e -> !e.isSpectator() && e.isPickable(), eye.distanceToSqr(end));
        return hit == null ? null : hit.getEntity();
    }

    private static int inspect(CommandSourceStack source, @Nullable Entity entity, boolean chat) {
        if (entity == null) {
            source.sendFailure(Component.literal("Look at a mob within " + (int) REACH + " blocks, or name one: /"
                    + MobSettings.MODID + " inspect <target>"));
            return 0;
        }
        EntityType<?> type = entity.getType();
        ResourceLocation dimension = entity.level().dimension().location();
        Holder<?> biome = entity.level().getBiome(BlockPos.containing(entity.position()));
        String biomeId = biome.unwrapKey().map(k -> shortId(k.location())).orElse("unknown");
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty().append(entity.getDisplayName()).append(Component.literal(" (" + shortId(entityId) + ")"))
                .withStyle(ChatFormatting.GOLD));
        lines.add(info("Category", type.getCategory().getName()));
        lines.add(info("Dimension", shortId(dimension)));
        lines.add(info("Biome", biomeId));
        if (entity instanceof Mob mob) {
            boolean persistent = mob.isPersistenceRequired() || mob.requiresCustomPersistence();
            lines.add(info("Despawns", persistent ? "no (persistent)" : mob.removeWhenFarAway(Double.MAX_VALUE) ? "yes, when far away" : "no"));
        }
        if (entity instanceof LivingEntity living) {
            lines.add(Component.literal("Attributes:").withStyle(ChatFormatting.GOLD));
            attributes(living, type, dimension, lines);
        }
        addRules(lines, type, dimension);
        ServerPlayer viewer = chat ? null : MobViewCommand.screenViewer(source);
        if (viewer != null && entity instanceof LivingEntity living && !(entity instanceof ServerPlayer)) {
            MobViewCommand.viewLive(viewer, living, lines);
            return 1;
        }
        String command = "/data get entity " + entity.getStringUUID();
        lines.add(CommandText.link("[Show NBT]", command).append(" ").append(CommandText.mobButtons(entityId)));
        for (Component line : lines) source.sendSuccess(() -> line, false);
        return 1;
    }

    private static Component info(String label, String value) {
        return Component.literal("  " + label + ": ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    /** Every attribute the mob has: current value, its type's default when different, and whether a rule overrides it. */
    private static void attributes(LivingEntity living, EntityType<?> type, ResourceLocation dimension, List<Component> lines) {
        @Nullable AttributeSupplier defaults = DefaultAttributes.hasSupplier(type)
                ? DefaultAttributes.getSupplier(castLiving(type)) : null;
        for (Holder.Reference<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            AttributeInstance instance = living.getAttribute(attribute);
            if (instance == null) continue;
            double value = instance.getValue();
            MutableComponent line = Component.literal("  " + attribute.key().location().getPath() + ": ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(number(value)).withStyle(ChatFormatting.WHITE));
            if (defaults != null && defaults.hasAttribute(attribute)) {
                double base = defaults.getBaseValue(attribute);
                if (Math.abs(base - value) > 1e-6) line.append(Component.literal(" (default " + number(base) + ")").withStyle(ChatFormatting.DARK_GRAY));
            }
            for (Rules.AttributeOverride rule : Rules.ATTRIBUTE_OVERRIDES.get()) {
                if (rule.attribute().is(attribute.key()) && Rules.matchesMob(rule.entity(), type) && Rules.matches(rule.dimension(), dimension)) {
                    line.append(Component.literal(" ← attributeOverrides " + entity(rule.entity()) + " = " + number(rule.value()))
                            .withStyle(ChatFormatting.AQUA));
                    break;
                }
            }
            lines.add(line);
        }
    }

    @SuppressWarnings("unchecked")
    private static EntityType<? extends LivingEntity> castLiving(EntityType<?> type) {
        return (EntityType<? extends LivingEntity>) type;
    }

    /** The damage, experience, despawn and drop rules that cover this mob in this dimension. */
    private static List<Component> rules(EntityType<?> type, ResourceLocation dimension) {
        List<Component> lines = new ArrayList<>();
        for (Rules.DamageModifier rule : Rules.DAMAGE_MODIFIERS.get()) {
            if (!Rules.matchesMob(rule.entity(), type) || !Rules.matches(rule.dimension(), dimension)) continue;
            String text = (rule.dealt() != null ? "dealt ×" + number(rule.dealt()) : "")
                    + (rule.dealt() != null && rule.taken() != null ? ", " : "")
                    + (rule.taken() != null ? "taken ×" + number(rule.taken()) : "");
            lines.add(rule("damageModifiers", rule.entity(), text));
        }
        for (Rules.ExperienceModifier rule : Rules.EXPERIENCE_MODIFIERS.get()) {
            if (Rules.matchesMob(rule.entity(), type) && Rules.matches(rule.dimension(), dimension)) {
                lines.add(rule("experienceModifiers", rule.entity(), "×" + number(rule.multiplier())));
            }
        }
        for (Rules.DespawnRange rule : Rules.DESPAWN_RANGES.get()) {
            if (Rules.matchesMob(rule.entity(), type) && Rules.matches(rule.dimension(), dimension)) {
                lines.add(rule("despawnRanges", rule.entity(), number(rule.range()) + " blocks"));
            }
        }
        for (CommandText.DropRule rule : CommandText.dropRules(type, dimension)) {
            lines.add(rule(rule.list(), rule.entity(), rule.text()));
        }
        return lines;
    }

    private static Component rule(String list, Rules.EntityRule entity, String text) {
        return Component.literal("  " + list + " ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("[" + entity(entity) + "] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
    }

    /** How the rule named the mob: its ID, a tag, a whole mod or {@code *}. */
    private static String entity(Rules.EntityRule rule) {
        if (rule.type() != null) return BuiltInRegistries.ENTITY_TYPE.getKey(rule.type()).toString();
        if (rule.tag() != null) return "#" + rule.tag().location();
        return rule.namespace() != null ? rule.namespace() + ":*" : "*";
    }

}
