package br.com.ritmics.ui.diagnostics;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.lang.reflect.Field;
import java.util.function.Predicate;

import br.com.ritmics.R;
import br.com.ritmics.audio.input.AudioInputEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** UI smoke tests. No claim about acoustic precision; those tests require a real device. */
@RunWith(AndroidJUnit4.class)
public final class MetronomeActivityTest {
    @Test public void opensAndPreservesBpmAcrossRecreationWithoutStartingPlayback() {
        try (ActivityScenario<MetronomeActivity> scenario = ActivityScenario.launch(MetronomeActivity.class)) {
            scenario.onActivity(activity -> {
                EditText input = activity.findViewById(R.id.bpm_input);
                input.setText("137");
                input.onEditorAction(EditorInfo.IME_ACTION_DONE);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("137", ((EditText) activity.findViewById(R.id.bpm_input)).getText().toString());
                assertTrue(activity.findViewById(R.id.start).isEnabled());
                assertFalse(activity.findViewById(R.id.stop).isEnabled());
            });
        }
    }

    @Test public void invalidBpmDoesNotStartAudio() {
        try (ActivityScenario<MetronomeActivity> scenario = ActivityScenario.launch(MetronomeActivity.class)) {
            scenario.onActivity(activity -> {
                EditText input = activity.findViewById(R.id.bpm_input);
                input.setText("0");
                activity.findViewById(R.id.start).performClick();
                assertNotNull(input.getError());
                assertTrue(activity.findViewById(R.id.start).isEnabled());
            });
        }
    }

    @Test public void declaresMicrophoneWithoutInternet() throws PackageManager.NameNotFoundException {
        Context context = ApplicationProvider.getApplicationContext();
        String[] requested = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
        assertTrue(java.util.Arrays.asList(requested).contains(Manifest.permission.RECORD_AUDIO));
        assertEquals(PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.INTERNET));
    }

    @Test public void detectorDefaultsToMutedClickAndDoesNotCaptureOnLaunch() {
        try (ActivityScenario<MetronomeActivity> scenario = ActivityScenario.launch(MetronomeActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(((androidx.appcompat.widget.SwitchCompat) activity.findViewById(R.id.mute)).isChecked());
                assertFalse(((androidx.appcompat.widget.SwitchCompat) activity.findViewById(R.id.use_microphone)).isChecked());
                assertTrue(activity.findViewById(R.id.probe_start).isEnabled());
            });
        }
    }

    @Test public void calibrationControlsAreIdleAndManualAdjustmentStartsAtZero() {
        try (ActivityScenario<MetronomeActivity> scenario = ActivityScenario.launch(MetronomeActivity.class)) {
            scenario.onActivity(activity -> {
                android.widget.SeekBar manual = activity.findViewById(R.id.manual_offset);
                assertEquals(150, manual.getProgress());
                assertTrue(activity.findViewById(R.id.calibration_noise).isEnabled());
                assertTrue(activity.findViewById(R.id.calibration_latency).isEnabled());
                assertFalse(activity.findViewById(R.id.stop).isShown());
            });
        }
    }

    @Test public void compoundMeterShowsSixBeatsWithBothGroupsAccented() {
        try (ActivityScenario<MetronomeActivity> scenario = ActivityScenario.launch(MetronomeActivity.class)) {
            scenario.onActivity(activity -> {
                ((RadioGroup) activity.findViewById(R.id.meter_group)).check(R.id.meter_6_8);
                LinearLayout beats = activity.findViewById(R.id.accent_row);
                assertEquals(6, beats.getChildCount());
                for (int i = 0; i < 6; i++) {
                    CheckBox beat = beats.getChildAt(i).findViewById(R.id.beat_toggle);
                    assertEquals(i == 0 || i == 3, beat.isChecked());
                }
            });
        }
    }

    @Test public void structureChoicesSurviveRecreation() {
        try (ActivityScenario<MetronomeActivity> scenario = ActivityScenario.launch(MetronomeActivity.class)) {
            scenario.onActivity(activity -> {
                ((RadioGroup) activity.findViewById(R.id.meter_group)).check(R.id.meter_3_4);
                ((RadioGroup) activity.findViewById(R.id.subdivision_group)).check(R.id.subdivision_3);
                LinearLayout beats = activity.findViewById(R.id.accent_row);
                ((CheckBox) beats.getChildAt(2).findViewById(R.id.beat_toggle)).setChecked(true);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(R.id.meter_3_4, ((RadioGroup) activity.findViewById(R.id.meter_group)).getCheckedRadioButtonId());
                assertEquals(R.id.subdivision_3,
                        ((RadioGroup) activity.findViewById(R.id.subdivision_group)).getCheckedRadioButtonId());
                LinearLayout beats = activity.findViewById(R.id.accent_row);
                assertEquals(3, beats.getChildCount());
                assertTrue(((CheckBox) beats.getChildAt(0).findViewById(R.id.beat_toggle)).isChecked());
                assertFalse(((CheckBox) beats.getChildAt(1).findViewById(R.id.beat_toggle)).isChecked());
                assertTrue(((CheckBox) beats.getChildAt(2).findViewById(R.id.beat_toggle)).isChecked());
                assertTrue(((TextView) activity.findViewById(R.id.subdivision_caption)).getText()
                        .toString().startsWith("Tercinas"));
            });
        }
    }

    @Test public void calibrationEndsAsSoonAsCaptureStopsMidway() throws InterruptedException {
        grantMicrophone();
        try (ActivityScenario<MetronomeActivity> scenario = ActivityScenario.launch(MetronomeActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.calibration_latency).performClick());
            waitFor(scenario, 5000,
                    activity -> inputEngine(activity).snapshot().state == AudioInputEngine.State.CAPTURING);
            // A device change stops only the capture, exactly as the engine's route listener does.
            scenario.onActivity(activity -> inputEngine(activity).stop(AudioInputEngine.StopReason.ROUTE_CHANGED));
            waitFor(scenario, 2000, activity -> activity.findViewById(R.id.calibration_latency).isEnabled());
            scenario.onActivity(activity -> assertEquals(activity.getString(R.string.calibration_cancelled),
                    ((TextView) activity.findViewById(R.id.calibration_status)).getText().toString()));
        }
    }

    private static void grantMicrophone() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                ApplicationProvider.getApplicationContext().getPackageName(), Manifest.permission.RECORD_AUDIO);
    }

    /** Polls on the main thread; the Activity itself refreshes its state every 100 ms. */
    private static void waitFor(ActivityScenario<MetronomeActivity> scenario, long timeoutMs,
                                Predicate<MetronomeActivity> condition) throws InterruptedException {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        boolean[] met = {false};
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> met[0] = condition.test(activity));
            if (met[0]) return;
            Thread.sleep(50);
        }
        fail("Condition not met within " + timeoutMs + " ms");
    }

    /** Route changes cannot be triggered from a test, so the private capture engine is reached directly. */
    private static AudioInputEngine inputEngine(MetronomeActivity activity) {
        try {
            Field field = MetronomeActivity.class.getDeclaredField("inputEngine");
            field.setAccessible(true);
            return (AudioInputEngine) field.get(activity);
        } catch (ReflectiveOperationException unreachable) {
            throw new AssertionError(unreachable);
        }
    }
}
