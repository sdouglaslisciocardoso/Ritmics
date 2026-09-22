package br.com.ritmics.core.audio;

/**
 * Computes inexpensive level information for a block of signed 16-bit PCM.
 * The result object is caller-owned so the capture loop can reuse it without
 * allocating on every block.
 */
public final class AudioLevelMeter {
    private AudioLevelMeter() { }

    public static void measure(short[] samples, int offset, int count, MutableLevel out) {
        if (samples == null || out == null) throw new NullPointerException();
        if (offset < 0 || count < 0 || offset > samples.length - count) {
            throw new IllegalArgumentException("invalid PCM range");
        }
        if (count == 0) {
            out.peak = 0f;
            out.rms = 0f;
            out.dbfs = -96f;
            out.clipping = false;
            return;
        }
        long squareSum = 0L;
        int peak = 0;
        boolean clipping = false;
        for (int i = offset; i < offset + count; i++) {
            int sample = samples[i];
            int magnitude = sample == Short.MIN_VALUE ? 32768 : Math.abs(sample);
            if (magnitude > peak) peak = magnitude;
            if (magnitude >= 32700) clipping = true;
            squareSum += (long) sample * sample;
        }
        double rms = Math.sqrt(squareSum / (double) count) / 32768.0;
        double peakRatio = peak / 32768.0;
        out.peak = (float) peakRatio;
        out.rms = (float) rms;
        // Keep a useful finite floor for silence while retaining the true RMS value.
        out.dbfs = rms == 0 ? -96f : (float) Math.max(-96, 20.0 * Math.log10(rms));
        out.clipping = clipping;
    }

    public static final class MutableLevel {
        public float peak;
        public float rms;
        public float dbfs;
        public boolean clipping;
    }
}
