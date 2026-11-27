package com.ronec.voicemagic.network;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractCaster;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Only compact IDs cross the network. Book discovery and spell authorization are server-side. */
public record ArsVoiceCastPayload(String command) implements CustomPacketPayload {
    public static final Type<ArsVoiceCastPayload> TYPE=new Type<>(
        ResourceLocation.fromNamespaceAndPath(VoiceMagicMod.MOD_ID,"ars_voice_cast"));
    public static final StreamCodec<ByteBuf,ArsVoiceCastPayload> STREAM_CODEC=StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,ArsVoiceCastPayload::command,ArsVoiceCastPayload::new);
    private static final Map<UUID,Long> LAST_CAST=new ConcurrentHashMap<>();
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}

    public static void handle(ArsVoiceCastPayload payload,IPayloadContext context){
        if(!(context.player() instanceof ServerPlayer player))return;
        if(payload.command()==null || payload.command().length()>1700)return;
        String[] args=payload.command().split("\\|",4);
        if(args.length!=4 || !"inventory".equals(args[1]))return;
        long now=player.level().getGameTime();
        Long last=LAST_CAST.get(player.getUUID());
        if(last!=null && now>=last && now-last<5)return;

        ArsVoiceSupport.BookRef sourceBook;
        Spell spell;
        if("saved".equals(args[0])){
            if(!"-".equals(args[2]))return;
            ArsVoiceSupport.SavedSpell saved=ArsVoiceSupport.findSavedSpell(player,args[3]);
            if(saved==null){
                notifyPlayer(player,"That saved incantation isn't in any carried spellbook.");
                return;
            }
            sourceBook=saved.book();
            spell=saved.spell(); // Recipe belongs to this specific physical book.
        }else if("glyphs".equals(args[0])){
            if(!"-".equals(args[2]))return;
            String[] keys=args[3].split(",",-1);
            if(keys.length<2 || keys.length>20)return;
            List<AbstractSpellPart> recipe=new ArrayList<>();
            for(String key:keys){
                if(!key.startsWith("glyph:") || !ArsVoiceSupport.isConceptKey(key))return;
                ResourceLocation id=ResourceLocation.tryParse(key.substring(6));
                AbstractSpellPart glyph=id==null?null:GlyphRegistry.getSpellPart(id);
                if(glyph==null||!glyph.isEnabled()||!ArsVoiceSupport.playerKnows(player,glyph)){
                    notifyPlayer(player,"Unknown or locked glyph in incantation.");
                    return;
                }
                recipe.add(glyph);
            }
            sourceBook=ArsVoiceSupport.strongestBook(player);
            if(sourceBook==null){
                notifyPlayer(player,"Carry an Ars Nouveau spellbook to improvise.");
                return;
            }
            spell=new Spell(recipe);
        }else return;

        if(sourceBook==null || spell.isEmpty() || spell.getCastMethod()==null)return;
        AbstractCaster<?> caster=sourceBook.caster();
        if(caster==null)return;

        List<AbstractSpellPart> recipe=new ArrayList<>(spell.unsafeList());
        if(!new GlyphMaxTierValidator(sourceBook.tier()).validate(recipe).isEmpty()){
            notifyPlayer(player,"A glyph exceeds the tier of its source spellbook.");
            return;
        }
        if(!ArsNouveauAPI.getInstance().getSpellCraftingSpellValidator().validate(recipe).isEmpty()){
            notifyPlayer(player,"Those glyphs do not form a valid Ars Nouveau spell.");
            return;
        }
        // Keep Ars Nouveau's casting events and mana calculations. The book
        // stack is supplied as the caster tool, even when no book is in hand.
        LAST_CAST.put(player.getUUID(),now);
        InventoryVoiceCaster.cast(player,sourceBook.stack(),caster,spell);
    }

    private static void notifyPlayer(ServerPlayer player,String message){
        player.sendSystemMessage(Component.literal("[Voice Magic] "+message));
    }
}
