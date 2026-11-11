package com.jazzcash.notifier;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                    tts.setAudioAttributes(audioAttributes);
                }

                // Urdu or Hindi voice accent sounds most natural in Pakistan
                int res = tts.setLanguage(new Locale("hi", "IN"));
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US);
                }

                // Crisp pitch and steady pacing for maximum loudness and shop clarity
                tts.setPitch(1.08f);
                tts.setSpeechRate(0.92f);
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

    public int getVolumePercent() {
        SharedPreferences prefs = context.getSharedPreferences("RizzleShopPrefs", Context.MODE_PRIVATE);
        return prefs.getInt("voice_volume_percent", 100); // 100% Full Boost by default
    }

    public void setVolumePercent(int percent) {
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;
        SharedPreferences prefs = context.getSharedPreferences("RizzleShopPrefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("voice_volume_percent", percent).apply();
        applyVolume();
    }

    public void applyVolume() {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int percent = getVolumePercent();
                int target = Math.round((percent / 100.0f) * max);
                if (target < 1 && percent > 0) target = 1;
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply volume: " + e.getMessage());
        }
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

    public void speakOutflow(String channel, double amount) {
        if (!isSoundEnabled()) return;

        final String text;
        if ("easypaisa".equalsIgnoreCase(channel)) {
            text = "Easypaisa se " + (long)amount + " rupay send ho gaye hain";
        } else {
            text = "JazzCash se " + (long)amount + " rupay send ho gaye hain";
        }

        speak(text);
    }

    public void speak(String text) {
        if (!isSoundEnabled()) return;

        // Force maximum target volume before speaking
        applyVolume();

        if (tts != null && isReady) {
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f); // 100% max software gain
            params.putString(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_MUSIC));
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "PAYMENT_ANNOUNCE");

            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "PAYMENT_ANNOUNCE");
        } else {
            initTts();
        }
    }
}
