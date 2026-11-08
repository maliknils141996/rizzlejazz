package com.jazzcash.notifier;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentNotificationListener extends NotificationListenerService {
    private static final String TAG = "PaymentNotification";
    private TextToSpeech tts;
    private boolean isTtsReady = false;

    @Override
    public void onCreate() {
        super.onCreate();
        initTts();
    }

    private void initTts() {
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true;
                tts.setLanguage(new Locale("hi", "IN"));
            }
        });
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        CharSequence textChar = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigTextChar = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        String body = "";
        if (bigTextChar != null && bigTextChar.length() > 0) {
            body = bigTextChar.toString();
        } else if (textChar != null) {
            body = textChar.toString();
        }

        String fullContent = (title + " " + body).trim();
        Log.d(TAG, "Notification from: " + sbn.getPackageName() + " | Content: " + fullContent);

        // Check if message is related to JazzCash / 8558 or payments
        String lower = fullContent.toLowerCase();
        boolean isPayment = lower.contains("8558") || 
                            lower.contains("jazzcash") || 
                            lower.contains("received rs") || 
                            lower.contains("till id") ||
                            lower.contains("payment received") ||
                            lower.contains("credited");

        // Ignore sent/debit notifications
        if (lower.contains("sent to") || lower.contains("transferred to") || lower.contains("paid to")) {
            return;
        }

        if (isPayment) {
            processPaymentAlert(title, fullContent);
        }
    }

    private void processPaymentAlert(String sender, String text) {
        // Extract Amount
        String amount = "";
        Pattern pattern = Pattern.compile("(?:Received|received|Payment of)\\s*Rs\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            amount = matcher.group(1).replace(",", "");
        } else {
            Pattern p2 = Pattern.compile("Rs\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher m2 = p2.matcher(text);
            if (m2.find()) {
                amount = m2.group(1).replace(",", "");
            }
        }

        // Voice Alert
        final String speech;
        if (!amount.isEmpty()) {
            speech = "JazzCash par " + amount + " rupay receive ho gaye hain";
        } else {
            speech = "JazzCash par payment receive ho gayi hai";
        }

        speak(speech);

        // Save last log for UI debug
        SharedPreferences prefs = getSharedPreferences("JazzCashPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("last_event", "Payment detected: Rs. " + amount + " | " + speech).apply();

        // Webhook Post
        String webhookUrl = prefs.getString("webhook_url", "");
        if (!webhookUrl.isEmpty()) {
            final String fSender = sender.isEmpty() ? "8558" : sender;
            final String fText = text;
            new Thread(() -> sendHttp(webhookUrl, fSender, fText)).start();
        }
    }

    private void speak(String text) {
        if (tts != null && isTtsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PAY_ALERT");
        } else {
            initTts();
        }
    }

    private void sendHttp(String endpoint, String sender, String message) {
        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "RisingJazzMerchantApp/1.0");
            conn.setInstanceFollowRedirects(true);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            String json = String.format("{\"from\":\"%s\",\"text\":\"%s\"}", 
                escape(sender), escape(message));

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "Webhook response: " + code);
            SharedPreferences prefs = getSharedPreferences("JazzCashPrefs", Context.MODE_PRIVATE);
            prefs.edit().putString("last_webhook_status", "HTTP " + code + " at " + new java.util.Date().toString()).apply();
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Webhook failed: " + e.getMessage());
            SharedPreferences prefs = getSharedPreferences("JazzCashPrefs", Context.MODE_PRIVATE);
            prefs.edit().putString("last_webhook_status", "Error: " + e.getMessage()).apply();
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
