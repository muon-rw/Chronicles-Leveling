package dev.muon.chronicles_leveling.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A per-category amplifier add carried on a brewed potion stack. The stored bottle keeps its base potion
 * ({@code POTION_CONTENTS} untouched), so recipes, brewing chains, and {@code is(Potions.X)} all still recognize it;
 * the amplifier is materialized into a TRANSIENT boosted {@link PotionContents} at each delivery point (drink, splash,
 * lingering cloud, arrow, tooltip) via {@link #boosted}, never written back to the item. Duration boosts ride the
 * separate vanilla {@code POTION_DURATION_SCALE}, so {@code boosted} leaves durations untouched.
 */
public record BrewPotency(int beneficial, int harmful, int neutral) {

    public static final BrewPotency NONE = new BrewPotency(0, 0, 0);

    public static final Codec<BrewPotency> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("beneficial", 0).forGetter(BrewPotency::beneficial),
            Codec.INT.optionalFieldOf("harmful", 0).forGetter(BrewPotency::harmful),
            Codec.INT.optionalFieldOf("neutral", 0).forGetter(BrewPotency::neutral)
    ).apply(i, BrewPotency::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BrewPotency> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, BrewPotency::beneficial,
            ByteBufCodecs.INT, BrewPotency::harmful,
            ByteBufCodecs.INT, BrewPotency::neutral,
            BrewPotency::new);

    public boolean isEmpty() {
        return beneficial == 0 && harmful == 0 && neutral == 0;
    }

    /** A transient copy of {@code base} with each effect's amplifier raised by its category add; durations untouched. */
    public static PotionContents boosted(PotionContents base, BrewPotency potency) {
        if (potency == null || potency.isEmpty()) {
            return base;
        }
        return boosted(base, potency.beneficial(), potency.harmful(), potency.neutral(), 1.0);
    }

    /**
     * A transient copy of {@code base} with per-category amplifier adds, plus a harmful-only duration multiplier (for
     * Toxicology; 1.0 = none). Beneficial and neutral durations and instant/infinite effects are untouched, so the
     * separate {@code POTION_DURATION_SCALE} still scales every effect on top. Never stored back onto an item.
     */
    public static PotionContents boosted(PotionContents base, int beneficialAmp, int harmfulAmp, int neutralAmp,
            double harmfulDurationMult) {
        if (beneficialAmp == 0 && harmfulAmp == 0 && neutralAmp == 0 && harmfulDurationMult == 1.0) {
            return base;
        }
        List<MobEffectInstance> out = new ArrayList<>();
        for (MobEffectInstance effect : base.getAllEffects()) {
            MobEffectCategory category = effect.getEffect().value().getCategory();
            int add = switch (category) {
                case BENEFICIAL -> beneficialAmp;
                case HARMFUL -> harmfulAmp;
                case NEUTRAL -> neutralAmp;
            };
            int amplifier = Math.min(MobEffectInstance.MAX_AMPLIFIER, effect.getAmplifier() + add);
            int duration = effect.getDuration();
            if (category == MobEffectCategory.HARMFUL && harmfulDurationMult != 1.0
                    && !effect.getEffect().value().isInstantenous() && !effect.isInfiniteDuration()) {
                duration = Math.max(1, (int) (duration * harmfulDurationMult));
            }
            out.add(new MobEffectInstance(effect.getEffect(), duration, amplifier,
                    effect.isAmbient(), effect.isVisible(), effect.showIcon()));
        }
        if (out.isEmpty()) {
            return base;
        }
        String name = base.potion().map(holder -> holder.value().name()).orElse(base.customName().orElse(null));
        return new PotionContents(Optional.empty(), base.customColor(), out, Optional.ofNullable(name));
    }
}
