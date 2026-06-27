package dev.muon.chronicles_leveling.skill.ability;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.combat_attributes.resource.PlayerResources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The one owned seam to Combat-Attributes' stamina/mana pools. Gated by {@code isModLoaded} with the
 * concrete CA calls isolated in a static-nested {@link Impl} so the JVM does not link
 * {@link PlayerResources} until first use behind the gate: no {@code NoClassDefFoundError} when CA is absent.
 *
 * <p>CA's own {@code trySpendStamina}/{@code trySpendMana} only check {@code > 0} then debit a clamped
 * amount (its Souls-like "spend what you have" semantics), so they do NOT gate full affordability. This
 * seam adds the explicit {@code >= cost} pre-check, making actives all-or-nothing (you need the full cost
 * to fire): the right feel for discrete abilities, and a conscious divergence from CA's native behavior.
 *
 * <p>Degradation: when CA is absent, {@link #trySpend} returns {@code true} (costs skipped, abilities still
 * fire and still cooldown-gate) and the getters return a defined 0. CA is the hard expectation; its absence
 * is a dev/edge case where free-but-functional beats disabled. Flip the {@code !CA} branch to disable.
 */
public final class CombatResources {

    private static final boolean CA = Services.PLATFORM.isModLoaded("combat_attributes");

    static {
        if (!CA) {
            ChroniclesLeveling.LOG.warn("Combat-Attributes not present; ability resource costs are disabled."
                    + " Abilities still fire and cooldown-gate, but cost nothing.");
        }
    }

    private CombatResources() {}

    /** Whether CA is present; the HUD checks this before drawing any resource-readiness tint. */
    public static boolean isActive() {
        return CA;
    }

    /** Atomically debits the cost if the player can afford ALL of it; true if spent (or CA absent). */
    public static boolean trySpend(ServerPlayer player, AbilityCost cost) {
        return !CA || Impl.trySpend(player, cost);
    }

    /** Whether the player can afford the full cost right now, without debiting; true if affordable (or CA absent). */
    public static boolean canAfford(ServerPlayer player, AbilityCost cost) {
        return !CA || Impl.canAfford(player, cost);
    }

    /** Debits up to {@code amount} stamina from a target (CA's spend-what-you-have semantics); no-op without CA. */
    public static void drainStamina(ServerPlayer target, float amount) {
        if (CA && amount > 0f) {
            Impl.drainStamina(target, amount);
        }
    }

    /** Player-typed (not just ServerPlayer) so the client HUD can read the owning player's pool. CA syncs it. */
    public static float getStamina(Player player) {
        return CA ? Impl.getStamina(player) : 0f;
    }

    public static float getMana(Player player) {
        return CA ? Impl.getMana(player) : 0f;
    }

    /** Not linked until first call behind the {@link #CA} gate. */
    private static final class Impl {
        private Impl() {}

        static boolean canAfford(ServerPlayer player, AbilityCost cost) {
            // Explicit full-cost gate CA does not provide; <= 0 legs are trivially affordable.
            return PlayerResources.getStamina(player) >= cost.stamina()
                    && PlayerResources.getMana(player) >= cost.mana();
        }

        static boolean trySpend(ServerPlayer player, AbilityCost cost) {
            if (!canAfford(player, cost)) {
                return false;
            }
            // Both affordable on the single server thread within this tick; debit (cost <= 0 is a CA no-op).
            PlayerResources.trySpendStamina(player, cost.stamina());
            PlayerResources.trySpendMana(player, cost.mana());
            return true;
        }

        static void drainStamina(ServerPlayer target, float amount) {
            PlayerResources.trySpendStamina(target, amount);
        }

        static float getStamina(Player player) {
            return PlayerResources.getStamina(player);
        }

        static float getMana(Player player) {
            return PlayerResources.getMana(player);
        }
    }
}
