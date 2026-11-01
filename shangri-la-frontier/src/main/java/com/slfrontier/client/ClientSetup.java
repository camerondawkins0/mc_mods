package com.slfrontier.client;

import com.slfrontier.SLFrontier;
import com.slfrontier.client.anim.AnimSet;
import com.slfrontier.client.anim.MobAnims;
import com.slfrontier.client.fx.FxRenderer;
import com.slfrontier.client.model.ModelDefs;
import com.slfrontier.client.model.SlfModel;
import com.slfrontier.client.overlay.QuestPointer;
import com.slfrontier.client.render.SlfMobRenderer;
import com.slfrontier.entity.SlfAnimated;
import com.slfrontier.entity.boss.WezaemonEntity;
import com.slfrontier.registry.ModEntities;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = SLFrontier.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private static final ModelLayerLocation LYCAGON = layer("lycagon");
    private static final ModelLayerLocation WEZAEMON = layer("wezaemon");
    private static final ModelLayerLocation KIRIN = layer("kirin");
    private static final ModelLayerLocation VORPAL_BUNNY = layer("vorpal_bunny");
    private static final ModelLayerLocation EMUL = layer("emul");
    private static final ModelLayerLocation VYSACHE = layer("vysache");
    private static final ModelLayerLocation BILAC = layer("bilac");
    private static final ModelLayerLocation SETSUNA = layer("setsuna");
    private static final ModelLayerLocation GUIDE = layer("guide");
    private static final ModelLayerLocation SMITH = layer("smith");
    private static final ModelLayerLocation PENCILGON = layer("pencilgon");
    private static final ModelLayerLocation OIKATZO = layer("oikatzo");
    private static final ModelLayerLocation GOBLIN = layer("goblin");
    private static final ModelLayerLocation EMPIRE_BEE = layer("empire_bee");
    private static final ModelLayerLocation QUAD_BEETLE = layer("quad_beetle");
    private static final ModelLayerLocation MUD_DIGGER = layer("mud_digger");
    private static final ModelLayerLocation RAVENOUS_SNAKE = layer("ravenous_snake");
    private static final ModelLayerLocation WOODMAGE = layer("woodmage");

    private ClientSetup() {
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(SLFrontier.id(name), "main");
    }

    private static Identifier tex(String name) {
        return SLFrontier.id("textures/entity/" + name + ".png");
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        // Mods → Shangri-La Frontier → Config: NeoForge's generated screen for the client and server configs.
        ModList.get().getModContainerById(SLFrontier.MODID)
                .ifPresent(mod -> mod.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new));
        // The Frontier Compass needle is data now: its item model definition (assets/slfrontier/items) reads the
        // minecraft:compass property with the lodestone target, which follows the stack's lodestone tracker.
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(KeyBindings.CATEGORY);
        for (var key : KeyBindings.SKILLS) event.register(key);
        event.register(KeyBindings.MENU);
        event.register(KeyBindings.MAP);
        event.register(KeyBindings.MINIMAP);
    }

    @SubscribeEvent
    public static void registerGui(RegisterGuiLayersEvent event) {
        event.registerAboveAll(SLFrontier.id("frontier_hud"), FrontierHud::render);
        // Under every HUD element: the screen flash and speed lines of a big hit.
        event.registerBelowAll(SLFrontier.id("impact"), com.slfrontier.client.fx.Impact::renderOverlay);
    }

    /** Shader pipelines of the world effects and the quest marker. */
    @SubscribeEvent
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(FxRenderer.ADDITIVE_PIPELINE);
        event.registerPipeline(QuestPointer.SOLID_PIPELINE);
    }

    /** Players carry the skill animation playing on them into their render state (for the model mixin and the pose hook). */
    @SubscribeEvent
    public static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                ClientAnims.extract(avatar, state);
            }
        });
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LYCAGON, ModelDefs::lycagon);
        event.registerLayerDefinition(WEZAEMON, ModelDefs::wezaemon);
        event.registerLayerDefinition(KIRIN, ModelDefs::kirin);
        event.registerLayerDefinition(VORPAL_BUNNY, ModelDefs::vorpalBunny);
        event.registerLayerDefinition(EMUL, ModelDefs::emul);
        event.registerLayerDefinition(VYSACHE, ModelDefs::vysache);
        event.registerLayerDefinition(BILAC, ModelDefs::bilac);
        event.registerLayerDefinition(SETSUNA, ModelDefs::setsuna);
        event.registerLayerDefinition(GUIDE, ModelDefs::guide);
        event.registerLayerDefinition(SMITH, ModelDefs::smith);
        event.registerLayerDefinition(PENCILGON, ModelDefs::pencilgon);
        event.registerLayerDefinition(OIKATZO, ModelDefs::oikatzo);
        event.registerLayerDefinition(GOBLIN, ModelDefs::goblin);
        event.registerLayerDefinition(EMPIRE_BEE, ModelDefs::empireBee);
        event.registerLayerDefinition(QUAD_BEETLE, ModelDefs::quadBeetle);
        event.registerLayerDefinition(MUD_DIGGER, ModelDefs::mudDigger);
        event.registerLayerDefinition(RAVENOUS_SNAKE, ModelDefs::ravenousSnake);
        event.registerLayerDefinition(WOODMAGE, ModelDefs::woodmage);
    }

    /**
     * Scales follow canon sizes where it stays playable: Emul is a 40 cm rabbit, Vorpal
     * Bunnies come up to your knee, Lycagon towers over the player and Wezaemon stands
     * about two and a half blocks tall.
     */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        slf(event, ModEntities.LYCAGON.get(), LYCAGON, MobAnims.LYCAGON, "lycagon", 1.4f, 1.25f);
        slf(event, ModEntities.SHADOW_WOLF.get(), LYCAGON, MobAnims.SHADOW_WOLF, "shadow_wolf", 1.4f, 0.38f);
        SlfModel.Hook<WezaemonEntity> katana = new SlfModel.Hook<>(WezaemonEntity::isDisarmed,
                (s, m) -> m.part("katana").ifPresent(p -> p.visible = !s.flag));
        slf(event, ModEntities.WEZAEMON.get(), WEZAEMON, MobAnims.WEZAEMON, "wezaemon", 0.9f, 0.85f, katana, false);
        slf(event, ModEntities.VORPAL_BUNNY.get(), VORPAL_BUNNY, MobAnims.VORPAL_BUNNY, "vorpal_bunny", 0.5f, 0.55f);
        slf(event, ModEntities.EMUL.get(), EMUL, MobAnims.EMUL, "emul", 0.6f, 0.45f);
        slf(event, ModEntities.VYSACHE.get(), VYSACHE, MobAnims.VYSACHE, "vysache", 0.7f, 0.85f);
        slf(event, ModEntities.BILAC.get(), BILAC, MobAnims.BILAC, "bilac", 0.6f, 0.6f);
        slf(event, ModEntities.GUIDE.get(), GUIDE, MobAnims.NPC, "frontier_guide", 0.5f, 0.9375f);
        slf(event, ModEntities.BLACKSMITH.get(), SMITH, MobAnims.NPC, "frontier_blacksmith", 0.5f, 0.9375f);
        slf(event, ModEntities.SETSUNA.get(), SETSUNA, MobAnims.GHOST, "setsuna", 0.3f, 0.9375f, null, true);
        slf(event, ModEntities.GOBLIN.get(), GOBLIN, MobAnims.GOBLIN, "goblin", 0.6f, 0.75f);
        slf(event, ModEntities.EMPIRE_BEE.get(), EMPIRE_BEE, MobAnims.EMPIRE_BEE, "empire_bee", 0.45f, 0.85f);
        slf(event, ModEntities.EMPIRE_BEE_QUEEN.get(), EMPIRE_BEE, MobAnims.EMPIRE_BEE, "empire_bee_queen", 0.45f, 2f);
        slf(event, ModEntities.QUAD_BEETLE.get(), QUAD_BEETLE, MobAnims.QUAD_BEETLE, "quad_beetle", 1.0f, 1.3f);
        slf(event, ModEntities.MUD_DIGGER.get(), MUD_DIGGER, MobAnims.MUD_DIGGER, "mud_digger", 1.1f, 1.1f);
        slf(event, ModEntities.REDCAP_GOBLIN.get(), GOBLIN, MobAnims.GOBLIN, "redcap_goblin", 0.6f, 0.75f);
        slf(event, ModEntities.RAVENOUS_SNAKE.get(), RAVENOUS_SNAKE, MobAnims.RAVENOUS_SNAKE, "ravenous_snake", 0.8f, 1.6f);
        slf(event, ModEntities.WOODMAGE.get(), WOODMAGE, MobAnims.WOODMAGE, "woodmage", 0.8f, 0.85f);
        slf(event, ModEntities.KIRIN.get(), KIRIN, MobAnims.KIRIN, "kirin", 1.0f, 1.2f);
        slf(event, ModEntities.LYCAGON_SHADOW.get(), LYCAGON, MobAnims.LYCAGON, "lycagon_shadow", 1.4f, 1.0f, null, true);
        slf(event, ModEntities.PENCILGON.get(), PENCILGON, MobAnims.PENCILGON, "pencilgon", 0.5f, 0.9375f);
        slf(event, ModEntities.OIKATZO.get(), OIKATZO, MobAnims.OIKATZO, "oikatzo", 0.5f, 0.9f);

        event.registerEntityRenderer(ModEntities.HOLY_WATER.get(), ThrownItemRenderer::new);
    }

    private static <T extends Mob & SlfAnimated> void slf(EntityRenderersEvent.RegisterRenderers event, EntityType<T> type,
                                                          ModelLayerLocation layer, AnimSet anims, String texture,
                                                          float shadow, float scale) {
        slf(event, type, layer, anims, texture, shadow, scale, null, false);
    }

    /** {@code ghost} draws the creature see-through (Setsuna, Lycagon's shadow). */
    private static <T extends Mob & SlfAnimated> void slf(EntityRenderersEvent.RegisterRenderers event, EntityType<T> type,
                                                          ModelLayerLocation layer, AnimSet anims, String texture,
                                                          float shadow, float scale, @Nullable SlfModel.Hook<T> hook, boolean ghost) {
        event.registerEntityRenderer(type, ctx -> new SlfMobRenderer<>(ctx,
                new SlfModel(ctx.bakeLayer(layer), anims, hook == null ? null : hook.pose(),
                        ghost ? RenderTypes::entityTranslucent : RenderTypes::entityCutout),
                tex(texture), shadow, scale, hook == null ? null : hook.flag()));
    }
}
