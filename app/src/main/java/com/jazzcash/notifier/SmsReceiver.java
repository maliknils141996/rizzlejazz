package com.jazzcash.notifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            return;
        }

        // Only process if license is active
        if (!LicenseManager.isLicenseActive(context)) {
            Log.d(TAG, "License inactive, ignoring SMS.");
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        StringBuilder fullMsg = new StringBuilder();
        String sender = "";

        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
            sender = sms.getDisplayOriginatingAddress();
            fullMsg.append(sms.getMessageBody());
        }

        if (sender == null) return;
        final String finalSender = sender.trim();
        final String finalMsg = fullMsg.toString().trim();

        // STRICT SENDER FILTER: Only process official payment shortcodes (8558 for JazzCash, 3737 for Easypaisa)
        boolean isOfficialJazz = finalSender.contains("8558");
        boolean isOfficialEasy = finalSender.contains("3737");

        if (!isOfficialJazz && !isOfficialEasy) {
            Log.d(TAG, "Ignoring non-financial SMS from: " + finalSender);
            return;
        }

        Log.d(TAG, "Financial SMS received from: " + finalSender + " | Body: " + finalMsg);

        final PendingResult pendingResult = goAsync();
        new Thread(() -> {
            try {
                PaymentProcessor.processIncomingText(context, finalSender, finalMsg);
            } finally {
                pendingResult.finish();
            }
        }).start();
    }
}
