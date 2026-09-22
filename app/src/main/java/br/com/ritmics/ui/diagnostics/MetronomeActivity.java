package br.com.ritmics.ui.diagnostics;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import br.com.ritmics.R;
import br.com.ritmics.audio.input.AudioInputEngine;
import br.com.ritmics.audio.output.MetronomeEngine;
import br.com.ritmics.core.music.MetronomeConfig;
import br.com.ritmics.core.audio.InterferenceProbe;
import br.com.ritmics.core.time.MonotonicClockMapper;
import java.util.Locale;

/** Diagnostic screen only. The full training/session UI belongs to later stages. */
public final class MetronomeActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO = 1001;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private MetronomeEngine engine;
    private AudioInputEngine inputEngine;
    private EditText bpmInput;
    private SeekBar bpmSlider;
    private SeekBar volume;
    private Spinner meter;
    private SwitchCompat accent;
    private SwitchCompat subdivision;
    private SwitchCompat mute;
    private SwitchCompat useMicrophone;
    private Button start;
    private Button stop;
    private Button microphoneSettings;
    private TextView status;
    private TextView microphoneStatus;
    private TextView diagnostics;
    private TextView pending;
    private TextView volumeLabel;
    private ProgressBar microphoneLevel;
    private int bpm = 80;
    private boolean polling;
    private boolean permissionAsked;
    private boolean sessionWithInput, probePending;
    private SeekBar sensitivity;
    private TextView sensitivityLabel, detectorStatus, probeStatus;
    private Button probeStart;
    private final InterferenceProbe probe = new InterferenceProbe();

    // UI polling only reads diagnostics; it never triggers a musical event.
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!polling) return;
            refreshState();
            uiHandler.postDelayed(this, 100);
        }
    };

    private final BroadcastReceiver becomingNoisy = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                inputEngine.stop(AudioInputEngine.StopReason.ROUTE_CHANGED);
                engine.stop(MetronomeEngine.StopReason.ROUTE_CHANGED);
                refreshState();
            }
        }
    };

    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_metronome);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        engine = new MetronomeEngine(getApplicationContext());
        inputEngine = new AudioInputEngine((AudioManager) getSystemService(AUDIO_SERVICE), null);
        configureInsets();
        bpmInput = findViewById(R.id.bpm_input);
        bpmSlider = findViewById(R.id.bpm_slider);
        volume = findViewById(R.id.volume);
        meter = findViewById(R.id.meter);
        accent = findViewById(R.id.accent);
        subdivision = findViewById(R.id.subdivision);
        mute = findViewById(R.id.mute);
        useMicrophone = findViewById(R.id.use_microphone);
        start = findViewById(R.id.start);
        stop = findViewById(R.id.stop);
        microphoneSettings = findViewById(R.id.microphone_settings);
        status = findViewById(R.id.status);
        microphoneStatus = findViewById(R.id.microphone_status);
        microphoneLevel = findViewById(R.id.microphone_level);
        diagnostics = findViewById(R.id.diagnostics);
        pending = findViewById(R.id.pending_tempo);
        volumeLabel = findViewById(R.id.volume_label);
        sensitivity = findViewById(R.id.sensitivity);
        sensitivityLabel = findViewById(R.id.sensitivity_label);
        detectorStatus = findViewById(R.id.detector_status);
        probeStatus = findViewById(R.id.probe_status);
        probeStart = findViewById(R.id.probe_start);

        if (savedState != null) bpm = Math.max(30, Math.min(240, savedState.getInt("bpm", 80)));
        setBpm(bpm);
        meter.setSelection(savedState == null ? 2 : savedState.getInt("meter", 2));
        accent.setChecked(savedState == null || savedState.getBoolean("accent", true));
        subdivision.setChecked(savedState != null && savedState.getBoolean("subdivision"));
        mute.setChecked(savedState == null || savedState.getBoolean("mute", true));
        useMicrophone.setChecked(savedState != null && savedState.getBoolean("useMicrophone"));
        permissionAsked = savedState != null && savedState.getBoolean("permissionAsked");
        volume.setProgress(savedState == null ? 55 : savedState.getInt("volume", 55));
        updateVolume();
        sensitivity.setProgress(savedState == null ? 50 : savedState.getInt("sensitivity", 50));
        updateSensitivity();
        sensitivity.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateSensitivity();
            }
        });

        findViewById(R.id.decrease).setOnClickListener(view -> adjustBpm(-1));
        findViewById(R.id.increase).setOnClickListener(view -> adjustBpm(1));
        findViewById(R.id.apply_bpm).setOnClickListener(view -> applyTypedBpm());
        bpmInput.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) {
                applyTypedBpm();
                return true;
            }
            return false;
        });
        bpmSlider.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) setBpm(progress + MetronomeConfig.MIN_BPM);
            }
        });
        volume.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateVolume();
            }
        });
        mute.setOnCheckedChangeListener((button, checked) -> {
            engine.setMuted(checked);
            refreshState();
        });
        start.setOnClickListener(view -> requestStart(false));
        probeStart.setOnClickListener(view -> {
            useMicrophone.setChecked(true);
            requestStart(true);
        });
        stop.setOnClickListener(view -> {
            probePending = false;
            probe.cancel();
            inputEngine.stop(AudioInputEngine.StopReason.USER);
            engine.stop(MetronomeEngine.StopReason.USER);
            refreshState();
        });
        microphoneSettings.setOnClickListener(view -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        useMicrophone.setOnCheckedChangeListener((button, checked) -> refreshState());
        refreshState();
    }

    private void updateSensitivity() {
        sensitivityLabel.setText(getString(R.string.sensitivity_value, sensitivity.getProgress()));
        inputEngine.setSensitivity(sensitivity.getProgress() / 100f);
    }

    private void requestStart(boolean testing) {
        if (!applyTypedBpm()) return;
        // Preserve the intent while the permission dialog is visible so a granted
        // permission can continue the exact action the user requested.
        probePending = testing;
        if (useMicrophone.isChecked() && !hasMicrophonePermission()) {
            requestMicrophonePermission();
            return;
        }
        startSession();
    }

    private boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMicrophonePermission() {
        if (permissionAsked && !ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            microphoneStatus.setText(R.string.microphone_permission_blocked);
            microphoneSettings.setVisibility(View.VISIBLE);
            return;
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            microphoneStatus.setText(R.string.microphone_permission_rationale);
        }
        permissionAsked = true;
        ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO);
    }

    private void startSession() {
        if (!polling || engine.snapshot().isBusy() || inputEngine.isBusy()) return;
        sessionWithInput = useMicrophone.isChecked();
        MetronomeConfig config = new MetronomeConfig(bpm, meter.getSelectedItemPosition() + 2,
                subdivision.isChecked() ? 2 : 1, accent.isChecked());
        if (useMicrophone.isChecked() && !inputEngine.start()) {
            microphoneStatus.setText(R.string.microphone_error);
            return;
        }
        if (!engine.start(config, volume.getProgress() / 100f, probePending || mute.isChecked())) {
            inputEngine.stop(AudioInputEngine.StopReason.ERROR);
            probePending = false;
        }
        refreshState();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                      @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            permissionAsked = false;
            refreshState();
            microphoneStatus.setText(R.string.microphone_granted);
            if (useMicrophone.isChecked()) startSession();
            else probePending = false;
            return;
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            probePending = false;
            microphoneStatus.setText(R.string.microphone_permission_blocked);
            microphoneSettings.setVisibility(View.VISIBLE);
        } else {
            probePending = false;
            microphoneStatus.setText(R.string.microphone_no_permission);
        }
        refreshState();
    }

    private void configureInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), root);
        controller.setAppearanceLightStatusBars(!dark);
        controller.setAppearanceLightNavigationBars(!dark);
    }

    private boolean applyTypedBpm() {
        try {
            int candidate = Integer.parseInt(bpmInput.getText().toString().trim());
            MetronomeConfig.requireBpm(candidate);
            setBpm(candidate);
            return true;
        } catch (IllegalArgumentException invalid) {
            bpmInput.setError(getString(R.string.bpm_error));
            return false;
        }
    }

    private void adjustBpm(int change) {
        if (applyTypedBpm()) setBpm(Math.max(30, Math.min(240, bpm + change)));
    }

    private void setBpm(int value) {
        bpm = value;
        bpmInput.setError(null);
        bpmInput.setText(Integer.toString(value));
        bpmSlider.setProgress(value - MetronomeConfig.MIN_BPM);
        // Expose the real BPM, rather than the slider's 0..210 storage range, to accessibility.
        ViewCompat.setStateDescription(bpmSlider, value + " " + getString(R.string.bpm_unit));
        engine.requestBpm(value);
    }

    private void updateVolume() {
        volumeLabel.setText(getString(R.string.volume_format, volume.getProgress()));
        engine.setVolume(volume.getProgress() / 100f);
    }

    private void refreshState() {
        MetronomeEngine.Snapshot state = engine.snapshot();
        AudioInputEngine.Snapshot input = inputEngine.snapshot();
        // Both devices stop on either path failing; gain of focus never restarts capture.
        if (sessionWithInput && (state.state == MetronomeEngine.State.ERROR
                || state.state == MetronomeEngine.State.IDLE || state.state == MetronomeEngine.State.STOPPING)
                && input.isBusy()) {
            inputEngine.stop(AudioInputEngine.StopReason.ERROR);
        }
        if (sessionWithInput && (input.state == AudioInputEngine.State.ERROR
                || input.state == AudioInputEngine.State.IDLE || input.state == AudioInputEngine.State.STOPPING)
                && state.isBusy()) engine.stop(MetronomeEngine.StopReason.FAILURE);
        updateProbe(state, input);
        boolean busy = state.isBusy() || input.isBusy();
        start.setEnabled(!busy);
        stop.setEnabled(state.state == MetronomeEngine.State.PLAYING
                || state.state == MetronomeEngine.State.STARTING
                || input.state == AudioInputEngine.State.CAPTURING
                || input.state == AudioInputEngine.State.STARTING);
        meter.setEnabled(!busy);
        accent.setEnabled(!busy);
        subdivision.setEnabled(!busy);
        useMicrophone.setEnabled(!busy);
        boolean testing = probePending || probe.isRunning();
        probeStart.setEnabled(!busy && !testing);
        volume.setEnabled(!testing);
        mute.setEnabled(!testing);
        sensitivity.setEnabled(!testing);
        bpmInput.setEnabled(!testing);
        bpmSlider.setEnabled(!testing);
        findViewById(R.id.apply_bpm).setEnabled(!testing);
        findViewById(R.id.decrease).setEnabled(!testing);
        findViewById(R.id.increase).setEnabled(!testing);
        if (busy) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String message;
        switch (state.state) {
            case STARTING: message = getString(R.string.status_starting); break;
            case PLAYING: message = getString(mute.isChecked() ? R.string.status_muted : R.string.status_playing); break;
            case STOPPING: message = getString(R.string.status_stopping); break;
            case ERROR: message = state.error == null ? getString(R.string.status_error) : state.error; break;
            default:
                if (state.reason == MetronomeEngine.StopReason.BACKGROUND) message = getString(R.string.status_background);
                else if (state.reason == MetronomeEngine.StopReason.FOCUS_LOSS) message = getString(R.string.status_focus);
                else if (state.reason == MetronomeEngine.StopReason.ROUTE_CHANGED) message = getString(R.string.status_route);
                else message = getString(state.reason == null ? R.string.status_ready : R.string.status_stopped);
        }
        if (!message.contentEquals(status.getText())) status.setText(message);
        if (state.sampleRate > 0) {
            String outputText = getString(R.string.diagnostics_format, state.sampleRate, state.bufferFrames,
                    state.bufferFrames * 1000.0 / state.sampleRate,
                    getString(state.lowLatency ? R.string.yes : R.string.no), state.bpm,
                    state.eventsRendered, state.framesWritten, state.underruns);
            String inputText = input.sampleRate > 0 ? getString(R.string.input_diagnostics_format,
                    input.sampleRate, input.bufferBytes, input.blockFrames, input.sourceName,
                    input.framesRead, input.blocksRead, input.readErrors,
                    input.timestampSource == null ? "—" : input.timestampSource.name(),
                    input.clockQuality == null ? "NONE" : input.clockQuality.name(),
                    ppm(input.driftPpm),
                    input.timestampObservations, input.rejectedTimestamps) : "";
            double outputDrift = state.clock == null ? Double.NaN : state.clock.driftPpm;
            String timing = getString(R.string.clock_output,
                    clockLabel(state.clock == null ? MonotonicClockMapper.Quality.NONE : state.clock.quality),
                    ppm(outputDrift), ppm(input.driftPpm - outputDrift));
            diagnostics.setText(outputText + "\n\n" + inputText + "\n" + timing);
        }
        pending.setText(busy && state.bpm != state.requestedBpm
                ? getString(R.string.bpm_pending, state.requestedBpm) : getString(R.string.tempo_live_hint));

        microphoneSettings.setVisibility(View.GONE);
        int levelPercent = Math.max(0, Math.min(100, Math.round(input.peak * 100f)));
        microphoneLevel.setProgress(levelPercent);
        if (!useMicrophone.isChecked()) {
            microphoneStatus.setText(R.string.microphone_ready);
        } else if (input.state == AudioInputEngine.State.STARTING) {
            microphoneStatus.setText(R.string.microphone_starting);
        } else if (input.state == AudioInputEngine.State.CAPTURING) {
            if (input.clientSilenced) microphoneStatus.setText(R.string.microphone_silenced);
            else if (input.clipping) microphoneStatus.setText(R.string.microphone_clipping);
            else microphoneStatus.setText(getString(R.string.microphone_capturing, levelPercent));
        } else if (input.state == AudioInputEngine.State.ERROR) {
            microphoneStatus.setText(input.error == null ? getString(R.string.microphone_error) : input.error);
        } else if (!hasMicrophonePermission() && permissionAsked) {
            microphoneStatus.setText(R.string.microphone_no_permission);
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                microphoneSettings.setVisibility(View.VISIBLE);
            }
        } else {
            microphoneStatus.setText(R.string.microphone_ready);
        }
        if (input.sampleRate == 0) detectorStatus.setText(R.string.detector_idle);
        else if (input.settling && input.isBusy()) detectorStatus.setText(R.string.detector_settling);
        else if (input.lastBeat == null) detectorStatus.setText(getString(R.string.detector_count, input.detections));
        else detectorStatus.setText(getString(R.string.detector_last, input.detections,
                input.lastBeat.frame, clockLabel(input.lastBeat.timestampSource
                        == AudioInputEngine.TimestampSource.HARDWARE_MONOTONIC
                        ? MonotonicClockMapper.Quality.HARDWARE : MonotonicClockMapper.Quality.ESTIMATED),
                20 * Math.log10(Math.max(1e-5, input.noiseRms))));
    }

    private String ppm(double value) {
        return Double.isFinite(value) ? String.format(Locale.getDefault(), "%.1f ppm", value)
                : getString(R.string.clock_pending);
    }
    private String clockLabel(MonotonicClockMapper.Quality quality) {
        return getString(quality == MonotonicClockMapper.Quality.HARDWARE ? R.string.clock_hardware
                : quality == MonotonicClockMapper.Quality.ESTIMATED ? R.string.clock_estimated : R.string.clock_pending);
    }
    private void updateProbe(MetronomeEngine.Snapshot output, AudioInputEngine.Snapshot input) {
        boolean ready = output.state == MetronomeEngine.State.PLAYING
                && input.state == AudioInputEngine.State.CAPTURING;
        boolean failed = output.state == MetronomeEngine.State.ERROR || output.state == MetronomeEngine.State.IDLE
                || output.state == MetronomeEngine.State.STOPPING || input.state == AudioInputEngine.State.ERROR
                || input.state == AudioInputEngine.State.IDLE || input.state == AudioInputEngine.State.STOPPING;
        if ((probePending || probe.isRunning()) && failed) { probePending = false; probe.cancel(); }
        if (probePending && ready && !input.settling) {
            probePending = false;
            probe.start(System.nanoTime(), input.detections);
        }
        if (probe.isRunning()) {
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            boolean invalid = input.clientSilenced || input.clipping || volume.getProgress() == 0
                    || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0;
            InterferenceProbe.Phase phase = probe.advance(System.nanoTime(), input.detections, invalid);
            engine.setMuted(phase != InterferenceProbe.Phase.CLICK);
            if (phase == InterferenceProbe.Phase.COMPLETE) {
                inputEngine.stop(AudioInputEngine.StopReason.USER);
                engine.stop(MetronomeEngine.StopReason.USER);
                mute.setChecked(true); // safe fallback following an acoustic experiment
            }
        }
        switch (probe.phase()) {
            case QUIET: probeStatus.setText(R.string.probe_quiet); break;
            case CLICK: probeStatus.setText(R.string.probe_click); break;
            case TAIL: probeStatus.setText(R.string.probe_tail); break;
            case CANCELLED: probeStatus.setText(R.string.probe_cancelled); break;
            case COMPLETE:
                if (probe.isInconclusive()) probeStatus.setText(R.string.probe_invalid);
                else probeStatus.setText(getString(R.string.probe_result, probe.baseline(), probe.exposed(),
                        getString(probe.baseline() + probe.exposed() > 0
                                ? R.string.probe_contaminated : R.string.probe_no_candidates)));
                break;
            default: break;
        }
    }

    @Override protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(this, becomingNoisy,
                new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override protected void onResume() {
        super.onResume();
        polling = true;
        uiHandler.post(refresh);
    }

    @Override protected void onPause() {
        probePending = false;
        probe.cancel();
        polling = false;
        uiHandler.removeCallbacks(refresh);
        engine.stop(MetronomeEngine.StopReason.BACKGROUND);
        inputEngine.stop(AudioInputEngine.StopReason.BACKGROUND);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }

    @Override protected void onStop() {
        unregisterReceiver(becomingNoisy);
        super.onStop();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        out.putInt("bpm", bpm);
        out.putInt("meter", meter.getSelectedItemPosition());
        out.putBoolean("accent", accent.isChecked());
        out.putBoolean("subdivision", subdivision.isChecked());
        out.putBoolean("mute", mute.isChecked());
        out.putBoolean("useMicrophone", useMicrophone.isChecked());
        out.putBoolean("permissionAsked", permissionAsked);
        out.putInt("volume", volume.getProgress());
        out.putInt("sensitivity", sensitivity.getProgress());
        super.onSaveInstanceState(out);
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) { }
        @Override public void onStopTrackingTouch(SeekBar seekBar) { }
    }
}
