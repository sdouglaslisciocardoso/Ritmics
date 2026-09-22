package br.com.ritmics.audio.output;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRouting;
import android.media.AudioTrack;
import android.media.AudioTimestamp;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import br.com.ritmics.core.audio.PcmMetronomeRenderer;
import br.com.ritmics.core.audio.PcmWriter;
import br.com.ritmics.core.music.MetronomeConfig;
import br.com.ritmics.core.time.MonotonicClockMapper;

/**
 * Stage 2 output adapter. The audio worker owns rendering, writes and release.
 * The main thread can request cancellation and pause the native sink to unblock a write.
 * Hardware frame timestamps map to the monotonic domain; acoustic latency remains uncalibrated.
 */
public final class MetronomeEngine {
    public enum State { IDLE, STARTING, PLAYING, STOPPING, ERROR }
    public enum StopReason { USER, BACKGROUND, FOCUS_LOSS, ROUTE_CHANGED, UNDERRUN, FAILURE }

    public static final class Snapshot {
        public final State state;
        public final StopReason reason;
        public final int bpm;
        public final int requestedBpm;
        public final int sampleRate;
        public final int bufferFrames;
        public final int underruns;
        public final boolean lowLatency;
        public final long framesWritten;
        public final long eventsRendered;
        public final String error;
        public final MonotonicClockMapper.Snapshot clock;
        public final long firstClickFrame;
        public final int routeId;

        private Snapshot(Run run) {
            state = run.state;
            reason = run.reason;
            bpm = run.renderedBpm;
            requestedBpm = run.requestedBpm;
            sampleRate = run.sampleRate;
            bufferFrames = run.bufferFrames;
            underruns = run.underruns;
            lowLatency = run.lowLatency;
            framesWritten = run.framesWritten;
            eventsRendered = run.eventsRendered;
            error = run.error;
            clock = run.clock == null ? null : run.clock.snapshot();
            firstClickFrame = run.firstClickFrame;
            routeId = run.routeId;
        }

        public boolean isBusy() {
            return state == State.STARTING || state == State.PLAYING || state == State.STOPPING;
        }
    }

    private static final class Run {
        final MetronomeConfig config;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final Object trackLock = new Object();
        volatile State state = State.STARTING;
        volatile StopReason reason;
        volatile int requestedBpm;
        volatile int renderedBpm;
        volatile float volume;
        volatile boolean muted;
        volatile int sampleRate;
        volatile int bufferFrames;
        volatile int underruns;
        volatile boolean lowLatency;
        volatile long framesWritten;
        volatile long eventsRendered;
        volatile String error;
        volatile int routeId = -1;
        volatile MonotonicClockMapper clock;
        volatile long firstClickFrame;
        AudioTrack track; // Protected by trackLock for publication and cancellation.
        AudioFocusRequest focusRequest;

        Run(MetronomeConfig config, float volume, boolean muted) {
            this.config = config;
            this.requestedBpm = config.getBpm();
            this.renderedBpm = config.getBpm();
            this.volume = volume;
            this.muted = muted;
        }
    }

    private final AudioManager audioManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile Run current;
    private volatile Snapshot last = initialSnapshot();

    public MetronomeEngine(Context context) {
        audioManager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
    }

    private static Snapshot initialSnapshot() {
        Run empty = new Run(new MetronomeConfig(80, 4, 1, true), 0.55f, false);
        empty.state = State.IDLE;
        return new Snapshot(empty);
    }

