package dev.muon.chronicles_leveling.effect;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

public final class ModEffectsFabric {

    public static final Holder<MobEffect> BLEED = register("bleed", new BleedMobEffect());
    public static final Holder<MobEffect> SUNDER = register("sunder", new SunderMobEffect());
    public static final Holder<MobEffect> PACIFIED = register("pacified", new PacifiedMobEffect());

    private ModEffectsFabric() {}

    private static Holder<MobEffect> register(String path, MobEffect effect) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ChroniclesLeveling.id(path), effect);
    }

    public static void init() {
        ModEffects.BLEED = BLEED;
        ModEffects.SUNDER = SUNDER;
        ModEffects.PACIFIED = PACIFIED;
    }
}
