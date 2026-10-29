package com.slfrontier;

import com.slfrontier.network.ModNetwork;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.registry.ModBlocks;
import com.slfrontier.registry.ModCreativeTabs;
import com.slfrontier.registry.ModEffects;
import com.slfrontier.registry.ModEntities;
import com.slfrontier.registry.ModItems;
import com.slfrontier.registry.ModArmorMaterials;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shangri-La Frontier — a fan-made adventure mod.
 *
 * Put on the Full-Dive VR Headset to log into "the Frontier", a separate dimension
 * where your avatar has levels, stats, stamina and skills, and where the story with
 * Emul, Vysache, Setsuna, Wezaemon the Tombguard and Lycagon the Nightslayer unfolds.
 */
@Mod(SLFrontier.MODID)
public class SLFrontier {
    public static final String MODID = "slfrontier";
    public static final Logger LOGGER = LoggerFactory.getLogger("Shangri-La Frontier");

    public SLFrontier(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SlfConfig.CLIENT_SPEC);
        container.registerConfig(ModConfig.Type.SERVER, SlfConfig.SERVER_SPEC);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModArmorMaterials.ARMOR_MATERIALS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        com.slfrontier.world.feature.FrontierFeatures.FEATURES.register(modBus);
        com.slfrontier.world.design.DesignRegistries.register(modBus);
        modBus.addListener(ModNetwork::register);
        LOGGER.info("Connecting to Shangri-La Frontier...");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
