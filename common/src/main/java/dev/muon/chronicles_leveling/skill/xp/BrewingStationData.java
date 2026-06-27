package dev.muon.chronicles_leveling.skill.xp;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-{@code BrewingStandBlockEntity} state for the alchemy skill.
 *
 * <ul>
 *   <li>{@link #pendingXp}: a 3-slot list of alchemy XP owed for a potion brewed here but not yet taken (0 = none).
 *       The value is computed at brew time, when the output's identity and count are known, because the brewed
 *       potion's components cannot be recovered from the taken stack on a shift-click (a quick-move empties the
 *       slot before {@code onTake}). It survives chunk unloads, restarts, and the brewer logging out before they
 *       take the potion, and is paid out in full to the taker on the first take.</li>
 *   <li>{@link #owner}: the last player to open the stand. The brewing perks (Master Brewer, Catalysis, Potent
 *       Mixtures, Lingering Touch) read this owner's capabilities, since {@code doBrew} is a static, playerless tick.</li>
 * </ul>
 *
 * <p>Mirrors the shape of {@code PlayerSkillData} (record + Codec) so the loader-specific {@code AttachmentType}
 * entries register identically. Both fields use {@code optionalFieldOf} so saves written before they existed
 * round-trip cleanly.
 */
public record BrewingStationData(List<Integer> pendingXp, Optional<UUID> owner) {

    private static final List<Integer> NONE = List.of(0, 0, 0);

    public static final BrewingStationData DEFAULT = new BrewingStationData(NONE, Optional.empty());

    public static final Codec<BrewingStationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.listOf().optionalFieldOf("pending_xp", NONE).forGetter(BrewingStationData::pendingXp),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(BrewingStationData::owner)
    ).apply(instance, BrewingStationData::new));

    /** Alchemy XP owed for slot 0/1/2 from a brew done here, or 0 if the slot holds nothing freshly brewed. */
    public int pendingXp(int slot) {
        return slot < 0 || slot >= pendingXp.size() ? 0 : pendingXp.get(slot);
    }

    /** Returns a new instance with the slot's pending XP set (owner preserved); identity-returns when unchanged. */
    public BrewingStationData withPendingXp(int slot, int xp) {
        if (slot < 0 || slot > 2 || pendingXp(slot) == xp) {
            return this;
        }
        List<Integer> updated = new ArrayList<>(NONE);
        for (int i = 0; i < 3; i++) {
            updated.set(i, pendingXp(i));
        }
        updated.set(slot, xp);
        return new BrewingStationData(List.copyOf(updated), owner);
    }

    /** Returns a new instance with the owning player set (pending XP preserved); identity-returns when unchanged. */
    public BrewingStationData withOwner(UUID newOwner) {
        Optional<UUID> next = Optional.ofNullable(newOwner);
        return next.equals(owner) ? this : new BrewingStationData(pendingXp, next);
    }
}
