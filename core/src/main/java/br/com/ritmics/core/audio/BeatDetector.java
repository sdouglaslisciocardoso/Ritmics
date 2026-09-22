package br.com.ritmics.core.audio;

/** Streaming mono PCM16 transient detector. One instance per continuous input run.
 * No Android dependencies, musical grid, allocations or click blanking in process(). */
public final class BeatDetector {
    public interface Listener { void onOnset(long frame, float strength); }
    private final int rate, window, refractory, quietFrames;
    private final double hpPole;
    private final float[] history;
    private final Listener listener;
    private long nextFrame, lastOnset = Long.MIN_VALUE / 2;
    private int inWindow, quietWindows;
    private double previousInput, previousHighPass, energy, slow, noise = .00015;
    private double sensitivity = .5, releaseThreshold;
    private boolean armed = true;
    private long detections;

    public BeatDetector(int sampleRate, Listener listener) {
        if (sampleRate < 8000 || sampleRate > 192000 || listener == null)
            throw new IllegalArgumentException("sampleRate/listener");
        rate = sampleRate; this.listener = listener;
        window = Math.max(1, sampleRate / 1000);
        refractory = sampleRate * 40 / 1000; // below 62.5 ms at 240 BPM with sixteenth notes.
        quietFrames = Math.max(1, sampleRate / 2000);
        history = new float[sampleRate * 12 / 1000];
        hpPole = Math.exp(-2 * Math.PI * 60 / sampleRate);
    }
    public void setSensitivity(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("sensitivity");
        sensitivity = value;
    }
    public void process(short[] pcm, int offset, int count, long firstFrame) {
        if (offset < 0 || count < 0 || offset > pcm.length - count || firstFrame != nextFrame)
            throw new IllegalArgumentException("Non-contiguous PCM or invalid range");
        for (int i = offset; i < offset + count; i++) {
            double x = pcm[i] / 32768.0;
            double y = x - previousInput + hpPole * previousHighPass;
            previousInput = x; previousHighPass = y;
            history[(int) (nextFrame % history.length)] = (float) Math.abs(y);
            energy += y * y; nextFrame++; inWindow++;
            if (inWindow < window) continue;
            double rms = Math.sqrt(energy / inWindow);
            energy = 0; inWindow = 0;
            double threshold = Math.max(.002 + (1 - sensitivity) * .018,
                    noise * (2.5 + (1 - sensitivity) * 5.5));
            if (nextFrame < rate / 2) {
                noise += (rms - noise) * .03; // Settling, not latency calibration.
            } else if (armed && nextFrame - lastOnset >= refractory
                    && rms > threshold && rms > slow * 1.7) {
                long onset = locateOnset(Math.max(noise * 2, threshold * .18));
                if (onset - lastOnset >= refractory) {
                    lastOnset = onset; detections++;
                    armed = false; quietWindows = 0; releaseThreshold = threshold * .65;
                    listener.onOnset(onset, (float) rms);
                }
            }
            if (!armed) {
                quietWindows = rms < releaseThreshold ? quietWindows + 1 : 0;
                if (quietWindows >= 8 && nextFrame - lastOnset >= refractory) armed = true;
            } else if (rms < threshold) {
                noise += (Math.min(rms, noise * 2 + .0001) - noise) * .002;
            }
            slow += (rms - slow) * .04;
        }
    }
    private long locateOnset(double threshold) {
        long earliest = Math.max(0, nextFrame - history.length);
        long onset = nextFrame - window;
        int quiet = 0;
        for (long f = nextFrame - 1; f >= earliest; f--) {
            if (history[(int) (f % history.length)] > threshold) { onset = f; quiet = 0; }
            else if (++quiet >= quietFrames) break;
        }
        return Math.max(earliest, onset);
    }
    public long getDetections() { return detections; }
    public float getNoiseRms() { return (float) noise; }
    public boolean isSettling() { return nextFrame < rate / 2; }
}
