package dev.muon.chronicles_leveling.skill;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Fabric attachment for {@link PlayerSkillData}.
 *
 * <p>Persistent (saved via the codec) and synced to the owning client via
 * Fabric's built-in attachment sync. Mirrors {@code PlayerLevelAttachmentFabric}.
 */
public final class PlayerSkillAttachmentFabric {

    public static final AttachmentType<PlayerSkillData> SKILLS = AttachmentRegistry.create(
            ChroniclesLeveling.id("player_skills"),
            builder -> builder
                    .initializer(() -> PlayerSkillData.DEFAULT)
                    .persistent(PlayerSkillData.CODEC)
                    .syncWith(PlayerSkillData.STREAM_CODEC.cast(), AttachmentSyncPredicate.targetOnly())
    );

    private PlayerSkillAttachmentFabric() {}

    /** Forces class-load + registration during early mod init. */
    public static void init() {
        ChroniclesLeveling.LOG.debug("Registered Fabric player skills attachment: {}", SKILLS.identifier());
    }
}
