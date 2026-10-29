package com.slfrontier.client.screen;

import com.slfrontier.client.ClientState;
import com.slfrontier.client.KeyBindings;
import com.slfrontier.client.map.MapStore;
import com.slfrontier.client.map.MapView;
import com.slfrontier.client.ui.Gfx;
import com.slfrontier.client.ui.SlfButton;
import com.slfrontier.client.ui.Theme;
import com.slfrontier.data.PlayerData;
import com.slfrontier.network.ModNetwork;
import com.slfrontier.registry.ModAttachments;
import com.slfrontier.world.Frontier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Frontier map (default key M): everything you have explored, your attuned waypoints
 * and towns, the current objective and your own pins. Select a waypoint to fast travel.
 */
public class MapScreen extends Screen {
    private static final int SIDEBAR = 132;
    private static float savedZoom = 1f;

    private final MapView view = new MapView();
    private int left, top, w, h;
    private float openTicks;
    @Nullable
    private String selected;
    private boolean dragging;
    private double listScroll;
    private SlfButton travelButton;

    public MapScreen() {
        super(Component.translatable("gui.slfrontier.tab.map"));
    }

    @Override
    protected void init() {
        w = width - 16;
        h = height - 34;
        left = 8;
        top = 26;
        LocalPlayer p = Minecraft.getInstance().player;
        if (view.zoom == 1f && p != null && openTicks == 0) {
            view.centerX = p.getX();
            view.centerZ = p.getZ();
            view.zoom = savedZoom;
        }
        view.setRect(left + 6, top + 22, left + w - SIDEBAR - 8, top + h - 6);
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int tx = left + 10;
        for (FrontierScreen.Tab t : FrontierScreen.Tab.values()) {
            Component label = Component.translatable("gui.slfrontier.tab." + t.name().toLowerCase());
            int bw = font.width(label) + 26;
            addRenderableWidget(new SlfButton(tx, top - 17, bw, 17, label, SlfButton.Style.TAB, b -> {
                if (t != FrontierScreen.Tab.MAP) FrontierScreen.open(t);
            }).icon(Theme.icon(t.icon), 16).selected(t == FrontierScreen.Tab.MAP));
            tx += bw + 3;
        }
        int sx = left + w - SIDEBAR - 2;
        addRenderableWidget(new SlfButton(view.x0 + 4, view.y1 - 18, 16, 14, Component.literal("+"), SlfButton.Style.SMALL, b -> zoomBy(1.5f, null)));
        addRenderableWidget(new SlfButton(view.x0 + 22, view.y1 - 18, 16, 14, Component.literal("-"), SlfButton.Style.SMALL, b -> zoomBy(1 / 1.5f, null)));
        addRenderableWidget(new SlfButton(view.x0 + 40, view.y1 - 18, 16, 14, Component.empty(), SlfButton.Style.SMALL, b -> {
            LocalPlayer p = Minecraft.getInstance().player;
            view.centerX = p.getX();
            view.centerZ = p.getZ();
        }).icon(Theme.icon("player"), 16));
        travelButton = addRenderableWidget(new SlfButton(sx, top + h - 26, SIDEBAR - 4, 18,
                Component.translatable("gui.slfrontier.fast_travel"), SlfButton.Style.PRIMARY, b -> travel())
                .icon(Theme.icon("waypoint"), 16));
        updateTravelButton();
    }

    private void updateTravelButton() {
        if (travelButton == null) return;
        travelButton.active = travelRefusal() == null;
    }

