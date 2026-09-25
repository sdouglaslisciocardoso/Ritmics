package br.com.ritmics.core.calibration;

/**
 * Decides, on each UI poll, whether a running calibration may keep measuring. A calibration
 * owns both audio paths from start to finish, so any path that stops before the calibration
 * ends it at once instead of leaving it waiting for audio that will never come back.
 */
public final class CalibrationSupervisor {
    /** Engine states mapped by the caller; STOPPED covers both stopping and idle. */
    public enum PathState { STARTING, RUNNING, STOPPED, FAILED }
    public enum Verdict { WAIT, MEASURE, INTERRUPTED, FAILED, TIMED_OUT }

    private CalibrationSupervisor() { }

    public static Verdict check(PathState output, PathState input, boolean inputSettling,
                                boolean clocksReady, long waitingNanos, long waitTimeoutNanos) {
        if (output == PathState.FAILED || input == PathState.FAILED) return Verdict.FAILED;
        if (output == PathState.STOPPED || input == PathState.STOPPED) return Verdict.INTERRUPTED;
        if (output == PathState.RUNNING && input == PathState.RUNNING && !inputSettling && clocksReady) {
            return Verdict.MEASURE;
        }
        return waitingNanos > waitTimeoutNanos ? Verdict.TIMED_OUT : Verdict.WAIT;
    }
}
