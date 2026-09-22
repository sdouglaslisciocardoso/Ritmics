package br.com.ritmics.core.music;

import org.junit.Test;

import static org.junit.Assert.*;

public final class BeatScheduleTest {
    @Test public void noAccumulatedRoundingAcrossAllSupportedTemposForOneHour() {
        for (int rate : new int[]{44100, 48000}) {
            for (int bpm = 30; bpm <= 240; bpm++) {
                for (int subdivision = 1; subdivision <= MetronomeConfig.MAX_SUBDIVISIONS; subdivision++) {
                    BeatSchedule schedule = new BeatSchedule(rate,
                            new MetronomeConfig(bpm, 4, subdivision, true), 1200);
                    int count = bpm * subdivision * 60;
                    long previous = -1;
                    for (int event = 0; event < count; event++) {
                        double ideal = 1200 + event * 60.0 * rate / (bpm * subdivision);
                        long actual = schedule.getNextFrame();
                        assertTrue("Quantization exceeds half a frame", Math.abs(actual - ideal) <= 0.500001);
                        assertTrue("Events must be strictly ordered", actual > previous);
                        previous = actual;
                        schedule.consumeNextEvent();
                    }
                }
            }
        }
    }

    @Test public void fastBinarySubdivisionIs125Milliseconds() {
        BeatSchedule schedule = new BeatSchedule(48000, new MetronomeConfig(240, 4, 2, true), 0);
        for (int i = 0; i < 32; i++) {
            assertEquals(i * 6000L, schedule.getNextFrame());
            schedule.consumeNextEvent();
        }
    }

    @Test public void fastestSubdivisionIs62Point5Milliseconds() {
        BeatSchedule schedule = new BeatSchedule(48000, new MetronomeConfig(240, 4, 4, true), 0);
        for (int i = 0; i < 64; i++) {
            assertEquals(i * 3000L, schedule.getNextFrame());
            schedule.consumeNextEvent();
        }
    }

    @Test public void tripletsSplitThePulseInThreeEqualParts() {
        BeatSchedule schedule = new BeatSchedule(48000, new MetronomeConfig(120, 3, 3, true), 0);
        for (int i = 0; i < 27; i++) {
            assertEquals(i * 8000L, schedule.getNextFrame());
            int expected = i % 9 == 0 ? BeatSchedule.ACCENT
                    : i % 3 == 0 ? BeatSchedule.PULSE : BeatSchedule.SUBDIVISION;
            assertEquals(expected, schedule.consumeNextEvent());
        }
    }

    @Test public void compoundMeterAccentsBothGroupsByDefault() {
        MetronomeConfig config = new MetronomeConfig(180, 6, 1, MetronomeConfig.defaultAccents(6));
        assertEquals(8, config.getBeatUnit());
        BeatSchedule schedule = new BeatSchedule(48000, config, 0);
        for (int i = 0; i < 18; i++) {
            assertEquals(i % 3 == 0 ? BeatSchedule.ACCENT : BeatSchedule.PULSE, schedule.consumeNextEvent());
        }
    }

    @Test public void everyPulseCanBeAccentedIndependently() {
        boolean[] accents = {false, true, false, true};
        MetronomeConfig config = new MetronomeConfig(100, 4, 2, accents);
        accents[0] = true; // the configuration keeps its own copy
        BeatSchedule schedule = new BeatSchedule(44100, config, 0);
        for (int i = 0; i < 16; i++) {
            int expected = i % 2 != 0 ? BeatSchedule.SUBDIVISION
                    : (i / 2) % 2 == 1 ? BeatSchedule.ACCENT : BeatSchedule.PULSE;
            assertEquals(expected, schedule.consumeNextEvent());
        }
        assertFalse(config.getAccents()[0]);
    }

    @Test public void simpleMetersAccentOnlyTheDownbeatByDefault() {
        for (int beats = 2; beats <= 4; beats++) {
            boolean[] accents = MetronomeConfig.defaultAccents(beats);
            assertEquals(beats, accents.length);
            assertTrue(accents[0]);
            for (int i = 1; i < beats; i++) assertFalse(accents[i]);
            assertEquals(4, MetronomeConfig.beatUnit(beats));
        }
    }

