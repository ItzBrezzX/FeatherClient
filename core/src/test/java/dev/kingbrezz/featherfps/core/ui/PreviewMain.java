package dev.kingbrezz.featherfps.core.ui;

import dev.kingbrezz.featherfps.core.Section;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * Renders the real FeatherView layout offscreen to PNG files. NOT a game screenshot: the frame data is synthetic
 * (clearly labelled in each image), memory and CPU are this JVM's real readings, and the font is a stand-in.
 */
public final class PreviewMain {
    static final int BANNER = 30;

    static final class AwtRenderer implements UiRenderer {
        final Graphics2D g;
        final Font font;
        final FontRenderContext frc;
        final java.util.ArrayList<Shape> saved = new java.util.ArrayList<>();

        AwtRenderer(Graphics2D g) {
            this.g = g;
            this.font = new Font("DejaVu Sans", Font.PLAIN, 1).deriveFont(8f);
            g.setFont(font);
            this.frc = g.getFontRenderContext();
        }

        @Override public void fillRect(int x, int y, int w, int h, int argb) { g.setColor(new Color(argb, true)); g.fillRect(x, y, w, h); }
        @Override public void drawText(String t, int x, int y, int argb) { g.setColor(new Color(argb, true)); g.drawString(t, x, y + 7); }
        @Override public int textWidth(String t) { return (int) Math.ceil(font.getStringBounds(t, frc).getWidth()); }
        @Override public int lineHeight() { return 9; }
        @Override public void pushClip(int x, int y, int w, int h) { saved.add(g.getClip()); g.clipRect(x, y, w, h); }
        @Override public void popClip() { g.setClip(saved.remove(saved.size() - 1)); }
    }

    /** Feeds synthetic frames (deterministic pseudo-random jitter plus an occasional hitch) into the monitor. */
    static void feed(UiTests.Fx fx, double seconds, double baseMs, double jitterMs, int hitchEvery, long seed) {
        long ts = fx.t, end = ts + (long) (seconds * 1e9);
        int i = 0;
        while (ts < end) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double j = ((seed >>> 33) % 1000) / 1000.0;
            long dt = (long) ((baseMs + j * jitterMs) * 1e6);
            if (hitchEvery > 0 && ++i % hitchEvery == 0) dt = (long) ((18 + j * 16) * 1e6);
            ts += dt;
            fx.pm.onFrame(ts);
        }
        fx.t = ts;
    }

    static UiTests.Fx scene(Set<String> active, Collection<String> mods, Section s) throws Exception {
        UiTests.Fx fx = new UiTests.Fx(active, mods);
        fx.w = 640; fx.h = 360;   // GUI scale 3 on a 1080p window
        feed(fx, 60, 6.4, 1.4, 430, 12345);   // 60 s at roughly 144 fps with a hitch every ~430 frames
        fx.view.select(s);
        return fx;
    }

    static BufferedImage paint(UiTests.Fx fx, long advanceNs) {
        int W = fx.w, H = fx.h, SCALE = W >= 600 ? 2 : 3;
        BufferedImage img = new BufferedImage(W * SCALE, H * SCALE + BANNER, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(new Color(0xFF14141C));
        g.fillRect(0, 0, img.getWidth(), BANNER);
        g.setColor(new Color(0xFFFACC15));
        g.setFont(new Font("DejaVu Sans", Font.BOLD, 12));
        g.drawString("LAYOUT PREVIEW: synthetic frame data, offscreen render, substitute font. Not a Minecraft screenshot.", 10, 20);
        g.translate(0, BANNER);
        g.setPaint(new GradientPaint(0, 0, new Color(0xFF4A6A8A), 0, H * SCALE, new Color(0xFF2E4A2E)));
        g.fillRect(0, 0, W * SCALE, H * SCALE);
        g.scale(SCALE, SCALE);
        fx.t += advanceNs;
        fx.view.render(new AwtRenderer(g), W, H, fx.mx, fx.my, fx.t);
        g.dispose();
        return img;
    }

    static void draw(UiTests.Fx fx, String name, File dir) throws Exception {
        paint(fx, 600_000_000L);                       // settles readings and animation state
        BufferedImage img = paint(fx, 16_000_000L);
        ImageIO.write(img, "png", new File(dir, name + ".png"));
        System.out.println("wrote " + name + ".png");
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File dir = new File(args[0]);
        dir.mkdirs();

        UiTests.Fx a = scene(Set.of(), List.of("sodium", "lithium"), Section.DASHBOARD);
        draw(a, "1-dashboard", dir);
        UiTests.Fx a2 = scene(Set.of(), List.of("sodium", "lithium"), Section.DASHBOARD);
        a2.w = 480; a2.h = 270;   // smallest common GUI scale
        draw(a2, "1b-dashboard-compact", dir);

        UiTests.Fx b = scene(Set.of(), List.of(), Section.PERFORMANCE);
        b.view.options().stage("monitor.historySeconds", "120");
        b.view.options().stage("monitor.intervalMs", "250");
        b.frame();
        b.reveal("row:memory.warnings");
        b.frame();
        Rect row = b.view.hitRect("row:monitor.intervalMs");
        b.mx = row.x() + 40; b.my = row.y() + 6;
        draw(b, "2-performance-pending-tooltip", dir);

        UiTests.Fx c = scene(Set.of(), List.of("entityculling"), Section.FPS);
        draw(c, "3-fps-inactive-settings", dir);

        UiTests.Fx d = scene(Set.of(), List.of(), Section.PROFILES);
        d.click("profile:PERFORMANCE");
        d.click("btn:export");
        d.view.mouseScrolled(240, 135, 100);
        for (int n = 0; n < 40; n++) d.frame();
        d.mx = -1; d.my = -1;
        draw(d, "4-profiles", dir);

        UiTests.Fx e = scene(Set.of("rendering.skipOffscreenBlockEntities"), List.of("sodium", "entityculling", "lithium", "ferritecore"), Section.COMPATIBILITY);
        draw(e, "5-compatibility", dir);

        UiTests.Fx f = scene(Set.of(), List.of(), Section.ABOUT);
        draw(f, "6-about", dir);

        UiTests.Fx g = scene(Set.of(), List.of("sodium"), Section.PERFORMANCE);
        g.pm.benchmark().start(g.t, 15);
        feed(g, 16, 8.2, 1.2, 300, 777);
        g.pm.benchmark().start(g.t, 15);
        feed(g, 16, 6.3, 1.0, 600, 4242);
        draw(g, "7-benchmark", dir);

        UiTests.Fx h = scene(Set.of(), List.of(), Section.DASHBOARD);
        h.click("btn:graph-ft");
        h.mx = -1; h.my = -1;
        draw(h, "8-frametime-graph", dir);

        UiTests.Fx i = scene(Set.of(), List.of(), Section.DASHBOARD);
        i.view.keyPressed(Keys.F, Keys.MOD_CONTROL);
        i.type("memory");
        draw(i, "9-search", dir);
    }
}
