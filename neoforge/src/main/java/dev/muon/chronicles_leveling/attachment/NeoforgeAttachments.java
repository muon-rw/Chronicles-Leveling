package dev.muon.chronicles_leveling.attachment;

import com.mojang.serialization.Codec;
import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.skill.PlayerSkillData;
import dev.muon.chronicles_leveling.skill.xp.BrewingStationData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Single registration hub for every NeoForge attachment the mod uses. One
 * {@link DeferredRegister} for the whole mod — the entrypoint binds it to the
 * mod bus exactly once. Adding a new attachment is one entry here plus a thin
 * {@code Store} impl in the relevant domain package.
 *
 * <p>Sync setup mirrors {@code FabricAttachments}:
 * <ul>
 *   <li>{@link #PLAYER_LEVEL} / {@link #PLAYER_SKILLS} — Codec for disk +
 *       StreamCodec for the platform's auto-sync to the owning client.</li>
 *   <li>{@link #BREWING_STATION} / {@link #SPAWNER_ORIGIN} — persistent only;
 *       server-side state.</li>
 * </ul>
 */
public final class NeoforgeAttachments {

    public static final DeferredRegister<AttachmentType<?>> REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ChroniclesLeveling.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerLevelData>> PLAYER_LEVEL =
            REGISTRY.register("player_level",
                    () -> AttachmentType.builder(() -> PlayerLevelData.DEFAULT)
                            .serialize(PlayerLevelData.CODEC.fieldOf("level"))
                            .sync(PlayerLevelData.STREAM_CODEC)
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerSkillData>> PLAYER_SKILLS =
            REGISTRY.register("player_skills",
                    () -> AttachmentType.builder(() -> PlayerSkillData.DEFAULT)
                            .serialize(PlayerSkillData.CODEC.fieldOf("skills_data"))
                            .sync(PlayerSkillData.STREAM_CODEC.cast())
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BrewingStationData>> BREWING_STATION =
            REGISTRY.register("brewing_station_state",
                    () -> AttachmentType.builder(() -> BrewingStationData.DEFAULT)
                            .serialize(BrewingStationData.CODEC.fieldOf("brewing_station_data"))
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> SPAWNER_ORIGIN =
            REGISTRY.register("from_spawner",
                    () -> AttachmentType.builder(() -> Boolean.FALSE)
                            .serialize(Codec.BOOL.fieldOf("from_spawner"))
                            .build()
            );

    private NeoforgeAttachments() {}
}
