package dev.muon.chronicles_leveling;

import dev.muon.chronicles_leveling.attachment.NeoforgeAttachments;
import dev.muon.chronicles_leveling.item.ModItemsNeoforge;
import dev.muon.chronicles_leveling.sounds.ModSoundsNeoforge;
import dev.muon.chronicles_leveling.stat.ModStatsNeoforge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * NeoForge mod entrypoint.
 *
 * <p>Constructor binds {@link net.neoforged.neoforge.registries.DeferredRegister} sets to the mod bus before
 * common init runs — registries fire after constructors return, so deferred
 * holders must already be subscribed to the bus by that point.
 *
 * <p>{@link FMLCommonSetupEvent} is the safe place to publish stat holders
 * to common-state {@link dev.muon.chronicles_leveling.stat.ModStats} because
 * deferred registries have fired by then.
 */
@Mod(ChroniclesLeveling.MOD_ID)
public class ChroniclesLevelingNeoforge {

    public ChroniclesLevelingNeoforge(IEventBus modBus) {
        ModStatsNeoforge.REGISTRY.register(modBus);
        ModItemsNeoforge.REGISTRY.register(modBus);
        ModSoundsNeoforge.REGISTRY.register(modBus);
        NeoforgeAttachments.REGISTRY.register(modBus);

        modBus.addListener(ChroniclesLevelingNeoforge::onCommonSetup);

        ChroniclesLeveling.init();
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // Deferred registries have fired by this point — safe to publish holders to common.
        event.enqueueWork(() -> {
            ModStatsNeoforge.init();
            ModItemsNeoforge.init();
            ModSoundsNeoforge.init();
        });
    }
}
