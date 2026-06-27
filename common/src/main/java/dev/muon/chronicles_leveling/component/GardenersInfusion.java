package dev.muon.chronicles_leveling.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Marker stamped on food that Gardener's Infusion enriched at craft/cook time. The boosted hunger/saturation, and at
 * tier 3 the doubled positive-effect durations, are baked directly into the stack's vanilla FOOD/CONSUMABLE
 * components, so vanilla hunger, Combat-Attributes Legacy Hunger, and AppleSkin all read them natively. This marker
 * only records the tier for the tooltip and to keep a re-craft from re-baking an already-infused stack.
 */
public record GardenersInfusion(int tier) {

    public static final Codec<GardenersInfusion> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("tier").forGetter(GardenersInfusion::tier)
    ).apply(i, GardenersInfusion::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GardenersInfusion> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, GardenersInfusion::tier,
            GardenersInfusion::new);
}
