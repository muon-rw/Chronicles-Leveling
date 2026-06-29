package dev.muon.chronicles_leveling.client;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.client.screen.ChroniclesTextures;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.SkillRegistry;
import dev.muon.chronicles_leveling.skill.ability.SkillAbility;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import dev.muon.chronicles_leveling.skill.perk.SkillPerk;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Sanity check (mirrors {@link SkillTranslationAudit}): logs any registered perk or ability whose GUI
 * sprite is absent from the loaded resources (ERROR in dev, WARN in production), so a renamed/added perk
 * or a stale regenerated icon surfaces in the log instead of as a silent missing-texture in the tree/HUD.
 * Checks each perk's icon and its locked grayscale variant, and each ability's icon.
 */
public final class SkillSpriteAudit {

    private SkillSpriteAudit() {}

    public static void run(ResourceManager resources) {
        if (!SkillRegistry.isFrozen()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (SkillDefinition def : SkillRegistry.all()) {
            for (SkillPerk perk : def.perks()) {
                check(missing, resources, "perk '" + perk.owningSkill() + "/" + perk.id() + "' icon",
                        ChroniclesTextures.perk(perk.owningSkill(), perk.id()));
                check(missing, resources, "perk '" + perk.owningSkill() + "/" + perk.id() + "' locked icon",
                        ChroniclesTextures.perkLocked(perk.owningSkill(), perk.id()));
            }
            for (SkillAbility ability : def.abilities()) {
                check(missing, resources, "ability '" + ability.id() + "' icon", ability.icon());
            }
        }

        if (missing.isEmpty()) {
            ChroniclesLeveling.LOG.info("Skill sprite audit: all perk + ability sprites present.");
            return;
        }
        String summary = "Skill sprite audit found " + missing.size() + " missing sprite(s):\n  "
                + String.join("\n  ", missing);
        if (Services.PLATFORM.isDevelopmentEnvironment()) {
            ChroniclesLeveling.LOG.error(summary);   // dev: loud, fail-fast
        } else {
            ChroniclesLeveling.LOG.warn(summary);    // production: warn only
        }
    }

    private static void check(List<String> missing, ResourceManager resources, String what, Identifier texture) {
        if (resources.getResource(texture).isEmpty()) {
            missing.add(what + ": missing texture '" + texture + "'");
        }
    }
}
