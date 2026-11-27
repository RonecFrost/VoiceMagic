package com.ronec.voicemagic.client;

import com.ronec.voicemagic.audio.MicCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Guided five-sample training with random local audio replay and clearly layered UI. */
public final class VoiceTrainerScreen extends Screen {
    private final Screen parent;
    private final String concept;
    private final String displayName;
    private EditBox word;
    private MicCapture mic;
    private Button recordButton;
    private Button playbackButton;
    private String state="Record five pronunciations of your chosen word.";
    private int sessionCount;
    private boolean cancelled;
    private int top;

    public VoiceTrainerScreen(Screen parent,String concept,String displayName) {
        super(Component.literal("Voice Magic - "+displayName));
        this.parent=parent;
        this.concept=concept;
        this.displayName=displayName;
    }

    @Override protected void init() {
        int cx=width/2;
        top=Math.max(3,(height-237)/2);
        VoiceProfile profile=VoiceClient.profile();
        word=new EditBox(font,cx-113,top+56,226,20,Component.literal("Incantation label"));
        word.setMaxLength(24);
        word.setValue(profile==null?displayName:
            profile.count(concept)==0?displayName:profile.alias(concept));
        addRenderableWidget(word);
        recordButton=addRenderableWidget(Button.builder(Component.literal("Record five pronunciations"),
            b->toggleTraining()).bounds(cx-113,top+84,226,20).build());
        playbackButton=addRenderableWidget(Button.builder(Component.literal("Play a random recording"),
            b->playback()).bounds(cx-113,top+109,226,20).build());
        addRenderableWidget(Button.builder(Component.literal("Forget & retrain"),b->{
            if(mic!=null)return;
            VoiceProfile p=VoiceClient.profile();
            if(p!=null)p.reset(concept);
            state="Recordings and acoustic templates cleared.";
            updatePlaybackButton();
        }).bounds(cx-113,top+134,226,20).build());
        addRenderableWidget(Button.builder(Component.literal("Back to Voice Book"),b->onClose())
            .bounds(cx-113,top+207,226,20).build());
        updatePlaybackButton();
    }
    private void updatePlaybackButton(){
        VoiceProfile p=VoiceClient.profile();
        int clips=p==null?0:p.availablePlaybackSamples(concept);
        if(playbackButton!=null){
            playbackButton.active=clips>0&&mic==null;
            playbackButton.setMessage(Component.literal("Play random recording ("+clips+"/5)"));
        }
    }
    private void playback() {
        if(mic!=null)return;
        VoiceProfile p=VoiceClient.profile();
        if(p!=null && p.playRandomRecording(concept)) {
            state="Playing one randomly selected training pronunciation.";
        } else state="No audio saved for this word. Retrain to enable playback.";
    }
    private void toggleTraining(){
        if(mic!=null){cancelled=true;mic.stop();state="Recording stopped.";return;}
        VoiceProfile p=VoiceClient.profile();
        if(p==null)return;
        String alias=word.getValue().strip();
        if(alias.isEmpty()){state="Enter a name for your chosen sound.";return;}
        if(p.count(concept)>=5){
            state="Already trained. Choose Forget & retrain to replace it.";
            return;
        }
        sessionCount=p.count(concept);
        cancelled=false;
        state="Listening for recording "+(sessionCount+1)+"/5...";
        recordButton.setMessage(Component.literal("Stop recording"));
        word.setEditable(false);
        updatePlaybackButton();
        mic=new MicCapture(false,new MicCapture.Listener(){
            @Override public void onUtterance(short[] sample){
                Minecraft.getInstance().execute(()->{
                    if(cancelled||VoiceTrainerScreen.this.minecraft==null||mic==null)return;
                    int before=p.count(concept);
                    if(before>=5)return;
                    int count=p.teach(concept,alias,sample);
                    if(count==before){
                        state="Audio too short. Try again with a clearer pronunciation.";
                        return;
                    }
                    sessionCount=count;
                    if(count>=5){
                        state="All five samples recorded. You can play them back now.";
                        mic.stop();
                    }else state="Recorded "+count+"/5. Say your word again after a pause.";
                    updatePlaybackButton();
                });
            }
            @Override public void onFinished(){
                Minecraft.getInstance().execute(()->{
                    if(VoiceTrainerScreen.this.minecraft==null)return;
                    mic=null;
                    word.setEditable(true);
                    recordButton.setMessage(Component.literal("Record five pronunciations"));
                    updatePlaybackButton();
                    if(!cancelled&&sessionCount<5&&!state.contains("error"))
                        state="Mic stopped. "+sessionCount+"/5 saved; restart to continue.";
                });
            }
            @Override public void onError(String error){
                Minecraft.getInstance().execute(()->state="Microphone error: "+error);
            }
        });
        mic.start();
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        int cx=width/2;
        // No vanilla blur pass is used on top of widgets or text.
        g.fill(0,0,width,height,0xA509111C);
        g.fill(cx-174,top-4,cx+174,top+234,0xEE1B2738);
        g.fill(cx-171,top-1,cx+171,top+231,0xFF29364B);
        g.fill(cx-171,top-1,cx+171,top+2,0xFF8CB3C7);
        for(Renderable widget:renderables)widget.render(g,mouseX,mouseY,partial);
        g.drawCenteredString(font,fit(displayName.toUpperCase(Locale.ROOT),46),cx,top+10,0xFFF2E6D2);
        g.drawCenteredString(font,"VOICE INCANTATION TRAINING",cx,top+25,0xFFD0DEED);
        g.drawString(font,"Personal word or phrase:",cx-113,top+43,0xFFD2DEEE);
        VoiceProfile p=VoiceClient.profile();
        int count=p==null?0:p.count(concept);
        g.drawCenteredString(font,"Recognition templates: "+count+"/5",cx,top+163,0xFF9DEBC7);
        g.drawCenteredString(font,fit(state,53),cx,top+181,0xFFEEF3F8);
        if(p!=null && count>0 && p.availablePlaybackSamples(concept)==0)
            g.drawCenteredString(font,"Old v0.4 samples require retraining for audio playback",cx,top+194,0xFFFFC99F);
        else if(mic!=null&&mic.active()){
            int centerY=top+195;
            for(int i=0;i<31;i++){
                int h=(int)(2+mic.level()*(4+6*Math.abs(Math.sin(i*1.4))));
                g.fill(cx-75+i*5,centerY-h/2,cx-73+i*5,centerY+h/2,0xFF9BE2E5);
            }
        }
    }
    private static String fit(String text,int limit){
        return text.length()<=limit?text:text.substring(0,limit-3)+"...";
    }
    @Override public void onClose(){
        cancelled=true;
        if(mic!=null)mic.stop();
        minecraft.setScreen(parent);
    }
}
