package br.com.ritmics.core.audio;

/** Synthesized once, before playback. Short attacks with a smooth, finite tail. */
public final class ClickBank {
    private final short[][] sounds = new short[3][];

    public ClickBank(int sampleRate) {
        sounds[0] = synthesize(sampleRate, 1800.0, 0.78);
        sounds[1] = synthesize(sampleRate, 1250.0, 0.62);
        sounds[2] = synthesize(sampleRate, 900.0, 0.38);
    }

    short[] get(int kind) { return sounds[kind]; }

    private static short[] synthesize(int sampleRate, double frequency, double gain) {
        int length = Math.max(2, sampleRate / 100); // 10 ms, well below the 125 ms minimum spacing.
        short[] pcm = new short[length];
        for (int i = 0; i < length; i++) {
            double time = (double) i / sampleRate;
            double attack = Math.min(1.0, time / 0.0004);
            double tail = 0.5 * (1.0 + Math.cos(Math.PI * i / (length - 1)));
            double envelope = attack * Math.exp(-time / 0.0028) * tail;
            pcm[i] = (short) Math.round(Short.MAX_VALUE * gain * envelope
                    * Math.sin(2.0 * Math.PI * frequency * time));
        }
        return pcm;
    }
}

