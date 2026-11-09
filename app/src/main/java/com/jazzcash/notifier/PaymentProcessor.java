package com.jazzcash.notifier;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentProcessor {
    private static final String TAG = "PaymentProcessor";

    public static void processIncomingText(Context context, String sender, String message) {
        if (message == null || message.isEmpty()) return;
        String lowerMsg = message.toLowerCase();

        // 1. Identify Channel (JazzCash vs Easypaisa)
        String channel = "jazzcash";
        boolean isJazzCash = sender.contains("8558") || lowerMsg.contains("jazzcash") || lowerMsg.contains("till id");
        boolean isEasypaisa = sender.contains("3737") || lowerMsg.contains("easypaisa") || lowerMsg.contains("telenor");

        if (isEasypaisa) {
            channel = "easypaisa";
        } else if (!isJazzCash && !lowerMsg.contains("received rs") && !lowerMsg.contains("transferred rs")) {
            // Not a payment message
            return;
        }

        // 2. Identify Type (Inflow vs Outflow)
        boolean isOutflow = lowerMsg.contains("transferred rs") || 
                            lowerMsg.contains("transferred to") || 
                            lowerMsg.contains("sent rs") || 
                            lowerMsg.contains("paid to") || 
                            lowerMsg.contains("you have sent") ||
                            lowerMsg.contains("debited");

        String type = isOutflow ? "outflow" : "inflow";

        // 3. Extract Amount
        double amount = extractAmount(message);
        if (amount <= 0) return;

        // 4. Extract Trx ID
        String trxId = extractTrxId(message);

        // 5. Extract Counterparty (Sender or Recipient)
        String counterparty = extractCounterparty(message);
        if (counterparty.isEmpty()) {
            counterparty = sender.isEmpty() ? (channel.toUpperCase() + " User") : sender;
        }

        String whoSent = isOutflow ? "Shop" : counterparty;
        String whoReceived = isOutflow ? counterparty : "Shop";
        String purpose = isOutflow ? (channel.toUpperCase() + " Transfer Out") : (channel.toUpperCase() + " Payment Received");

        // 6. Save to Local SQLite Database
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        boolean inserted = db.insertTransaction(trxId, amount, type, channel, whoSent, whoReceived, purpose);

        if (inserted) {
            Log.d(TAG, "Recorded " + type + " of Rs. " + amount + " on " + channel + " (TID: " + trxId + ")");

            // 7. Voice Announcement for Inflow (+)
            if ("inflow".equalsIgnoreCase(type)) {
                VoiceEngine.getInstance(context).speakPayment(channel, amount);
            }

            // 8. Notify MainActivity to update UI live
            Intent refreshIntent = new Intent("com.rizzleshop.ACTION_REFRESH_KHATA");
            context.sendBroadcast(refreshIntent);
        }
    }

    private static double extractAmount(String msg) {
        try {
            // Patterns: "Received Rs. 1,500.00", "sent Rs. 500", "Rs. 250 received", "Payment of Rs 1000"
            Pattern p1 = Pattern.compile("(?:Received|received|sent|Transferred|Payment of|credited with)\\s*Rs\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher m1 = p1.matcher(msg);
            if (m1.find()) {
                return Double.parseDouble(m1.group(1).replace(",", ""));
            }

            Pattern p2 = Pattern.compile("Rs\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:received|transferred|sent|has been received)", Pattern.CASE_INSENSITIVE);
            Matcher m2 = p2.matcher(msg);
            if (m2.find()) {
                return Double.parseDouble(m2.group(1).replace(",", ""));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing amount: " + e.getMessage());
        }
        return 0.0;
    }

    private static String extractTrxId(String msg) {
        try {
            Pattern p = Pattern.compile("(?:Trx\\s*ID|TID|Transaction\\s*ID|TXN\\s*ID|Trans\\s*ID)[:\\s]*([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(msg);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing TID: " + e.getMessage());
        }
        return "TXN_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 900 + 100);
    }

    private static String extractCounterparty(String msg) {
        try {
            Pattern p = Pattern.compile("(?:from|to|by)\\s*(\\+?92[0-9]{10}|03[0-9]{9})", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(msg);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }
}