    /** Client-side reasons fast travel can't happen (the server checks again). */
    @Nullable
    private Component travelRefusal() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !Frontier.isIn(p)) return Component.translatable("message.slfrontier.travel.outside");
        if (!ClientState.fastTravelEnabled()) return Component.translatable("message.slfrontier.travel.disabled");
        if (selected == null) return Component.translatable("gui.slfrontier.select_waypoint");
        PlayerData d = ModAttachments.get(p);
        long left = d.fastTravelReadyAt() - p.level().getGameTime();
        if (left > 0 && !p.isCreative()) return Component.translatable("message.slfrontier.travel.cooldown", (left + 19) / 20);
        return null;
    }

    private void travel() {
        if (selected == null) return;
        PacketDistributor.sendToServer(new ModNetwork.FastTravel(selected));
        onClose();
    }

    @Override
    public void tick() {
        openTicks++;
        updateTravelButton();
    }

    @Override
    public void removed() {
        savedZoom = view.zoom;
        MapStore.saveAll();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Rendering -------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        float t = Mth.clamp((openTicks + partialTick) / 5f, 0f, 1f);
        Gfx.alpha = t;
        Gfx.panel(g, left, top, w, h, 8);
        LocalPlayer player = Minecraft.getInstance().player;
        float[] diamond = Gfx.ngon(left + 16, top + 10, 3.5f, 4, 0);
        Gfx.poly(g, diamond, top + 6, top + 14, Theme.ACCENT, Theme.ACCENT_SOFT);
        g.drawString(font, Component.translatable("gui.slfrontier.map_title").withStyle(ChatFormatting.BOLD), left + 24, top + 6,
                Gfx.fade(Theme.ACCENT), false);

        boolean inFrontier = player != null && Frontier.isIn(player);
        view.draw(g);
        Gfx.outline(g, new float[]{view.x0, view.y0, view.x1, view.y0, view.x1, view.y1, view.x0, view.y1}, 1f, Theme.BORDER);
        if (!inFrontier) {
            Component msg = Component.translatable("gui.slfrontier.map_unavailable");
            g.drawString(font, msg, (int) view.screenCX() - font.width(msg) / 2, (int) view.screenCY(), Gfx.fade(Theme.TEXT_DIM), true);
        } else {
            renderMarkers(g, player, mouseX, mouseY, partialTick);
        }
        // Cursor coordinates and scale
        if (view.contains(mouseX, mouseY)) {
            double[] wpos = view.toWorld(mouseX, mouseY);
            String c = String.format("X %d  Z %d", Mth.floor(wpos[0]), Mth.floor(wpos[1]));
            g.drawString(font, c, view.x1 - 4 - font.width(c), view.y1 - 12, Gfx.fade(Theme.TEXT), true);
        }
        String scale = String.format("%.0f%%", view.zoom * 100);
        g.drawString(font, scale, view.x0 + 60, view.y1 - 14, Gfx.fade(Theme.TEXT_DIM), true);

        renderSidebar(g, mouseX, mouseY);
        for (var r : renderables) r.render(g, mouseX, mouseY, partialTick);
        Gfx.alpha = 1f;
        if (travelButton != null && travelButton.isHovered() && !travelButton.active) {
            Component why = travelRefusal();
            if (why != null) g.renderTooltip(font, why, mouseX, mouseY);
        }
    }

    private void renderMarkers(GuiGraphics g, LocalPlayer player, int mouseX, int mouseY, float partialTick) {
        g.enableScissor(view.x0, view.y0, view.x1, view.y1);
        // Pins
        for (long[] pin : MapStore.pins()) {
            float[] s = view.toScreen(pin[0] + 0.5, pin[1] + 0.5);
            MapView.icon(g, Theme.icon("pin"), s[0], s[1] - 5, 11, 0);
        }
        // Waypoints & towns
        for (ModNetwork.Marker m : ClientState.waypoints()) {
            float[] s = view.toScreen(m.pos().getX() + 0.5, m.pos().getZ() + 0.5);
            boolean town = m.kind() == ModNetwork.Marker.TOWN;
            boolean sel = m.id().equals(selected);
            if (sel) {
                float pulse = 9 + Mth.sin((openTicks + partialTick) * 0.25f) * 1.5f;
                Gfx.ring(g, s[0], s[1], pulse, pulse + 1.5f, 0, Mth.TWO_PI, Theme.withA(Theme.ACCENT, 0.9f), Theme.withA(Theme.ACCENT, 0.2f));
            }
            MapView.icon(g, Theme.icon(town ? "town" : "waypoint"), s[0], s[1], town ? 13 : 11, 0);
            if (town || view.zoom >= 1.5f || sel) {
                Component name = Component.translatable(m.nameKey());
                int nw = font.width(name);
                Gfx.rect(g, s[0] - nw / 2f - 2, s[1] + 7, s[0] + nw / 2f + 2, s[1] + 17, 0xA0050A14);
                g.drawString(font, name, (int) (s[0] - nw / 2f), (int) s[1] + 8, Gfx.fade(town ? 0xFFF2D8A0 : Theme.TEXT), false);
            }
        }
        // Objective
        ModNetwork.Marker quest = ClientState.questMarker();
        if (quest != null) {
            float[] s = view.toScreen(quest.pos().getX() + 0.5, quest.pos().getZ() + 0.5);
            float[] c = view.clampToEdge(s[0], s[1], 8);
            float pulse = 1f + 0.15f * Mth.sin((openTicks + partialTick) * 0.3f);
            if (c[2] > 0) {
                float ang = (float) Math.atan2(s[1] - view.screenCY(), s[0] - view.screenCX()) + Mth.HALF_PI;
                MapView.icon(g, Theme.icon("player"), c[0], c[1], 10, ang);
            } else {
                Gfx.ring(g, s[0], s[1], 7 * pulse, 8.5f * pulse, 0, Mth.TWO_PI, Theme.withA(Theme.GOLD, 0.8f), Theme.withA(Theme.GOLD, 0.1f));
                MapView.icon(g, Theme.icon("main_quest"), s[0], s[1], 12, 0);
            }
        }
        // Player
        float[] ps = view.toScreen(Mth.lerp(partialTick, player.xo, player.getX()), Mth.lerp(partialTick, player.zo, player.getZ()));
        float[] pc = view.clampToEdge(ps[0], ps[1], 6);
        MapView.icon(g, Theme.icon("player"), pc[0], pc[1], 11, (player.getYRot() + 180f) * Mth.DEG_TO_RAD + view.rotation);
        g.disableScissor();
        // Hover tooltip for markers
        ModNetwork.Marker hover = markerAt(mouseX, mouseY);
        if (hover != null) {
            g.renderTooltip(font, List.of(Component.translatable(hover.nameKey()).getVisualOrderText(),
                    Component.translatable("gui.slfrontier.distance", distance(hover)).withStyle(ChatFormatting.GRAY).getVisualOrderText()),
                    mouseX, mouseY);
        }
    }

    private int distance(ModNetwork.Marker m) {
        LocalPlayer p = Minecraft.getInstance().player;
        return (int) Math.sqrt(m.pos().distToCenterSqr(p.getX(), m.pos().getY(), p.getZ()));
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int sx = left + w - SIDEBAR - 2, sy = top + 22, sw = SIDEBAR - 4;
        Gfx.header(g, font, Component.translatable("gui.slfrontier.waypoints"), sx, sy, sw);
        List<ModNetwork.Marker> wps = ClientState.waypoints();
        int listY = sy + 12, listH = h - 22 - 12 - 104;
        Gfx.inset(g, sx, listY, sw, listH);
        int rowH = 20;
        int visible = Math.max(1, (listH - 4) / rowH);
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, wps.size() - visible));
        g.enableScissor(sx + 1, listY + 1, sx + sw - 1, listY + listH - 1);
        int y = listY + 2 - (int) (listScroll * rowH);
        PlayerData d = ModAttachments.get(Minecraft.getInstance().player);
        for (ModNetwork.Marker m : wps) {
            boolean sel = m.id().equals(selected);
            boolean hover = mouseX >= sx && mouseX < sx + sw && mouseY >= y && mouseY < y + rowH - 1 && mouseY >= listY && mouseY < listY + listH;
            if (sel) {
                Gfx.chamfer(g, sx + 2, y, sw - 4, rowH - 2, 2, 0, 2, 0, 0x5A3FA8D0, 0x203FA8D0);
            } else if (hover) {
                Gfx.chamfer(g, sx + 2, y, sw - 4, rowH - 2, 2, 0, 2, 0, 0x28FFFFFF, 0x10FFFFFF);
            }
            boolean town = m.kind() == ModNetwork.Marker.TOWN;
            Gfx.icon(g, Theme.icon(town ? "town" : "waypoint"), sx + 4, y + 4, 10, 16);
            String name = font.plainSubstrByWidth(Component.translatable(m.nameKey()).getString(), sw - 22);
            g.drawString(font, name, sx + 17, y + 2, Gfx.fade(town ? 0xFFF2D8A0 : Theme.TEXT), false);
            String dist = distance(m) + "m" + (m.id().equals(d.respawnWaypoint()) ? "  ✚" : "");
            g.drawString(font, dist, sx + 17, y + 11, Gfx.fade(Theme.TEXT_DIM), false);
            y += rowH;
        }
        if (wps.isEmpty()) {
            Gfx.wrapped(g, font, Component.translatable("gui.slfrontier.no_waypoints"), sx + 5, listY + 5, sw - 10, Theme.TEXT_FAINT, 9);
        }
        g.disableScissor();

        // Selected waypoint card
        int cy = listY + listH + 6;
        ModNetwork.Marker sel = selectedMarker();
        if (sel != null) {
            g.drawString(font, Component.translatable(sel.nameKey()), sx, cy, Gfx.fade(Theme.GOLD), true);
            g.drawString(font, Component.translatable("gui.slfrontier.distance", distance(sel)), sx, cy + 11, Gfx.fade(Theme.TEXT_DIM), false);
            if (sel.id().equals(d.respawnWaypoint())) {
                g.drawString(font, Component.translatable("gui.slfrontier.respawn_point"), sx, cy + 21, Gfx.fade(Theme.GREEN), false);
            }
        } else {
            Gfx.wrapped(g, font, Component.translatable("gui.slfrontier.map_help"), sx, cy, sw, Theme.TEXT_FAINT, 9);
        }
        ModNetwork.Marker quest = ClientState.questMarker();
        if (quest != null) {
            int qy = top + h - 50;
            Gfx.icon(g, Theme.icon("main_quest"), sx, qy, 9, 16);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(quest.nameKey()).getString(), sw - 12), sx + 12, qy,
                    Gfx.fade(Theme.GOLD), false);
            g.drawString(font, Component.translatable("gui.slfrontier.distance", distance(quest)), sx + 12, qy + 10, Gfx.fade(Theme.TEXT_DIM), false);
        }
    }

    @Nullable
    private ModNetwork.Marker selectedMarker() {
        if (selected == null) return null;
        for (ModNetwork.Marker m : ClientState.waypoints()) if (m.id().equals(selected)) return m;
        return null;
    }

    @Nullable
    private ModNetwork.Marker markerAt(double mx, double my) {
        if (!view.contains((float) mx, (float) my)) return null;
        ModNetwork.Marker best = null;
        double bestD = 8 * 8;
        for (ModNetwork.Marker m : ClientState.markers()) {
            float[] s = view.toScreen(m.pos().getX() + 0.5, m.pos().getZ() + 0.5);
            double d = (s[0] - mx) * (s[0] - mx) + (s[1] - my) * (s[1] - my);
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    // ---- Input -----------------------------------------------------------------------

    private void zoomBy(float factor, @Nullable double[] anchorScreen) {
        double[] before = anchorScreen == null ? null : view.toWorld(anchorScreen[0], anchorScreen[1]);
        view.zoom = Mth.clamp(view.zoom * factor, 0.125f, 8f);
        if (before != null) {
            double[] after = view.toWorld(anchorScreen[0], anchorScreen[1]);
            view.centerX += before[0] - after[0];
            view.centerZ += before[1] - after[1];
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int sx = left + w - SIDEBAR - 2, listY = top + 34, listH = h - 22 - 12 - 104;
        if (mx >= sx && mx < sx + SIDEBAR - 4 && my >= listY && my < listY + listH) {
            int idx = (int) ((my - listY - 2) / 20 + listScroll);
            List<ModNetwork.Marker> wps = ClientState.waypoints();
            if (idx >= 0 && idx < wps.size()) {
                selected = wps.get(idx).id();
                view.centerX = wps.get(idx).pos().getX() + 0.5;
                view.centerZ = wps.get(idx).pos().getZ() + 0.5;
                updateTravelButton();
            }
            return true;
        }
        if (view.contains((float) mx, (float) my)) {
            if (button == 1) {
                double[] wpos = view.toWorld(mx, my);
                MapStore.togglePin(Mth.floor(wpos[0]), Mth.floor(wpos[1]), 6 / view.zoom);
                return true;
            }
            ModNetwork.Marker m = markerAt(mx, my);
            if (m != null && (m.kind() == ModNetwork.Marker.WAYPOINT || m.kind() == ModNetwork.Marker.TOWN)) {
                selected = m.id();
                updateTravelButton();
                return true;
            }
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            view.centerX -= dx / view.zoom;
            view.centerZ -= dy / view.zoom;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (view.contains((float) mx, (float) my)) {
            zoomBy(sy > 0 ? 1.25f : 0.8f, new double[]{mx, my});
        } else {
            listScroll -= sy;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (KeyBindings.MAP.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
