package com.sofodev.mobsettings;

import static com.sofodev.mobsettings.CommandText.number;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;

/**
 * EXPERIMENTAL. Builds the read-only tabs of the inspect screen: spawns after rules (tagged with what changed them),
 * drops with removeDrops marks, and every config line that covers the mob. Effective rules come from {@code get()};
 * lines saved since the world loaded come from {@code getRaw()} and are marked pending a restart.
 */
final class InspectInfo {
    private InspectInfo() {}

    static MobNetwork.ViewInfo build(MinecraftServer server, EntityType<?> type, Identifier dimension, List<Component> overview) {
        return new MobNetwork.ViewInfo(overview, spawns(server, type), drops(server, type, dimension), rules(type));
    }

    private static List<MobNetwork.SpawnRow> spawns(MinecraftServer server, EntityType<?> type) {
        List<MobNetwork.SpawnRow> rows = new ArrayList<>();
        for (MobLookups.Spawn spawn : MobLookups.spawns(server, type)) {
            List<String> tags = new ArrayList<>();
            for (Rules.AddSpawn rule : Rules.ADD_SPAWNS.get()) {
                if (rule.entity() == type && Rules.matches(rule.dimension(), spawn.dimension()) && rule.biome().matches(spawn.biome())) {
                    tags.add("added");
                    break;
                }
            }
            for (Rules.RemoveSpawn rule : Rules.REMOVE_SPAWNS.get()) {
                if (Rules.matches(rule.entity(), type) && Rules.matches(rule.dimension(), spawn.dimension()) && rule.biome().matches(spawn.biome())) {
                    tags.add("reduced to " + number(rule.keep() * 100) + "%");
                }
            }
            for (Rules.GroupSize rule : Rules.GROUP_SIZES.get()) {
                if (Rules.matches(rule.entity(), type) && Rules.matches(rule.dimension(), spawn.dimension()) && rule.biome().matches(spawn.biome())) {
                    tags.add("group resized");
                    break;
                }
            }
            rows.add(new MobNetwork.SpawnRow(spawn.dimension().toString(), spawn.biomeId(), spawn.weight(),
                    spawn.data().minCount(), spawn.data().maxCount(), String.join(", ", tags)));
        }
        return rows;
    }

    /** Loot items, each marked if a removeDrops rule for this dimension takes it away, then the extraDrops lines. */
    private static List<MobNetwork.DropRow> drops(MinecraftServer server, EntityType<?> type, Identifier dimension) {
        List<MobNetwork.DropRow> rows = new ArrayList<>();
        List<Rules.RemoveDrop> removes = Rules.REMOVE_DROPS.get().stream()
                .filter(r -> Rules.matches(r.entity(), type) && Rules.matches(r.dimension(), dimension)).toList();
        for (Identifier item : MobLookups.drops(server, type)) {
            // Several rules stack: each removes its share of what is left.
            double kept = 1;
            boolean covered = false;
            for (Rules.RemoveDrop rule : removes) {
                if (rule.item() == null || BuiltInRegistries.ITEM.getKey(rule.item()).equals(item)) {
                    kept *= 1 - rule.chance();
                    covered = true;
                }
            }
            int removed = covered ? (int) Math.round((1 - kept) * 100) : -1;
            rows.add(new MobNetwork.DropRow(item.toString(), removed, false, ""));
        }
        for (Rules.ExtraDrop rule : Rules.EXTRA_DROPS.get()) {
            if (!Rules.matchesMob(rule.entity(), type) || !Rules.matches(rule.dimension(), dimension)) continue;
            rows.add(new MobNetwork.DropRow(BuiltInRegistries.ITEM.getKey(rule.item()).toString(), -1, true,
                    rule.count() + "x" + CommandText.chance(rule.chance()) + (rule.looting() ? ", more with Looting" : "")));
        }
        return rows;
    }

    private static List<MobNetwork.RuleRow> rules(EntityType<?> type) {
        List<MobNetwork.RuleRow> rows = new ArrayList<>();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        add(rows, Rules.ADD_SPAWNS, true, id, r -> r.entity() == type);
        add(rows, Rules.REMOVE_SPAWNS, true, id, r -> Rules.matches(r.entity(), type));
        add(rows, Rules.GROUP_SIZES, true, id, r -> Rules.matches(r.entity(), type));
        add(rows, Rules.LIGHT_SPAWN_RESTRICTIONS, false, id, r -> Rules.matches(r.entity(), type));
        add(rows, Rules.REMOVE_DROPS, false, id, r -> Rules.matches(r.entity(), type));
        add(rows, Rules.EXTRA_DROPS, false, id, r -> Rules.matchesMob(r.entity(), type));
        add(rows, Rules.ATTRIBUTE_OVERRIDES, false, id, r -> Rules.matchesMob(r.entity(), type));
        add(rows, Rules.DAMAGE_MODIFIERS, false, id, r -> Rules.matchesMob(r.entity(), type));
        add(rows, Rules.EXPERIENCE_MODIFIERS, false, id, r -> Rules.matchesMob(r.entity(), type));
        add(rows, Rules.DESPAWN_RANGES, false, id, r -> Rules.matchesMob(r.entity(), type));
        return rows;
    }

    /**
     * The list's lines covering the mob. For lists that need a world restart, a saved line the game isn't using yet
     * is pending, and a line the game still uses but that was taken out of the file is shown as removed-pending.
     * Invalid lines are shown when they mention the mob's ID, so typos can be spotted.
     */
    private static <T> void add(List<MobNetwork.RuleRow> rows, Rules.Cache<T> cache, boolean restart, String id, Predicate<T> covers) {
        List<? extends String> saved = cache.config().getRaw();
        List<? extends String> loaded = cache.config().get();
        for (int i = 0; i < saved.size(); i++) {
            String line = saved.get(i);
            if (line.isBlank()) continue;
            T rule = cache.parse(line);
            if (rule == null) {
                if (line.contains(id)) rows.add(new MobNetwork.RuleRow(cache.key(), i, line, MobNetwork.INVALID));
                continue;
            }
            if (!covers.test(rule)) continue;
            int state = restart && !loaded.contains(line) ? MobNetwork.PENDING_RESTART : MobNetwork.ACTIVE;
            rows.add(new MobNetwork.RuleRow(cache.key(), i, line, state));
        }
        if (!restart) return;
        for (String line : loaded) {
            if (saved.contains(line)) continue;
            T rule = cache.parse(line);
            if (rule != null && covers.test(rule)) rows.add(new MobNetwork.RuleRow(cache.key(), -1, line, MobNetwork.REMOVED_PENDING_RESTART));
        }
    }
}
