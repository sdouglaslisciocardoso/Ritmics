package br.com.ritmics.calibration;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import java.util.Locale;

/** Stable-enough route identity for calibration profiles; device ids are session-only. */
public final class AudioRouteInfo {
    public final String key;
    public final String label;
    public final boolean bluetooth;

    private AudioRouteInfo(String key, String label, boolean bluetooth) {
        this.key = key;
        this.label = label;
        this.bluetooth = bluetooth;
    }

    public static AudioRouteInfo resolve(AudioManager manager, int inputId, int outputId,
                                         int inputRate, int outputRate, int source) {
        AudioDeviceInfo input = find(manager, inputId);
        AudioDeviceInfo output = find(manager, outputId);
        String inputSignature = signature(input, "entrada");
        String outputSignature = signature(output, "saída");
        String key = String.format(Locale.ROOT, "v1|%s|%s|%d|%d|%d",
                inputSignature, outputSignature, inputRate, outputRate, source);
        String label = "Saída: " + display(output, "não identificada")
                + " · Entrada: " + display(input, "não identificada");
        return new AudioRouteInfo(key, label, isBluetooth(input) || isBluetooth(output));
    }

    private static AudioDeviceInfo find(AudioManager manager, int id) {
        if (id < 0) return null;
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (device.getId() == id) return device;
        }
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.getId() == id) return device;
        }
        return null;
    }

    private static String signature(AudioDeviceInfo device, String fallback) {
        if (device == null) return fallback;
        String address = Build.VERSION.SDK_INT >= 28 ? device.getAddress() : "";
        return device.getType() + ":" + clean(address) + ":"
                + clean(String.valueOf(device.getProductName()));
    }

    private static String display(AudioDeviceInfo device, String fallback) {
        if (device == null) return fallback;
        String product = clean(String.valueOf(device.getProductName()));
        String type = typeName(device.getType());
        return product.isEmpty() || product.equalsIgnoreCase("null") ? type : product + " (" + type + ")";
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('|', '_').replace('\t', ' ').trim();
    }

    private static String typeName(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC: return "microfone interno";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "alto-falante";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "fone com microfone";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "fone com fio";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth";
            default: return "tipo " + type;
        }
    }

    private static boolean isBluetooth(AudioDeviceInfo device) {
        if (device == null) return false;
        int type = device.getType();
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return true;
        if (Build.VERSION.SDK_INT >= 31 && (type == AudioDeviceInfo.TYPE_BLE_HEADSET
                || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) return true;
        return Build.VERSION.SDK_INT >= 33 && type == AudioDeviceInfo.TYPE_BLE_BROADCAST;
    }
}
