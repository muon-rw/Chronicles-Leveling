package dev.muon.chronicles_leveling.level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The full leveling state we persist per-player.
 *
 * <p>Kept as a single record (vs. multiple attachments) so a single sync packet
 * delivers the whole picture and there's no inter-attachment ordering risk on
 * client load.
 *
 * <ul>
 *   <li>{@link #level} — current player level. Starts at 1.</li>
 *   <li>{@link #xp} — XP banked toward the next level. Reset to 0 on level-up.</li>
 *   <li>{@link #unspentPoints} — stat points the player has earned but hasn't allocated.</li>
 * </ul>
 *
 * <p>Stat allocations themselves are stored on each player's vanilla
 * {@link net.minecraft.world.entity.ai.attributes.AttributeInstance} rather than
 * here — that lets vanilla persistence + sync handle them and lets attribute
 * tooltips work everywhere a vanilla attribute would.
 */
public record PlayerLevelData(int level, int xp, int unspentPoints) {

    public static final PlayerLevelData DEFAULT = new PlayerLevelData(1, 0, 0);

    public static final Codec<PlayerLevelData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("level").forGetter(PlayerLevelData::level),
            Codec.INT.fieldOf("xp").forGetter(PlayerLevelData::xp),
            Codec.INT.fieldOf("unspent_points").forGetter(PlayerLevelData::unspentPoints)
    ).apply(instance, PlayerLevelData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerLevelData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PlayerLevelData::level,
                    ByteBufCodecs.VAR_INT, PlayerLevelData::xp,
                    ByteBufCodecs.VAR_INT, PlayerLevelData::unspentPoints,
                    PlayerLevelData::new
            );

    public PlayerLevelData withLevel(int level) {
        return new PlayerLevelData(level, this.xp, this.unspentPoints);
    }

    public PlayerLevelData withXp(int xp) {
        return new PlayerLevelData(this.level, xp, this.unspentPoints);
    }

    public PlayerLevelData withUnspentPoints(int unspentPoints) {
        return new PlayerLevelData(this.level, this.xp, unspentPoints);
    }
}
