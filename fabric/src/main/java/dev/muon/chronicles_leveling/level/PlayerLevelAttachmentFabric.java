package dev.muon.chronicles_leveling.level;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * Fabric attachment for {@link PlayerLevelData}.
 *
 * <p>Persistent and synced via Fabric's built-in attachment sync. Earlier
 * Fabric versions had timing bugs around player join + dimension change that
 * required manual sync packets — those have since been patched, so we let the
 * platform handle it.
 *
 * <p>{@link AttachmentSyncPredicate#targetOnly()} restricts sync to the owning
 * client; other tracking clients only see the rendered nameplate level on a
 * separate path, not the full record.
 */
public final class PlayerLevelAttachmentFabric {

    public static final AttachmentType<PlayerLevelData> LEVEL = AttachmentRegistry.create(
            ChroniclesLeveling.id("player_level"),
            builder -> builder
                    .initializer(() -> PlayerLevelData.DEFAULT)
                    .persistent(PlayerLevelData.CODEC)
                    .syncWith(PlayerLevelData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
    );

    private PlayerLevelAttachmentFabric() {}

    /** Forces class-load + registration during early mod init. */
    public static void init() {
        ChroniclesLeveling.LOG.debug("Registered Fabric player level attachment: {}", LEVEL.identifier());
    }
}
