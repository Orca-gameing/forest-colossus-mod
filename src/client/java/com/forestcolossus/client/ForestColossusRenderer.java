package com.forestcolossus.client;

import com.forestcolossus.entity.ForestColossusEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.List;

public class ForestColossusRenderer extends GeoEntityRenderer<ForestColossusEntity> {
    
    private static final Identifier GLOW_TEXTURE = Identifier.of("forestcolossus", "textures/entity/forest_colossus_glow.png");
    
    public ForestColossusRenderer(EntityRendererFactory.Context context) {
        super(context, new ForestColossusModel());
        this.shadowRadius = 4.0f;
        
        // Add glowing eyes layer
        addRenderLayer(new GlowingEyesLayer(this));
    }
    
    @Override
    public void render(ForestColossusEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
    
    @Override
    public void renderRecursively(MatrixStack poseStack, ForestColossusEntity entity, GeoBone bone, RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.renderRecursively(poseStack, entity, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        
        // Render held tree on right hand bone
        if (bone.getName().equals("right_hand") && entity.hasTree() && !isReRender) {
            renderHeldTree(poseStack, entity, bufferSource, packedLight);
        }
    }
    
    // Glowing eyes render layer
    private static class GlowingEyesLayer extends GeoRenderLayer<ForestColossusEntity> {
        public GlowingEyesLayer(GeoEntityRenderer<ForestColossusEntity> renderer) {
            super(renderer);
        }
        
        @Override
        public void render(MatrixStack poseStack, ForestColossusEntity entity, software.bernie.geckolib.cache.object.BakedGeoModel bakedModel, 
                         RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer, 
                         float partialTick, int packedLight, int packedOverlay) {
            
            // Only render glow at night
            if (!entity.shouldEyesGlow()) return;
            
            // Get the glow render layer with full brightness
            RenderLayer glowLayer = RenderLayer.getEyes(GLOW_TEXTURE);
            VertexConsumer glowBuffer = bufferSource.getBuffer(glowLayer);
            
            // Render with full brightness (15, 15 = max light)
            int fullBright = LightmapTextureManager.pack(15, 15);
            
            getRenderer().reRender(bakedModel, poseStack, bufferSource, entity, glowLayer, glowBuffer, partialTick, fullBright, packedOverlay, 0xFFFFFFFF);
        }
    }
    
    private void renderHeldTree(MatrixStack poseStack, ForestColossusEntity entity, VertexConsumerProvider bufferSource, int packedLight) {
        BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
        
        // Get the ACTUAL tree block types that were grabbed
        BlockState logBlock = entity.getHeldTreeBlock();
        BlockState leavesBlock = entity.getHeldLeafBlock();
        
        // If leaves are air or invalid, derive from log type
        if (leavesBlock == null || leavesBlock.isAir()) {
            leavesBlock = getMatchingLeaves(logBlock);
        }
        
        // Get the actual tree structure
        List<int[]> logPositions = entity.getLogPositions();
        List<int[]> leafPositions = entity.getLeafPositions();
        
        poseStack.push();
        
        // Rotate 90 degrees to hold horizontally (tree pointing forward from hand)
        poseStack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(90));
        
        // Calculate the center of the LOG positions (not leaves) to center the trunk in hand
        if (!logPositions.isEmpty()) {
            // Find the bounds of logs only
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
            
            // Center of the trunk (in original coordinates before rotation)
            float centerX = (minX + maxX) / 2.0f;
            float centerY = (minY + maxY) / 2.0f;
            float centerZ = (minZ + maxZ) / 2.0f;
            
            // After 90 degree X rotation:
            // Original Y axis becomes -Z axis
            // Original Z axis becomes Y axis
            // So to center: translate by (-centerX, -centerZ, centerY) then adjust for block center
            poseStack.translate(-centerX - 0.5f, -centerZ - 0.5f, centerY + 0.5f);
            
            // Render actual captured tree structure
            for (int[] pos : logPositions) {
                renderBlock(poseStack, bufferSource, blockRenderManager, logBlock, pos[0], pos[1], pos[2], packedLight);
            }
            for (int[] pos : leafPositions) {
                renderBlock(poseStack, bufferSource, blockRenderManager, leavesBlock, pos[0], pos[1], pos[2], packedLight);
            }
        } else {
            // Fallback: simple tree shape centered at hand
            poseStack.translate(-0.5f, -0.5f, 2.5f);
            for (int y = 0; y < 5; y++) {
                renderBlock(poseStack, bufferSource, blockRenderManager, logBlock, 0, y, 0, packedLight);
            }
            renderBlock(poseStack, bufferSource, blockRenderManager, leavesBlock, 0, 5, 0, packedLight);
            renderBlock(poseStack, bufferSource, blockRenderManager, leavesBlock, 1, 4, 0, packedLight);
            renderBlock(poseStack, bufferSource, blockRenderManager, leavesBlock, -1, 4, 0, packedLight);
            renderBlock(poseStack, bufferSource, blockRenderManager, leavesBlock, 0, 4, 1, packedLight);
            renderBlock(poseStack, bufferSource, blockRenderManager, leavesBlock, 0, 4, -1, packedLight);
        }
        
        poseStack.pop();
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
    
    private void renderBlock(MatrixStack matrices, VertexConsumerProvider vertexConsumers, BlockRenderManager blockRenderManager, BlockState state, float x, float y, float z, int light) {
        matrices.push();
        matrices.translate(x, y, z);
        
        // Use renderBlockAsEntity for better leaf rendering
        blockRenderManager.renderBlockAsEntity(state, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
        
        matrices.pop();
    }
}
