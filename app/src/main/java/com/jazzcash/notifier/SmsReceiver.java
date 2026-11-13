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

        // ALLOW ALL OFFICIAL PAKISTANI WALLETS & BANKS
        // 1. Wallets: 8558 (JazzCash), 3737 (Easypaisa), SadaPay (9922), NayaPay (8080)
        // 2. Banks: Meezan (8222), Alfalah (2252), HBL (4250), UBL (8257), MCB (4722),
        //           ABL (2252), Faysal (2257), Askari (9225), BOP (8122), JS (8489),
        //           Soneri (8899), BankIslami (2242), AlBaraka (9222), DIB (8282)
        // 3. Any alphanumeric sender with bank/wallet/raast name
        // 4. Any SMS with financial transaction keywords (Rs/PKR + received/sent/credited/debited)
        String s = finalSender.toLowerCase();
        String m = finalMsg.toLowerCase();

        boolean isFinancialSender = s.contains("8558") || s.contains("3737") ||
                s.contains("8222") || s.contains("2252") || s.contains("4250") ||
                s.contains("8257") || s.contains("4722") || s.contains("2257") ||
                s.contains("9225") || s.contains("8122") || s.contains("8489") ||
                s.contains("8899") || s.contains("9922") || s.contains("8080") ||
                s.contains("2242") || s.contains("9222") || s.contains("8282") ||
                s.contains("jazz") || s.contains("easy") || s.contains("meezan") ||
                s.contains("alfalah") || s.contains("bafl") || s.contains("hbl") ||
                s.contains("ubl") || s.contains("mcb") || s.contains("allied") ||
                s.contains("abl") || s.contains("faysal") || s.contains("fbl") ||
                s.contains("askari") || s.contains("bop") || s.contains("js") ||
                s.contains("soneri") || s.contains("sada") || s.contains("naya") ||
                s.contains("bank") || s.contains("raast");

        boolean hasFinancialContent = (m.contains("rs") || m.contains("pkr")) &&
                (m.contains("received") || m.contains("recieved") || m.contains("credited") ||
                 m.contains("sent") || m.contains("debited") || m.contains("transferred") ||
                 m.contains("paid") || m.contains("qr payment") || m.contains("deposited"));

        if (!isFinancialSender && !hasFinancialContent) {
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
