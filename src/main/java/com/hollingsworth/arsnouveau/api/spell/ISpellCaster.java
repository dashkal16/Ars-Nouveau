package com.hollingsworth.arsnouveau.api.spell;

import com.hollingsworth.arsnouveau.api.util.MathUtil;
import com.hollingsworth.arsnouveau.common.block.tile.IntangibleAirTile;
import com.hollingsworth.arsnouveau.common.block.tile.PhantomBlockTile;
import com.hollingsworth.arsnouveau.common.block.tile.ScribesTile;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import java.util.Map;

public interface ISpellCaster {

    Spell getSpell();

    Spell getSpell(int slot);

    int getMaxSlots();

    int getCurrentSlot();

    void setCurrentSlot(int slot);

    void setSpell(Spell spell, int slot);

    void setSpell(Spell spell);

    Map<Integer, Spell> getSpells();

    default ActionResult<ItemStack> castSpell(World worldIn, PlayerEntity playerIn, Hand handIn, TranslationTextComponent invalidMessage){
        ItemStack stack = playerIn.getItemInHand(handIn);
        ISpellCaster caster = this;

        if(worldIn.isClientSide)
            return ActionResult.pass(playerIn.getItemInHand(handIn));

        RayTraceResult result = playerIn.pick(5, 0, false);
        if(result instanceof BlockRayTraceResult && worldIn.getBlockEntity(((BlockRayTraceResult) result).getBlockPos()) instanceof ScribesTile)
            return new ActionResult<>(ActionResultType.SUCCESS, stack);
        if(result instanceof BlockRayTraceResult && !playerIn.isShiftKeyDown()){
            if(worldIn.getBlockEntity(((BlockRayTraceResult) result).getBlockPos()) != null &&
                    !(worldIn.getBlockEntity(((BlockRayTraceResult) result).getBlockPos()) instanceof IntangibleAirTile
                            ||(worldIn.getBlockEntity(((BlockRayTraceResult) result).getBlockPos()) instanceof PhantomBlockTile))) {
                return new ActionResult<>(ActionResultType.SUCCESS, stack);
            }
        }

        if(caster.getSpell() == null) {
            playerIn.sendMessage(invalidMessage, Util.NIL_UUID);
            return new ActionResult<>(ActionResultType.CONSUME, stack);
        }
        SpellResolver resolver = new SpellResolver(caster.getSpell().recipe, new SpellContext(caster.getSpell(), playerIn));
        EntityRayTraceResult entityRes = MathUtil.getLookedAtEntity(playerIn, 25);

        if(entityRes != null && entityRes.getEntity() instanceof LivingEntity){
            resolver.onCastOnEntity(stack, playerIn, (LivingEntity) entityRes.getEntity(), handIn);
            return new ActionResult<>(ActionResultType.CONSUME, stack);
        }

        if(result instanceof BlockRayTraceResult){
            ItemUseContext context = new ItemUseContext(playerIn, handIn, (BlockRayTraceResult) result);
            resolver.onCastOnBlock(context);
            return new ActionResult<>(ActionResultType.CONSUME, stack);
        }

        resolver.onCast(stack,playerIn,worldIn);
        return new ActionResult<>(ActionResultType.CONSUME, stack);
    }
}