    @Test public void meterAndSubdivisionDetermineAccentPositions() {
        for (int beats : new int[]{2, 3, 4, 6}) {
            BeatSchedule schedule = new BeatSchedule(48000, new MetronomeConfig(120, beats, 2, true), 0);
            for (int i = 0; i < beats * 2 * 3; i++) {
                int expected = i % (beats * 2) == 0 ? BeatSchedule.ACCENT
                        : i % 2 == 0 ? BeatSchedule.PULSE : BeatSchedule.SUBDIVISION;
                assertEquals(expected, schedule.consumeNextEvent());
            }
        }
    }

    @Test public void disablingAccentPreservesPulsesAndSubdivisions() {
        BeatSchedule schedule = new BeatSchedule(44100, new MetronomeConfig(90, 3, 2, false), 0);
        for (int i = 0; i < 20; i++) {
            assertEquals(i % 2 == 0 ? BeatSchedule.PULSE : BeatSchedule.SUBDIVISION,
                    schedule.consumeNextEvent());
        }
    }

    @Test public void tempoChangePreservesBoundaryThenChangesFollowingIntervals() {
        BeatSchedule schedule = new BeatSchedule(48000, new MetronomeConfig(120, 4, 1, true), 100);
        schedule.consumeNextEvent();
        schedule.requestBpm(60);
        for (int i = 1; i < 4; i++) {
            assertEquals(100L + i * 24000, schedule.getNextFrame());
            schedule.consumeNextEvent();
            assertEquals(120, schedule.getBpm());
        }
        assertEquals(96100, schedule.getNextFrame());
        assertEquals(BeatSchedule.ACCENT, schedule.consumeNextEvent());
        assertEquals(60, schedule.getBpm());
        assertEquals(144100, schedule.getNextFrame());
    }

    @Test public void lastTempoRequestWinsAndCanBeCancelled() {
        BeatSchedule schedule = new BeatSchedule(48000, new MetronomeConfig(120, 2, 1, true), 0);
        schedule.consumeNextEvent();
        schedule.requestBpm(80);
        schedule.requestBpm(160);
        schedule.requestBpm(120);
        schedule.consumeNextEvent();
        schedule.consumeNextEvent();
        assertEquals(120, schedule.getBpm());
        schedule.requestBpm(90);
        schedule.requestBpm(180);
        schedule.consumeNextEvent();
        schedule.consumeNextEvent();
        assertEquals(180, schedule.getBpm());
    }

    @Test public void committedBarCannotBeChangedRetroactively() {
        BeatSchedule schedule = new BeatSchedule(48000, new MetronomeConfig(120, 2, 1, true), 0);
        schedule.consumeNextEvent();
        schedule.consumeNextEvent();
        schedule.consumeNextEvent(); // second bar already committed
        schedule.requestBpm(60);
        assertEquals(72000, schedule.getNextFrame());
        schedule.consumeNextEvent();
        assertEquals(96000, schedule.getNextFrame());
        schedule.consumeNextEvent();
        assertEquals(144000, schedule.getNextFrame());
    }

    @Test public void invalidConfigurationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MetronomeConfig(29, 4, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new MetronomeConfig(241, 4, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new MetronomeConfig(120, 1, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new MetronomeConfig(120, 5, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new MetronomeConfig(120, 7, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new MetronomeConfig(120, 4, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new MetronomeConfig(120, 4, 5, true));
        assertThrows(IllegalArgumentException.class,
                () -> new MetronomeConfig(120, 4, 1, new boolean[3]));
        assertThrows(IllegalArgumentException.class,
                () -> new MetronomeConfig(120, 4, 1, (boolean[]) null));
        assertThrows(IllegalArgumentException.class,
                () -> new BeatSchedule(0, new MetronomeConfig(120, 4, 1, true), 0));
    }
}
