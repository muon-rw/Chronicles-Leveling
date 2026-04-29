package dev.muon.chronicles_leveling.sounds;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSoundsFabric {

    public static final Holder<SoundEvent> LEVEL_UP = register("level_up");
    public static final Holder<SoundEvent> SP_SPEND = register("sp_spend");

    private ModSoundsFabric() {}

    private static Holder<SoundEvent> register(String path) {
        Identifier id = ChroniclesLeveling.id(path);
        return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id,
                SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
        ModSounds.LEVEL_UP = LEVEL_UP;
        ModSounds.SP_SPEND = SP_SPEND;
    }
}
