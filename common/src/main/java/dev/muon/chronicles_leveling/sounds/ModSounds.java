package dev.muon.chronicles_leveling.sounds;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.sounds.SoundEvent;

/**
 * Central reference for the mod's {@link SoundEvent}s.
 *
 * <p>Each entry resolves a sound by id rather than going through the
 * {@code SOUND_EVENT} registry — UI playback via {@code SoundManager} only
 * needs the location, and {@code SoundEvent.createVariableRangeEvent} avoids
 * the loader-specific registry plumbing for sounds we never broadcast from
 * the server.
 *
 * <p>Sound files live at {@code assets/chronicles_leveling/sounds/&lt;name&gt;.ogg}
 * and are wired up in {@code assets/chronicles_leveling/sounds.json}; add a
 * matching entry there alongside any new constant added below.
 */
public final class ModSounds {

    private ModSounds() {}

    /** Plays when the player crosses into "can afford the next level" territory. */
    public static final SoundEvent LEVEL_UP = ui("level_up");

    /** Plays when the player allocates a stat point. */
    public static final SoundEvent SP_SPEND = ui("sp_spend");

    private static SoundEvent ui(String path) {
        return SoundEvent.createVariableRangeEvent(ChroniclesLeveling.id(path));
    }
}
