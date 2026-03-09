package com.forestcolossus.client;

import com.forestcolossus.ForestColossusMod;
import com.forestcolossus.entity.ForestColossusEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class ForestColossusModel extends GeoModel<ForestColossusEntity> {
    
    @Override
    public Identifier getModelResource(ForestColossusEntity entity) {
        return Identifier.of(ForestColossusMod.MOD_ID, "geo/forest_colossus.geo.json");
    }
    
    @Override
    public Identifier getTextureResource(ForestColossusEntity entity) {
        return Identifier.of(ForestColossusMod.MOD_ID, "textures/entity/forest_colossus.png");
    }
    
    @Override
    public Identifier getAnimationResource(ForestColossusEntity entity) {
        return Identifier.of(ForestColossusMod.MOD_ID, "animations/forest_colossus.animation.json");
    }
}
