package br.com.ritmics.core.music;

/**
 * Thread-confined, sample-frame schedule. No timers and no cumulative rounding.
 * This is a nominal sample-clock schedule, not a hardware presentation timestamp.
 */
public final class BeatSchedule {
    public static final int ACCENT = 0;
    public static final int PULSE = 1;
    public static final int SUBDIVISION = 2;

    private final int sampleRate;
    private final MetronomeConfig config;
    private long segmentOriginFrame;
    private long segmentEvent;
    private long totalEvents;
    private long nextFrame;
    private int bpm;
    private int requestedBpm;

    public BeatSchedule(int sampleRate, MetronomeConfig config, long firstFrame) {
        if (sampleRate < 8000 || sampleRate > 192000 || firstFrame < 0) {
            throw new IllegalArgumentException("Invalid sample rate or first frame");
        }
        this.sampleRate = sampleRate;
        this.config = config;
        this.segmentOriginFrame = firstFrame;
        this.nextFrame = firstFrame;
        this.bpm = config.getBpm();
        this.requestedBpm = bpm;
    }

    public void requestBpm(int bpm) {
        MetronomeConfig.requireBpm(bpm);
        requestedBpm = bpm;
    }

    public long getNextFrame() { return nextFrame; }
    public long getTotalEvents() { return totalEvents; }
    public int getBpm() { return bpm; }
    public int getRequestedBpm() { return requestedBpm; }

    /** Commits exactly one event, applying a pending tempo at an unrendered bar start. */
    public int consumeNextEvent() {
        long position = totalEvents % config.getEventsPerBar();
        if (position == 0 && requestedBpm != bpm) {
            segmentOriginFrame = nextFrame;
            segmentEvent = 0;
            bpm = requestedBpm;
        }
        int kind = position == 0 && config.isAccentFirstBeat()
                ? ACCENT : position % config.getSubdivisions() == 0 ? PULSE : SUBDIVISION;
        totalEvents++;
        segmentEvent++;
        long numerator = 60L * sampleRate;
        long denominator = (long) bpm * config.getSubdivisions();
        // Split quotient/remainder to avoid multiplying a long session index by sample rate.
        long whole = Math.multiplyExact(segmentEvent / denominator, numerator);
        long fraction = ((segmentEvent % denominator) * numerator + denominator / 2) / denominator;
        nextFrame = Math.addExact(segmentOriginFrame, Math.addExact(whole, fraction));
        return kind;
    }
}

