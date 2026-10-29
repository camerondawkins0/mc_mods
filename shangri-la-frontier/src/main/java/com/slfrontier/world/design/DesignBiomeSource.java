package com.slfrontier.world.design;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Biomes painted from the designed Frontier map instead of climate noise. */
public class DesignBiomeSource extends BiomeSource {
    public static final MapCodec<DesignBiomeSource> CODEC =
            Biome.CODEC.listOf().fieldOf("biomes").xmap(DesignBiomeSource::new, s -> s.biomes);

    private final List<Holder<Biome>> biomes;
    private volatile Map<String, Holder<Biome>> byName;

    public DesignBiomeSource(List<Holder<Biome>> biomes) {
        this.biomes = biomes;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        Map<String, Holder<Biome>> map = byName;
        if (map == null) {
            map = new HashMap<>();
            for (Holder<Biome> holder : biomes) {
                Map<String, Holder<Biome>> target = map;
                holder.unwrapKey().ifPresent(key -> target.put(key.location().getPath(), holder));
            }
            byName = map;
        }
        Holder<Biome> biome = map.get(FrontierDesign.get().biomeAt(qx << 2, qz << 2));
        return biome != null ? biome : biomes.get(0);
    }
}
