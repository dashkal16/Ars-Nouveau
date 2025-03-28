package com.hollingsworth.arsnouveau.common.network;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.event.FamiliarSummonEvent;
import com.hollingsworth.arsnouveau.api.familiar.AbstractFamiliarHolder;
import com.hollingsworth.arsnouveau.api.familiar.IFamiliar;
import com.hollingsworth.arsnouveau.client.particle.ParticleUtil;
import com.hollingsworth.arsnouveau.common.potions.ModPotions;
import com.hollingsworth.arsnouveau.common.util.PortUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSummonFamiliar {

    String familiarID;
    int entityID;

    public PacketSummonFamiliar(String id, int entityID){
        this.familiarID = id;
        this.entityID = entityID;
    }

    //Decoder
    public PacketSummonFamiliar(PacketBuffer buf){
        familiarID = buf.readUtf(32767);
        entityID = buf.readInt();
    }

    //Encoder
    public void toBytes(PacketBuffer buf){
        buf.writeUtf(familiarID);
        buf.writeInt(entityID);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(()->{
            if(ctx.get().getSender() != null){
                AbstractFamiliarHolder familiarHolder = ArsNouveauAPI.getInstance().getFamiliarHolderMap().get(familiarID);
                Entity owner = ctx.get().getSender().level.getEntity(entityID);

                if(owner instanceof LivingEntity && ((LivingEntity) owner).hasEffect(ModPotions.FAMILIAR_SICKNESS_EFFECT)){
                    PortUtil.sendMessage(owner, new TranslationTextComponent("ars_nouveau.familiar.sickness"));
                    return;
                }

                IFamiliar familiarEntity = familiarHolder.getSummonEntity(owner.level);
                familiarEntity.setOwnerID(owner.getUUID());
                familiarEntity.getThisEntity().setPos(owner.getX(), owner.getY(), owner.getZ());

                FamiliarSummonEvent summonEvent = new FamiliarSummonEvent(familiarEntity.getThisEntity(), owner);
                MinecraftForge.EVENT_BUS.post(summonEvent);

                if(!summonEvent.isCanceled()) {
                    owner.level.addFreshEntity(familiarEntity.getThisEntity());
                    ParticleUtil.spawnPoof((ServerWorld) owner.level, familiarEntity.getThisEntity().blockPosition());
                    if (owner instanceof LivingEntity) {
                        ((LivingEntity) owner).addEffect(new EffectInstance(ModPotions.FAMILIAR_SICKNESS_EFFECT, 20 * 300, 0, false, false, true));
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);

    }
}
