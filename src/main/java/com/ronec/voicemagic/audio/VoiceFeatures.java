package com.ronec.voicemagic.audio;

/**
 * Dependency-free, locally computed voiceprint features for short utterances.
 * MFCC-like mel cepstra + constrained dynamic time warping; NOT speech-to-text.
 * Deliberately separated from Minecraft for unit testing and replacement later.
 */
public final class VoiceFeatures {
    private VoiceFeatures() {}
    private static final int SAMPLE_RATE = 16000;
    private static final int WINDOW = 400;
    private static final int HOP = 160;
    private static final int FFT = 512;
    private static final int MEL = 26;
    private static final int COEFFS = 13;
    private static final double[][] FILTERS = buildFilters();

    public static double[][] extract(short[] pcm) {
        if (pcm == null || pcm.length < WINDOW * 3) return new double[0][];
        int count = 1 + (pcm.length - WINDOW) / HOP;
        double[][] out = new double[count][COEFFS];
        for (int frame = 0; frame < count; frame++) {
            double[] real = new double[FFT], imag = new double[FFT];
            int start = frame * HOP;
            for (int i = 0; i < WINDOW; i++) {
                double previous = i == 0 ? 0 : pcm[start + i - 1];
                real[i] = ((pcm[start+i] - .95 * previous) / 32768.0)
                        * (.54 - .46 * Math.cos(2 * Math.PI * i / (WINDOW - 1)));
            }
            fft(real, imag);
            double[] mel = new double[MEL];
            for (int k = 0; k < MEL; k++) {
                double power = 0;
                for (int f = 0; f <= FFT / 2; f++) {
                    double p = real[f]*real[f] + imag[f]*imag[f];
                    power += p * FILTERS[k][f];
                }
                mel[k] = Math.log(Math.max(power, 1.0e-12));
            }
            for (int c = 0; c < COEFFS; c++) {
                double sum = 0;
                for (int m = 0; m < MEL; m++) sum += mel[m] * Math.cos(Math.PI * c * (m+.5) / MEL);
                out[frame][c] = sum / MEL;
            }
        }
        // Remove channel gain/microphone coloration; preserve contour across time.
        for (int c = 0; c < COEFFS; c++) {
            double mean = 0;
            for (double[] values : out) mean += values[c];
            mean /= count;
            for (double[] values : out) values[c] -= mean;
        }
        return out;
    }

    public static double distance(double[][] a, double[][] b) {
        if (a.length < 3 || b.length < 3) return Double.POSITIVE_INFINITY;
        if (Math.max(a.length, b.length) > Math.min(a.length, b.length) * 2.5)
            return Double.POSITIVE_INFINITY;
        int n=a.length, m=b.length;
        double[] prev = new double[m+1], curr = new double[m+1];
        java.util.Arrays.fill(prev, Double.POSITIVE_INFINITY);
        prev[0] = 0;
        int band = Math.max(8, Math.abs(n-m)+Math.max(n,m)/3);
        for (int i = 1; i <= n; i++) {
            java.util.Arrays.fill(curr, Double.POSITIVE_INFINITY);
            int lo = Math.max(1, i-band), hi = Math.min(m, i+band);
            for (int j=lo; j<=hi; j++) {
                double cost = 0;
                for (int k=1; k<COEFFS; k++) {
                    double delta = a[i-1][k]-b[j-1][k];
                    cost += delta * delta;
                }
                cost = Math.sqrt(cost/(COEFFS-1));
                curr[j] = cost + Math.min(prev[j-1], Math.min(prev[j], curr[j-1]));
            }
            double[] tmp=prev; prev=curr; curr=tmp;
        }
        return prev[m]/Math.max(n,m);
    }

    private static double[][] buildFilters() {
        double min = 2595*Math.log10(1+80.0/700);
        double max = 2595*Math.log10(1+7600.0/700);
        int[] points = new int[MEL+2];
        for (int i=0; i<points.length; i++) {
            double hz = 700*(Math.pow(10,(min+(max-min)*i/(MEL+1))/2595)-1);
            points[i] = Math.max(0,Math.min(FFT/2, (int)Math.floor((FFT+1)*hz/SAMPLE_RATE)));
        }
        double[][] filters = new double[MEL][FFT/2+1];
        for (int i=0; i<MEL; i++) {
            for (int f=points[i]; f<points[i+1]; f++) {
                if (points[i+1] != points[i]) filters[i][f] = (f-points[i])*1.0/(points[i+1]-points[i]);
            }
            for (int f=points[i+1]; f<points[i+2]; f++) {
                if (points[i+2]!=points[i+1]) filters[i][f] = (points[i+2]-f)*1.0/(points[i+2]-points[i+1]);
            }
        }
        return filters;
    }

    private static void fft(double[] re, double[] im) {
        int n=re.length;
        for (int i=1,j=0; i<n; i++) {
            int bit=n>>1;
            for (; (j&bit)!=0; bit>>=1) j^=bit;
            j^=bit;
            if (i<j) { double tmp=re[i];re[i]=re[j];re[j]=tmp; tmp=im[i];im[i]=im[j];im[j]=tmp; }
        }
        for (int len=2;len<=n;len<<=1) {
            double angle=-2*Math.PI/len, wr0=Math.cos(angle), wi0=Math.sin(angle);
            for (int i=0;i<n;i+=len) {
                double wr=1, wi=0;
                for (int j=0;j<len/2;j++) {
                    int u=i+j,v=i+j+len/2;
                    double tr=re[v]*wr-im[v]*wi, ti=re[v]*wi+im[v]*wr;
                    re[v]=re[u]-tr; im[v]=im[u]-ti;
                    re[u]+=tr; im[u]+=ti;
                    double nw=wr*wr0-wi*wi0; wi=wr*wi0+wi*wr0; wr=nw;
                }
            }
        }
    }
}
