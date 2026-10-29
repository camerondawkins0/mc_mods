package com.slfrontier.client.screen;

import com.slfrontier.client.KeyBindings;
import com.slfrontier.client.ui.Gfx;
import com.slfrontier.client.ui.SlfButton;
import com.slfrontier.client.ui.Theme;
import com.slfrontier.data.PlayerData;
import com.slfrontier.inventory.FrontierInventoryMenu;
import com.slfrontier.inventory.ItemCategory;
import com.slfrontier.network.InventoryNetwork;
import com.slfrontier.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.slfrontier.inventory.FrontierInventoryMenu.*;

/**
 * The Frontier inventory (inventory key inside the Frontier, or the avatar menu's Items tab):
 * the avatar and its equipment on the left, the bag with category tabs and the quick slots in
 * the middle, and the details of the hovered item with an avatar summary on the right. Screens
 * too narrow for the full window drop the details column; item tooltips still work there.
 */
public class FrontierInventoryScreen extends AbstractContainerScreen<FrontierInventoryMenu> {
    private static final int FRAME_TOP = 0xC0101C2C, FRAME_BOTTOM = 0xD0060C16, FRAME_BORDER = 0xFF24486A;
    private static final int SUMMARY_H = 48;
    private static ItemCategory category = ItemCategory.ALL;

    private final List<SlfButton> categoryButtons = new ArrayList<>();
    private boolean wide;
    private float openTicks;
    private int lastClicked = -1;
    private boolean hoverMainHand;

    public FrontierInventoryScreen(FrontierInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = HEIGHT;
        menu.setFilter(category);
    }

    // ---- Layout ----------------------------------------------------------------------

    @Override
    protected void init() {
        wide = width >= WIDTH + 16;
        imageWidth = wide ? WIDTH : COMPACT_WIDTH;
        super.init();
        topPos = Math.max(19, (height - imageHeight) / 2 + 9);

        int tx = leftPos + 10;
        for (FrontierScreen.Tab t : FrontierScreen.Tab.values()) {
            Component label = Component.translatable("gui.slfrontier.tab." + t.name().toLowerCase());
            int bw = font.width(label) + 26;
            addRenderableWidget(new SlfButton(tx, topPos - 17, bw, 17, label, SlfButton.Style.TAB, b -> switchTab(t))
                    .icon(Theme.icon(t.icon), 16).selected(t == FrontierScreen.Tab.ITEMS));
            tx += bw + 3;
        }

        categoryButtons.clear();
        ItemCategory[] categories = ItemCategory.values();
        for (int i = 0; i < categories.length; i++) {
            ItemCategory c = categories[i];
            SlfButton b = new SlfButton(leftPos + BAG_X + 2 + i * 31, topPos + 36, 29, 16, Component.empty(), SlfButton.Style.TAB,
                    btn -> setCategory(c)).icon(Theme.icon("category_" + c.id), 16).accent(categoryColor(c)).selected(c == category);
            b.setTooltip(Tooltip.create(Component.translatable(c.nameKey())));
            categoryButtons.add(addRenderableWidget(b));
        }

        SlfButton sort = new SlfButton(leftPos + BAG_X + BAG_W - 46, topPos + 19, 46, 13, Component.translatable("gui.slfrontier.inv.sort"),
                SlfButton.Style.SMALL, b -> PacketDistributor.sendToServer(new InventoryNetwork.SortBag())).icon(Theme.icon("sort"), 16);
        sort.setTooltip(Tooltip.create(Component.translatable("gui.slfrontier.inv.sort_tip")));
        addRenderableWidget(sort);
    }

    private void switchTab(FrontierScreen.Tab t) {
        if (t == FrontierScreen.Tab.ITEMS) return;
        minecraft.player.closeContainer();
        FrontierScreen.open(t);
    }

    private void setCategory(ItemCategory c) {
        category = c;
        menu.setFilter(c);
        ItemCategory[] categories = ItemCategory.values();
        for (int i = 0; i < categoryButtons.size(); i++) categoryButtons.get(i).selected(categories[i] == c);
    }

