package com.jazzcash.notifier;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

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

    // Filter Pill Tabs
    private Button btnFilterToday;
    private Button btnFilterYesterday;
    private Button btnFilterMonth;
    private Button btnFilterAll;
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

        // Security License Verification
        if (!LicenseManager.isLicenseActive(this)) {
            redirectToActivation();
            return;
        }

        setContentView(R.layout.activity_main);

        initViews();
        setupFilterTabs();
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

        btnFilterToday = findViewById(R.id.btnFilterToday);
        btnFilterYesterday = findViewById(R.id.btnFilterYesterday);
        btnFilterMonth = findViewById(R.id.btnFilterMonth);
        btnFilterAll = findViewById(R.id.btnFilterAll);
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

        btnShareSummary.setOnClickListener(v -> shareReportViaWhatsApp());
    }

    private void setupFilterTabs() {
        btnFilterToday.setOnClickListener(v -> selectFilterTab(0));
        btnFilterYesterday.setOnClickListener(v -> selectFilterTab(1));
        btnFilterMonth.setOnClickListener(v -> selectFilterTab(2));
        btnFilterAll.setOnClickListener(v -> selectFilterTab(3));

        updateTabButtonStyles();
    }

    private void selectFilterTab(int index) {
        selectedFilterIndex = index;
        updateTabButtonStyles();
        loadKhataData();
    }

    private void updateTabButtonStyles() {
        Button[] tabs = {btnFilterToday, btnFilterYesterday, btnFilterMonth, btnFilterAll};
        for (int i = 0; i < tabs.length; i++) {
            if (i == selectedFilterIndex) {
                tabs[i].setBackground(ContextCompat.getDrawable(this, R.drawable.tab_selected));
                tabs[i].setTextColor(Color.WHITE);
                tabs[i].setTypeface(null, Typeface.BOLD);
            } else {
                tabs[i].setBackground(ContextCompat.getDrawable(this, R.drawable.tab_unselected));
                tabs[i].setTextColor(Color.parseColor("#94A3B8"));
                tabs[i].setTypeface(null, Typeface.NORMAL);
            }
        }
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
            tvNetBalance.setTextColor(Color.parseColor("#FDA4AF"));
        }

        tvInflowTotal.setText("Rs. " + intCurrencyFmt.format(stats.totalInflow));
        tvInflowCount.setText(stats.inflowCount + " payments");

        tvOutflowTotal.setText("Rs. " + intCurrencyFmt.format(stats.totalOutflow));
        tvOutflowCount.setText(stats.outflowCount + " transfers");

        // 2. Fetch Transactions Feed
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
                String channel = tx.channel != null ? tx.channel.toLowerCase() : "jazzcash";
                if (channel.contains("easypaisa")) {
                    tvTxChannel.setText("EASYPAISA");
                    tvTxChannel.setBackgroundColor(Color.parseColor("#00A651"));
                } else {
                    tvTxChannel.setText("JAZZCASH");
                    tvTxChannel.setBackgroundColor(Color.parseColor("#ED1C24"));
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
                    tvTxParty.setText("Sent To: " + (tx.recipient != null && !tx.recipient.isEmpty() ? tx.recipient : "Recipient"));
                }

                tvTxPurpose.setText(tx.purpose != null && !tx.purpose.isEmpty() ? tx.purpose : "Payment");
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
