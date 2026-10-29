package com.slfrontier.client.overlay;

import com.slfrontier.client.ui.Gfx;
import com.slfrontier.client.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The travel loading screen: a dark holographic backdrop with the destination, a progress
 * bar and a random tip or piece of Frontier lore. It never pauses the game; the running
 * {@link TravelOverlay} trip decides when it fades out.
 */
public class TravelScreen extends ReceivingLevelScreen {
    private static final int MAX_TIP_LINES = 4;

    private final BooleanSupplier levelReady;
    private boolean finished;
    private long lastFrame;

    public TravelScreen(BooleanSupplier levelReady, Reason reason) {
        super(levelReady, reason);
        this.levelReady = levelReady;
    }

    @Override
    public void tick() {
        TravelOverlay.tick(this, levelReady);
    }

    void finish() {
        if (finished) return;
        finished = true;
        if (minecraft != null && minecraft.screen == this) onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        TravelOverlay.Trip trip = TravelOverlay.trip();
        if (trip == null) return;
        long now = Util.getMillis();
        float time = (now - trip.start) / 1000f;
        updateProgress(trip, now, time);
        float cx = width / 2f, cy = height / 2f;

        Gfx.alpha = trip.screenAlpha();
        renderBackdrop(g, cx, cy, time);

        Gfx.alpha = trip.contentAlpha();
        int pw = Math.min(320, width - 32);
        List<FormattedCharSequence> lines = trip.tip.getString().isEmpty() ? List.of() : font.split(trip.tip, pw - 32);
        if (lines.size() > MAX_TIP_LINES) lines = lines.subList(0, MAX_TIP_LINES);
        int tipH = lines.isEmpty() ? 0 : 20 + lines.size() * 10;
        int ph = 72 + (tipH > 0 ? tipH + 6 : 0);
        int px = (width - pw) / 2, py = (height - ph) / 2;
        g.pose().pushPose();
        g.pose().translate(0, (1 - Gfx.alpha) * 6, 0);
        Gfx.panel(g, px, py, pw, ph, 8);

        float[] diamond = Gfx.ngon(px + 16, py + 12, 3.5f, 4, 0);
        Gfx.poly(g, diamond, py + 8, py + 16, Theme.ACCENT, Theme.ACCENT_SOFT);
        Component header = trip.header.copy().withStyle(ChatFormatting.BOLD);
        text(g, header, px + 24, py + 8, 1f, Theme.ACCENT, false);
        float rule = px + 28 + font.width(header);
        Gfx.hgrad(g, rule, py + 12, px + pw - 12, py + 13, Theme.withA(Theme.ACCENT, 0.7f), Theme.withA(Theme.ACCENT, 0f));

        float scale = Math.min(2f, (pw - 28) / (float) Math.max(1, font.width(trip.destination)));
        text(g, trip.destination, cx - font.width(trip.destination) * scale / 2f, py + 23, scale, Theme.TEXT, true);

        int bx = px + 12, by = py + 48, bw = pw - 24;
        Gfx.bar(g, bx, by, bw, 4, trip.progress, Theme.ACCENT, Theme.ACCENT_SOFT, Theme.BAR_BACK, 0);
        float filled = bw * trip.progress;
        float sweep = bx + (time * 0.9f % 1f) * (filled + 24) - 12;
        if (filled > 4) {
            Gfx.hgrad(g, Math.max(bx, sweep - 14), by, Math.min(bx + filled, sweep), by + 4, 0x00FFFFFF, 0x70FFFFFF);
        }
        Component status = Component.translatable(trip.arrived ? "travel.slfrontier.status.ready" : "travel.slfrontier.status.loading");
        text(g, status, bx, by + 8, 0.7f, Theme.TEXT_DIM, false);
        String pct = Math.round(trip.progress * 100) + "%";
        text(g, Component.literal(pct), bx + bw - font.width(pct) * 0.7f, by + 8, 0.7f, Theme.TEXT_DIM, false);

        if (tipH > 0) {
            int tx = px + 10, ty = py + 70, tw = pw - 20;
            Gfx.inset(g, tx, ty, tw, tipH);
            Component label = Component.translatable(trip.lore ? "travel.slfrontier.lore" : "travel.slfrontier.tip")
                    .withStyle(ChatFormatting.BOLD);
            int labelColor = trip.lore ? Theme.VIOLET : Theme.GOLD;
            Gfx.rect(g, tx, ty + 3, tx + 1.5f, ty + tipH - 3, labelColor);
            text(g, label, tx + 7, ty + 5, 0.75f, labelColor, false);
            int ly = ty + 15;
            for (FormattedCharSequence line : lines) {
                if (Gfx.alpha >= 0.03f) g.drawString(font, line, tx + 7, ly, Gfx.fade(Theme.TEXT), false);
                ly += 10;
            }
        }
        g.pose().popPose();
        Gfx.alpha = 1f;
    }

    /** Full-screen dark glass with scanlines, a soft centre glow and slowly turning holo rings. */
    private void renderBackdrop(GuiGraphics g, float cx, float cy, float time) {
        Gfx.vgrad(g, 0, 0, width, height, 0xFF0B1628, 0xFF03070E);
        float r = Math.max(width, height) * 0.55f;
        Gfx.ring(g, cx, cy, 0, r, 0, Mth.TWO_PI, Theme.withA(Theme.ACCENT, 0.09f), Theme.withA(Theme.ACCENT, 0f));
        for (float y = 1; y < height; y += 3) {
            Gfx.quad(g, 0, y, 0x08FFFFFF, 0, y + 1, 0x08FFFFFF, width, y + 1, 0x08FFFFFF, width, y, 0x08FFFFFF);
        }
        Gfx.flush(g);
        float big = Math.min(width, height) * 0.42f;
        int ringA = Theme.withA(Theme.ACCENT, 0.30f), ringB = Theme.withA(Theme.ACCENT, 0.05f);
        for (int i = 0; i < 3; i++) {
            float a0 = time * 0.5f + i * Mth.TWO_PI / 3;
            Gfx.ring(g, cx, cy, big, big + 1.5f, a0, a0 + 1.3f, ringA, ringB);
        }
        float small = big * 0.82f;
        for (int i = 0; i < 2; i++) {
            float a0 = -time * 0.8f + i * Mth.PI;
            Gfx.ring(g, cx, cy, small, small + 0.8f, a0, a0 + 2.2f, ringB, ringA);
        }
    }

    /** Eases the bar toward how far the arrival has really got (never backwards). */
    private void updateProgress(TravelOverlay.Trip trip, long now, float time) {
        float dt = lastFrame == 0 ? 0f : Math.min(0.25f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float target;
        if (trip.arrived || trip.fadeStart >= 0) target = 1f;
        else if (trip.chunksLoaded) target = 0.85f;
        else target = 0.6f * (1f - (float) Math.exp(-time / 1.2f));
        trip.progress = Math.max(trip.progress, trip.progress + (target - trip.progress) * Math.min(1f, dt * 6f));
    }

    private void text(GuiGraphics g, Component text, float x, float y, float scale, int color, boolean shadow) {
        if (Gfx.alpha >= 0.03f) Gfx.text(g, font, text, x, y, scale, color, shadow);
    }
}
