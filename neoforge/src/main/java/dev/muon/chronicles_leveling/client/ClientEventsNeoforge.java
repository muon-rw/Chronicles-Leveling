package dev.muon.chronicles_leveling.client;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

/**
 * NeoForge client-side wiring. The newer NeoForge {@code EventBusSubscriber}
 * has no explicit {@code bus} field — the dispatcher routes by event type, so
 * mod-bus events ({@code RegisterKeyMappingsEvent}) and game-bus events
 * ({@code ClientTickEvent.Post}) can sit in one class.
 */
@EventBusSubscriber(modid = ChroniclesLeveling.MOD_ID, value = Dist.CLIENT)
public final class ClientEventsNeoforge {

    private ClientEventsNeoforge() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ChroniclesKeybinds.OPEN_STATS);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ChroniclesKeybinds.tick();
        XpAffordabilityNotifier.tick();
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent.CanRender event) {
        if (event.getEntity() instanceof Player player) {
            event.setContent(PlayerNameplateRenderer.decorate(event.getContent(), player));
        }
    }
}