    @Override
    protected void containerTick() {
        openTicks++;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (KeyBindings.MENU.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hoveredSlot != null) lastClicked = hoveredSlot.index;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ---- Rendering -------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hoverMainHand = isHovering(HAND_X, MAIN_HAND_Y, 16, 16, mouseX, mouseY);
        Gfx.alpha = Mth.clamp((openTicks + partialTick) / 4f, 0f, 1f);
        super.render(g, mouseX, mouseY, partialTick);
        Gfx.alpha = 1f;
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        super.renderTooltip(g, mouseX, mouseY);
        ItemStack held = minecraft.player.getMainHandItem();
        if (hoverMainHand && menu.getCarried().isEmpty() && !held.isEmpty()) {
            g.renderTooltip(font, getTooltipFromContainerItem(held), held.getTooltipImage(), held, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBlurredBackground(partialTick);
        renderMenuBackground(g);
        renderBg(g, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        LocalPlayer player = minecraft.player;
        g.pose().pushPose();
        g.pose().translate(leftPos, topPos, 0);
        Gfx.panel(g, 0, 0, imageWidth, imageHeight, 8);
        renderTitle(g, player);
        renderEquipment(g, player);
        renderBag(g, player);
        g.pose().popPose();
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, leftPos + EQUIP_X + 24, topPos + 38, leftPos + EQUIP_X + EQUIP_W - 24,
                topPos + imageHeight - 12, 48, 0.0625f, mouseX, mouseY, player);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (wide) renderDetails(g, minecraft.player);
    }

    @Override
    public int getSlotColor(int index) {
        return 0x5A7FE7FF;
    }

    private void renderTitle(GuiGraphics g, LocalPlayer player) {
        float[] diamond = Gfx.ngon(16, 10, 3.5f, 4, 0);
        Gfx.poly(g, diamond, 6, 14, Theme.ACCENT, Theme.ACCENT_SOFT);
        g.drawString(font, Component.translatable("gui.slfrontier.system").withStyle(ChatFormatting.BOLD), 24, 6, Gfx.fade(Theme.ACCENT), false);
        Component right = Component.translatable("gui.slfrontier.title_right", player.getDisplayName(), data().level());
        g.drawString(font, right, imageWidth - 12 - font.width(right), 6, Gfx.fade(Theme.TEXT_DIM), false);
        Gfx.hgrad(g, 10, 17, imageWidth / 2f, 18, Theme.withA(Theme.ACCENT, 0.05f), Theme.withA(Theme.ACCENT, 0.5f));
        Gfx.hgrad(g, imageWidth / 2f, 17, imageWidth - 10, 18, Theme.withA(Theme.ACCENT, 0.5f), Theme.withA(Theme.ACCENT, 0.05f));
    }

    // ---- Equipment -------------------------------------------------------------------

    private void renderEquipment(GuiGraphics g, LocalPlayer player) {
        int x = EQUIP_X, w = EQUIP_W, y0 = 34, y1 = imageHeight - 8;
        Gfx.header(g, font, Component.translatable("gui.slfrontier.inv.equipment"), x, 22, w);
        Gfx.inset(g, x, y0, w, y1 - y0);
        // The platform the avatar stands on.
        float cx = x + w / 2f;
        for (int i = 0; i < 5; i++) {
            float ry = 176 - i * 3, half = 34 - i * 4;
            Gfx.hgrad(g, cx - half, ry, cx, ry + 1, 0, Theme.withA(Theme.ACCENT, 0.15f + 0.05f * i));
            Gfx.hgrad(g, cx, ry, cx + half, ry + 1, Theme.withA(Theme.ACCENT, 0.15f + 0.05f * i), 0);
        }
        float[] hex = Gfx.ngon(x + 14, y0 + 14, 10, 6, Mth.PI / 6);
        Gfx.poly(g, hex, y0 + 4, y0 + 24, 0xF02A5F88, 0xF0123A5A);
        Gfx.outline(g, hex, 1f, Theme.GOLD);
        String lv = String.valueOf(data().level());
        g.drawString(font, lv, x + 14 - font.width(lv) / 2, y0 + 10, Gfx.fade(Theme.GOLD), true);
        g.drawString(font, Component.translatable("gui.slfrontier.job"), x + 28, y0 + 5, Gfx.fade(Theme.TEXT_DIM), false);

        for (int i = ARMOR_START; i < BAG_START; i++) {
            Slot slot = menu.slots.get(i);
            frame(g, slot.x, slot.y, slot.getItem(), Theme.BORDER);
        }
        // The held item mirrors the selected quick slot; it isn't a slot of its own.
        ItemStack held = player.getMainHandItem();
        frame(g, HAND_X, MAIN_HAND_Y, held, Theme.GOLD);
        if (held.isEmpty()) {
            float alpha = Gfx.alpha;
            Gfx.alpha = alpha * 0.3f;
            Gfx.icon(g, Theme.icon("category_weapons"), HAND_X + 2, MAIN_HAND_Y + 2, 12, 16);
            Gfx.alpha = alpha;
        } else {
            g.renderItem(held, HAND_X, MAIN_HAND_Y);
            g.renderItemDecorations(font, held, HAND_X, MAIN_HAND_Y);
        }
        if (hoverMainHand) renderSlotHighlight(g, HAND_X, MAIN_HAND_Y, 0, getSlotColor(0));

        g.drawString(font, Component.translatable("gui.slfrontier.inv.main_hand"), x + 6, y1 - 27, Gfx.fade(Theme.TEXT_DIM), false);
        Component name = held.isEmpty() ? Component.translatable("gui.slfrontier.inv.empty_hand") : held.getHoverName();
        String shown = font.plainSubstrByWidth(name.getString(), w - 12);
        g.drawString(font, shown, x + 6, y1 - 16, Gfx.fade(held.isEmpty() ? Theme.TEXT_FAINT : rarityColor(held)), !held.isEmpty());
    }

    // ---- Bag -------------------------------------------------------------------------

    private void renderBag(GuiGraphics g, LocalPlayer player) {
        int x = BAG_X, w = BAG_W;
        int used = 0, matching = 0;
        for (int i = BAG_START; i < HOTBAR_START; i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (s.isEmpty()) continue;
            used++;
            if (category.matches(s)) matching++;
        }
        Component head = category == ItemCategory.ALL ? Component.translatable("gui.slfrontier.inv.bag")
                : Component.translatable("gui.slfrontier.inv.bag_filtered", Component.translatable(category.nameKey()), matching);
        Gfx.header(g, font, head, x, 22, w - 50);
        Gfx.inset(g, x, 52, w, 68);
        Gfx.header(g, font, Component.translatable("gui.slfrontier.inv.quick_slots"), x, 126, w);
        Gfx.inset(g, x, 137, w, 28);

        int accent = categoryColor(category);
        for (int i = BAG_START; i < HOTBAR_START; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack s = slot.getItem();
            boolean shown = slot.isActive();
            frame(g, slot.x, slot.y, shown ? s : ItemStack.EMPTY, category != ItemCategory.ALL && shown && !s.isEmpty() ? accent : FRAME_BORDER);
            if (!shown) {
                g.renderItem(s, slot.x, slot.y);
                g.renderItemDecorations(font, s, slot.x, slot.y);
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                Gfx.rect(g, slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, 0xC0081018);
                g.pose().popPose();
            }
        }
        int selected = player.getInventory().selected;
        for (int i = HOTBAR_START; i < END; i++) {
            Slot slot = menu.slots.get(i);
            boolean sel = i - HOTBAR_START == selected;
            frame(g, slot.x, slot.y, slot.getItem(), sel ? Theme.GOLD : FRAME_BORDER);
            if (sel) Gfx.rect(g, slot.x + 3, slot.y + 18, slot.x + 13, slot.y + 19, Theme.GOLD);
        }

        int cy = 171;
        g.drawString(font, Component.translatable("gui.slfrontier.inv.space"), x, cy, Gfx.fade(Theme.TEXT_DIM), false);
        String count = used + "/" + (HOTBAR_START - BAG_START);
        g.drawString(font, count, x + w - font.width(count), cy, Gfx.fade(used >= HOTBAR_START - BAG_START ? Theme.RED : Theme.TEXT), false);
        Gfx.bar(g, x, cy + 10, w, 3, used / (float) (HOTBAR_START - BAG_START), Theme.ACCENT, Theme.ACCENT_SOFT, Theme.BAR_BACK, 9);
        g.drawString(font, Component.translatable("gui.slfrontier.inv.hint_move"), x, cy + 19, Gfx.fade(Theme.TEXT_FAINT), false);
        g.drawString(font, Component.translatable("gui.slfrontier.inv.hint_keys"), x, cy + 29, Gfx.fade(Theme.TEXT_FAINT), false);
    }

    /** A slot frame; items above common rarity get a glow of their rarity colour. */
    private void frame(GuiGraphics g, int x, int y, ItemStack stack, int border) {
        int bottom = !stack.isEmpty() && stack.getRarity() != Rarity.COMMON ? Theme.withA(rarityColor(stack), 0.3f) : FRAME_BOTTOM;
        Gfx.chamfer(g, x - 1, y - 1, 18, 18, 3, 0, 3, 0, FRAME_TOP, bottom);
        Gfx.outline(g, Gfx.chamferPoints(x - 1, y - 1, 18, 18, 3, 0, 3, 0), 1f, border);
    }

    // ---- Details ---------------------------------------------------------------------

    /** The hovered item, else the hovered main hand, else the carried item, else the last clicked slot's item. */
    private ItemStack detailStack() {
        if (hoveredSlot != null && hoveredSlot.hasItem()) return hoveredSlot.getItem();
        ItemStack held = minecraft.player.getMainHandItem();
        if (hoverMainHand && !held.isEmpty()) return held;
        if (!menu.getCarried().isEmpty()) return menu.getCarried();
        if (lastClicked >= 0 && lastClicked < menu.slots.size()) return menu.slots.get(lastClicked).getItem();
        return ItemStack.EMPTY;
    }

    private void renderDetails(GuiGraphics g, LocalPlayer player) {
        int x = DETAILS_X, w = DETAILS_W, y = 34, maxY = imageHeight - 8 - SUMMARY_H - 4;
        Gfx.header(g, font, Component.translatable("gui.slfrontier.inv.details"), x, 22, w);
        ItemStack stack = detailStack();
        if (stack.isEmpty()) {
            Gfx.inset(g, x, y, w, maxY - y);
            float alpha = Gfx.alpha;
            Gfx.alpha = alpha * 0.25f;
            Gfx.icon(g, Theme.icon("bag"), x + w / 2 - 12, y + 30, 24, 16);
            Gfx.alpha = alpha;
            List<FormattedCharSequence> lines = font.split(Component.translatable("gui.slfrontier.inv.no_item"), w - 20);
            int ty = y + 62;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + (w - font.width(line)) / 2, ty, Gfx.fade(Theme.TEXT_FAINT), false);
                ty += 10;
            }
        } else {
            renderItemDetails(g, player, stack, x, y, w, maxY);
        }
        renderSummary(g, player, x, imageHeight - 8 - SUMMARY_H, w);
    }

