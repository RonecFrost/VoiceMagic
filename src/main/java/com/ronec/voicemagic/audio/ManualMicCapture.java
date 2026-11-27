package com.ronec.voicemagic.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;

/**
 * Records precisely one voice-training sample between the player's Start and
 * Stop clicks. Unlike MicCapture, this NEVER splits on silence or truncates
 * the end of a word to satisfy voice-activity detection.
 */
public final class ManualMicCapture {
    public interface Listener {
        void onRecorded(short[] samples);
        void onError(String message);
        void onFinished();
    }

    private static final int OUTPUT_RATE=16000;
    private static final int MAX_SECONDS=13;
    private volatile TargetDataLine line;
    private volatile boolean stopRequested;
    private volatile boolean cancelled;
    private volatile boolean active;
    private volatile double level;
    private final Listener listener;

    public ManualMicCapture(Listener listener) {this.listener=listener;}
    public double level(){return level;}
    public boolean active(){return active;}

    public synchronized void start(){
        if(active)return;
        stopRequested=false;
        cancelled=false;
        active=true;
        Thread worker=new Thread(this::record,"VoiceMagic-manual-recording");
        worker.setDaemon(true);
        worker.start();
    }

    /** Stop at the next 20 ms audio frame, including the tail of the sound. */
    public void stop(){
        stopRequested=true;
        // Deliberately don't close the microphone here. The capture thread must
        // finish its current audio read to retain the last spoken consonant.
    }

    /** Discard audio when leaving the screen. */
    public void cancel(){
        cancelled=true;
        stopRequested=true;
        TargetDataLine current=line;
        if(current!=null)try{current.close();}catch(Exception ignored){}
    }

    private void record(){
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(OUTPUT_RATE*2*3);
        int sampleRate=OUTPUT_RATE;
        try {
            TargetDataLine opened=null;
            for(int rate:new int[]{16000,48000}){
                AudioFormat format=new AudioFormat((float)rate,16,1,true,false);
                DataLine.Info info=new DataLine.Info(TargetDataLine.class,format);
                if(!AudioSystem.isLineSupported(info))continue;
                try {
                    opened=(TargetDataLine)AudioSystem.getLine(info);
                    opened.open(format,rate/5*2);
                    sampleRate=rate;
                    break;
                }catch(LineUnavailableException ex){
                    if(opened!=null){opened.close();opened=null;}
                }
            }
            if(opened==null)throw new LineUnavailableException("No compatible microphone (16 kHz or 48 kHz).");
            line=opened;
            if(cancelled)return;
            opened.start();
            byte[] chunk=new byte[(sampleRate/50)*2];
            int maxBytes=sampleRate*MAX_SECONDS*2;
            while(!cancelled && bytes.size()<maxBytes){
                // Check the stop flag after the read, not before; capture the
                // complete final frame without voice-activity gating.
                int got=opened.read(chunk,0,chunk.length);
                if(got<=0){
                    if(stopRequested)break;
                    continue;
                }
                bytes.write(chunk,0,Math.min(got,maxBytes-bytes.size()));
                double power=0;int frames=got/2;
                for(int i=0;i<frames;i++){
                    int sample=(short)((chunk[i*2]&255)|((chunk[i*2+1]&255)<<8));
                    power+=(double)sample*sample;
                }
                level=Math.min(1,Math.sqrt(power/Math.max(frames,1))/5500.);
                if(stopRequested)break;
            }
            if(!cancelled){
                short[] raw=decode(bytes.toByteArray(),sampleRate);
                listener.onRecorded(trimConservatively(raw));
            }
        }catch(Exception ex){
            if(!cancelled)listener.onError(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage());
        }finally{
            active=false;level=0;
            try{if(line!=null){line.stop();line.close();}}catch(Exception ignored){}
            line=null;
            listener.onFinished();
        }
    }

    static short[] decode(byte[] bytes,int rate){
        int length=bytes.length/2;
        short[] result=new short[(int)(length*16000L/rate)];
        for(int i=0;i<result.length;i++){
            int source=i*rate/16000;
            result[i]=(short)((bytes[source*2]&255)|((bytes[source*2+1]&255)<<8));
        }
        return result;
    }

    /**
     * Remove only idle time around the spoken sample, not quiet phonemes.
     * 250 ms of audio before and after detected speech is retained. We retain
     * the original whole clip when its level is too low to safely trim.
     */
    public static short[] trimConservatively(short[] raw){
        if(raw==null||raw.length<2400)return raw==null?new short[0]:raw;
        int block=160; // 10 ms
        int count=raw.length/block;
        double[] rms=new double[count];
        double highest=0;
        for(int i=0;i<count;i++){
            double energy=0;
            for(int j=i*block;j<(i+1)*block;j++){
                double sample=raw[j];energy+=sample*sample;
            }
            rms[i]=Math.sqrt(energy/block);
            highest=Math.max(highest,rms[i]);
        }
        if(highest<200)return raw;
        double threshold=Math.max(100,Math.min(800,highest*.12));
        int first=0,last=count-1;
        while(first<count&&rms[first]<threshold)first++;
        while(last>=0&&rms[last]<threshold)last--;
        if(first>=count||last<first)return raw;
        int start=Math.max(0,first*block-4000);
        int end=Math.min(raw.length,(last+1)*block+4000);
        if(end-start<2400)return raw;
        return java.util.Arrays.copyOfRange(raw,start,end);
    }
}
