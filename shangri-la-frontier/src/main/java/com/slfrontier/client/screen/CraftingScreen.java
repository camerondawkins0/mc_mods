package com.slfrontier.client.screen;

import com.slfrontier.client.CraftingClient;
import com.slfrontier.client.ui.Gfx;
import com.slfrontier.client.ui.SlfButton;
import com.slfrontier.client.ui.Theme;
import com.slfrontier.crafting.FrontierCrafting;
import com.slfrontier.crafting.FrontierRecipe;
import com.slfrontier.crafting.Station;
import com.slfrontier.data.PlayerData;
import com.slfrontier.network.CraftingNetwork;
import com.slfrontier.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Frontier crafting window for one station: category tabs, a scrolling recipe list
 * (recipes that can't be made here or yet are dimmed with the reason), and a detail panel
 * with the result, materials (have/need), a quantity selector and the Craft button, which
 * shows the server-run progress.
 */
public class CraftingScreen extends Screen {
    private static final List<String> TABS = List.of("all", "weapon", "armor", "consumable", "material", "gear");
    private static final int ROW = 24, HEADER = 14, ING_ROW = 18;

    private static String lastTab = "all";
    private static final Map<Station, ResourceLocation> LAST_SELECTED = new EnumMap<>(Station.class);

    private final Station station;
    private final int npcEntity;
    private final String npcId;
    private final BlockPos anchor;

    private String tab = lastTab;
    @Nullable
    private FrontierRecipe selected;
    private int quantity = 1;
    private double scroll;
    private int ticks;
    private int left, top, w, h;
    private final List<Row> rows = new ArrayList<>();
    /** How many times each recipe can be made right now; refreshed every tick. */
    private final Map<ResourceLocation, Integer> craftable = new HashMap<>();

    @Nullable
    private ResourceLocation crafting;
    private int craftQty, craftTotal, craftStart;
    @Nullable
    private Component status;
    private int statusColor, statusUntil;
    @Nullable
    private SlfButton craftButton;

    /** A list line: a section header or a recipe, at a fixed offset inside the list. */
    private record Row(@Nullable FrontierRecipe recipe, @Nullable Component header, int y, int h) {
    }

    /**
     * @param npcEntity the NPC smith running the station, or -1 at a block
     * @param anchor    where the station is; the screen closes when the player walks away
     */
    public CraftingScreen(Station station, int npcEntity, String npcId, BlockPos anchor) {
        super(Component.translatable(station.nameKey()));
        this.station = station;
        this.npcEntity = npcEntity;
        this.npcId = npcId;
        this.anchor = anchor;
        ResourceLocation last = LAST_SELECTED.get(station);
        this.selected = last == null ? null : CraftingClient.get(last);
    }

    private static PlayerData data() {
        return ModAttachments.get(Minecraft.getInstance().player);
    }

    private FrontierRecipe.Lock lock(FrontierRecipe r) {
        return r.lock(data(), station, npcId);
    }

    private int craftable(FrontierRecipe r) {
        return craftable.getOrDefault(r.id(), 0);
    }

    // ---- Layout ----------------------------------------------------------------------

    @Override
    protected void init() {
        w = Math.min(452, width - 16);
        h = Math.min(262, height - 34);
        left = (width - w) / 2;
        top = (height - h) / 2 + 9;
        refreshCraftable();
        buildRows();
        if (selected == null || rows.stream().noneMatch(r -> r.recipe() == selected)) selectFirst();
        scrollTo(selected);
        rebuild();
    }

    private int cx0() {
        return left + 8;
    }

    private int cy0() {
        return top + 22;
    }

    private int cx1() {
        return left + w - 8;
    }

    private int cy1() {
        return top + h - 8;
    }

    private int listW() {
        return (cx1() - cx0()) * 44 / 100;
    }

    private int dx() {
        return cx0() + listW() + 8;
    }

    private int dw() {
        return cx1() - dx();
    }

    /** Recipes for this station first, then everything else (dimmed), filtered by the category tab. */
    private void buildRows() {
        rows.clear();
        List<FrontierRecipe> here = new ArrayList<>(), other = new ArrayList<>();
        for (FrontierRecipe r : CraftingClient.recipes()) {
            if (!tab.equals("all") && !tab.equals(r.category())) continue;
            (r.station() == station ? here : other).add(r);
        }
        int y = 0;
        if (!here.isEmpty()) {
            rows.add(new Row(null, Component.translatable("gui.slfrontier.craft.header_here", Component.translatable(station.nameKey())), y, HEADER));
            y += HEADER;
            for (FrontierRecipe r : here) {
                rows.add(new Row(r, null, y, ROW));
                y += ROW;
            }
        }
        if (!other.isEmpty()) {
            rows.add(new Row(null, Component.translatable("gui.slfrontier.craft.header_other"), y, HEADER));
            y += HEADER;
            for (FrontierRecipe r : other) {
                rows.add(new Row(r, null, y, ROW));
                y += ROW;
            }
        }
    }

    private void selectFirst() {
        selected = null;
        for (Row r : rows) {
            if (r.recipe() != null && lock(r.recipe()) == FrontierRecipe.Lock.NONE) {
                select(r.recipe());
                return;
            }
        }
        for (Row r : rows) {
            if (r.recipe() != null) {
                select(r.recipe());
                return;
            }
        }
    }

    private void select(@Nullable FrontierRecipe r) {
        if (r != selected) quantity = 1;
        selected = r;
        if (r != null) LAST_SELECTED.put(station, r.id());
    }

    private int contentHeight() {
        return rows.isEmpty() ? 0 : rows.get(rows.size() - 1).y() + rows.get(rows.size() - 1).h();
    }

    private int viewHeight() {
        return cy1() - cy0() - 4;
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight() - viewHeight()));
    }

    private void scrollTo(@Nullable FrontierRecipe r) {
        for (Row row : rows) {
            if (row.recipe() == r) {
                if (row.y() < scroll) scroll = Math.max(0, row.y() - HEADER);
                else if (row.y() + row.h() > scroll + viewHeight()) scroll = row.y() + row.h() - viewHeight();
            }
        }
        clampScroll();
    }

    private void rebuild() {
        clearWidgets();
        // Category tabs above the window; icon-only when the labels don't fit.
        int full = 0;
        for (String t : TABS) full += font.width(Component.translatable("gui.slfrontier.craft.tab." + t)) + 26 + 3;
        boolean compact = full > w - 20;
        int tx = left + 10;
        for (String t : TABS) {
            Component label = compact ? Component.empty() : Component.translatable("gui.slfrontier.craft.tab." + t);
            int bw = compact ? 24 : font.width(label) + 26;
            addRenderableWidget(new SlfButton(tx, top - 17, bw, 17, label, SlfButton.Style.TAB, b -> switchTab(t))
                    .icon(Theme.icon("cat_" + t), 16).selected(t.equals(tab)).accent(station.color));
            tx += bw + 3;
        }

        int by = cy1() - 18;
        int max = Math.max(1, selected == null ? 1 : Math.min(craftable(selected), FrontierCrafting.MAX_BATCH));
        addRenderableWidget(new SlfButton(dx(), by + 1, 22, 16, Component.literal("1"), SlfButton.Style.SMALL, b -> setQuantity(1))
                .selected(quantity == 1).accent(station.color));
        addRenderableWidget(new SlfButton(dx() + 24, by + 1, 22, 16, Component.literal("5"), SlfButton.Style.SMALL, b -> setQuantity(5))
                .selected(quantity == 5).accent(station.color));
        addRenderableWidget(new SlfButton(dx() + 48, by + 1, 34, 16, Component.translatable("gui.slfrontier.craft.max"), SlfButton.Style.SMALL,
                b -> setQuantity(selected == null ? 1 : Math.max(1, Math.min(craftable(selected), FrontierCrafting.MAX_BATCH))))
                .selected(quantity == max && quantity != 1 && quantity != 5).accent(station.color));

        int cw = Math.min(100, dw() - 120);
        craftButton = new SlfButton(cx1() - cw, by, cw, 18, craftLabel(), SlfButton.Style.PRIMARY, b -> pressCraft())
                .icon(Theme.icon(station.icon()), 16).accent(station.color);
        addRenderableWidget(craftButton);
        updateCraftButton();
    }

    private Component craftLabel() {
        return Component.translatable(crafting != null ? "gui.slfrontier.craft.cancel" : "gui.slfrontier.craft.action." + station.id);
    }

    private void updateCraftButton() {
        if (craftButton == null) return;
        craftButton.setMessage(craftLabel());
        craftButton.active = crafting != null || canCraftSelected();
    }

    private boolean canCraftSelected() {
        return selected != null && lock(selected) == FrontierRecipe.Lock.NONE && craftable(selected) >= quantity;
    }

    private void switchTab(String t) {
        tab = t;
        lastTab = t;
        scroll = 0;
        buildRows();
        if (selected == null || rows.stream().noneMatch(r -> r.recipe() == selected)) selectFirst();
        rebuild();
    }

    private void setQuantity(int q) {
        quantity = Mth.clamp(q, 1, FrontierCrafting.MAX_BATCH);
        rebuild();
    }

    private void pressCraft() {
        if (crafting != null) {
            PacketDistributor.sendToServer(new CraftingNetwork.CancelCraft());
            return;
        }
        if (canCraftSelected()) PacketDistributor.sendToServer(new CraftingNetwork.Craft(selected.id(), quantity));
    }

    /** Recipes were re-sent (login or /reload). */
    public void recipesChanged() {
        if (selected != null) selected = CraftingClient.get(selected.id());
        refreshCraftable();
        buildRows();
        if (selected == null) selectFirst();
        clampScroll();
        rebuild();
    }

    public void onStatus(CraftingNetwork.CraftStatus payload) {
        switch (payload.state()) {
            case CraftingNetwork.CraftStatus.STARTED -> {
                crafting = payload.recipe();
                craftQty = payload.quantity();
                craftTotal = Math.max(1, payload.ticks());
                craftStart = ticks;
                status = null;
            }
            case CraftingNetwork.CraftStatus.DONE -> {
                crafting = null;
                showStatus(payload.message(), Theme.GREEN);
            }
            case CraftingNetwork.CraftStatus.FAILED -> {
                crafting = null;
                showStatus(payload.message(), Theme.RED);
            }
            default -> {
                crafting = null;
                showStatus(payload.message(), Theme.TEXT_DIM);
            }
        }
        refreshCraftable();
        rebuild();
    }

    private void showStatus(String message, int color) {
        status = message.isEmpty() ? null : DialogueScreen.parse(message);
        statusColor = color;
        statusUntil = ticks + 80;
    }

    private void refreshCraftable() {
        LocalPlayer player = Minecraft.getInstance().player;
        craftable.clear();
        if (player == null) return;
        for (FrontierRecipe r : CraftingClient.recipes()) {
            if (r.station() == station) craftable.put(r.id(), r.maxCrafts(player, FrontierCrafting.MAX_BATCH));
        }
    }

    // ---- Ticking & input ---------------------------------------------------------------

    @Override
    public void tick() {
        ticks++;
        refreshCraftable();
        updateCraftButton();
        if (tooFar()) onClose();
    }

    /** Closes the window once the player walks away from the station or the smith. */
    private boolean tooFar() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return true;
        if (npcEntity >= 0 && player.level().getEntity(npcEntity) instanceof Entity npc) {
            return npc.distanceToSqr(player) > (FrontierCrafting.NPC_RANGE + 1) * (FrontierCrafting.NPC_RANGE + 1);
        }
        double r = (npcEntity >= 0 ? FrontierCrafting.NPC_RANGE : FrontierCrafting.BLOCK_RANGE) + 1;
        return player.distanceToSqr(Vec3.atCenterOf(anchor)) > r * r;
    }

    @Override
    public void removed() {
        if (crafting != null) PacketDistributor.sendToServer(new CraftingNetwork.CancelCraft());
        crafting = null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        if ((key == 257 || key == 335) && crafting == null && canCraftSelected()) { // enter
            pressCraft();
            return true;
        }
        if (key == 264 || key == 265) { // down / up: move through the list
            moveSelection(key == 264 ? 1 : -1);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void moveSelection(int dir) {
        List<FrontierRecipe> list = rows.stream().map(Row::recipe).filter(r -> r != null).toList();
        if (list.isEmpty()) return;
        int i = list.indexOf(selected);
        select(list.get(Mth.clamp(i < 0 ? 0 : i + dir, 0, list.size() - 1)));
        scrollTo(selected);
        rebuild();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        Row row = rowAt(mx, my);
        if (row != null && row.recipe() != null) {
            select(row.recipe());
            rebuild();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < cx0() + listW() + 4) {
            scroll -= sy * 14;
            clampScroll();
        } else if (selected != null && my > cy1() - 20) {
            setQuantity(quantity + (sy > 0 ? 1 : -1));
        }
        return true;
    }

    @Nullable
    private Row rowAt(double mx, double my) {
        int x0 = cx0(), y0 = cy0(), y1 = cy1();
        if (mx < x0 || mx >= x0 + listW() || my < y0 || my >= y1) return null;
        double local = my - y0 - 2 + scroll;
        for (Row r : rows) {
            if (local >= r.y() && local < r.y() + r.h()) return r;
        }
        return null;
    }

    // ---- Rendering -------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = Mth.clamp((ticks + partialTick) / 5f, 0f, 1f);
        renderBackground(g, mouseX, mouseY, partialTick);
        g.pose().pushPose();
        g.pose().translate(0, (1 - t) * 8, 0);
        Gfx.alpha = t;
        Gfx.panel(g, left, top, w, h, 8, Theme.PANEL_TOP, Theme.PANEL_BOTTOM, Theme.BORDER, station.color);
        renderTitle(g);
        @Nullable ItemStack hovered = renderList(g, mouseX, mouseY);
        ItemStack detailHover = renderDetail(g, mouseX, mouseY, partialTick);
        if (hovered == null) hovered = detailHover;
        for (var r : renderables) r.render(g, mouseX, mouseY, partialTick);
        Gfx.alpha = 1f;
        g.pose().popPose();
        if (hovered != null && !hovered.isEmpty()) g.renderTooltip(font, hovered, mouseX, mouseY);
    }

    private void renderTitle(GuiGraphics g) {
        float[] diamond = Gfx.ngon(left + 16, top + 10, 3.5f, 4, 0);
        Gfx.poly(g, diamond, top + 6, top + 14, station.color, Theme.withA(station.color, 0.6f));
        Component name = Component.translatable(station.nameKey()).withStyle(ChatFormatting.BOLD);
        g.drawString(font, name, left + 24, top + 6, Gfx.fade(station.color), false);
        if (!npcId.isEmpty()) {
            Component smith = Component.translatable("gui.slfrontier.craft.run_by", Component.translatable("entity.slfrontier." + npcId));
            g.drawString(font, smith, left + 30 + font.width(name), top + 6, Gfx.fade(Theme.TEXT_DIM), false);
        }
        Component right = Component.translatable("gui.slfrontier.craft.level", data().level());
        g.drawString(font, right, left + w - 12 - font.width(right), top + 6, Gfx.fade(Theme.TEXT_DIM), false);
        Gfx.hgrad(g, left + 10, top + 17, left + w / 2f, top + 18, Theme.withA(station.color, 0.05f), Theme.withA(station.color, 0.5f));
        Gfx.hgrad(g, left + w / 2f, top + 17, left + w - 10, top + 18, Theme.withA(station.color, 0.5f), Theme.withA(station.color, 0.05f));
    }

    /** @return the item under the mouse, for a tooltip */
    @Nullable
    private ItemStack renderList(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = cx0(), y0 = cy0(), lw = listW(), y1 = cy1();
        Gfx.inset(g, x0, y0, lw, y1 - y0);
        ItemStack hovered = null;
        if (rows.isEmpty()) {
            Component empty = Component.translatable(CraftingClient.recipes().isEmpty() ? "gui.slfrontier.craft.no_recipes" : "gui.slfrontier.craft.empty");
            Gfx.wrapped(g, font, empty, x0 + 8, y0 + 10, lw - 16, Theme.TEXT_FAINT, 10);
            return null;
        }
        clampScroll();
        g.enableScissor(x0 + 1, y0 + 1, x0 + lw - 1, y1 - 1);
        for (Row row : rows) {
            int y = y0 + 2 + row.y() - (int) scroll;
            if (y + row.h() < y0 || y > y1) continue;
            if (row.header() != null) {
                g.drawString(font, row.header(), x0 + 5, y + 4, Gfx.fade(Theme.TEXT_DIM), false);
                int tw = font.width(row.header()) + 9;
                Gfx.hgrad(g, x0 + tw, y + 8, x0 + lw - 6, y + 9, Theme.withA(Theme.ACCENT, 0.4f), Theme.withA(Theme.ACCENT, 0f));
                continue;
            }
            FrontierRecipe r = row.recipe();
            FrontierRecipe.Lock lock = lock(r);
            boolean sel = r == selected;
            boolean hover = mouseX >= x0 && mouseX < x0 + lw && mouseY >= Math.max(y, y0) && mouseY < Math.min(y + ROW - 1, y1);
            if (sel) {
                Gfx.chamfer(g, x0 + 2, y, lw - 4, ROW - 2, 3, 0, 3, 0, Theme.withA(station.color, 0.32f), Theme.withA(station.color, 0.1f));
                Gfx.rect(g, x0 + 2, y, x0 + 3.5f, y + ROW - 2, station.color);
            } else if (hover) {
                Gfx.chamfer(g, x0 + 2, y, lw - 4, ROW - 2, 3, 0, 3, 0, 0x30FFFFFF, 0x10FFFFFF);
            }
            ItemStack stack = r.result();
            g.renderItem(stack, x0 + 6, y + 3);
            g.renderItemDecorations(font, stack, x0 + 6, y + 3);
            boolean locked = lock != FrontierRecipe.Lock.NONE;
            if (locked) Gfx.rect(g, x0 + 6, y + 3, x0 + 22, y + 19, 0xA0060C16);
            if (hover && mouseX < x0 + 24) hovered = stack;

            int count = craftable(r);
            int nameColor = locked ? Theme.TEXT_FAINT : sel ? 0xFFFFFFFF : Theme.TEXT;
            String name = font.plainSubstrByWidth(stack.getHoverName().getString(), lw - 32);
            g.drawString(font, name, x0 + 26, y + 3, Gfx.fade(nameColor), !locked);
            Component sub;
            int subColor;
            if (locked) {
                sub = lockText(r, lock);
                subColor = lock == FrontierRecipe.Lock.STATION ? 0xFF6A7A90 : 0xFFC07070;
            } else if (count > 0) {
                sub = Component.translatable("gui.slfrontier.craft.ready", count);
                subColor = Theme.GREEN;
            } else {
                sub = Component.translatable("gui.slfrontier.craft.missing");
                subColor = 0xFFB08A60;
            }
            String subText = font.plainSubstrByWidth(sub.getString(), lw - 32);
            g.drawString(font, subText, x0 + 26, y + 13, Gfx.fade(subColor), false);
        }
        g.disableScissor();
        int content = contentHeight(), view = viewHeight();
        if (content > view) {
            float frac = (float) view / content;
            float sy = y0 + 2 + view * (float) (scroll / content);
            Gfx.rect(g, x0 + lw - 3, sy, x0 + lw - 1.5f, sy + view * frac, Theme.withA(station.color, 0.6f));
        }
        return hovered;
    }

    private Component lockText(FrontierRecipe r, FrontierRecipe.Lock lock) {
        return switch (lock) {
            case STATION -> Component.translatable("gui.slfrontier.craft.lock.station", Component.translatable(r.station().nameKey()));
            case NPC -> Component.translatable("gui.slfrontier.craft.lock.npc", Component.translatable("entity.slfrontier." + r.npc()));
            case STAGE -> Component.translatable("gui.slfrontier.craft.lock.stage", FrontierCrafting.roman(r.requiresStage().act));
            case LEVEL -> Component.translatable("gui.slfrontier.craft.lock.level", r.minLevel());
            case NONE -> Component.empty();
        };
    }

    /** @return the item under the mouse, for a tooltip */
    @Nullable
    private ItemStack renderDetail(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = dx(), y = cy0(), dw = dw();
        FrontierRecipe r = selected;
        if (r == null) {
            g.drawString(font, Component.translatable("gui.slfrontier.craft.select"), x, y + 4, Gfx.fade(Theme.TEXT_FAINT), false);
            return null;
        }
        ItemStack hovered = null;
        ItemStack result = r.result();
        FrontierRecipe.Lock lock = lock(r);

        // Result: big icon, name, category and smith chips.
        Gfx.chamfer(g, x, y, 40, 40, 6, 0, 6, 0, Theme.withA(station.color, 0.28f), 0x90081018);
        Gfx.outline(g, Gfx.chamferPoints(x, y, 40, 40, 6, 0, 6, 0), 1f, station.color);
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 4, 0);
        g.pose().scale(2f, 2f, 1f);
        g.renderItem(result, 0, 0);
        g.pose().popPose();
        if (result.getCount() > 1) {
            String n = "×" + result.getCount();
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.drawString(font, n, x + 38 - font.width(n), y + 30, Gfx.fade(0xFFFFFFFF), true);
            g.pose().popPose();
        }
        if (mouseX >= x && mouseX < x + 40 && mouseY >= y && mouseY < y + 40) hovered = result;
        Gfx.text(g, font, Component.literal(font.plainSubstrByWidth(result.getHoverName().getString(), (int) ((dw - 46) / 1.25f))),
                x + 46, y + 2, 1.25f, rarityColor(result), true);
        Component cat = Component.translatableWithFallback("gui.slfrontier.craft.cat." + r.category(), r.category());
        int chipX = chip(g, x + 46, y + 16, cat, station.color);
        if (!r.npc().isEmpty()) chip(g, chipX + 3, y + 16, Component.translatable("entity.slfrontier." + r.npc()), Theme.GOLD);
        String time = String.format("%.1fs", r.time() / 20f);
        Gfx.icon(g, Theme.icon("hourglass"), x + 46, y + 30, 9, 16);
        g.drawString(font, time, x + 58, y + 31, Gfx.fade(Theme.TEXT_DIM), false);
        if (r.minLevel() > 1) {
            Component lv = Component.translatable("gui.slfrontier.craft.req_level", r.minLevel());
            g.drawString(font, lv, x + 64 + font.width(time), y + 31,
                    Gfx.fade(data().level() >= r.minLevel() ? Theme.TEXT_DIM : Theme.RED), false);
        }

        // Bottom block (fixed): status line, progress bar, buttons.
        int barY = cy1() - 27;
        int statusY = barY - 11;
        // Materials block sits above the status line; the tooltip gets whatever space is left.
        int matH = 12 + r.ingredients().size() * ING_ROW;
        int matY = statusY - 4 - matH;
        int lore = y + 45;
        int maxLines = Math.max(0, (matY - 4 - lore) / 10);
        List<FormattedCharSequence> lines = new ArrayList<>();
        LocalPlayer player = Minecraft.getInstance().player;
        List<Component> tip = result.getTooltipLines(Item.TooltipContext.of(player.level()), player, TooltipFlag.Default.NORMAL);
        for (int i = 1; i < tip.size(); i++) {
            if (tip.get(i).getString().isBlank()) continue;
            lines.addAll(font.split(tip.get(i), dw));
        }
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            boolean cut = i == maxLines - 1 && lines.size() > maxLines;
            if (cut) g.drawString(font, "…", x, lore + i * 10, Gfx.fade(Theme.TEXT_FAINT), false);
            else g.drawString(font, lines.get(i), x, lore + i * 10, Gfx.fade(0xFFFFFFFF), false);
        }

        matY = Math.min(matY, lore + Math.min(maxLines, lines.size()) * 10 + 6);
        Gfx.header(g, font, Component.translatable("gui.slfrontier.craft.materials"), x, matY, dw);
        int iy = matY + 12;
        for (FrontierRecipe.Part part : r.ingredients()) {
            List<ItemStack> examples = part.examples();
            ItemStack icon = examples.get((ticks / 20) % examples.size());
            int need = part.count() * Math.max(1, quantity);
            int have = FrontierRecipe.have(player, part);
            Gfx.chamfer(g, x, iy, dw, ING_ROW - 2, 2, 0, 2, 0, 0x50101C2C, 0x30081018);
            g.renderItem(icon, x + 2, iy);
            g.drawString(font, font.plainSubstrByWidth(part.name().getString(), dw - 70), x + 22, iy + 4, Gfx.fade(Theme.TEXT), false);
            String count = have + "/" + need;
            g.drawString(font, count, x + dw - 5 - font.width(count), iy + 4, Gfx.fade(have >= need ? Theme.GREEN : Theme.RED), true);
            if (mouseX >= x && mouseX < x + dw && mouseY >= iy && mouseY < iy + ING_ROW - 2) hovered = icon;
            iy += ING_ROW;
        }

        // Status: a fresh message, the running craft, why it's locked, or readiness.
        boolean running = crafting != null && crafting.equals(r.id());
        Component line;
        int color;
        if (status != null && ticks < statusUntil) {
            line = status;
            color = statusColor;
        } else if (crafting != null) {
            FrontierRecipe c = CraftingClient.get(crafting);
            line = Component.translatable("gui.slfrontier.craft.progress." + station.id,
                    c == null ? Component.empty() : c.result().getHoverName(), craftQty);
            color = station.color;
        } else if (lock != FrontierRecipe.Lock.NONE) {
            line = lockText(r, lock);
            color = Theme.RED;
        } else if (craftable(r) < quantity) {
            line = Component.translatable("gui.slfrontier.craft.missing");
            color = 0xFFE0A060;
        } else {
            line = Component.translatable("gui.slfrontier.craft.batch_time", quantity, String.format("%.1f", r.batchTicks(quantity) / 20f));
            color = Theme.TEXT_DIM;
        }
        g.drawString(font, font.plainSubstrByWidth(line.getString(), dw), x, statusY, Gfx.fade(color), false);

        float frac = 0;
        if (crafting != null) frac = Mth.clamp((ticks - craftStart + partialTick) / craftTotal, 0f, 1f);
        else if (status != null && ticks < statusUntil && statusColor == Theme.GREEN) frac = 1f;
        Gfx.bar(g, x, barY, dw, 5, frac, station.color, Theme.withA(station.color, 0.55f), Theme.BAR_BACK, 0);
        if (crafting != null) {
            float shine = (ticks + partialTick) % 20 / 20f;
            float sx = x + dw * frac * shine;
            Gfx.rect(g, sx, barY, Math.min(x + dw * frac, sx + 3), barY + 5, 0x60FFFFFF);
            if (running) {
                String left = String.format("%.1fs", Math.max(0, craftTotal - (ticks - craftStart + partialTick)) / 20f);
                g.drawString(font, left, x + dw - font.width(left), statusY, Gfx.fade(Theme.TEXT), false);
            }
        }

        // Quantity readout between the quantity buttons and the Craft button.
        String q = "×" + quantity;
        g.drawString(font, q, x + 88, cy1() - 13, Gfx.fade(Theme.TEXT), true);
        return hovered;
    }

    /** A small coloured label; returns its right edge. */
    private int chip(GuiGraphics g, int x, int y, Component text, int color) {
        int cw = font.width(text) + 8;
        Gfx.chamfer(g, x, y, cw, 11, 2, 0, 2, 0, Theme.withA(color, 0.45f), Theme.withA(color, 0.2f));
        g.drawString(font, text, x + 4, y + 2, Gfx.fade(0xFFFFFFFF), false);
        return x + cw;
    }

    private static int rarityColor(ItemStack stack) {
        Rarity rarity = stack.getRarity();
        if (rarity == Rarity.COMMON) return Theme.GOLD;
        Integer c = rarity.color().getColor();
        return c == null ? Theme.GOLD : 0xFF000000 | c;
    }
}
