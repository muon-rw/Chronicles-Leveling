package dev.muon.chronicles_leveling.client.mining;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.muon.chronicles_leveling.ChroniclesLeveling;
import dev.muon.chronicles_leveling.client.AbilityWindowStoreClient;
import dev.muon.chronicles_leveling.skill.ability.runtime.AbilityWindowStore;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Map;

/**
 * Draws the Vein Sight ore boxes. Uses a custom lines render type whose depth test always passes, so the outlines
 * show through terrain. Called from each loader's after-translucent-terrain render hook.
 */
public final class VeinSightRenderer {

    private VeinSightRenderer() {}

    private static final VoxelShape BLOCK = Shapes.block();
    private static final float LINE_WIDTH = 2.0f;

    private static RenderType linesNoDepth;

    private static RenderType linesNoDepth() {
        if (linesNoDepth == null) {
            RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(ChroniclesLeveling.id("pipeline/lines_no_depth"))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .build();
            linesNoDepth = RenderType.create("chronicles_leveling/lines_no_depth",
                    RenderSetup.builder(pipeline).createRenderSetup());
        }
        return linesNoDepth;
    }

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 cameraPos, long gameTime) {
        if (!AbilityWindowStoreClient.isActive(AbilityWindowStore.WindowKind.VEIN_SIGHT, gameTime)) {
            return;
        }
        Map<Integer, List<BlockPos>> buckets = VeinSightScanner.buckets();
        if (buckets.isEmpty()) {
            return;
        }
        RenderType type = linesNoDepth();
        VertexConsumer consumer = bufferSource.getBuffer(type);
        for (Map.Entry<Integer, List<BlockPos>> entry : buckets.entrySet()) {
            int color = entry.getKey();
            for (BlockPos pos : entry.getValue()) {
                ShapeRenderer.renderShape(poseStack, consumer, BLOCK,
                        pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z,
                        color, LINE_WIDTH);
            }
        }
        bufferSource.endBatch(type);
    }
}
