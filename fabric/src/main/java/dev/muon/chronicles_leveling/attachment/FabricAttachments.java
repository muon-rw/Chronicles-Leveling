package dev.muon.chronicles_leveling.attachment;

import com.mojang.serialization.Codec;
import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.skill.PlayerSkillData;
import dev.muon.chronicles_leveling.skill.xp.BrewingStationData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Single registration hub for every Fabric attachment the mod uses. Adding a
 * new attachment is one entry here plus a thin {@code Store} impl in the
 * relevant domain package; the entrypoint wiring stays one {@link #init()}
 * call.
 *
 * <p>Sync setup follows the platform layer's contract:
 * <ul>
 *   <li>{@link #PLAYER_LEVEL} / {@link #PLAYER_SKILLS} — synced to the owning
 *       client only ({@link AttachmentSyncPredicate#targetOnly()}).</li>
 *   <li>{@link #BREWING_STATION} / {@link #SPAWNER_ORIGIN} — persistent only;
 *       server-side XP gates that don't need to round-trip.</li>
 * </ul>
 */
public final class FabricAttachments {

    public static final AttachmentType<PlayerLevelData> PLAYER_LEVEL = AttachmentRegistry.create(
            ChroniclesLeveling.id("player_level"),
            builder -> builder
                    .initializer(() -> PlayerLevelData.DEFAULT)
                    .persistent(PlayerLevelData.CODEC)
                    .syncWith(PlayerLevelData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
    );

    public static final AttachmentType<PlayerSkillData> PLAYER_SKILLS = AttachmentRegistry.create(
            ChroniclesLeveling.id("player_skills"),
            builder -> builder
                    .initializer(() -> PlayerSkillData.DEFAULT)
                    .persistent(PlayerSkillData.CODEC)
                    .syncWith(PlayerSkillData.STREAM_CODEC.cast(), AttachmentSyncPredicate.targetOnly())
    );

    public static final AttachmentType<BrewingStationData> BREWING_STATION = AttachmentRegistry.create(
            ChroniclesLeveling.id("brewing_station_state"),
            builder -> builder
                    .initializer(() -> BrewingStationData.DEFAULT)
                    .persistent(BrewingStationData.CODEC)
    );

    public static final AttachmentType<Boolean> SPAWNER_ORIGIN = AttachmentRegistry.create(
            ChroniclesLeveling.id("from_spawner"),
            builder -> builder
                    .initializer(() -> Boolean.FALSE)
                    .persistent(Codec.BOOL)
    );

    private FabricAttachments() {}

    /** Forces class-load + registration during early mod init. Call once. */
    public static void init() {
        ChroniclesLeveling.LOG.debug("Registered Fabric attachments: {}, {}, {}, {}",
                PLAYER_LEVEL.identifier(), PLAYER_SKILLS.identifier(),
                BREWING_STATION.identifier(), SPAWNER_ORIGIN.identifier());
    }
}
