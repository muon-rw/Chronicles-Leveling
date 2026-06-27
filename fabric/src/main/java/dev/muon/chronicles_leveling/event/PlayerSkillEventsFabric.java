package dev.muon.chronicles_leveling.event;

import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.SkillModifierApplier;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import dev.muon.chronicles_leveling.skill.ability.runtime.HeldCastDriver;
import dev.muon.chronicles_leveling.skill.combat.CombatProcRouter;
import dev.muon.chronicles_leveling.skill.combat.QuickBladeHandler;
import dev.muon.chronicles_leveling.skill.enchant.EssenceHoarderHandler;
import dev.muon.chronicles_leveling.skill.enchant.WizardsStudyHandler;
import dev.muon.chronicles_leveling.skill.social.WanderingEyeSpawns;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Fabric-side player lifecycle hooks for the skill-ability layer, the skill-keyed twin
 * of {@link PlayerStatsEventsFabric}. Kept a separate class because skill recompute is
 * independent of stat recompute: skill modifiers never target the stat attributes
 * (validated at registry freeze), so the two appliers observe the same events without coupling.
 */
public final class PlayerSkillEventsFabric {

    private PlayerSkillEventsFabric() {}

    public static void initLifecycle() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            PlayerSkillManager.reconcile(player);
            SkillModifierApplier.recompute(player);
            PlayerSkillManager.reconcileAbilityBindings(player);
            WizardsStudyHandler.syncTarget(player);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            SkillModifierApplier.recompute(newPlayer);
            WizardsStudyHandler.syncTarget(newPlayer);
            // The dynamic-modifier snapshots refer to the dead entity, whose transient attribute modifiers don't
            // survive respawn; drop them so the next equipment event / tick re-derives onto the new entity.
            EssenceHoarderHandler.clear(newPlayer.getUUID());
            QuickBladeHandler.clear(newPlayer.getUUID());
            HeldCastDriver.clear(newPlayer.getUUID());   // an immediate respawn rebuilds the same-UUID player alive before the tick death-guard sees it
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            SkillEffects.clear(id);
            AbilityWindowStore.clear(id);   // drop transient ability windows (the tick driver only sees online players)
            HeldCastDriver.clear(id);   // drop any in-progress held cast
            CombatProcRouter.clear(id);   // drop the Momentum streak
            EssenceHoarderHandler.clear(id);   // drop the dynamic-modifier snapshot
            QuickBladeHandler.clear(id);
        });

        ServerTickEvents.END_SERVER_TICK.register(AbilityWindowStore::tick);
        ServerTickEvents.END_SERVER_TICK.register(HeldCastDriver::tick);
        ServerTickEvents.END_SERVER_TICK.register(EssenceHoarderHandler::tick);
        ServerTickEvents.END_SERVER_TICK.register(QuickBladeHandler::tick);
        ServerTickEvents.END_SERVER_TICK.register(WanderingEyeSpawns::tick);
    }
}
