package com.hollingsworth.arsnouveau.api.familiar;

import com.hollingsworth.arsnouveau.ArsNouveau;
import com.hollingsworth.arsnouveau.api.util.NBTUtil;
import com.hollingsworth.arsnouveau.common.capability.SerializableCapabilityProvider;
import com.hollingsworth.arsnouveau.common.network.Networking;
import com.hollingsworth.arsnouveau.common.network.PacketSyncFamiliars;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.hollingsworth.arsnouveau.setup.InjectionUtil.Null;

public class FamiliarCap implements IFamiliarCap {

    private Set<String> ownedFamiliars = new HashSet<>();

    private final LivingEntity livingEntity;

    public FamiliarCap(@Nullable final LivingEntity entity){
        this.livingEntity = entity;
    }

    @Override
    public boolean unlockFamiliar(String holderID) {
        return ownedFamiliars.add(holderID);
    }

    @Override
    public boolean ownsFamiliar(String holderID) {
        return ownedFamiliars.contains(holderID);
    }

    @Override
    public Collection<String> getUnlockedFamiliars() {
        return ownedFamiliars;
    }

    @Override
    public void setUnlockedFamiliars(Collection<String> familiars) {
        this.ownedFamiliars = new HashSet<>(familiars);
    }

    @Override
    public boolean removeFamiliar(String holderID) {
        return this.ownedFamiliars.remove(holderID);
    }

    @CapabilityInject(IFamiliarCap.class)
    public static final Capability<IFamiliarCap> FAMILIAR_CAPABILITY = Null();


    public static final Direction DEFAULT_FACING = null;


    public static final ResourceLocation ID = new ResourceLocation(ArsNouveau.MODID, "familiar");

    public static void register(){

        CapabilityManager.INSTANCE.register(IFamiliarCap.class, new Capability.IStorage<IFamiliarCap>() {
            @Nullable
            @Override
            public INBT writeNBT(Capability<IFamiliarCap> capability, IFamiliarCap instance, Direction side) {
                CompoundNBT tag = new CompoundNBT();
                FamiliarCap.serializeFamiliars(tag, instance);
                return tag;
            }

            @Override
            public void readNBT(Capability<IFamiliarCap> capability, IFamiliarCap instance, Direction side, INBT nbt) {
                if(!(nbt instanceof CompoundNBT))
                    return;
                CompoundNBT tag = (CompoundNBT)nbt;
                instance.setUnlockedFamiliars(NBTUtil.readStrings(tag, "fam"));
            }
        }, () -> new FamiliarCap(null));
        System.out.println("Finished Registering FamiliarCap");
    }

    public static void serializeFamiliars(CompoundNBT tag, IFamiliarCap cap){
        NBTUtil.writeStrings(tag, "fam", cap.getUnlockedFamiliars());
    }

    public static List<String> deserializeFamiliars(CompoundNBT tag){
        return NBTUtil.readStrings(tag, "fam");
    }

    /**
     * Get the {@link IFamiliarCap} from the specified entity.
     *
     * @param entity The entity
     * @return A lazy optional containing the IMana, if any
     */
    public static LazyOptional<IFamiliarCap> getFamiliarCap(final LivingEntity entity){
        return entity.getCapability(FAMILIAR_CAPABILITY, DEFAULT_FACING);
    }

    public static ICapabilityProvider createProvider(final IFamiliarCap familiarCap) {
        return new SerializableCapabilityProvider<>(FAMILIAR_CAPABILITY, DEFAULT_FACING, familiarCap);
    }

    /**
     * Event handler for the {@link IFamiliarCap} capability.
     */
    @SuppressWarnings("unused")
    @Mod.EventBusSubscriber(modid = ArsNouveau.MODID)
    private static class EventHandler {

        /**
         * Attach the {@link IFamiliarCap} capability to all living entities.
         *
         * @param event The event
         */
        @SubscribeEvent
        public static void attachCapabilities(final AttachCapabilitiesEvent<Entity> event) {

            if (event.getObject() instanceof PlayerEntity) {
                final FamiliarCap familiarCap = new FamiliarCap((LivingEntity) event.getObject());
                event.addCapability(ID, createProvider(familiarCap));
            }
        }

        /**
         * Copy the player's mana when they respawn after dying or returning from the end.
         *
         * @param event The event
         */
        @SubscribeEvent
        public static void playerClone(final PlayerEvent.Clone event) {
            getFamiliarCap(event.getOriginal()).ifPresent(oldFamiliarCap -> getFamiliarCap(event.getPlayer()).ifPresent(newFamiliarCap -> {
                newFamiliarCap.setUnlockedFamiliars(oldFamiliarCap.getUnlockedFamiliars());
                FamiliarCap.syncFamiliars(event.getPlayer());
            }));
        }

        @SubscribeEvent
        public static void onPlayerLoginEvent(PlayerEvent.PlayerLoggedInEvent event) {
            if(event.getPlayer() instanceof ServerPlayerEntity){
                FamiliarCap.syncFamiliars(event.getPlayer());
            }
        }

        @SubscribeEvent
        public static void respawnEvent(PlayerEvent.PlayerRespawnEvent event) {
            if(event.getPlayer() instanceof ServerPlayerEntity)
                FamiliarCap.syncFamiliars(event.getPlayer());
        }


        @SubscribeEvent
        public static void onPlayerStartTrackingEvent(PlayerEvent.StartTracking event) {
            if (event.getTarget() instanceof PlayerEntity && event.getPlayer() instanceof ServerPlayerEntity) {
                FamiliarCap.syncFamiliars(event.getPlayer());
            }
        }

        @SubscribeEvent
        public static void onPlayerDimChangedEvent(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getPlayer() instanceof ServerPlayerEntity)
                FamiliarCap.syncFamiliars(event.getPlayer());
        }
    }

    public static void syncFamiliars(PlayerEntity player){
        IFamiliarCap cap = FamiliarCap.getFamiliarCap(player).orElse(new FamiliarCap(player));
        CompoundNBT tag = new CompoundNBT();
        FamiliarCap.serializeFamiliars(tag, cap);
        Networking.sendToPlayer(new PacketSyncFamiliars(tag), player);
    }
}
