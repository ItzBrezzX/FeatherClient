package dev.kingbrezz.featherfps.fabric;

import dev.kingbrezz.featherfps.core.ui.UiRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * UNCOMPILED (written against the documented 1.21.11 API, never built).
 * Maps the core's tiny drawing interface onto GuiGraphics. fill() takes corner coordinates, not width/height.
 */
final class GuiGraphicsRenderer implements UiRenderer {
    private final GuiGraphics graphics;
    private final Font font;

    GuiGraphicsRenderer(GuiGraphics graphics, Font font) {
        this.graphics = graphics;
        this.font = font;
    }

    @Override public void fillRect(int x, int y, int w, int h, int argb) { graphics.fill(x, y, x + w, y + h, argb); }

    @Override public void drawText(String text, int x, int y, int argb) { graphics.drawString(font, text, x, y, argb, false); }

    @Override public int textWidth(String text) { return font.width(text); }

    @Override public int lineHeight() { return font.lineHeight; }

    @Override public void pushClip(int x, int y, int w, int h) { graphics.enableScissor(x, y, x + w, y + h); }

    @Override public void popClip() { graphics.disableScissor(); }
}
