package dev.muon.chronicles_leveling;

import dev.muon.chronicles_leveling.command.ChroniclesCommands;
import dev.muon.chronicles_leveling.event.PlayerStatsEventsFabric;
import dev.muon.chronicles_leveling.item.ModItemsFabric;
import dev.muon.chronicles_leveling.level.PlayerLevelAttachmentFabric;
import dev.muon.chronicles_leveling.network.NetworkRegistrationFabric;
import dev.muon.chronicles_leveling.skill.PlayerSkillAttachmentFabric;
import dev.muon.chronicles_leveling.sounds.ModSoundsFabric;
import dev.muon.chronicles_leveling.stat.ModStatsFabric;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Fabric mod entrypoint. Order matters here:
 * <ol>
 *   <li>Common {@link ChroniclesLeveling#init()} first — registers configs +
 *       wires the DD compat shim.</li>
 *   <li>Stat attribute registration before the player-attribute mixin would
 *       run (it does so the first time {@code Player.createAttributes()} is
 *       called, after entrypoints).</li>
 *   <li>Attachment registration before any save load, before the network
 *       receiver registration that depends on it.</li>
 *   <li>Network channels last; they don't depend on other state but should
 *       not register before {@code DataSync} ID space is built.</li>
 * </ol>
 */
public class ChroniclesLevelingFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ChroniclesLeveling.init();

        ModStatsFabric.ensureInitialized();
        PlayerLevelAttachmentFabric.init();
        PlayerSkillAttachmentFabric.init();
        ModSoundsFabric.init();
        ModItemsFabric.init();
        NetworkRegistrationFabric.initServer();

        PlayerStatsEventsFabric.initLifecycle();

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
                ChroniclesCommands.register(dispatcher));
    }
}
