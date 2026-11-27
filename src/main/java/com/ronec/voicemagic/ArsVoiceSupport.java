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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bridges our client-only voice templates to Ars' normal spellbook and glyph data. */
public final class ArsVoiceSupport {
    private ArsVoiceSupport() {}

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

    /** Recipe-based identity: modifying a saved spell requires a new voice enrollment. */
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

    public static boolean heldSpellbook(Player player, InteractionHand hand) {
        return player.getItemInHand(hand).getItem() instanceof SpellBook;
    }

    public static InteractionHand findBook(Player player) {
        if(heldSpellbook(player,InteractionHand.MAIN_HAND))return InteractionHand.MAIN_HAND;
        if(heldSpellbook(player,InteractionHand.OFF_HAND))return InteractionHand.OFF_HAND;
        return null;
    }

    public static AbstractCaster<?> caster(Player player, InteractionHand hand) {
        if(hand==null || !heldSpellbook(player,hand))return null;
        return SpellCasterRegistry.from(player.getItemInHand(hand));
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
