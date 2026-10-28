package com.slfrontier.client;

import com.slfrontier.SLFrontier;
import com.slfrontier.client.anim.AnimSet;
import com.slfrontier.client.anim.MobAnims;
import com.slfrontier.client.model.ModelDefs;
import com.slfrontier.client.model.SlfModel;
import com.slfrontier.client.render.NpcHumanoidRenderer;
import com.slfrontier.client.render.SlfMobRenderer;
import com.slfrontier.entity.SlfAnimated;
import com.slfrontier.entity.boss.WezaemonEntity;
import com.slfrontier.registry.ModEntities;
import com.slfrontier.registry.ModItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import java.util.function.BiConsumer;

@EventBusSubscriber(modid = SLFrontier.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private static final ModelLayerLocation VORPAL_BUNNY = layer("vorpal_bunny");
    private static final ModelLayerLocation RABBITFOLK = layer("rabbitfolk");
    private static final ModelLayerLocation EMPIRE_BEE = layer("empire_bee");
    private static final ModelLayerLocation QUAD_BEETLE = layer("quad_beetle");
    private static final ModelLayerLocation MUD_DIGGER = layer("mud_digger");
    private static final ModelLayerLocation LYCAGON = layer("lycagon");
    private static final ModelLayerLocation WEZAEMON = layer("wezaemon");
    private static final ModelLayerLocation BIPED = layer("biped");

    private ClientSetup() {
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(SLFrontier.id(name), "main");
    }

    private static ResourceLocation tex(String name) {
        return SLFrontier.id("textures/entity/" + name + ".png");
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModItems.FRONTIER_COMPASS.get(), ResourceLocation.withDefaultNamespace("angle"),
                new CompassItemPropertyFunction((level, stack, entity) -> {
                    LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                    return tracker == null ? null : tracker.target().orElse(null);
                })));
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        for (var key : KeyBindings.SKILLS) event.register(key);
        event.register(KeyBindings.MENU);
        event.register(KeyBindings.MAP);
        event.register(KeyBindings.MINIMAP);
    }

    @SubscribeEvent
    public static void registerGui(RegisterGuiLayersEvent event) {
        event.registerAboveAll(SLFrontier.id("frontier_hud"), FrontierHud::render);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VORPAL_BUNNY, ModelDefs::vorpalBunny);
        event.registerLayerDefinition(RABBITFOLK, ModelDefs::rabbitfolk);
        event.registerLayerDefinition(EMPIRE_BEE, ModelDefs::empireBee);
        event.registerLayerDefinition(QUAD_BEETLE, ModelDefs::quadBeetle);
        event.registerLayerDefinition(MUD_DIGGER, ModelDefs::mudDigger);
        event.registerLayerDefinition(LYCAGON, ModelDefs::lycagon);
        event.registerLayerDefinition(WEZAEMON, ModelDefs::wezaemon);
        event.registerLayerDefinition(BIPED, () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 64));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        slf(event, ModEntities.VORPAL_BUNNY.get(), VORPAL_BUNNY, MobAnims.VORPAL_BUNNY, "vorpal_bunny", 0.3f, 1f, null);
        slf(event, ModEntities.EMPIRE_BEE.get(), EMPIRE_BEE, MobAnims.EMPIRE_BEE, "empire_bee", 0.4f, 1f, null);
        slf(event, ModEntities.EMPIRE_BEE_QUEEN.get(), EMPIRE_BEE, MobAnims.EMPIRE_BEE, "empire_bee_queen", 0.8f, 2f, null);
        slf(event, ModEntities.QUAD_BEETLE.get(), QUAD_BEETLE, MobAnims.QUAD_BEETLE, "quad_beetle", 1.0f, 1f, null);
        slf(event, ModEntities.MUD_DIGGER.get(), MUD_DIGGER, MobAnims.MUD_DIGGER, "mud_digger", 1.1f, 1f, null);
        slf(event, ModEntities.LYCAGON.get(), LYCAGON, MobAnims.LYCAGON, "lycagon", 1.5f, 1f, null);
        slf(event, ModEntities.SHADOW_WOLF.get(), LYCAGON, MobAnims.SHADOW_WOLF, "shadow_wolf", 0.5f, 0.38f, null);
        BiConsumer<WezaemonEntity, SlfModel<WezaemonEntity>> katana =
                (e, m) -> m.part("katana").ifPresent(p -> p.visible = !e.isDisarmed());
        slf(event, ModEntities.WEZAEMON.get(), WEZAEMON, MobAnims.WEZAEMON, "wezaemon", 0.8f, 1f, katana);
        slf(event, ModEntities.EMUL.get(), RABBITFOLK, MobAnims.RABBITFOLK, "emul", 0.3f, 1f, null);
        slf(event, ModEntities.BILAC.get(), RABBITFOLK, MobAnims.RABBITFOLK, "bilac", 0.35f, 1.1f, null);
        slf(event, ModEntities.VYSACHE.get(), RABBITFOLK, MobAnims.RABBITFOLK, "vysache", 0.6f, 2.0f, null);

        biped(event, ModEntities.GOBLIN.get(), "goblin", 0.8f, false);
        biped(event, ModEntities.GUIDE.get(), "frontier_guide", 1f, false);
        biped(event, ModEntities.BLACKSMITH.get(), "frontier_blacksmith", 1f, false);
        biped(event, ModEntities.SETSUNA.get(), "setsuna", 1f, true);

        event.registerEntityRenderer(ModEntities.HOLY_WATER.get(), ThrownItemRenderer::new);
    }

    private static <T extends Mob & SlfAnimated> void slf(EntityRenderersEvent.RegisterRenderers event, EntityType<T> type,
                                                          ModelLayerLocation layer, AnimSet anims, String texture,
                                                          float shadow, float scale, BiConsumer<T, SlfModel<T>> hook) {
        event.registerEntityRenderer(type, ctx -> new SlfMobRenderer<>(ctx,
                new SlfModel<>(ctx.bakeLayer(layer), anims, hook), tex(texture), shadow, scale));
    }

    private static <T extends Mob> void biped(EntityRenderersEvent.RegisterRenderers event, EntityType<T> type, String texture,
                                              float scale, boolean ghost) {
        event.registerEntityRenderer(type, (EntityRendererProvider.Context ctx) -> {
            HumanoidModel<T> model = ghost ? NpcHumanoidRenderer.ghostModel(ctx.bakeLayer(BIPED))
                    : new HumanoidModel<>(ctx.bakeLayer(BIPED));
            EntityRenderer<T> renderer = new NpcHumanoidRenderer<>(ctx, model, tex(texture), scale);
            return renderer;
        });
    }
}
