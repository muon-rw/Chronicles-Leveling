package dev.muon.chronicles_leveling.effect;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffectsNeoforge {

    public static final DeferredRegister<MobEffect> REGISTRY =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, ChroniclesLeveling.MOD_ID);

    public static final DeferredHolder<MobEffect, BleedMobEffect> BLEED =
            REGISTRY.register("bleed", BleedMobEffect::new);
    public static final DeferredHolder<MobEffect, SunderMobEffect> SUNDER =
            REGISTRY.register("sunder", SunderMobEffect::new);
    public static final DeferredHolder<MobEffect, PacifiedMobEffect> PACIFIED =
            REGISTRY.register("pacified", PacifiedMobEffect::new);

    private ModEffectsNeoforge() {}

    /** Call after deferred registries fire. */
    public static void init() {
        ModEffects.BLEED = BLEED;
        ModEffects.SUNDER = SUNDER;
        ModEffects.PACIFIED = PACIFIED;
    }
}
