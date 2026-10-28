package com.slfrontier.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** A chamfered, glowing Frontier-style button. Optionally shows an icon and a "selected" state. */
public class SlfButton extends AbstractButton {
    public enum Style { NORMAL, PRIMARY, TAB, SMALL }

    private final Consumer<SlfButton> onPress;
    private final Style style;
    @Nullable
    private ResourceLocation icon;
    private int iconTexSize = 16;
    private boolean selected;
    private int accent = Theme.ACCENT;

    public SlfButton(int x, int y, int w, int h, Component label, Style style, Consumer<SlfButton> onPress) {
        super(x, y, w, h, label);
        this.style = style;
        this.onPress = onPress;
    }

    public SlfButton icon(ResourceLocation icon, int texSize) {
        this.icon = icon;
        this.iconTexSize = texSize;
        return this;
    }

    public SlfButton accent(int color) {
        this.accent = color;
        return this;
    }

    public SlfButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public void onPress() {
        onPress.accept(this);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        boolean hover = isHoveredOrFocused() && active;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int cut = style == Style.SMALL ? 2 : 4;
        int top, bottom, border;
        if (!active) {
            top = 0x9010161F;
            bottom = 0x9008101A;
            border = 0xFF26323F;
        } else if (selected) {
            top = Theme.withA(accent, 0.55f);
            bottom = Theme.withA(accent, 0.22f);
            border = accent;
        } else if (style == Style.PRIMARY) {
            top = hover ? 0xE03A7FB0 : 0xD02A5F88;
            bottom = hover ? 0xE01A4A70 : 0xD0123A5A;
            border = hover ? 0xFFFFFFFF : accent;
        } else {
            top = hover ? 0xE0254060 : 0xD0162A42;
            bottom = hover ? 0xE0142840 : 0xD00C1828;
            border = hover ? accent : Theme.BORDER;
        }
        if (style == Style.TAB) {
            Gfx.chamfer(g, x, y, w, h, cut, cut, 0, 0, top, bottom);
            Gfx.outline(g, Gfx.chamferPoints(x, y, w, h, cut, cut, 0, 0), 1f, border);
            if (selected) Gfx.rect(g, x + 1, y + h - 1, x + w - 1, y + h + 1, Theme.PANEL_TOP);
        } else {
            Gfx.chamfer(g, x, y, w, h, cut, 0, cut, 0, top, bottom);
            Gfx.outline(g, Gfx.chamferPoints(x, y, w, h, cut, 0, cut, 0), 1f, border);
        }
        if (hover && !selected) {
            Gfx.hgrad(g, x + 2, y + 1, x + w / 2f, y + 2, Theme.withA(accent, 0f), Theme.withA(accent, 0.8f));
            Gfx.hgrad(g, x + w / 2f, y + 1, x + w - 2, y + 2, Theme.withA(accent, 0.8f), Theme.withA(accent, 0f));
        }
        int textColor = !active ? Theme.TEXT_FAINT : selected ? 0xFFFFFFFF : hover ? 0xFFFFFFFF : Theme.TEXT;
        int tx;
        int textWidth = font.width(getMessage());
        if (icon != null) {
            int is = Math.min(h - 4, 12);
            int total = is + (textWidth > 0 ? 4 + textWidth : 0);
            int ix = x + (w - total) / 2;
            Gfx.icon(g, icon, ix, y + (h - is) / 2, is, iconTexSize);
            tx = ix + is + 4;
        } else {
            tx = x + (w - textWidth) / 2;
        }
        g.drawString(font, getMessage(), tx, y + (h - 8) / 2, Gfx.fade(textColor), true);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
