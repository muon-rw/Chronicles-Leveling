package dev.muon.chronicles_leveling.skill;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Shared tag handles used by more than one skill subsystem, so the canonical definition can't drift
 * between, say, the XP seam and the proc seam.
 */
public final class SkillTags {

    private SkillTags() {}

    /**
     * The cross-loader ore tag (Fabric Convention Tags v2 and NeoForge both publish {@code #c:ores}),
     * so one {@link TagKey#create} resolves on either side without a per-loader bridge. Read by both
     * {@code MiningXpHandler} (XP) and {@code GatherProcRouter} (loot procs).
     */
    public static final TagKey<Block> ORES = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));
}
