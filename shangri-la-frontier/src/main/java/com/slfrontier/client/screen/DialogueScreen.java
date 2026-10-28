package com.slfrontier.client.screen;

import com.slfrontier.client.ui.Gfx;
import com.slfrontier.client.ui.SlfButton;
import com.slfrontier.client.ui.Theme;
import com.slfrontier.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * NPC dialogue, framed like a cutscene: letterbox bars, a live portrait of the speaker,
 * typewriter text, and numbered replies (click or press 1-9).
 */
public class DialogueScreen extends Screen {
    private static final int BOX_HEIGHT = 74;
    private final ModNetwork.OpenDialogue dialogue;
    private int line;
    private float typed;
    private float openTicks;

    public DialogueScreen(ModNetwork.OpenDialogue dialogue) {
        super(Component.translatable(dialogue.speakerKey()));
        this.dialogue = dialogue;
    }

    /** Turns {@code key|arg|@argKey} into a component; '@' marks an argument that is itself a translation key. */
    public static Component parse(String raw) {
        String[] parts = raw.split("\\|");
        Object[] args = new Object[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            args[i - 1] = parts[i].startsWith("@") ? Component.translatable(parts[i].substring(1)) : parts[i];
        }
        return Component.translatable(parts[0], args);
    }

    private int boxLeft() {
        return Math.max(10, width / 2 - 220);
    }

    private int boxRight() {
        return Math.min(width - 10, width / 2 + 220);
    }

    private int boxTop() {
        return height - BOX_HEIGHT - 12;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private boolean onLastLine() {
        return line >= dialogue.lines().size() - 1;
    }

    private void rebuild() {
        clearWidgets();
        if (!onLastLine()) return;
        List<String> options = dialogue.options();
        int bw = Math.min(250, boxRight() - boxLeft() - 90);
        int x = boxRight() - bw;
        int y = boxTop() - 8 - options.size() * 19;
        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            Component label = Component.literal((i + 1) + ".  ").withStyle(ChatFormatting.AQUA).append(parse(options.get(i)));
            addRenderableWidget(new SlfButton(x, y + i * 19, bw, 17, label, SlfButton.Style.NORMAL, b -> choose(index)));
        }
    }

    private void advance() {
        if (typed < currentLine().getString().length()) {
            typed = 9999;
            return;
        }
        if (!onLastLine()) {
            line++;
            typed = 0;
            rebuild();
        }
    }

    private void choose(int option) {
        PacketDistributor.sendToServer(new ModNetwork.DialogueChoice(dialogue.entityId(), option));
        onClose();
    }

    private Component currentLine() {
        if (dialogue.lines().isEmpty()) return Component.empty();
        return parse(dialogue.lines().get(Math.min(line, dialogue.lines().size() - 1)));
    }

    @Override
    public void tick() {
        typed += 1.8f;
        openTicks++;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (mouseY > boxTop() - 4) {
            advance();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 32 || key == 257 || key == 335) { // space / enter
            advance();
            return true;
        }
        if (key >= 49 && key <= 57 && onLastLine() && typed >= currentLine().getString().length()) { // 1-9
            int option = key - 49;
            if (option < dialogue.options().size()) {
                choose(option);
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Letterbox bars instead of a blurred world: the speaker stays visible.
        float t = Mth.clamp((openTicks + partialTick) / 6f, 0f, 1f);
        int bar = (int) (22 * t);
        Gfx.rect(g, 0, 0, width, bar, 0xF0000000);
        Gfx.vgrad(g, 0, height - BOX_HEIGHT - 60, width, height, 0x00000000, 0xC0000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        float t = Mth.clamp((openTicks + partialTick) / 5f, 0f, 1f);
        Gfx.alpha = t;
        int left = boxLeft(), right = boxRight(), top = boxTop();
        g.pose().pushPose();
        g.pose().translate(0, (1 - t) * 10, 0);
        Gfx.panel(g, left, top, right - left, BOX_HEIGHT, 8);

        // Portrait of the speaker
        int pw = 70, ph = 88;
        int px = left + 8, py = top - 22;
        Gfx.chamfer(g, px, py, pw, ph, 6, 0, 6, 0, 0xF0182A42, 0xF00A1422);
        Gfx.outline(g, Gfx.chamferPoints(px, py, pw, ph, 6, 0, 6, 0), 1f, Theme.ACCENT);
        Entity npc = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(dialogue.entityId());
        if (npc instanceof LivingEntity living) {
            // Frame head and upper body: the top ~60% of the model fills the portrait.
            float height = Math.max(0.5f, living.getBbHeight());
            int scale = (int) Mth.clamp((ph - 14) / (0.62f * height), 10, 90);
            int centerY = (int) (py + 10 + height * scale / 2f);
            g.enableScissor(px + 1, py + 1, px + pw - 1, py + ph - 1);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px, centerY - ph / 2, px + pw, centerY + ph / 2,
                    scale, 0.0625f, mouseX, mouseY, living);
            g.disableScissor();
        }

        // Name plate
        int nx = px + pw + 8;
        int nameWidth = font.width(title) + 16;
        Gfx.chamfer(g, nx, top - 12, nameWidth, 14, 4, 0, 4, 0, 0xF02A5F88, 0xF0123A5A);
        Gfx.outline(g, Gfx.chamferPoints(nx, top - 12, nameWidth, 14, 4, 0, 4, 0), 1f, Theme.GOLD);
        g.drawString(font, title, nx + 8, top - 9, Gfx.fade(Theme.GOLD), true);

        // Typewriter text
        String full = currentLine().getString();
        String shown = full.substring(0, Math.min(full.length(), (int) typed));
        int y = top + 10;
        for (FormattedCharSequence seq : font.split(Component.literal(shown), right - nx - 12)) {
            g.drawString(font, seq, nx, y, Gfx.fade(0xFFF0F6FF), false);
            y += 11;
        }
        boolean done = typed >= full.length();
        if (done && !onLastLine() && ((int) (openTicks / 8)) % 2 == 0) {
            float ax = right - 18, ay = top + BOX_HEIGHT - 14;
            Gfx.tri(g, ax, ay, Theme.ACCENT, ax + 8, ay, Theme.ACCENT, ax + 4, ay + 5, Theme.ACCENT_SOFT);
            Gfx.flush(g);
        }
        String counter = (Math.min(line, dialogue.lines().size() - 1) + 1) + "/" + dialogue.lines().size();
        g.drawString(font, counter, nx, top + BOX_HEIGHT - 12, Gfx.fade(Theme.TEXT_FAINT), false);
        if (!onLastLine()) {
            Component hint = Component.translatable("gui.slfrontier.dialogue_hint");
            g.drawString(font, hint, right - 28 - font.width(hint), top + BOX_HEIGHT - 12, Gfx.fade(Theme.TEXT_FAINT), false);
        }
        g.pose().popPose();
        for (var r : renderables) r.render(g, mouseX, mouseY, partialTick);
        Gfx.alpha = 1f;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
