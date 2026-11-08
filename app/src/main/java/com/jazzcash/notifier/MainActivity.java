package com.jazzcash.notifier;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQ_CODE = 101;
    private EditText etWebhookUrl;
    private TextView tvStatus, tvLastEvent;
    private SharedPreferences prefs;
    private TextToSpeech testTts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("JazzCashPrefs", MODE_PRIVATE);

        etWebhookUrl = findViewById(R.id.etWebhookUrl);
        tvStatus = findViewById(R.id.tvStatus);
        tvLastEvent = findViewById(R.id.tvLastEvent);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnTestVoice = findViewById(R.id.btnTestVoice);
        Button btnNotifPermission = findViewById(R.id.btnNotifPermission);

        String savedUrl = prefs.getString("webhook_url", "https://yourdomain.com/api/webhook.php");
        etWebhookUrl.setText(savedUrl);

        updateDebugStatus();

        btnSave.setOnClickListener(v -> {
            String url = etWebhookUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter your Webhook URL", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("webhook_url", url).apply();
            tvStatus.setText("Status: Configured & Active 🟢");
            Toast.makeText(this, "Settings Saved Successfully!", Toast.LENGTH_SHORT).show();
        });

        // Initialize TTS
        testTts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                testTts.setLanguage(new Locale("hi", "IN"));
            }
        });

        btnTestVoice.setOnClickListener(v -> {
            String text = "JazzCash par 1500 rupay receive ho gaye hain";
            if (testTts != null) {
                testTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TEST_ALERT");
            }
            Toast.makeText(this, "Awaaz suniye: " + text, Toast.LENGTH_LONG).show();
        });

        btnNotifPermission.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            Toast.makeText(this, "Rising Jazz Merchant ko ON kar dein", Toast.LENGTH_LONG).show();
        });

        checkPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDebugStatus();
    }

    private void updateDebugStatus() {
        String lastEvent = prefs.getString("last_event", "No payments detected yet.");
        String lastStatus = prefs.getString("last_webhook_status", "Not sent yet");
        tvLastEvent.setText("Last Event: " + lastEvent + "\nWebhook: " + lastStatus);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                }, PERMISSION_REQ_CODE);
            } else {
                tvStatus.setText("Status: Ready & Listening 🟢");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (testTts != null) {
            testTts.stop();
            testTts.shutdown();
        }
    }
}
