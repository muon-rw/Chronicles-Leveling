package dev.muon.chronicles_leveling.skill;

import java.util.List;
import java.util.Set;

/**
 * Canonical list of action-trained skills. Fixed at compile time — pack
 * authors customize each skill via its own file under
 * {@code config/chronicles_leveling/skills/<id>.toml}, but the set of skill
 * ids itself is not configurable.
 *
 * <p>{@link #LEFT_COL} and {@link #RIGHT_COL} are the screen layout order
 * (same index in both = same row); {@link #ALL} is a flat lookup set used
 * for validation.
 */
public final class Skills {

    private Skills() {}

    public static final String WEAPONRY   = "weaponry";
    public static final String ARCHERY    = "archery";
    public static final String MAGIC      = "magic";
    public static final String ARMOR      = "armor";
    public static final String ACROBATICS = "acrobatics";
    public static final String ALCHEMY    = "alchemy";

    public static final String MINING     = "mining";
    public static final String SPEECH     = "speech";
    public static final String FARMING    = "farming";
    public static final String ENCHANTING = "enchanting";
    public static final String SMITHING   = "smithing";
    public static final String FISHING    = "fishing";

    public static final List<String> LEFT_COL = List.of(
            WEAPONRY, ARCHERY, MAGIC, ARMOR, ACROBATICS, ALCHEMY
    );

    public static final List<String> RIGHT_COL = List.of(
            MINING, SPEECH, FARMING, ENCHANTING, SMITHING, FISHING
    );

    public static final List<String> ALL = List.of(
            WEAPONRY, ARCHERY, MAGIC, ARMOR, ACROBATICS, ALCHEMY,
            MINING, SPEECH, FARMING, ENCHANTING, SMITHING, FISHING
    );

    private static final Set<String> ID_SET = Set.copyOf(ALL);

    public static boolean isRegistered(String skillId) {
        return ID_SET.contains(skillId);
    }
}
