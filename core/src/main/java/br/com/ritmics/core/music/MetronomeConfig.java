package br.com.ritmics.core.music;

/**
 * Immutable musical configuration. BPM counts the meter's written pulse: quarter notes in
 * 2/4, 3/4 and 4/4, eighth notes in 6/8.
 */
public final class MetronomeConfig {
    public static final int MIN_BPM = 30;
    public static final int MAX_BPM = 240;
    public static final int MAX_SUBDIVISIONS = 4;

    private final int bpm;
    private final int beatsPerBar;
    private final int subdivisions;
    private final boolean[] accents;

    public MetronomeConfig(int bpm, int beatsPerBar, int subdivisions, boolean accentFirstBeat) {
        this(bpm, beatsPerBar, subdivisions, firstBeatOnly(beatsPerBar, accentFirstBeat));
    }

    /** One accent flag per pulse of the bar; the array is copied. */
    public MetronomeConfig(int bpm, int beatsPerBar, int subdivisions, boolean[] accents) {
        requireBpm(bpm);
        if (!isSupportedMeter(beatsPerBar)) {
            throw new IllegalArgumentException("Only 2/4, 3/4, 4/4 and 6/8 are supported");
        }
        if (subdivisions < 1 || subdivisions > MAX_SUBDIVISIONS) {
            throw new IllegalArgumentException("Subdivisions must be between 1 and 4");
        }
        if (accents == null || accents.length != beatsPerBar) {
            throw new IllegalArgumentException("One accent flag is required per beat");
        }
        this.bpm = bpm;
        this.beatsPerBar = beatsPerBar;
        this.subdivisions = subdivisions;
        this.accents = accents.clone();
    }

    public static void requireBpm(int bpm) {
        if (bpm < MIN_BPM || bpm > MAX_BPM) {
            throw new IllegalArgumentException("BPM must be between 30 and 240");
        }
    }

    public static boolean isSupportedMeter(int beatsPerBar) {
        return beatsPerBar == 2 || beatsPerBar == 3 || beatsPerBar == 4 || beatsPerBar == 6;
    }

    /** Written pulse of the meter: 4 for a quarter note, 8 for an eighth note. */
    public static int beatUnit(int beatsPerBar) {
        return beatsPerBar == 6 ? 8 : 4;
    }

    /** Downbeat accent; 6/8 also accents the fourth eighth to expose its two groups of three. */
    public static boolean[] defaultAccents(int beatsPerBar) {
        boolean[] accents = new boolean[beatsPerBar];
        accents[0] = true;
        if (beatsPerBar == 6) accents[3] = true;
        return accents;
    }

    private static boolean[] firstBeatOnly(int beatsPerBar, boolean accent) {
        // Invalid meters are reported by the main constructor, not by the array allocation.
        boolean[] accents = new boolean[Math.max(0, beatsPerBar)];
        if (accents.length > 0) accents[0] = accent;
        return accents;
    }

    public int getBpm() { return bpm; }
    public int getBeatsPerBar() { return beatsPerBar; }
    public int getBeatUnit() { return beatUnit(beatsPerBar); }
    public int getSubdivisions() { return subdivisions; }
    public boolean isAccented(int beat) { return accents[beat]; }
    public boolean[] getAccents() { return accents.clone(); }
    public int getEventsPerBar() { return beatsPerBar * subdivisions; }
}
