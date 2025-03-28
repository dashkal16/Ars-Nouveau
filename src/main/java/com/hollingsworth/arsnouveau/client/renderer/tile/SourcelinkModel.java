package com.hollingsworth.arsnouveau.client.renderer.tile;

import com.hollingsworth.arsnouveau.ArsNouveau;
import com.hollingsworth.arsnouveau.common.block.tile.SourcelinkTile;
import net.minecraft.util.ResourceLocation;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class SourcelinkModel<T extends SourcelinkTile> extends AnimatedGeoModel<SourcelinkTile> {

    public ResourceLocation modelLocation;
    public ResourceLocation textLoc;
    public ResourceLocation animationLoc = new ResourceLocation(ArsNouveau.MODID , "animations/volcanic_sourcelink_animations.json");

    public SourcelinkModel(String name){
        this.modelLocation = new ResourceLocation(ArsNouveau.MODID , "geo/" + name + "_sourcelink.geo.json");
        this.textLoc = new ResourceLocation(ArsNouveau.MODID, "textures/blocks/" + name + "_sourcelink.png");
    }

    @Override
    public ResourceLocation getModelLocation(SourcelinkTile agronomicSourcelink) {
        return modelLocation;
    }

    @Override
    public ResourceLocation getTextureLocation(SourcelinkTile agronomicSourcelink) {
        return textLoc;
    }

    @Override
    public ResourceLocation getAnimationFileLocation(SourcelinkTile agronomicSourcelink) {
        return animationLoc;
    }
}
