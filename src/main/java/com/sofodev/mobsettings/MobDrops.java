package com.sofodev.mobsettings;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Removes, then adds, registry-ID-based drops for any living entity, including modded ones. */
public final class MobDrops {
    private MobDrops() {}

    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        EntityType<?> type = entity.getType();
        // Removal runs first so extra drops are never stripped, e.g. "*|*" plus a few extra drops replaces all loot.
        for (Rules.RemoveDrop rule : Rules.REMOVE_DROPS.get()) {
            if (!Rules.matches(rule.entity(), type)) continue;
            event.getDrops().removeIf(itemEntity ->
                    (rule.item() == null || itemEntity.getItem().is(rule.item()))
                            && entity.getRandom().nextDouble() < rule.chance());
        }
        if (MSConfig.DROP_ONLY_ON_PLAYER_KILL.get() && !(event.getSource().getEntity() instanceof Player)) return;
        for (Rules.ExtraDrop rule : Rules.EXTRA_DROPS.get()) {
            if (!Rules.matches(rule.entity(), type) || entity.getRandom().nextDouble() >= rule.chance()) continue;
            int remaining = rule.count();
            while (remaining > 0) {
                int size = Math.min(remaining, rule.item().getDefaultMaxStackSize());
                if (size <= 0) break;
                event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                        new ItemStack(rule.item(), size)));
                remaining -= size;
            }
        }
    }
}
