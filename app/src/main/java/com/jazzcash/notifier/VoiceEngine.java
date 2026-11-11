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
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                            .build();
                    tts.setAudioAttributes(audioAttributes);
                }

                // Urdu or Hindi voice accent sounds most natural in Pakistan
                int res = tts.setLanguage(new Locale("hi", "IN"));
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US);
                }

                // Higher pitch (1.20f) penetrates ambient noise with louder acoustic perception
                tts.setPitch(1.20f);
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

    /**
     * Boost hardware speaker volume to 100% MAXIMUM before speaking
     */
    public void boostMaxLoudness() {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                // Request temporary audio focus so background apps duck and full amplifier power goes to speech
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

                // Set music/media stream to 100% absolute maximum
                int maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0);

                // Also boost alarm and notification volume to maximum as fallback
                int maxAlarm = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                am.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0);

                int maxNotif = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, maxNotif, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to boost loudness: " + e.getMessage());
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

        // Force maximum hardware speaker boost
        boostMaxLoudness();

        if (tts != null && isReady) {
            tts.setPitch(1.20f);
            tts.setSpeechRate(0.95f);

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
