package com.jazzcash.notifier;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
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
import androidx.core.content.ContextCompat;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int SMS_PERMISSION_REQ_CODE = 202;

    private TextView tvLicenseBadge;
    private Button btnSoundToggle;
    private LinearLayout llNotifBanner;
    private TextView tvBannerTitle;
    private TextView tvBannerSubtitle;
    private Button btnEnableNotif;

    // Top Total Hero Card
    private TextView tvPeriodTag;
    private TextView tvNetBalance;
    private TextView tvTotalInflowSummary;
    private TextView tvTotalOutflowSummary;
    private Button btnShareSummary;

    // Dual Accounts (JazzCash & Easypaisa)
    private TextView tvJazzNetBalance;
    private TextView tvJazzInflow;
    private TextView tvJazzOutflow;

    private TextView tvEasyNetBalance;
    private TextView tvEasyInflow;
    private TextView tvEasyOutflow;

    // Filter Buttons
    private Button btnFilterToday;
    private Button btnFilterYesterday;
    private Button btnFilterMonth;
    private Button btnFilterCalendar;
    private TextView tvDateRangeLabel;

    private LinearLayout llEmptyState;
    private LinearLayout llTransactionsList;
    private Spinner spinnerBankFilter;
    private EditText etSearchTransactions;
    private TextView btnClearSearch;

    private String selectedBankFilter = "all";
    private String currentSearchQuery = "";
    private List<DatabaseHelper.TransactionItem> loadedTransactions = new ArrayList<>();

    public static class BankOption {
        public final String key;
        public final String label;

        public BankOption(String key, String label) {
            this.key = key;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private int selectedFilterIndex = 0; // 0=Today, 1=Yesterday, 2=This Month, 3=Custom Calendar Date
    private String customSelectedDate = null; // "yyyy-MM-dd"

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
        tvBannerTitle = findViewById(R.id.tvBannerTitle);
        tvBannerSubtitle = findViewById(R.id.tvBannerSubtitle);
        btnEnableNotif = findViewById(R.id.btnEnableNotif);

        tvPeriodTag = findViewById(R.id.tvPeriodTag);
        tvNetBalance = findViewById(R.id.tvNetBalance);
        tvTotalInflowSummary = findViewById(R.id.tvTotalInflowSummary);
        tvTotalOutflowSummary = findViewById(R.id.tvTotalOutflowSummary);
        btnShareSummary = findViewById(R.id.btnShareSummary);

        tvJazzNetBalance = findViewById(R.id.tvJazzNetBalance);
        tvJazzInflow = findViewById(R.id.tvJazzInflow);
        tvJazzOutflow = findViewById(R.id.tvJazzOutflow);

        tvEasyNetBalance = findViewById(R.id.tvEasyNetBalance);
        tvEasyInflow = findViewById(R.id.tvEasyInflow);
        tvEasyOutflow = findViewById(R.id.tvEasyOutflow);

        btnFilterToday = findViewById(R.id.btnFilterToday);
        btnFilterYesterday = findViewById(R.id.btnFilterYesterday);
        btnFilterMonth = findViewById(R.id.btnFilterMonth);
        btnFilterCalendar = findViewById(R.id.btnFilterCalendar);
        tvDateRangeLabel = findViewById(R.id.tvDateRangeLabel);

        llEmptyState = findViewById(R.id.llEmptyState);
        llTransactionsList = findViewById(R.id.llTransactionsList);

        // Bank Dropdown Selector
        spinnerBankFilter = findViewById(R.id.spinnerBankFilter);
        setupBankFilterSpinner();

        // Search Controls
        etSearchTransactions = findViewById(R.id.etSearchTransactions);
        btnClearSearch = findViewById(R.id.btnClearSearch);

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
            btnSoundToggle.setTextColor(Color.parseColor("#6D28D9"));
        } else {
            btnSoundToggle.setText("🔇 Voice OFF");
            btnSoundToggle.setTextColor(Color.parseColor("#94A3B8"));
        }
    }

    private void setupBankFilterSpinner() {
        if (spinnerBankFilter == null) return;

        List<BankOption> options = new ArrayList<>();
        options.add(new BankOption("all", "⭐ تمام اکاؤنٹس اور بینکس (All Banks & Wallets)"));
        options.add(new BankOption("jazzcash", "🔴 JazzCash (جاز کیش)"));
        options.add(new BankOption("easypaisa", "🟢 Easypaisa (ایزی پیسہ)"));
        options.add(new BankOption("meezan", "🔵 Meezan Bank (میزان بینک)"));
        options.add(new BankOption("alfalah", "🔵 Bank Alfalah (بینک الفلاح)"));
        options.add(new BankOption("hbl", "🟢 HBL (حبیب بینک)"));
        options.add(new BankOption("ubl", "🔵 UBL (یو بی ایل)"));
        options.add(new BankOption("mcb", "🟠 MCB Bank (ایم سی بی)"));
        options.add(new BankOption("allied", "🔵 Allied Bank ABL (الائیڈ بینک)"));
        options.add(new BankOption("faysal", "🟣 Faysal Bank (فیصل بینک)"));
        options.add(new BankOption("askari", "🟢 Askari Bank (عسکری بینک)"));
        options.add(new BankOption("bop", "🔵 Bank of Punjab BOP (بینک آف پنجاب)"));
        options.add(new BankOption("jsbank", "🔵 JS Bank (جے ایس بینک)"));
        options.add(new BankOption("soneri", "🔵 Soneri Bank (سونیری بینک)"));
        options.add(new BankOption("sadapay", "🟢 SadaPay (سادہ پے)"));
        options.add(new BankOption("nayapay", "🟠 NayaPay (نیا پے)"));
        options.add(new BankOption("bankislami", "🟢 BankIslami (بینک اسلامی)"));
        options.add(new BankOption("albaraka", "🔵 Al Baraka Bank (البرکہ بینک)"));
        options.add(new BankOption("dib", "🟢 Dubai Islamic Bank (دبئی اسلامک)"));
        options.add(new BankOption("scb", "🔵 Standard Chartered (اسٹینڈرڈ چارٹرڈ)"));

        ArrayAdapter<BankOption> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options);
        spinnerBankFilter.setAdapter(adapter);

        spinnerBankFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                BankOption opt = options.get(position);
                selectedBankFilter = opt.key;
                loadKhataData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupClickListeners() {
        btnSoundToggle.setOnClickListener(v -> {
            boolean current = VoiceEngine.getInstance(this).isSoundEnabled();
            VoiceEngine.getInstance(this).setSoundEnabled(!current);
            updateSoundToggleUI();
            Toast.makeText(this, !current ? "Soundbox Voice Enabled" : "Soundbox Voice Muted", Toast.LENGTH_SHORT).show();
        });

        // Live Transaction Search Listener
        if (etSearchTransactions != null) {
            etSearchTransactions.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentSearchQuery = s.toString();
                    if (btnClearSearch != null) {
                        btnClearSearch.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    renderTransactionsList();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                if (etSearchTransactions != null) {
                    etSearchTransactions.setText("");
                }
            });
        }

        btnEnableNotif.setOnClickListener(v -> handleAllowAllPermissions());

        btnShareSummary.setOnClickListener(v -> shareReportViaWhatsApp());
    }

    private void setupFilterTabs() {
        btnFilterToday.setOnClickListener(v -> selectFilterTab(0));
        btnFilterYesterday.setOnClickListener(v -> selectFilterTab(1));
        btnFilterMonth.setOnClickListener(v -> selectFilterTab(2));
        btnFilterCalendar.setOnClickListener(v -> showDatePickerDialog());

        updateTabButtonStyles();
    }

    private void selectFilterTab(int index) {
        selectedFilterIndex = index;
        if (index != 3) {
            btnFilterCalendar.setText("📅 Custom");
        }
        updateTabButtonStyles();
        loadKhataData();
    }

    private void showDatePickerDialog() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            SimpleDateFormat keyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat displayFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

            customSelectedDate = keyFmt.format(cal.getTime());
            String displayStr = displayFmt.format(cal.getTime());

            btnFilterCalendar.setText("📅 " + displayStr);
            selectedFilterIndex = 3;
            updateTabButtonStyles();
            loadKhataData();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

        dialog.show();
    }

    private void updateTabButtonStyles() {
        Button[] tabs = {btnFilterToday, btnFilterYesterday, btnFilterMonth, btnFilterCalendar};
        for (int i = 0; i < tabs.length; i++) {
            if (i == selectedFilterIndex) {
                tabs[i].setBackground(ContextCompat.getDrawable(this, R.drawable.tab_selected));
                tabs[i].setTextColor(Color.WHITE);
                tabs[i].setTypeface(null, Typeface.BOLD);
            } else {
                tabs[i].setBackgroundColor(Color.TRANSPARENT);
                tabs[i].setTextColor(Color.parseColor("#64748B"));
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

        updatePermissionBanner();
        loadKhataData();

        // Ensure Notification Listener is actively connected
        boolean notifAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && notifAccess) {
            try {
                PaymentNotificationListener.requestRebind(new ComponentName(this, PaymentNotificationListener.class));
            } catch (Exception ignored) {}
        }

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

    private void updatePermissionBanner() {
        boolean runtimePerms = PermissionHelper.hasAllRequiredPermissions(this);
        boolean notifAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName());

        if (!runtimePerms || !notifAccess) {
            llNotifBanner.setVisibility(View.VISIBLE);
            if (tvBannerTitle != null) tvBannerTitle.setText("⚠️ تمام پرمیشنز آن کریں (Permissions Required)");
            if (tvBannerSubtitle != null) {
                if (!runtimePerms && !notifAccess) {
                    tvBannerSubtitle.setText("ایس ایم ایس اور نوٹیفکیشن ایکسیس الاؤ کرنے کے لیے نیچے بٹن دبائیں۔");
                } else if (!runtimePerms) {
                    tvBannerSubtitle.setText("ادائیگی کی آواز سننے کے لیے ایس ایم ایس پرمیشن الاؤ کریں۔");
                } else {
                    tvBannerSubtitle.setText("جاز کیش اور ایزی پیسہ ایپ کے نوٹیفکیشن سننے کے لیے نوٹیفکیشن ایکسیس آن کریں۔");
                }
            }
            if (btnEnableNotif != null) {
                btnEnableNotif.setText("⚡ تمام پرمیشنز الاؤ کریں (Allow All Permissions)");
            }
        } else {
            llNotifBanner.setVisibility(View.GONE);
        }
    }

    private void handleAllowAllPermissions() {
        boolean hasSms = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasSms = (checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                      checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED);
        }
        boolean hasNotifAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName());

        if (!hasSms) {
            // Check if permanently denied, if so open app details
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS) &&
                getSharedPreferences("PayVoxPerms", MODE_PRIVATE).getBoolean("sms_asked", false)) {
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    Toast.makeText(this, "Permissions me ja kar SMS ko Allow karein", Toast.LENGTH_LONG).show();
                    return;
                } catch (Exception ignored) {}
            }
            getSharedPreferences("PayVoxPerms", MODE_PRIVATE).edit().putBoolean("sms_asked", true).apply();
            PermissionHelper.requestAppPermissions(this);
        } else if (!hasNotifAccess) {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                Toast.makeText(this, "PayVox ko dhoondh kar ON karein", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        }
    }

    private void checkPermissions() {
        if (!PermissionHelper.hasAllRequiredPermissions(this)) {
            PermissionHelper.requestAppPermissions(this);
        }
        updatePermissionBanner();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        // If notification access is still missing, immediately open notification settings
        boolean hasNotifAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName());
        if (!hasNotifAccess) {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                Toast.makeText(this, "PayVox ko dhoondh kar ON karein", Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
        }

        updatePermissionBanner();
    }

    public static class ChannelMeta {
        public final String displayName;
        public final int color;

        public ChannelMeta(String displayName, String hexColor) {
            this.displayName = displayName;
            this.color = Color.parseColor(hexColor);
        }
    }

    public static ChannelMeta getChannelMeta(String rawChannel) {
        if (rawChannel == null) return new ChannelMeta("BANK", "#1E293B");
        String ch = rawChannel.toLowerCase().trim();
        if (ch.contains("jazzcash")) return new ChannelMeta("JAZZCASH", "#ED1C24");
        if (ch.contains("easypaisa")) return new ChannelMeta("EASYPAISA", "#00A651");
        if (ch.contains("meezan")) return new ChannelMeta("MEEZAN BANK", "#1A365D");
        if (ch.contains("alfalah")) return new ChannelMeta("BANK ALFALAH", "#002D62");
        if (ch.contains("hbl")) return new ChannelMeta("HBL", "#00593B");
        if (ch.contains("ubl")) return new ChannelMeta("UBL", "#0072CE");
        if (ch.contains("mcb")) return new ChannelMeta("MCB BANK", "#EA580C");
        if (ch.contains("allied") || ch.contains("abl")) return new ChannelMeta("ALLIED BANK", "#0284C7");
        if (ch.contains("faysal")) return new ChannelMeta("FAYSAL BANK", "#7E22CE");
        if (ch.contains("askari")) return new ChannelMeta("ASKARI BANK", "#15803D");
        if (ch.contains("bop") || ch.contains("punjab")) return new ChannelMeta("BANK OF PUNJAB", "#0369A1");
        if (ch.contains("jsbank") || ch.contains("js")) return new ChannelMeta("JS BANK", "#2563EB");
        if (ch.contains("soneri")) return new ChannelMeta("SONERI BANK", "#0284C7");
        if (ch.contains("sadapay")) return new ChannelMeta("SADAPAY", "#0D9488");
        if (ch.contains("nayapay")) return new ChannelMeta("NAYAPAY", "#F97316");
        if (ch.contains("bankislami")) return new ChannelMeta("BANKISLAMI", "#16A34A");
        if (ch.contains("albaraka")) return new ChannelMeta("AL BARAKA", "#1E3A8A");
        if (ch.contains("dib")) return new ChannelMeta("DIB", "#059669");
        if (ch.contains("scb")) return new ChannelMeta("STANDARD CHARTERED", "#0369A1");
        return new ChannelMeta(rawChannel.toUpperCase(), "#334155");
    }

    private void loadKhataData() {
        String[] dateRange = computeDateRange(selectedFilterIndex);
        String startDate = dateRange[0];
        String endDate = dateRange[1];
        String label = dateRange[2];

        String periodBadge = "TODAY";
        if (selectedFilterIndex == 1) periodBadge = "YESTERDAY";
        else if (selectedFilterIndex == 2) periodBadge = "THIS MONTH";
        else if (selectedFilterIndex == 3) periodBadge = (customSelectedDate != null ? customSelectedDate : "CUSTOM DATE");

        if (!"all".equalsIgnoreCase(selectedBankFilter)) {
            periodBadge += " • " + VoiceEngine.getChannelVoiceName(selectedBankFilter).toUpperCase();
        }

        tvPeriodTag.setText(periodBadge);
        tvDateRangeLabel.setText(label);

        DatabaseHelper db = DatabaseHelper.getInstance(this);

        // 1. Overall Stats for Selected Bank (or Combined)
        DatabaseHelper.KhataStats totalStats = db.getStats(startDate, endDate, selectedBankFilter);
        tvNetBalance.setText("Rs. " + currencyFmt.format(totalStats.netBalance));
        tvTotalInflowSummary.setText("↓ In: Rs. " + intCurrencyFmt.format(totalStats.totalInflow) + " (" + totalStats.inflowCount + ")");
        tvTotalOutflowSummary.setText("↑ Out: Rs. " + intCurrencyFmt.format(totalStats.totalOutflow) + " (" + totalStats.outflowCount + ")");

        // 2. JazzCash Stats (8558)
        DatabaseHelper.KhataStats jazzStats = db.getStats(startDate, endDate, "jazzcash");
        tvJazzNetBalance.setText("Rs. " + currencyFmt.format(jazzStats.netBalance));
        tvJazzInflow.setText("↓ In: Rs. " + intCurrencyFmt.format(jazzStats.totalInflow));
        tvJazzOutflow.setText("↑ Out: Rs. " + intCurrencyFmt.format(jazzStats.totalOutflow));

        // 3. Easypaisa Stats (3737)
        DatabaseHelper.KhataStats easyStats = db.getStats(startDate, endDate, "easypaisa");
        tvEasyNetBalance.setText("Rs. " + currencyFmt.format(easyStats.netBalance));
        tvEasyInflow.setText("↓ In: Rs. " + intCurrencyFmt.format(easyStats.totalInflow));
        tvEasyOutflow.setText("↑ Out: Rs. " + intCurrencyFmt.format(easyStats.totalOutflow));

        // 4. Live Transactions Feed for Selected Bank (or all)
        loadedTransactions = db.getTransactions(startDate, endDate, selectedBankFilter);
        renderTransactionsList();
    }

    private void renderTransactionsList() {
        if (llTransactionsList == null) return;
        llTransactionsList.removeAllViews();

        List<DatabaseHelper.TransactionItem> filtered = new ArrayList<>();
        String query = currentSearchQuery.trim().toLowerCase();

        for (DatabaseHelper.TransactionItem tx : loadedTransactions) {
            if (query.isEmpty()) {
                filtered.add(tx);
            } else {
                String amtStr = String.valueOf((long) tx.amount);
                String amtFmt = currencyFmt.format(tx.amount).toLowerCase();
                String channel = tx.channel != null ? tx.channel.toLowerCase() : "";
                String tid = tx.trxId != null ? tx.trxId.toLowerCase() : "";
                String sender = tx.sender != null ? tx.sender.toLowerCase() : "";
                String recipient = tx.recipient != null ? tx.recipient.toLowerCase() : "";
                String purpose = tx.purpose != null ? tx.purpose.toLowerCase() : "";

                if (amtStr.contains(query) ||
                    amtFmt.contains(query) ||
                    channel.contains(query) ||
                    tid.contains(query) ||
                    sender.contains(query) ||
                    recipient.contains(query) ||
                    purpose.contains(query)) {
                    filtered.add(tx);
                }
            }
        }

        if (filtered.isEmpty()) {
            llEmptyState.setVisibility(View.VISIBLE);
        } else {
            llEmptyState.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);

            for (DatabaseHelper.TransactionItem tx : filtered) {
                View itemView = inflater.inflate(R.layout.item_transaction, llTransactionsList, false);

                TextView tvTxChannel = itemView.findViewById(R.id.tvTxChannel);
                TextView tvTxAmount = itemView.findViewById(R.id.tvTxAmount);
                TextView tvTxParty = itemView.findViewById(R.id.tvTxParty);
                TextView tvTxPurpose = itemView.findViewById(R.id.tvTxPurpose);
                TextView tvTxId = itemView.findViewById(R.id.tvTxId);
                TextView tvTxTime = itemView.findViewById(R.id.tvTxTime);

                // Channel Badge Styling
                ChannelMeta meta = getChannelMeta(tx.channel);
                tvTxChannel.setText(meta.displayName);
                tvTxChannel.setBackgroundColor(meta.color);

                // Amount & Inflow/Outflow styling
                boolean isInflow = "inflow".equalsIgnoreCase(tx.type);
                if (isInflow) {
                    tvTxAmount.setText("+ Rs. " + currencyFmt.format(tx.amount));
                    tvTxAmount.setTextColor(Color.parseColor("#059669"));
                    tvTxParty.setText("From: " + (tx.sender != null && !tx.sender.isEmpty() ? tx.sender : "Customer"));
                } else {
                    tvTxAmount.setText("- Rs. " + currencyFmt.format(tx.amount));
                    tvTxAmount.setTextColor(Color.parseColor("#E11D48"));
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

            case 3: // Custom Date from Calendar
                if (customSelectedDate != null) {
                    start = customSelectedDate + " 00:00:00";
                    end = customSelectedDate + " 23:59:59";
                    label = "Date: " + customSelectedDate;
                } else {
                    String today = sdf.format(cal.getTime());
                    start = today + " 00:00:00";
                    end = today + " 23:59:59";
                    label = "Today (" + today + ")";
                }
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
        DatabaseHelper db = DatabaseHelper.getInstance(this);

        DatabaseHelper.KhataStats currentStats = db.getStats(dateRange[0], dateRange[1], selectedBankFilter);
        String channelTitle = VoiceEngine.getChannelVoiceName(selectedBankFilter);
        if ("all".equalsIgnoreCase(selectedBankFilter)) {
            channelTitle = "All Accounts (تمام اکاؤنٹس اور بینکس)";
        }

        DatabaseHelper.KhataStats jazzStats = db.getStats(dateRange[0], dateRange[1], "jazzcash");
        DatabaseHelper.KhataStats easyStats = db.getStats(dateRange[0], dateRange[1], "easypaisa");

        String report = "📊 *PayVox - Khata Summary Report*\n"
                + "📅 *Period:* " + dateRange[2] + "\n"
                + "🏦 *Account/Bank:* " + channelTitle + "\n"
                + "------------------------------------\n"
                + "⭐ *SELECTED NET BALANCE: Rs. " + currencyFmt.format(currentStats.netBalance) + "*\n"
                + "   ↓ Total Inflow: Rs. " + currencyFmt.format(currentStats.totalInflow) + " (" + currentStats.inflowCount + " txns)\n"
                + "   ↑ Total Outflow: Rs. " + currencyFmt.format(currentStats.totalOutflow) + " (" + currentStats.outflowCount + " txns)\n"
                + "------------------------------------\n"
                + "🔴 *JazzCash:* Rs. " + currencyFmt.format(jazzStats.netBalance) + "\n"
                + "🟢 *Easypaisa:* Rs. " + currencyFmt.format(easyStats.netBalance) + "\n"
                + "------------------------------------\n"
                + "⚡ Generated by PayVox Smart Soundbox & Multi-Bank Khata";

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
