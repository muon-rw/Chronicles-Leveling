package dev.muon.chronicles_leveling.attachment;

import com.mojang.serialization.Codec;
import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.skill.PlayerSkillData;
import dev.muon.chronicles_leveling.skill.enchant.TableUsageData;
import dev.muon.chronicles_leveling.skill.gather.CampfireOwnerData;
import dev.muon.chronicles_leveling.skill.gather.FurnaceOwnerData;
import dev.muon.chronicles_leveling.skill.xp.BrewingStationData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Sync setup rationale:
 * <ul>
 *   <li>{@link #PLAYER_LEVEL}: synced to every tracking client
 *       ({@link AttachmentSyncPredicate#all()}) so other players can render
 *       level-aware HUD elements (nameplates, tab list, etc.).</li>
 *   <li>{@link #PLAYER_SKILLS}: synced to the owning client only
 *       ({@link AttachmentSyncPredicate#targetOnly()}); per-skill stats are
 *       private to the player.</li>
 *   <li>{@link #BREWING_STATION} / {@link #SPAWNER_ORIGIN}: persistent only;
 *       server-side XP gates that don't need to round-trip.</li>
 * </ul>
 */
public final class FabricAttachments {

    public static final AttachmentType<PlayerLevelData> PLAYER_LEVEL = AttachmentRegistry.create(
            ChroniclesLeveling.id("player_level"),
            builder -> builder
                    .initializer(() -> PlayerLevelData.DEFAULT)
                    .persistent(PlayerLevelData.CODEC)
                    .copyOnDeath()
                    .syncWith(PlayerLevelData.STREAM_CODEC, AttachmentSyncPredicate.all())
    );

    public static final AttachmentType<PlayerSkillData> PLAYER_SKILLS = AttachmentRegistry.create(
            ChroniclesLeveling.id("player_skills"),
            builder -> builder
                    .initializer(() -> PlayerSkillData.DEFAULT)
                    .persistent(PlayerSkillData.CODEC)
                    .copyOnDeath()
                    .syncWith(PlayerSkillData.STREAM_CODEC.cast(), AttachmentSyncPredicate.targetOnly())
    );

    public static final AttachmentType<BrewingStationData> BREWING_STATION = AttachmentRegistry.create(
            ChroniclesLeveling.id("brewing_station_state"),
            builder -> builder
                    .initializer(() -> BrewingStationData.DEFAULT)
                    .persistent(BrewingStationData.CODEC)
    );

    public static final AttachmentType<CampfireOwnerData> CAMPFIRE_OWNER = AttachmentRegistry.create(
            ChroniclesLeveling.id("campfire_owner"),
            builder -> builder
                    .initializer(() -> CampfireOwnerData.DEFAULT)
                    .persistent(CampfireOwnerData.CODEC)
    );

    public static final AttachmentType<FurnaceOwnerData> FURNACE_OWNER = AttachmentRegistry.create(
            ChroniclesLeveling.id("furnace_owner"),
            builder -> builder
                    .initializer(() -> FurnaceOwnerData.DEFAULT)
                    .persistent(FurnaceOwnerData.CODEC)
    );

    public static final AttachmentType<Boolean> SPAWNER_ORIGIN = AttachmentRegistry.create(
            ChroniclesLeveling.id("from_spawner"),
            builder -> builder
                    .initializer(() -> Boolean.FALSE)
                    .persistent(Codec.BOOL)
                    .copyOnDeath()
    );

    public static final AttachmentType<TableUsageData> TABLE_USAGE = AttachmentRegistry.create(
            ChroniclesLeveling.id("table_usage"),
            builder -> builder
                    .initializer(() -> TableUsageData.DEFAULT)
                    .persistent(TableUsageData.CODEC)
                    .copyOnDeath()
    );

    private FabricAttachments() {}

    /** Forces class-load + registration during early mod init. Call once. */
    public static void init() {
        ChroniclesLeveling.LOG.debug("Registered Fabric attachments: {}, {}, {}, {}",
                PLAYER_LEVEL.identifier(), PLAYER_SKILLS.identifier(),
                BREWING_STATION.identifier(), SPAWNER_ORIGIN.identifier());
    }
}
