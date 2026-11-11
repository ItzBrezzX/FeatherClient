package dev.kingbrezz.featherfps.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kingbrezz.featherfps.core.CompatDetector;
import dev.kingbrezz.featherfps.core.ConfigStore;
import dev.kingbrezz.featherfps.core.FeatherConfig;
import dev.kingbrezz.featherfps.core.Info;
import dev.kingbrezz.featherfps.core.LoadResult;
import dev.kingbrezz.featherfps.core.PerformanceMonitor;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UNCOMPILED, UNTESTED DRAFT for Minecraft 1.21.11 + Fabric. Written from the documented APIs only.
 * What it does: loads the config, detects other mods, registers the Right Shift keybind, opens the settings
 * screen, and samples one frame per HUD draw so the dashboard has real frame times.
 * What it does NOT do: apply any FPS limit, culling, particle or entity optimization (no mixins on purpose).
 */
public final class FeatherFpsClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Info.MOD_ID);
    private static FeatherFpsClient instance;

    private ConfigStore store;
    private FeatherConfig config;
    private PerformanceMonitor monitor;
    private CompatDetector.Report compat;
    private String minecraftVersion = "unknown";
    private KeyMapping openKey;

    static FeatherFpsClient instance() { return instance; }

    ConfigStore store() { return store; }

    FeatherConfig config() { return config; }

    PerformanceMonitor monitor() { return monitor; }

    CompatDetector.Report compat() { return compat; }

    String minecraftVersion() { return minecraftVersion; }

    @Override
    public void onInitializeClient() {
        instance = this;
        FabricLoader loader = FabricLoader.getInstance();

        store = new ConfigStore(loader.getConfigDir());
        LoadResult loaded = store.load();
        config = loaded.config();
        for (String w : loaded.warnings()) LOGGER.warn("[{}] {}", Info.NAME, w);

        monitor = new PerformanceMonitor(config);
        List<String> ids = loader.getAllMods().stream().map(m -> m.getMetadata().getId()).toList();
        compat = CompatDetector.detect(ids);
        minecraftVersion = loader.getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        LOGGER.info("[{}] loaded config; detected {} known optimization mod(s)", Info.NAME, compat.detected().size());

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Info.MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.featherfps.open", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new FeatherScreen(null));
            }
        });

        // One sample per drawn HUD frame. Limitation: nothing is sampled in the main menu or with the HUD hidden (F1).
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(Info.MOD_ID, "frame_sampler"),
                (graphics, tickCounter) -> monitor.onFrame(System.nanoTime()));
    }
}
