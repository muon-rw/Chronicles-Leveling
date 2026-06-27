package dev.muon.chronicles_leveling.skill.ability;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.network.NetworkDispatcher;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillEffects;
import dev.muon.chronicles_leveling.skill.SkillRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server-side cast authority. Resolves an instant cast end to end, and exposes the shared gate the held-cast
 * driver reuses each tick. Server-authoritative: the client may send any id, so {@link #validated} re-derives the
 * unlocked set and {@link #evaluate} re-checks every user-facing gate against frozen-registry + synced state.
 *
 * <p>All FREE checks run before any debit; {@link #evaluate} only CHECKS (no debit) so it is reusable for per-tick
 * re-gating. {@link #resolve} debits last and stamps the cooldown BEFORE {@link SkillAbility#run}, so a throwing
 * ability is consumed once (cooldown-locked), never free-recastable. A denied cast emits a {@link
 * dev.muon.chronicles_leveling.network.message.CastFailedPacket} the client renders + reacts to.
 */
public final class AbilityCaster {

    private AbilityCaster() {}

    /** A failed gate: why, an optional ability-supplied message (UNAVAILABLE), and a detail value (cooldown seconds). */
    public record Denial(CastDenyReason reason, Component message, int detail) {}

    public static void resolve(ServerPlayer player, Identifier abilityId) {
        SkillAbility ability = validated(player, abilityId);
        if (ability == null) {
            return;
        }
        Denial denial = evaluate(player, ability);
        if (denial != null) {
            fail(player, abilityId, denial);
            return;
        }
        if (!CombatResources.trySpend(player, ability.cost())) {
            fail(player, abilityId, new Denial(costReason(player, ability.cost()), null, 0));
            return;
        }
        startCooldown(player, ability);
        try {
            ability.run(player);
        } catch (RuntimeException e) {
            ChroniclesLeveling.LOG.error("Ability '{}' threw during run()", abilityId, e);
        }
    }

    /** The registered ability if it exists AND the player has unlocked it, else null (both are silently ignored: the client never offers either). */
    public static SkillAbility validated(ServerPlayer player, Identifier abilityId) {
        SkillAbility ability = SkillRegistry.ability(abilityId);
        if (ability == null) {
            ChroniclesLeveling.LOG.debug("Cast rejected: unknown ability '{}'", abilityId);
            return null;
        }
        if (!SkillEffects.hasAbility(player, abilityId)) {
            return null;
        }
        return ability;
    }

    /** The user-facing gates (cooldown, canActivate, resource affordability); null if all pass. Pure check, no debit. */
    public static Denial evaluate(ServerPlayer player, SkillAbility ability) {
        long now = player.level().getGameTime();
        long cooldownEnd = PlayerSkillManager.get(player).abilityCooldownEnd(ability.id().toString());
        if (cooldownEnd > now) {
            return new Denial(CastDenyReason.COOLDOWN, null, (int) Math.ceil((cooldownEnd - now) / 20.0));
        }
        if (!ability.canActivate(player)) {
            return new Denial(CastDenyReason.UNAVAILABLE, ability.activationError(player), 0);
        }
        if (!CombatResources.canAfford(player, ability.cost())) {
            return new Denial(costReason(player, ability.cost()), null, 0);
        }
        return null;
    }

    /** Stamps the cooldown from now (clamped to >= 1 tick so a zero-cooldown ability still can't fire twice in a tick). */
    public static void startCooldown(ServerPlayer player, SkillAbility ability) {
        long now = player.level().getGameTime();
        PlayerSkillManager.setAbilityCooldown(player, ability.id(), now + Math.max(1, ability.baseCooldownTicks()));
    }

    /** Sends the structured denial to the casting client (it renders the message + plays a local reaction). */
    public static void fail(ServerPlayer player, Identifier abilityId, Denial denial) {
        NetworkDispatcher.sendCastFailed(player, abilityId, denial);
    }

    /** Whichever resource the player is short on (stamina checked first, matching {@code CombatResources.trySpend}). */
    public static CastDenyReason costReason(ServerPlayer player, AbilityCost cost) {
        if (cost.stamina() > 0f && CombatResources.getStamina(player) < cost.stamina()) {
            return CastDenyReason.NEED_STAMINA;
        }
        return CastDenyReason.NEED_MANA;
    }
}
