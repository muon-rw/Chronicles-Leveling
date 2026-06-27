package dev.muon.chronicles_leveling.event;

import dev.muon.chronicles_leveling.skill.SkillContributor;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.function.Consumer;

/**
 * Fired on the mod bus during common setup, after the core skills register but before the
 * registry freezes, so NeoForge addons can register skill contributions. Addons subscribe
 * with {@code @SubscribeEvent} on the mod bus and call {@link #register}.
 *
 * <p>Mirrors the Fabric {@code chronicles_leveling:skills} entrypoint discovery; both end with a
 * {@code List<SkillContributor>} fed to {@code SkillBootstrap.registerAndFreeze}.
 */
public class RegisterSkillContributionsEvent extends Event implements IModBusEvent {

    private final Consumer<SkillContributor> sink;

    public RegisterSkillContributionsEvent(Consumer<SkillContributor> sink) {
        this.sink = sink;
    }

    public void register(SkillContributor contributor) {
        sink.accept(contributor);
    }
}
