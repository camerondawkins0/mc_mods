package com.slfrontier.client.screen;

import com.slfrontier.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * NPC dialogue: one line at a time in a box at the bottom of the screen, with the
 * reply options shown after the last line. Text appears with a typewriter effect.
 */
public class DialogueScreen extends Screen {
    private static final int BOX_HEIGHT = 74;
    private final ModNetwork.OpenDialogue dialogue;
    private int line;
    private float typed;

    public DialogueScreen(ModNetwork.OpenDialogue dialogue) {
        super(Component.translatable(dialogue.speakerKey()));
        this.dialogue = dialogue;
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
        int boxTop = height - BOX_HEIGHT - 8;
        if (!onLastLine()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.slfrontier.next"), b -> advance())
                    .bounds(width / 2 + 120, boxTop + BOX_HEIGHT - 24, 60, 18).build());
            return;
        }
        List<String> options = dialogue.options();
        int bw = 220;
        int y = boxTop - 6 - options.size() * 22;
        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            addRenderableWidget(Button.builder(Component.translatable(options.get(i)), b -> choose(index))
                    .bounds(width / 2 - bw / 2, y + i * 22, bw, 20).build());
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
        String raw = dialogue.lines().get(Math.min(line, dialogue.lines().size() - 1));
        String[] parts = raw.split("\\|");
        Object[] args = new Object[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return Component.translatable(parts[0], args);
    }

    @Override
    public void tick() {
        typed += 1.6f;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (mouseY > height - BOX_HEIGHT - 8) {
            advance();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 32 || key == 257) { // space / enter
            advance();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int left = width / 2 - 190;
        int right = width / 2 + 190;
        int top = height - BOX_HEIGHT - 8;
        g.fill(left, top, right, top + BOX_HEIGHT, 0xD0101018);
        g.fill(left, top, right, top + 1, 0xFF7FE7FF);
        g.fill(left, top + BOX_HEIGHT - 1, right, top + BOX_HEIGHT, 0xFF7FE7FF);
        // Speaker tag
        int nameWidth = font.width(title) + 12;
        g.fill(left + 8, top - 12, left + 8 + nameWidth, top + 2, 0xFF1B3B5A);
        g.drawString(font, title, left + 14, top - 9, 0xFFD34F, true);
        // Typewriter text
        String full = currentLine().getString();
        String shown = full.substring(0, Math.min(full.length(), (int) typed));
        int y = top + 10;
        for (FormattedCharSequence seq : font.split(Component.literal(shown), right - left - 20)) {
            g.drawString(font, seq, left + 10, y, 0xF0F0F0, false);
            y += 11;
        }
        String counter = (Math.min(line, dialogue.lines().size() - 1) + 1) + "/" + dialogue.lines().size();
        g.drawString(font, counter, right - 10 - font.width(counter), top + BOX_HEIGHT - 12, 0x8090A0, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
