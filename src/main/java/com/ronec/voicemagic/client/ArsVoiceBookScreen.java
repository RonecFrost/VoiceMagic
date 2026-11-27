package com.ronec.voicemagic.client;

import com.hollingsworth.arsnouveau.api.spell.AbstractCaster;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import com.ronec.voicemagic.ArsVoiceSupport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Voice tab invoked from the original Ars Nouveau spell-crafting screen. */
public final class ArsVoiceBookScreen extends Screen {
    private final GuiSpellBook parent;
    private boolean glyphPage;
    private int page;
    private final List<Entry> rows=new ArrayList<>();
    private record Entry(String key,String name) {}

    public ArsVoiceBookScreen(GuiSpellBook parent) {
        super(Component.literal("Ars Nouveau - Voice Incantations"));
        this.parent=parent;
    }

    @Override protected void init() {
        rows.clear();
        int cx=width/2,cy=height/2;
        var player=minecraft.player;
        if(player==null)return;

        AbstractCaster<?> caster=parent.caster;
        if(glyphPage) {
            for(AbstractSpellPart glyph:ArsVoiceSupport.knownGlyphs(player))
                rows.add(new Entry(ArsVoiceSupport.glyphKey(glyph),glyph.getLocaleName()));
        } else if(caster!=null) {
            for(int i=0;i<caster.getMaxSlots();i++) {
                Spell spell=caster.getSpell(i);
                if(spell==null||spell.isEmpty())continue;
                String name=caster.getSpellName(i);
                if(name==null||name.isBlank())name="Spell "+(i+1);
                rows.add(new Entry(ArsVoiceSupport.savedKey(spell),name+" [slot "+(i+1)+"]"));
            }
        }
        addRenderableWidget(Button.builder(Component.literal(glyphPage?"Saved Spells":"[Saved Spells]"),
                b->switchTab(false)).bounds(cx-155,cy-94,145,20).build());
        addRenderableWidget(Button.builder(Component.literal(glyphPage?"[Individual Glyphs]":"Individual Glyphs"),
                b->switchTab(true)).bounds(cx+10,cy-94,145,20).build());

        VoiceProfile profile=VoiceClient.profile();
        int maxPage=Math.max(0,(rows.size()-1)/10);
        page=Math.min(page,maxPage);
        int start=page*10;
        for(int i=start;i<Math.min(rows.size(),start+10);i++) {
            Entry entry=rows.get(i);
            int j=i-start;
            int count=profile==null?0:profile.count(entry.key());
            String title=fit(entry.name(),20)+" ["+count+"/5]";
            addRenderableWidget(Button.builder(Component.literal(title),b->
                    minecraft.setScreen(new VoiceTrainerScreen(this,entry.key(),entry.name())))
                    .bounds(cx-158+(j%2)*163,cy-60+(j/2)*24,154,21).build());
        }
        if(page>0) addRenderableWidget(Button.builder(Component.literal("Previous"),b->changePage(-1))
                .bounds(cx-160,cy+68,96,20).build());
        if(page<maxPage) addRenderableWidget(Button.builder(Component.literal("Next"),b->changePage(1))
                .bounds(cx+64,cy+68,96,20).build());

        addRenderableWidget(Button.builder(Component.literal(
                "Casting: "+(VoiceClient.isLiveMode()?"IMPROVISED GLYPHS":"SAVED SPELLS")),b->{
                    VoiceClient.toggleMode();
                    refreshVoiceWidgets();
                }).bounds(cx-150,cy+95,300,20).build());
        addRenderableWidget(Button.builder(Component.literal("Back to Ars Book"),b->onClose())
                .bounds(cx-95,cy+126,190,20).build());
    }

    private void switchTab(boolean value) {
        glyphPage=value;page=0;refreshVoiceWidgets();
    }
    private void changePage(int direction) {
        page=Math.max(0,page+direction);refreshVoiceWidgets();
    }
    private void refreshVoiceWidgets() {
        clearWidgets();
        init();
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        renderBackground(g,mouseX,mouseY,partial);
        int cx=width/2,cy=height/2;
        g.fill(cx-175,cy-130,cx+175,cy+152,0xF01B2230);
        g.drawCenteredString(font,"ARS NOUVEAU - VOICE TRAINING",cx,cy-119,0xFFF2E1B8);
        g.drawCenteredString(font,glyphPage?"Train any unlocked glyph":"Train one spoken phrase for each saved spell",
                cx,cy-85,0xFFC7D7EC);
        if(rows.isEmpty())g.drawCenteredString(font,glyphPage?"No learned glyphs found":"Save a spell in your Ars book first",
                cx,cy-8,0xFFF4D8B8);
        g.drawCenteredString(font,"Page "+(page+1)+" / "+Math.max(1,(rows.size()+9)/10),
                cx,cy+57,0xFFBBC9E0);
        g.drawCenteredString(font,"Hold ALT: speak / release: cast. SEMICOLON: switch mode.",
                cx,cy+119,0xFFB9C5DA);
        super.render(g,mouseX,mouseY,partial);
    }
    private static String fit(String s,int n) {
        return s.length()>n?s.substring(0,n-3)+"...":s;
    }
    @Override public void onClose() {
        minecraft.setScreen(parent);
    }
}
