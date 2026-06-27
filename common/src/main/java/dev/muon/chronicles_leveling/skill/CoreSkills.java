package dev.muon.chronicles_leveling.skill;

import dev.muon.chronicles_leveling.skill.catalog.AcrobaticsSkill;
import dev.muon.chronicles_leveling.skill.catalog.AlchemySkill;
import dev.muon.chronicles_leveling.skill.catalog.ArcherySkill;
import dev.muon.chronicles_leveling.skill.catalog.DefenseSkill;
import dev.muon.chronicles_leveling.skill.catalog.EnchantingSkill;
import dev.muon.chronicles_leveling.skill.catalog.FishingSkill;
import dev.muon.chronicles_leveling.skill.catalog.HerbalismSkill;
import dev.muon.chronicles_leveling.skill.catalog.MagicSkill;
import dev.muon.chronicles_leveling.skill.catalog.MiningSkill;
import dev.muon.chronicles_leveling.skill.catalog.SmithingSkill;
import dev.muon.chronicles_leveling.skill.catalog.SpeechSkill;
import dev.muon.chronicles_leveling.skill.catalog.WeaponrySkill;

/**
 * Registers the twelve core skill definitions into the {@link SkillRegistry}. Each skill's
 * tree is authored in its own {@code skill.catalog.*Skill} file (see {@code MiningSkill} as
 * the exemplar); this just assembles them. Addons add or extend skills via the
 * {@link SkillContributor} seam, which runs after this and before freeze.
 */
public final class CoreSkills {

    private CoreSkills() {}

    public static void bootstrap() {
        SkillRegistry.register(WeaponrySkill.define());
        SkillRegistry.register(ArcherySkill.define());
        SkillRegistry.register(MagicSkill.define());
        SkillRegistry.register(DefenseSkill.define());
        SkillRegistry.register(AcrobaticsSkill.define());
        SkillRegistry.register(AlchemySkill.define());
        SkillRegistry.register(MiningSkill.define());
        SkillRegistry.register(SpeechSkill.define());
        SkillRegistry.register(HerbalismSkill.define());
        SkillRegistry.register(EnchantingSkill.define());
        SkillRegistry.register(SmithingSkill.define());
        SkillRegistry.register(FishingSkill.define());
    }
}
