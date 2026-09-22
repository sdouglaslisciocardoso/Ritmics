package br.com.ritmics.core.time;

/** Independent frame clock -> System.nanoTime domain. No acoustic latency calibration. */
public final class MonotonicClockMapper {
    public enum Quality { NONE, HARDWARE, ESTIMATED }
    public static final long UNKNOWN_UNCERTAINTY = Long.MAX_VALUE;
    private static final long MAX_AGE = 1_000_000_000L;
    private final double nominal;
    private final long[] frames = new long[32];
    private final long[] times = new long[32];
    private int size, next;
    private long anchorFrame, anchorNanos, lastHardwareNanos, observations, rejected;
    private double slope, residual;
    private boolean driftReady;
    private Quality quality = Quality.NONE;

    public static final class Snapshot {
        public final Quality quality;
        public final long anchorFrame, anchorNanos, observations, rejectedObservations;
        /** Unknown until external acoustic calibration; residual is not absolute accuracy. */
        public final long uncertaintyNanos = UNKNOWN_UNCERTAINTY;
        public final double nanosPerFrame, driftPpm, residualNanos;
        private Snapshot(MonotonicClockMapper m) {
            quality = m.quality; anchorFrame = m.anchorFrame; anchorNanos = m.anchorNanos;
            nanosPerFrame = m.slope; observations = m.observations; rejectedObservations = m.rejected;
            driftPpm = m.driftReady && quality == Quality.HARDWARE
                    ? (m.nominal / m.slope - 1) * 1_000_000 : Double.NaN;
            residualNanos = m.residual;
        }
        public long toNanos(long frame) {
            if (quality == Quality.NONE) throw new IllegalStateException("Clock unavailable");
            return anchorNanos + Math.round((frame - anchorFrame) * nanosPerFrame);
        }
    }

    public MonotonicClockMapper(int sampleRate) {
        if (sampleRate < 8000 || sampleRate > 192000) throw new IllegalArgumentException("sampleRate");
        nominal = 1_000_000_000.0 / sampleRate;
        slope = nominal;
    }

    public synchronized boolean observe(long frame, long nanos) {
        if (frame < 0 || nanos <= 0) { rejected++; return false; }
        if (quality == Quality.HARDWARE && size > 0) {
            int prev = (next + frames.length - 1) % frames.length;
            long df = frame - frames[prev], dt = nanos - times[prev];
            if (df == 0 && dt == 0) return false; // Repeated poll, not a new observation.
            if (df <= 0 || dt <= 0 || Math.abs(dt - df * nominal) > Math.max(5_000_000, dt * .02)
                    || (size >= 3 && Math.abs(nanos - toNanos(frame)) > 8_000_000)) {
                rejected++; return false;
            }
        } else {
            size = 0; next = 0; slope = nominal;
        }
        frames[next] = frame; times[next] = nanos;
        next = (next + 1) % frames.length;
        size = Math.min(size + 1, frames.length);
        anchorFrame = frame; anchorNanos = nanos; lastHardwareNanos = nanos;
        quality = Quality.HARDWARE; observations++;
        fit();
        return true;
    }

    private void fit() {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        long earliest = anchorNanos;
        for (int i = 0; i < size; i++) {
            double x = frames[i] - anchorFrame, y = times[i] - anchorNanos;
            sx += x; sy += y; sxx += x * x; sxy += x * y;
            earliest = Math.min(earliest, times[i]);
        }
        double denominator = sxx - sx * sx / size;
        if (size >= 3 && anchorNanos - earliest >= 200_000_000 && denominator > 0) {
            double candidate = (sxy - sx * sy / size) / denominator;
            if (Math.abs(candidate / nominal - 1) <= .02) slope = candidate;
        }
        double intercept = (sy - slope * sx) / size;
        double sum = 0;
        for (int i = 0; i < size; i++) {
            double e = times[i] - anchorNanos - intercept - (frames[i] - anchorFrame) * slope;
            sum += e * e;
        }
        residual = Math.sqrt(sum / size);
        driftReady = size >= 3 && anchorNanos - earliest >= 1_000_000_000;
        anchorNanos += Math.round(intercept);
    }

    public synchronized boolean observe(long frame, long nanos, long now) {
        if (nanos > now + 5_000_000 || now - nanos > MAX_AGE) { rejected++; return false; }
        return observe(frame, nanos);
    }
    public synchronized void expire(long now) {
        if (quality == Quality.HARDWARE && now - lastHardwareNanos > MAX_AGE) {
            quality = Quality.NONE; size = 0; next = 0; driftReady = false;
        }
    }
    /** Delivery fallback; never used to claim hardware rate or calibrated precision. */
    public synchronized void seedEstimate(long frame, long nanos) {
        if (frame < 0 || nanos <= 0 || quality == Quality.HARDWARE) return;
        anchorFrame = frame; anchorNanos = nanos; slope = nominal;
        quality = Quality.ESTIMATED; driftReady = false;
    }
    public synchronized Quality quality() { return quality; }
    public synchronized double nanosPerFrame() { return slope; }
    public synchronized long toNanos(long frame) {
        if (quality == Quality.NONE) throw new IllegalStateException("Clock unavailable");
        return anchorNanos + Math.round((frame - anchorFrame) * slope);
    }
    public synchronized long toFrame(long nanos) {
        if (quality == Quality.NONE) return -1;
        return anchorFrame + Math.round((nanos - anchorNanos) / slope);
    }
    public synchronized Snapshot snapshot() { return new Snapshot(this); }
}
