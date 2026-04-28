package dev.muon.chronicles_leveling.client.screen;

import dev.muon.chronicles_leveling.skill.PlayerSkillData;
import dev.muon.chronicles_leveling.skill.PlayerSkillManager;
import dev.muon.chronicles_leveling.skill.SkillCurve;
import dev.muon.chronicles_leveling.skill.Skills;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * "Skills" tab — twelve action-trained skill levels in a 6×2 grid.
 *
 * <p>Each cell carries the skill name in the top-left and the current integer
 * level in the top-right (both rendered at 0.75× to keep the long names from
 * crowding), with a thin XP bar across the cell's full inner width below.
 * There are no plus buttons here — skills gain progress through use, not
 * allocation.
 *
 * <p>Header: scaled "Skills" title with a divider line beneath. The table is
 * fully framed: top/bottom horizontal wraps, left/right vertical edges, and a
 * vertical divider down the middle.
 *
 * <p>Skill data is read live from {@link PlayerSkillManager} (synced via the
 * loader's attachment system) and the per-skill XP curve from
 * {@link SkillCurve} (fed by {@code ConfigSync.skillCurves}, also synced).
 *
 * <p>Sized 176×166 to line up with the inventory texture.
 */
public class SkillsScreen extends Screen {

    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 166;

    private static final int TITLE_Y = 12;
    private static final float TITLE_SCALE = 1.2f;
    private static final int HEADER_LINE_Y = 24;

    private static final int FIRST_ROW_Y = 38;
    private static final int ROW_HEIGHT = 20;          // pitch: 17 content + 3 separator (1 empty + 1 line + 1 empty)
    private static final int ROW_CONTENT_H = 17;

    // Inside-cell offsets (relative to the cell's top y / left x).
    private static final int CELL_NAME_X = 1;
    private static final int CELL_NAME_TOP = 3;
    private static final int CELL_BAR_TOP = 11;
    private static final int CELL_BAR_HEIGHT = 3;

    private static final float CELL_TEXT_SCALE = 0.75f;

    private static final int SEPARATOR_X_LEFT = 9;
    private static final int SEPARATOR_X_RIGHT = 167;
    private static final int COLUMN_DIVIDER_X = 87;

    private static final int LEFT_CELL_X0 = 13;
    private static final int LEFT_CELL_X1 = 84;
    private static final int RIGHT_CELL_X0 = 91;
    private static final int RIGHT_CELL_X1 = 162;

    private static final int PARCHMENT_OFFSET_X = 6;
    private static final int PARCHMENT_OFFSET_Y = 6;

    private static final int COLOR_TITLE = 0xFF3F3F3F;
    private static final int COLOR_NAME = 0xFF3F3F3F;
    private static final int COLOR_VALUE = 0xFF8B5A2B;
    private static final int COLOR_SEPARATOR = 0xFF8B7355;

    private static final Identifier XP_BAR_BACKGROUND = Identifier.withDefaultNamespace("hud/experience_bar_background");
    private static final Identifier XP_BAR_PROGRESS = Identifier.withDefaultNamespace("hud/experience_bar_progress");

    private int leftPos;
    private int topPos;

    public SkillsScreen() {
        super(Component.translatable("chronicles_leveling.screen.skills.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - IMAGE_WIDTH) / 2;
        this.topPos = (this.height - IMAGE_HEIGHT) / 2;
        addRenderableWidget(new ChroniclesTabBar(leftPos, topPos));
    }

    private static int rowY(int rowIdx) {
        return FIRST_ROW_Y + rowIdx * ROW_HEIGHT;
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
        renderTitle(graphics);
        renderSeparators(graphics);
        renderSkillCells(graphics, mouseX, mouseY);
    }

    private void renderTitle(GuiGraphicsExtractor graphics) {
        Component title = Component.translatable("chronicles_leveling.screen.skills.title");
        int textW = font.width(title);
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos + IMAGE_WIDTH / 2f, topPos + TITLE_Y);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE);
        graphics.text(font, title, -textW / 2, 0, COLOR_TITLE, false);
        graphics.pose().popMatrix();
    }

    private void renderSeparators(GuiGraphicsExtractor graphics) {
        int x0 = leftPos + SEPARATOR_X_LEFT;
        int x1 = leftPos + SEPARATOR_X_RIGHT;

        // Divider beneath the title.
        drawHorizontalLine(graphics, x0, x1, topPos + HEADER_LINE_Y);

        // Top wrap line above the table.
        int topRowLineY = topPos + FIRST_ROW_Y - 2;
        drawHorizontalLine(graphics, x0, x1, topRowLineY);

        // Line after each row (last iteration draws the bottom wrap).
        int bottomRowLineY = topRowLineY;
        for (int i = 0; i < Skills.LEFT_COL.size(); i++) {
            bottomRowLineY = topPos + rowY(i) + ROW_CONTENT_H + 1;
            drawHorizontalLine(graphics, x0, x1, bottomRowLineY);
        }

        // Vertical edges + middle divider, all spanning top wrap to bottom wrap inclusive.
        drawVerticalLine(graphics, x0, topRowLineY, bottomRowLineY);
        drawVerticalLine(graphics, x1 - 1, topRowLineY, bottomRowLineY);
        drawVerticalLine(graphics, leftPos + COLUMN_DIVIDER_X, topRowLineY, bottomRowLineY);
    }

    private void drawHorizontalLine(GuiGraphicsExtractor graphics, int x0, int x1, int y) {
        graphics.fill(x0, y, x1, y + 1, COLOR_SEPARATOR);
    }

    private void drawVerticalLine(GuiGraphicsExtractor graphics, int x, int y0, int y1) {
        graphics.fill(x, y0, x + 1, y1 + 1, COLOR_SEPARATOR);
    }

    private void renderSkillCells(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        PlayerSkillData data = player == null ? PlayerSkillData.DEFAULT : PlayerSkillManager.get(player);

        for (int i = 0; i < Skills.LEFT_COL.size(); i++) {
            int y = topPos + rowY(i);
            renderSkillCell(graphics, Skills.LEFT_COL.get(i), data,
                    leftPos + LEFT_CELL_X0, leftPos + LEFT_CELL_X1, y, mouseX, mouseY);
            renderSkillCell(graphics, Skills.RIGHT_COL.get(i), data,
                    leftPos + RIGHT_CELL_X0, leftPos + RIGHT_CELL_X1, y, mouseX, mouseY);
        }
    }

    private void renderSkillCell(GuiGraphicsExtractor graphics, String skillId, PlayerSkillData data,
                                 int cellX0, int cellX1, int cellY, int mouseX, int mouseY) {
        Component name = Component.translatable("chronicles_leveling.skill." + skillId);
        PlayerSkillData.SkillEntry entry = data.get(skillId);
        String levelText = Integer.toString(entry.level());
        int textY = cellY + CELL_NAME_TOP;

        // Name (left-aligned, scaled around its top-left anchor).
        graphics.pose().pushMatrix();
        graphics.pose().translate(cellX0 + CELL_NAME_X, textY);
        graphics.pose().scale(CELL_TEXT_SCALE, CELL_TEXT_SCALE);
        graphics.text(font, name, 0, 0, COLOR_NAME, false);
        graphics.pose().popMatrix();

        // Level (right-aligned, anchored at the cell's right edge so the scaled text
        // grows leftward from there).
        int levelW = font.width(levelText);
        graphics.pose().pushMatrix();
        graphics.pose().translate(cellX1, textY);
        graphics.pose().scale(CELL_TEXT_SCALE, CELL_TEXT_SCALE);
        graphics.text(font, levelText, -levelW, 0, COLOR_VALUE, false);
        graphics.pose().popMatrix();

        int barWidth = cellX1 - cellX0;
        int barY = cellY + CELL_BAR_TOP;
        int xpToNext = SkillCurve.xpToNext(skillId, entry.level());

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_BACKGROUND,
                cellX0, barY, barWidth, CELL_BAR_HEIGHT);
        if (xpToNext > 0 && entry.xp() > 0) {
            int progressW = (int) Math.min(barWidth, ((long) barWidth * entry.xp()) / xpToNext);
            if (progressW > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BAR_PROGRESS,
                        barWidth, CELL_BAR_HEIGHT, 0, 0,
                        cellX0, barY, progressW, CELL_BAR_HEIGHT);
            }
        }

        if (ProgressTooltip.isHovered(mouseX, mouseY, cellX0, barY, barWidth, CELL_BAR_HEIGHT)) {
            graphics.setTooltipForNextFrame(font,
                    ProgressTooltip.build(entry.xp(), xpToNext), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
