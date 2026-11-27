package com.ronec.voicemagic.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Local, compressed PCM storage and optional playback for five training samples. */
public final class VoiceRecordings {
    private VoiceRecordings() {}
    private static final int MAX_SAMPLES=16000*13;
    private static final AudioFormat PLAYBACK_FORMAT=new AudioFormat(16000f,16,1,true,false);

    private static String safeKey(String key) {
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b=new StringBuilder();
            for(int i=0;i<16;i++)b.append(String.format("%02x",hash[i]&255));
            return b.toString();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static Path path(Path root,String key,int index) {
        if(index<0||index>=5)throw new IllegalArgumentException("Only five recordings per concept");
        return root.resolve(safeKey(key)).resolve("sample-"+index+".pcm.gz");
    }
    public static void save(Path root,String key,int index,short[] pcm)throws IOException {
        if(pcm==null||pcm.length<3000||pcm.length>MAX_SAMPLES)
            throw new IOException("Audio clip length out of range");
        Path file=path(root,key,index);
        Files.createDirectories(file.getParent());
        try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))){
            out.writeInt(pcm.length);
            for(short s:pcm)out.writeShort(s);
        }
    }
    public static int count(Path root,String key) {
        int count=0;
        for(int i=0;i<5;i++)if(Files.isRegularFile(path(root,key,i)))count++;
        return count;
    }
    public static boolean playRandom(Path root,String key) {
        List<Path> files=new ArrayList<>();
        for(int i=0;i<5;i++){
            Path p=path(root,key,i);
            if(Files.isRegularFile(p))files.add(p);
        }
        if(files.isEmpty())return false;
        Path selected=files.get(ThreadLocalRandom.current().nextInt(files.size()));
        Thread play=new Thread(()->{
            try {
                short[] samples;
                try(DataInputStream in=new DataInputStream(new GZIPInputStream(Files.newInputStream(selected)))){
                    int count=in.readInt();
                    if(count<3000||count>MAX_SAMPLES)return;
                    samples=new short[count];
                    for(int i=0;i<count;i++)samples[i]=in.readShort();
                }
                byte[] bytes=new byte[samples.length*2];
                for(int i=0;i<samples.length;i++){
                    bytes[i*2]=(byte)(samples[i]&255);
                    bytes[i*2+1]=(byte)((samples[i]>>>8)&255);
                }
                DataLine.Info info=new DataLine.Info(SourceDataLine.class,PLAYBACK_FORMAT);
                SourceDataLine line=(SourceDataLine)AudioSystem.getLine(info);
                try {
                    line.open(PLAYBACK_FORMAT);
                    line.start();
                    int at=0;
                    while(at<bytes.length)at+=line.write(bytes,at,Math.min(8192,bytes.length-at));
                    line.drain();
                }finally{line.stop();line.close();}
            }catch(Exception ex) {
                System.err.println("[VoiceMagic] Playback failed: "+ex.getMessage());
            }
        },"VoiceMagic-training-playback");
        play.setDaemon(true);
        play.start();
        return true;
    }
    public static void clear(Path root,String key) {
        for(int i=0;i<5;i++){
            try {Files.deleteIfExists(path(root,key,i));}
            catch(IOException ex){System.err.println("[VoiceMagic] Could not remove old recording: "+ex.getMessage());}
        }
        try{Files.deleteIfExists(root.resolve(safeKey(key)));}
        catch(IOException ignored){}
    }
}
