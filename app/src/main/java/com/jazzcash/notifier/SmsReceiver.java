package com.jazzcash.notifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsMessage;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "JazzCashSmsReceiver";
    private static TextToSpeech tts;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        StringBuilder fullMsg = new StringBuilder();
        String sender = "";

        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
            sender = sms.getDisplayOriginatingAddress();
            fullMsg.append(sms.getMessageBody());
        }

        final String message = fullMsg.toString();
        final String finalSender = sender;
        Log.d(TAG, "SMS received from: " + sender + " | Body: " + message);

        // Keep BroadcastReceiver alive with goAsync()
        final PendingResult pendingResult = goAsync();

        new Thread(() -> {
            try {
                handleSms(context, finalSender, message);
            } finally {
                pendingResult.finish();
            }
        }).start();
    }

    private void handleSms(Context context, String sender, String message) {
        String lowerMsg = message.toLowerCase();

        // Safety: ignore debit/sent messages
        if (lowerMsg.contains("transferred to") || lowerMsg.contains("sent to") || lowerMsg.contains("paid to")) {
            return;
        }

        // Check if from JazzCash or contains payment info
        boolean isJazzCash = sender.contains("8558") || 
                             lowerMsg.contains("jazzcash") || 
                             lowerMsg.contains("received rs") ||
                             lowerMsg.contains("till id") ||
                             lowerMsg.contains("payment of rs");

        if (!isJazzCash) {
            return;
        }

        // Extract Amount
        String amount = "";
        Pattern pattern = Pattern.compile("(?:Received|received|Payment of)\\s*Rs\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            amount = matcher.group(1).replace(",", "");
        } else {
            Pattern p2 = Pattern.compile("Rs\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher m2 = p2.matcher(message);
            if (m2.find()) {
                amount = m2.group(1).replace(",", "");
            }
        }

        // Voice Alert
        final String speechText = !amount.isEmpty() 
            ? "JazzCash par " + amount + " rupay receive ho gaye hain"
            : "JazzCash par payment receive ho gayi hai";

        speakVoice(context, speechText);

        // Send to Webhook
        SharedPreferences prefs = context.getSharedPreferences("JazzCashPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("last_sms", "Rs. " + amount + " from " + sender).apply();

        String webhookUrl = prefs.getString("webhook_url", "");
        if (!webhookUrl.isEmpty()) {
            sendHttp(webhookUrl, sender, message, prefs);
        }
    }

    private void speakVoice(Context context, String text) {
        if (tts == null) {
            tts = new TextToSpeech(context.getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(new Locale("hi", "IN"));
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SMS_ALERT");
                }
            });
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SMS_ALERT");
        }
    }

    private void sendHttp(String urlStr, String sender, String message, SharedPreferences prefs) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "RisingJazzMerchant/1.0");
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
            Log.d(TAG, "Webhook HTTP code: " + code);
            prefs.edit().putString("last_webhook_status", "HTTP " + code + " at " + new java.util.Date().toString()).apply();
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Webhook exception: " + e.getMessage());
            prefs.edit().putString("last_webhook_status", "Error: " + e.getMessage()).apply();
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
