package com.ronec.voicemagic.client;

import com.ronec.voicemagic.audio.ManualMicCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/**
 * Five deliberate takes: Start -> speak -> Stop -> Save & Next.
 * The audio recorder never relies on a silent gap to delimit the take.
 */
public final class VoiceTrainerScreen extends Screen {
    private final Screen parent;
    private final String concept;
    private final String displayName;
    private EditBox word;
    private ManualMicCapture mic;
    private Button recordButton;
    private Button nextButton;
    private Button retryButton;
    private Button playbackButton;
    private Button forgetButton;
    private short[] pending;
    private String state="Record five individual takes of your personal incantation.";
    private boolean processing;
    private boolean cancelled;
    private int top;

    public VoiceTrainerScreen(Screen parent,String concept,String displayName){
        super(Component.literal("Voice Magic - "+displayName));
        this.parent=parent;
        this.concept=concept;
        this.displayName=displayName;
    }

    @Override protected void init(){
        int cx=width/2;
        top=Math.max(3,(height-236)/2);
        VoiceProfile p=VoiceClient.profile();
        word=new EditBox(font,cx-113,top+54,226,20,Component.literal("Incantation label"));
        word.setMaxLength(24);
        if(p!=null && p.count(concept)>0)word.setValue(p.alias(concept));
        else word.setValue(displayName);
        addRenderableWidget(word);

        recordButton=addRenderableWidget(Button.builder(Component.literal("Start Recording"),
            b->toggleRecording()).bounds(cx-113,top+81,111,20).build());
        nextButton=addRenderableWidget(Button.builder(Component.literal("Save & Next"),
            b->saveAndNext()).bounds(cx+2,top+81,111,20).build());
        retryButton=addRenderableWidget(Button.builder(Component.literal("Retry Take"),
            b->retryTake()).bounds(cx-113,top+106,111,20).build());
        playbackButton=addRenderableWidget(Button.builder(Component.literal("Play Saved"),
            b->playSaved()).bounds(cx+2,top+106,111,20).build());
        forgetButton=addRenderableWidget(Button.builder(Component.literal("Forget All & Retrain"),b->{
            if(mic!=null||processing)return;
            VoiceProfile profile=VoiceClient.profile();
            if(profile!=null)profile.reset(concept);
            pending=null;
            state="Old samples cleared. Ready to record sample 1 of 5.";
            updateControls();
        }).bounds(cx-113,top+132,226,20).build());

        addRenderableWidget(Button.builder(Component.literal("Back to Voice Book"),b->onClose())
            .bounds(cx-113,top+207,226,20).build());
        updateControls();
    }

    private void updateControls(){
        VoiceProfile p=VoiceClient.profile();
        int count=p==null?0:p.count(concept);
        boolean recording=mic!=null && !processing;
        if(recordButton!=null){
            recordButton.active=!processing && (recording || (pending==null && count<5));
            recordButton.setMessage(Component.literal(recording?"Stop Recording":"Start Recording"));
        }
        if(nextButton!=null){
            nextButton.active=!processing && mic==null && pending!=null && count<5;
            nextButton.setMessage(Component.literal(count==4?"Save Final Take":"Save & Next"));
        }
        if(retryButton!=null)retryButton.active=!processing && mic==null && pending!=null;
        if(playbackButton!=null){
            int clips=p==null?0:p.availablePlaybackSamples(concept);
            playbackButton.active=mic==null&&!processing&&clips>0;
            playbackButton.setMessage(Component.literal("Play Saved ("+clips+"/5)"));
        }
        if(forgetButton!=null)forgetButton.active=mic==null&&!processing;
        if(word!=null)word.setEditable(mic==null&&!processing&&pending==null&&count==0);
    }

