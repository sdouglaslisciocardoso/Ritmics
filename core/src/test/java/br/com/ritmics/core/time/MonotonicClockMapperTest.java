package br.com.ritmics.core.time;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MonotonicClockMapperTest {
    @Test public void mapsFramesToMonotonicNanos() {
        MonotonicClockMapper mapper = new MonotonicClockMapper(48_000);
        assertTrue(mapper.observe(100, 1_000_000_000L));
        assertTrue(mapper.observe(48_100, 2_000_000_000L));
        assertEquals(1_500_000_000L, mapper.toNanos(24_100), 2_000L);
        assertEquals(24_100L, mapper.toFrame(1_500_000_000L));
    }

    @Test public void rejectsBackwardsObservationAndReportsIt() {
        MonotonicClockMapper mapper = new MonotonicClockMapper(48_000);
        assertTrue(mapper.observe(100, 1_000_000_000L));
        assertFalse(mapper.observe(90, 999_000_000L));
        assertEquals(1L, mapper.snapshot().rejectedObservations);
    }

    @Test public void estimateIsMarkedWithHigherUncertainty() {
        MonotonicClockMapper mapper = new MonotonicClockMapper(44_100);
        mapper.seedEstimate(0, 2_000_000_000L);
        MonotonicClockMapper.Snapshot snapshot = mapper.snapshot();
        assertEquals(MonotonicClockMapper.Quality.ESTIMATED, snapshot.quality);
        assertTrue(snapshot.uncertaintyNanos > 0);
        assertEquals(2_000_000_000L, mapper.toNanos(0));
    }

    @Test public void replacesFallbackWithoutFittingItsUnknownLatency() {
        MonotonicClockMapper m = new MonotonicClockMapper(48000);
        m.seedEstimate(0, 2_000_000_000L);
        assertTrue(m.observe(480, 1_500_000_000L));
        assertEquals(1_500_000_000L, m.toNanos(480));
        assertTrue(Double.isNaN(m.snapshot().driftPpm));
        assertEquals(Long.MAX_VALUE, m.snapshot().uncertaintyNanos);
    }
    @Test public void clockExpiresAndCanReacquireAfterCounterReset() {
        MonotonicClockMapper m = new MonotonicClockMapper(48000);
        m.observe(48000, 2_000_000_000L);
        m.expire(3_000_000_001L);
        assertEquals(MonotonicClockMapper.Quality.NONE, m.quality());
        assertTrue(m.observe(0, 4_000_000_000L));
    }
    @Test public void observesRateDriftOverLongRunAndRejectsJump() {
        MonotonicClockMapper m = new MonotonicClockMapper(48000);
        for (int i = 0; i < 36000; i++) {
            assertTrue(m.observe(4800L * i, 1_000_000_000L + Math.round(i * 100_000_000.0 / 1.0001)));
        }
        assertEquals(100.0, m.snapshot().driftPpm, .1);
        assertFalse(m.observe(4800L * 36000, 8_000_000_000_000L));
        assertEquals(1, m.snapshot().rejectedObservations);
    }
    @Test public void duplicatesAreNotNewSamplesAndFutureDomainIsRejected() {
        MonotonicClockMapper m = new MonotonicClockMapper(48000);
        m.observe(0, 1_000_000_000);
        assertFalse(m.observe(0, 1_000_000_000));
        assertFalse(m.observe(4800, 9_000_000_000L, 1_100_000_000L));
        assertEquals(1, m.snapshot().observations);
    }
}
