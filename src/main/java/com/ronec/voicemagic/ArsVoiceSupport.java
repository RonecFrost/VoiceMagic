package com.ronec.voicemagic;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.registry.SpellCasterRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractCaster;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.capability.IPlayerCap;
import com.hollingsworth.arsnouveau.common.items.SpellBook;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Books are memory references, not required held items. Inventory inspection
 * is performed separately on the client (training) and server (authorization).
 */
public final class ArsVoiceSupport {
    private ArsVoiceSupport() {}

    public record BookRef(int slot, boolean offhand, ItemStack stack) {
        public SpellBook item(){return (SpellBook)stack.getItem();}
        public int tier(){return item().getTier().value;}
        public AbstractCaster<?> caster(){return SpellCasterRegistry.from(stack);}
        public String description(){
            return (offhand?"Offhand":"Inventory "+(slot+1))+" / Tier "+tier();
        }
    }
    public record SavedSpell(BookRef book,int slot,Spell spell) {}

    public static List<BookRef> inventoryBooks(Player player) {
        List<BookRef> found=new ArrayList<>();
        if(player==null)return found;
        // The main inventory contains hotbar + backpack, including selected hand.
        for(int i=0;i<player.getInventory().items.size();i++) {
            ItemStack stack=player.getInventory().items.get(i);
            if(!stack.isEmpty() && stack.getItem() instanceof SpellBook)
                found.add(new BookRef(i,false,stack));
        }
        for(int i=0;i<player.getInventory().offhand.size();i++) {
            ItemStack stack=player.getInventory().offhand.get(i);
            if(!stack.isEmpty() && stack.getItem() instanceof SpellBook)
                found.add(new BookRef(i,true,stack));
        }
        return found;
    }

    public static BookRef strongestBook(Player player) {
        BookRef strongest=null;
        for(BookRef book:inventoryBooks(player))
            if(strongest==null || book.tier()>strongest.tier())strongest=book;
        return strongest;
    }

    public static SavedSpell findSavedSpell(Player player,String key) {
        if(key==null || !key.startsWith("saved:") || !isConceptKey(key))return null;
        SavedSpell chosen=null;
        for(BookRef book:inventoryBooks(player)){
            AbstractCaster<?> caster=book.caster();
            if(caster==null)continue;
            for(int i=0;i<caster.getMaxSlots();i++){
                Spell spell=caster.getSpell(i);
                if(spell==null || spell.isEmpty())continue;
                if(!savedKey(spell).equals(key))continue;
                // Duplicate identical recipes across books: use the most
                // capable copy, without making an invented spell for the player.
                if(chosen==null||book.tier()>chosen.book().tier())
                    chosen=new SavedSpell(book,i,spell);
            }
        }
        return chosen;
    }

    public static boolean isConceptKey(String key) {
        if (key == null || key.length() > 120) return false;
        if (key.startsWith("saved:")) return key.substring(6).matches("[0-9a-f]{24}");
        if (key.startsWith("glyph:")) {
            ResourceLocation id=ResourceLocation.tryParse(key.substring(6));
            return id != null && key.substring(6).contains(":") &&
                    key.substring(6).equals(id.toString());
        }
        return false;
    }

    public static String glyphKey(AbstractSpellPart part) {
        return "glyph:" + part.getRegistryName();
    }

    /** Recipe-based identity remains compatible with existing v0.7 training. */
    public static String savedKey(Spell spell) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] hashed=digest.digest(spell.serializeRecipe().toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb=new StringBuilder("saved:");
            for(int i=0;i<12;i++)sb.append(String.format("%02x",hashed[i]&255));
            return sb.toString();
        } catch(Exception e) {
            throw new IllegalStateException("SHA-256 is required by Java",e);
        }
    }

    public static List<AbstractSpellPart> knownGlyphs(Player player) {
        Set<ResourceLocation> ids=new HashSet<>();
        List<AbstractSpellPart> list=new ArrayList<>();
        IPlayerCap cap=CapabilityRegistry.getPlayerDataCap(player);
        for(AbstractSpellPart part:GlyphRegistry.getDefaultStartingSpells()) {
            if(ids.add(part.getRegistryName()))list.add(part);
        }
        if(cap!=null)for(AbstractSpellPart part:cap.getKnownGlyphs()) {
            if(ids.add(part.getRegistryName()))list.add(part);
        }
        list.sort((a,b)->a.getLocaleName().compareToIgnoreCase(b.getLocaleName()));
        return list;
    }

    public static boolean playerKnows(Player player, AbstractSpellPart part) {
        if(GlyphRegistry.getDefaultStartingSpells().contains(part))return true;
        IPlayerCap cap=CapabilityRegistry.getPlayerDataCap(player);
        return cap!=null && cap.knowsGlyph(part);
    }
}
