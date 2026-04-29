package dev.muon.chronicles_leveling.client.screen;

import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.config.Configs;
import dev.muon.chronicles_leveling.level.LevelingCurve;
import dev.muon.chronicles_leveling.level.PlayerLevelData;
import dev.muon.chronicles_leveling.level.PlayerLevelManager;
import dev.muon.chronicles_leveling.level.VanillaXp;
import dev.muon.chronicles_leveling.network.NetworkDispatcher;
import dev.muon.chronicles_leveling.stat.ModStats;
import dev.muon.chronicles_leveling.stat.StatModifierSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "Levels" tab — the player's character overview and stat allocation screen.
 *
 * <p>Header: scaled "Levels" title with a divider beneath; the player name on
 * a regular-size line under the divider; "Lv. n" with the level-up {@code +}
 * button pinned to its right (group is centered, so the button's X is updated
 * each frame to track the level text width); a vanilla XP bar that shows
 * progress detail in a hover tooltip; and a "Points: n" line with the XP icon
 * to its left.
 *
 * <p>Body: six stat rows (Strength / Dexterity / Constitution / Intelligence /
 * Wisdom / Luckiness). Each row has a sprite-backed {@code +} button on the
 * left that spends an unspent skill point on that stat, the stat name, and
 * the integer attribute value on the right. Rows are framed by 1px horizontal
 * separators (1px padding above and below each line) above the first row,
 * between each pair, and below the last.
 *
 * <p>Sized 176×166 to line up with the inventory texture (art originally from
 * PlayerEx, licensed MIT).
 */
public class LevelUpScreen extends Screen {

    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 166;

    private static final int FIRST_ROW_Y = 68;
    private static final int ROW_HEIGHT = 15;          // pitch: 10 content + 3 separator (1 empty + 1 line + 1 empty)
    private static final int ROW_CONTENT_H = 12;

    private static final int BUTTON_X_OFFSET = 12;
    private static final int NAME_X_OFFSET = 26;
    private static final int VALUE_RIGHT_MARGIN = 13;

    private static final int TITLE_Y = 12;
    private static final float TITLE_SCALE = 1.2f;
    private static final int HEADER_LINE_Y = 24;
    private static final int NAME_LINE_Y = 27;
    private static final int LEVEL_LINE_Y = 38;
    private static final int XP_BAR_Y = 49;
    private static final int POINTS_LINE_Y = 56;

    private static final int XP_BAR_WIDTH = 155;
    private static final int XP_BAR_HEIGHT = 4;
    private static final int XP_BAR_HOVER_PADDING = 2;
    private static final int LEVEL_UP_BUTTON_GAP = 4;
    private static final int POINTS_ICON_GAP = 2;

    private static final int SEPARATOR_X_LEFT = 9;
    private static final int SEPARATOR_X_RIGHT = 167;

    private static final int TEXT_HEIGHT = 8;

    private static final Identifier XP_BAR_BACKGROUND = Identifier.withDefaultNamespace("hud/experience_bar_background");
    private static final Identifier XP_BAR_PROGRESS = Identifier.withDefaultNamespace("hud/experience_bar_progress");

    private static final int PARCHMENT_OFFSET_X = 6;
    private static final int PARCHMENT_OFFSET_Y = 6;

    private static final int COLOR_TITLE = 0xFF3F3F3F;
    private static final int COLOR_HEADER = 0xFF5A5A5A;
    private static final int COLOR_NAME = 0xFF3F3F3F;
    private static final int COLOR_VALUE = 0xFF8B5A2B;
    private static final int COLOR_SEPARATOR = 0xFF8B7355;

    private int leftPos;
    private int topPos;
    private PlusButton levelUpButton;

