package com.jazzcash.notifier;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class LicenseManager {
    private static final String TAG = "LicenseManager";
    public static final String SECRET_SALT = "RIZZLE_MERCHANT_SECRET_KEY_2026_PK";
    public static final String BLACKLIST_URL = "https://raw.githubusercontent.com/maliknils141996/rizzlejazz/main/blacklist.json";

    private static final String PREF_NAME = "RizzleLicensePrefs";
    private static final String KEY_LICENSE = "active_license_key";
    private static final String KEY_EXPIRY = "license_expiry_time";
    private static final String KEY_LAST_SEEN = "last_clock_seen";
    private static final String KEY_BLACKLISTED = "is_device_blacklisted";

    // Generate unique Device ID for this phone (e.g. RJ-9A4B-88F1)
    public static String getDeviceId(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.isEmpty()) {
            androidId = Build.SERIAL != null ? Build.SERIAL : "UNKNOWN99";
        }
        String clean = androidId.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (clean.length() < 8) {
            clean = (clean + "00000000").substring(0, 8);
        }
        return "RJ-" + clean.substring(0, 4) + "-" + clean.substring(4, 8);
    }

    public static boolean isLicenseActive(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_BLACKLISTED, false)) {
            return false;
        }

        long expiry = prefs.getLong(KEY_EXPIRY, 0);
        long now = System.currentTimeMillis();

        // Anti Clock Tampering Check
        long lastSeen = prefs.getLong(KEY_LAST_SEEN, 0);
        if (now < (lastSeen - 3600000)) { // Clock set back more than 1 hour
            Log.w(TAG, "Clock tampering detected!");
            return false;
        }
        prefs.edit().putLong(KEY_LAST_SEEN, now).apply();

        return (expiry > now);
    }

    public static long getRemainingDays(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long expiry = prefs.getLong(KEY_EXPIRY, 0);
        long diff = expiry - System.currentTimeMillis();
        if (diff <= 0) return 0;
        return (diff / (1000L * 60 * 60 * 24)) + 1;
    }

    public static ValidationResult activateLicense(Context context, String inputKey) {
        if (inputKey == null) return new ValidationResult(false, "License key cannot be empty.");
        String key = inputKey.trim();

        if (!key.startsWith("RJ-")) {
            return new ValidationResult(false, "Invalid format. Key must start with RJ-");
        }

        String[] parts = key.split("-");
        if (parts.length != 3) {
            return new ValidationResult(false, "Invalid key structure.");
        }

        String b64Payload = parts[1];
        String providedSig = parts[2].toUpperCase(Locale.ROOT);

        try {
            // Restore Base64 padding
            int mod = b64Payload.length() % 4;
            if (mod == 2) b64Payload += "==";
            else if (mod == 3) b64Payload += "=";

            byte[] decodedBytes = Base64.decode(b64Payload, Base64.DEFAULT);
            String payload = new String(decodedBytes, StandardCharsets.UTF_8);

            String[] dataParts = payload.split("\\|");
            if (dataParts.length != 2) {
                return new ValidationResult(false, "Corrupt license data.");
            }

            String keyDeviceId = dataParts[0].trim();
            long expiryTime = Long.parseLong(dataParts[1].trim());

            // 1. Check Device ID match
            String myDeviceId = getDeviceId(context).replace("-", "");
            String cleanKeyDevice = keyDeviceId.replace("-", "");
            if (!myDeviceId.equalsIgnoreCase(cleanKeyDevice)) {
                return new ValidationResult(false, "This license is registered for a different device!");
            }

            // 2. Cryptographic HMAC Signature check
            String expectedSig = computeHmac(payload, SECRET_SALT);
            if (!expectedSig.equalsIgnoreCase(providedSig)) {
                return new ValidationResult(false, "Invalid cryptographic signature! Counterfeit key.");
            }

            // 3. Expiry Check
            if (expiryTime <= System.currentTimeMillis()) {
                return new ValidationResult(false, "This license key has already expired!");
            }

            // Activation Success -> Save to secure prefs
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(KEY_LICENSE, key)
                    .putLong(KEY_EXPIRY, expiryTime)
                    .putLong(KEY_LAST_SEEN, System.currentTimeMillis())
                    .putBoolean(KEY_BLACKLISTED, false)
                    .apply();

            return new ValidationResult(true, "License Activated Successfully! Welcome.");

        } catch (Exception e) {
            Log.e(TAG, "Activation failed: " + e.getMessage());
            return new ValidationResult(false, "Failed to verify key: " + e.getMessage());
        }
    }

    private static String computeHmac(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString().substring(0, 16).toUpperCase(Locale.ROOT);
    }

    // Background Check for Blacklist (Anti-cheating)
    public static void checkBlacklistAsync(Context context) {
        new Thread(() -> {
            try {
                URL url = new URL(BLACKLIST_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder jsonStr = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) jsonStr.append(line);
                    reader.close();

                    JSONObject obj = new JSONObject(jsonStr.toString());
                    JSONArray blocked = obj.optJSONArray("blocked_devices");
                    if (blocked != null) {
                        String myDev = getDeviceId(context).replace("-", "");
                        for (int i = 0; i < blocked.length(); i++) {
                            String bDev = blocked.getString(i).replace("-", "");
                            if (myDev.equalsIgnoreCase(bDev)) {
                                // Blacklist match! Permanently wipe license!
                                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                                prefs.edit()
                                        .putBoolean(KEY_BLACKLISTED, true)
                                        .putLong(KEY_EXPIRY, 0)
                                        .apply();
                                Log.w(TAG, "Device permanently blacklisted!");
                                break;
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                // Offline is fine, fails silently
            }
        }).start();
    }

    public static class ValidationResult {
        public final boolean success;
        public final String message;

        public ValidationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
