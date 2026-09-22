package br.com.ritmics.core.audio;

import org.junit.Test;

import br.com.ritmics.core.audio.AudioLevelMeter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AudioLevelMeterTest {
    @Test public void silenceHasZeroLevel() {
        AudioLevelMeter.MutableLevel level = new AudioLevelMeter.MutableLevel();
        AudioLevelMeter.measure(new short[] {0, 0, 0}, 0, 3, level);
        assertEquals(0f, level.peak, 0.0001f);
        assertEquals(0f, level.rms, 0.0001f);
        assertEquals(-96f, level.dbfs, 0.0001f);
        assertFalse(level.clipping);
    }

    @Test public void fullScaleBlockReportsClipping() {
        AudioLevelMeter.MutableLevel level = new AudioLevelMeter.MutableLevel();
        AudioLevelMeter.measure(new short[] {32767, -32768}, 0, 2, level);
        assertEquals(1f, level.peak, 0.0001f);
        assertEquals(1f, level.rms, 0.0001f);
        assertTrue(level.clipping);
    }

    @Test public void rangeCanBeMeasuredWithoutAllocatingResult() {
        AudioLevelMeter.MutableLevel level = new AudioLevelMeter.MutableLevel();
        AudioLevelMeter.measure(new short[] {0, 1000, 0}, 1, 1, level);
        assertEquals(1000f / 32768f, level.peak, 0.0001f);
        assertFalse(level.clipping);
    }
}
