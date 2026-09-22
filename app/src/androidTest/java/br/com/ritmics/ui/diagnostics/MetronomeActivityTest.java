package br.com.ritmics.ui.diagnostics;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import br.com.ritmics.R;
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
                activity.findViewById(R.id.apply_bpm).performClick();
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
}