    /** Called by the visible Activity. Does not start another worker until cleanup finishes. */
    public synchronized boolean start(MetronomeConfig config, float volume, boolean muted) {
        requireVolume(volume);
        if (current != null) return false;
        Run run = new Run(config, volume, muted);
        current = run;
        try {
            run.focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes())
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(change -> {
                        if (change != AudioManager.AUDIOFOCUS_GAIN) {
                            stopRun(run, StopReason.FOCUS_LOSS);
                        }
                    }, mainHandler)
                    .build();
            if (audioManager.requestAudioFocus(run.focusRequest)
                    != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                run.error = "O sistema não concedeu foco de áudio. Feche outro áudio e tente novamente.";
                run.reason = StopReason.FOCUS_LOSS;
                run.state = State.ERROR;
                finish(run);
                return false;
            }
            new Thread(() -> stream(run), "Ritmics-AudioOutput").start();
            return true;
        } catch (RuntimeException failure) {
            run.error = "Não foi possível iniciar o áudio: " + failure.getClass().getSimpleName();
            run.reason = StopReason.FAILURE;
            run.state = State.ERROR;
            finish(run);
            return false;
        }
    }

    public void stop(StopReason reason) {
        Run run = current;
        if (run != null) stopRun(run, reason);
    }

    private void stopRun(Run run, StopReason reason) {
        if (current != run || !run.cancelled.compareAndSet(false, true)) return;
        run.reason = reason;
        run.state = State.STOPPING;
        synchronized (run.trackLock) {
            if (run.track != null) {
                try {
                    run.track.pause();
                } catch (IllegalStateException ignored) {
                    // Worker cleanup is still required if the native track died.
                }
            }
        }
    }

    public void requestBpm(int bpm) {
        MetronomeConfig.requireBpm(bpm);
        Run run = current;
        if (run != null) run.requestedBpm = bpm;
    }

    public void setVolume(float volume) {
        requireVolume(volume);
        Run run = current;
        if (run != null) run.volume = volume;
    }

    public void setMuted(boolean muted) {
        Run run = current;
        if (run != null) run.muted = muted;
    }

    public Snapshot snapshot() {
        Run run = current;
        return run == null ? last : new Snapshot(run);
    }

    private void stream(Run run) {
        AudioTrack track = null;
        AudioRouting.OnRoutingChangedListener routeListener = null;
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            if (run.cancelled.get()) return;
            track = createTrack();
            synchronized (run.trackLock) {
                run.track = track;
                if (run.cancelled.get()) return;
            }
            run.sampleRate = track.getSampleRate();
            run.clock = new MonotonicClockMapper(run.sampleRate);
            run.bufferFrames = track.getBufferSizeInFrames();
            run.lowLatency = track.getPerformanceMode() == AudioTrack.PERFORMANCE_MODE_LOW_LATENCY;
            final AudioTrack output = track;
            routeListener = router -> {
                if (current != run || run.cancelled.get()) return;
                AudioDeviceInfo device = router.getRoutedDevice();
                if (device == null) {
                    if (run.routeId != -1) stopRun(run, StopReason.ROUTE_CHANGED);
                    return;
                }
                if (run.routeId != -1 && run.routeId != device.getId()) {
                    stopRun(run, StopReason.ROUTE_CHANGED);
                } else {
                    run.routeId = device.getId();
                }
            };
            track.addOnRoutingChangedListener(routeListener, mainHandler);

            int blockFrames = Math.max(64, run.sampleRate / 200); // About 5 ms.
            short[] block = new short[Math.max(blockFrames, run.bufferFrames)];
            long firstClickFrame = Math.max(run.bufferFrames, run.sampleRate / 20);
            run.firstClickFrame = firstClickFrame;
            PcmMetronomeRenderer renderer = new PcmMetronomeRenderer(
                    run.sampleRate, run.config, firstClickFrame);
            PcmWriter.Sink sink = (pcm, offset, count) -> output.write(
                    pcm, offset, count, AudioTrack.WRITE_BLOCKING);
            BooleanSupplier cancelled = run.cancelled::get;

            // Prime the native buffer before play. First click follows a short silent lead-in.
            renderer.setGain(run.muted ? 0f : run.volume);
            renderer.render(block, 0, run.bufferFrames);
            run.framesWritten += PcmWriter.writeFully(sink, block, run.bufferFrames, cancelled);
            synchronized (run.trackLock) {
                if (run.cancelled.get()) return;
                track.play();
                AudioDeviceInfo initialDevice = track.getRoutedDevice();
                if (initialDevice != null) run.routeId = initialDevice.getId();
                run.state = State.PLAYING;
            }
            int initialUnderruns = track.getUnderrunCount();
            AudioTimestamp timestamp = new AudioTimestamp();
            long nextTimestampPoll = 0;
            while (!run.cancelled.get()) {
                renderer.requestBpm(run.requestedBpm);
                renderer.setGain(run.muted ? 0f : run.volume);
                renderer.render(block, 0, blockFrames);
                run.framesWritten += PcmWriter.writeFully(sink, block, blockFrames, cancelled);
                if (run.cancelled.get()) break;
                long now = System.nanoTime();
                if (now >= nextTimestampPoll) {
                    run.clock.expire(now);
                    if (track.getTimestamp(timestamp)) {
                        run.clock.observe(timestamp.framePosition, timestamp.nanoTime, now);
                    }
                    // Playback head is a coarse delivery estimate, never a calibrated presentation timestamp.
                    run.clock.seedEstimate(Integer.toUnsignedLong(track.getPlaybackHeadPosition()), now);
                    nextTimestampPoll = now + 100_000_000;
                }
                run.renderedBpm = renderer.getBpm();
                run.eventsRendered = renderer.getEventCount();
                run.underruns = track.getUnderrunCount() - initialUnderruns;
                if (run.underruns > 0) {
                    run.reason = StopReason.UNDERRUN;
                    run.error = "O áudio ficou sem amostras (underrun). Reprodução interrompida; tente novamente.";
                    run.cancelled.set(true);
                    run.state = State.ERROR;
                }
            }
        } catch (RuntimeException failure) {
            if (!run.cancelled.get()) {
                run.reason = StopReason.FAILURE;
                run.error = "Falha na saída de áudio: " + failure.getClass().getSimpleName()
                        + ". Encerre outros áudios e tente novamente.";
                run.state = State.ERROR;
            }
        } finally {
            try {
                synchronized (run.trackLock) {
                    run.track = null;
                    if (track != null) {
                        try {
                            if (routeListener != null) track.removeOnRoutingChangedListener(routeListener);
                            track.pause();
                            track.flush();
                        } finally {
                            track.release();
                        }
                    }
                }
            } catch (RuntimeException cleanupFailure) {
                run.error = "O sistema informou uma falha ao liberar a saída de áudio.";
                run.reason = StopReason.FAILURE;
                run.state = State.ERROR;
            } finally {
                if (run.state != State.ERROR) run.state = State.IDLE;
                finish(run);
            }
        }
    }

    private synchronized void finish(Run run) {
        try {
            if (run.focusRequest != null) audioManager.abandonAudioFocusRequest(run.focusRequest);
        } catch (RuntimeException failure) {
            run.error = "O sistema informou uma falha ao encerrar o foco de áudio.";
            run.reason = StopReason.FAILURE;
            run.state = State.ERROR;
        } finally {
            if (current == run) {
                last = new Snapshot(run);
                current = null;
            }
        }
    }

    private AudioTrack createTrack() {
        int nativeRate = parsePositive(audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE), 48000);
        int[] rates = {nativeRate, 48000, 44100};
        RuntimeException lastFailure = null;
        for (int rate : rates) {
            if (rate < 8000 || rate > 192000) continue;
            int minimum = AudioTrack.getMinBufferSize(rate,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) continue;
            int burst = parsePositive(audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER), rate / 200);
            int frames = Math.max((minimum + 1) / 2, Math.max(rate / 25, Math.min(burst, rate) * 4));
            for (int mode : new int[]{AudioTrack.PERFORMANCE_MODE_LOW_LATENCY, AudioTrack.PERFORMANCE_MODE_NONE}) {
                AudioTrack candidate = null;
                try {
                    candidate = new AudioTrack.Builder()
                            .setAudioAttributes(attributes())
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(rate)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .setBufferSizeInBytes(frames * 2)
                            .setPerformanceMode(mode)
                            .build();
                    if (candidate.getState() == AudioTrack.STATE_INITIALIZED) return candidate;
                } catch (IllegalArgumentException | UnsupportedOperationException failure) {
                    lastFailure = failure;
                }
                if (candidate != null) candidate.release();
            }
        }
        throw new IllegalStateException("No usable PCM output configuration", lastFailure);
    }

    private static AudioAttributes attributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    }

    private static int parsePositive(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void requireVolume(float volume) {
        if (!Float.isFinite(volume) || volume < 0f || volume > 1f) {
            throw new IllegalArgumentException("Volume must be between 0 and 1");
        }
    }
}
