package dev.muon.chronicles_leveling.skill.gather;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * Per-{@code AbstractFurnaceBlockEntity} cook attribution for Gardener's Infusion: the player who most recently loaded
 * the furnace's input via its menu (the cook). The smelt tick is a static, playerless loop, so the boost is credited
 * from this owner when the result is assembled. Stored as a BE attachment so it persists through chunk unloads /
 * restarts, mirroring {@code BrewingStationData}'s owner.
 */
public record FurnaceOwnerData(Optional<UUID> owner) {

    public static final FurnaceOwnerData DEFAULT = new FurnaceOwnerData(Optional.empty());

    public static final Codec<FurnaceOwnerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(FurnaceOwnerData::owner)
    ).apply(instance, FurnaceOwnerData::new));

    public UUID ownerOrNull() {
        return owner.orElse(null);
    }

    /** A copy with the owning cook set; identity-returns when unchanged. */
    public FurnaceOwnerData withOwner(UUID newOwner) {
        Optional<UUID> next = Optional.ofNullable(newOwner);
        return next.equals(owner) ? this : new FurnaceOwnerData(next);
    }
}
