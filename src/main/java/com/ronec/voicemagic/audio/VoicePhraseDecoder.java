package com.ronec.voicemagic.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Experimental offline connected-word recognizer for an individually trained vocabulary.
 * Compares possible adjacent portions of one continuous utterance against enrolled words.
 * No cloud STT, language dictionary, or pauses between words are needed.
 */
public final class VoicePhraseDecoder {
    private VoicePhraseDecoder() {}
    private static final int SKIP=3; // 30 ms per feature frame
    private static final int PROBES=22;
    private static final double[] STRETCH={.72,.86,1.0,1.16,1.34,1.53};

    public record Template(String key,List<double[][]> recordings) {}
    private record Word(String key,List<double[][]> examples,double threshold,int nominalLength){}
    private record Step(String word,int from,int usedFrames){}
    private record Result(double cost,int words,int used) {}

    public static List<String> decode(short[] audio,List<Template> learned,int maxWords){
        if(audio==null || learned.isEmpty() || audio.length<3600 || audio.length>16000*13)
            return List.of();
        double[][] in=thin(VoiceFeatures.extract(audio));
        if(in.length<5)return List.of();

        List<Word> dictionary=new ArrayList<>();
        for(Template t:learned){
            if(t==null || t.recordings()==null || t.recordings().size()<5)continue;
            List<double[][]> refs=new ArrayList<>();
            for(double[][] rec:t.recordings()) {
                if(rec!=null && rec.length>=6)refs.add(thin(rec));
            }
            if(refs.size()<5)continue;
            // Match against several examples so this tolerates individual pronunciation variation.
            double within=0;int pairs=0;
            for(int i=0;i<refs.size();i++)for(int j=i+1;j<refs.size();j++){
                double d=compare(refs.get(i),0,refs.get(i).length,refs.get(j),null);
                if(Double.isFinite(d)){within+=d;pairs++;}
            }
            within=pairs==0?.25:within/pairs;
            List<double[][]> representatives=pickRepresentatives(refs,3);
            int nominal=(int)Math.round(refs.stream().mapToInt(r->r.length).average().orElse(12));
            dictionary.add(new Word(t.key(),representatives,
                    Math.max(.055,Math.min(1.65,within*2.15+.04)),nominal));
        }
        if(dictionary.isEmpty())return List.of();

        int n=in.length;
        // Prefix sums let each candidate fragment be locally normalized, just like a
        // separately trained word, even though the entire utterance was one phrase.
        double[][] sum=new double[n+1][13];
        for(int i=0;i<n;i++)for(int k=0;k<13;k++)sum[i+1][k]=sum[i][k]+in[i][k];
        double[] dp=new double[n+1];
        int[] used=new int[n+1],wordCounts=new int[n+1];
        Step[] predecessor=new Step[n+1];
        Arrays.fill(dp,Double.POSITIVE_INFINITY);
        dp[0]=0;
        for(int i=0;i<n;i++){
            if(!Double.isFinite(dp[i]))continue;
            if(dp[i]+.58<dp[i+1]){
                dp[i+1]=dp[i]+.58;
                used[i+1]=used[i];
                wordCounts[i+1]=wordCounts[i];
                predecessor[i+1]=new Step(null,i,0);
            }
            if(wordCounts[i]>=maxWords)continue;
            for(Word w:dictionary) {
                for(double ratio:STRETCH){
                    int end=i+Math.max(4,(int)Math.round(w.nominalLength()*ratio));
                    if(end>n)continue;
                    int length=end-i;
                    double best=Double.POSITIVE_INFINITY;
                    for(double[][] ref:w.examples()) {
                        double d=compare(in,i,end,ref,sum);
                        if(d<best)best=d;
                    }
                    if(best>w.threshold()*1.48)continue;
                    double ratioPenalty=.35*Math.abs(Math.log((double)length/w.nominalLength()));
                    double newCost=dp[i]+1.10+2.1*best/w.threshold()+ratioPenalty;
                    if(newCost<dp[end]){
                        dp[end]=newCost;
                        used[end]=used[i]+length;
                        wordCounts[end]=wordCounts[i]+1;
                        predecessor[end]=new Step(w.key(),i,length);
                    }
                }
            }
        }
        if(predecessor[n]==null)return List.of();
        List<String> ordered=new ArrayList<>();
        int index=n;
        while(index>0 && predecessor[index]!=null){
            Step s=predecessor[index];
            if(s.word()!=null)ordered.add(s.word());
            index=s.from();
        }
        Collections.reverse(ordered);
        // Reject phrases with large unintelligible portions; avoids matching background noise.
        if(ordered.isEmpty()||ordered.size()>maxWords||used[n]<(int)(n*.58))return List.of();
        return ordered;
    }

    /** Keep one frame every 30 ms to bound the connected-speech search cost. */
    private static double[][] thin(double[][] source) {
        if(source==null)return new double[0][];
        double[][] out=new double[(source.length+SKIP-1)/SKIP][];
        for(int i=0;i<out.length;i++)out[i]=source[i*SKIP];
        return out;
    }

    private static List<double[][]> pickRepresentatives(List<double[][]> samples,int count){
        Map<double[][],Double> means=new HashMap<>();
        for(double[][] sample:samples){
            double sum=0;int n=0;
            for(double[][] other:samples)if(other!=sample){
                sum+=compare(sample,0,sample.length,other,null);n++;
            }
            means.put(sample,n==0?0:sum/n);
        }
        List<double[][]> result=new ArrayList<>(samples);
        result.sort(Comparator.comparingDouble(means::get));
        return result.subList(0,Math.min(count,result.size()));
    }

    /**
     * Fast locally mean-normalized approximate alignment. Duration changes are handled
     * by testing multiple boundary lengths; no sentence transcript is required.
     */
    private static double compare(double[][] input,int from,int to,double[][] example,double[][] prefix){
        int len=to-from;
        if(len<4||example.length<4)return Double.POSITIVE_INFINITY;
        double[] mean=new double[13];
        if(prefix==null){
            for(int i=from;i<to;i++)for(int k=1;k<13;k++)mean[k]+=input[i][k];
            for(int k=1;k<13;k++)mean[k]/=len;
        }else{
            for(int k=1;k<13;k++)mean[k]=(prefix[to][k]-prefix[from][k])/len;
        }
        double result=0;
        for(int p=0;p<PROBES;p++){
            double relative=(p+.5)/PROBES;
            int ai=from+Math.min(len-1,(int)(relative*len));
            int bi=Math.min(example.length-1,(int)(relative*example.length));
            double err=0;
            for(int k=1;k<13;k++){
                double delta=input[ai][k]-mean[k]-example[bi][k];
                err+=delta*delta;
            }
            result+=Math.sqrt(err/12.0);
        }
        return result/PROBES;
    }
}
