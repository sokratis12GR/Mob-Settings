package com.sofodev.mobsettings;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /mobsettings}: lists the subcommands, and {@code /mobsettings discord} links SoFoDev's Workshop.
 * The root is open to everyone; each operator subcommand carries its own permission check.
 * Registered before the other command classes so this node's (lack of) requirement wins.
 */
public final class MobSettingsCommand {
    static final String DISCORD_INVITE = "https://discord.gg/JCWbJvA";

    private MobSettingsCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(MobSettings.MODID)
                .executes(MobSettingsCommand::help)
                .then(Commands.literal("help").executes(MobSettingsCommand::help))
                .then(Commands.literal("discord").executes(MobSettingsCommand::discord)));
    }

    static boolean isOp(CommandSourceStack source) {
        return source.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String root = CommandText.ROOT;
        source.sendSuccess(() -> Component.literal("Mob Settings commands (click to fill in):").withStyle(ChatFormatting.GOLD), false);
        if (isOp(source)) {
            entry(source, root + " spawns ", "spawns <entity> [all|<dimension>] [page]", "Biomes a mob spawns in");
            entry(source, root + " spawns here", "spawns here", "What can spawn where you stand");
            entry(source, root + " drops ", "drops <entity> [dimension]", "Loot table items and drop rules");
            entry(source, root + " inspect", "inspect [target]", "The looked-at mob, its attributes and rules");
            entry(source, root + " inspect type ", "inspect type <entity>", "A mob type's rules, spawns and drops");
            if (MSConfig.EXPERIMENTAL_INSPECT_SCREEN.get()) {
                entry(source, root + " inspect chat", "inspect chat [target]", "(Experimental screen on) Print to chat instead");
            }
        }
        entry(source, root + " discord", "discord", "Join SoFoDev's Workshop on Discord");
        return 1;
    }

    private static void entry(CommandSourceStack source, String command, String usage, String description) {
        Component line = Component.literal("  ").append(CommandText.suggest(usage, command, description))
                .append(Component.literal("  " + description).withStyle(ChatFormatting.GRAY));
        source.sendSuccess(() -> line, false);
    }

    private static int discord(CommandContext<CommandSourceStack> context) {
        Component line = Component.literal("Questions, bugs or ideas? Join ").withStyle(ChatFormatting.GOLD)
                .append(CommandText.url("SoFoDev's Workshop", DISCORD_INVITE))
                .append(Component.literal(" on Discord.").withStyle(ChatFormatting.GOLD));
        context.getSource().sendSuccess(() -> line, false);
        return 1;
    }
}
