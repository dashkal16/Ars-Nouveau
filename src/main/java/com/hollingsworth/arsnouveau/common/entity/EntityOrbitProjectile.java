package com.hollingsworth.arsnouveau.common.entity;

import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.client.particle.GlowParticleData;
import com.hollingsworth.arsnouveau.common.network.Networking;
import com.hollingsworth.arsnouveau.common.network.PacketANEffect;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.*;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.FMLPlayMessages;
import net.minecraftforge.fml.network.NetworkHooks;

public class EntityOrbitProjectile extends EntityProjectileSpell{
    public Entity wardedEntity;
    int ticksLeft;
    private static final DataParameter<Integer> OWNER_UUID = EntityDataManager.defineId(EntityOrbitProjectile.class, DataSerializers.INT);
    public static final DataParameter<Integer> OFFSET = EntityDataManager.defineId(EntityOrbitProjectile.class, DataSerializers.INT);
    public static final DataParameter<Integer> ACCELERATES = EntityDataManager.defineId(EntityOrbitProjectile.class, DataSerializers.INT);
    public static final DataParameter<Integer> AOE = EntityDataManager.defineId(EntityOrbitProjectile.class, DataSerializers.INT);
    public static final DataParameter<Integer> TOTAL = EntityDataManager.defineId(EntityOrbitProjectile.class, DataSerializers.INT);
    public int extendTimes;

    public EntityOrbitProjectile(World worldIn, double x, double y, double z) {
        super(worldIn, x, y, z);
    }

    public EntityOrbitProjectile(World worldIn, LivingEntity shooter) {
        super(worldIn, shooter);
    }

    public EntityOrbitProjectile(World world, SpellResolver resolver){
        super(world, resolver);
    }

    public EntityOrbitProjectile(EntityType<EntityOrbitProjectile> entityWardProjectileEntityType, World world) {
        super(entityWardProjectileEntityType, world);
    }


    public void setOffset(int offset){
        entityData.set(OFFSET, offset);
    }

    public int getOffset(){
        int val = 15;
        return (entityData.get(OFFSET)) * val;
    }

    public void setTotal(int total){
        entityData.set(TOTAL, total);
    }

    public int getTotal(){
        return entityData.get(TOTAL) > 0 ? entityData.get(TOTAL) : 1;
    }

    public void setAccelerates(int accelerates){
        entityData.set(ACCELERATES, accelerates);
    }

    public int getAccelerates(){
        return entityData.get(ACCELERATES);
    }

    public void setAoe(int aoe){
        entityData.set(AOE, aoe);
    }

