package br.com.ritmics.ui.diagnostics;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
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
import br.com.ritmics.calibration.AudioRouteInfo;
import br.com.ritmics.calibration.CalibrationRepository;
import br.com.ritmics.core.calibration.CalibrationProfile;
import br.com.ritmics.core.calibration.CalibrationSupervisor;
import br.com.ritmics.core.calibration.LatencyCalibration;
import br.com.ritmics.core.music.MetronomeConfig;
import br.com.ritmics.core.audio.InterferenceProbe;
import br.com.ritmics.core.time.MonotonicClockMapper;
import java.util.Locale;

/**
 * Training setup screen. Technical controls (volume, microphone, diagnostics) live in the
 * settings panel of the same Activity so the audio session survives opening it.
 */
public final class MetronomeActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO = 1001;
    private static final long NOISE_MEASUREMENT_NANOS = 3_000_000_000L;
    private static final long ACOUSTIC_TIMEOUT_NANOS = 18_000_000_000L;
    private enum CalibrationMode { IDLE, NOISE, ACOUSTIC }
    /** Both calibrations run this pulse; the acoustic estimator assumes its 500 ms period. */
    private static final MetronomeConfig CALIBRATION_PULSE =
            new MetronomeConfig(120, 4, 1, MetronomeConfig.defaultAccents(4));
    private static final int[] METER_IDS = {R.id.meter_2_4, R.id.meter_3_4, R.id.meter_4_4, R.id.meter_6_8};
    private static final int[] METER_BEATS = {2, 3, 4, 6};
    private static final int[] SUBDIVISION_IDS = {R.id.subdivision_1, R.id.subdivision_2,
            R.id.subdivision_3, R.id.subdivision_4};
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private MetronomeEngine engine;
    private AudioInputEngine inputEngine;
    private EditText bpmInput;
    private SeekBar bpmSlider;
    private SeekBar volume;
    private RadioGroup meterGroup;
    private RadioGroup subdivisionGroup;
    private LinearLayout accentRow;
    private CheckBox[] beatToggles = new CheckBox[0];
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
    private TextView meterCaption, subdivisionCaption, accentHint;
    private View mainScreen, settingsPanel, livePanel;
    private ProgressBar microphoneLevel;
    private int bpm = 80;
    private int beatsPerBar = 4;
    private int subdivisions = 1;
    private boolean[] accents = MetronomeConfig.defaultAccents(4);
    private boolean polling;
    private boolean permissionAsked;
    private boolean sessionWithInput, probePending;
    private SeekBar sensitivity;
    private TextView sensitivityLabel, detectorStatus, probeStatus;
    private Button probeStart;
    private final InterferenceProbe probe = new InterferenceProbe();
    private CalibrationRepository calibrationRepository;
    private CalibrationProfile calibrationProfile;
    private AudioRouteInfo calibrationRoute;
    private String loadedRouteKey;
    private CalibrationMode calibrationMode = CalibrationMode.IDLE;
    private LatencyCalibration latencyCalibration;
    private long calibrationStartedNanos, measurementStartedNanos, lastCalibrationDetection;
    private double noiseSum;
    private int noiseSamples;
    private boolean noisePermissionPending, acousticPermissionPending;
    private double manualAdjustmentMs;
    private Button calibrateNoise, calibrateLatency, resetCalibration;
    private SeekBar manualOffset;
    private TextView calibrationStatus, calibrationProfileView, manualOffsetLabel;

    private final OnBackPressedCallback closeSettingsOnBack = new OnBackPressedCallback(false) {
        @Override public void handleOnBackPressed() { showSettings(false); }
    };

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
        calibrationRepository = new CalibrationRepository(getApplicationContext());
        configureInsets();
        bpmInput = findViewById(R.id.bpm_input);
        bpmSlider = findViewById(R.id.bpm_slider);
        volume = findViewById(R.id.volume);
        meterGroup = findViewById(R.id.meter_group);
        subdivisionGroup = findViewById(R.id.subdivision_group);
        accentRow = findViewById(R.id.accent_row);
        meterCaption = findViewById(R.id.meter_caption);
        subdivisionCaption = findViewById(R.id.subdivision_caption);
        accentHint = findViewById(R.id.accent_hint);
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
        calibrateNoise = findViewById(R.id.calibration_noise);
        calibrateLatency = findViewById(R.id.calibration_latency);
        resetCalibration = findViewById(R.id.calibration_reset);
        manualOffset = findViewById(R.id.manual_offset);
        manualOffsetLabel = findViewById(R.id.manual_offset_label);
        calibrationStatus = findViewById(R.id.calibration_status);
        calibrationProfileView = findViewById(R.id.calibration_profile);
        mainScreen = findViewById(R.id.main_screen);
        settingsPanel = findViewById(R.id.settings_panel);
        livePanel = findViewById(R.id.live_panel);
        // Screen readers announce the panel when it opens instead of having focus forced onto it.
        ViewCompat.setAccessibilityPaneTitle(settingsPanel, getString(R.string.settings_title));
        configureModes();

        if (savedState != null) bpm = Math.max(30, Math.min(240, savedState.getInt("bpm", 80)));
        setBpm(bpm);
        restoreStructure(savedState);
        mute.setChecked(savedState == null || savedState.getBoolean("mute", true));
        useMicrophone.setChecked(savedState != null && savedState.getBoolean("useMicrophone"));
        permissionAsked = savedState != null && savedState.getBoolean("permissionAsked");
        volume.setProgress(savedState == null ? 55 : savedState.getInt("volume", 55));
        updateVolume();
        sensitivity.setProgress(savedState == null ? 50 : savedState.getInt("sensitivity", 50));
        updateSensitivity();
        manualAdjustmentMs = savedState == null ? 0 : savedState.getDouble("manualAdjustmentMs", 0);
        manualOffset.setProgress((int) Math.round(manualAdjustmentMs) + 150);
        updateManualAdjustment(false);
        sensitivity.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateSensitivity();
            }
        });
        manualOffset.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateManualAdjustment(fromUser);
            }
        });

        findViewById(R.id.decrease).setOnClickListener(view -> adjustBpm(-1));
        findViewById(R.id.increase).setOnClickListener(view -> adjustBpm(1));
        bpmInput.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) {
                if (applyTypedBpm()) finishBpmEditing();
                return true;
            }
            return false;
        });
        bpmInput.setOnFocusChangeListener((view, focused) -> {
            // Leaving the field never keeps an invalid value on screen.
            if (!focused && !applyTypedBpm()) setBpm(bpm);
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
        calibrateNoise.setOnClickListener(view -> requestNoiseCalibration());
        calibrateLatency.setOnClickListener(view -> requestAcousticCalibration());
        resetCalibration.setOnClickListener(view -> {
            calibrationRepository.clearAll();
            calibrationProfile = null;
            calibrationRoute = null;
            manualAdjustmentMs = 0;
            manualOffset.setProgress(150);
            updateManualAdjustment(false);
            calibrationProfileView.setText(R.string.calibration_no_profile);
            calibrationStatus.setText(R.string.calibration_reset_done);
        });
        stop.setOnClickListener(view -> {
            cancelCalibration(true);
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
        findViewById(R.id.open_settings).setOnClickListener(view -> showSettings(true));
        findViewById(R.id.close_settings).setOnClickListener(view -> showSettings(false));
        getOnBackPressedDispatcher().addCallback(this, closeSettingsOnBack);
        showSettings(savedState != null && savedState.getBoolean("settingsVisible"));
        refreshState();
    }

    /** Only the live mode exists in this stage; the recorded mode is shown as upcoming. */
    private void configureModes() {
        View live = findViewById(R.id.mode_live);
        live.setSelected(true);
        ViewCompat.setStateDescription(live, getString(R.string.mode_selected));
        ViewCompat.setStateDescription(findViewById(R.id.mode_recorded), getString(R.string.mode_unavailable));
    }

    private void restoreStructure(Bundle savedState) {
        if (savedState != null) {
            int savedBeats = savedState.getInt("beatsPerBar", 4);
            beatsPerBar = MetronomeConfig.isSupportedMeter(savedBeats) ? savedBeats : 4;
            subdivisions = Math.max(1, Math.min(MetronomeConfig.MAX_SUBDIVISIONS,
                    savedState.getInt("subdivisions", 1)));
            boolean[] saved = savedState.getBooleanArray("accents");
            accents = saved != null && saved.length == beatsPerBar
                    ? saved : MetronomeConfig.defaultAccents(beatsPerBar);
        }
        for (int i = 0; i < METER_BEATS.length; i++) {
            if (METER_BEATS[i] == beatsPerBar) meterGroup.check(METER_IDS[i]);
        }
        subdivisionGroup.check(SUBDIVISION_IDS[subdivisions - 1]);
        buildAccentRow();
        updateCaptions();
        meterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            for (int i = 0; i < METER_IDS.length; i++) {
                if (METER_IDS[i] != checkedId || METER_BEATS[i] == beatsPerBar) continue;
                beatsPerBar = METER_BEATS[i];
                accents = MetronomeConfig.defaultAccents(beatsPerBar);
                buildAccentRow();
                updateCaptions();
            }
        });
        subdivisionGroup.setOnCheckedChangeListener((group, checkedId) -> {
            for (int i = 0; i < SUBDIVISION_IDS.length; i++) {
                if (SUBDIVISION_IDS[i] == checkedId) subdivisions = i + 1;
            }
            updateCaptions();
        });
    }

    private void buildAccentRow() {
        accentRow.removeAllViews();
        beatToggles = new CheckBox[beatsPerBar];
        // Six beats must still fit a 360 dp wide phone without horizontal scrolling.
        int gap = Math.round(getResources().getDisplayMetrics().density * (beatsPerBar > 4 ? 4 : 12));
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int beat = 0; beat < beatsPerBar; beat++) {
            View item = inflater.inflate(R.layout.item_beat, accentRow, false);
            if (beat > 0) ((LinearLayout.LayoutParams) item.getLayoutParams()).setMarginStart(gap);
            CheckBox toggle = item.findViewById(R.id.beat_toggle);
            TextView mark = item.findViewById(R.id.accent_mark);
            // Every item reuses the same ids; the Activity bundle owns the accent state.
            toggle.setSaveEnabled(false);
            toggle.setText(String.format(Locale.getDefault(), "%d", beat + 1));
            toggle.setContentDescription(getString(R.string.beat_description, beat + 1));
            toggle.setChecked(accents[beat]);
            mark.setVisibility(accents[beat] ? View.VISIBLE : View.INVISIBLE);
            final int index = beat;
            toggle.setOnCheckedChangeListener((button, checked) -> {
                accents[index] = checked;
                mark.setVisibility(checked ? View.VISIBLE : View.INVISIBLE);
            });
            beatToggles[beat] = toggle;
            accentRow.addView(item);
        }
    }

    private void updateCaptions() {
        boolean eighthPulse = MetronomeConfig.beatUnit(beatsPerBar) == 8;
        meterCaption.setText(getString(eighthPulse ? R.string.meter_caption_eighth
                : R.string.meter_caption_quarter, beatsPerBar));
        String[] captions = getResources().getStringArray(eighthPulse
                ? R.array.subdivision_captions_eighth : R.array.subdivision_captions_quarter);
        subdivisionCaption.setText(captions[subdivisions - 1]);
    }

    private void showSettings(boolean visible) {
        settingsPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        mainScreen.setImportantForAccessibility(visible
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        closeSettingsOnBack.setEnabled(visible);
    }

    private void updateSensitivity() {
        sensitivityLabel.setText(getString(R.string.sensitivity_value, sensitivity.getProgress()));
        inputEngine.setSensitivity(sensitivity.getProgress() / 100f);
    }

    private void updateManualAdjustment(boolean persist) {
        manualAdjustmentMs = manualOffset.getProgress() - 150;
        if (manualAdjustmentMs == 0) manualOffsetLabel.setText(R.string.calibration_manual_zero);
        else manualOffsetLabel.setText(getString(R.string.calibration_manual_value,
                (int) manualAdjustmentMs));
        ViewCompat.setStateDescription(manualOffset,
                getString(R.string.calibration_manual_value, (int) manualAdjustmentMs));
        if (persist && calibrationProfile != null) {
            calibrationProfile = calibrationProfile.withManualAdjustment(manualAdjustmentMs);
            calibrationRepository.save(calibrationProfile);
            showCalibrationProfile(calibrationProfile);
        }
    }

    private void requestNoiseCalibration() {
        if (engine.snapshot().isBusy() || inputEngine.isBusy()) return;
        useMicrophone.setChecked(true);
        noisePermissionPending = true;
        acousticPermissionPending = false;
        if (!hasMicrophonePermission()) { requestMicrophonePermission(); return; }
        beginNoiseCalibration();
    }

    private void requestAcousticCalibration() {
        if (engine.snapshot().isBusy() || inputEngine.isBusy()) return;
        useMicrophone.setChecked(true);
        acousticPermissionPending = true;
        noisePermissionPending = false;
        if (!hasMicrophonePermission()) { requestMicrophonePermission(); return; }
        beginAcousticCalibration();
    }

    private void beginNoiseCalibration() {
        noisePermissionPending = false;
        calibrationMode = CalibrationMode.NOISE;
        calibrationStartedNanos = System.nanoTime();
        measurementStartedNanos = 0;
        noiseSum = 0; noiseSamples = 0;
        // A silent output run identifies the output route, so the noise profile is saved under
        // the same key that is looked up later when training on this route.
        sessionWithInput = true;
        calibrationStatus.setText(R.string.calibration_noise_settling);
        if (!inputEngine.start() || !engine.start(CALIBRATION_PULSE, volume.getProgress() / 100f, true)) {
            inputEngine.stop(AudioInputEngine.StopReason.ERROR);
            engine.stop(MetronomeEngine.StopReason.FAILURE);
            failCalibration(R.string.calibration_latency_failed);
        }
        refreshState(); // Lock the controls now rather than on the next 100 ms poll.
    }

    private void beginAcousticCalibration() {
        acousticPermissionPending = false;
        calibrationMode = CalibrationMode.ACOUSTIC;
        calibrationStartedNanos = System.nanoTime();
        measurementStartedNanos = 0;
        lastCalibrationDetection = -1;
        latencyCalibration = new LatencyCalibration();
        sessionWithInput = true;
        calibrationStatus.setText(R.string.calibration_latency_waiting);
        if (!inputEngine.start()
                || !engine.start(CALIBRATION_PULSE, Math.max(.25f, volume.getProgress() / 100f), false)) {
            inputEngine.stop(AudioInputEngine.StopReason.ERROR);
            engine.stop(MetronomeEngine.StopReason.FAILURE);
            failCalibration(R.string.calibration_latency_failed);
        }
        refreshState(); // Lock the controls now rather than on the next 100 ms poll.
    }

    private void cancelCalibration(boolean userVisible) {
        if (calibrationMode == CalibrationMode.IDLE) return;
        calibrationMode = CalibrationMode.IDLE;
        noisePermissionPending = acousticPermissionPending = false;
        latencyCalibration = null;
        if (userVisible) calibrationStatus.setText(R.string.calibration_cancelled);
    }

    private void stopCalibrationAudio() {
        engine.stop(MetronomeEngine.StopReason.USER);
        inputEngine.stop(AudioInputEngine.StopReason.USER);
    }

    private static CalibrationSupervisor.PathState pathOf(MetronomeEngine.State state) {
        switch (state) {
            case STARTING: return CalibrationSupervisor.PathState.STARTING;
            case PLAYING: return CalibrationSupervisor.PathState.RUNNING;
            case ERROR: return CalibrationSupervisor.PathState.FAILED;
            default: return CalibrationSupervisor.PathState.STOPPED;
        }
    }

    private static CalibrationSupervisor.PathState pathOf(AudioInputEngine.State state) {
        switch (state) {
            case STARTING: return CalibrationSupervisor.PathState.STARTING;
            case CAPTURING: return CalibrationSupervisor.PathState.RUNNING;
            case ERROR: return CalibrationSupervisor.PathState.FAILED;
            default: return CalibrationSupervisor.PathState.STOPPED;
        }
    }

    private void failCalibration(int message) {
        calibrationMode = CalibrationMode.IDLE;
        latencyCalibration = null;
        calibrationStatus.setText(message);
    }

    private void requestStart(boolean testing) {
        if (!applyTypedBpm()) return;
        noisePermissionPending = acousticPermissionPending = false;
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
            noisePermissionPending = acousticPermissionPending = false;
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
        MetronomeConfig config = new MetronomeConfig(bpm, beatsPerBar, subdivisions, accents);
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
            if (noisePermissionPending) beginNoiseCalibration();
            else if (acousticPermissionPending) beginAcousticCalibration();
            else if (useMicrophone.isChecked()) startSession();
            else probePending = false;
            return;
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            probePending = false;
            noisePermissionPending = acousticPermissionPending = false;
            microphoneStatus.setText(R.string.microphone_permission_blocked);
            microphoneSettings.setVisibility(View.VISIBLE);
        } else {
            probePending = false;
            noisePermissionPending = acousticPermissionPending = false;
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
        // The design is dark-only, so system bar icons are always light.
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), root);
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
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

    private void finishBpmEditing() {
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(bpmInput.getWindowToken(), 0);
        bpmInput.clearFocus();
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
        // A running calibration owns the output pulse; the new tempo applies to the next session.
        if (calibrationMode == CalibrationMode.IDLE) engine.requestBpm(value);
    }

    private void updateVolume() {
        volumeLabel.setText(getString(R.string.volume_format, volume.getProgress()));
        engine.setVolume(volume.getProgress() / 100f);
    }

    private static void setTextIfChanged(TextView view, CharSequence text) {
        if (!text.toString().contentEquals(view.getText())) view.setText(text);
    }

    private static void setVisible(View view, boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != visibility) view.setVisibility(visibility);
    }

    private static void setChildrenEnabled(RadioGroup group, boolean enabled) {
        for (int i = 0; i < group.getChildCount(); i++) group.getChildAt(i).setEnabled(enabled);
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
        updateCalibration(state, input);
        loadProfileForActiveRoute(state, input);
        updateProbe(state, input);
        boolean busy = state.isBusy() || input.isBusy();
        boolean calibrating = calibrationMode != CalibrationMode.IDLE;
        boolean stoppable = calibrating || state.state == MetronomeEngine.State.PLAYING
                || state.state == MetronomeEngine.State.STARTING
                || input.state == AudioInputEngine.State.CAPTURING
                || input.state == AudioInputEngine.State.STARTING;
        start.setEnabled(!busy && !calibrating);
        stop.setEnabled(stoppable);
        // One primary action at a time, as in the design; stopping stays one tap away.
        setVisible(start, !stoppable);
        setVisible(stop, stoppable);
        setChildrenEnabled(meterGroup, !busy);
        setChildrenEnabled(subdivisionGroup, !busy);
        for (CheckBox toggle : beatToggles) toggle.setEnabled(!busy);
        setTextIfChanged(accentHint, getString(busy ? R.string.accent_locked : R.string.accent_hint));
        useMicrophone.setEnabled(!busy);
        boolean testing = probePending || probe.isRunning();
        probeStart.setEnabled(!busy && !testing);
        calibrateNoise.setEnabled(!busy && calibrationMode == CalibrationMode.IDLE);
        calibrateLatency.setEnabled(!busy && calibrationMode == CalibrationMode.IDLE);
        resetCalibration.setEnabled(!busy && calibrationMode == CalibrationMode.IDLE);
        manualOffset.setEnabled(!busy && calibrationMode == CalibrationMode.IDLE);
        // Both acoustic experiments assume a fixed pulse, level and detector sensitivity.
        boolean locked = testing || calibrating;
        volume.setEnabled(!locked);
        mute.setEnabled(!locked);
        sensitivity.setEnabled(!locked);
        bpmInput.setEnabled(!locked);
        bpmSlider.setEnabled(!locked);
        findViewById(R.id.decrease).setEnabled(!locked);
        findViewById(R.id.increase).setEnabled(!locked);
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
        setTextIfChanged(status, message);
        // The design has no idle status line; it appears once there is something to report.
        setVisible(status, state.state != MetronomeEngine.State.IDLE || state.reason != null);
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
        boolean tempoPending = busy && state.bpm != state.requestedBpm;
        if (tempoPending) setTextIfChanged(pending, getString(R.string.bpm_pending, state.requestedBpm));
        setVisible(pending, tempoPending);

        setVisible(livePanel, useMicrophone.isChecked());
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

    private void updateCalibration(MetronomeEngine.Snapshot output, AudioInputEngine.Snapshot input) {
        if (calibrationMode == CalibrationMode.IDLE) return;
        long now = System.nanoTime();
        boolean noise = calibrationMode == CalibrationMode.NOISE;
        boolean clocksReady = noise || (output.clock != null
                && output.clock.quality != MonotonicClockMapper.Quality.NONE);
        switch (CalibrationSupervisor.check(pathOf(output.state), pathOf(input.state), input.settling,
                clocksReady, now - calibrationStartedNanos, ACOUSTIC_TIMEOUT_NANOS)) {
            case INTERRUPTED:
                stopCalibrationAudio();
                failCalibration(R.string.calibration_cancelled);
                return;
            case FAILED:
            case TIMED_OUT:
                stopCalibrationAudio();
                failCalibration(R.string.calibration_latency_failed);
                return;
            case WAIT:
                calibrationStatus.setText(noise ? R.string.calibration_noise_settling
                        : R.string.calibration_latency_waiting);
                return;
            default:
                break;
        }
        if (noise) {
            if (measurementStartedNanos == 0) measurementStartedNanos = now;
            calibrationStatus.setText(R.string.calibration_noise_measuring);
            noiseSum += input.noiseRms;
            noiseSamples++;
            if (now - measurementStartedNanos < NOISE_MEASUREMENT_NANOS) return;
            float averageNoise = (float) (noiseSum / Math.max(1, noiseSamples));
            float recommended = LatencyCalibration.recommendedSensitivity(averageNoise);
            sensitivity.setProgress(Math.round(recommended * 100));
            updateSensitivity();
            calibrationRoute = routeOf(output, input);
            double dbfs = 20 * Math.log10(Math.max(1e-6, averageNoise));
            calibrationProfile = new CalibrationProfile(calibrationRoute.key, calibrationRoute.label,
                    System.currentTimeMillis(), 0, manualAdjustmentMs, dbfs, recommended, 0, 0,
                    CalibrationProfile.Confidence.UNAVAILABLE,
                    input.clockQuality == MonotonicClockMapper.Quality.HARDWARE, false);
            calibrationRepository.save(calibrationProfile);
            loadedRouteKey = calibrationRoute.key;
            showCalibrationProfile(calibrationProfile);
            calibrationStatus.setText(getString(R.string.calibration_noise_complete,
                    dbfs, Math.round(recommended * 100)));
            calibrationMode = CalibrationMode.IDLE;
            stopCalibrationAudio();
            return;
        }

        calibrationRoute = routeOf(output, input);
        if (calibrationRoute.bluetooth) {
            engine.stop(MetronomeEngine.StopReason.USER);
            inputEngine.stop(AudioInputEngine.StopReason.USER);
            failCalibration(R.string.calibration_bluetooth);
            return;
        }
        if (measurementStartedNanos == 0) measurementStartedNanos = now;
        if (input.lastBeat != null && input.detections != lastCalibrationDetection) {
            lastCalibrationDetection = input.detections;
            long intervalFrames = output.sampleRate / 2L; // Fixed 120 BPM calibration pulse.
            long firstTime = output.clock.toNanos(output.firstClickFrame);
            long elapsed = input.lastBeat.timestampNanos - firstTime;
            if (elapsed >= 0) {
                long click = Math.max(0, elapsed / 500_000_000L);
                long expectedFrame = output.firstClickFrame + click * intervalFrames;
                latencyCalibration.add(output.clock.toNanos(expectedFrame), input.lastBeat.timestampNanos);
            }
        }
        calibrationStatus.setText(getString(R.string.calibration_latency_measuring,
                latencyCalibration.sampleCount(), LatencyCalibration.TARGET_SAMPLES));
        boolean timedOut = now - measurementStartedNanos > ACOUSTIC_TIMEOUT_NANOS;
        if (!latencyCalibration.hasTargetSamples() && !timedOut) return;
        engine.stop(MetronomeEngine.StopReason.USER);
        inputEngine.stop(AudioInputEngine.StopReason.USER);
        if (!latencyCalibration.canFinish()) {
            failCalibration(R.string.calibration_latency_failed);
            return;
        }
        try {
            LatencyCalibration.Result result = latencyCalibration.result();
            boolean hardwareInput = input.clockQuality == MonotonicClockMapper.Quality.HARDWARE;
            boolean hardwareOutput = output.clock.quality == MonotonicClockMapper.Quality.HARDWARE;
            CalibrationProfile.Confidence confidence = result.confidence(hardwareInput,
                    hardwareOutput, calibrationRoute.bluetooth);
            double noiseDbfs = 20 * Math.log10(Math.max(1e-6, input.noiseRms));
            calibrationProfile = new CalibrationProfile(calibrationRoute.key, calibrationRoute.label,
                    System.currentTimeMillis(), result.delayMs, manualAdjustmentMs, noiseDbfs,
                    sensitivity.getProgress() / 100f, result.acceptedSamples, result.dispersionMs,
                    confidence, hardwareInput, hardwareOutput);
            calibrationRepository.save(calibrationProfile);
            loadedRouteKey = calibrationRoute.key;
            showCalibrationProfile(calibrationProfile);
            calibrationStatus.setText(getString(R.string.calibration_latency_complete,
                    result.delayMs, result.dispersionMs, confidenceLabel(confidence)));
            calibrationMode = CalibrationMode.IDLE;
            latencyCalibration = null;
        } catch (IllegalStateException unstable) {
            failCalibration(R.string.calibration_latency_failed);
        }
    }

    /** Single source of profile keys, so saving and loading always agree on the route. */
    private AudioRouteInfo routeOf(MetronomeEngine.Snapshot output, AudioInputEngine.Snapshot input) {
        return AudioRouteInfo.resolve((AudioManager) getSystemService(AUDIO_SERVICE), input.routeId,
                output.routeId, input.sampleRate, output.sampleRate, input.source);
    }

    private void showCalibrationProfile(CalibrationProfile profile) {
        if (profile.confidence == CalibrationProfile.Confidence.UNAVAILABLE) {
            calibrationProfileView.setText(getString(R.string.calibration_noise_profile_format,
                    profile.routeLabel, profile.noiseDbfs, Math.round(profile.sensitivity * 100)));
        } else {
            calibrationProfileView.setText(getString(R.string.calibration_profile_format,
                    profile.routeLabel, profile.acousticDelayMs, profile.manualAdjustmentMs,
                    profile.dispersionMs, profile.acceptedSamples, confidenceLabel(profile.confidence)));
        }
    }

    private void loadProfileForActiveRoute(MetronomeEngine.Snapshot output,
                                           AudioInputEngine.Snapshot input) {
        if (calibrationMode != CalibrationMode.IDLE
                || !CalibrationSupervisor.isLive(pathOf(output.state), pathOf(input.state))
                || input.routeId < 0 || output.routeId < 0
                || input.sampleRate <= 0 || output.sampleRate <= 0) return;
        AudioRouteInfo route = routeOf(output, input);
        if (route.key.equals(loadedRouteKey)) return;
        loadedRouteKey = route.key;
        calibrationRoute = route;
        calibrationProfile = calibrationRepository.load(route.key);
        if (calibrationProfile == null) {
            calibrationProfileView.setText(R.string.calibration_no_profile);
            manualAdjustmentMs = 0;
        } else {
            manualAdjustmentMs = calibrationProfile.manualAdjustmentMs;
            showCalibrationProfile(calibrationProfile);
        }
        manualOffset.setProgress((int) Math.round(manualAdjustmentMs) + 150);
        updateManualAdjustment(false);
    }

    private String confidenceLabel(CalibrationProfile.Confidence confidence) {
        switch (confidence) {
            case HIGH: return getString(R.string.calibration_confidence_high);
            case MEDIUM: return getString(R.string.calibration_confidence_medium);
            case LOW: return getString(R.string.calibration_confidence_low);
            default: return getString(R.string.calibration_confidence_unavailable);
        }
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
        cancelCalibration(false);
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
        out.putInt("beatsPerBar", beatsPerBar);
        out.putInt("subdivisions", subdivisions);
        out.putBooleanArray("accents", accents);
        out.putBoolean("mute", mute.isChecked());
        out.putBoolean("useMicrophone", useMicrophone.isChecked());
        out.putBoolean("permissionAsked", permissionAsked);
        out.putInt("volume", volume.getProgress());
        out.putInt("sensitivity", sensitivity.getProgress());
        out.putDouble("manualAdjustmentMs", manualAdjustmentMs);
        out.putBoolean("settingsVisible", settingsPanel.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(out);
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) { }
        @Override public void onStopTrackingTouch(SeekBar seekBar) { }
    }
}
