package com.hollingsworth.arsnouveau.common.block.tile;

import com.hollingsworth.arsnouveau.api.client.ITooltipProvider;
import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.api.mana.AbstractManaTile;
import com.hollingsworth.arsnouveau.api.util.BlockUtil;
import com.hollingsworth.arsnouveau.api.util.NBTUtil;
import com.hollingsworth.arsnouveau.client.particle.ParticleUtil;
import com.hollingsworth.arsnouveau.common.items.DominionWand;
import com.hollingsworth.arsnouveau.common.util.PortUtil;
import com.hollingsworth.arsnouveau.setup.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ArcaneRelayTile extends AbstractManaTile implements ITooltipProvider, IWandable, IAnimatable {

    public ArcaneRelayTile() {
        super(BlockRegistry.ARCANE_RELAY_TILE);
    }

    public ArcaneRelayTile(TileEntityType<?> type){
        super(type);
    }

    public BlockPos getToPos() {
        return toPos;
    }

    public void setToPos(BlockPos toPos) {
        this.toPos = toPos;
    }

    public BlockPos getFromPos() {
        return fromPos;
    }

    public void setFromPos(BlockPos fromPos) {
        this.fromPos = fromPos;
    }

    private BlockPos toPos;
    private BlockPos fromPos;


    public boolean setTakeFrom(BlockPos pos){
        if(BlockUtil.distanceFrom(pos, this.worldPosition) > getMaxDistance()){
            return false;
        }
        this.fromPos = pos;
        update();
        return true;
    }

    public boolean setSendTo(BlockPos pos ){
        if(BlockUtil.distanceFrom(pos, this.worldPosition) > getMaxDistance()){
            return false;
        }
        this.toPos = pos;
        update();
        return true;
    }

    public int getMaxDistance(){
        return 30;
    }

    public void clearPos(){
        this.toPos = null;
        this.fromPos = null;
        update();
    }

    @Override
    public int getTransferRate() {
        return 1000;
    }

    @Override
    public int getMaxMana() {
        return 1000;
    }

    public boolean closeEnough(BlockPos pos){
        return BlockUtil.distanceFrom(pos, this.worldPosition) <= getMaxDistance();
    }

    @Override
    public void onFinishedConnectionFirst(@Nullable BlockPos storedPos, @Nullable LivingEntity storedEntity, PlayerEntity playerEntity) {
        if(storedPos == null || level.isClientSide)
            return;
        // Let relays take from us, no action needed.
        if(this.setSendTo(storedPos.immutable())) {
            PortUtil.sendMessage(playerEntity, new TranslationTextComponent("ars_nouveau.connections.send", DominionWand.getPosString(storedPos)));
            ParticleUtil.beam(storedPos, worldPosition, level);
        }else{
            PortUtil.sendMessage(playerEntity, new TranslationTextComponent("ars_nouveau.connections.fail"));
        }
    }

    @Override
    public void onFinishedConnectionLast(@Nullable BlockPos storedPos, @Nullable LivingEntity storedEntity, PlayerEntity playerEntity) {
        if(storedPos == null)
            return;
        if(level.getBlockEntity(storedPos) instanceof ArcaneRelayTile)
            return;
        if(this.setTakeFrom(storedPos.immutable())) {
            PortUtil.sendMessage(playerEntity, new TranslationTextComponent("ars_nouveau.connections.take", DominionWand.getPosString(storedPos)));
        }else{
            PortUtil.sendMessage(playerEntity, new TranslationTextComponent("ars_nouveau.connections.fail"));
        }
    }

    @Override
    public void onWanded(PlayerEntity playerEntity) {
        this.clearPos();
        PortUtil.sendMessage(playerEntity,new TranslationTextComponent("ars_nouveau.connections.cleared"));
    }


    @Override
    public void tick() {
        if(level.isClientSide){
            return;
        }
        if(level.getGameTime() % 20 != 0)
            return;

        if(fromPos != null){
            // Block has been removed
            if(!(level.getBlockEntity(fromPos) instanceof AbstractManaTile)){
                fromPos = null;
                update();
                return;
            }else if(level.getBlockEntity(fromPos) instanceof AbstractManaTile){
                // Transfer mana fromPos to this
                AbstractManaTile fromTile = (AbstractManaTile) level.getBlockEntity(fromPos);
                if(transferMana(fromTile, this) > 0){
                    update();
                    ParticleUtil.spawnFollowProjectile(level, fromPos, worldPosition);
                }
            }
        }

        if(toPos == null)
            return;
        if(!(level.getBlockEntity(toPos) instanceof AbstractManaTile)){
            toPos = null;
            update();
            return;
        }
        AbstractManaTile toTile = (AbstractManaTile) this.level.getBlockEntity(toPos);
        if(transferMana(this, toTile) > 0){
            ParticleUtil.spawnFollowProjectile(level, worldPosition, toPos);
        }
    }

    @Override
    public void load(BlockState state, CompoundNBT tag) {
        if(NBTUtil.hasBlockPos(tag, "to")){
            this.toPos = NBTUtil.getBlockPos(tag, "to");
        }else{
            toPos = null;
        }
        if(NBTUtil.hasBlockPos(tag, "from")){
            this.fromPos = NBTUtil.getBlockPos(tag, "from");
        }else{
            fromPos = null;
        }
        super.load(state, tag);
    }

    @Override
    public CompoundNBT save(CompoundNBT tag) {
        if(toPos != null) {
            NBTUtil.storeBlockPos(tag, "to", toPos);
        }else{
            NBTUtil.removeBlockPos(tag, "to");
        }
        if(fromPos != null) {
            NBTUtil.storeBlockPos(tag, "from", fromPos);
        }else{
            NBTUtil.removeBlockPos(tag, "from");
        }
        return super.save(tag);
    }

    @Override
    public List<String> getTooltip() {
        List<String> list = new ArrayList<>();
        if(toPos == null){
            list.add(new TranslationTextComponent("ars_nouveau.relay.no_to").getString());
        }else{
            list.add(new TranslationTextComponent("ars_nouveau.relay.one_to", 1).getString());
        }
        if(fromPos == null){
            list.add(new TranslationTextComponent("ars_nouveau.relay.no_from").getString());
        }else{
            list.add(new TranslationTextComponent("ars_nouveau.relay.one_from", 1).getString());
        }
        return list;
    }
    AnimationFactory factory = new AnimationFactory(this);

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController(this, "rotate_controller", 0, this::idlePredicate));
        data.addAnimationController(new AnimationController(this, "float_controller", 0, this::floatPredicate));
    }

    private <P extends IAnimatable> PlayState idlePredicate(AnimationEvent<P> event) {
        event.getController().setAnimation(new AnimationBuilder().addAnimation("floating", true));
        return PlayState.CONTINUE;
    }

    private <P extends IAnimatable> PlayState floatPredicate(AnimationEvent<P> event) {
        event.getController().setAnimation(new AnimationBuilder().addAnimation("rotation", true));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }
}
