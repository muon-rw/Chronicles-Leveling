package dev.muon.chronicles_leveling.client;

import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.skill.PlayerSkillData;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillRegistry;
import dev.muon.chronicles_leveling.skill.ability.AbilityCost;
import dev.muon.chronicles_leveling.skill.ability.AbilitySlots;
import dev.muon.chronicles_leveling.skill.ability.CombatResources;
import dev.muon.chronicles_leveling.skill.ability.SkillAbility;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Loader-agnostic: NeoForge registers this as a {@code GuiLayer} ({@code RegisterGuiLayersEvent}) and Fabric as a
 * {@code HudElement} ({@code HudElementRegistry}); both have the same {@code (GuiGraphicsExtractor, DeltaTracker)}
 * shape and call {@link #render}. All state is read client-side from the synced skill attachment plus
 * Combat-Attributes; nothing server-authoritative happens here. The icon is a placeholder gem for now; per-ability
 * art is a later asset task.
 */
public final class AbilityHudRenderer {

    private AbilityHudRenderer() {}

    private static final int GAP = 2;
    private static final int HOTBAR_HALF_WIDTH = 91;   // vanilla hotbar is 182 wide, centered
    private static final int ICON_INSET = 2;           // icon margin inside the box (per side)

    private static final int COLOR_BOX = 0xC0202020;
    private static final int COLOR_BORDER = 0xFF000000;
    private static final int COLOR_ICON = 0xFF6A8BD0;       // placeholder gem tint
    private static final int COLOR_COOLDOWN = 0x80FFFFFF;   // vanilla-style cooldown sweep
    private static final int COLOR_UNAFFORDABLE = 0x99101018;
    private static final int COLOR_KEY = 0xFFFFFFFF;

    private record Entry(int slot, SkillAbility ability) {}

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) {
            return;
        }
        var cfg = Configs.CLIENT.abilityHud;
        if (!cfg.enabled.get()) {
            return;
        }
        LocalPlayer player = mc.player;
        PlayerSkillData data = PlayerSkillManager.get(player);

        List<Entry> entries = new ArrayList<>();
        for (int slot = 0; slot < AbilitySlots.COUNT; slot++) {
            String abilityId = data.slotAbility(slot);
            if (abilityId == null) {
                continue;
            }
            Identifier id = Identifier.tryParse(abilityId);
            SkillAbility ability = id == null ? null : SkillRegistry.ability(id);
            if (ability != null) {
                entries.add(new Entry(slot, ability));
            }
        }
        if (entries.isEmpty()) {
            return;
        }

        int box = cfg.slotSize.get();
        int icon = Math.max(1, box - 2 * ICON_INSET);
        // Inline to the right of the hotbar, bottom-aligned (with config nudges).
        int x = graphics.guiWidth() / 2 + HOTBAR_HALF_WIDTH + cfg.gapFromHotbar.get();
        int y = graphics.guiHeight() - box - 1 + cfg.verticalOffset.get();
        // Clamp the strip on-screen so it can't clip off the right edge at high GUI scale / narrow windows.
        int stripWidth = entries.size() * box + (entries.size() - 1) * GAP;
        x = Math.max(1, Math.min(x, graphics.guiWidth() - stripWidth - 1));
        long gameTime = mc.level.getGameTime();
        Font font = mc.font;

        for (Entry entry : entries) {
            renderSlot(graphics, font, player, data, entry, x, y, box, icon, gameTime);
            x += box + GAP;
        }
    }

    private static void renderSlot(GuiGraphicsExtractor graphics, Font font, LocalPlayer player,
                                   PlayerSkillData data, Entry entry, int x, int y, int box, int icon, long gameTime) {
        SkillAbility ability = entry.ability();

        // Box + 1px border + placeholder icon.
        graphics.fill(x, y, x + box, y + box, COLOR_BOX);
        graphics.fill(x, y, x + box, y + 1, COLOR_BORDER);
        graphics.fill(x, y + box - 1, x + box, y + box, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + box, COLOR_BORDER);
        graphics.fill(x + box - 1, y, x + box, y + box, COLOR_BORDER);
        int iconX = x + (box - icon) / 2;
        int iconY = y + (box - icon) / 2;
        graphics.fill(iconX, iconY, iconX + icon, iconY + icon, COLOR_ICON);

        // Resource-readiness dim (Combat-Attributes; no-op if CA absent or the ability is free).
        if (!canAfford(player, ability.cost())) {
            graphics.fill(iconX, iconY, iconX + icon, iconY + icon, COLOR_UNAFFORDABLE);
        }

        // Cooldown sweep: a translucent overlay rising from the bottom as it recovers (vanilla item-cooldown look).
        long end = data.abilityCooldownEnd(ability.id().toString());
        if (end > gameTime) {
            int total = Math.max(1, ability.baseCooldownTicks());
            float remaining = Math.min(1f, (end - gameTime) / (float) total);
            int h = Math.max(1, Math.round(icon * remaining));
            graphics.fill(iconX, iconY + icon - h, iconX + icon, iconY + icon, COLOR_COOLDOWN);
        }

        // Keybind label overlaid at the slot's bottom-right (item-count style; blank when the cast key is unbound).
        KeyLabel.draw(graphics, font, entry.slot(), x, y, box);
    }

    private static boolean canAfford(LocalPlayer player, AbilityCost cost) {
        if (!CombatResources.isActive()) {
            return true;   // no resource system → never gate the tint
        }
        return (cost.stamina() <= 0 || CombatResources.getStamina(player) >= cost.stamina())
                && (cost.mana() <= 0 || CombatResources.getMana(player) >= cost.mana());
    }

    private static final class KeyLabel {
        static void draw(GuiGraphicsExtractor graphics, Font font, int slot, int x, int y, int box) {
            KeyMapping key = ChroniclesKeybinds.ABILITY_SLOTS[slot];
            if (key.isUnbound()) {
                return;
            }
            Component label = key.getTranslatedKeyMessage();
            int w = font.width(label);
            graphics.text(font, label, x + box - 1 - w, y + box - font.lineHeight, COLOR_KEY, true);
        }
    }
}
