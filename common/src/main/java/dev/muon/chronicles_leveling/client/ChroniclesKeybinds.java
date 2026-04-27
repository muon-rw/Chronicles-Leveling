package dev.muon.chronicles_leveling.client;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.client.screen.ChroniclesTab;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Client keybinding declarations.
 *
 * <p>The {@link KeyMapping} instance is created here but loader code is
 * responsible for registering it (NeoForge: {@code RegisterKeyMappingsEvent},
 * Fabric: {@code KeyMappingHelper#registerKeyMapping}). We expose
 * {@link #tick()} as the place that consumes queued presses.
 *
 * <p>Default key {@code G} matches PlayerEx's main panel, so anyone migrating
 * from PlayerEx finds the screen on the same key.
 */
public final class ChroniclesKeybinds {

    private static final String CATEGORY = "key.categories.chronicles_leveling";

    public static final KeyMapping OPEN_STATS = new KeyMapping(
            "key.chronicles_leveling.open_stats",
            GLFW.GLFW_KEY_G,
            new KeyMapping.Category(ChroniclesLeveling.id(CATEGORY))
    );

    private ChroniclesKeybinds() {}

    /** Loader code calls this from a per-tick hook. Drains queued presses. */
    public static void tick() {
        while (OPEN_STATS.consumeClick()) {
            ChroniclesTab.LEVELS.open();
        }
    }
}
