package dev.muon.chronicles_leveling;

import dev.muon.chronicles_leveling.client.AbilityHudRenderer;
import dev.muon.chronicles_leveling.client.ChroniclesKeybinds;
import dev.muon.chronicles_leveling.client.SkillSpriteAudit;
import dev.muon.chronicles_leveling.client.SkillTranslationAudit;
import dev.muon.chronicles_leveling.client.XpAffordabilityNotifier;
import dev.muon.chronicles_leveling.client.mining.VeinSightRenderer;
import dev.muon.chronicles_leveling.client.mining.VeinSightScanner;
import dev.muon.chronicles_leveling.network.message.AbilityWindowsPacket;
import dev.muon.chronicles_leveling.network.message.ArcaneInsightCluesPacket;
import dev.muon.chronicles_leveling.network.message.CastFailedPacket;
import dev.muon.chronicles_leveling.network.message.WizardsStudyTablePacket;
import dev.muon.chronicles_leveling.skill.enchant.WizardsStudyHandler;
import dev.muon.chronicles_leveling.skill.gather.GardenersInfusionHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Collection;
import java.util.List;

public class ChroniclesLevelingFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(ChroniclesKeybinds.OPEN_STATS);
        for (var slotKey : ChroniclesKeybinds.ABILITY_SLOTS) {
            KeyMappingHelper.registerKeyMapping(slotKey);
        }
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.HEALTH_BAR,
                ChroniclesLeveling.id("ability_hud"),
                AbilityHudRenderer::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ChroniclesKeybinds.tick();
            XpAffordabilityNotifier.tick();
            VeinSightScanner.tick();
        });

        // Audit skill/perk/ability assets each time client resources finish (re)loading; depends on
        // LANGUAGES so I18n is populated before the lang-key check runs.
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return ChroniclesLeveling.id("skill_asset_audit");
                    }

                    @Override
                    public Collection<Identifier> getFabricDependencies() {
                        return List.of(ResourceReloadListenerKeys.LANGUAGES);
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        SkillTranslationAudit.run();
                        SkillSpriteAudit.run(manager);
                    }
                });

        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                VeinSightRenderer.render(context.poseStack(), context.bufferSource(),
                        mc.gameRenderer.getMainCamera().position(), mc.level.getGameTime());
            }
        });

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (WizardsStudyHandler.isMagicallyInfused(stack)) {
                lines.add(Math.min(1, lines.size()), WizardsStudyHandler.magicallyInfusedLine());
            }
            if (GardenersInfusionHandler.isInfused(stack)) {
                lines.add(Math.min(1, lines.size()), GardenersInfusionHandler.infusedLine());
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(ArcaneInsightCluesPacket.TYPE,
                (payload, context) -> context.client().execute(
                        () -> ArcaneInsightCluesPacket.handleOnClient(payload)));
        ClientPlayNetworking.registerGlobalReceiver(WizardsStudyTablePacket.TYPE,
                (payload, context) -> context.client().execute(
                        () -> WizardsStudyTablePacket.handleOnClient(payload)));
        ClientPlayNetworking.registerGlobalReceiver(AbilityWindowsPacket.TYPE,
                (payload, context) -> context.client().execute(
                        () -> AbilityWindowsPacket.handleOnClient(payload)));
        ClientPlayNetworking.registerGlobalReceiver(CastFailedPacket.TYPE,
                (payload, context) -> context.client().execute(
                        () -> CastFailedPacket.handleOnClient(payload)));
    }
}
