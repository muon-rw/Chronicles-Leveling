package dev.muon.chronicles_leveling.level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;

/**
 * The full leveling state we persist per-player.
 *
 * <p>Kept as a single record (vs. multiple attachments) so a single sync packet
 * delivers the whole picture and there's no inter-attachment ordering risk on
 * client load.
 *
 * <ul>
 *   <li>{@link #level} — current player level. Starts at 1.</li>
 *   <li>{@link #unspentPoints} — stat points the player has earned but hasn't allocated.</li>
 *   <li>{@link #allocations} — points the player has spent on each stat, keyed by stat id
 *       (e.g. {@code "strength" -> 7}). Source of truth for "what did the player allocate";
 *       materialized onto each stat attribute as a single stable-id {@code AttributeModifier}
 *       by {@link dev.muon.chronicles_leveling.stat.StatModifierApplier}. Keeping it here
 *       (vs. on the attribute base) means {@code /attribute base set} can't fool the
 *       refund pool, and a respec is just clearing this map and re-applying.</li>
 * </ul>
 *
 * <p>The XP that funds level-ups is the player's vanilla XP pool — we don't
 * bank a separate counter.
 */
public record PlayerLevelData(int level, int unspentPoints, Map<String, Integer> allocations) {

    public static final PlayerLevelData DEFAULT = new PlayerLevelData(1, 0, Map.of());

    public PlayerLevelData {
        // Defensive copy + immutable. Callers can mutate or alias their map without us caring.
        allocations = Map.copyOf(allocations);
    }

    public static final Codec<PlayerLevelData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("level").forGetter(PlayerLevelData::level),
            Codec.INT.fieldOf("unspent_points").forGetter(PlayerLevelData::unspentPoints),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("allocations", Map.of())
                    .forGetter(PlayerLevelData::allocations)
    ).apply(instance, PlayerLevelData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerLevelData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PlayerLevelData::level,
                    ByteBufCodecs.VAR_INT, PlayerLevelData::unspentPoints,
                    ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
                    PlayerLevelData::allocations,
                    PlayerLevelData::new
            );

    public int allocation(String statId) {
        return allocations.getOrDefault(statId, 0);
    }

    public PlayerLevelData withLevel(int level) {
        return new PlayerLevelData(level, this.unspentPoints, this.allocations);
    }

    public PlayerLevelData withUnspentPoints(int unspentPoints) {
        return new PlayerLevelData(this.level, unspentPoints, this.allocations);
    }

    public PlayerLevelData withAllocation(String statId, int value) {
        Map<String, Integer> next = new HashMap<>(this.allocations);
        if (value <= 0) {
            next.remove(statId);
        } else {
            next.put(statId, value);
        }
        return new PlayerLevelData(this.level, this.unspentPoints, next);
    }

    public PlayerLevelData withAllocationsCleared() {
        return new PlayerLevelData(this.level, this.unspentPoints, Map.of());
    }
}
