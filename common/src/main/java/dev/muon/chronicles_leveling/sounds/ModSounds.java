package dev.muon.chronicles_leveling.sounds;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/**
 * Central reference for the mod's {@link SoundEvent}s.
 *
 * <p>Holders are populated by loader-specific code (NeoForge: {@code DeferredRegister},
 * Fabric: {@code Registry.registerForHolder}) — registration matters because some
 * sounds (orb confirms, tome use) play <i>server-side</i> via {@code Level#playSound},
 * which looks the event up in {@link net.minecraft.core.registries.BuiltInRegistries#SOUND_EVENT}
 * and silently no-ops if it's not there.
 *
 * <p>Sound files live at {@code assets/chronicles_leveling/sounds/&lt;name&gt;.ogg}
 * and are wired up in {@code assets/chronicles_leveling/sounds.json}.
 */
public final class ModSounds {

    private ModSounds() {}

    /** Plays on level-ups, orb confirmations, and tome use. */
    public static Holder<SoundEvent> LEVEL_UP;

    /** Plays whenever the player allocates a stat point or buys a level. */
    public static Holder<SoundEvent> SP_SPEND;
}