    public LevelUpScreen() {
        super(Component.translatable("chronicles_leveling.screen.levels.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - IMAGE_WIDTH) / 2;
        this.topPos = (this.height - IMAGE_HEIGHT) / 2;

        addRenderableWidget(new ChroniclesTabBar(leftPos, topPos));

        // Vertically center the 10px button on the 8px text row, then nudge 1px up
        // so the glyph sits visually flush with the cap-height of "Lv. n".
        int levelUpY = topPos + LEVEL_LINE_Y - (ChroniclesSprites.BUTTON_H - TEXT_HEIGHT) / 2 - 1;
        // X is set in extractRenderState because it depends on the rendered Lv. n width.
        this.levelUpButton = new PlusButton(
                leftPos, levelUpY,
                Component.translatable("chronicles_leveling.stat.level"),
                this::canLevelUp,
                NetworkDispatcher::sendLevelUp);
        addRenderableWidget(this.levelUpButton);

        int statButtonX = leftPos + BUTTON_X_OFFSET;
        for (int i = 0; i < ModStats.ALL.size(); i++) {
            ModStats.Entry stat = ModStats.ALL.get(i);
            addRenderableWidget(new PlusButton(
                    statButtonX, rowButtonY(i),
                    Component.translatable("chronicles_leveling.stat." + stat.id()),
                    () -> canSpendOn(stat.id()),
                    () -> NetworkDispatcher.sendAllocateStat(stat.id())));
        }
    }

    private static int rowY(int rowIdx) {
        return FIRST_ROW_Y + rowIdx * ROW_HEIGHT;
    }

    private int rowButtonY(int rowIdx) {
        return topPos + rowY(rowIdx) + (ROW_CONTENT_H - ChroniclesSprites.BUTTON_H) / 2;
    }

    private boolean canLevelUp() {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        PlayerLevelData data = PlayerLevelManager.get(player);
        int maxLevel = Configs.SYNC.maxLevel.get();
        if (maxLevel > 0 && data.level() >= maxLevel) return false;
        return VanillaXp.availableExperiencePoints(player) >= LevelingCurve.xpToNext(data.level());
    }

    private boolean canSpendPoint() {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        return PlayerLevelManager.get(player).unspentPoints() > 0;
    }

    private boolean canSpendOn(String statId) {
        if (!canSpendPoint()) return false;
        int maxStatLevel = Configs.SYNC.maxStatLevel.get();
        if (maxStatLevel <= 0) return true;
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        // Mirrors the server check: cap the player's allocation only — external
        // attribute modifiers can push the total higher and shouldn't gate spending.
        return PlayerLevelManager.get(player).allocation(statId) < maxStatLevel;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ChroniclesTextures.PARCHMENT,
                leftPos + PARCHMENT_OFFSET_X, topPos + PARCHMENT_OFFSET_Y,
                0, 0,
                IMAGE_WIDTH, IMAGE_HEIGHT,
                256, 256
        );
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ChroniclesTextures.GUI,
                leftPos, topPos,
                0, 0,
                IMAGE_WIDTH, IMAGE_HEIGHT,
                256, 256
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        LocalPlayer player = Minecraft.getInstance().player;
        PlayerLevelData data = player == null ? PlayerLevelData.DEFAULT : PlayerLevelManager.get(player);
        int rung = LevelingCurve.xpToNext(data.level());
        int availableXp = player == null ? 0 : VanillaXp.availableExperiencePoints(player);

        renderHeader(graphics, player, data, rung, availableXp, mouseX, mouseY);
        renderSeparators(graphics);
        renderStatRows(graphics, player, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphicsExtractor graphics, LocalPlayer player, PlayerLevelData data, int rung, int availableXp, int mouseX, int mouseY) {
        renderTitle(graphics, mouseX, mouseY);
        renderName(graphics, player);
        renderLevelLine(graphics, player, data, rung, availableXp, mouseX, mouseY);
        renderXpBar(graphics, availableXp, rung, mouseX, mouseY);
        renderPointsLine(graphics, data.unspentPoints(), mouseX, mouseY);
    }

    private void renderTitle(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Component title = Component.translatable("chronicles_leveling.screen.levels.title");
        int textW = font.width(title);
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos + IMAGE_WIDTH / 2f, topPos + TITLE_Y);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.text(font, title, -textW / 2, 0, COLOR_TITLE, false);
        graphics.pose().popMatrix();

        int scaledW = (int) Math.ceil(textW * TITLE_SCALE);
        int scaledH = (int) Math.ceil(TEXT_HEIGHT * TITLE_SCALE);
        int x0 = leftPos + IMAGE_WIDTH / 2 - scaledW / 2;
        int y0 = topPos + TITLE_Y;
        if (isHovered(mouseX, mouseY, x0, y0, scaledW, scaledH)) {
            graphics.setComponentTooltipForNextFrame(font, buildLevelsTitleTooltip(), mouseX, mouseY);
        }
    }

    private void renderName(GuiGraphicsExtractor graphics, LocalPlayer player) {
        if (player == null) return;
        Component name = player.getName();
        int textW = font.width(name);
        int x = leftPos + (IMAGE_WIDTH - textW) / 2;
        graphics.text(font, name, x, topPos + NAME_LINE_Y, COLOR_NAME, false);
    }

    private void renderLevelLine(GuiGraphicsExtractor graphics, LocalPlayer player, PlayerLevelData data, int rung, int availableXp, int mouseX, int mouseY) {
        Component lvText = Component.translatable("chronicles_leveling.screen.levels.level_format", data.level());
        int textW = font.width(lvText);
        int totalW = textW + LEVEL_UP_BUTTON_GAP + ChroniclesSprites.BUTTON_W;
        int startX = leftPos + (IMAGE_WIDTH - totalW) / 2;
        int textY = topPos + LEVEL_LINE_Y;

        graphics.text(font, lvText, startX, textY, COLOR_HEADER, false);
        levelUpButton.setX(startX + textW + LEVEL_UP_BUTTON_GAP);

        if (isHovered(mouseX, mouseY, startX, textY, textW, TEXT_HEIGHT)) {
            graphics.setComponentTooltipForNextFrame(font,
                    List.of(Component.translatable("chronicles_leveling.tooltip.level.current")),
                    mouseX, mouseY);
        }

        int btnX = levelUpButton.getX();
        int btnY = levelUpButton.getY();
        if (isHovered(mouseX, mouseY, btnX, btnY, ChroniclesSprites.BUTTON_W, ChroniclesSprites.BUTTON_H)) {
            List<Component> lines = buildLevelUpButtonTooltip(player, data, rung, availableXp);
            if (!lines.isEmpty()) {
                graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
        }
    }

    private void renderXpBar(GuiGraphicsExtractor graphics, int xp, int rung, int mouseX, int mouseY) {
        int barX = leftPos + (IMAGE_WIDTH - XP_BAR_WIDTH) / 2;
        int barY = topPos + XP_BAR_Y;

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_BACKGROUND,
                barX, barY, XP_BAR_WIDTH, XP_BAR_HEIGHT);

        if (rung > 0 && xp > 0) {
            int progressW = (int) Math.min(XP_BAR_WIDTH, ((long) XP_BAR_WIDTH * xp) / rung);
            if (progressW > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_PROGRESS,
                        XP_BAR_WIDTH, XP_BAR_HEIGHT, 0, 0,
                        barX, barY, progressW, XP_BAR_HEIGHT);
            }
        }

        if (ProgressTooltip.isHovered(mouseX, mouseY, barX, barY, XP_BAR_WIDTH, XP_BAR_HEIGHT, XP_BAR_HOVER_PADDING)) {
            graphics.setTooltipForNextFrame(font, ProgressTooltip.build(xp, rung), mouseX, mouseY);
        }
    }

