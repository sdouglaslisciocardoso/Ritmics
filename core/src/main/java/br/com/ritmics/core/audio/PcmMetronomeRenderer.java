package br.com.ritmics.core.audio;

import br.com.ritmics.core.music.BeatSchedule;
import br.com.ritmics.core.music.MetronomeConfig;

/** Allocation-free render path. One PCM frame is one mono 16-bit sample. */
public final class PcmMetronomeRenderer {
    private final BeatSchedule schedule;
    private final ClickBank clicks;
    private final int gainRampFrames;
    private long framePosition;
    private short[] activeClick;
    private int clickPosition;
    private float currentGain;
    private float targetGain;
    private float gainStep;
    private int rampRemaining;

    public PcmMetronomeRenderer(int sampleRate, MetronomeConfig config, long firstFrame) {
        schedule = new BeatSchedule(sampleRate, config, firstFrame);
        clicks = new ClickBank(sampleRate);
        gainRampFrames = Math.max(1, sampleRate / 200);
    }

    public void requestBpm(int bpm) { schedule.requestBpm(bpm); }
    public int getBpm() { return schedule.getBpm(); }
    public int getRequestedBpm() { return schedule.getRequestedBpm(); }
    public long getFramePosition() { return framePosition; }
    public long getEventCount() { return schedule.getTotalEvents(); }

    public void setGain(float gain) {
        if (!Float.isFinite(gain) || gain < 0f || gain > 1f) {
            throw new IllegalArgumentException("Gain must be finite and between 0 and 1");
        }
        if (gain != targetGain) {
            targetGain = gain;
            rampRemaining = gainRampFrames;
            gainStep = (targetGain - currentGain) / gainRampFrames;
        }
    }

    public void render(short[] destination, int offset, int count) {
        if (offset < 0 || count < 0 || offset > destination.length - count) {
            throw new IndexOutOfBoundsException("PCM destination bounds");
        }
        for (int i = 0; i < count; i++) {
            if (framePosition == schedule.getNextFrame()) {
                activeClick = clicks.get(schedule.consumeNextEvent());
                clickPosition = 0;
            }
            if (rampRemaining > 0) {
                currentGain += gainStep;
                if (--rampRemaining == 0) currentGain = targetGain;
            }
            short sample = 0;
            if (activeClick != null && clickPosition < activeClick.length) {
                sample = activeClick[clickPosition++];
            }
            destination[offset + i] = (short) Math.round(sample * currentGain);
            framePosition++;
        }
    }
}

