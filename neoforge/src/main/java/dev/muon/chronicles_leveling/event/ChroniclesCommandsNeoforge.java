package dev.muon.chronicles_leveling.event;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.command.ChroniclesCommands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * NeoForge command registration. Fabric uses {@code CommandRegistrationCallback}
 * directly from the entrypoint; on NeoForge we hang off the GAME-bus
 * {@link RegisterCommandsEvent}, which fires after the dispatcher exists.
 */
@EventBusSubscriber(modid = ChroniclesLeveling.MOD_ID)
public final class ChroniclesCommandsNeoforge {

    private ChroniclesCommandsNeoforge() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ChroniclesCommands.register(event.getDispatcher());
    }
}
