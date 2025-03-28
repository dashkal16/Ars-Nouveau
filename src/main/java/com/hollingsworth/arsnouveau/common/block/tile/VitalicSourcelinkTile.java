package com.hollingsworth.arsnouveau.common.block.tile;

import com.hollingsworth.arsnouveau.ArsNouveau;
import com.hollingsworth.arsnouveau.api.entity.IDispellable;
import com.hollingsworth.arsnouveau.api.entity.ISummon;
import com.hollingsworth.arsnouveau.api.mana.SourcelinkEventQueue;
import com.hollingsworth.arsnouveau.client.particle.ParticleUtil;
import com.hollingsworth.arsnouveau.setup.BlockRegistry;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ArsNouveau.MODID)
public class VitalicSourcelinkTile extends SourcelinkTile{
    public VitalicSourcelinkTile(TileEntityType<?> tileEntityTypeIn) {
        super(tileEntityTypeIn);
    }

    // Test for a quark tag that has disabled baby growth.
    private static final String TAG_POISONED = "quark:poison_potato_applied";

    public VitalicSourcelinkTile(){
        super(BlockRegistry.VITALIC_TILE);
    }

    @Override
    public int getMaxMana() {
        return 2500;
    }

    @Override
    public void tick() {
        super.tick();
        if(!level.isClientSide && level.getGameTime() % 60 == 0){
            for(AnimalEntity entity : level.getLoadedEntitiesOfClass(AnimalEntity.class, new AxisAlignedBB(worldPosition).inflate(6))){
                if(entity.isBaby()){
                    if(entity.getAge() < 0){
                        if(ModList.get().isLoaded("quark") && entity.getPersistentData().contains(TAG_POISONED)){
                            return;
                        }
                        entity.setAge(Math.min(0,entity.getAge() + 500));
                        this.addMana(10);
                        ParticleUtil.spawnFollowProjectile(level, entity.blockPosition(), this.worldPosition);
                    }
                }

            }
        }
    }

    @SubscribeEvent
    public static void babySpawnEvent(BabyEntitySpawnEvent e) {
        int mana = 600;
        SourcelinkEventQueue.addManaEvent(e.getParentA().level, VitalicSourcelinkTile.class, mana, e, e.getParentA().blockPosition());
    }

    @SubscribeEvent
    public static void livingDeath(LivingDeathEvent e) {
        if(e.getEntityLiving().level.isClientSide || e.getEntity() instanceof IDispellable || e.getEntity() instanceof ISummon)
            return;
        int mana = 200;
        SourcelinkEventQueue.addManaEvent(e.getEntityLiving().level, VitalicSourcelinkTile.class, mana, e, e.getEntityLiving().blockPosition());
    }

    @Override
    public boolean usesEventQueue() {
        return true;
    }
}
