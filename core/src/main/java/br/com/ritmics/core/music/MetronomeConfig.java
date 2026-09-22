package br.com.ritmics.core.music;

/** Immutable musical configuration. BPM always means quarter notes per minute. */
public final class MetronomeConfig {
    public static final int MIN_BPM = 30;
    public static final int MAX_BPM = 240;

    private final int bpm;
    private final int beatsPerBar;
    private final int subdivisions;
    private final boolean accentFirstBeat;

    public MetronomeConfig(int bpm, int beatsPerBar, int subdivisions, boolean accentFirstBeat) {
        requireBpm(bpm);
        if (beatsPerBar < 2 || beatsPerBar > 4) {
            throw new IllegalArgumentException("Only 2/4, 3/4 and 4/4 are supported");
        }
        if (subdivisions != 1 && subdivisions != 2) {
            throw new IllegalArgumentException("Subdivisions must be 1 or 2");
        }
        this.bpm = bpm;
        this.beatsPerBar = beatsPerBar;
        this.subdivisions = subdivisions;
        this.accentFirstBeat = accentFirstBeat;
    }

    public static void requireBpm(int bpm) {
        if (bpm < MIN_BPM || bpm > MAX_BPM) {
            throw new IllegalArgumentException("BPM must be between 30 and 240");
        }
    }

    public int getBpm() { return bpm; }
    public int getBeatsPerBar() { return beatsPerBar; }
    public int getSubdivisions() { return subdivisions; }
    public boolean isAccentFirstBeat() { return accentFirstBeat; }
    public int getEventsPerBar() { return beatsPerBar * subdivisions; }
}

