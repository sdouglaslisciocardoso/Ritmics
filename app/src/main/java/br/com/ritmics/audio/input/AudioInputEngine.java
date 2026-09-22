package br.com.ritmics.audio.input;

import android.annotation.SuppressLint;
import android.media.*;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import br.com.ritmics.core.audio.AudioLevelMeter;
import br.com.ritmics.core.audio.BeatDetector;
import br.com.ritmics.core.time.MonotonicClockMapper;

/** Capture and DSP share a single worker. No PCM persists beyond this run. */
public final class AudioInputEngine {
    public enum State { IDLE, STARTING, CAPTURING, STOPPING, ERROR }
    public enum StopReason { USER, BACKGROUND, ROUTE_CHANGED, ERROR }
    public enum TimestampSource { FRAME_INDEX, HARDWARE_MONOTONIC, SYSTEM_ESTIMATE }
    public interface Listener {
        /** Synchronous worker callback; the block and its array are reused immediately. */
        void onPcmBlock(PcmBlock block);
        void onStateChanged(Snapshot snapshot);
    }
    public static final class PcmBlock {
        public final short[] samples;
        public int sampleCount, sampleRate;
        public long firstFrame, firstTimestampNanos;
        public double nanosPerFrame;
        public TimestampSource timestampSource;
        public final AudioLevelMeter.MutableLevel level = new AudioLevelMeter.MutableLevel();
        private PcmBlock(int count) { samples = new short[count]; }
    }
    public static final class Beat {
        public final long frame, timestampNanos, processedNanos;
        public final TimestampSource timestampSource;
        public final float strength;
        private Beat(long frame, long nanos, TimestampSource source, float strength) {
            this.frame = frame; timestampNanos = nanos; timestampSource = source;
            this.strength = strength; processedNanos = System.nanoTime();
        }
    }
    public static final class Snapshot {
        public final State state;
        public final StopReason reason;
        public final String error, sourceName;
        public final int sampleRate, bufferBytes, blockFrames, source, routeId;
        public final long framesRead, blocksRead, readErrors, detections;
        public final float peak, rms, dbfs, noiseRms;
        public final boolean clipping, clientSilenced, settling;
        public final Beat lastBeat;
        public final TimestampSource timestampSource;
        public final MonotonicClockMapper.Quality clockQuality;
        public final double driftPpm;
        public final long timestampUncertaintyNanos, timestampObservations, rejectedTimestamps;
        private Snapshot(Run r) {
            state = r.state; reason = r.reason; error = r.error;
            sampleRate = r.sampleRate; bufferBytes = r.bufferBytes; blockFrames = r.blockFrames;
            source = r.source; sourceName = sourceName(source); routeId = r.routeId;
            framesRead = r.framesRead; blocksRead = r.blocksRead; readErrors = r.readErrors;
            detections = r.detections; lastBeat = r.lastBeat; noiseRms = r.noiseRms; settling = r.settling;
            peak = r.peak; rms = r.rms; dbfs = r.dbfs; clipping = System.nanoTime() < r.clipUntil;
            clientSilenced = r.clientSilenced; timestampSource = r.timestampSource;
            MonotonicClockMapper.Snapshot c = r.clock == null ? null : r.clock.snapshot();
            clockQuality = c == null ? MonotonicClockMapper.Quality.NONE : c.quality;
            driftPpm = c == null ? Double.NaN : c.driftPpm;
            timestampUncertaintyNanos = MonotonicClockMapper.UNKNOWN_UNCERTAINTY;
            timestampObservations = c == null ? 0 : c.observations;
            rejectedTimestamps = c == null ? 0 : c.rejectedObservations;
        }
        public boolean isBusy() {
            return state == State.STARTING || state == State.CAPTURING || state == State.STOPPING;
        }
    }
    private static final class Run {
        final Object recordLock = new Object();
        volatile State state = State.STARTING;
        volatile StopReason reason;
        volatile String error;
        volatile boolean cancelled, clientSilenced, settling = true;
        volatile int sampleRate, bufferBytes, blockFrames, source, routeId = -1;
        volatile long framesRead, blocksRead, readErrors, detections, clipUntil;
        volatile float peak, rms, dbfs = -96, noiseRms;
        volatile Beat lastBeat;
        volatile TimestampSource timestampSource = TimestampSource.FRAME_INDEX;
        volatile MonotonicClockMapper clock;
        AudioRecord record; // publication/start/stop/release guarded by recordLock
    }
    private final AudioManager manager;
    private final Listener listener;
    private final Handler callbacks = new Handler(Looper.getMainLooper());
    private volatile Run current;
    private volatile Snapshot last = idleSnapshot();
    private volatile float sensitivity = .5f;
    public AudioInputEngine(AudioManager manager, Listener listener) {
        if (manager == null) throw new NullPointerException("audioManager");
        this.manager = manager; this.listener = listener;
    }
    private static Snapshot idleSnapshot() {
        Run r = new Run(); r.state = State.IDLE; return new Snapshot(r);
    }
    public void setSensitivity(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("sensitivity");
        sensitivity = value;
    }
    public synchronized boolean start() {
        if (current != null) return false;
        Run r = new Run(); current = r;
        new Thread(() -> capture(r), "Ritmics-AudioInput").start();
        return true;
    }
    public void stop(StopReason reason) {
        Run r = current;
        if (r == null) return;
        synchronized (r.recordLock) {
            if (r.cancelled) return;
            r.cancelled = true; r.reason = reason; r.state = State.STOPPING;
            if (r.record != null) {
                try { r.record.stop(); } catch (IllegalStateException ignored) { }
            }
        }
    }
    public Snapshot snapshot() { Run r = current; return r == null ? last : new Snapshot(r); }
    public boolean isBusy() { return current != null; }

