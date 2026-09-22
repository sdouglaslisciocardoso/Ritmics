package br.com.ritmics.core.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PcmWriterTest {
    @Test public void partialWritesPreserveEverySampleInOrder() {
        short[] pcm = {10, 20, 30, 40, 50, 60, 70};
        List<Short> actual = new ArrayList<>();
        int count = PcmWriter.writeFully((data, offset, remaining) -> {
            int accepted = Math.min(2, remaining);
            for (int i = 0; i < accepted; i++) actual.add(data[offset + i]);
            return accepted;
        }, pcm, pcm.length, () -> false);
        assertEquals(pcm.length, count);
        for (int i = 0; i < pcm.length; i++) assertEquals(pcm[i], (short) actual.get(i));
    }

    @Test public void zeroAndNegativeWritesFailInsteadOfSpinningOrSkipping() {
        for (int code : new int[]{0, -1, -6, 5}) {
            assertThrows(IllegalStateException.class,
                    () -> PcmWriter.writeFully((data, offset, count) -> code, new short[4], 4, () -> false));
        }
    }

    @Test public void cancellationBeforeWriteDoesNotTouchTheSink() {
        assertEquals(0, PcmWriter.writeFully((data, offset, count) -> {
            fail("Cancelled stream must not write");
            return count;
        }, new short[10], 10, () -> true));
    }

    @Test public void cancellationUnblocksWithoutReportingSpuriousFailure() {
        AtomicBoolean cancelled = new AtomicBoolean();
        assertEquals(0, PcmWriter.writeFully((data, offset, count) -> {
            cancelled.set(true);
            return -3;
        }, new short[10], 10, cancelled::get));
    }

    @Test public void acceptedFramesAreCountedEvenWhenCancellationArrivesDuringWrite() {
        AtomicBoolean cancelled = new AtomicBoolean();
        assertEquals(3, PcmWriter.writeFully((data, offset, count) -> {
            cancelled.set(true);
            return 3;
        }, new short[10], 10, cancelled::get));
    }
}
