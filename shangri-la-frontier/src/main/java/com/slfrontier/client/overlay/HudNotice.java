package com.slfrontier.client.overlay;

import com.slfrontier.SlfConfig;
import com.slfrontier.client.KeyBindings;
import com.slfrontier.client.ui.Gfx;
import com.slfrontier.client.ui.Theme;
import com.slfrontier.world.Frontier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * A compact notice band in the style of the HUD announcements, for things only the client
 * knows about: the minimap being toggled, or entering the Frontier while it is hidden.
 */
public final class HudNotice {
    private static final int LENGTH = 70;

    @Nullable
    private static Component title;
    private static Component sub = Component.empty();
    private static int color;
    private static int ticks;
    private static boolean wasInFrontier;

    private HudNotice() {
    }

    public static void show(Component title, Component subtitle, int color) {
        HudNotice.title = title;
        HudNotice.sub = subtitle;
        HudNotice.color = color;
        ticks = LENGTH;
    }

    public static void minimapToggled(boolean shown) {
        Component key = KeyBindings.MINIMAP.getTranslatedKeyMessage();
        if (shown) {
            show(Component.translatable("notice.slfrontier.minimap_shown"), Component.translatable("notice.slfrontier.minimap_shown.sub", key),
                    Theme.ACCENT);
        } else {
            show(Component.translatable("notice.slfrontier.minimap_hidden"),
                    Component.translatable("notice.slfrontier.minimap_hidden.sub", key), Theme.GOLD);
        }
    }

    public static void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean inFrontier = player != null && Frontier.isIn(player);
        // A hidden minimap is easy to forget about: remind on the way in.
        if (inFrontier && !wasInFrontier && SlfConfig.FRONTIER_HUD.get() && !SlfConfig.SHOW_MINIMAP.get()) minimapToggled(false);
        wasInFrontier = inFrontier;
        if (ticks > 0 && !TravelOverlay.isActive()) ticks--;
    }

    public static void clear() {
        ticks = 0;
        wasInFrontier = false;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks <= 0 || title == null || mc.options.hideGui || mc.screen != null) return;
        Font font = mc.font;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        float in = Mth.clamp((LENGTH - ticks + pt) / 6f, 0f, 1f);
        float out = Mth.clamp((ticks - pt) / 12f, 0f, 1f);
        float a = Math.min(in, out);
        if (a < 0.03f) return;
        int w = g.guiWidth();
        int y = g.guiHeight() / 3 + 42;
        Component head = title.copy().withStyle(ChatFormatting.BOLD);
        float band = Math.max(font.width(head), font.width(sub) * 0.8f) / 2 + 36;
        float grow = band * (0.7f + 0.3f * in);
        int c = color | 0xFF000000;
        Gfx.alpha = a;
        Gfx.hgrad(g, w / 2f - grow, y - 4, w / 2f, y + 19, 0x00060C16, 0xB0060C16);
        Gfx.hgrad(g, w / 2f, y - 4, w / 2f + grow, y + 19, 0xB0060C16, 0x00060C16);
        Gfx.hgrad(g, w / 2f - grow, y - 5, w / 2f, y - 4, Theme.withA(c, 0f), c);
        Gfx.hgrad(g, w / 2f, y - 5, w / 2f + grow, y - 4, c, Theme.withA(c, 0f));
        Gfx.hgrad(g, w / 2f - grow, y + 19, w / 2f, y + 20, Theme.withA(c, 0f), c);
        Gfx.hgrad(g, w / 2f, y + 19, w / 2f + grow, y + 20, c, Theme.withA(c, 0f));
        Gfx.text(g, font, head, w / 2f - font.width(head) / 2f, y, 1f, c, true);
        Gfx.text(g, font, sub, w / 2f - font.width(sub) * 0.4f, y + 10, 0.8f, 0xFFFFFFFF, true);
        Gfx.alpha = 1f;
    }
}
