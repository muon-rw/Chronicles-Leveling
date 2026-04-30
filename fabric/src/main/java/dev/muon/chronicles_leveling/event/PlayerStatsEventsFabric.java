package dev.muon.chronicles_leveling.event;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.stat.StatModifierApplier;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric-side player lifecycle hooks for stat allocation.
 *
 * <p>Two things to wire on this loader:
 * <ol>
 *   <li>On login, grant the configured starting points if this is a new
 *       profile, then recompute stat modifiers (vanilla persists allocations
 *       in the {@code AttributeInstance} base value, but the secondary
 *       modifiers we drive from them are not persisted — we re-derive on
 *       login + respawn).</li>
 *   <li>On respawn, re-derive secondary modifiers because the new player
 *       starts with a fresh attribute map.</li>
 * </ol>
 *
 * <p>Player-attribute attachment itself is handled by {@code PlayerAttributesMixin}.
 * Skill-XP routing lives in {@link SkillXpEventsFabric}.
 */
public final class PlayerStatsEventsFabric {

    private PlayerStatsEventsFabric() {}

    public static void initLifecycle() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            grantStartingPointsIfNew(player);
            StatModifierApplier.recompute(player);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                StatModifierApplier.recompute(newPlayer)
        );
    }

    private static void grantStartingPointsIfNew(ServerPlayer player) {
        var store = Services.PLATFORM.getPlayerLevelStore();
        if (store.has(player)) return;
        int starting = Configs.STATS.startingPoints.get();
        PlayerLevelManager.set(player, PlayerLevelData.DEFAULT.withUnspentPoints(starting));
    }
}
