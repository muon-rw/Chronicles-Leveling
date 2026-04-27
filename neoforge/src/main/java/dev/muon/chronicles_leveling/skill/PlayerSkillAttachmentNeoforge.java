package dev.muon.chronicles_leveling.skill;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge attachment for {@link PlayerSkillData}.
 *
 * <p>Persistent and synced. Same shape as {@code PlayerLevelAttachmentNeoforge} —
 * Codec for disk, StreamCodec for sync.
 */
public final class PlayerSkillAttachmentNeoforge {

    public static final DeferredRegister<AttachmentType<?>> REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ChroniclesLeveling.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerSkillData>> SKILLS =
            REGISTRY.register("player_skills",
                    () -> AttachmentType.builder(() -> PlayerSkillData.DEFAULT)
                            .serialize(PlayerSkillData.CODEC.fieldOf("skills_data"))
                            .sync(PlayerSkillData.STREAM_CODEC.cast())
                            .build()
            );

    private PlayerSkillAttachmentNeoforge() {}
}
