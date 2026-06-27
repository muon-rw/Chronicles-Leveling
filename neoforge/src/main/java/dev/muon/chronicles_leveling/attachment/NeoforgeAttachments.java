package dev.muon.chronicles_leveling.attachment;

import com.mojang.serialization.Codec;
import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.skill.PlayerSkillData;
import dev.muon.chronicles_leveling.skill.enchant.TableUsageData;
import dev.muon.chronicles_leveling.skill.gather.CampfireOwnerData;
import dev.muon.chronicles_leveling.skill.gather.FurnaceOwnerData;
import dev.muon.chronicles_leveling.skill.xp.BrewingStationData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Sync visibility mirrors {@code FabricAttachments}:
 * <ul>
 *   <li>{@link #PLAYER_LEVEL}: synced to every tracking client (NeoForge's single-arg
 *       {@code .sync(streamCodec)} broadcasts by default), so other players can render
 *       level-aware HUD elements.</li>
 *   <li>{@link #PLAYER_SKILLS}: synced to the owning client only via the
 *       {@code (holder, to) -> holder == to} predicate; per-skill stats are private.</li>
 *   <li>{@link #BREWING_STATION} / {@link #SPAWNER_ORIGIN}: persistent only, server-side.</li>
 * </ul>
 */
public final class NeoforgeAttachments {

    public static final DeferredRegister<AttachmentType<?>> REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ChroniclesLeveling.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerLevelData>> PLAYER_LEVEL =
            REGISTRY.register("player_level",
                    () -> AttachmentType.builder(() -> PlayerLevelData.DEFAULT)
                            .serialize(PlayerLevelData.CODEC.fieldOf("level"))
                            .copyOnDeath()
                            .sync(PlayerLevelData.STREAM_CODEC)
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerSkillData>> PLAYER_SKILLS =
            REGISTRY.register("player_skills",
                    () -> AttachmentType.builder(() -> PlayerSkillData.DEFAULT)
                            .serialize(PlayerSkillData.CODEC.fieldOf("skills_data"))
                            .copyOnDeath()
                            .sync((holder, to) -> holder == to, PlayerSkillData.STREAM_CODEC.cast())
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BrewingStationData>> BREWING_STATION =
            REGISTRY.register("brewing_station_state",
                    () -> AttachmentType.builder(() -> BrewingStationData.DEFAULT)
                            .serialize(BrewingStationData.CODEC.fieldOf("brewing_station_data"))
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CampfireOwnerData>> CAMPFIRE_OWNER =
            REGISTRY.register("campfire_owner",
                    () -> AttachmentType.builder(() -> CampfireOwnerData.DEFAULT)
                            .serialize(CampfireOwnerData.CODEC.fieldOf("campfire_owner"))
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FurnaceOwnerData>> FURNACE_OWNER =
            REGISTRY.register("furnace_owner",
                    () -> AttachmentType.builder(() -> FurnaceOwnerData.DEFAULT)
                            .serialize(FurnaceOwnerData.CODEC.fieldOf("furnace_owner"))
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> SPAWNER_ORIGIN =
            REGISTRY.register("from_spawner",
                    () -> AttachmentType.builder(() -> Boolean.FALSE)
                            .serialize(Codec.BOOL.fieldOf("from_spawner"))
                            .copyOnDeath()
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TableUsageData>> TABLE_USAGE =
            REGISTRY.register("table_usage",
                    () -> AttachmentType.builder(() -> TableUsageData.DEFAULT)
                            .serialize(TableUsageData.CODEC.fieldOf("table_usage"))
                            .copyOnDeath()
                            .build()
            );

    private NeoforgeAttachments() {}
}
