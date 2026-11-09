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

        // 2. Proof of incoming payment (matches Raast, JazzCash app, Easypaisa app, Bank alerts)
        boolean isExplicitIncoming = lowerContent.contains("received") ||
                                     lowerContent.contains("incoming payment") ||
                                     lowerContent.contains("has sent you") ||
                                     lowerContent.contains("sent you") ||
                                     lowerContent.contains("credited") ||
                                     lowerContent.contains("deposited");

        if (!isExplicitIncoming) {
            Log.d(TAG, "Ignoring non-incoming payment notification: " + fullContent);
            return;
        }

        // 3. Outbound notifications must NEVER trigger an incoming alert
        // (E.g. Merchant sends money out: "You sent Rs 500 to...", "Payment of Rs 1 was successful to...", "transferred to...")
        if (lowerContent.contains("you have sent") ||
            lowerContent.contains("you sent") ||
            lowerContent.contains("paid to") ||
            (lowerContent.contains("payment to") && !lowerContent.contains("received")) ||
            (lowerContent.contains("successful to") && !lowerContent.contains("received")) ||
            lowerContent.contains("transferred to") ||
            lowerContent.contains("cash withdrawal") ||
            lowerContent.contains("withdrawn rs") ||
            lowerContent.contains("debited") ||
            lowerContent.contains("otp") ||
            lowerContent.contains("login") ||
            lowerContent.contains("balance inquiry")) {
            Log.d(TAG, "Ignoring outbound/system notification: " + fullContent);
            return;
        }

        // 4. Identify Channel strictly (8558 = JazzCash, 3737 = Easypaisa)
        String senderContext;
        if (pkg.contains("mobilink") || pkg.contains("jazzcash") || title.toLowerCase().contains("jazzcash") || lowerContent.contains("jazzcash")) {
            senderContext = "8558";
        } else if (pkg.contains("telenor") || pkg.contains("easypaisa") || title.toLowerCase().contains("easypaisa") || lowerContent.contains("easypaisa")) {
            senderContext = "3737";
        } else {
            Log.d(TAG, "Notification not from JazzCash or Easypaisa app: " + pkg);
            return;
        }

        Log.d(TAG, "Valid incoming payment notification: " + fullContent);
        PaymentProcessor.processIncomingText(this, senderContext, fullContent);
    }
}
