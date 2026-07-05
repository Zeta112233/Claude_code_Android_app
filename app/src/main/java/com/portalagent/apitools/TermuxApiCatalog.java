package com.portalagent.apitools;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TermuxApiCatalog {

    private TermuxApiCatalog() {}

    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
        entry("AudioInfo", "termux-audio-info", "audio", "read", "intent", null),
        entry("BatteryStatus", "termux-battery-status", "power", "read", "http", "/battery"),
        entry("Brightness", "termux-brightness", "display", "mutating", "intent",
            null, "android.permission.WRITE_SETTINGS"),
        entry("CameraInfo", "termux-camera-info", "camera", "read", "http", "/camera"),
        entry("CameraPhoto", "termux-camera-photo", "camera", "capture", "intent",
            null, "android.permission.CAMERA"),
        entry("CallLog", "termux-call-log", "phone", "sensitive_read", "intent",
            null, "android.permission.READ_CALL_LOG"),
        entry("Clipboard", "termux-clipboard-get/set", "clipboard", "sensitive_read_write", "http", "/clipboard"),
        entry("ContactList", "termux-contact-list", "contacts", "sensitive_read", "intent",
            null, "android.permission.READ_CONTACTS"),
        entry("Dialog", "termux-dialog", "ui", "user_interactive", "intent", null),
        entry("Download", "termux-download", "network", "mutating", "intent", null),
        entry("Fingerprint", "termux-fingerprint", "auth", "user_interactive", "intent",
            null, "android.permission.USE_BIOMETRIC"),
        entry("InfraredFrequencies", "termux-infrared-frequencies", "infrared", "read", "intent",
            null, "android.permission.TRANSMIT_IR"),
        entry("InfraredTransmit", "termux-infrared-transmit", "infrared", "mutating", "intent",
            null, "android.permission.TRANSMIT_IR"),
        entry("JobScheduler", "termux-job-scheduler", "jobs", "mutating", "intent", null),
        entry("Keystore", "termux-keystore", "security", "sensitive", "intent", null),
        entry("Location", "termux-location", "location", "sensitive_read", "intent",
            null, "android.permission.ACCESS_FINE_LOCATION"),
        entry("MediaPlayer", "termux-media-player", "media", "mutating", "intent", null),
        entry("MediaScanner", "termux-media-scan", "media", "mutating", "intent", null),
        entry("MicRecorder", "termux-microphone-record", "audio", "capture", "intent",
            null, "android.permission.RECORD_AUDIO"),
        entry("Nfc", "termux-nfc", "nfc", "user_interactive", "intent",
            null, "android.permission.NFC"),
        entry("NotificationList", "termux-notification-list", "notifications", "sensitive_read", "intent",
            null, "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"),
        entry("Notification", "termux-notification", "notifications", "mutating", "intent", null),
        entry("NotificationChannel", "termux-notification-channel", "notifications", "mutating", "intent", null),
        entry("NotificationRemove", "termux-notification-remove", "notifications", "mutating", "intent", null),
        entry("NotificationReply", "termux-notification-reply", "notifications", "sensitive_write", "intent", null),
        entry("SAF", "termux-saf-*", "storage", "sensitive_read_write", "intent",
            null, "user_selected_tree"),
        entry("Sensor", "termux-sensor", "sensors", "read", "http", "/sensors",
            "android.permission.BODY_SENSORS"),
        entry("Share", "termux-share", "sharing", "user_interactive", "intent", null),
        entry("SmsInbox", "termux-sms-inbox", "sms", "sensitive_read", "intent",
            null, "android.permission.READ_SMS", "android.permission.READ_CONTACTS"),
        entry("SmsSend", "termux-sms-send", "sms", "sensitive_write", "intent",
            null, "android.permission.READ_PHONE_STATE", "android.permission.SEND_SMS"),
        entry("StorageGet", "termux-storage-get", "storage", "user_interactive", "intent",
            null, "user_file_picker"),
        entry("SpeechToText", "termux-speech-to-text", "audio", "capture", "intent",
            null, "android.permission.RECORD_AUDIO"),
        entry("TelephonyCall", "termux-telephony-call", "phone", "sensitive_write", "intent",
            null, "android.permission.CALL_PHONE"),
        entry("TelephonyCellInfo", "termux-telephony-cellinfo", "phone", "sensitive_read", "intent",
            null, "android.permission.ACCESS_COARSE_LOCATION"),
        entry("TelephonyDeviceInfo", "termux-telephony-deviceinfo", "phone", "sensitive_read", "intent",
            null, "android.permission.READ_PHONE_STATE"),
        entry("TextToSpeech", "termux-tts-speak/engines", "audio", "mutating", "intent", null),
        entry("Toast", "termux-toast", "ui", "mutating", "intent", null),
        entry("Torch", "termux-torch", "camera", "mutating", "intent", null),
        entry("Usb", "termux-usb", "usb", "user_interactive", "intent", null),
        entry("Vibrate", "termux-vibrate", "haptics", "mutating", "intent",
            null, "android.permission.VIBRATE"),
        entry("Volume", "termux-volume", "audio", "mutating", "intent", null),
        entry("Wallpaper", "termux-wallpaper", "display", "mutating", "intent",
            null, "android.permission.SET_WALLPAPER"),
        entry("WifiConnectionInfo", "termux-wifi-connectioninfo", "wifi", "read", "http", "/wifi"),
        entry("WifiScanInfo", "termux-wifi-scaninfo", "wifi", "sensitive_read", "intent",
            null, "android.permission.ACCESS_FINE_LOCATION"),
        entry("WifiEnable", "termux-wifi-enable", "wifi", "mutating", "intent",
            null, "android.permission.CHANGE_WIFI_STATE")
    ));

    @NonNull
    public static List<Entry> entries() {
        return ENTRIES;
    }

    @NonNull
    public static JSONArray toJsonArray() {
        JSONArray array = new JSONArray();
        for (Entry entry : ENTRIES) {
            array.put(entry.toJson());
        }
        return array;
    }

    @NonNull
    public static String toJsonString() {
        JSONObject root = new JSONObject();
        put(root, "count", ENTRIES.size());
        put(root, "apis", toJsonArray());
        put(root, "policy", "Full Termux:API receiver is embedded. HTTP direct endpoints stay limited to low-risk local read APIs; sensitive or mutating APIs require Android permissions, user interaction, or the Termux:API intent path.");
        return root.toString();
    }

    @NonNull
    public static JSONArray httpEndpointsJsonArray() {
        JSONArray array = new JSONArray();
        for (Entry entry : ENTRIES) {
            if (entry.httpEndpoint != null) {
                JSONObject endpoint = new JSONObject();
                put(endpoint, "path", entry.httpEndpoint);
                put(endpoint, "api_method", entry.apiMethod);
                put(endpoint, "command", entry.command);
                put(endpoint, "risk", entry.risk);
                array.put(endpoint);
            }
        }
        JSONObject catalog = new JSONObject();
        put(catalog, "path", "/termux-api/catalog");
        put(catalog, "api_method", "catalog");
        put(catalog, "command", "termux-api-catalog");
        put(catalog, "risk", "read");
        array.put(catalog);
        return array;
    }

    private static Entry entry(@NonNull String apiMethod, @NonNull String command,
                               @NonNull String category, @NonNull String risk,
                               @NonNull String bridge, @Nullable String httpEndpoint,
                               String... permissions) {
        return new Entry(apiMethod, command, category, risk, bridge, httpEndpoint, permissions);
    }

    private static void put(@NonNull JSONObject object, @NonNull String name, @Nullable Object value) {
        try {
            object.put(name, value);
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to write Termux:API catalog JSON", e);
        }
    }

    public static final class Entry {
        public final String apiMethod;
        public final String command;
        public final String category;
        public final String risk;
        public final String bridge;
        @Nullable
        public final String httpEndpoint;
        public final List<String> permissions;

        private Entry(@NonNull String apiMethod, @NonNull String command,
                      @NonNull String category, @NonNull String risk,
                      @NonNull String bridge, @Nullable String httpEndpoint,
                      @NonNull String[] permissions) {
            this.apiMethod = apiMethod;
            this.command = command;
            this.category = category;
            this.risk = risk;
            this.bridge = bridge;
            this.httpEndpoint = httpEndpoint;
            this.permissions = Collections.unmodifiableList(Arrays.asList(permissions));
        }

        @NonNull
        public JSONObject toJson() {
            JSONObject object = new JSONObject();
            put(object, "api_method", apiMethod);
            put(object, "command", command);
            put(object, "category", category);
            put(object, "risk", risk);
            put(object, "bridge", bridge);
            if (httpEndpoint != null) put(object, "http_endpoint", httpEndpoint);
            put(object, "permissions", new JSONArray(permissions));
            return object;
        }
    }
}
