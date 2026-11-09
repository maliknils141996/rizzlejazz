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
        Log.d(TAG, "Notification received: " + fullContent);

        PaymentProcessor.processIncomingText(this, title, fullContent);
    }
}
