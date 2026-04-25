package dev.muon.chronicles_leveling.event;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.stat.StatModifierApplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * NeoForge-side player lifecycle hooks for leveling. Mirrors the Fabric file.
 *
 * <p>Listens on the GAME bus (vs. MOD bus) since these are per-player events
 * fired during runtime, not during mod init.
 */
@EventBusSubscriber(modid = ChroniclesLeveling.MOD_ID)
public final class PlayerStatsEventsNeoforge {

    private PlayerStatsEventsNeoforge() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        grantStartingPointsIfNew(player);
        StatModifierApplier.recompute(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        StatModifierApplier.recompute(player);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        StatModifierApplier.recompute(player);
    }

    private static void grantStartingPointsIfNew(ServerPlayer player) {
        var store = Services.PLATFORM.getPlayerLevelStore();
        if (store.has(player)) return;
        int starting = Configs.SYNC.startingPoints.get();
        PlayerLevelManager.set(player, PlayerLevelData.DEFAULT.withUnspentPoints(starting));
    }
}
