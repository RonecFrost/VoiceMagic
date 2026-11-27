package com.ronec.voicemagic.network;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractCaster;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.items.SpellBook;
import com.hollingsworth.arsnouveau.common.spell.validation.GlyphMaxTierValidator;
import com.ronec.voicemagic.ArsVoiceSupport;
import com.ronec.voicemagic.VoiceMagicMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Voice recognition stays client-side; only bounded spell requests cross the network.
 * Uses Ars Nouveau's own spell-caster, validator, mana and spell-execution methods.
 */
public record ArsVoiceCastPayload(String command) implements CustomPacketPayload {
    public static final Type<ArsVoiceCastPayload> TYPE=new Type<>(
        ResourceLocation.fromNamespaceAndPath(VoiceMagicMod.MOD_ID,"ars_voice_cast"));
    public static final StreamCodec<ByteBuf,ArsVoiceCastPayload> STREAM_CODEC=StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,ArsVoiceCastPayload::command,ArsVoiceCastPayload::new);
    private static final Map<UUID,Long> LAST_CAST=new ConcurrentHashMap<>();
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}

    public static void handle(ArsVoiceCastPayload payload, IPayloadContext context) {
        if(!(context.player() instanceof ServerPlayer player))return;
        if(payload.command()==null || payload.command().length()>1700)return;
        String[] args=payload.command().split("\\|",4);
        if(args.length<4)return;
        InteractionHand hand;
        try {hand=InteractionHand.valueOf(args[1]);}
        catch(IllegalArgumentException ex){return;}
        if(!ArsVoiceSupport.heldSpellbook(player,hand)) {
            notifyPlayer(player,"Hold an Ars Nouveau spellbook in either hand to cast.");
            return;
        }
        AbstractCaster<?> caster=ArsVoiceSupport.caster(player,hand);
        if(caster==null)return;
        long now=player.level().getGameTime();
        Long last=LAST_CAST.get(player.getUUID());
        if(last!=null && now>=last && now-last<5)return;

        Spell spell;
        if("saved".equals(args[0])) {
            try {
                int slot=Integer.parseInt(args[2]);
                if(slot<0 || slot>=caster.getMaxSlots())return;
                spell=caster.getSpell(slot);
                if(spell==null || spell.isEmpty() || !ArsVoiceSupport.savedKey(spell).equals(args[3])) {
                    notifyPlayer(player,"Saved spell changed. Retrain its complete incantation.");
                    return;
                }
            } catch(NumberFormatException ex){return;}
        } else if("glyphs".equals(args[0])) {
            if(!"-".equals(args[2]))return;
            String[] keys=args[3].split(",",-1);
            if(keys.length<2 || keys.length>20)return;
            List<AbstractSpellPart> recipe=new ArrayList<>();
            for(String key:keys) {
                if(!key.startsWith("glyph:") || !ArsVoiceSupport.isConceptKey(key))return;
                ResourceLocation id=ResourceLocation.tryParse(key.substring(6));
                AbstractSpellPart glyph=id==null?null:GlyphRegistry.getSpellPart(id);
                if(glyph==null || !glyph.isEnabled() || !ArsVoiceSupport.playerKnows(player,glyph)) {
                    notifyPlayer(player,"Unknown or locked glyph in incantation.");
                    return;
                }
                recipe.add(glyph);
            }
            if(!(player.getItemInHand(hand).getItem() instanceof SpellBook book))return;
            if(!new GlyphMaxTierValidator(book.getTier().value).validate(recipe).isEmpty()) {
                notifyPlayer(player,"Spell includes glyphs above your spellbook tier.");
                return;
            }
            if(!ArsNouveauAPI.getInstance().getSpellCraftingSpellValidator().validate(recipe).isEmpty()) {
                notifyPlayer(player,"Those glyphs don't form a valid Ars Nouveau spell.");
                return;
            }
            spell=new Spell(recipe);
        } else return;
        if(spell==null || spell.isEmpty() || spell.getCastMethod()==null)return;
        // CastResolver revalidates compatibility, mana, and the usual Ars spell restrictions.
        LAST_CAST.put(player.getUUID(),now);
        caster.castSpell(player.level(),player,hand,Component.literal("Invalid Ars Nouveau spell"),spell);
    }

    private static void notifyPlayer(ServerPlayer player,String message) {
        player.sendSystemMessage(Component.literal("[Voice Magic] "+message));
    }
}
