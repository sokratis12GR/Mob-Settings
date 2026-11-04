package com.sofodev.mobsettings;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Removes, then adds, registry-ID-based drops for any living entity except players, including modded ones. */
public final class MobDrops {
    private MobDrops() {}

    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        // A player's death drops are their own items, so no rule may touch them.
        if (entity instanceof Player) return;
        EntityType<?> type = entity.getType();
        ResourceLocation dimension = entity.level().dimension().location();
        // Removal runs first so extra drops are never stripped, e.g. "*|*" plus a few extra drops replaces all loot.
        for (Rules.RemoveDrop rule : Rules.REMOVE_DROPS.get()) {
            if (!Rules.matchesMob(rule.entity(), type) || !Rules.matches(rule.dimension(), dimension)) continue;
            event.getDrops().removeIf(itemEntity ->
                    (rule.item() == null || itemEntity.getItem().is(rule.item()))
                            && entity.getRandom().nextDouble() < rule.chance());
        }
        if (MSConfig.DROP_ONLY_ON_PLAYER_KILL.get() && !(event.getSource().getEntity() instanceof Player)) return;
        for (Rules.ExtraDrop rule : Rules.EXTRA_DROPS.get()) {
            if (!Rules.matchesMob(rule.entity(), type) || !Rules.matches(rule.dimension(), dimension) || entity.getRandom().nextDouble() >= rule.chance()) continue;
            ItemStack stack = rule.template() == null ? new ItemStack(rule.item()) : rule.template().create(entity.level().registryAccess());
            if (stack == null) continue;
            int remaining = rule.count() + (rule.looting() ? lootingBonus(event) : 0);
            while (remaining > 0) {
                int size = Math.min(remaining, stack.getMaxStackSize());
                if (size <= 0) break;
                event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack.copyWithCount(size)));
                remaining -= size;
            }
        }
    }

    /** Like vanilla's looting bonus: 0 to 1 extra items per level of the killer's Looting. */
    private static int lootingBonus(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)) return 0;
        LivingEntity entity = event.getEntity();
        Holder<Enchantment> looting = entity.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
        return entity.getRandom().nextInt(EnchantmentHelper.getEnchantmentLevel(looting, killer) + 1);
    }
}
