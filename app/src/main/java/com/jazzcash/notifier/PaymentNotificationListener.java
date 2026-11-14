package com.jazzcash.notifier;

import android.app.Notification;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class PaymentNotificationListener extends NotificationListenerService {
    private static final String TAG = "PaymentNotification";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "PayVox PaymentNotificationListener CONNECTED successfully!");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "PayVox PaymentNotificationListener DISCONNECTED! Attempting rebind...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(new ComponentName(this, PaymentNotificationListener.class));
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;

        if (!LicenseManager.isLicenseActive(this)) {
            return;
        }

        String pkg = sbn.getPackageName() != null ? sbn.getPackageName().toLowerCase() : "";

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        CharSequence textChar = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigTextChar = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        String body = "";
        if (bigTextChar != null && bigTextChar.length() > 0) {
            body = bigTextChar.toString();
        } else if (textChar != null) {
            body = textChar.toString();
        }

        String fullContent = (title + " " + body).trim();
        String lowerContent = fullContent.toLowerCase();

        // Filter out non-financial system notifications (OTP, login, balance check, etc.)
        if (lowerContent.contains("otp") ||
            lowerContent.contains("login") ||
            lowerContent.contains("verification code") ||
            lowerContent.contains("balance inquiry") ||
            lowerContent.contains("password") ||
            lowerContent.contains("downloading")) {
            return;
        }

        // 2. Identify Financial Transaction using Central Payment Engine
        String txType = PaymentProcessor.detectTransactionType(fullContent);
        if (txType == null) {
            Log.d(TAG, "Ignoring non-financial notification: " + fullContent);
            return;
        }

        // 3. Identify Channel Context (JazzCash, Easypaisa, or Pakistani Banking Apps)
        String senderContext;
        String lowerTitle = title != null ? title.toLowerCase() : "";

        if (lowerTitle.contains("8558") || lowerTitle.contains("jazzcash") || pkg.contains("mobilink") || pkg.contains("jazzcash") || lowerContent.contains("jazzcash")) {
            senderContext = "8558";
        } else if (lowerTitle.contains("3737") || lowerTitle.contains("easypaisa") || pkg.contains("telenor") || pkg.contains("easypaisa") || lowerContent.contains("easypaisa")) {
            senderContext = "3737";
        } else if (lowerTitle.contains("8079") || lowerTitle.contains("8078") || lowerTitle.contains("9060") || lowerTitle.contains("8222") || lowerTitle.contains("meezan") || pkg.contains("meezan") || lowerContent.contains("meezan")) {
            senderContext = "8079";
        } else if (lowerTitle.contains("2252") || lowerTitle.contains("alfalah") || pkg.contains("alfalah") || lowerContent.contains("alfalah")) {
            senderContext = "2252";
        } else if (lowerTitle.contains("4250") || lowerTitle.contains("hbl") || pkg.contains("hbl") || lowerContent.contains("hbl")) {
            senderContext = "4250";
        } else if (lowerTitle.contains("8257") || lowerTitle.contains("ubl") || pkg.contains("ubl") || lowerContent.contains("ubl")) {
            senderContext = "8257";
        } else if (lowerTitle.contains("4722") || lowerTitle.contains("mcb") || pkg.contains("mcb") || lowerContent.contains("mcb")) {
            senderContext = "4722";
        } else if (lowerTitle.contains("nbp") || pkg.contains("nbp") || lowerContent.contains("national bank") || lowerContent.contains("nbp")) {
            senderContext = "nbp";
        } else if (lowerTitle.contains("alhabib") || lowerTitle.contains("bahl") || lowerContent.contains("bank al habib") || lowerContent.contains("alhabib")) {
            senderContext = "alhabib";
        } else if (lowerTitle.contains("allied") || lowerTitle.contains("abl") || pkg.contains("allied") || pkg.contains("abl") || lowerContent.contains("allied bank")) {
            senderContext = "allied";
        } else if (lowerTitle.contains("faysal") || lowerTitle.contains("fbl") || pkg.contains("faysal") || lowerContent.contains("faysal")) {
            senderContext = "faysal";
        } else if (lowerTitle.contains("askari") || lowerTitle.contains("akbl") || pkg.contains("askari") || lowerContent.contains("askari")) {
            senderContext = "askari";
        } else if (lowerTitle.contains("bop") || lowerTitle.contains("punjab") || pkg.contains("bop") || lowerContent.contains("bank of punjab")) {
            senderContext = "bop";
        } else if (lowerTitle.contains("sadapay") || pkg.contains("sadapay") || lowerContent.contains("sadapay")) {
            senderContext = "sadapay";
        } else if (lowerTitle.contains("nayapay") || pkg.contains("nayapay") || lowerContent.contains("nayapay")) {
            senderContext = "nayapay";
        } else {
            // Use title as sender (often holds the shortcode like 8558, 8079, etc. from SMS apps)
            senderContext = (title != null && !title.trim().isEmpty()) ? title.trim() : "bank";
        }

        Log.d(TAG, "Valid financial (" + txType + ") notification from " + senderContext + ": " + fullContent);
        PaymentProcessor.processIncomingText(this, senderContext, fullContent);
    }
}
