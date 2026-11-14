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

        // 1. Skip all SMS apps because SmsReceiver handles real SMS directly from telephony provider
        if (pkg.contains("messaging") || pkg.contains("mms") || pkg.contains("telephony")) {
            return;
        }

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
            lowerContent.contains("password")) {
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
        if (pkg.contains("mobilink") || pkg.contains("jazzcash") || title.toLowerCase().contains("jazzcash") || lowerContent.contains("jazzcash")) {
            senderContext = "8558";
        } else if (pkg.contains("telenor") || pkg.contains("easypaisa") || title.toLowerCase().contains("easypaisa") || lowerContent.contains("easypaisa")) {
            senderContext = "3737";
        } else if (pkg.contains("meezan") || lowerContent.contains("meezan")) {
            senderContext = "meezan";
        } else if (pkg.contains("alfalah") || lowerContent.contains("alfalah")) {
            senderContext = "alfalah";
        } else if (pkg.contains("hbl") || lowerContent.contains("hbl")) {
            senderContext = "hbl";
        } else if (pkg.contains("ubl") || lowerContent.contains("ubl")) {
            senderContext = "ubl";
        } else if (pkg.contains("mcb") || lowerContent.contains("mcb")) {
            senderContext = "mcb";
        } else if (pkg.contains("allied") || pkg.contains("abl") || lowerContent.contains("allied bank")) {
            senderContext = "allied";
        } else if (pkg.contains("faysal") || lowerContent.contains("faysal")) {
            senderContext = "faysal";
        } else if (pkg.contains("askari") || lowerContent.contains("askari")) {
            senderContext = "askari";
        } else if (pkg.contains("sadapay") || lowerContent.contains("sadapay")) {
            senderContext = "sadapay";
        } else if (pkg.contains("nayapay") || lowerContent.contains("nayapay")) {
            senderContext = "nayapay";
        } else {
            senderContext = "bank";
        }

        Log.d(TAG, "Valid financial (" + txType + ") notification: " + fullContent);
        PaymentProcessor.processIncomingText(this, senderContext, fullContent);
    }
}
