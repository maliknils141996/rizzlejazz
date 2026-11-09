package com.jazzcash.notifier;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentProcessor {
    private static final String TAG = "PaymentProcessor";

    public static void processIncomingText(Context context, String sender, String message) {
        if (message == null || message.trim().isEmpty()) return;
        String lowerMsg = message.toLowerCase();

        // 1. FILTER OUT CASHBACK / BONUS / INCENTIVE SPAM
        // When customer pays via QR/Till, JazzCash/Easypaisa sends a 2nd SMS for 1 or 2 Rs cashback.
        // We MUST ignore it so it doesn't interrupt or override the real payment announcement!
        if (lowerMsg.contains("cashback") ||
            lowerMsg.contains("cash back") ||
            lowerMsg.contains("bonus") ||
            lowerMsg.contains("bouns") ||
            lowerMsg.contains("incentive") ||
            lowerMsg.contains("commission") ||
            lowerMsg.contains("reward") ||
            lowerMsg.contains("loyalty") ||
            lowerMsg.contains("points credited") ||
            lowerMsg.contains("earning credited") ||
            lowerMsg.contains("promo") ||
            lowerMsg.contains("fee refund")) {
            Log.d(TAG, "Ignored cashback/bonus spam alert: " + message);
            return;
        }

        // 2. Identify Channel (JazzCash vs Easypaisa)
        String channel = "jazzcash";
        boolean isJazzCash = sender.contains("8558") || lowerMsg.contains("jazzcash") || lowerMsg.contains("till id");
        boolean isEasypaisa = sender.contains("3737") || lowerMsg.contains("easypaisa") || lowerMsg.contains("telenor");

        if (isEasypaisa) {
            channel = "easypaisa";
        } else if (!isJazzCash && !lowerMsg.contains("received rs") && !lowerMsg.contains("transferred rs") && !lowerMsg.contains("payment of rs")) {
            // Not a financial transaction message
            return;
        }

        // 3. Identify Type (Inflow vs Outflow)
        boolean isOutflow = lowerMsg.contains("transferred rs") ||
                            lowerMsg.contains("transferred to") ||
                            lowerMsg.contains("transferred of") ||
                            lowerMsg.contains("sent rs") ||
                            lowerMsg.contains("you have sent") ||
                            lowerMsg.contains("paid rs") ||
                            lowerMsg.contains("paid to") ||
                            (lowerMsg.contains("payment of rs") && lowerMsg.contains("made to")) ||
                            lowerMsg.contains("cash withdrawal") ||
                            lowerMsg.contains("withdrawn rs") ||
                            lowerMsg.contains("debited with") ||
                            lowerMsg.contains("has been debited");

        String type = isOutflow ? "outflow" : "inflow";

        // 4. Extract Amount
        double amount = extractAmount(message);
        if (amount <= 0) {
            Log.w(TAG, "Could not extract valid amount from: " + message);
            return;
        }

        // 5. Extract Trx ID
        String trxId = extractTrxId(message);

        // 6. Extract Counterparty (Sender or Recipient)
        String counterparty = extractCounterparty(message, isOutflow);
        if (counterparty.isEmpty()) {
            counterparty = isOutflow ? "Vendor / Recipient" : (channel.toUpperCase() + " Customer");
        }

        String whoSent = isOutflow ? "Shop" : counterparty;
        String whoReceived = isOutflow ? counterparty : "Shop";
        String purpose = isOutflow ? (channel.toUpperCase() + " Outflow / Sent") : (channel.toUpperCase() + " Payment Received");

        // 7. Save to Local SQLite Database
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        boolean inserted = db.insertTransaction(trxId, amount, type, channel, whoSent, whoReceived, purpose);

        if (inserted) {
            Log.d(TAG, "Recorded " + type + " of Rs. " + amount + " on " + channel + " (TID: " + trxId + ")");

            // 8. Voice Announcement ONLY for Inflow (+) payments from customers
            if ("inflow".equalsIgnoreCase(type)) {
                VoiceEngine.getInstance(context).speakPayment(channel, amount);
            }

            // 9. Notify MainActivity to update UI Net Balance & Khata live
            Intent refreshIntent = new Intent("com.rizzleshop.ACTION_REFRESH_KHATA");
            context.sendBroadcast(refreshIntent);
        }
    }

    private static double extractAmount(String msg) {
        try {
            // Pattern 1: "Received Rs. 1,500.00", "sent Rs. 500", "Payment of Rs 1000", "credited with Rs. 250"
            Pattern p1 = Pattern.compile("(?:Received|received|sent|Transferred|Payment of|credited with|withdrawal of)\\s*Rs\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher m1 = p1.matcher(msg);
            if (m1.find()) {
                return Double.parseDouble(m1.group(1).replace(",", ""));
            }

            // Pattern 2: "Rs. 250 received", "Rs. 1000 transferred"
            Pattern p2 = Pattern.compile("Rs\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:received|transferred|sent|has been received|paid)", Pattern.CASE_INSENSITIVE);
            Matcher m2 = p2.matcher(msg);
            if (m2.find()) {
                return Double.parseDouble(m2.group(1).replace(",", ""));
            }

            // Pattern 3: "Payment of Rs. 100" without explicit "received" right after
            Pattern p3 = Pattern.compile("Rs\\.?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher m3 = p3.matcher(msg);
            if (m3.find()) {
                return Double.parseDouble(m3.group(1).replace(",", ""));
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

    private static String extractCounterparty(String msg, boolean isOutflow) {
        try {
            // Check for phone numbers: 03001234567 or 923001234567
            Pattern pPhone = Pattern.compile("(?:from|to|by)\\s*(\\+?92[0-9]{10}|03[0-9]{9})", Pattern.CASE_INSENSITIVE);
            Matcher mPhone = pPhone.matcher(msg);
            if (mPhone.find()) {
                return mPhone.group(1).trim();
            }

            // Check for names like "to Ali Khan" or "from Usman"
            Pattern pName = Pattern.compile("(?:from|to)\\s+([A-Za-z\\s]{3,20})(?:\\(|\\.|TID|Balance|$)", Pattern.CASE_INSENSITIVE);
            Matcher mName = pName.matcher(msg);
            if (mName.find()) {
                String name = mName.group(1).trim();
                if (!name.equalsIgnoreCase("your") && !name.equalsIgnoreCase("account")) {
                    return name;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return isOutflow ? "Transfer / Outflow" : "Customer";
    }
}
