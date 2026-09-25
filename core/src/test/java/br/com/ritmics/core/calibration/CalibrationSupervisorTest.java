package br.com.ritmics.core.calibration;

import org.junit.Test;

import br.com.ritmics.core.calibration.CalibrationSupervisor.Verdict;

import static br.com.ritmics.core.calibration.CalibrationSupervisor.PathState.FAILED;
import static br.com.ritmics.core.calibration.CalibrationSupervisor.PathState.RUNNING;
import static br.com.ritmics.core.calibration.CalibrationSupervisor.PathState.STARTING;
import static br.com.ritmics.core.calibration.CalibrationSupervisor.PathState.STOPPED;
import static org.junit.Assert.assertEquals;

public final class CalibrationSupervisorTest {
    private static final long TIMEOUT = 18_000_000_000L;

    @Test public void captureStoppedMidMeasurementInterruptsCalibration() {
        // Headphones unplugged: capture ends in IDLE while the output is still playing.
        assertEquals(Verdict.INTERRUPTED,
                CalibrationSupervisor.check(RUNNING, STOPPED, false, true, 1_000_000_000L, TIMEOUT));
    }

    @Test public void outputStoppedByFocusLossInterruptsCalibration() {
        assertEquals(Verdict.INTERRUPTED,
                CalibrationSupervisor.check(STOPPED, RUNNING, false, true, 5_000_000_000L, TIMEOUT));
    }

    @Test public void stoppedPathIsReportedImmediatelyInsteadOfWaitingForTimeout() {
        assertEquals(Verdict.INTERRUPTED,
                CalibrationSupervisor.check(STOPPED, STOPPED, false, false, 0, TIMEOUT));
    }

    @Test public void failedPathTakesPrecedenceOverStoppedPath() {
        assertEquals(Verdict.FAILED, CalibrationSupervisor.check(FAILED, STOPPED, false, true, 0, TIMEOUT));
        assertEquals(Verdict.FAILED, CalibrationSupervisor.check(RUNNING, FAILED, false, true, 0, TIMEOUT));
    }

    @Test public void startingPathsWaitUntilTheTimeoutThenGiveUp() {
        assertEquals(Verdict.WAIT, CalibrationSupervisor.check(STARTING, STARTING, false, false, TIMEOUT, TIMEOUT));
        assertEquals(Verdict.TIMED_OUT,
                CalibrationSupervisor.check(STARTING, RUNNING, false, false, TIMEOUT + 1, TIMEOUT));
    }

    @Test public void settlingCaptureOrMissingClockKeepsWaiting() {
        assertEquals(Verdict.WAIT, CalibrationSupervisor.check(RUNNING, RUNNING, true, true, 0, TIMEOUT));
        assertEquals(Verdict.WAIT, CalibrationSupervisor.check(RUNNING, RUNNING, false, false, 0, TIMEOUT));
    }

    @Test public void runningPathsWithReadyClocksMeasure() {
        assertEquals(Verdict.MEASURE, CalibrationSupervisor.check(RUNNING, RUNNING, false, true, 0, TIMEOUT));
    }
}
