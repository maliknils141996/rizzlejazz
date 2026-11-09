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

        final String finalSender = sender;
        final String finalMsg = fullMsg.toString();
        Log.d(TAG, "SMS from: " + sender + " | Body: " + finalMsg);

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
