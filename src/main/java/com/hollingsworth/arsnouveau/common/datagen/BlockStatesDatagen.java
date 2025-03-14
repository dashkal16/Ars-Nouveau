package com.hollingsworth.arsnouveau.common.datagen;

import com.hollingsworth.arsnouveau.ArsNouveau;
import com.hollingsworth.arsnouveau.common.lib.LibBlockNames;
import com.hollingsworth.arsnouveau.setup.BlockRegistry;
import net.minecraft.block.Block;
import net.minecraft.data.DataGenerator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

public class BlockStatesDatagen extends BlockStateProvider {

    public BlockStatesDatagen(DataGenerator gen, String modid, ExistingFileHelper exFileHelper) {
        super(gen, modid, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
//        simpleBlock(BlockRegistry.ARCANE_STONE, new ModelFile.UncheckedModelFile("ars_nouveau:block/arcane_ore"));
        registerNormalCube(BlockRegistry.ARCANE_STONE, LibBlockNames.ARCANE_STONE);
        registerNormalCube(BlockRegistry.ARCANE_BRICKS, LibBlockNames.ARCANE_BRICKS);
        registerNormalCube(BlockRegistry.AB_ALTERNATE, LibBlockNames.AB_ALTERNATE);
        registerNormalCube(BlockRegistry.AB_BASKET, LibBlockNames.AB_BASKET);
        registerNormalCube(BlockRegistry.AB_HERRING, LibBlockNames.AB_HERRING);
        registerNormalCube(BlockRegistry.AB_MOSAIC, LibBlockNames.AB_MOSAIC);

        registerNormalCube(BlockRegistry.AB_CLOVER, LibBlockNames.AB_CLOVER);
        registerNormalCube(BlockRegistry.AB_SMOOTH, LibBlockNames.AB_SMOOTH);
        registerNormalCube(BlockRegistry.AB_SMOOTH_SLAB, LibBlockNames.AB_SMOOTH_SLAB);
        registerNormalCube(BlockRegistry.MANA_GEM_BLOCK, LibBlockNames.MANA_GEM_BLOCK);

        registerNormalCube(BlockRegistry.AB_SMOOTH_BASKET, LibBlockNames.AB_SMOOTH_BASKET);
        registerNormalCube(BlockRegistry.AB_SMOOTH_CLOVER, LibBlockNames.AB_SMOOTH_CLOVER);
        registerNormalCube(BlockRegistry.AB_SMOOTH_HERRING, LibBlockNames.AB_SMOOTH_HERRING);
        registerNormalCube(BlockRegistry.AB_SMOOTH_MOSAIC, LibBlockNames.AB_SMOOTH_MOSAIC);
        registerNormalCube(BlockRegistry.AB_SMOOTH_ALTERNATING, LibBlockNames.AB_SMOOTH_ALTERNATING);
        registerNormalCube(BlockRegistry.AB_SMOOTH_ASHLAR, LibBlockNames.AB_SMOOTH_ASHLAR);

        registerNormalCube(BlockRegistry.AS_GOLD_ALT, LibBlockNames.AS_GOLD_ALT);
        registerNormalCube(BlockRegistry.AS_GOLD_ASHLAR, LibBlockNames.AS_GOLD_ASHLAR);
        registerNormalCube(BlockRegistry.AS_GOLD_BASKET, LibBlockNames.AS_GOLD_BASKET);
        registerNormalCube(BlockRegistry.AS_GOLD_CLOVER, LibBlockNames.AS_GOLD_CLOVER);
        registerNormalCube(BlockRegistry.AS_GOLD_HERRING, LibBlockNames.AS_GOLD_HERRING);
        registerNormalCube(BlockRegistry.AS_GOLD_MOSAIC, LibBlockNames.AS_GOLD_MOSAIC);
        registerNormalCube(BlockRegistry.AS_GOLD_SLAB, LibBlockNames.AS_GOLD_SLAB);
        registerNormalCube(BlockRegistry.AS_GOLD_STONE, LibBlockNames.AS_GOLD_STONE);
    }

    public void registerNormalCube(Block block, String registry){
        buildNormalCube(registry);
        simpleBlock(block, getUncheckedModel(registry));
    }

    public static ModelFile getUncheckedModel(String registry){
        return new ModelFile.UncheckedModelFile("ars_nouveau:block/" + registry);
    }

    public void buildNormalCube(String registryName){
        this.models().getBuilder(registryName).parent(new ModelFile.UncheckedModelFile("block/cube_all")).texture("all",getBlockLoc(registryName));
    }

    public ResourceLocation getBlockLoc(String registryName){
        return new ResourceLocation(ArsNouveau.MODID, "blocks" + "/" +registryName);
    }
}
