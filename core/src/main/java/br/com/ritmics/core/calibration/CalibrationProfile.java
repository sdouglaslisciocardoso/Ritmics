package br.com.ritmics.core.calibration;

/** Immutable calibration result. Times share the System.nanoTime monotonic domain. */
public final class CalibrationProfile {
    public enum Confidence { UNAVAILABLE, LOW, MEDIUM, HIGH }

    public final String routeKey;
    public final String routeLabel;
    public final long createdAtMillis;
    public final double acousticDelayMs;
    public final double manualAdjustmentMs;
    public final double noiseDbfs;
    public final float sensitivity;
    public final int acceptedSamples;
    public final double dispersionMs;
    public final Confidence confidence;
    public final boolean hardwareInputTimestamp;
    public final boolean hardwareOutputTimestamp;

    public CalibrationProfile(String routeKey, String routeLabel, long createdAtMillis,
                              double acousticDelayMs, double manualAdjustmentMs,
                              double noiseDbfs, float sensitivity, int acceptedSamples,
                              double dispersionMs, Confidence confidence,
                              boolean hardwareInputTimestamp, boolean hardwareOutputTimestamp) {
        if (routeKey == null || routeKey.isEmpty() || routeLabel == null || routeLabel.isEmpty())
            throw new IllegalArgumentException("route");
        if (createdAtMillis < 0 || !finiteInRange(acousticDelayMs, 0, 500)
                || !finiteInRange(manualAdjustmentMs, -150, 150)
                || !Double.isFinite(noiseDbfs) || !finiteInRange(sensitivity, 0, 1)
                || acceptedSamples < 0 || !finiteInRange(dispersionMs, 0, 500)
                || confidence == null) throw new IllegalArgumentException("profile values");
        this.routeKey = routeKey;
        this.routeLabel = routeLabel;
        this.createdAtMillis = createdAtMillis;
        this.acousticDelayMs = acousticDelayMs;
        this.manualAdjustmentMs = manualAdjustmentMs;
        this.noiseDbfs = noiseDbfs;
        this.sensitivity = sensitivity;
        this.acceptedSamples = acceptedSamples;
        this.dispersionMs = dispersionMs;
        this.confidence = confidence;
        this.hardwareInputTimestamp = hardwareInputTimestamp;
        this.hardwareOutputTimestamp = hardwareOutputTimestamp;
    }

    /**
     * Convention used by the future evaluator:
     * corrected = captured - measured acoustic delay + manual adjustment.
     * A positive manual value moves the judged beat later; a negative value moves it earlier.
     */
    public long correctedBeatNanos(long capturedNanos) {
        double correctionMs = manualAdjustmentMs - acousticDelayMs;
        return capturedNanos + Math.round(correctionMs * 1_000_000.0);
    }

    public double effectiveCorrectionMs() { return manualAdjustmentMs - acousticDelayMs; }

    public CalibrationProfile withManualAdjustment(double value) {
        return new CalibrationProfile(routeKey, routeLabel, createdAtMillis, acousticDelayMs,
                value, noiseDbfs, sensitivity, acceptedSamples, dispersionMs, confidence,
                hardwareInputTimestamp, hardwareOutputTimestamp);
    }

    private static boolean finiteInRange(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }
}
