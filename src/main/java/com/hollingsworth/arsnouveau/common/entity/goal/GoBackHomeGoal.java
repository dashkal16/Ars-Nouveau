package com.hollingsworth.arsnouveau.common.entity.goal;

import com.hollingsworth.arsnouveau.api.util.BlockUtil;
import net.minecraft.entity.MobEntity;
import net.minecraft.util.math.BlockPos;

import java.util.function.Supplier;

public class GoBackHomeGoal extends DistanceRestrictedGoal {
    MobEntity entity;
    Supplier<Boolean> shouldGo;

    public GoBackHomeGoal(MobEntity entity, Supplier<BlockPos> pos, int maxDistance) {
        super(pos, maxDistance);
        this.entity = entity;
        this.shouldGo = () -> true;
    }

    public GoBackHomeGoal(MobEntity entity, Supplier<BlockPos> pos, int maxDistance, Supplier<Boolean> shouldGo) {
        super(pos, maxDistance);
        this.entity = entity;
        this.shouldGo = shouldGo;
    }

    @Override
    public void tick() {
        if(positionFrom.get() != null && BlockUtil.distanceFrom(entity.blockPosition(), positionFrom.get()) > 5){
            BlockPos homePos = positionFrom.get();
            entity.getNavigation().moveTo(homePos.getX(), homePos.getY(),homePos.getZ(), 1.5);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return positionFrom != null && BlockUtil.distanceFrom(entity.blockPosition(), positionFrom.get()) > 5 && shouldGo.get();
    }

    @Override
    public boolean canUse() {
        return entity.level.random.nextFloat() < 0.02f && positionFrom != null && !this.isInRange(entity.blockPosition()) && shouldGo.get();
    }
}
