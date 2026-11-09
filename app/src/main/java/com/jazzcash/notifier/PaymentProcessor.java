package com.jazzcash.notifier;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.LruCache;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentProcessor {
    private static final String TAG = "PaymentProcessor";

    // Deduplication cache: stores "channel_amount_trx" -> timestamp
    // Prevents double-announcements when both SMS and Notification fire for the same payment
    private static final LruCache<String, Long> processedAlertsCache = new LruCache<>(100);
    private static final long DEDUP_WINDOW_MS = 15000; // 15 seconds window

    public static synchronized void processIncomingText(Context context, String sender, String message) {
        if (message == null || message.trim().isEmpty()) return;
        String lowerMsg = message.toLowerCase();
        String lowerSender = sender != null ? sender.toLowerCase() : "";

        // 1. FILTER OUT CASHBACK / BONUS / INCENTIVE SPAM
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

        // 2. STRICT CHANNEL IDENTIFICATION
        String channel = null;

        // Priority A: Sender Number (8558 vs 3737)
        if (lowerSender.contains("8558")) {
            channel = "jazzcash";
        } else if (lowerSender.contains("3737")) {
            channel = "easypaisa";
        } else if (lowerSender.contains("jazzcash") || lowerSender.contains("mobilink")) {
            channel = "jazzcash";
        } else if (lowerSender.contains("easypaisa") || lowerSender.contains("telenor")) {
            channel = "easypaisa";
        }

        // Priority B: If sender is generic, inspect message body carefully
        if (channel == null) {
            boolean mentionsJazz = lowerMsg.contains("jazzcash") || lowerMsg.contains("till id");
            boolean mentionsEasy = lowerMsg.contains("easypaisa");

            if (mentionsJazz && !mentionsEasy) {
                channel = "jazzcash";
            } else if (mentionsEasy && !mentionsJazz) {
                channel = "easypaisa";
            } else if (mentionsJazz && mentionsEasy) {
                // If promotional text mentions both, determine primary context
                if (lowerMsg.startsWith("thank you") || lowerMsg.contains("till id") || lowerMsg.contains("jazzcash account")) {
                    channel = "jazzcash";
                } else if (lowerMsg.contains("easypaisa account")) {
                    channel = "easypaisa";
                }
            }
        }

        // If not JazzCash or Easypaisa, ignore
        if (channel == null) {
            Log.d(TAG, "Not a recognized JazzCash or Easypaisa alert. Ignoring.");
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

        // 6. DEDUPLICATION CHECK
        long now = System.currentTimeMillis();
        String dedupKeyWithTrx = channel + "_" + (long)amount + "_" + trxId;
        String dedupKeyGeneral = channel + "_" + (long)amount;

        Long lastSeenWithTrx = processedAlertsCache.get(dedupKeyWithTrx);
        if (lastSeenWithTrx != null && (now - lastSeenWithTrx) < DEDUP_WINDOW_MS) {
            Log.d(TAG, "Duplicate alert detected (with Trx ID). Skipping: " + dedupKeyWithTrx);
            return;
        }

        // Also check if general key was seen within 3 seconds (SMS + notification collision)
        Long lastSeenGeneral = processedAlertsCache.get(dedupKeyGeneral);
        if (lastSeenGeneral != null && (now - lastSeenGeneral) < 5000) {
            Log.d(TAG, "Duplicate alert detected (within 5s). Skipping: " + dedupKeyGeneral);
            return;
        }

        // Register in cache
        processedAlertsCache.put(dedupKeyWithTrx, now);
        processedAlertsCache.put(dedupKeyGeneral, now);

        // 7. Extract Counterparty
        String counterparty = extractCounterparty(message, isOutflow);
        if (counterparty.isEmpty()) {
            counterparty = isOutflow ? "Vendor / Recipient" : (channel.toUpperCase() + " Customer");
        }

        String whoSent = isOutflow ? "Shop" : counterparty;
        String whoReceived = isOutflow ? counterparty : "Shop";
        String purpose = isOutflow ? (channel.toUpperCase() + " Outflow / Sent") : (channel.toUpperCase() + " Payment Received");

        // 8. Save to Local SQLite Database
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        boolean inserted = db.insertTransaction(trxId, amount, type, channel, whoSent, whoReceived, purpose);

        if (inserted) {
            Log.d(TAG, "Recorded " + type + " of Rs. " + amount + " on " + channel + " (TID: " + trxId + ")");

            // 9. Voice Announcement ONLY for Inflow (+) on the SPECIFIC channel
            if ("inflow".equalsIgnoreCase(type)) {
                VoiceEngine.getInstance(context).speakPayment(channel, amount);
            }

            // 10. Notify MainActivity to update UI Net Balance & Khata live
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
