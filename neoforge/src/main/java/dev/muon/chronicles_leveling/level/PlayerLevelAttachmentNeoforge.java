package dev.muon.chronicles_leveling.level;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge attachment for {@link PlayerLevelData}.
 *
 * <p>Persistent (saved to disk via the codec) and synced (sent automatically
 * to the owning client when changed). Same shape as DD's
 * {@code EntityLevelAttachmentNeoForge.LEVEL} — Codec for disk, StreamCodec
 * for sync.
 */
public final class PlayerLevelAttachmentNeoforge {

    public static final DeferredRegister<AttachmentType<?>> REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ChroniclesLeveling.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerLevelData>> LEVEL =
            REGISTRY.register("player_level",
                    () -> AttachmentType.builder(() -> PlayerLevelData.DEFAULT)
                            .serialize(PlayerLevelData.CODEC.fieldOf("level"))
                            .sync(PlayerLevelData.STREAM_CODEC)
                            .build()
            );

    private PlayerLevelAttachmentNeoforge() {}
}
