package com.jazzcash.notifier;

import android.content.Context;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

public class VoiceEngine {
    private static final String TAG = "VoiceEngine";
    private static VoiceEngine instance;
    private TextToSpeech tts;
    private boolean isReady = false;
    private final Context context;

    public static synchronized VoiceEngine getInstance(Context context) {
        if (instance == null) {
            instance = new VoiceEngine(context.getApplicationContext());
        }
        return instance;
    }

    private VoiceEngine(Context context) {
        this.context = context;
        initTts();
    }

    private void initTts() {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                isReady = true;
                // Urdu or Hindi accent sounds most natural in Pakistan
                int res = tts.setLanguage(new Locale("hi", "IN"));
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US);
                }
                tts.setSpeechRate(0.95f);
            }
        });
    }

    public boolean isSoundEnabled() {
        SharedPreferences prefs = context.getSharedPreferences("RizzleShopPrefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("sound_enabled", true);
    }

    public void setSoundEnabled(boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences("RizzleShopPrefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("sound_enabled", enabled).apply();
    }

    public void speakPayment(String channel, double amount) {
        if (!isSoundEnabled()) return;

        final String text;
        if ("easypaisa".equalsIgnoreCase(channel)) {
            text = "Easypaisa par " + (long)amount + " rupay receive ho gaye hain";
        } else {
            text = "JazzCash par " + (long)amount + " rupay receive ho gaye hain";
        }

        speak(text);
    }

    public void speak(String text) {
        if (tts != null && isReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PAYMENT_ANNOUNCE");
        } else {
            initTts();
        }
    }
}
