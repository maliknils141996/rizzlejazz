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

        // 3. Identify Channel Context (Sender shortcode / app package / SMS title)
        String senderContext = "";
        String lowerTitle = title != null ? title.toLowerCase() : "";

        // Stage A: Originating Application Package
        if (pkg.contains("mobilink") || pkg.contains("jazzcash")) {
            senderContext = "8558";
        } else if (pkg.contains("telenor") || pkg.contains("easypaisa")) {
            senderContext = "3737";
        } else if (pkg.contains("meezan")) {
            senderContext = "8079";
        } else if (pkg.contains("alfalah")) {
            senderContext = "2252";
        } else if (pkg.contains("hbl")) {
            senderContext = "4250";
        } else if (pkg.contains("ubl")) {
            senderContext = "8257";
        } else if (pkg.contains("mcb")) {
            senderContext = "4722";
        } else if (pkg.contains("nbp")) {
            senderContext = "nbp";
        } else if (pkg.contains("alhabib") || pkg.contains("bahl")) {
            senderContext = "alhabib";
        } else if (pkg.contains("allied") || pkg.contains("abl")) {
            senderContext = "allied";
        } else if (pkg.contains("faysal")) {
            senderContext = "faysal";
        } else if (pkg.contains("askari")) {
            senderContext = "askari";
        } else if (pkg.contains("bop")) {
            senderContext = "bop";
        } else if (pkg.contains("sadapay")) {
            senderContext = "sadapay";
        } else if (pkg.contains("nayapay")) {
            senderContext = "nayapay";
        }

        // Stage B: If from SMS app (Google Messages, Samsung Messages), inspect title for sender shortcode / name
        if (senderContext.isEmpty()) {
            if (lowerTitle.contains("8079") || lowerTitle.contains("8078") || lowerTitle.contains("9060") || lowerTitle.contains("8222") || lowerTitle.contains("meezan") || lowerTitle.contains("mbl")) {
                senderContext = "8079";
            } else if (lowerTitle.contains("8558") || lowerTitle.contains("jazzcash") || lowerTitle.startsWith("jc")) {
                senderContext = "8558";
            } else if (lowerTitle.contains("3737") || lowerTitle.contains("easypaisa") || lowerTitle.startsWith("ep")) {
                senderContext = "3737";
            } else if (lowerTitle.contains("2252") || lowerTitle.contains("alfalah") || lowerTitle.contains("bafl")) {
                senderContext = "2252";
            } else if (lowerTitle.contains("4250") || lowerTitle.contains("hbl") || lowerTitle.contains("habib")) {
                senderContext = "4250";
            } else if (lowerTitle.contains("8257") || lowerTitle.contains("ubl")) {
                senderContext = "8257";
            } else if (lowerTitle.contains("4722") || lowerTitle.contains("mcb")) {
                senderContext = "4722";
            } else if (lowerTitle.contains("nbp") || lowerTitle.contains("national bank")) {
                senderContext = "nbp";
            } else if (lowerTitle.contains("alhabib") || lowerTitle.contains("bahl") || lowerTitle.contains("al habib")) {
                senderContext = "alhabib";
            } else if (lowerTitle.contains("allied") || lowerTitle.contains("abl")) {
                senderContext = "allied";
            } else if (lowerTitle.contains("faysal") || lowerTitle.contains("fbl")) {
                senderContext = "faysal";
            } else if (lowerTitle.contains("askari") || lowerTitle.contains("akbl")) {
                senderContext = "askari";
            } else if (lowerTitle.contains("bop") || lowerTitle.contains("punjab")) {
                senderContext = "bop";
            } else if (lowerTitle.contains("sadapay")) {
                senderContext = "sadapay";
            } else if (lowerTitle.contains("nayapay")) {
                senderContext = "nayapay";
            } else {
                senderContext = (title != null && !title.trim().isEmpty()) ? title.trim() : "bank";
            }
        }

        Log.d(TAG, "Valid financial (" + txType + ") notification from " + senderContext + ": " + fullContent);
        PaymentProcessor.processIncomingText(this, senderContext, fullContent);
    }
}
