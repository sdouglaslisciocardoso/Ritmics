package br.com.ritmics.core.audio;

import java.util.function.BooleanSupplier;

/** Handles partial writes without skipping or duplicating frames. */
public final class PcmWriter {
    @FunctionalInterface
    public interface Sink {
        int write(short[] pcm, int offset, int count);
    }

    private PcmWriter() { }

    public static int writeFully(Sink sink, short[] pcm, int count, BooleanSupplier cancelled) {
        if (count < 0 || count > pcm.length) throw new IllegalArgumentException("Invalid count");
        int written = 0;
        while (written < count && !cancelled.getAsBoolean()) {
            int result = sink.write(pcm, written, count - written);
            if (cancelled.getAsBoolean()) break;
            if (result <= 0 || result > count - written) {
                throw new IllegalStateException("PCM write failed: " + result);
            }
            written += result;
        }
        return written;
    }
}
