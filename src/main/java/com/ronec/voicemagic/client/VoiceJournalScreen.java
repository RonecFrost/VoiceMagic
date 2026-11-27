package com.ronec.voicemagic.client;

import com.ronec.voicemagic.SpellConcepts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** All test concepts are available from the beginning. Words are trained individually. */
public final class VoiceJournalScreen extends Screen {
    private static final String[] TABS={"Spells","Targets","Modes","Effects"};
    private final String section;
    private final int page;
    public VoiceJournalScreen() { this("Spells",0); }
    private VoiceJournalScreen(String section,int page) {
        super(Component.literal("Voice Magic - Spellbook"));
        this.section=section; this.page=page;
    }

    @Override protected void init() {
        int cx=width/2,cy=height/2;
        for(int i=0;i<TABS.length;i++) {
            String tab=TABS[i];
            addRenderableWidget(Button.builder(Component.literal(tab.equals(section)?("[" + tab + "]"):tab),
                b -> minecraft.setScreen(new VoiceJournalScreen(tab,0)))
                .bounds(cx-166+i*84,cy-100,80,20).build());
        }
        List<String> concepts=SpellConcepts.section(section);
        int pages=Math.max(1,(concepts.size()+9)/10);
        int currentPage=Math.min(page,pages-1);
        VoiceProfile profile=VoiceClient.profile();
        for(int i=currentPage*10;i<Math.min(concepts.size(),(currentPage+1)*10);i++) {
            String concept=concepts.get(i);
            int position=i-currentPage*10;
            String label=SpellConcepts.label(concept)+" ["+(profile==null?0:profile.count(concept))+"/5]";
            addRenderableWidget(Button.builder(Component.literal(label),
                b -> minecraft.setScreen(new VoiceTrainerScreen(this,concept)))
                .bounds(cx-159+(position%2)*162,cy-69+(position/2)*25,155,21).build());
        }
        if(currentPage>0) {
            addRenderableWidget(Button.builder(Component.literal("< Previous"),b ->
                minecraft.setScreen(new VoiceJournalScreen(section,currentPage-1)))
                .bounds(cx-160,cy+72,92,19).build());
        }
        if(currentPage+1<pages) {
            addRenderableWidget(Button.builder(Component.literal("Next >"),b ->
                minecraft.setScreen(new VoiceJournalScreen(section,currentPage+1)))
                .bounds(cx+68,cy+72,92,19).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose())
            .bounds(cx-51,cy+99,102,19).build());
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        renderBackground(g,mouseX,mouseY,partialTick);
        int cx=width/2,cy=height/2;
        g.fill(cx-180,cy-129,cx+180,cy+129,0xE0171C28);
        g.fill(cx-175,cy-124,cx+175,cy+124,0xFFE9DFC3);
        g.fill(cx,cy-124,cx+1,cy+124,0xFFC1AE8E);
        g.drawCenteredString(font,"VOICE MAGIC - INCANTATION JOURNAL",cx,cy-117,0xFF4E3228);
        List<String> concepts=SpellConcepts.section(section);
        int pages=Math.max(1,(concepts.size()+9)/10);
        g.drawCenteredString(font,section+" - page "+(Math.min(page,pages-1)+1)+"/"+pages,
            cx,cy+61,0xFF624A39);
        if("Spells".equals(section))
            g.drawCenteredString(font,"Projectile OR Gamemode / Effect + Target + Value",cx,cy+89,0xFF59432F);
        else
            g.drawCenteredString(font,"Click any concept to record five personal pronunciations.",cx,cy+89,0xFF59432F);
        super.render(g,mouseX,mouseY,partialTick);
    }
}
