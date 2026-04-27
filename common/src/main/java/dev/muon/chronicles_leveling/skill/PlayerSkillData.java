package dev.muon.chronicles_leveling.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player skill state. A single record holding every skill the player has
 * progressed in, keyed by skill id. Skills the player hasn't touched aren't
 * stored — {@link #get(String)} returns {@link SkillEntry#DEFAULT} (level 1,
 * 0 XP) for those, which is the correct starting state.
 *
 * <p>Mirrors {@code PlayerLevelData} in shape: one record, one Codec for disk,
 * one StreamCodec for sync, so the loader's attachment system can persist + sync
 * the whole picture as one unit.
 */
public record PlayerSkillData(Map<String, SkillEntry> skills) {

    public PlayerSkillData {
        skills = Map.copyOf(skills);
    }

    public static final PlayerSkillData DEFAULT = new PlayerSkillData(Map.of());

    public static final Codec<PlayerSkillData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, SkillEntry.CODEC).fieldOf("skills").forGetter(PlayerSkillData::skills)
    ).apply(instance, PlayerSkillData::new));

    public static final StreamCodec<ByteBuf, PlayerSkillData> STREAM_CODEC =
            ByteBufCodecs.<ByteBuf, String, SkillEntry, Map<String, SkillEntry>>map(
                    HashMap::new,
                    ByteBufCodecs.STRING_UTF8,
                    SkillEntry.STREAM_CODEC
            ).map(PlayerSkillData::new, PlayerSkillData::skills);

    /** Returns the player's entry for the given skill, or {@link SkillEntry#DEFAULT} if untouched. */
    public SkillEntry get(String skillId) {
        return skills.getOrDefault(skillId, SkillEntry.DEFAULT);
    }

    /** Returns a new instance with the given skill replaced. */
    public PlayerSkillData with(String skillId, SkillEntry entry) {
        Map<String, SkillEntry> updated = new HashMap<>(skills);
        updated.put(skillId, entry);
        return new PlayerSkillData(updated);
    }

    /**
     * Per-skill state: integer level (starts at 1) and XP banked toward the
     * next level. Reset to 0 on level-up by the trainer logic (not implemented
     * yet — XP gain hooks will land separately).
     */
    public record SkillEntry(int level, int xp) {

        public static final SkillEntry DEFAULT = new SkillEntry(1, 0);

        public static final Codec<SkillEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("level").forGetter(SkillEntry::level),
                Codec.INT.fieldOf("xp").forGetter(SkillEntry::xp)
        ).apply(instance, SkillEntry::new));

        public static final StreamCodec<ByteBuf, SkillEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SkillEntry::level,
                ByteBufCodecs.VAR_INT, SkillEntry::xp,
                SkillEntry::new
        );

        public SkillEntry withLevel(int level) {
            return new SkillEntry(level, this.xp);
        }

        public SkillEntry withXp(int xp) {
            return new SkillEntry(this.level, xp);
        }
    }
}
