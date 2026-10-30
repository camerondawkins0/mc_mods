package com.slfrontier.world.design;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Reads one channel of the designed Frontier map at a column: the ground height, how rough the
 * ground may get, or how craggy. The noise router (data/slfrontier/worldgen/noise_settings)
 * combines these with detail noise into the terrain.
 */
public record DesignDensity(Channel channel) implements DensityFunction.SimpleFunction {
    public static final MapCodec<DesignDensity> MAP_CODEC =
            Channel.CODEC.fieldOf("channel").xmap(DesignDensity::new, DesignDensity::channel);
    public static final KeyDispatchDataCodec<DesignDensity> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    public enum Channel implements StringRepresentable {
        HEIGHT("height", 0, 320), ROUGHNESS("roughness", 0, 32), CRAG("crag", 0, 1);

        public static final Codec<Channel> CODEC = StringRepresentable.fromEnum(Channel::values);
        private final String name;
        final double min, max;

        Channel(String name, double min, double max) {
            this.name = name;
            this.min = min;
            this.max = max;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    @Override
    public double compute(FunctionContext ctx) {
        FrontierDesign design = FrontierDesign.get();
        return switch (channel) {
            case HEIGHT -> design.height(ctx.blockX(), ctx.blockZ());
            case ROUGHNESS -> design.roughness(ctx.blockX(), ctx.blockZ());
            case CRAG -> design.crag(ctx.blockX(), ctx.blockZ());
        };
    }

    @Override
    public double minValue() {
        return channel.min;
    }

    @Override
    public double maxValue() {
        return channel.max;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