    @SuppressLint("MissingPermission") // Activity requests RECORD_AUDIO; revocation is caught here.
    private void capture(Run r) {
        AudioRecord input = null;
        AudioRouting.OnRoutingChangedListener routeListener = null;
        AudioManager.AudioRecordingCallback configListener = null;
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            if (r.cancelled) return;
            input = createRecord(r);
            if (input == null) throw new IllegalStateException("No PCM configuration");
            r.clock = new MonotonicClockMapper(r.sampleRate);
            PcmBlock block = new PcmBlock(r.blockFrames);
            block.sampleRate = r.sampleRate;
            BeatDetector detector = new BeatDetector(r.sampleRate, (frame, strength) -> {
                r.lastBeat = new Beat(frame, r.clock.toNanos(frame), r.timestampSource, strength);
                r.detections++;
            });
            final AudioRecord local = input;
            routeListener = router -> {
                if (current != r || r.cancelled) return;
                AudioDeviceInfo device = router.getRoutedDevice();
                int id = device == null ? -1 : device.getId();
                if (r.routeId != -1 && r.routeId != id) stop(StopReason.ROUTE_CHANGED);
                else r.routeId = id;
            };
            input.addOnRoutingChangedListener(routeListener, callbacks);
            if (Build.VERSION.SDK_INT >= 29) {
                configListener = new AudioManager.AudioRecordingCallback() {
                    @Override public void onRecordingConfigChanged(java.util.List<AudioRecordingConfiguration> configs) {
                        if (current != r || r.cancelled) return;
                        for (AudioRecordingConfiguration c : configs) {
                            if (c.getClientAudioSessionId() == local.getAudioSessionId()) {
                                r.clientSilenced = c.isClientSilenced();
                            }
                        }
                    }
                };
                input.registerAudioRecordingCallback(command -> callbacks.post(command), configListener);
            }
            synchronized (r.recordLock) {
                r.record = input;
                if (r.cancelled) return;
                input.startRecording();
                if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                    throw new IllegalStateException("Not recording");
                r.state = State.CAPTURING;
            }
            AudioTimestamp stamp = new AudioTimestamp();
            int emptyReads = 0;
            while (!r.cancelled) {
                int read = input.read(block.samples, 0, block.samples.length, AudioRecord.READ_BLOCKING);
                long deliveredAt = System.nanoTime();
                if (r.cancelled) break;
                if (read < 0) { r.readErrors++; throw new IllegalStateException("AudioRecord read " + read); }
                if (read == 0) {
                    if (++emptyReads > 4) throw new IllegalStateException("No PCM progress");
                    Thread.sleep(5);
                    continue;
                }
                emptyReads = 0;
                block.firstFrame = r.framesRead; block.sampleCount = read;
                r.framesRead += read; r.blocksRead++;
                r.clock.expire(deliveredAt);
                if (r.blocksRead % 8 == 0 && input.getTimestamp(stamp, AudioTimestamp.TIMEBASE_MONOTONIC)
                        == AudioRecord.SUCCESS) {
                    r.clock.observe(stamp.framePosition, stamp.nanoTime, deliveredAt);
                }
                r.clock.seedEstimate(r.framesRead, deliveredAt);
                block.firstTimestampNanos = r.clock.toNanos(block.firstFrame);
                block.nanosPerFrame = r.clock.nanosPerFrame();
                r.timestampSource = r.clock.quality() == MonotonicClockMapper.Quality.HARDWARE
                        ? TimestampSource.HARDWARE_MONOTONIC : TimestampSource.SYSTEM_ESTIMATE;
                block.timestampSource = r.timestampSource;
                AudioLevelMeter.measure(block.samples, 0, read, block.level);
                r.peak = Math.max(block.level.peak, r.peak * .88f);
                r.rms = block.level.rms; r.dbfs = block.level.dbfs;
                if (block.level.clipping) r.clipUntil = deliveredAt + 500_000_000;
                detector.setSensitivity(sensitivity);
                detector.process(block.samples, 0, read, block.firstFrame);
                r.noiseRms = detector.getNoiseRms(); r.settling = detector.isSettling();
                if (listener != null) listener.onPcmBlock(block);
            }
        } catch (SecurityException denied) {
            fail(r, "Permissão de microfone indisponível. Confira as configurações.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (!r.cancelled) fail(r, "Captura interrompida. Inicie novamente.");
        } catch (RuntimeException failure) {
            if (!r.cancelled) fail(r, "Entrada indisponível ou interrompida. Confira o microfone e tente novamente.");
        } finally {
            synchronized (r.recordLock) {
                r.record = null;
                if (input != null) {
                    try {
                        if (routeListener != null) input.removeOnRoutingChangedListener(routeListener);
                        if (Build.VERSION.SDK_INT >= 29 && configListener != null)
                            input.unregisterAudioRecordingCallback(configListener);
                        input.stop();
                    } catch (RuntimeException ignored) {
                        // Always release even when the platform has already stopped the source.
                    } finally { input.release(); }
                }
            }
            r.peak = 0; r.rms = 0; r.clipUntil = 0;
            if (r.state != State.ERROR) r.state = State.IDLE;
            synchronized (this) {
                last = new Snapshot(r);
                if (current == r) current = null;
            }
            if (listener != null) listener.onStateChanged(last);
        }
    }
    private static void fail(Run r, String message) {
        r.state = State.ERROR; r.reason = StopReason.ERROR; r.error = message; r.cancelled = true;
    }
    @SuppressLint("MissingPermission")
    private AudioRecord createRecord(Run r) {
        boolean raw = "true".equalsIgnoreCase(manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
        int[] sources = raw ? new int[]{MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC}
                : new int[]{MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC};
        for (int source : sources) for (int rate : new int[]{48000, 44100, 16000}) {
            if (r.cancelled) return null;
            int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) continue;
            int block = rate / 200; // 5 ms blocks; larger native buffer protects scheduling.
            int bytes = Math.max(min * 2, block * 2 * 4);
            AudioRecord candidate = null;
            try {
                candidate = new AudioRecord.Builder().setAudioSource(source)
                        .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setBufferSizeInBytes(bytes).build();
                if (candidate.getState() == AudioRecord.STATE_INITIALIZED) {
                    r.sampleRate = candidate.getSampleRate(); r.blockFrames = block;
                    r.bufferBytes = candidate.getBufferSizeInFrames() * 2; r.source = source;
                    return candidate;
                }
            } catch (IllegalArgumentException | UnsupportedOperationException ignored) { }
            if (candidate != null) candidate.release();
        }
        return null;
    }
    private static String sourceName(int source) {
        if (source == MediaRecorder.AudioSource.UNPROCESSED) return "UNPROCESSED";
        if (source == MediaRecorder.AudioSource.VOICE_RECOGNITION) return "VOICE_RECOGNITION";
        return "MIC";
    }
}
