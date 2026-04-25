package dev.muon.chronicles_leveling.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

/**
 * The three tabs that ride along the top of the player's "main" UI cluster:
 * Inventory, Stats, Attributes. Based off of PlayerEx, licensed MIT
 *
 * <p>Each tab carries its own 16×16 icon (from PlayerEx's icon set). Stats
 * uses the {@code combat.png} icon
 *
 * <p>Switching is just opening the target {@link Screen} on the client — no
 * round-trip needed.
 */
public enum ChroniclesTab {

    INVENTORY("inventory", InventoryScreen.class, ChroniclesTextures.ICON_INVENTORY, () -> {
        var mc = Minecraft.getInstance();
        return mc.player == null ? null : new InventoryScreen(mc.player);
    }),
    STATS("stats", LevelUpScreen.class, ChroniclesTextures.ICON_COMBAT, LevelUpScreen::new),
    ATTRIBUTES("attributes", AttributesScreen.class, ChroniclesTextures.ICON_ATTRIBUTES, AttributesScreen::new);

    private final String key;
    private final Class<? extends Screen> screenClass;
    private final Identifier icon;
    private final Supplier<Screen> screenFactory;

    ChroniclesTab(String key, Class<? extends Screen> screenClass, Identifier icon, Supplier<Screen> screenFactory) {
        this.key = key;
        this.screenClass = screenClass;
        this.icon = icon;
        this.screenFactory = screenFactory;
    }

    public Component title() {
        return Component.translatable("chronicles_leveling.tab." + key);
    }

    public Identifier icon() {
        return icon;
    }

    /** Whether this tab's screen is the active screen on the client. */
    public boolean isActive(Screen current) {
        return screenClass.isInstance(current);
    }

    /** Open the target screen. No-op if it's already active. */
    public void open() {
        Minecraft mc = Minecraft.getInstance();
        if (isActive(mc.screen)) return;
        Screen next = screenFactory.get();
        if (next != null) mc.setScreen(next);
    }
}