    public int getAoe(){
        return entityData.get(AOE);
    }
    @Override
    public void tick() {
        this.age++;
        if(!level.isClientSide && this.age > 60 * 20 + 30 * 20 * extendTimes){
            this.remove();
            return;
        }
        if(!level.isClientSide && spellResolver == null)
            this.remove();
        Entity owner = level.getEntity(getOwnerID());
//        this.remove();
        if(!level.isClientSide && owner == null) {
            this.remove();
            return;
        }

        if(owner == null)
            return;
        double rotateSpeed = 10.0 - getAccelerates();
        double radiusMultiplier = 1.5 + 0.5*getAoe();

        this.setPos(owner.getX()- radiusMultiplier * Math.sin(tickCount/rotateSpeed + getOffset()),
                owner.getY() + 1,
                owner.getZ()- radiusMultiplier * Math.cos(tickCount/rotateSpeed + getOffset()));

        Vector3d vector3d2 = this.position();
        int nextTick = tickCount + 3;
        Vector3d vector3d3 = new Vector3d(
                owner.getX() - radiusMultiplier * Math.sin(nextTick/rotateSpeed + getOffset()),
                owner.getY() + 1,
                owner.getZ()- radiusMultiplier * Math.cos(nextTick/rotateSpeed + getOffset()));
        RayTraceResult raytraceresult = this.level.clip(new RayTraceContext(vector3d2, vector3d3, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, this));
        if (raytraceresult != null && raytraceresult.getType() != RayTraceResult.Type.MISS) {
            vector3d3 = raytraceresult.getLocation();
        }
        EntityRayTraceResult entityraytraceresult = this.findHitEntity(vector3d2, vector3d3);
        if (entityraytraceresult != null) {
            raytraceresult = entityraytraceresult;
        }

        if (raytraceresult != null && raytraceresult instanceof EntityRayTraceResult) {
            Entity entity = ((EntityRayTraceResult)raytraceresult).getEntity();
            Entity entity1 = this.getOwner();
            if (entity instanceof PlayerEntity && entity1 instanceof PlayerEntity && !((PlayerEntity)entity1).canHarmPlayer((PlayerEntity)entity)) {
                raytraceresult = null;
            }
        }

        if (raytraceresult != null && raytraceresult.getType() != RayTraceResult.Type.MISS  && !net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, raytraceresult)) {
            this.onHit(raytraceresult);
            this.hasImpulse = true;
        }
        if(level.isClientSide && this.age > 2) {
                double deltaX = getX() - xOld;
                double deltaY = getY() - yOld;
                double deltaZ = getZ() - zOld;
                double dist = Math.ceil(Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 8);

                for (double j = 0; j < dist; j++) {
                    double coeff = j / dist;

                    level.addParticle(GlowParticleData.createData(getParticleColor()),
                            (float) (xo + deltaX * coeff),
                            (float) (yo + deltaY * coeff), (float)
                                    (zo + deltaZ * coeff),
                            0.0125f * (random.nextFloat() - 0.5f),
                            0.0125f * (random.nextFloat() - 0.5f),
                            0.0125f * (random.nextFloat() - 0.5f));

                }
            }

    }

    protected void attemptRemoval(){
        this.pierceLeft--;
        if(this.pierceLeft < 0){
            this.level.broadcastEntityEvent(this, (byte)3);
            this.remove();
        }
    }

    @Override
    protected void onHit(RayTraceResult result) {
        if(level.isClientSide || result == null)
            return;

        if(result.getType() == RayTraceResult.Type.ENTITY) {
            if (((EntityRayTraceResult) result).getEntity().equals(this.getOwner())) return;
            if(this.spellResolver != null) {
                this.spellResolver.onResolveEffect(level, (LivingEntity) this.getOwner(), result);
                Networking.sendToNearby(level, new BlockPos(result.getLocation()), new PacketANEffect(PacketANEffect.EffectType.BURST,
                        new BlockPos(result.getLocation()),getParticleColorWrapper()));
                attemptRemoval();
            }
        }else if(numSensitive > 0 && result instanceof BlockRayTraceResult && !this.removed){
            BlockRayTraceResult blockraytraceresult = (BlockRayTraceResult)result;
            if(this.spellResolver != null) {
                this.spellResolver.onResolveEffect(this.level, (LivingEntity) this.getOwner(), blockraytraceresult);
            }
            Networking.sendToNearby(level, ((BlockRayTraceResult) result).getBlockPos(), new PacketANEffect(PacketANEffect.EffectType.BURST,
                    new BlockPos(result.getLocation()).below(), getParticleColorWrapper()));
            attemptRemoval();
        }
    }
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER_UUID, 0);
        this.entityData.define(OFFSET, 0);
        this.entityData.define(ACCELERATES, 0);
        this.entityData.define(AOE, 0);
        this.entityData.define(TOTAL, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundNBT tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("left", ticksLeft);
        tag.putInt("offset", getOffset());
        tag.putInt("aoe", getAoe());
        tag.putInt("accelerate", getAccelerates());
        tag.putInt("total", getTotal());
        tag.putInt("ownerID", getOwnerID());
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT tag) {
        super.readAdditionalSaveData(tag);
        this.ticksLeft = tag.getInt("left");
        setOffset(tag.getInt("offset"));
        setAoe(tag.getInt("aoe"));
        setAccelerates(tag.getInt("accelerate"));
        setOwnerID(tag.getInt("ownerID"));
        setTotal(tag.getInt("total"));
    }

    @Override
    public EntityType<?> getType() {
        return ModEntities.ENTITY_WARD;
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public EntityOrbitProjectile(FMLPlayMessages.SpawnEntity packet, World world) {
        super(ModEntities.ENTITY_WARD, world);
    }

    public int getTicksLeft() {
        return ticksLeft;
    }

    public void setTicksLeft(int ticks) {
        this.ticksLeft = ticks;
    }

    public int getOwnerID() {
        return this.getEntityData().get(OWNER_UUID);
    }

    public void setOwnerID(int uuid) {
        this.getEntityData().set(OWNER_UUID,uuid);
    }
}
