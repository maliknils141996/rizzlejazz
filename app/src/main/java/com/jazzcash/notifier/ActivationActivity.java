package com.jazzcash.notifier;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class ActivationActivity extends Activity {

    private TextView tvDeviceId;
    private EditText etLicenseKey;
    private TextView tvStatusMessage;
    private Button btnActivate;
    private Button btnCopyDeviceId;
    private Button btnWhatsAppSupport;
    private String currentDeviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If license already active, jump straight to MainActivity
        if (LicenseManager.isLicenseActive(this)) {
            launchMainActivity();
            return;
        }

        setContentView(R.layout.activity_activation);

        tvDeviceId = findViewById(R.id.tvDeviceId);
        etLicenseKey = findViewById(R.id.etLicenseKey);
        tvStatusMessage = findViewById(R.id.tvStatusMessage);
        btnActivate = findViewById(R.id.btnActivate);
        btnCopyDeviceId = findViewById(R.id.btnCopyDeviceId);
        btnWhatsAppSupport = findViewById(R.id.btnWhatsAppSupport);

        currentDeviceId = LicenseManager.getDeviceId(this);
        tvDeviceId.setText(currentDeviceId);

        btnCopyDeviceId.setOnClickListener(v -> copyDeviceIdToClipboard());

        btnActivate.setOnClickListener(v -> handleActivation());

        btnWhatsAppSupport.setOnClickListener(v -> openWhatsAppForSupport());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (LicenseManager.isLicenseActive(this)) {
            launchMainActivity();
        }
    }

    private void handleActivation() {
        String key = etLicenseKey.getText().toString().trim();
        if (key.isEmpty()) {
            showError("Please enter your license key.");
            return;
        }

        LicenseManager.ValidationResult result = LicenseManager.activateLicense(this, key);
        if (result.success) {
            tvStatusMessage.setTextColor(Color.parseColor("#10B981"));
            tvStatusMessage.setText(result.message);
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();

            // Smooth transition to Main Khata
            new Handler(Looper.getMainLooper()).postDelayed(this::launchMainActivity, 600);
        } else {
            showError(result.message);
        }
    }

    private void showError(String msg) {
        tvStatusMessage.setTextColor(Color.parseColor("#F43F5E"));
        tvStatusMessage.setText("⚠️ " + msg);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void copyDeviceIdToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Device ID", currentDeviceId);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Device ID copied to clipboard: " + currentDeviceId, Toast.LENGTH_SHORT).show();
        }
    }

    private void openWhatsAppForSupport() {
        try {
            String text = "Hello! I want to request a trial/license for PayVox Soundbox.\nMy Device ID: " + currentDeviceId;
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://wa.me/?text=" + encoded));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp could not be opened: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void launchMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
