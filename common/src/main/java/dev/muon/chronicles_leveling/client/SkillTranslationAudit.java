package dev.muon.chronicles_leveling.client;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.platform.Services;
import dev.muon.chronicles_leveling.skill.SkillRegistry;
import dev.muon.chronicles_leveling.skill.perk.SkillDefinition;
import dev.muon.chronicles_leveling.skill.perk.SkillPerk;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;

public final class SkillTranslationAudit {

    private SkillTranslationAudit() {}

    public static void run() {
        if (!SkillRegistry.isFrozen()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (SkillDefinition def : SkillRegistry.all()) {
            checkComponentKey(missing, "skill '" + def.id() + "' name", def.display());
            def.description().ifPresent(d -> checkComponentKey(missing, "skill '" + def.id() + "' description", d));
            for (SkillPerk perk : def.perks()) {
                String base = "chronicles_leveling.perk." + perk.owningSkill() + "." + perk.id();
                checkKey(missing, "perk '" + perk.owningSkill() + "/" + perk.id() + "' name", base);
                checkKey(missing, "perk '" + perk.owningSkill() + "/" + perk.id() + "' description", base + ".desc");
            }
        }

        if (missing.isEmpty()) {
            ChroniclesLeveling.LOG.info("Skill translation audit: all skill + perk lang keys present.");
            return;
        }
        String summary = "Skill translation audit found " + missing.size() + " missing lang key(s):\n  "
                + String.join("\n  ", missing);
        if (Services.PLATFORM.isDevelopmentEnvironment()) {
            ChroniclesLeveling.LOG.error(summary);   // dev: loud, fail-fast
        } else {
            ChroniclesLeveling.LOG.warn(summary);    // production: warn only
        }
    }

    private static void checkComponentKey(List<String> missing, String what, Component component) {
        if (component.getContents() instanceof TranslatableContents tc) {
            checkKey(missing, what, tc.getKey());
        }
    }

    private static void checkKey(List<String> missing, String what, String key) {
        if (!I18n.exists(key)) {
            missing.add(what + ": missing key '" + key + "'");
        }
    }
}
