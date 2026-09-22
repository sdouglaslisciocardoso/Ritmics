package br.com.ritmics.core.audio;

import br.com.ritmics.core.music.MetronomeConfig;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PcmMetronomeRendererTest {
    private PcmMetronomeRenderer renderer() {
        PcmMetronomeRenderer renderer = new PcmMetronomeRenderer(48000,
                new MetronomeConfig(137, 3, 2, true), 1000);
        renderer.setGain(0.8f);
        return renderer;
    }

    @Test public void outputIsIdenticalAcrossArbitraryBlockBoundaries() {
        PcmMetronomeRenderer whole = renderer();
        PcmMetronomeRenderer blocked = renderer();
        short[] expected = new short[48000 * 8];
        short[] actual = new short[expected.length];
        whole.render(expected, 0, expected.length);
        int offset = 0;
        int[] sizes = {1, 7, 239, 240, 511, 997};
        for (int i = 0; offset < actual.length; i++) {
            int count = Math.min(sizes[i % sizes.length], actual.length - offset);
            blocked.render(actual, offset, count);
            offset += count;
        }
        assertArrayEquals(expected, actual);
        assertEquals(whole.getEventCount(), blocked.getEventCount());
    }

    @Test public void silenceStillAdvancesMusicalPhaseAndMuteCanBeReversed() {
        PcmMetronomeRenderer renderer = new PcmMetronomeRenderer(48000,
                new MetronomeConfig(120, 4, 2, true), 0);
        short[] second = new short[48000];
        renderer.render(second, 0, second.length);
        for (short value : second) assertEquals(0, value);
        assertEquals(4, renderer.getEventCount());
        assertEquals(48000, renderer.getFramePosition());
        renderer.setGain(1);
        renderer.render(second, 0, second.length);
        assertTrue(energy(second) > 0);
        assertEquals(8, renderer.getEventCount());
        renderer.setGain(0);
        renderer.render(new short[240], 0, 240); // finish the 5 ms gain ramp
        renderer.render(second, 0, second.length);
        for (short value : second) assertEquals(0, value);
    }

    @Test public void clickBeginsAtTheScheduledFrameAndHasNoLongTail() {
        PcmMetronomeRenderer renderer = new PcmMetronomeRenderer(48000,
                new MetronomeConfig(120, 4, 1, true), 1000);
        renderer.setGain(1);
        short[] pcm = new short[24000];
        renderer.render(pcm, 0, pcm.length);
        for (int i = 0; i <= 1000; i++) assertEquals(0, pcm[i]);
        assertNotEquals(0, pcm[1001]);
        for (int i = 1480; i < pcm.length; i++) assertEquals(0, pcm[i]);
    }

    @Test public void maximumVolumeDoesNotClipAndAccentIsDistinct() {
        ClickBank bank = new ClickBank(48000);
        assertTrue(energy(bank.get(0)) > energy(bank.get(1)));
        assertTrue(energy(bank.get(1)) > energy(bank.get(2)));
        for (int kind = 0; kind < 3; kind++) {
            short[] pcm = bank.get(kind);
            assertEquals(0, pcm[0]);
            assertEquals(0, pcm[pcm.length - 1]);
            for (short value : pcm) assertTrue(Math.abs((int) value) < Short.MAX_VALUE);
        }
    }

    @Test public void invalidGainAndDestinationBoundsAreRejected() {
        PcmMetronomeRenderer renderer = renderer();
        assertThrows(IllegalArgumentException.class, () -> renderer.setGain(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> renderer.setGain(-1));
        assertThrows(IllegalArgumentException.class, () -> renderer.setGain(1.01f));
        assertThrows(IndexOutOfBoundsException.class, () -> renderer.render(new short[4], 2, 3));
    }

    private long energy(short[] values) {
        long total = 0;
        for (short value : values) total += (long) value * value;
        return total;
    }
}
