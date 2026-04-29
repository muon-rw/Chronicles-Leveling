package dev.muon.chronicles_leveling.sounds;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSoundsNeoforge {

    public static final DeferredRegister<SoundEvent> REGISTRY =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, ChroniclesLeveling.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL_UP = register("level_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> SP_SPEND = register("sp_spend");

    private ModSoundsNeoforge() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        Identifier id = ChroniclesLeveling.id(path);
        return REGISTRY.register(path, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
        ModSounds.LEVEL_UP = LEVEL_UP;
        ModSounds.SP_SPEND = SP_SPEND;
    }
}
