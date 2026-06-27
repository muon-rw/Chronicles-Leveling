package dev.muon.chronicles_leveling;

import dev.muon.chronicles_leveling.attachment.NeoforgeAttachments;
import dev.muon.chronicles_leveling.component.ModComponentsNeoforge;
import dev.muon.chronicles_leveling.effect.ModEffectsNeoforge;
import dev.muon.chronicles_leveling.item.ModItemsNeoforge;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.SkillBootstrap;
import dev.muon.chronicles_leveling.sounds.ModSoundsNeoforge;
import dev.muon.chronicles_leveling.stat.ModStatsNeoforge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * The constructor must bind DeferredRegister sets to the mod bus before common init; registries
 * fire after constructors return, so holders must already be subscribed by that point.
 *
 * <p>{@link FMLCommonSetupEvent} is the safe place to publish stat holders to common and to freeze
 * the skill registry: every mod constructor, including dependent addons', has run by then, so
 * {@code RegisterSkillContributionsEvent} reaches every addon listener before the freeze.
 */
@Mod(ChroniclesLeveling.MOD_ID)
public class ChroniclesLevelingNeoforge {

    private static IEventBus modBus;

    public ChroniclesLevelingNeoforge(IEventBus modBus) {
        ChroniclesLevelingNeoforge.modBus = modBus;
        ModStatsNeoforge.REGISTRY.register(modBus);
        ModItemsNeoforge.REGISTRY.register(modBus);
        ModSoundsNeoforge.REGISTRY.register(modBus);
        ModEffectsNeoforge.REGISTRY.register(modBus);
        ModComponentsNeoforge.REGISTRY.register(modBus);
        NeoforgeAttachments.REGISTRY.register(modBus);

        modBus.addListener(ChroniclesLevelingNeoforge::onCommonSetup);
        modBus.addListener(ModItemsNeoforge::onModifyDefaultComponents);

        ChroniclesLeveling.init();
    }

    /** The platform helper uses this bus to post {@code RegisterSkillContributionsEvent}. */
    public static IEventBus modBus() {
        return modBus;
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModStatsNeoforge.init();
            ModItemsNeoforge.init();
            ModComponentsNeoforge.init();
            ModSoundsNeoforge.init();
            ModEffectsNeoforge.init();

            SkillBootstrap.registerAndFreeze(Services.PLATFORM.collectSkillContributors());
        });
    }
}
