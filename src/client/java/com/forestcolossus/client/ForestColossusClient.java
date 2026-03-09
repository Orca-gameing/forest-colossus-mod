package com.forestcolossus.client;

import com.forestcolossus.ForestColossusMod;
import com.forestcolossus.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

@Environment(EnvType.CLIENT)
public class ForestColossusClient implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        ForestColossusMod.LOGGER.info("Forest Colossus Client initializing...");
        
        // Register GeckoLib renderers
        EntityRendererRegistry.register(ModEntities.FOREST_COLOSSUS, ForestColossusRenderer::new);
        EntityRendererRegistry.register(ModEntities.THROWN_TREE, ThrownTreeRenderer::new);
        
        ForestColossusMod.LOGGER.info("Forest Colossus Client initialized!");
    }
}
