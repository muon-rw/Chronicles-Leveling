package dev.muon.chronicles_leveling.effect;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

/**
 * Custom {@link MobEffect} holders, populated by loader-specific code (NeoForge {@code DeferredRegister},
 * Fabric {@code Registry.registerForHolder}) and published here in {@code init()}.
 *
 * <p>Registration matters: {@code CombatProcRouter} applies these via {@code MobEffectInstance} on the
 * server, which resolves the holder against {@code BuiltInRegistries.MOB_EFFECT}. Effect icons live at
 * {@code assets/chronicles_leveling/textures/mob_effect/&lt;name&gt;.png} (see skills-ui-todo.md).
 */
public final class ModEffects {

    private ModEffects() {}

    /** Weaponry "Rend": stacking bleed DoT (amplifier = stacks). */
    public static Holder<MobEffect> BLEED;

    /** Weaponry "Sunder": timed armor-shred debuff. */
    public static Holder<MobEffect> SUNDER;

    /** Speech "Pacify": marker that denies a mob's targeting checks while held. */
    public static Holder<MobEffect> PACIFIED;
}
