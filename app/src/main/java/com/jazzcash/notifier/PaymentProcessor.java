package com.jazzcash.notifier;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.LruCache;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentProcessor {
    private static final String TAG = "PaymentProcessor";

    // Deduplication cache: stores "channel_trxId" -> timestamp
    // Prevents double-announcements when both SMS and Notification fire for the exact same transaction
    private static final LruCache<String, Long> processedAlertsCache = new LruCache<>(150);
    private static final long DEDUP_WINDOW_MS = 25000; // 25 seconds window

    public static synchronized void processIncomingText(Context context, String sender, String message) {
        if (message == null || message.trim().isEmpty()) return;
        String lowerMsg = message.toLowerCase();
        String lowerSender = sender != null ? sender.toLowerCase() : "";

        // 1. FILTER OUT PURE SYSTEM & UTILITY ALERTS (OTP, login, PIN warnings)
        if (lowerMsg.contains("otp") ||
            lowerMsg.contains("verification code") ||
            lowerMsg.contains("login to your") ||
            lowerMsg.contains("mpin will be locked") ||
            lowerMsg.contains("reset mpin") ||
            lowerMsg.contains("password")) {
            Log.d(TAG, "Ignored system/security non-financial alert: " + message);
            return;
        }

        // Filter out MDR Cashback ONLY if it is a cashback notice (e.g. "Dear Merchant, you have received MDR Cashback of Rs 0.15")
        // and NOT a real customer transfer.
        if (lowerMsg.contains("mdr cashback") ||
            (lowerMsg.startsWith("dear merchant, you have received") && lowerMsg.contains("cashback of rs 0.") && !lowerMsg.contains("via raast"))) {
            Log.d(TAG, "Ignored MDR cashback fee notice: " + message);
            return;
        }

        // 2. DETECT CHANNEL / BANK / WALLET
        String channel = detectChannel(sender, message);
        if (channel == null) {
            channel = "jazzcash"; // safe default
        }

        // 3. IDENTIFY TRANSACTION TYPE (INFLOW VS OUTFLOW)
        // High priority: Check Outflow signals first (e.g. "sent to RIZZLE PHARMACY... will be credited within 30 minutes")
        boolean isOutflow = lowerMsg.contains("sent to ") ||
                            lowerMsg.contains("you have sent") ||
                            lowerMsg.contains("you sent") ||
                            lowerMsg.contains("sent rs") ||
                            lowerMsg.contains("sent pkr") ||
                            lowerMsg.contains("paid to") ||
                            lowerMsg.contains("paid rs") ||
                            lowerMsg.contains("transferred to") ||
                            lowerMsg.contains("transfer to") ||
                            lowerMsg.contains("will be credited") || // Outbound bank transfer confirmation
                            (lowerMsg.contains("payment to") && !lowerMsg.contains("received") && !lowerMsg.contains("recieved")) ||
                            (lowerMsg.contains("successful to") && !lowerMsg.contains("received") && !lowerMsg.contains("recieved")) ||
                            lowerMsg.contains("cash withdrawal") ||
                            lowerMsg.contains("withdrawn") ||
                            lowerMsg.contains("debited");

        boolean isInflow = !isOutflow && (
                           lowerMsg.contains("received") ||
                           lowerMsg.contains("recieved") || // JazzCash official SMS spelling: "You have Recieved Rs..."
                           lowerMsg.contains("incoming payment") ||
                           lowerMsg.contains("has sent you") ||
                           lowerMsg.contains("sent you") ||
                           (lowerMsg.contains("credited") && !lowerMsg.contains("will be credited")) ||
                           lowerMsg.contains("deposited") ||
                           lowerMsg.contains("qr payment")
        );

        String type;
        if (isOutflow) {
            type = "outflow";
        } else if (isInflow) {
            type = "inflow";
        } else {
            // Not a financial movement
            Log.d(TAG, "Message neither explicit inflow nor outflow. Ignoring: " + message);
            return;
        }

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
        // If a real TID exists, deduplicate against double alerts for that exact same TID
        if (trxId != null && !trxId.isEmpty() && !trxId.startsWith("TX-GEN-")) {
            String dedupKey = channel + "_" + type + "_" + trxId;
            Long lastSeen = processedAlertsCache.get(dedupKey);
            if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) {
                Log.d(TAG, "Duplicate alert detected for same TID (" + dedupKey + "). Skipping.");
                return;
            }
            processedAlertsCache.put(dedupKey, now);
        }

        // 7. Extract Counterparty
        String counterparty = extractCounterparty(message, isOutflow);
        if (counterparty.isEmpty()) {
            counterparty = isOutflow ? "Vendor / Recipient" : (VoiceEngine.getChannelVoiceName(channel) + " Customer");
        }

        String whoSent = isOutflow ? "Shop" : counterparty;
        String whoReceived = isOutflow ? counterparty : "Shop";
        String channelVoice = VoiceEngine.getChannelVoiceName(channel);
        String purpose = isOutflow ? (channelVoice + " Outflow / Sent") : (channelVoice + " Payment Received");

        // 8. Save to Local SQLite Database
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        boolean inserted = db.insertTransaction(trxId, amount, type, channel, whoSent, whoReceived, purpose);

        if (inserted) {
            Log.d(TAG, "Recorded " + type + " of Rs. " + amount + " on " + channel + " (TID: " + trxId + ")");

            // 9. Voice Announcement for both INFLOW (+) and OUTFLOW (-) on this exact channel/bank!
            if ("inflow".equalsIgnoreCase(type)) {
                VoiceEngine.getInstance(context).speakPayment(channel, amount);
            } else if ("outflow".equalsIgnoreCase(type)) {
                VoiceEngine.getInstance(context).speakOutflow(channel, amount);
            }

            // 10. Live refresh MainActivity
            Intent refreshIntent = new Intent("com.rizzleshop.ACTION_REFRESH_KHATA");
            context.sendBroadcast(refreshIntent);
        }
    }

    public static String detectChannel(String sender, String message) {
        String s = sender != null ? sender.toLowerCase() : "";
        String m = message != null ? message.toLowerCase() : "";

        // 1. Meezan Bank (8222, MeezanBank, Meezan)
        if (s.contains("8222") || s.contains("meezan") || m.contains("meezan")) {
            return "meezan";
        }
        // 2. Bank Alfalah (2252, Alfalah, BAFL)
        if (s.contains("alfalah") || s.contains("bafl") || m.contains("alfalah") || m.contains("bank alfalah") || (s.contains("2252") && m.contains("alfalah"))) {
            return "alfalah";
        }
        // 3. HBL (4250, HBL, Habib Bank)
        if (s.contains("4250") || s.contains("hbl") || m.contains("hbl") || m.contains("habib bank")) {
            return "hbl";
        }
        // 4. UBL (8257, UBL)
        if (s.contains("8257") || s.contains("ubl") || m.contains("ubl digital") || m.contains("ubl a/c") || m.contains("ubl account") || (m.contains("ubl") && !m.contains("public"))) {
            return "ubl";
        }
        // 5. MCB Bank (4722, MCB)
        if (s.contains("4722") || s.contains("mcb") || m.contains("mcb")) {
            return "mcb";
        }
        // 6. Allied Bank (ABL, 2252)
        if (s.contains("allied") || s.contains("abl") || m.contains("allied bank") || m.contains("abl a/c")) {
            return "allied";
        }
        // 7. Faysal Bank (2257, Faysal, FBL)
        if (s.contains("2257") || s.contains("faysal") || s.contains("fbl") || m.contains("faysal bank")) {
            return "faysal";
        }
        // 8. Askari Bank (9225, AKBL, Askari)
        if (s.contains("9225") || s.contains("akbl") || s.contains("askari") || m.contains("askari bank")) {
            return "askari";
        }
        // 9. Bank of Punjab (8122, BOP)
        if (s.contains("8122") || s.contains("bop") || m.contains("bank of punjab")) {
            return "bop";
        }
        // 10. JS Bank (8489, JS Bank)
        if (s.contains("8489") || s.contains("jsbank") || s.contains("js bank") || m.contains("js bank")) {
            return "jsbank";
        }
        // 11. Soneri Bank (8899, Soneri)
        if (s.contains("8899") || s.contains("soneri") || m.contains("soneri bank")) {
            return "soneri";
        }
        // 12. SadaPay (SadaPay, 9922)
        if (s.contains("sadapay") || s.contains("9922") || m.contains("sadapay")) {
            return "sadapay";
        }
        // 13. NayaPay (NayaPay, 8080)
        if (s.contains("nayapay") || s.contains("8080") || m.contains("nayapay")) {
            return "nayapay";
        }
        // 14. BankIslami (2242)
        if (s.contains("2242") || s.contains("bankislami") || m.contains("bankislami") || m.contains("bank islami")) {
            return "bankislami";
        }
        // 15. Al Baraka (9222)
        if (s.contains("9222") || s.contains("albaraka") || m.contains("al baraka")) {
            return "albaraka";
        }
        // 16. Dubai Islamic Bank (8282, DIB)
        if (s.contains("8282") || s.contains("dib") || m.contains("dubai islamic")) {
            return "dib";
        }
        // 17. Standard Chartered (SCB)
        if (s.contains("scb") || m.contains("standard chartered")) {
            return "scb";
        }
        // 18. JazzCash (8558, Mobilink, JazzCash)
        if (s.contains("8558") || s.contains("jazzcash") || s.contains("mobilink") || m.contains("jazzcash") || m.contains("till id")) {
            return "jazzcash";
        }
        // 19. Easypaisa (3737, Telenor, Easypaisa)
        if (s.contains("3737") || s.contains("easypaisa") || s.contains("telenor") || m.contains("easypaisa")) {
            return "easypaisa";
        }

        // Generic Raast / Bank
        if (m.contains("raast") || m.contains("a/c") || m.contains("account") || m.contains("acct")) {
            return "bank";
        }

        return "jazzcash";
    }

    private static double extractAmount(String msg) {
        try {
            // Pattern 1: Action followed by Rs/PKR
            // E.g. "Received Rs. 1,500.00", "Recieved Rs 60.00", "credited with PKR 5000", "sent Rs. 500", "Payment of Rs 1000"
            Pattern p1 = Pattern.compile("(?:Received|recieved|sent|Transferred|Payment of|credited with|withdrawal of|debited with|deposited)\\s*(?:Rs\\.?|PKR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher m1 = p1.matcher(msg);
            if (m1.find()) {
                return Double.parseDouble(m1.group(1).replace(",", ""));
            }

            // Pattern 2: Rs/PKR followed by action
            // E.g. "Rs 60 received", "PKR 5,000.00 credited", "Rs 9,177.00 sent to", "QR Payment Rs 60 received"
            Pattern p2 = Pattern.compile("(?:Rs\\.?|PKR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:received|recieved|transferred|sent|has been received|paid|credited|debited)", Pattern.CASE_INSENSITIVE);
            Matcher m2 = p2.matcher(msg);
            if (m2.find()) {
                return Double.parseDouble(m2.group(1).replace(",", ""));
            }

            // Pattern 3: Bank phrasing: "credited by PKR 5,000" or "debited by Rs 2000"
            Pattern p3 = Pattern.compile("(?:credited|debited|received|recieved)\\s+(?:by|with|for)?\\s*(?:Rs\\.?|PKR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher m3 = p3.matcher(msg);
            if (m3.find()) {
                return Double.parseDouble(m3.group(1).replace(",", ""));
            }

            // Pattern 4: "QR Payment Rs 60"
            Pattern p4 = Pattern.compile("(?:QR\\s*Payment|Payment)\\s*(?:Rs\\.?|PKR)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher m4 = p4.matcher(msg);
            if (m4.find()) {
                return Double.parseDouble(m4.group(1).replace(",", ""));
            }

            // Pattern 5: General Rs/PKR fallback
            Pattern p5 = Pattern.compile("(?:Rs\\.?|PKR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher m5 = p5.matcher(msg);
            while (m5.find()) {
                double val = Double.parseDouble(m5.group(1).replace(",", ""));
                // If it's a real amount (not a fee like 0.15)
                if (val >= 0.50) {
                    return val;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing amount: " + e.getMessage());
        }
        return 0.0;
    }

    private static String extractTrxId(String msg) {
        try {
            Pattern p = Pattern.compile("(?:Trx\\s*ID|TID|TxID|Txn\\s*ID|Transaction\\s*ID|TXN|Trans\\s*ID|Ref(?:erence)?\\s*(?:No|ID|#)?|STAN)[:\\s]*([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(msg);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing TID: " + e.getMessage());
        }
        return "TX-GEN-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 9000 + 1000);
    }

    private static String extractCounterparty(String msg, boolean isOutflow) {
        try {
            // Check for phone numbers: 03001234567 or 923001234567
            Pattern pPhone = Pattern.compile("(?:from|to|by)\\s*(\\+?92[0-9]{10}|03[0-9]{9})", Pattern.CASE_INSENSITIVE);
            Matcher mPhone = pPhone.matcher(msg);
            if (mPhone.find()) {
                return mPhone.group(1).trim();
            }

            // Check for Raast format: "received from MALIK NAVEED IQBAL AC ..." or "received from ALI KHAN in your"
            Pattern pRaast = Pattern.compile("(?:received|recieved|credited)\\s+(?:from|via Raast from)\\s+([A-Za-z0-9\\s]{2,35}?)(?:\\s+AC|\\s+A/C|\\s+via|\\(|\\.|in your|TID|,|$)", Pattern.CASE_INSENSITIVE);
            Matcher mRaast = pRaast.matcher(msg);
            if (mRaast.find()) {
                String name = mRaast.group(1).trim();
                if (!name.equalsIgnoreCase("your") && !name.equalsIgnoreCase("account") && name.length() > 2) {
                    return name;
                }
            }

            // Check for standard names like "sent to RIZZLE PHARMACY," or "to Ali Khan"
            Pattern pName = Pattern.compile("(?:from|to)\\s+([A-Za-z0-9\\s]{2,30}?)(?:\\s+AC|\\s+A/C|,|\\(|\\.|TID|Balance|$)", Pattern.CASE_INSENSITIVE);
            Matcher mName = pName.matcher(msg);
            if (mName.find()) {
                String name = mName.group(1).trim();
                if (!name.equalsIgnoreCase("your") && !name.equalsIgnoreCase("account") && name.length() > 2) {
                    return name;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return isOutflow ? "Vendor / Recipient" : "Customer";
    }
}
