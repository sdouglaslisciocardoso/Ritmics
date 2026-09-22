package br.com.ritmics.core.audio;

/** UI-owned no-playing experiment; phase timers never trigger musical pulses. */
public final class InterferenceProbe {
    public enum Phase { IDLE, QUIET, CLICK, TAIL, COMPLETE, CANCELLED }
    private Phase phase = Phase.IDLE;
    private long since, baselineStart, clickStart, baseline, exposed;
    private boolean invalid;
    public void start(long now, long detections) {
        phase = Phase.QUIET; since = now; baselineStart = detections;
        baseline = 0; exposed = 0; invalid = false;
    }
    public Phase advance(long now, long detections, boolean invalidInput) {
        invalid |= invalidInput;
        if (phase == Phase.QUIET && now - since >= 2_000_000_000L) {
            baseline = detections - baselineStart; clickStart = detections;
            phase = Phase.CLICK; since = now;
        } else if (phase == Phase.CLICK && now - since >= 10_000_000_000L) {
            phase = Phase.TAIL; since = now;
        } else if (phase == Phase.TAIL && now - since >= 1_000_000_000L) {
            exposed = detections - clickStart; phase = Phase.COMPLETE;
        }
        return phase;
    }
    public void cancel() { if (isRunning()) phase = Phase.CANCELLED; }
    public boolean isRunning() { return phase == Phase.QUIET || phase == Phase.CLICK || phase == Phase.TAIL; }
    public Phase phase() { return phase; }
    public long baseline() { return baseline; }
    public long exposed() { return exposed; }
    public boolean isInconclusive() { return invalid || phase != Phase.COMPLETE; }
}