    private void renderPointsLine(GuiGraphicsExtractor graphics, int unspentPoints, int mouseX, int mouseY) {
        Component text = Component.translatable("chronicles_leveling.screen.levels.points_format", unspentPoints);
        int textW = font.width(text);
        int totalW = ChroniclesSprites.ICON_SIZE + POINTS_ICON_GAP + textW;
        int startX = leftPos + (IMAGE_WIDTH - totalW) / 2;
        int textY = topPos + POINTS_LINE_Y;
        // 9px icon biased 1px above the 8px text so their bottom edges align.
        int iconY = textY - 1;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ChroniclesTextures.GUI,
                startX, iconY,
                ChroniclesSprites.XP_U, ChroniclesSprites.XP_V,
                ChroniclesSprites.ICON_SIZE, ChroniclesSprites.ICON_SIZE,
                ChroniclesSprites.SHEET_SIZE, ChroniclesSprites.SHEET_SIZE
        );

        graphics.text(font, text,
                startX + ChroniclesSprites.ICON_SIZE + POINTS_ICON_GAP, textY,
                COLOR_HEADER, false);

        // Hover icon + label as one block; icon top is 1px above text.
        int hoverH = ChroniclesSprites.ICON_SIZE;
        if (isHovered(mouseX, mouseY, startX, iconY, totalW, hoverH)) {
            graphics.setComponentTooltipForNextFrame(font,
                    List.of(Component.translatable("chronicles_leveling.tooltip.points.description")),
                    mouseX, mouseY);
        }
    }

    private void renderSeparators(GuiGraphicsExtractor graphics) {
        int x0 = leftPos + SEPARATOR_X_LEFT;
        int x1 = leftPos + SEPARATOR_X_RIGHT;

        // Divider beneath the "Levels" title.
        drawHorizontalLine(graphics, x0, x1, topPos + HEADER_LINE_Y);

        // Top wrap line above the stat block.
        int topRowLineY = topPos + FIRST_ROW_Y - 2;
        drawHorizontalLine(graphics, x0, x1, topRowLineY);

        // Line after each row (last iteration draws the bottom wrap).
        int bottomRowLineY = topRowLineY;
        for (int i = 0; i < ModStats.ALL.size(); i++) {
            bottomRowLineY = topPos + rowY(i) + ROW_CONTENT_H + 1;
            drawHorizontalLine(graphics, x0, x1, bottomRowLineY);
        }

        // Vertical edges, top wrap to bottom wrap inclusive — frames the table.
        drawVerticalLine(graphics, x0, topRowLineY, bottomRowLineY);
        drawVerticalLine(graphics, x1 - 1, topRowLineY, bottomRowLineY);
    }

    private void drawHorizontalLine(GuiGraphicsExtractor graphics, int x0, int x1, int y) {
        graphics.fill(x0, y, x1, y + 1, COLOR_SEPARATOR);
    }

    private void drawVerticalLine(GuiGraphicsExtractor graphics, int x, int y0, int y1) {
        graphics.fill(x, y0, x + 1, y1 + 1, COLOR_SEPARATOR);
    }

    private void renderStatRows(GuiGraphicsExtractor graphics, LocalPlayer player, int mouseX, int mouseY) {
        for (int i = 0; i < ModStats.ALL.size(); i++) {
            ModStats.Entry stat = ModStats.ALL.get(i);
            AttributeInstance instance = player == null ? null : player.getAttribute(ModStats.get(stat.id()));
            int displayValue = instance == null ? 0 : (int) Math.floor(instance.getValue());

            renderRow(graphics, i, stat.id(), instance,
                    Component.translatable("chronicles_leveling.stat." + stat.id()),
                    Integer.toString(displayValue), displayValue,
                    mouseX, mouseY);
        }
    }

    private void renderRow(GuiGraphicsExtractor graphics, int rowIdx, String statId, AttributeInstance instance,
                           Component name, String value, int displayValue, int mouseX, int mouseY) {
        int y = topPos + rowY(rowIdx);
        int textY = y + (ROW_CONTENT_H - TEXT_HEIGHT) / 2;

        graphics.text(font, name, leftPos + NAME_X_OFFSET, textY, COLOR_NAME, false);

        int valueW = font.width(value);
        int valueLeft = leftPos + IMAGE_WIDTH - VALUE_RIGHT_MARGIN - valueW;
        graphics.text(font, value, valueLeft, textY, COLOR_VALUE, false);

        // Stat name/value text area — excluding the +button column on the left so that
        // hovering the button shows the button-specific tooltip instead. Mirrors the
        // AttributesScreen split: hovering the value column yields a base + modifier
        // breakdown; hovering the rest yields the per-point "grants" list.
        int hoverX0 = leftPos + NAME_X_OFFSET;
        int hoverX1 = leftPos + IMAGE_WIDTH - VALUE_RIGHT_MARGIN;
        if (isHovered(mouseX, mouseY, hoverX0, y, hoverX1 - hoverX0, ROW_CONTENT_H)) {
            if (instance != null && mouseX >= valueLeft) {
                List<Component> breakdown = AttributeLineRenderer.valueBreakdown(
                        instance.getAttribute(), instance.getBaseValue(), instance.getModifiers(), Optional.empty());
                if (!breakdown.isEmpty()) {
                    graphics.setComponentTooltipForNextFrame(font, breakdown, mouseX, mouseY);
                }
            } else {
                graphics.setComponentTooltipForNextFrame(font, buildStatTooltip(statId, name), mouseX, mouseY);
            }
        }

        int btnX = leftPos + BUTTON_X_OFFSET;
        int btnY = rowButtonY(rowIdx);
        if (isHovered(mouseX, mouseY, btnX, btnY, ChroniclesSprites.BUTTON_W, ChroniclesSprites.BUTTON_H)) {
            List<Component> lines = buildStatButtonTooltip(statId, instance, name, displayValue);
            if (!lines.isEmpty()) {
                graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
        }
    }

    private static boolean isHovered(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private List<Component> buildLevelsTitleTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("chronicles_leveling.tooltip.levels.description.spend")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.empty());
        lines.add(Component.translatable("chronicles_leveling.tooltip.levels.description.each")
                .withStyle(ChatFormatting.GRAY));
        int maxLevel = Configs.SYNC.maxLevel.get();
        if (maxLevel > 0) {
            lines.add(Component.translatable("chronicles_leveling.tooltip.levels.max_level", maxLevel)
                    .withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private List<Component> buildLevelUpButtonTooltip(LocalPlayer player, PlayerLevelData data, int rung, int availableXp) {
        int maxLevel = Configs.SYNC.maxLevel.get();
        if (maxLevel > 0 && data.level() >= maxLevel) return List.of();

        // Cost expressed in vanilla XP-levels, computed from the actual piecewise curve.
        // Absolute (depends only on `rung`, not the player's current XP bar) so both Spend
        // and Required keep a stable level/xp pair as the player gains or loses vanilla XP.
        int costInLevels = VanillaXp.getLevelForTotalXp(rung);
        String costStr = ProgressTooltip.formatAmount(rung);

        if (availableXp < rung) {
            return List.of(
                    Component.translatable("chronicles_leveling.tooltip.level_up.insufficient")
                            .withStyle(ChatFormatting.RED),
                    requiredLine(costInLevels, costStr).withStyle(ChatFormatting.GRAY));
        }

        return List.of(spendLine(costInLevels, costStr));
    }

    private static MutableComponent spendLine(int approxLevels, String costStr) {
        if (approxLevels >= 2) {
            return Component.translatable("chronicles_leveling.tooltip.level_up.format", approxLevels, costStr);
        }
        if (approxLevels == 1) {
            return Component.translatable("chronicles_leveling.tooltip.level_up.format.one", costStr);
        }
        return Component.translatable("chronicles_leveling.tooltip.level_up.format.no_levels", costStr);
    }

    private static MutableComponent requiredLine(int approxLevels, String costStr) {
        if (approxLevels >= 2) {
            return Component.translatable("chronicles_leveling.tooltip.level_up.required", approxLevels, costStr);
        }
        if (approxLevels == 1) {
            return Component.translatable("chronicles_leveling.tooltip.level_up.required.one", costStr);
        }
        return Component.translatable("chronicles_leveling.tooltip.level_up.required.no_levels", costStr);
    }

    private List<Component> buildStatTooltip(String statId, Component statName) {
        List<Component> lines = new ArrayList<>();
        int maxStatLevel = Configs.SYNC.maxStatLevel.get();
        if (maxStatLevel > 0) {
            lines.add(Component.translatable("chronicles_leveling.tooltip.stat.max_level", maxStatLevel)
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("chronicles_leveling.tooltip.stat.grants_header", statName)
                .withStyle(ChatFormatting.GRAY));

        boolean anyShown = false;
        for (StatModifierSpec spec : Configs.SYNC.getStatModifierSpecs(statId)) {
            if (spec.amountPerPoint.get() == 0.0) continue;
            lines.add(formatModifierLine(spec));
            anyShown = true;
        }
        if (!anyShown) {
            lines.add(Component.translatable("chronicles_leveling.tooltip.stat.no_modifiers", statName)
                    .withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    private List<Component> buildStatButtonTooltip(String statId, AttributeInstance instance, Component statName, int displayValue) {
        var player = Minecraft.getInstance().player;
        int maxStatLevel = Configs.SYNC.maxStatLevel.get();
        if (player != null && maxStatLevel > 0
                && PlayerLevelManager.get(player).allocation(statId) >= maxStatLevel) {
            return List.of();
        }
        if (!canSpendPoint()) {
            return List.of(Component.translatable("chronicles_leveling.tooltip.stat.no_points")
                    .withStyle(ChatFormatting.RED));
        }
        return List.of(Component.translatable(
                "chronicles_leveling.tooltip.stat.allocate.format",
                statName, displayValue, displayValue + 1));
    }

    private static final Identifier PER_POINT_MODIFIER_ID = ChroniclesLeveling.id("tooltip/per_point");

    private static Component formatModifierLine(StatModifierSpec spec) {
        Identifier targetId = spec.targetAttribute.get();
        Optional<? extends Holder<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(
                ResourceKey.create(Registries.ATTRIBUTE, targetId));
        if (holder.isEmpty()) {
            // Unregistered (e.g. modded attribute not loaded) — fall back to the raw id so
            // pack authors can still see what the spec points at.
            return Component.literal("? " + targetId);
        }
        AttributeModifier modifier = new AttributeModifier(
                PER_POINT_MODIFIER_ID, spec.amountPerPoint.get(), spec.operation.get());
        return AttributeLineRenderer.modifierComponent(holder.get(), modifier);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
