package com.ronec.voicemagic.audio;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

/** One locally accessed microphone stream. Silence separates utterances. */
public final class MicCapture {
    public interface Listener {
        void onUtterance(short[] pcm);
        /** Optional partial audio: used only for live visual glyph previews. */
        default void onPreview(short[] pcm) {}
        void onFinished();
        void onError(String message);
    }
    private volatile boolean running;
    private volatile TargetDataLine line;
    private volatile double level;
    private volatile String status="Microphone idle";
    private Thread thread;
    private final boolean stopAfterOne;
    private final Listener listener;

    public MicCapture(boolean stopAfterOne, Listener listener) {
        this.stopAfterOne=stopAfterOne;
        this.listener=listener;
    }
    public double level() { return level; }
    public String status() { return status; }
    public synchronized void start() {
        if (running) return;
        running=true;
        thread=new Thread(this::capture, "VoiceMagic-microphone");
        thread.setDaemon(true);
        thread.start();
    }
    public void stop() {
        running=false;
        TargetDataLine active=line;
        if (active!=null) { try { active.stop(); active.close(); } catch(Exception ignored) {} }
    }
    public boolean active() { return running; }

    private void capture() {
        ByteArrayOutputStream utterance = new ByteArrayOutputStream();
        boolean talking=false;
        int silence=0, voiced=0;
        long lastPreview=0;
        double noise=100;
        int sampleRate=16000;
        try {
            TargetDataLine opened=null;
            for (int rate : new int[]{16000, 48000}) {
                AudioFormat format = new AudioFormat((float)rate,16,1,true,false);
                DataLine.Info info=new DataLine.Info(TargetDataLine.class,format);
                if (!AudioSystem.isLineSupported(info)) continue;
                try {
                    opened=(TargetDataLine)AudioSystem.getLine(info);
                    opened.open(format, rate/5*2);
                    sampleRate=rate;
                    break;
                } catch(LineUnavailableException e) {
                    if (opened!=null) {opened.close();opened=null;}
                }
            }
            if (opened==null) throw new LineUnavailableException("No compatible 16 kHz or 48 kHz microphone found. Check Windows microphone permissions and input device.");
            line=opened;
            int chunkSamples=sampleRate/50; // 20ms
            byte[] buffer=new byte[chunkSamples*2];
            opened.start();
            status="Listening";
            while (running) {
                int read=opened.read(buffer,0,buffer.length);
                if(read<=0) continue;
                int samples=read/2;
                double sum=0;
                for(int i=0;i<samples;i++) {
                    int s=(short)((buffer[2*i]&255)|((buffer[2*i+1]&255)<<8));
                    sum+=s*(double)s;
                }
                double rms=Math.sqrt(sum/Math.max(1,samples));
                level=Math.min(1,rms/5000);
                boolean voice=rms>Math.max(260,noise*2.8);
                if (!voice && !talking) noise=Math.min(1500,noise*.96+rms*.04);
                if (voice) {
                    talking=true; silence=0; voiced+=samples;
                    utterance.write(buffer,0,read);
                } else if(talking) {
                    silence+=samples;
                    utterance.write(buffer,0,read);
                }
                if(talking && voiced>=sampleRate*.48) {
                    long now=System.nanoTime();
                    if(now-lastPreview>850_000_000L) {
                        lastPreview=now;
                        byte[] clip=utterance.toByteArray();
                        listener.onPreview(toShorts(clip,clip.length,sampleRate));
                    }
                }
                if(talking && (silence>=sampleRate*.32 || utterance.size()>=sampleRate*2*12.0)) {
                    int trailingBytes=(int)(Math.min(silence,sampleRate*.32)*2);
                    if (voiced>=sampleRate*.14) {
                        short[] audio=toShorts(utterance.toByteArray(),Math.max(0,utterance.size()-trailingBytes), sampleRate);
                        listener.onUtterance(audio);
                        if(stopAfterOne) { running=false; break; }
                    }
                    utterance.reset(); talking=false; silence=0; voiced=0;
                }
            }
            if (!stopAfterOne && talking && voiced>=sampleRate*.14) {
                listener.onUtterance(toShorts(utterance.toByteArray(),utterance.size(),sampleRate));
            }
        } catch(Exception ex) {
            if(running) {status="Microphone error";listener.onError(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage());}
        } finally {
            running=false; level=0;
            try {if(line!=null){line.stop();line.close();}}catch(Exception ignored){}
            line=null;
            listener.onFinished();
        }
    }

    private static short[] toShorts(byte[] b,int bytes,int sampleRate) {
        int length=bytes/2;
        short[] out=new short[(int)(length*16000L/sampleRate)];
        for(int i=0;i<out.length;i++) {
            int src=i*sampleRate/16000;
            out[i]=(short)((b[src*2]&255)|((b[src*2+1]&255)<<8));
        }
        return out;
    }
}
