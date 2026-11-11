package dev.kingbrezz.featherfps.fabric;

import dev.kingbrezz.featherfps.core.Info;
import dev.kingbrezz.featherfps.core.ui.FeatherView;
import dev.kingbrezz.featherfps.core.ui.ViewEnvironment;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * UNCOMPILED (written against the documented 1.21.11 input API: MouseButtonEvent / KeyEvent, never built).
 * A thin shell: all layout and behavior live in the tested core FeatherView.
 */
public final class FeatherScreen extends Screen {
    private final Screen parent;
    private final FeatherView view;

    public FeatherScreen(Screen parent) {
        super(Component.literal(Info.NAME));
        this.parent = parent;
        FeatherFpsClient m = FeatherFpsClient.instance();
        ViewEnvironment env = new ViewEnvironment(
                m.minecraftVersion(), "Fabric",
                Set.of(), // no loader-dependent setting is implemented yet, so all of them show as INACTIVE
                m.compat(), m.monitor(), m.store(), m.config(),
                "No frame data yet. FPS is sampled while the in-game HUD is drawn.",
                m.store().file().resolveSibling("feather-fps-export.properties"),
                this::onClose);
        this.view = new FeatherView(env);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        view.render(new GuiGraphicsRenderer(graphics, this.font), this.width, this.height, mouseX, mouseY, System.nanoTime());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return view.mouseClicked(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return view.mouseReleased(event.x(), event.y(), event.button()) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return view.mouseDragged(event.x(), event.y(), event.button()) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return view.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return view.keyPressed(event.key(), event.modifiers()) || super.keyPressed(event);
    }

    /** Feeds the settings search box. UNVERIFIED: CharacterEvent.codepoint() is assumed from the 1.21.9+ input rework. */
    @Override
    public boolean charTyped(CharacterEvent event) {
        return view.charTyped(event.codepoint()) || super.charTyped(event);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    /** Keep the world running underneath so the dashboard shows live numbers. */
    @Override
    public boolean isPauseScreen() { return false; }
}
