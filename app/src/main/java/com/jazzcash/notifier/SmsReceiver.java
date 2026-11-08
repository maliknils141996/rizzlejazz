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
    private static final String TAG = "JazzCashReceiver";
    private static TextToSpeech tts;
    private static boolean isTtsInitialized = false;

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

        String message = fullMsg.toString();
        Log.d(TAG, "Incoming SMS from: " + sender + " | Text: " + message);

        // Filter: Check if SMS is from JazzCash / 8558 or contains payment keywords
        boolean isJazzCash = sender.contains("8558") || 
                             message.toLowerCase().contains("jazzcash") || 
                             message.toLowerCase().contains("received rs") ||
                             message.toLowerCase().contains("till id");

        if (isJazzCash) {
            processPayment(context, sender, message);
        }
    }

    private void processPayment(Context context, String sender, String message) {
        // Ignore debit/transferred out messages
        String lowerMsg = message.toLowerCase();
        if (lowerMsg.contains("transferred to") || lowerMsg.contains("sent to") || lowerMsg.contains("paid to")) {
            return;
        }

        // 1. Extract Amount
        String amount = "";
        Pattern pattern = Pattern.compile("(?:Received|received|Payment of)\\s*Rs\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            amount = matcher.group(1).replace(",", "");
        } else {
            // Fallback pattern: "Rs 500 received"
            Pattern fallback = Pattern.compile("Rs\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:received|credited)", Pattern.CASE_INSENSITIVE);
            Matcher m2 = fallback.matcher(message);
            if (m2.find()) {
                amount = m2.group(1).replace(",", "");
            }
        }

        // 2. Play Voice Announcement (Soundbox Alert)
        final String speechText;
        if (!amount.isEmpty()) {
            speechText = "JazzCash par " + amount + " rupay receive ho gaye hain";
        } else {
            speechText = "JazzCash par payment receive ho gayi hai";
        }

        speakVoice(context, speechText);

        // 3. Post to Webhook on Hostinger
        SharedPreferences prefs = context.getSharedPreferences("JazzCashPrefs", Context.MODE_PRIVATE);
        String webhookUrl = prefs.getString("webhook_url", "");

        if (!webhookUrl.isEmpty()) {
            final String fSender = sender;
            final String fMessage = message;
            new Thread(() -> sendWebhook(webhookUrl, fSender, fMessage)).start();
        }
    }

    private void speakVoice(Context context, String text) {
        if (tts == null) {
            tts = new TextToSpeech(context.getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS) {
                    isTtsInitialized = true;
                    tts.setLanguage(new Locale("hi", "IN"));
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JC_ALERT");
                }
            });
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JC_ALERT");
        }
    }

    private void sendWebhook(String urlStr, String sender, String message) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            String json = String.format("{\"from\":\"%s\",\"text\":\"%s\"}", 
                escape(sender), escape(message));

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "Webhook posted, HTTP response: " + code);
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Webhook POST error: " + e.getMessage());
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