    private void renderItemDetails(GuiGraphics g, LocalPlayer player, ItemStack stack, int x, int y, int w, int maxY) {
        ItemCategory cat = ItemCategory.of(stack);
        int rarity = rarityColor(stack);
        Gfx.chamfer(g, x, y, 36, 36, 6, 0, 6, 0, Theme.withA(rarity, 0.3f), 0x90081018);
        Gfx.outline(g, Gfx.chamferPoints(x, y, 36, 36, 6, 0, 6, 0), 1f, rarity);
        g.pose().pushPose();
        g.pose().translate(x + 2, y + 2, 0);
        g.pose().scale(2f, 2f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
        if (stack.getCount() > 1) {
            String n = "×" + stack.getCount();
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.drawString(font, n, x + 35 - font.width(n), y + 27, Gfx.fade(0xFFFFFFFF), true);
            g.pose().popPose();
        }

        // Name, then the category chip (a gold badge for key items) and the rarity.
        int tx = x + 42, tw = w - 42;
        List<FormattedCharSequence> name = font.split(stack.getHoverName(), tw);
        int ny = y + 1;
        for (int i = 0; i < Math.min(2, name.size()); i++) {
            g.drawString(font, name.get(i), tx, ny, Gfx.fade(rarity), true);
            ny += 10;
        }
        int cy = Math.max(y + 24, ny + 2);
        int cx = tx;
        if (cat == ItemCategory.KEY_ITEMS) {
            Component badge = Component.translatable("gui.slfrontier.inv.key_item");
            int bw = font.width(badge) + 17;
            Gfx.chamfer(g, cx, cy, bw, 11, 2, 0, 2, 0, Theme.withA(Theme.GOLD, 0.45f), Theme.withA(Theme.GOLD, 0.15f));
            Gfx.outline(g, Gfx.chamferPoints(cx, cy, bw, 11, 2, 0, 2, 0), 1f, Theme.GOLD);
            Gfx.icon(g, Theme.icon("category_key_items"), cx + 3, cy + 1, 9, 16);
            g.drawString(font, badge, cx + 14, cy + 2, Gfx.fade(Theme.GOLD), true);
            cx += bw + 5;
        } else {
            cx = chip(g, cx, cy, Component.translatable(cat.singularKey()), categoryColor(cat)) + 5;
        }
        String rarityKey = "gui.slfrontier.rarity." + stack.getRarity().getSerializedName();
        if (I18n.exists(rarityKey) && cx + font.width(I18n.get(rarityKey)) <= x + w) {
            g.drawString(font, Component.translatable(rarityKey), cx, cy + 2, Gfx.fade(rarity), false);
        }

        // Stats, compared with what is equipped now.
        int sy = Math.max(y + 41, cy + 15);
        Stats stats = Stats.of(stack, player);
        ItemStack worn = compareWith(stack, player);
        Stats ref = worn == null ? null : Stats.of(worn, player);
        if (stats.attack() != null) {
            sy = stat(g, x, sy, w, "gui.slfrontier.inv.attack", stats.attack(), ref == null ? null : ref.attackOrBase(player));
        }
        if (stats.speed() != null) sy = stat(g, x, sy, w, "gui.slfrontier.inv.attack_speed", stats.speed(), null);
        if (stats.armor() != null) {
            sy = stat(g, x, sy, w, "gui.slfrontier.inv.armor", stats.armor(), ref == null ? null : ref.armorOrZero());
        }
        if (stats.toughness() != null) {
            sy = stat(g, x, sy, w, "gui.slfrontier.inv.toughness", stats.toughness(), ref == null ? null : ref.toughnessOrZero());
        }
        if (stack.isDamageableItem()) {
            int max = stack.getMaxDamage(), left = max - stack.getDamageValue();
            float frac = left / (float) max;
            g.drawString(font, Component.translatable("gui.slfrontier.inv.durability"), x, sy, Gfx.fade(Theme.TEXT_DIM), false);
            String v = left + "/" + max;
            g.drawString(font, v, x + w - font.width(v), sy, Gfx.fade(Theme.TEXT), false);
            int a = frac > 0.5f ? Theme.GREEN : frac > 0.2f ? Theme.GOLD : Theme.RED;
            Gfx.bar(g, x, sy + 9, w, 3, frac, a, Gfx.lerpColor(a, 0xFF000000, 0.35f), Theme.BAR_BACK, 0);
            sy += 15;
        }

        // Lore, effects and enchantments, cut short with an ellipsis when they don't fit.
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component line : description(stack)) {
            if (lines.isEmpty() && line.getString().isBlank()) continue;
            lines.addAll(font.split(line, w));
        }
        int room = Math.max(0, (maxY - sy - 2) / 10);
        int shown = lines.size() > room ? Math.max(0, room - 1) : lines.size();
        sy += 2;
        for (int i = 0; i < shown; i++) {
            g.drawString(font, lines.get(i), x, sy, Gfx.fade(Theme.TEXT_DIM), false);
            sy += 10;
        }
        if (shown < lines.size() && room > 0) g.drawString(font, "…", x, sy, Gfx.fade(Theme.TEXT_FAINT), false);
    }