    private void toggleRecording(){
        if(processing)return;
        if(mic!=null){
            processing=true;
            state="Finishing this take without cutting off the last sound...";
            mic.stop();
            updateControls();
            return;
        }
        VoiceProfile p=VoiceClient.profile();
        if(p==null)return;
        if(p.count(concept)>=5){state="All five samples are already trained.";return;}
        if(pending!=null){state="Save & Next or Retry Take before recording again.";return;}
        if(word.getValue().strip().isEmpty()){
            state="Enter a name for your spoken incantation.";return;
        }

        cancelled=false;
        int sampleNumber=p.count(concept)+1;
        state="RECORDING "+sampleNumber+"/5 - speak fully, then click Stop Recording.";
        mic=new ManualMicCapture(new ManualMicCapture.Listener(){
            @Override public void onRecorded(short[] audio){
                Minecraft.getInstance().execute(()->{
                    if(cancelled||VoiceTrainerScreen.this.minecraft==null)return;
                    if(audio==null||audio.length<3200){
                        pending=null;
                        state="Too short. Start again and say the entire word.";
                        return;
                    }
                    // Avoid storing silent microphone captures as usable words.
                    double power=0;int stride=Math.max(1,audio.length/5000);
                    int n=0;
                    for(int i=0;i<audio.length;i+=stride){
                        power+=(double)audio[i]*audio[i];n++;
                    }
                    double rms=Math.sqrt(power/Math.max(1,n));
                    if(rms<70){
                        pending=null;
                        state="Audio was too quiet. Check your microphone and retry.";
                        return;
                    }
                    pending=audio;
                    state="Take "+sampleNumber+" captured. Save & Next, or Retry Take.";
                });
            }
            @Override public void onError(String error){
                Minecraft.getInstance().execute(()->{
                    if(!cancelled)state="Microphone error: "+error;
                });
            }
            @Override public void onFinished(){
                Minecraft.getInstance().execute(()->{
                    if(cancelled||VoiceTrainerScreen.this.minecraft==null)return;
                    mic=null;
                    processing=false;
                    updateControls();
                });
            }
        });
        mic.start();
        updateControls();
    }

    private void saveAndNext(){
        if(processing||mic!=null||pending==null)return;
        VoiceProfile p=VoiceClient.profile();
        if(p==null)return;
        String alias=word.getValue().strip();
        if(alias.isEmpty()){state="Enter a name for your spoken incantation.";return;}
        int before=p.count(concept);
        int count=p.teach(concept,alias,pending);
        if(count<=before){
            state="Could not analyze the recording. Please retry this take.";
            updateControls();
            return;
        }
        pending=null;
        if(count>=5)state="Training complete - all five deliberate recordings saved!";
        else state="Saved sample "+count+"/5. Start Recording for sample "+(count+1)+"/5.";
        updateControls();
    }

    private void retryTake(){
        if(mic!=null||processing)return;
        pending=null;
        VoiceProfile p=VoiceClient.profile();
        state="Take discarded. Ready to record sample "+((p==null?0:p.count(concept))+1)+"/5.";
        updateControls();
    }

    private void playSaved(){
        if(mic!=null||processing)return;
        VoiceProfile p=VoiceClient.profile();
        if(p!=null&&p.playRandomRecording(concept))
            state="Playing one randomly selected saved take.";
        else state="No audio playback files found. Retrain if these are older samples.";
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        int cx=width/2;
        // Background is rendered before widgets so buttons and captions remain sharp.
        g.fill(0,0,width,height,0xA509111C);
        g.fill(cx-174,top-4,cx+174,top+234,0xEE1B2738);
        g.fill(cx-171,top-1,cx+171,top+231,0xFF29364B);
        g.fill(cx-171,top-1,cx+171,top+2,0xFF8CB3C7);
        for(Renderable widget:renderables)widget.render(g,mouseX,mouseY,partial);
        g.drawCenteredString(font,fit(displayName.toUpperCase(Locale.ROOT),46),cx,top+8,0xFFF2E6D2);
        g.drawCenteredString(font,"MANUAL INCANTATION TRAINING",cx,top+23,0xFFD0DEED);
        g.drawString(font,"Your personal word or phrase:",cx-113,top+42,0xFFE1E8F2);
        VoiceProfile p=VoiceClient.profile();
        int count=p==null?0:p.count(concept);
        int current=Math.min(5,count+1);
        g.drawCenteredString(font,count>=5?"ALL FIVE TAKES SAVED":"Sample "+current+" / 5  |  Saved "+count+" / 5",
            cx,top+158,0xFF9DEBC7);
        g.drawCenteredString(font,fit(state,54),cx,top+177,0xFFF0F3FB);
        if(p!=null&&count>0&&p.availablePlaybackSamples(concept)==0)
            g.drawCenteredString(font,"Old training samples need retraining for playback.",cx,top+192,0xFFFFC99F);
        else if(mic!=null&&mic.active()){
            double volume=mic.level();
            int centerY=top+195;
            for(int i=0;i<29;i++){
                int h=(int)(3+volume*(4+10*Math.abs(Math.sin(i*1.7))));
                g.fill(cx-70+i*5,centerY-h/2,cx-68+i*5,centerY+h/2,0xFF9BE2E5);
            }
        }else if(pending!=null)
            g.drawCenteredString(font,"Take ready: Save & Next or Retry Take",cx,top+192,0xFFB5E4E4);
    }

    private static String fit(String text,int limit){
        return text.length()<=limit?text:text.substring(0,limit-3)+"...";
    }

    @Override public void onClose(){
        cancelled=true;
        if(mic!=null)mic.cancel();
        minecraft.setScreen(parent);
    }
}
