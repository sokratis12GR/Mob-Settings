package com.sofodev.mobsettings;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Applies registry-ID-based extra drops to any living entity, including modded ones. */
public final class MobDrops {
    private MobDrops() {}

    public static void onLivingDrops(LivingDropsEvent event) {
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
        removeConfiguredDrops(event, entityId);
        if (MSConfig.DROP_ONLY_ON_PLAYER_KILL.get() && !(event.getSource().getEntity() instanceof Player)) return;
        for (String line : MSConfig.EXTRA_DROPS.get()) {
            String[] fields = line.split("\\|", -1);
            if (fields.length != 4 || !(fields[0].equals("*") || fields[0].equals(entityId))) continue;
            Identifier itemId = Identifier.tryParse(fields[1]);
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) continue;
            final int count;
            final double chance;
            try {
                count = Integer.parseInt(fields[2]);
                chance = Double.parseDouble(fields[3]);
            } catch (NumberFormatException ex) { continue; }
            if (count <= 0 || count > 100000 || !Double.isFinite(chance) || chance < 0 || chance > 1) continue;
            if (event.getEntity().getRandom().nextDouble() >= chance) continue;
            Item item = BuiltInRegistries.ITEM.getValue(itemId);
            int remaining = count;
            while (remaining > 0) {
                int size = Math.min(remaining, item.getDefaultMaxStackSize());
                if (size <= 0) break;
                event.getDrops().add(new ItemEntity(event.getEntity().level(),
                        event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(),
                        new ItemStack(item, size)));
                remaining -= size;
            }
        }
    }

    private static void removeConfiguredDrops(LivingDropsEvent event, String entityId) {
        for (String line : MSConfig.REMOVE_DROPS.get()) {
            String[] fields = line.split("\\|", -1);
            if ((fields.length != 2 && fields.length != 3) || !(fields[0].equals("*") || fields[0].equals(entityId))) continue;
            String itemRule = fields[1];
            final double chance;
            try {
                chance = fields.length == 3 ? Double.parseDouble(fields[2]) : 1.0;
            } catch (NumberFormatException ex) { continue; }
            if (!Double.isFinite(chance) || chance < 0 || chance > 1) continue;
            event.getDrops().removeIf(itemEntity -> {
                if (!itemRule.equals("*")) {
                    Identifier itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
                    if (!itemId.toString().equals(itemRule)) return false;
                }
                return event.getEntity().getRandom().nextDouble() < chance;
            });
        }
    }
}
