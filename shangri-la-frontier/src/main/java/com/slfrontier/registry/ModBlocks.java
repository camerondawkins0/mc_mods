package com.slfrontier.registry;

import com.slfrontier.SLFrontier;
import com.slfrontier.block.NightslayerAltarBlock;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SLFrontier.MODID);

    /** The ore the Secondil weapon shop asked Sunraku to bring from the Diremarsh Waste. */
    public static final DeferredBlock<Block> GRAY_ORE = BLOCKS.register("gray_ore",
            () -> new DropExperienceBlock(UniformInt.of(1, 3),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE).mapColor(MapColor.COLOR_GRAY)));

    /** Red spider lilies bloom in the Hidden Garden where Setsuna waits. */
    public static final DeferredBlock<Block> RED_SPIDER_LILY = BLOCKS.register("red_spider_lily",
            () -> new FlowerBlock(MobEffects.WITHER, 4f,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY).mapColor(MapColor.COLOR_RED)));

    public static final DeferredBlock<Block> PRISMATIC_CRYSTAL = BLOCKS.register("prismatic_crystal",
            () -> new TransparentBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5f)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(s -> 10)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> PRISMATIC_CLUSTER = BLOCKS.register("prismatic_cluster",
            () -> new AmethystClusterBlock(7f, 3f, BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .lightLevel(s -> 7)
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<Block> NIGHTSLAYER_ALTAR = BLOCKS.register("nightslayer_altar",
            () -> new NightslayerAltarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.REINFORCED_DEEPSLATE)
                    .mapColor(MapColor.COLOR_BLACK)
                    .lightLevel(s -> 4)));

    private ModBlocks() {
    }
}
