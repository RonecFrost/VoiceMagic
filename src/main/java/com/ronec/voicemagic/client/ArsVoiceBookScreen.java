package com.ronec.voicemagic.client;

import com.hollingsworth.arsnouveau.api.spell.AbstractCaster;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import com.ronec.voicemagic.ArsVoiceSupport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** In-book voice tab with an opaque panel drawn before its widgets and captions. */
public final class ArsVoiceBookScreen extends Screen {
    private final GuiSpellBook parent;
    private boolean glyphPage;
    private int page,top;
    private final List<Entry> rows=new ArrayList<>();
    private record Entry(String key,String name) {}

    public ArsVoiceBookScreen(GuiSpellBook parent){
        super(Component.literal("Ars Nouveau - Voice Incantations"));
        this.parent=parent;
    }
    @Override protected void init(){
        rows.clear();
        int cx=width/2;
        top=Math.max(3,(height-236)/2);
        var player=minecraft.player;
        if(player==null)return;
        AbstractCaster<?> caster=parent.caster;
        if(glyphPage){
            for(AbstractSpellPart glyph:ArsVoiceSupport.knownGlyphs(player))
                rows.add(new Entry(ArsVoiceSupport.glyphKey(glyph),glyph.getLocaleName()));
        }else if(caster!=null){
            for(int i=0;i<caster.getMaxSlots();i++){
                Spell spell=caster.getSpell(i);
                if(spell==null||spell.isEmpty())continue;
                String name=caster.getSpellName(i);
                if(name==null||name.isBlank())name="Spell "+(i+1);
                rows.add(new Entry(ArsVoiceSupport.savedKey(spell),name+" [slot "+(i+1)+"]"));
            }
        }
        addRenderableWidget(Button.builder(Component.literal(glyphPage?"Saved Spells":"[Saved Spells]"),
            b->switchTab(false)).bounds(cx-159,top+27,155,20).build());
        addRenderableWidget(Button.builder(Component.literal(glyphPage?"[Individual Glyphs]":"Individual Glyphs"),
            b->switchTab(true)).bounds(cx+4,top+27,155,20).build());

        VoiceProfile profile=VoiceClient.profile();
        int maxPage=Math.max(0,(rows.size()-1)/8);
        page=Math.min(page,maxPage);
        for(int i=page*8;i<Math.min(rows.size(),(page+1)*8);i++){
            Entry entry=rows.get(i);
            int offset=i-page*8;
            int count=profile==null?0:profile.count(entry.key());
            String label=fit(entry.name(),21)+" ["+count+"/5]";
            addRenderableWidget(Button.builder(Component.literal(label),b->
                minecraft.setScreen(new VoiceTrainerScreen(this,entry.key(),entry.name())))
                .bounds(cx-159+(offset%2)*163,top+70+(offset/2)*23,155,20).build());
        }
        if(page>0)addRenderableWidget(Button.builder(Component.literal("Previous"),b->changePage(-1))
            .bounds(cx-159,top+164,100,18).build());
        if(page<maxPage)addRenderableWidget(Button.builder(Component.literal("Next"),b->changePage(1))
            .bounds(cx+59,top+164,100,18).build());

        addRenderableWidget(Button.builder(Component.literal(
            "Casting mode: "+(VoiceClient.isLiveMode()?"IMPROVISED GLYPHS":"SAVED SPELLS")),b->{
                VoiceClient.toggleMode();refresh();
            }).bounds(cx-159,top+185,318,20).build());
        addRenderableWidget(Button.builder(Component.literal("Back to Ars Spellbook"),b->onClose())
            .bounds(cx-108,top+209,216,19).build());
    }
    private void switchTab(boolean v){glyphPage=v;page=0;refresh();}
    private void changePage(int d){page=Math.max(0,page+d);refresh();}
    private void refresh(){clearWidgets();init();}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        int cx=width/2;
        // Explicit, single-layer background: no second blur pass over text or controls.
        g.fill(0,0,width,height,0xAA0B121C);
        g.fill(cx-177,top-4,cx+177,top+234,0xF01B2533);
        g.fill(cx-172,top+1,cx+172,top+4,0xFF97BBD0);
        for(Renderable widget:renderables)widget.render(g,mouseX,mouseY,partial);
        g.drawCenteredString(font,"ARS NOUVEAU - VOICE INCANTATIONS",cx,top+10,0xFFF2E6D2);
        g.drawCenteredString(font,glyphPage?"Train words for your unlocked Ars glyphs":
            "Train one complete phrase for each saved Ars spell",cx,top+53,0xFFCADBED);
        if(rows.isEmpty())g.drawCenteredString(font,glyphPage?"No unlocked glyphs found":
            "Save a spell in Ars Nouveau first",cx,top+99,0xFFDFC5AA);
        g.drawCenteredString(font,"Page "+(page+1)+" / "+Math.max(1,(rows.size()+7)/8),
            cx,top+165,0xFFBBD0E1);
    }
    private static String fit(String s,int n){return s.length()>n?s.substring(0,n-3)+"...":s;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
