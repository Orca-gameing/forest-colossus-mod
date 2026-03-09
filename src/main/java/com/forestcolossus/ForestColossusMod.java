package com.forestcolossus;

import com.forestcolossus.entity.ForestColossusEntity;
import com.forestcolossus.entity.ModEntities;
import com.forestcolossus.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForestColossusMod implements ModInitializer {
    public static final String MOD_ID = "forestcolossus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Forest Colossus Mod initializing...");
        
        // Register entities
        ModEntities.register();
        FabricDefaultAttributeRegistry.register(ModEntities.FOREST_COLOSSUS, ForestColossusEntity.createColossusAttributes());
        
        // Register items
        ModItems.registerItems();
        
        // Add spawn restrictions
        SpawnRestriction.register(ModEntities.FOREST_COLOSSUS, 
            SpawnLocationTypes.ON_GROUND,
            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            ForestColossusEntity::canSpawn);
        
        // Add to jungle biomes as rare spawn
        BiomeModifications.addSpawn(
            BiomeSelectors.includeByKey(
                BiomeKeys.JUNGLE,
                BiomeKeys.BAMBOO_JUNGLE,
                BiomeKeys.SPARSE_JUNGLE
            ),
            SpawnGroup.MONSTER,
            ModEntities.FOREST_COLOSSUS,
            1,  // Very rare weight
            1,  // Min group size
            1   // Max group size
        );
        
        LOGGER.info("Forest Colossus Mod initialized!");
    }
}
