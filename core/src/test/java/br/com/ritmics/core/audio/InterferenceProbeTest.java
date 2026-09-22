package br.com.ritmics.core.audio;
import org.junit.Test;
import static org.junit.Assert.*;

public final class InterferenceProbeTest {
    @Test public void countsBaselineAndClickTailWithoutIncludingPriorEvents() {
        InterferenceProbe p = new InterferenceProbe();
        p.start(1_000_000_000, 10);
        assertEquals(InterferenceProbe.Phase.QUIET, p.advance(2_999_999_999L, 11, false));
        assertEquals(InterferenceProbe.Phase.CLICK, p.advance(3_000_000_000L, 12, false));
        assertEquals(InterferenceProbe.Phase.TAIL, p.advance(13_000_000_000L, 32, false));
        assertEquals(InterferenceProbe.Phase.COMPLETE, p.advance(14_000_000_000L, 33, false));
        assertEquals(2, p.baseline()); assertEquals(21, p.exposed()); assertFalse(p.isInconclusive());
    }
    @Test public void interruptionAndSilencingDoNotYieldCleanResult() {
        InterferenceProbe p = new InterferenceProbe();
        p.start(0, 0); p.advance(2_000_000_000L, 0, true);
        p.advance(12_000_000_000L, 0, false); p.advance(13_000_000_000L, 0, false);
        assertTrue(p.isInconclusive());
        p.start(0, 0); p.cancel(); assertTrue(p.isInconclusive());
        assertEquals(InterferenceProbe.Phase.CANCELLED, p.advance(20_000_000_000L, 0, false));
    }
}
