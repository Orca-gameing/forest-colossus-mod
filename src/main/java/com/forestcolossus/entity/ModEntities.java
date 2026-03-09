package com.forestcolossus.entity;

import com.forestcolossus.ForestColossusMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {
    
    public static final EntityType<ForestColossusEntity> FOREST_COLOSSUS = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(ForestColossusMod.MOD_ID, "forest_colossus"),
        FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ForestColossusEntity::new)
            .dimensions(EntityDimensions.fixed(6.0f, 13.0f))
            .trackRangeChunks(16)
            .build()
    );
    
    public static final EntityType<ThrownTreeEntity> THROWN_TREE = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(ForestColossusMod.MOD_ID, "thrown_tree"),
        FabricEntityTypeBuilder.<ThrownTreeEntity>create(SpawnGroup.MISC, ThrownTreeEntity::new)
            .dimensions(EntityDimensions.fixed(2.0f, 2.0f))
            .trackRangeChunks(8)
            .build()
    );
    
    public static void register() {
        ForestColossusMod.LOGGER.info("Registering entities for " + ForestColossusMod.MOD_ID);
    }
}
