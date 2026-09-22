package br.com.ritmics.core.audio;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import br.com.ritmics.core.music.MetronomeConfig;

public final class BeatDetectorTest {
    private static void hit(short[] pcm, int start, int rate, double amplitude, double decay) {
        for (int j = 0; j < rate * .3 && start + j < pcm.length; j++) {
            double sound = amplitude * Math.exp(-j / (rate * decay)) * Math.cos(2 * Math.PI * 1300 * j / rate);
            int mixed = pcm[start + j] + (int) (32767 * sound);
            pcm[start + j] = (short) Math.max(-32768, Math.min(32767, mixed));
        }
    }
    private static List<Long> detect(short[] pcm, int rate, int chunk, float sensitivity) {
        List<Long> events = new ArrayList<>();
        BeatDetector detector = new BeatDetector(rate, (frame, strength) -> events.add(frame));
        detector.setSensitivity(sensitivity);
        for (int p = 0; p < pcm.length; p += chunk) detector.process(pcm, p, Math.min(chunk, pcm.length-p), p);
        return events;
    }
    @Test public void silenceAndConstantDcDoNotGenerateEvents() {
        short[] pcm = new short[96000];
        assertTrue(detect(pcm, 48000, 257, .5f).isEmpty());
        Arrays.fill(pcm, (short) 10000);
        assertTrue(detect(pcm, 48000, 240, .5f).isEmpty());
    }
    @Test public void detectsKnownOnsetsAtAllSupportedRates() {
        for (int rate : new int[]{16000, 44100, 48000}) {
            short[] pcm = new short[rate * 3];
            int onset = rate + 7;
            hit(pcm, onset, rate, .6, .015);
            hit(pcm, onset + rate, rate, .4, .015);
            List<Long> events = detect(pcm, rate, 137, .5f);
            assertEquals("rate " + rate, 2, events.size());
            assertTrue(Math.abs(events.get(0) - onset) <= rate * .004);
            assertTrue(Math.abs(events.get(1) - onset - rate) <= rate * .004);
        }
    }
    @Test public void timingDoesNotDependOnReadChunkBoundaries() {
        short[] pcm = new short[96000];
        hit(pcm, 48137, 48000, .6, .015);
        hit(pcm, 72123, 48000, .3, .015);
        assertEquals(detect(pcm, 48000, 1, .5f), detect(pcm, 48000, 4096, .5f));
        assertEquals(detect(pcm, 48000, 257, .5f), detect(pcm, 48000, 240, .5f));
    }
    @Test public void supportsEightHitsPerSecond() {
        short[] pcm = new short[144000];
        for (int i = 0; i < 12; i++) hit(pcm, 48000 + 6000 * i, 48000, .5, .005);
        assertEquals(12, detect(pcm, 48000, 240, .5f).size());
    }
    @Test public void suppressesRingingAndCloseDuplicate() {
        short[] pcm = new short[144000];
        hit(pcm, 48000, 48000, .7, .06);
        hit(pcm, 49000, 48000, .5, .01);
        assertEquals(1, detect(pcm, 48000, 127, .5f).size());
    }
    @Test public void higherSensitivityFindsWeakTransient() {
        short[] pcm = new short[96000];
        hit(pcm, 48000, 48000, .012, .01);
        assertTrue(detect(pcm, 48000, 240, 0).isEmpty());
        assertEquals(1, detect(pcm, 48000, 240, 1).size());
    }
    @Test public void stableNoiseDoesNotTriggerAndHitAboveNoiseDoes() {
        short[] pcm = new short[144000];
        Random random = new Random(13);
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (random.nextInt(401) - 200);
        assertTrue(detect(pcm, 48000, 240, .5f).isEmpty());
        hit(pcm, 48000, 48000, .3, .01);
        assertEquals(1, detect(pcm, 48000, 240, .5f).size());
    }
    @Test public void clippingDoesNotMultiplyOneHit() {
        short[] pcm = new short[96000];
        hit(pcm, 48000, 48000, 4, .015);
        assertEquals(1, detect(pcm, 48000, 240, .5f).size());
    }
    @Test(expected = IllegalArgumentException.class) public void gapsAreRejected() {
        new BeatDetector(48000, (f, s) -> {}).process(new short[240], 0, 240, 1);
    }
    @Test public void ownClickCanTriggerAndSimultaneousHitIsNotBlanked() {
        int rate = 48000;
        short[] clicks = new short[rate * 3];
        PcmMetronomeRenderer renderer = new PcmMetronomeRenderer(rate,
                new MetronomeConfig(120, 4, 1, true), rate);
        renderer.setGain(.6f);
        renderer.render(clicks, 0, clicks.length);
        List<Long> alone = detect(clicks, rate, 240, .5f);
        assertFalse("Speaker leakage cannot be assumed rejected", alone.isEmpty());
        short[] combined = clicks.clone();
        hit(combined, rate, rate, .5, .01);
        List<Long> simultaneous = detect(combined, rate, 240, .5f);
        assertTrue(simultaneous.stream().anyMatch(f -> Math.abs(f-rate) < rate * .004));
    }
}
