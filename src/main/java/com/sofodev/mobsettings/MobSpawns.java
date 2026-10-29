package com.sofodev.mobsettings;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class MobSpawns implements BiomeModifier {
    private static final MobSpawns INSTANCE = new MobSpawns();
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, MobSettings.MODID);
    private static final Supplier<MapCodec<MobSpawns>> CODEC =
            SERIALIZERS.register("configured_spawns", () -> MapCodec.unit(INSTANCE));

    static void register(IEventBus bus) { SERIALIZERS.register(bus); }
    @Override public MapCodec<? extends BiomeModifier> codec() { return CODEC.get(); }

    @Override
    public void modify(RegistryAccess registries, Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD && phase != Phase.REMOVE) return;
        String biomeId = biome.unwrapKey().map(key -> key.identifier().toString()).orElse("");
        if (phase == Phase.REMOVE) {
            for (String line : MSConfig.REMOVE_SPAWNS.get()) {
                String[] fields = line.split("\\|", -1);
                if ((fields.length != 2 && fields.length != 3) || !matches(fields[1], biomeId)) continue;
                String target = fields[0];
                if (!target.equals("*") && !registered(target)) continue;
                double percent = 100;
                if (fields.length == 3) {
                    try {
                        percent = Double.parseDouble(fields[2]);
                    } catch (NumberFormatException ex) { continue; }
                    if (!Double.isFinite(percent) || percent < 0 || percent > 100) continue;
                }
                double keep = 1 - percent / 100;
                Predicate<Weighted<MobSpawnSettings.SpawnerData>> isTarget = entry ->
                        target.equals("*") || BuiltInRegistries.ENTITY_TYPE.getKey(entry.value().type()).toString().equals(target);
                // Removing from all spawn categories also handles modded categories.
                for (MobCategory category : MobCategory.values()) {
                    WeightedList.Builder<MobSpawnSettings.SpawnerData> spawner = builder.getMobSpawnSettings().getSpawner(category);
                    List<Weighted<MobSpawnSettings.SpawnerData>> matched = spawner.getList().stream().filter(isTarget).toList();
                    spawner.removeIf(isTarget);
                    for (Weighted<MobSpawnSettings.SpawnerData> entry : matched) {
                        int weight = (int) Math.round(entry.weight() * keep);
                        if (weight > 0) spawner.add(entry.value(), weight);
                    }
                }
            }
        } else {
            for (String line : MSConfig.ADD_SPAWNS.get()) {
                String[] fields = line.split("\\|", -1);
                if (fields.length != 5 || !matches(fields[1], biomeId)) continue;
                Identifier id = Identifier.tryParse(fields[0]);
                if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) continue;
                final int weight, min, max;
                try {
                    weight = Integer.parseInt(fields[2]);
                    min = Integer.parseInt(fields[3]);
                    max = Integer.parseInt(fields[4]);
                } catch (NumberFormatException ex) { continue; }
                if (weight <= 0 || min <= 0 || max < min || weight > 100000 || max > 1000) continue;
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
                if (type.getCategory() == MobCategory.MISC) {
                    // Vanilla's SpawnerData ctor silently swaps MISC-category types to PIG; skip instead of spawning the wrong mob.
                    continue;
                }
                builder.getMobSpawnSettings().addSpawn(type, weight, min, max);
            }
        }
    }

    private static boolean matches(String rule, String actual) { return rule.equals("*") || rule.equals(actual); }
    private static boolean registered(String name) {
        Identifier id = Identifier.tryParse(name);
        return id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }
}
