package dev.muon.chronicles_leveling.skill;

import java.util.List;

/**
 * Assembles and freezes the {@link SkillRegistry} at common-setup time: core skills
 * first, then addon contributions, then freeze (validate + lock).
 *
 * <p>Both loaders call this once their stat attributes have published, from the
 * post-construction setup phase ({@code FMLCommonSetupEvent} on NeoForge, the end of
 * {@code onInitialize} on Fabric), never from a mod constructor, since the registry
 * must stay open until every dependent addon has had a chance to contribute.
 */
public final class SkillBootstrap {

    private SkillBootstrap() {}

    public static void registerAndFreeze(List<SkillContributor> contributors) {
        CoreSkills.bootstrap();
        for (SkillContributor contributor : contributors) {
            contributor.contribute();
        }
        SkillRegistry.freeze();
    }
}
