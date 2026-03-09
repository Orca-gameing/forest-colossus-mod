package com.forestcolossus.client;

import com.forestcolossus.entity.ThrownTreeEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.List;

@Environment(EnvType.CLIENT)
public class ThrownTreeRenderer extends EntityRenderer<ThrownTreeEntity> {
    
    private final BlockRenderManager blockRenderManager;
    
    public ThrownTreeRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.blockRenderManager = context.getBlockRenderManager();
    }
    
    @Override
    public void render(ThrownTreeEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        
        // Spinning rotation as it flies
        float rotation = (entity.treeAge + tickDelta) * 15f;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation * 0.5f));
        
        // Get the block types
        BlockState logBlock = entity.getTreeBlock();
        BlockState leavesBlock = entity.getLeafBlock();
        
        // If leaves are air or invalid, derive from log type
        if (leavesBlock == null || leavesBlock.isAir()) {
            leavesBlock = getMatchingLeaves(logBlock);
        }
        
        // Get the ACTUAL tree structure
        List<int[]> logPositions = entity.getLogPositions();
        List<int[]> leafPositions = entity.getLeafPositions();
        
        if (!logPositions.isEmpty()) {
            // Calculate center of LOG positions to center the tree
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            
            for (int[] pos : logPositions) {
                minX = Math.min(minX, pos[0]);
                maxX = Math.max(maxX, pos[0]);
                minY = Math.min(minY, pos[1]);
                maxY = Math.max(maxY, pos[1]);
                minZ = Math.min(minZ, pos[2]);
                maxZ = Math.max(maxZ, pos[2]);
            }
            
            // Center of the trunk
            float centerX = (minX + maxX) / 2.0f + 0.5f;
            float centerY = (minY + maxY) / 2.0f + 0.5f;
            float centerZ = (minZ + maxZ) / 2.0f + 0.5f;
            
            // Offset so center of trunk is at entity position
            matrices.translate(-centerX, -centerY, -centerZ);
            
            // Render the ACTUAL captured tree structure
            for (int[] pos : logPositions) {
                renderBlock(matrices, vertexConsumers, logBlock, pos[0], pos[1], pos[2], light);
            }
            for (int[] pos : leafPositions) {
                renderBlock(matrices, vertexConsumers, leavesBlock, pos[0], pos[1], pos[2], light);
            }
        } else {
            // Fallback: simple tree shape centered
            matrices.translate(-0.5, -2, -0.5);
            for (int y = 0; y < 4; y++) {
                renderBlock(matrices, vertexConsumers, logBlock, 0, y, 0, light);
            }
            renderBlock(matrices, vertexConsumers, leavesBlock, 0, 4, 0, light);
            renderBlock(matrices, vertexConsumers, leavesBlock, 1, 3, 0, light);
            renderBlock(matrices, vertexConsumers, leavesBlock, -1, 3, 0, light);
            renderBlock(matrices, vertexConsumers, leavesBlock, 0, 3, 1, light);
            renderBlock(matrices, vertexConsumers, leavesBlock, 0, 3, -1, light);
        }
        
        matrices.pop();
        
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
    
    private BlockState getMatchingLeaves(BlockState logBlock) {
        if (logBlock == null) return Blocks.OAK_LEAVES.getDefaultState();
        
        String blockName = logBlock.getBlock().getTranslationKey().toLowerCase();
        
        if (blockName.contains("dark_oak")) {
            return Blocks.DARK_OAK_LEAVES.getDefaultState();
        } else if (blockName.contains("spruce")) {
            return Blocks.SPRUCE_LEAVES.getDefaultState();
        } else if (blockName.contains("birch")) {
            return Blocks.BIRCH_LEAVES.getDefaultState();
        } else if (blockName.contains("jungle")) {
            return Blocks.JUNGLE_LEAVES.getDefaultState();
        } else if (blockName.contains("acacia")) {
            return Blocks.ACACIA_LEAVES.getDefaultState();
        } else if (blockName.contains("mangrove")) {
            return Blocks.MANGROVE_LEAVES.getDefaultState();
        } else if (blockName.contains("cherry")) {
            return Blocks.CHERRY_LEAVES.getDefaultState();
        } else if (blockName.contains("oak")) {
            return Blocks.OAK_LEAVES.getDefaultState();
        }
        
        return Blocks.OAK_LEAVES.getDefaultState();
    }
    
    private void renderBlock(MatrixStack matrices, VertexConsumerProvider vertexConsumers, BlockState state, float x, float y, float z, int light) {
        matrices.push();
        matrices.translate(x, y, z);
        
        // Use renderBlockAsEntity for better leaf rendering
        blockRenderManager.renderBlockAsEntity(state, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
        
        matrices.pop();
    }
    
    @Override
    public Identifier getTexture(ThrownTreeEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
