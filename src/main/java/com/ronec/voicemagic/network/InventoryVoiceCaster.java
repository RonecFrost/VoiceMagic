package com.ronec.voicemagic.network;

import com.hollingsworth.arsnouveau.api.spell.AbstractCaster;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.PlayerCaster;
import com.hollingsworth.arsnouveau.api.util.SpellUtil;
import com.hollingsworth.arsnouveau.common.block.tile.ScribesTile;
import com.hollingsworth.arsnouveau.common.datagen.BlockTagProvider;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentSensitive;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Mirrors Ars Nouveau's AbstractCaster.castSpell but with a book stack from
 * the player's actual inventory as the SpellContext caster tool.
 *
 * The player never has an item swapped into their hand. The resolver still
 * runs Ars' casting validators, mana consumption, event hooks and effects.
 */
public final class InventoryVoiceCaster {
    private InventoryVoiceCaster(){}

    public static void cast(ServerPlayer player,ItemStack sourceBook,
                            AbstractCaster<?> bookCaster,Spell original){
        if(sourceBook.isEmpty() || player.isSpectator())return;
        var level=player.serverLevel();
        InteractionHand hand=InteractionHand.MAIN_HAND; // virtual casting gesture, never swaps items
        Spell spell=bookCaster.modifySpellBeforeCasting(level,player,hand,original);
        if(spell==null || spell.isEmpty() || !spell.isValid()){
            player.sendSystemMessage(Component.literal("[Voice Magic] Invalid spell."));
            return;
        }
        SpellContext context=new SpellContext(level,spell,player,
                new PlayerCaster(player),sourceBook);
        SpellResolver resolver=bookCaster.getSpellResolver(context,level,player,hand);
        if(resolver==null || resolver.castType==null)return;
        boolean sensitive=resolver.spell.getBuffsAtIndex(0,player,AugmentSensitive.INSTANCE)>0;
        HitResult hit=SpellUtil.rayTrace(player,
                .5+player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue(),
                1,sensitive);
        if(hit instanceof BlockHitResult blockHit){
            BlockEntity tile=level.getBlockEntity(blockHit.getBlockPos());
            if(tile instanceof ScribesTile)return;
            if(!player.isShiftKeyDown() && tile!=null &&
                    !level.getBlockState(blockHit.getBlockPos()).is(BlockTagProvider.IGNORE_TILE))
                return;
        }
        if(hit instanceof EntityHitResult entityHit &&
                entityHit.getEntity() instanceof net.minecraft.world.entity.LivingEntity){
            resolver.onCastOnEntity(sourceBook,entityHit.getEntity(),hand);
        }else if(hit instanceof BlockHitResult blockHit &&
                (hit.getType()==HitResult.Type.BLOCK || sensitive)){
            // Context has a genuine player/hand for interactive glyphs.
            // The book itself is supplied separately in SpellContext.
            resolver.onCastOnBlock(new UseOnContext(player,hand,blockHit));
        }else{
            resolver.onCast(sourceBook,level);
        }
    }
}
