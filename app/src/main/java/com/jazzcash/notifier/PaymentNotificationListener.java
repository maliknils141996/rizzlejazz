package com.jazzcash.notifier;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class PaymentNotificationListener extends NotificationListenerService {
    private static final String TAG = "PaymentNotification";

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

        // 2. Outbound notifications must NEVER trigger an incoming payment alert!
        // E.g. If merchant sends money via Easypaisa/JazzCash app: "Payment of Rs 1 was successful", "You sent money"
        if (lowerContent.contains("sent") ||
            lowerContent.contains("transferred") ||
            lowerContent.contains("paid") ||
            lowerContent.contains("successful to") ||
            lowerContent.contains("payment to") ||
            lowerContent.contains("debited") ||
            lowerContent.contains("otp") ||
            lowerContent.contains("login") ||
            lowerContent.contains("balance")) {
            Log.d(TAG, "Ignoring outbound/system notification: " + fullContent);
            return;
        }

        // 3. Must explicitly contain proof of incoming received payment
        boolean isExplicitIncoming = lowerContent.contains("received rs") ||
                                     lowerContent.contains("has received") ||
                                     lowerContent.contains("payment received") ||
                                     lowerContent.contains("has sent you") ||
                                     lowerContent.contains("credited with");

        if (!isExplicitIncoming) {
            Log.d(TAG, "Ignoring non-incoming payment notification: " + fullContent);
            return;
        }

        // 4. Identify Channel strictly
        String senderContext;
        if (pkg.contains("mobilink") || pkg.contains("jazzcash") || title.contains("JazzCash") || lowerContent.contains("jazzcash")) {
            senderContext = "8558";
        } else if (pkg.contains("telenor") || pkg.contains("easypaisa") || title.contains("Easypaisa") || lowerContent.contains("easypaisa")) {
            senderContext = "3737";
        } else {
            return;
        }

        Log.d(TAG, "Valid incoming payment notification: " + fullContent);
        PaymentProcessor.processIncomingText(this, senderContext, fullContent);
    }
}
