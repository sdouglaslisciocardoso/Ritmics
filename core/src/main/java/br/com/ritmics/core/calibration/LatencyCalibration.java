package br.com.ritmics.core.calibration;

import java.util.Arrays;

/** Robust estimator for a controlled speaker-to-microphone acoustic loop test. */
public final class LatencyCalibration {
    public static final int MIN_SAMPLES = 8;
    public static final int TARGET_SAMPLES = 12;
    private static final double MIN_DELAY_MS = 3;
    private static final double MAX_DELAY_MS = 400;
    private final double[] delaysMs = new double[32];
    private int size, rejected;
    private long lastExpectedNanos = Long.MIN_VALUE;

    public static final class Result {
        public final double delayMs;
        public final double dispersionMs;
        public final int acceptedSamples;
        public final int rejectedSamples;

        private Result(double delayMs, double dispersionMs, int acceptedSamples, int rejectedSamples) {
            this.delayMs = delayMs;
            this.dispersionMs = dispersionMs;
            this.acceptedSamples = acceptedSamples;
            this.rejectedSamples = rejectedSamples;
        }

        public CalibrationProfile.Confidence confidence(boolean hardwareInput,
                                                        boolean hardwareOutput,
                                                        boolean bluetoothRoute) {
            if (bluetoothRoute || acceptedSamples < MIN_SAMPLES)
                return CalibrationProfile.Confidence.LOW;
            if (hardwareInput && hardwareOutput && acceptedSamples >= TARGET_SAMPLES
                    && dispersionMs <= 4) return CalibrationProfile.Confidence.HIGH;
            if (acceptedSamples >= 10 && dispersionMs <= 12)
                return CalibrationProfile.Confidence.MEDIUM;
            return CalibrationProfile.Confidence.LOW;
        }
    }

    public boolean add(long expectedPresentationNanos, long capturedOnsetNanos) {
        if (expectedPresentationNanos <= lastExpectedNanos || capturedOnsetNanos <= 0) {
            rejected++;
            return false;
        }
        lastExpectedNanos = expectedPresentationNanos;
        double delay = (capturedOnsetNanos - expectedPresentationNanos) / 1_000_000.0;
        if (!Double.isFinite(delay) || delay < MIN_DELAY_MS || delay > MAX_DELAY_MS
                || size == delaysMs.length) {
            rejected++;
            return false;
        }
        delaysMs[size++] = delay;
        return true;
    }

    public int sampleCount() { return size; }
    public int rejectedCount() { return rejected; }
    public boolean canFinish() { return size >= MIN_SAMPLES; }
    public boolean hasTargetSamples() { return size >= TARGET_SAMPLES; }

    public Result result() {
        if (!canFinish()) throw new IllegalStateException("Not enough samples");
        double[] values = Arrays.copyOf(delaysMs, size);
        double firstMedian = median(values);
        double[] deviations = deviations(values, firstMedian);
        double firstMad = median(deviations);
        double limit = Math.max(8, firstMad * 3.5);
        double[] kept = new double[size];
        int keptCount = 0;
        for (double value : values) {
            if (Math.abs(value - firstMedian) <= limit) kept[keptCount++] = value;
        }
        if (keptCount < MIN_SAMPLES) throw new IllegalStateException("Unstable samples");
        kept = Arrays.copyOf(kept, keptCount);
        double finalMedian = median(kept);
        double finalMad = median(deviations(kept, finalMedian));
        return new Result(finalMedian, finalMad, keptCount, rejected + size - keptCount);
    }

    /** Conservative recommendation: noisy environments lower sensitivity. */
    public static float recommendedSensitivity(float noiseRms) {
        if (!Float.isFinite(noiseRms) || noiseRms < 0) throw new IllegalArgumentException("noiseRms");
        if (noiseRms < .0025f) return .70f;
        if (noiseRms < .008f) return .55f;
        if (noiseRms < .025f) return .40f;
        return .25f;
    }

    private static double[] deviations(double[] values, double center) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) result[i] = Math.abs(values[i] - center);
        return result;
    }

    private static double median(double[] values) {
        Arrays.sort(values);
        int middle = values.length / 2;
        return values.length % 2 == 0 ? (values[middle - 1] + values[middle]) / 2 : values[middle];
    }
}
