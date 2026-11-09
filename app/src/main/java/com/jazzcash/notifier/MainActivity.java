package com.jazzcash.notifier;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int SMS_PERMISSION_REQ_CODE = 202;

    private TextView tvLicenseBadge;
    private Button btnSoundToggle;
    private LinearLayout llNotifBanner;
    private Button btnEnableNotif;

    private TextView tvPeriodTag;
    private TextView tvNetBalance;
    private TextView tvInflowTotal;
    private TextView tvInflowCount;
    private TextView tvOutflowTotal;
    private TextView tvOutflowCount;
    private Button btnShareSummary;

    private Button btnCashIn;
    private Button btnCashOut;

    private Spinner spDateFilter;
    private TextView tvDateRangeLabel;
    private LinearLayout llEmptyState;
    private LinearLayout llTransactionsList;

    private int selectedFilterIndex = 0; // 0=Today, 1=Yesterday, 2=This Month, 3=All Time
    private final DecimalFormat currencyFmt = new DecimalFormat("#,##0.00");
    private final DecimalFormat intCurrencyFmt = new DecimalFormat("#,##0");

    private BroadcastReceiver refreshReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Verify License Security
        if (!LicenseManager.isLicenseActive(this)) {
            redirectToActivation();
            return;
        }

        setContentView(R.layout.activity_main);

        initViews();
        setupFilterSpinner();
        setupClickListeners();
        checkPermissions();

        // Anti-cheating blacklist background sync
        LicenseManager.checkBlacklistAsync(this);
    }

    private void initViews() {
        tvLicenseBadge = findViewById(R.id.tvLicenseBadge);
        btnSoundToggle = findViewById(R.id.btnSoundToggle);
        llNotifBanner = findViewById(R.id.llNotifBanner);
        btnEnableNotif = findViewById(R.id.btnEnableNotif);

        tvPeriodTag = findViewById(R.id.tvPeriodTag);
        tvNetBalance = findViewById(R.id.tvNetBalance);
        tvInflowTotal = findViewById(R.id.tvInflowTotal);
        tvInflowCount = findViewById(R.id.tvInflowCount);
        tvOutflowTotal = findViewById(R.id.tvOutflowTotal);
        tvOutflowCount = findViewById(R.id.tvOutflowCount);
        btnShareSummary = findViewById(R.id.btnShareSummary);

        btnCashIn = findViewById(R.id.btnCashIn);
        btnCashOut = findViewById(R.id.btnCashOut);

        spDateFilter = findViewById(R.id.spDateFilter);
        tvDateRangeLabel = findViewById(R.id.tvDateRangeLabel);
        llEmptyState = findViewById(R.id.llEmptyState);
        llTransactionsList = findViewById(R.id.llTransactionsList);

        updateSoundToggleUI();
        updateLicenseBadge();
    }

    private void updateLicenseBadge() {
        long days = LicenseManager.getRemainingDays(this);
        tvLicenseBadge.setText("🟢 " + days + "d Active");
    }

    private void updateSoundToggleUI() {
        boolean soundOn = VoiceEngine.getInstance(this).isSoundEnabled();
        if (soundOn) {
            btnSoundToggle.setText("🔊 Voice ON");
            btnSoundToggle.setTextColor(Color.parseColor("#10B981"));
        } else {
            btnSoundToggle.setText("🔇 Voice OFF");
            btnSoundToggle.setTextColor(Color.parseColor("#94A3B8"));
        }
    }

    private void setupClickListeners() {
        btnSoundToggle.setOnClickListener(v -> {
            boolean current = VoiceEngine.getInstance(this).isSoundEnabled();
            VoiceEngine.getInstance(this).setSoundEnabled(!current);
            updateSoundToggleUI();
            Toast.makeText(this, !current ? "Soundbox Voice Enabled" : "Soundbox Voice Muted", Toast.LENGTH_SHORT).show();
        });

        btnEnableNotif.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            Toast.makeText(this, "Enable 'Rizzle Merchant' in Notification Access", Toast.LENGTH_LONG).show();
        });

        btnCashOut.setOnClickListener(v -> showCashOutDialog());
        btnCashIn.setOnClickListener(v -> showCashInDialog());

        btnShareSummary.setOnClickListener(v -> shareReportViaWhatsApp());
    }

    private void setupFilterSpinner() {
        String[] options = {"Today", "Yesterday", "This Month", "All Time"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options);
        spDateFilter.setAdapter(adapter);

        spDateFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedFilterIndex = position;
                loadKhataData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!LicenseManager.isLicenseActive(this)) {
            redirectToActivation();
            return;
        }

        checkNotificationListenerPermission();
        loadKhataData();

        // Register Live Refresh Receiver
        refreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                runOnUiThread(() -> loadKhataData());
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, new IntentFilter("com.rizzleshop.ACTION_REFRESH_KHATA"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(refreshReceiver, new IntentFilter("com.rizzleshop.ACTION_REFRESH_KHATA"));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (refreshReceiver != null) {
            try {
                unregisterReceiver(refreshReceiver);
            } catch (Exception ignored) {}
        }
    }

    private void checkNotificationListenerPermission() {
        boolean enabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName());
        llNotifBanner.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.READ_SMS
                }, SMS_PERMISSION_REQ_CODE);
            }
        }
    }

    private void loadKhataData() {
        String[] dateRange = computeDateRange(selectedFilterIndex);
        String startDate = dateRange[0];
        String endDate = dateRange[1];
        String label = dateRange[2];

        tvPeriodTag.setText(label.toUpperCase(Locale.ROOT) + " NET BALANCE");
        tvDateRangeLabel.setText(label);

        DatabaseHelper db = DatabaseHelper.getInstance(this);

        // 1. Fetch Stats
        DatabaseHelper.KhataStats stats = db.getStats(startDate, endDate);
        tvNetBalance.setText("Rs. " + currencyFmt.format(stats.netBalance));
        if (stats.netBalance >= 0) {
            tvNetBalance.setTextColor(Color.WHITE);
        } else {
            tvNetBalance.setTextColor(Color.parseColor("#F43F5E"));
        }

        tvInflowTotal.setText("Rs. " + intCurrencyFmt.format(stats.totalInflow));
        tvInflowCount.setText(stats.inflowCount + " payments");

        tvOutflowTotal.setText("Rs. " + intCurrencyFmt.format(stats.totalOutflow));
        tvOutflowCount.setText(stats.outflowCount + " transfers");

        // 2. Fetch Transactions
        List<DatabaseHelper.TransactionItem> transactions = db.getTransactions(startDate, endDate, "all");
        llTransactionsList.removeAllViews();

        if (transactions.isEmpty()) {
            llEmptyState.setVisibility(View.VISIBLE);
        } else {
            llEmptyState.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);

            for (DatabaseHelper.TransactionItem tx : transactions) {
                View itemView = inflater.inflate(R.layout.item_transaction, llTransactionsList, false);

                TextView tvTxChannel = itemView.findViewById(R.id.tvTxChannel);
                TextView tvTxAmount = itemView.findViewById(R.id.tvTxAmount);
                TextView tvTxParty = itemView.findViewById(R.id.tvTxParty);
                TextView tvTxPurpose = itemView.findViewById(R.id.tvTxPurpose);
                TextView tvTxId = itemView.findViewById(R.id.tvTxId);
                TextView tvTxTime = itemView.findViewById(R.id.tvTxTime);

                // Channel Badge Styling
                String channel = tx.channel != null ? tx.channel.toLowerCase() : "manual";
                if (channel.contains("jazzcash")) {
                    tvTxChannel.setText("JAZZCASH");
                    tvTxChannel.setBackgroundColor(Color.parseColor("#ED1C24"));
                } else if (channel.contains("easypaisa")) {
                    tvTxChannel.setText("EASYPAISA");
                    tvTxChannel.setBackgroundColor(Color.parseColor("#00A651"));
                } else {
                    tvTxChannel.setText("MANUAL CASH");
                    tvTxChannel.setBackgroundColor(Color.parseColor("#2563EB"));
                }

                // Amount & Inflow/Outflow styling
                boolean isInflow = "inflow".equalsIgnoreCase(tx.type);
                if (isInflow) {
                    tvTxAmount.setText("+ Rs. " + currencyFmt.format(tx.amount));
                    tvTxAmount.setTextColor(Color.parseColor("#10B981"));
                    tvTxParty.setText("From: " + (tx.sender != null && !tx.sender.isEmpty() ? tx.sender : "Customer"));
                } else {
                    tvTxAmount.setText("- Rs. " + currencyFmt.format(tx.amount));
                    tvTxAmount.setTextColor(Color.parseColor("#F43F5E"));
                    tvTxParty.setText("Sent To: " + (tx.recipient != null && !tx.recipient.isEmpty() ? tx.recipient : "Vendor"));
                }

                tvTxPurpose.setText(tx.purpose != null && !tx.purpose.isEmpty() ? tx.purpose : "General");
                tvTxId.setText("TID: " + (tx.trxId != null ? tx.trxId : "N/A"));
                tvTxTime.setText(tx.createdAt != null ? tx.createdAt : "");

                llTransactionsList.addView(itemView);
            }
        }
    }

    private String[] computeDateRange(int filterIndex) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        String start;
        String end;
        String label;

        switch (filterIndex) {
            case 1: // Yesterday
                cal.add(Calendar.DAY_OF_YEAR, -1);
                String yest = sdf.format(cal.getTime());
                start = yest + " 00:00:00";
                end = yest + " 23:59:59";
                label = "Yesterday (" + yest + ")";
                break;

            case 2: // This Month
                cal.set(Calendar.DAY_OF_MONTH, 1);
                String monthStart = sdf.format(cal.getTime());
                cal = Calendar.getInstance();
                String todayEnd = sdf.format(cal.getTime());
                start = monthStart + " 00:00:00";
                end = todayEnd + " 23:59:59";
                label = "This Month (" + new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date()) + ")";
                break;

            case 3: // All Time
                start = "2020-01-01 00:00:00";
                end = "2099-12-31 23:59:59";
                label = "All Time History";
                break;

            case 0: // Today
            default:
                String today = sdf.format(cal.getTime());
                start = today + " 00:00:00";
                end = today + " 23:59:59";
                label = "Today (" + today + ")";
                break;
        }

        return new String[]{start, end, label};
    }

    // CASH OUT / WITHDRAWAL MODAL
    private void showCashOutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cash_out, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        EditText etAmount = dialogView.findViewById(R.id.etOutAmount);
        EditText etSender = dialogView.findViewById(R.id.etOutSender);
        EditText etRecipient = dialogView.findViewById(R.id.etOutRecipient);
        EditText etPurpose = dialogView.findViewById(R.id.etOutPurpose);
        Button btnCancel = dialogView.findViewById(R.id.btnOutCancel);
        Button btnConfirm = dialogView.findViewById(R.id.btnOutConfirm);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString().trim();
            String sender = etSender.getText().toString().trim();
            String recipient = etRecipient.getText().toString().trim();
            String purpose = etPurpose.getText().toString().trim();

            if (amountStr.isEmpty()) {
                etAmount.setError("Enter amount");
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
                if (amount <= 0) throw new NumberFormatException();
            } catch (Exception e) {
                etAmount.setError("Invalid amount");
                return;
            }

            if (recipient.isEmpty()) {
                etRecipient.setError("Enter recipient name / detail");
                return;
            }

            if (sender.isEmpty()) sender = "Shop Owner";
            if (purpose.isEmpty()) purpose = "Cash Out";

            String trxId = "OUT_" + System.currentTimeMillis();
            boolean ok = DatabaseHelper.getInstance(this).insertTransaction(
                    trxId, amount, "outflow", "manual", sender, recipient, purpose
            );

            if (ok) {
                Toast.makeText(this, "Cash Out recorded successfully: Rs. " + amount, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                loadKhataData();
            } else {
                Toast.makeText(this, "Failed to record entry", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    // CASH IN MODAL
    private void showCashInDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cash_in, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        EditText etAmount = dialogView.findViewById(R.id.etInAmount);
        EditText etSender = dialogView.findViewById(R.id.etInSender);
        EditText etPurpose = dialogView.findViewById(R.id.etInPurpose);
        Button btnCancel = dialogView.findViewById(R.id.btnInCancel);
        Button btnConfirm = dialogView.findViewById(R.id.btnInConfirm);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString().trim();
            String sender = etSender.getText().toString().trim();
            String purpose = etPurpose.getText().toString().trim();

            if (amountStr.isEmpty()) {
                etAmount.setError("Enter amount");
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
                if (amount <= 0) throw new NumberFormatException();
            } catch (Exception e) {
                etAmount.setError("Invalid amount");
                return;
            }

            if (sender.isEmpty()) sender = "Counter Sale";
            if (purpose.isEmpty()) purpose = "Direct Payment";

            String trxId = "IN_" + System.currentTimeMillis();
            boolean ok = DatabaseHelper.getInstance(this).insertTransaction(
                    trxId, amount, "inflow", "manual", sender, "Shop", purpose
            );

            if (ok) {
                Toast.makeText(this, "Cash In added successfully: Rs. " + amount, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                loadKhataData();
            } else {
                Toast.makeText(this, "Failed to record entry", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void shareReportViaWhatsApp() {
        String[] dateRange = computeDateRange(selectedFilterIndex);
        DatabaseHelper.KhataStats stats = DatabaseHelper.getInstance(this).getStats(dateRange[0], dateRange[1]);

        String report = "📊 *Rizzle Merchant - Khata Summary*\n"
                + "📅 *Period:* " + dateRange[2] + "\n"
                + "------------------------------------\n"
                + "🟢 *Total Received:* Rs. " + currencyFmt.format(stats.totalInflow) + " (" + stats.inflowCount + " payments)\n"
                + "🔴 *Total Sent/Out:* Rs. " + currencyFmt.format(stats.totalOutflow) + " (" + stats.outflowCount + " transfers)\n"
                + "💰 *NET BALANCE:* Rs. " + currencyFmt.format(stats.netBalance) + "\n"
                + "------------------------------------\n"
                + "⚡ Generated by Rizzle Merchant Soundbox (100% Offline)";

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, report);
        sendIntent.setType("text/plain");
        sendIntent.setPackage("com.whatsapp");

        try {
            startActivity(sendIntent);
        } catch (Exception e) {
            // If direct WhatsApp package not installed or fails, open generic chooser
            Intent chooser = Intent.createChooser(sendIntent, "Share Khata Report via");
            startActivity(chooser);
        }
    }

    private void redirectToActivation() {
        Intent intent = new Intent(this, ActivationActivity.class);
        startActivity(intent);
        finish();
    }
}