    private int stat(GuiGraphics g, int x, int y, int w, String key, double value, @Nullable Double compare) {
        g.drawString(font, Component.translatable(key), x, y, Gfx.fade(Theme.TEXT_DIM), false);
        int right = x + w;
        if (compare != null) {
            double diff = value - compare;
            if (Math.abs(diff) >= 0.005) {
                String d = (diff > 0 ? "▲" : "▼") + format(Math.abs(diff));
                g.drawString(font, d, right - font.width(d), y, Gfx.fade(diff > 0 ? Theme.GREEN : Theme.RED), false);
                right -= font.width(d) + 4;
            }
        }
        String v = format(value);
        g.drawString(font, v, right - font.width(v), y, Gfx.fade(Theme.TEXT), false);
        Gfx.rect(g, x, y + 10, x + w, y + 10.5f, 0x301F3A55);
        return y + 12;
    }

    private static String format(double v) {
        return ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(v);
    }

    /** What a weapon or armor piece would replace: the held item, or the armor worn in its slot. */
    @Nullable
    private static ItemStack compareWith(ItemStack stack, Player player) {
        EquipmentSlot slot = player.getEquipmentSlotForItem(stack);
        if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            ItemStack worn = player.getItemBySlot(slot);
            return worn == stack ? null : worn;
        }
        ItemStack held = player.getMainHandItem();
        return held == stack || ItemCategory.of(stack) != ItemCategory.WEAPONS ? null : held;
    }

    /** The item's own tooltip lines (lore, potion effects), custom lore and enchantments. */
    private List<Component> description(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        Item.TooltipContext context = Item.TooltipContext.of(minecraft.level);
        stack.getItem().appendHoverText(stack, context, lines, TooltipFlag.NORMAL);
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) lore.addToTooltip(context, lines::add, TooltipFlag.NORMAL);
        stack.getTagEnchantments().addToTooltip(context, lines::add, TooltipFlag.NORMAL);
        stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).addToTooltip(context, lines::add, TooltipFlag.NORMAL);
        return lines;
    }

    private void renderSummary(GuiGraphics g, LocalPlayer player, int x, int y, int w) {
        PlayerData d = data();
        Gfx.header(g, font, Component.translatable("gui.slfrontier.inv.avatar"), x, y, w);
        Component lv = Component.translatable("gui.slfrontier.inv.level", d.level());
        g.drawString(font, lv, x, y + 12, Gfx.fade(Theme.GOLD), true);
        int lw = font.width(lv) + 6;
        g.drawString(font, font.plainSubstrByWidth(player.getName().getString(), w - lw), x + lw, y + 12, Gfx.fade(Theme.TEXT), false);

        g.drawString(font, Component.translatable("gui.slfrontier.hp"), x, y + 23, Gfx.fade(Theme.TEXT_DIM), false);
        String hp = Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth());
        g.drawString(font, hp, x + w - font.width(hp), y + 23, Gfx.fade(Theme.TEXT), false);
        Gfx.bar(g, x, y + 32, w, 3, player.getHealth() / Math.max(1f, player.getMaxHealth()), Theme.HP_A, Theme.HP_B, Theme.BAR_BACK, 0);

        int half = w / 2;
        summaryValue(g, x, y + 39, half - 6, "gui.slfrontier.inv.atk", format(player.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        summaryValue(g, x + half + 6, y + 39, half - 6, "gui.slfrontier.inv.def", String.valueOf(player.getArmorValue()));
    }

    private void summaryValue(GuiGraphics g, int x, int y, int w, String key, String value) {
        g.drawString(font, Component.translatable(key), x, y, Gfx.fade(Theme.TEXT_DIM), false);
        g.drawString(font, value, x + w - font.width(value), y, Gfx.fade(0xFFFFFFFF), true);
    }

    private int chip(GuiGraphics g, int x, int y, Component text, int color) {
        int tw = font.width(text) + 8;
        Gfx.chamfer(g, x, y, tw, 11, 2, 0, 2, 0, Theme.withA(color, 0.45f), Theme.withA(color, 0.15f));
        g.drawString(font, text, x + 4, y + 2, Gfx.fade(0xFFFFFFFF), false);
        return x + tw;
    }

    // ---- Helpers ---------------------------------------------------------------------

    private PlayerData data() {
        return ModAttachments.get(minecraft.player);
    }

    public static int categoryColor(ItemCategory c) {
        return switch (c) {
            case ALL -> Theme.ACCENT;
            case WEAPONS -> Theme.RED;
            case ARMOR -> 0xFF8FD8FF;
            case CONSUMABLES -> Theme.GREEN;
            case MATERIALS -> 0xFFE0A060;
            case KEY_ITEMS -> Theme.GOLD;
        };
    }

    /** Vanilla's rarity colours, tuned to the Frontier palette. */
    public static int rarityColor(ItemStack stack) {
        Rarity rarity = stack.getRarity();
        if (rarity == Rarity.COMMON) return Theme.TEXT;
        if (rarity == Rarity.UNCOMMON) return 0xFFFFE45A;
        if (rarity == Rarity.RARE) return Theme.ACCENT;
        if (rarity == Rarity.EPIC) return Theme.VIOLET;
        TextColor color = rarity.getStyleModifier().apply(Style.EMPTY).getColor();
        return color == null ? Theme.TEXT : 0xFF000000 | color.getValue();
    }

    /** Attack, attack speed, armor and toughness an item gives; null where it gives none. */
    private record Stats(@Nullable Double attack, @Nullable Double speed, @Nullable Double armor, @Nullable Double toughness) {
        static Stats of(ItemStack stack, Player player) {
            double atk = 0, spd = 0, arm = 0, tough = 0;
            boolean hasAtk = false, hasSpd = false, hasArm = false, hasTough = false;
            for (ItemAttributeModifiers.Entry e : stack.getAttributeModifiers().modifiers()) {
                AttributeModifier m = e.modifier();
                if (m.operation() != AttributeModifier.Operation.ADD_VALUE) continue;
                boolean hand = e.slot().test(EquipmentSlot.MAINHAND);
                if (hand && e.attribute().is(Attributes.ATTACK_DAMAGE)) {
                    atk += m.amount();
                    hasAtk = true;
                } else if (hand && e.attribute().is(Attributes.ATTACK_SPEED)) {
                    spd += m.amount();
                    hasSpd = true;
                } else if (e.attribute().is(Attributes.ARMOR)) {
                    arm += m.amount();
                    hasArm = true;
                } else if (e.attribute().is(Attributes.ARMOR_TOUGHNESS)) {
                    tough += m.amount();
                    hasTough = true;
                }
            }
            return new Stats(hasAtk ? player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) + atk : null,
                    hasSpd ? player.getAttributeBaseValue(Attributes.ATTACK_SPEED) + spd : null,
                    hasArm ? arm : null, hasTough ? tough : null);
        }

        double attackOrBase(Player player) {
            return attack != null ? attack : player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        }

        double armorOrZero() {
            return armor != null ? armor : 0;
        }

        double toughnessOrZero() {
            return toughness != null ? toughness : 0;
        }
    }
}
