package com.jazzcash.notifier;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQ_CODE = 101;
    private EditText etWebhookUrl;
    private TextView tvStatus;
    private SharedPreferences prefs;
    private TextToSpeech testTts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("JazzCashPrefs", MODE_PRIVATE);

        etWebhookUrl = findViewById(R.id.etWebhookUrl);
        tvStatus = findViewById(R.id.tvStatus);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnTestVoice = findViewById(R.id.btnTestVoice);

        // Load saved Webhook URL
        String savedUrl = prefs.getString("webhook_url", "https://yourdomain.com/api/webhook.php");
        etWebhookUrl.setText(savedUrl);

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

        // Initialize test TTS
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
            Toast.makeText(this, "Testing Speaker: " + text, Toast.LENGTH_SHORT).show();
        });

        checkPermissions();
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
                tvStatus.setText("Status: Listening for Payments 🟢");
            }
        } else {
            tvStatus.setText("Status: Listening for Payments 🟢");
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
