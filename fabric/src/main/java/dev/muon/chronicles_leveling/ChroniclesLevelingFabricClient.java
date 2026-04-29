package dev.muon.chronicles_leveling;

import dev.muon.chronicles_leveling.client.ChroniclesKeybinds;
import dev.muon.chronicles_leveling.client.XpAffordabilityNotifier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/**
 * Client mod entrypoint. Registers the keybind and ticks the
 * keybind handler. Network reception works automatically through
 * Fabric's auto-synced attachments.
 */
public class ChroniclesLevelingFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(ChroniclesKeybinds.OPEN_STATS);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ChroniclesKeybinds.tick();
            XpAffordabilityNotifier.tick();
        });
    }
}
