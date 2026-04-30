package dev.muon.chronicles_leveling.skill.xp;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-{@code BrewingStandBlockEntity} state for the alchemy XP grant.
 *
 * <p>Only the freshly-brewed flag matters across save/load: a 3-bit mask
 * (slot 0 = bit 0, etc.) that survives chunk unloads, server restarts, and
 * the brewer logging out before they take the potion.
 *
 * <p>Mirrors the shape of {@code PlayerSkillData} and {@code PlayerLevelData}
 * — record + Codec — so the loader-specific {@code AttachmentType} entries
 * register identically.
 */
public record BrewingStationData(byte freshlyBrewedMask) {

    public static final BrewingStationData DEFAULT = new BrewingStationData((byte) 0);

    public static final Codec<BrewingStationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BYTE.fieldOf("freshly_brewed_mask").forGetter(BrewingStationData::freshlyBrewedMask)
    ).apply(instance, BrewingStationData::new));

    /** True iff slot 0/1/2 is currently flagged as freshly brewed. */
    public boolean isFreshlyBrewed(int slot) {
        if (slot < 0 || slot > 2) return false;
        return ((freshlyBrewedMask >> slot) & 1) != 0;
    }

    /** Returns a new instance with the slot's flag set/cleared; identity-returns when unchanged. */
    public BrewingStationData with(int slot, boolean fresh) {
        if (slot < 0 || slot > 2) return this;
        byte bit = (byte) (1 << slot);
        byte updated = fresh ? (byte) (freshlyBrewedMask | bit) : (byte) (freshlyBrewedMask & ~bit);
        return updated == freshlyBrewedMask ? this : new BrewingStationData(updated);
    }
}
