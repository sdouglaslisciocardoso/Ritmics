package br.com.ritmics.calibration;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;

import br.com.ritmics.core.calibration.CalibrationProfile;

/** Local calibration profiles. No audio or account data is stored. */
public final class CalibrationRepository {
    private static final String PREFS = "calibration_profiles_v1";
    private static final String PREFIX = "profile_";
    private final SharedPreferences preferences;

    public CalibrationRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(CalibrationProfile profile) {
        preferences.edit().putString(key(profile.routeKey), encode(profile)).apply();
    }

    public CalibrationProfile load(String routeKey) {
        String stored = preferences.getString(key(routeKey), null);
        if (stored == null) return null;
        try { return decode(stored); }
        catch (RuntimeException invalid) {
            preferences.edit().remove(key(routeKey)).apply();
            return null;
        }
    }

    public void clearAll() { preferences.edit().clear().apply(); }

    private static String key(String routeKey) { return PREFIX + b64(routeKey); }

    private static String encode(CalibrationProfile p) {
        return "1\t" + b64(p.routeKey) + "\t" + b64(p.routeLabel) + "\t"
                + p.createdAtMillis + "\t" + p.acousticDelayMs + "\t" + p.manualAdjustmentMs
                + "\t" + p.noiseDbfs + "\t" + p.sensitivity + "\t" + p.acceptedSamples
                + "\t" + p.dispersionMs + "\t" + p.confidence.name() + "\t"
                + p.hardwareInputTimestamp + "\t" + p.hardwareOutputTimestamp;
    }

    private static CalibrationProfile decode(String value) {
        String[] p = value.split("\\t", -1);
        if (p.length != 13 || !"1".equals(p[0])) throw new IllegalArgumentException("version");
        return new CalibrationProfile(unb64(p[1]), unb64(p[2]), Long.parseLong(p[3]),
                Double.parseDouble(p[4]), Double.parseDouble(p[5]), Double.parseDouble(p[6]),
                Float.parseFloat(p[7]), Integer.parseInt(p[8]), Double.parseDouble(p[9]),
                CalibrationProfile.Confidence.valueOf(p[10]), Boolean.parseBoolean(p[11]),
                Boolean.parseBoolean(p[12]));
    }

    private static String b64(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP | Base64.URL_SAFE);
    }

    private static String unb64(String value) {
        return new String(Base64.decode(value, Base64.NO_WRAP | Base64.URL_SAFE), StandardCharsets.UTF_8);
    }
}
