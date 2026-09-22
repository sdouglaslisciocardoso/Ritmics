package br.com.ritmics.core.calibration;

import org.junit.Test;

import static org.junit.Assert.*;

public final class LatencyCalibrationTest {
    @Test public void medianRejectsOutlierAndReportsStableDelay() {
        LatencyCalibration calibration = new LatencyCalibration();
        long expected = 1_000_000_000L;
        double[] delays = {82, 81, 83, 82, 80, 84, 82, 81, 210, 83, 82, 81};
        for (double delay : delays) {
            assertTrue(calibration.add(expected, expected + Math.round(delay * 1_000_000)));
            expected += 500_000_000L;
        }
        LatencyCalibration.Result result = calibration.result();
        assertEquals(82, result.delayMs, .01);
        assertTrue(result.dispersionMs <= 1);
        assertEquals(11, result.acceptedSamples);
        assertEquals(1, result.rejectedSamples);
    }

    @Test public void rejectsDuplicateClickAndImpossibleDelay() {
        LatencyCalibration calibration = new LatencyCalibration();
        assertTrue(calibration.add(1_000_000_000L, 1_080_000_000L));
        assertFalse(calibration.add(1_000_000_000L, 1_090_000_000L));
        assertFalse(calibration.add(2_000_000_000L, 2_500_000_000L));
        assertEquals(1, calibration.sampleCount());
        assertEquals(2, calibration.rejectedCount());
    }

    @Test public void correctionSignIsExplicitAndSingleApplied() {
        CalibrationProfile profile = new CalibrationProfile("route", "Rota", 1,
                80, 10, -48, .5f, 12, 2, CalibrationProfile.Confidence.HIGH, true, true);
        assertEquals(-70, profile.effectiveCorrectionMs(), .001);
        assertEquals(930_000_000L, profile.correctedBeatNanos(1_000_000_000L));
        assertEquals(910_000_000L,
                profile.withManualAdjustment(-10).correctedBeatNanos(1_000_000_000L));
    }

    @Test public void noiseRecommendationBecomesLessSensitiveAsNoiseRises() {
        assertTrue(LatencyCalibration.recommendedSensitivity(.001f)
                > LatencyCalibration.recommendedSensitivity(.01f));
        assertTrue(LatencyCalibration.recommendedSensitivity(.01f)
                > LatencyCalibration.recommendedSensitivity(.05f));
    }
}
