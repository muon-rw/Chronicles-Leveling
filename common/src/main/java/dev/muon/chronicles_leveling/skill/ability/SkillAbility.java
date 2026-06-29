package dev.muon.chronicles_leveling.skill.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * An active, server-authoritative ability. Implementations are stateless singletons
 * (registered once, shared across all players); never hold per-player state on an
 * ability. Cooldowns live on the player attachment, timed windows in the ability
 * runtime.
 *
 * <p>The cast pipeline orchestrates the gate sequence (unlock check, cooldown,
 * {@link #canActivate}, then the Combat-Attributes resource cost) and only then calls
 * {@link #run}. Concrete abilities normally extend {@link AbstractAbility} and
 * implement just {@link #run}.
 */
public interface SkillAbility {

    Identifier id();

    /** HUD/tooltip icon: {@code textures/gui/ability/<name>.png}, derived from {@link #id()}. */
    default Identifier icon() {
        return Identifier.fromNamespaceAndPath(id().getNamespace(), "textures/gui/" + id().getPath() + ".png");
    }

    /** The skill this ability belongs to (a {@code Skills} id). */
    String owningSkill();

    int baseCooldownTicks();

    AbilityCost cost();

    /** Effect/window duration in ticks the tooltip surfaces; 0 means instantaneous (no duration shown). */
    default int durationTicks() {
        return 0;
    }

    /**
     * Extra activation gating beyond unlock + cooldown + resources (e.g. the right tool
     * in hand, a valid target). Must be side-effect free. Defaults to always allowed.
     */
    default boolean canActivate(ServerPlayer player) {
        return true;
    }

    /**
     * The action-bar reason shown when {@link #canActivate} blocks a cast (e.g. "hold a reagent"). {@code null}
     * falls back to a generic message. Re-evaluated when canActivate fails, so it can pick among failure modes.
     */
    default Component activationError(ServerPlayer player) {
        return null;
    }

    /** Performs the ability. Called only after every gate has passed. */
    void run(ServerPlayer player);

    // --- Held casts (charge / channel). INSTANT abilities ignore all of the below. ---

    /** How this ability is cast; {@link CastMode#INSTANT} (the default) uses the single-press path. */
    default CastMode castMode() {
        return CastMode.INSTANT;
    }

    /** CHARGE: ticks of holding that reach full charge (fraction 1.0). Hold caps here; longer does not over-charge. Keep well under the driver's safety cap, or a {@link #requiresFullCharge} ability can never reach full charge. */
    default int chargeTicks() {
        return 0;
    }

    /** CHARGE: if true, releasing below full charge fizzles (no fire, no cost, no cooldown); if false, the effect scales. */
    default boolean requiresFullCharge() {
        return false;
    }

    /** CHARGE: fires once on release. {@code fraction} is the charge level reached (0..1). Defaults to a plain {@link #run}. */
    default void runCharged(ServerPlayer player, float fraction) {
        run(player);
    }

    /** CHANNEL: max ticks the channel can sustain before it auto-ends; 0 = until the player releases (or a gate fails). */
    default int maxChannelTicks() {
        return 0;
    }

    /** CHANNEL: re-debit {@link #cost()} every N ticks while channeling; 0 = pay once up front, then sustain free. */
    default int channelCostIntervalTicks() {
        return 0;
    }

    /** CHANNEL: called once when the channel begins (after the start gate passes). */
    default void channelStart(ServerPlayer player) {
    }

    /** CHANNEL: called every tick while channeling. {@code elapsedTicks} counts from the start (first tick = 1). */
    default void channelTick(ServerPlayer player, int elapsedTicks) {
    }

    /** CHANNEL: called once when the channel stops, whether by release/max-duration ({@code interrupted=false}) or a failed gate ({@code interrupted=true}). */
    default void channelEnd(ServerPlayer player, int elapsedTicks, boolean interrupted) {
    }
}
