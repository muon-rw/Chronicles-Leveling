package dev.muon.chronicles_leveling.skill.ability.runtime;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.skill.ability.AbilityCaster;
import dev.muon.chronicles_leveling.skill.ability.CastDenyReason;
import dev.muon.chronicles_leveling.skill.ability.CastMode;
import dev.muon.chronicles_leveling.skill.ability.CombatResources;
import dev.muon.chronicles_leveling.skill.ability.SkillAbility;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-only driver for held casts (charge / channel). A player has at most one active held cast; the
 * {@link #tick} driver (run from the server-tick seam) re-gates a channel each tick and interrupts it on failure.
 * Cooldown starts when the cast ENDS (release / max-duration / interrupt), never during the hold. A charge spends
 * its cost on release; a channel spends up front and again every {@code channelCostIntervalTicks}.
 *
 * <p>Lost-release safety: a held cast is hard-capped at {@link #SAFETY_MAX_TICKS} (a charge auto-fires, a channel
 * auto-ends) so a dropped release or vanished client cannot leave a cast running forever; logout clears it outright.
 */
public final class HeldCastDriver {

    private HeldCastDriver() {}

    /** Hard cap on any held cast (lost-release / vanished-client backstop). A {@code requiresFullCharge} ability whose {@code chargeTicks} exceeds this can never reach full charge, so keep charge times well under it. */
    private static final int SAFETY_MAX_TICKS = 600;

    private static final class Held {
        final SkillAbility ability;
        final long startTick;
        long nextCostTick;

        Held(SkillAbility ability, long startTick, long nextCostTick) {
            this.ability = ability;
            this.startTick = startTick;
            this.nextCostTick = nextCostTick;
        }
    }

    private static final Map<UUID, Held> ACTIVE = new HashMap<>();

    /** Begins a held cast: gates it, pays the entry cost (channel) and records it, or denies. Ignores INSTANT ids. */
    public static void start(ServerPlayer player, Identifier abilityId) {
        SkillAbility ability = AbilityCaster.validated(player, abilityId);
        if (ability == null || ability.castMode() == CastMode.INSTANT || !player.isAlive()) {
            return;
        }
        if (ACTIVE.containsKey(player.getUUID())) {
            return;   // one held cast at a time; ignore an overlapping start
        }
        AbilityCaster.Denial denial = AbilityCaster.evaluate(player, ability);
        if (denial != null) {
            AbilityCaster.fail(player, abilityId, denial);
            return;
        }
        long now = player.level().getGameTime();
        long nextCostTick = Long.MAX_VALUE;
        if (ability.castMode() == CastMode.CHANNEL) {
            // Channel pays the entry cost now; charge pays on release.
            if (!CombatResources.trySpend(player, ability.cost())) {
                AbilityCaster.fail(player, abilityId, new AbilityCaster.Denial(AbilityCaster.costReason(player, ability.cost()), null, 0));
                return;
            }
            int interval = ability.channelCostIntervalTicks();
            nextCostTick = interval > 0 ? now + interval : Long.MAX_VALUE;
            try {
                ability.channelStart(player);
            } catch (RuntimeException e) {
                ChroniclesLeveling.LOG.error("Ability '{}' threw during channelStart()", abilityId, e);
            }
        }
        ACTIVE.put(player.getUUID(), new Held(ability, now, nextCostTick));
    }

    /** Ends a held cast: a charge fires (scaled by hold), a channel stops. Idempotent: a no-op if none / a different id is active. */
    public static void release(ServerPlayer player, Identifier abilityId) {
        Held held = ACTIVE.get(player.getUUID());
        if (held == null || !held.ability.id().equals(abilityId)) {
            return;
        }
        ACTIVE.remove(player.getUUID());
        finish(player, held);
    }

    /** Drops a player's in-progress cast without firing it (logout / death); no fire, no cooldown. */
    public static void clear(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Held held = ACTIVE.get(player.getUUID());
            if (held == null) {
                continue;
            }
            if (!player.isAlive()) {
                ACTIVE.remove(player.getUUID());   // death drops the cast without firing (still in the list pre-respawn)
                continue;
            }
            long now = player.level().getGameTime();
            int elapsed = (int) (now - held.startTick) + 1;   // 1-based: start() and the first tick() share a gametime

            if (!held.ability.canActivate(player)) {
                interrupt(player, held, elapsed, CastDenyReason.UNAVAILABLE);
                continue;
            }
            if (held.ability.castMode() == CastMode.CHANNEL) {
                if (now >= held.nextCostTick) {
                    if (!CombatResources.trySpend(player, held.ability.cost())) {
                        interrupt(player, held, elapsed, AbilityCaster.costReason(player, held.ability.cost()));
                        continue;
                    }
                    held.nextCostTick = now + held.ability.channelCostIntervalTicks();
                }
                try {
                    held.ability.channelTick(player, elapsed);
                } catch (RuntimeException e) {
                    ChroniclesLeveling.LOG.error("Ability '{}' threw during channelTick()", held.ability.id(), e);
                }
            }

            int max = held.ability.castMode() == CastMode.CHANNEL ? held.ability.maxChannelTicks() : 0;
            int cap = max > 0 ? Math.min(max, SAFETY_MAX_TICKS) : SAFETY_MAX_TICKS;
            if (elapsed >= cap) {
                ACTIVE.remove(player.getUUID());
                finish(player, held);
            }
        }
        ACTIVE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    /** Completes a cast normally (release / max-duration): charge fires scaled, channel ends; cooldown then starts. */
    private static void finish(ServerPlayer player, Held held) {
        if (!player.isAlive()) {
            return;   // released the same tick as death: drop without firing (the tick() death guard covers the cap path)
        }
        SkillAbility ability = held.ability;
        int elapsed = (int) (player.level().getGameTime() - held.startTick) + 1;
        if (ability.castMode() == CastMode.CHARGE) {
            float fraction = ability.chargeTicks() > 0 ? Math.min(1f, elapsed / (float) ability.chargeTicks()) : 1f;
            if (ability.requiresFullCharge() && fraction < 1f) {
                AbilityCaster.fail(player, ability.id(), new AbilityCaster.Denial(CastDenyReason.INTERRUPTED, null, 0));
                return;   // fizzle: no fire, no cost, no cooldown
            }
            if (!CombatResources.trySpend(player, ability.cost())) {
                AbilityCaster.fail(player, ability.id(), new AbilityCaster.Denial(AbilityCaster.costReason(player, ability.cost()), null, 0));
                return;
            }
            AbilityCaster.startCooldown(player, ability);
            try {
                ability.runCharged(player, fraction);
            } catch (RuntimeException e) {
                ChroniclesLeveling.LOG.error("Ability '{}' threw during runCharged()", ability.id(), e);
            }
        } else {
            AbilityCaster.startCooldown(player, ability);
            try {
                ability.channelEnd(player, elapsed, false);
            } catch (RuntimeException e) {
                ChroniclesLeveling.LOG.error("Ability '{}' threw during channelEnd()", ability.id(), e);
            }
        }
    }

    /** Cuts a channel short on a failed re-gate: ends it (interrupted), starts cooldown, and tells the client why. */
    private static void interrupt(ServerPlayer player, Held held, int elapsed, CastDenyReason reason) {
        ACTIVE.remove(player.getUUID());
        AbilityCaster.startCooldown(player, held.ability);
        try {
            held.ability.channelEnd(player, elapsed, true);
        } catch (RuntimeException e) {
            ChroniclesLeveling.LOG.error("Ability '{}' threw during channelEnd() on interrupt", held.ability.id(), e);
        }
        AbilityCaster.fail(player, held.ability.id(), new AbilityCaster.Denial(reason, null, 0));
    }
}
