package com.ronec.voicemagic.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ronec.voicemagic.audio.VoiceFeatures;
import com.ronec.voicemagic.audio.VoicePhraseDecoder;
import com.ronec.voicemagic.audio.VoiceRecordings;
import com.ronec.voicemagic.audio.ManualMicCapture;
import com.ronec.voicemagic.ArsVoiceSupport;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Saves local acoustic templates, not raw voice recordings or voice transcripts. */
public final class VoiceProfile {
    public static final class Entry {
        public String alias="";
        public List<double[][]> templates=new ArrayList<>();
    }
    public record Match(String concept,double score) {}
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private final Map<String,Entry> words=new HashMap<>();
    private final Path file;
    private final Path recordingsRoot;
    private VoiceProfile(Path file){
        this.file=file;
        this.recordingsRoot=file.getParent().getParent().resolve("recordings")
                .resolve(file.getFileName().toString().replace(".json",""));
    }

    public static VoiceProfile load(UUID uuid) {
        Path file=Minecraft.getInstance().gameDirectory.toPath().resolve("config/voicemagic/profiles/"+uuid+".json");
        VoiceProfile profile=new VoiceProfile(file);
        try {
            if(Files.exists(file)) {
                Stored state=GSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),Stored.class);
                if(state!=null && state.words!=null) {
                    for(var w:state.words.entrySet()) if(ArsVoiceSupport.isConceptKey(w.getKey()) && w.getValue()!=null && w.getValue().templates!=null)
                        profile.words.put(w.getKey(),w.getValue());
                }
            }
        }catch(Exception ignored) { /* malformed profile cannot crash Minecraft */ }
        return profile;
    }
    private static final class Stored { Map<String,Entry> words=new HashMap<>(); }
    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Stored state=new Stored(); state.words.putAll(words);
            Files.writeString(file,GSON.toJson(state),StandardCharsets.UTF_8);
        }catch(Exception ex){System.err.println("[VoiceMagic] Profile save failed: "+ex.getMessage());}
    }
    public synchronized int count(String concept) {
        Entry entry=words.get(concept);
        return entry==null?0:entry.templates.size();
    }
    public synchronized String alias(String concept) {
        Entry entry=words.get(concept);return entry==null?concept:entry.alias;
    }
    public synchronized void reset(String concept) {
        words.remove(concept);
        VoiceRecordings.clear(recordingsRoot,concept);
        save();
    }
    public synchronized int availablePlaybackSamples(String concept) {
        return VoiceRecordings.count(recordingsRoot,concept);
    }
    public synchronized boolean playRandomRecording(String concept) {
        return VoiceRecordings.playRandom(recordingsRoot,concept);
    }
    public synchronized int teach(String concept,String alias,short[] raw) {
        if(!ArsVoiceSupport.isConceptKey(concept))return 0;
        double[][] sample=VoiceFeatures.extract(ManualMicCapture.trimForMatching(raw));
        if(sample.length<6)return count(concept);
        Entry entry=words.computeIfAbsent(concept,c->new Entry());
        String cleaned=alias==null?concept:alias.strip();
        if(cleaned.length()>24) cleaned=cleaned.substring(0,24);
        if(!entry.alias.equals(cleaned)) {
            entry.templates.clear();
            VoiceRecordings.clear(recordingsRoot,concept);
        }
        entry.alias=cleaned.isEmpty()?concept:cleaned;
        if(entry.templates.size()<5) {
            int index=entry.templates.size();
            entry.templates.add(sample);
            try { VoiceRecordings.save(recordingsRoot,concept,index,raw); }
            catch(java.io.IOException error) {
                System.err.println("[VoiceMagic] Could not save training recording for playback: "+error.getMessage());
            }
        }
        save();return entry.templates.size();
    }
    public synchronized Match match(short[] raw) { return match(raw, s -> true); }
    public synchronized Match match(short[] raw, Predicate<String> allowed) {
        double[][] input=VoiceFeatures.extract(raw);
        if(input.length<6)return null;
        String bestConcept=null;
        double best=Double.POSITIVE_INFINITY, second=Double.POSITIVE_INFINITY;
        for(var pair:words.entrySet()) {
            if(!allowed.test(pair.getKey()))continue;
            List<double[][]> refs=pair.getValue().templates;
            if(refs.size()<5)continue;
            double distance=Double.POSITIVE_INFINITY;
            for(double[][] reference:refs)distance=Math.min(distance,VoiceFeatures.distance(input,reference));
            // Per-word enrollment calibration: tolerate variations in repeated pronunciations.
            double sum=0;int pairs=0;
            for(int i=0;i<refs.size();i++)for(int j=i+1;j<refs.size();j++){
                double d=VoiceFeatures.distance(refs.get(i),refs.get(j));
                if(Double.isFinite(d)){sum+=d;pairs++;}
            }
            double within=pairs==0?0:sum/pairs;
            double threshold=Math.max(.035,Math.min(1.50,within*2.1+.025));
            if(distance>threshold)continue;
            if(distance<best) { second=best;best=distance;bestConcept=pair.getKey(); }
            else second=Math.min(second,distance);
        }
        if(bestConcept==null || (Double.isFinite(second) && second-best<Math.max(.015,best*.12)))return null;
        return new Match(bestConcept,best);
    }
    /** Decode a full spoken phrase into known glyphs, even without pauses between words. */
    public synchronized List<String> matchContinuous(short[] raw,Predicate<String> allowed,int maxWords) {
        List<VoicePhraseDecoder.Template> dictionary=new ArrayList<>();
        for(var e:words.entrySet()) {
            if(allowed.test(e.getKey())&&e.getValue().templates.size()>=5)
                dictionary.add(new VoicePhraseDecoder.Template(e.getKey(),List.copyOf(e.getValue().templates)));
        }
        return VoicePhraseDecoder.decode(raw,dictionary,maxWords);
    }
    public synchronized List<String> learned() {
        List<String> out=new ArrayList<>();
        for(var e:words.entrySet())if(e.getValue().templates.size()>=5)out.add(e.getKey());
        out.sort(String::compareTo);return out;
    }
}
