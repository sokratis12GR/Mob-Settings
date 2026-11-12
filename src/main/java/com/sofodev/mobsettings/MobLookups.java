package com.sofodev.mobsettings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Server-side lookups of what a mob drops and where it spawns, shared by the commands and the config screen. */
public final class MobLookups {
    private MobLookups() {}

    /** A biome of a dimension the mob spawns in, with every rule applied. {@code total} is the category's weight. */
    public record Spawn(ResourceLocation dimension, Holder<Biome> biome, MobSpawnSettings.SpawnerData data, int total) {
        public String biomeId() {
            return biome.unwrapKey().map(key -> key.location().toString()).orElse("unknown");
        }
    }

    /**
     * The items the mob's loot table can drop, sorted by ID. Read from the encoded table, so every entry type that
     * names an item, item tag or nested table is found without reaching into loot internals.
     */
    public static List<ResourceLocation> drops(MinecraftServer server, EntityType<?> type) {
        Set<ResourceLocation> items = new TreeSet<>();
        RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        collect(server, ops, type.getDefaultLootTable(), items, new HashSet<>());
        return List.copyOf(items);
    }

    private static void collect(MinecraftServer server, RegistryOps<JsonElement> ops, ResourceKey<LootTable> key,
                                Set<ResourceLocation> items, Set<ResourceKey<LootTable>> seen) {
        if (!seen.add(key)) return;
        LootTable table = server.reloadableRegistries().getLootTable(key);
        if (table == LootTable.EMPTY) return;
        LootTable.DIRECT_CODEC.encodeStart(ops, table).result()
                .ifPresent(json -> walk(server, ops, json, items, seen));
    }

    private static void walk(MinecraftServer server, RegistryOps<JsonElement> ops, JsonElement json,
                             Set<ResourceLocation> items, Set<ResourceKey<LootTable>> seen) {
        if (json.isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray()) walk(server, ops, element, items, seen);
            return;
        }
        if (!json.isJsonObject()) return;
        JsonObject object = json.getAsJsonObject();
        String type = string(object, "type");
        JsonElement name = object.get("name");
        if (type != null && name != null) {
            switch (ResourceLocation.parse(type).toString()) {
                case "minecraft:item" -> {
                    ResourceLocation id = ResourceLocation.tryParse(name.getAsString());
                    if (id != null && !id.equals(BuiltInRegistries.ITEM.getDefaultKey())) items.add(id);
                }
                case "minecraft:tag" -> {
                    ResourceLocation id = ResourceLocation.tryParse(name.getAsString());
                    if (id != null) {
                        for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id))) {
                            item.unwrapKey().ifPresent(k -> items.add(k.location()));
                        }
                    }
                }
                case "minecraft:loot_table" -> {
                    // Either the ID of another table or an inline one.
                    if (name.isJsonPrimitive()) {
                        ResourceLocation id = ResourceLocation.tryParse(name.getAsString());
                        if (id != null) collect(server, ops, ResourceKey.create(Registries.LOOT_TABLE, id), items, seen);
                    } else {
                        walk(server, ops, name, items, seen);
                    }
                }
                default -> {}
            }
        }
        // Pools, composite children and anything else that can hold entries.
        for (var entry : object.entrySet()) {
            if (!entry.getKey().equals("name")) walk(server, ops, entry.getValue(), items, seen);
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    /**
     * Where the mob spawns naturally in a dimension: its biome spawn lists plus the dimension's rules. Structure
     * spawns (fortresses, witch huts...) aren't included, as they depend on the position.
     */
    public static List<Spawn> spawns(ServerLevel level, EntityType<?> type) {
        ResourceLocation dimension = level.dimension().location();
        MobCategory category = type.getCategory();
        List<Spawn> result = new ArrayList<>();
        List<Holder<Biome>> biomes = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
                .sorted((a, b) -> id(a).compareTo(id(b))).toList();
        for (Holder<Biome> biome : biomes) {
            LevelEvent.PotentialSpawns spawns = new LevelEvent.PotentialSpawns(level, category, BlockPos.ZERO,
                    biome.value().getMobSettings().getMobs(category));
            MobSpawns.applyDimensionRules(spawns, dimension, () -> biome);
            int total = 0;
            for (MobSpawnSettings.SpawnerData entry : spawns.getSpawnerDataList()) total += entry.getWeight().asInt();
            for (MobSpawnSettings.SpawnerData entry : spawns.getSpawnerDataList()) {
                if (entry.type == type) result.add(new Spawn(dimension, biome, entry, total));
            }
        }
        return result;
    }

    /** {@link #spawns(ServerLevel, EntityType)} for every loaded dimension. */
    public static List<Spawn> spawns(MinecraftServer server, EntityType<?> type) {
        List<Spawn> result = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) result.addAll(spawns(level, type));
        return result;
    }

    /** For the mob list: how many items each mob's loot table can drop, for mobs that drop any. */
    public static Map<EntityType<?>, Integer> dropCounts(MinecraftServer server) {
        Map<EntityType<?>, Integer> counts = new HashMap<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            int count = drops(server, type).size();
            if (count > 0) counts.put(type, count);
        }
        return counts;
    }

    /** For the mob list: in how many biomes (across all dimensions) each mob spawns naturally, after the rules. */
    public static Map<EntityType<?>, Integer> spawnCounts(MinecraftServer server) {
        Map<EntityType<?>, Integer> counts = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimension = level.dimension().location();
            for (Holder<Biome> biome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
                Set<EntityType<?>> here = new HashSet<>();
                for (MobCategory category : MobCategory.values()) {
                    LevelEvent.PotentialSpawns spawns = new LevelEvent.PotentialSpawns(level, category, BlockPos.ZERO,
                            biome.value().getMobSettings().getMobs(category));
                    MobSpawns.applyDimensionRules(spawns, dimension, () -> biome);
                    for (MobSpawnSettings.SpawnerData entry : spawns.getSpawnerDataList()) here.add(entry.type);
                }
                for (EntityType<?> type : here) counts.merge(type, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static String id(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.location().toString()).orElse("unknown");
    }
}
