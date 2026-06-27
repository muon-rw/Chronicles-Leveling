package dev.muon.chronicles_leveling.skill.gather;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Per-{@code CampfireBlockEntity} placer attribution for Gardener's Infusion: the player who placed the food in each
 * cooking slot, so the boost can be credited when the item pops on cook-finish (the cook tick is a static, playerless
 * loop). Stored as a BE attachment so it survives chunk unloads and restarts, mirroring {@code BrewingStationData};
 * an unowned slot holds the nil UUID.
 */
public record CampfireOwnerData(List<UUID> owners) {

    private static final UUID NONE = new UUID(0L, 0L);
    private static final int SLOTS = 4;

    public static final CampfireOwnerData DEFAULT = new CampfireOwnerData(List.of(NONE, NONE, NONE, NONE));

    public static final Codec<CampfireOwnerData> CODEC =
            UUIDUtil.CODEC.listOf().xmap(CampfireOwnerData::new, CampfireOwnerData::owners);

    /** The placer of the food in {@code slot}, or {@code null} if the slot is unowned. */
    public UUID owner(int slot) {
        if (slot < 0 || slot >= owners.size()) {
            return null;
        }
        UUID owner = owners.get(slot);
        return NONE.equals(owner) ? null : owner;
    }

    /** A copy with {@code slot}'s owner set ({@code null} clears it); identity-returns when unchanged. */
    public CampfireOwnerData with(int slot, UUID owner) {
        if (slot < 0 || slot >= SLOTS) {
            return this;
        }
        UUID target = owner == null ? NONE : owner;
        if (slot < owners.size() && owners.get(slot).equals(target)) {
            return this;
        }
        List<UUID> next = new ArrayList<>(owners);
        while (next.size() < SLOTS) {
            next.add(NONE);
        }
        next.set(slot, target);
        return new CampfireOwnerData(List.copyOf(next));
    }
}
