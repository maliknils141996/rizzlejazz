package com.jazzcash.notifier;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.LruCache;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentProcessor {
    private static final String TAG = "PaymentProcessor";

    // Deduplication cache: stores "channel_type_trxId" -> timestamp
    // Prevents double-announcements when both SMS and Notification fire for the exact same transaction
    private static final LruCache<String, Long> processedAlertsCache = new LruCache<>(150);
    private static final long DEDUP_WINDOW_MS = 25000; // 25 seconds window

    public static synchronized void processIncomingText(Context context, String sender, String message) {
        if (message == null || message.trim().isEmpty()) return;
        String lowerMsg = message.toLowerCase();

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

        // Filter out MDR Cashback ONLY if it is a cashback fee notice (e.g. "Dear Merchant, you have received MDR Cashback of Rs 0.15")
        // and NOT a real customer transfer.
        if (lowerMsg.contains("mdr cashback") ||
            (lowerMsg.startsWith("dear merchant, you have received") && lowerMsg.contains("cashback of rs 0.") && !lowerMsg.contains("via raast"))) {
            Log.d(TAG, "Ignored MDR cashback fee notice: " + message);
            return;
        }

        // 2. DETECT CHANNEL / BANK / WALLET (Sender shortcodes prioritized!)
        String channel = detectChannel(sender, message);
        if (channel == null) {
            channel = "jazzcash"; // safe default
        }

        // 3. IDENTIFY TRANSACTION TYPE (INFLOW VS OUTFLOW) WITH STRICT ACCURACY
        String type = detectTransactionType(message);
        if (type == null) {
            Log.d(TAG, "Message neither explicit inflow nor outflow. Ignoring: " + message);
            return;
        }
        boolean isOutflow = "outflow".equalsIgnoreCase(type);

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
        // If a real TID exists, deduplicate against double alerts for that exact same TID & type
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

    public static String detectTransactionType(String message) {
        if (message == null) return null;
        String lower = message.toLowerCase().trim();

        // 1. OUTFLOW SIGNALS (Debit, Transfer Out, Sent, Paid, Withdraw)
        boolean hasOutflowKeyword =
                lower.contains("debited") ||
                lower.contains("debit of") ||
                lower.contains("cash withdrawal") ||
                lower.contains("withdrawn") ||
                lower.contains("will be credited") || // Outbound IBFT
                lower.contains("paid to") ||
                lower.contains("paid bill") ||
                lower.contains("bill payment") ||
                lower.contains("purchase at");

        // Regex patterns for outflow:
        boolean sentPattern = Pattern.compile("\\b(?:sent|send|sending)\\b.*\\bto\\b", Pattern.CASE_INSENSITIVE).matcher(lower).find();
        boolean transferPattern = Pattern.compile("\\b(?:transferred|transfer)\\b.*\\bto\\b", Pattern.CASE_INSENSITIVE).matcher(lower).find();
        boolean paymentToPattern = Pattern.compile("\\bpayment\\b.*\\bto\\b", Pattern.CASE_INSENSITIVE).matcher(lower).find();
        boolean paidToPattern = Pattern.compile("\\bpaid\\b.*\\bto\\b", Pattern.CASE_INSENSITIVE).matcher(lower).find();
        boolean youSentPattern = Pattern.compile("\\byou\\s+(?:have\\s+)?(?:successfully\\s+)?sent\\b", Pattern.CASE_INSENSITIVE).matcher(lower).find();
        boolean hasBeenSent = lower.contains("has been sent") || lower.contains("is sent");

        boolean isOutflow = hasOutflowKeyword || sentPattern || transferPattern || paymentToPattern || paidToPattern || youSentPattern || hasBeenSent;

        // 2. INFLOW SIGNALS (Credit, Received, Deposited, Incoming)
        boolean hasInflowKeyword =
                lower.contains("received") ||
                lower.contains("recieved") || // JazzCash official typo
                lower.contains("incoming payment") ||
                lower.contains("has sent you") ||
                lower.contains("sent you") ||
                lower.contains("deposited") ||
                lower.contains("deposit of") ||
                lower.contains("qr payment") ||
                lower.contains("credited with") ||
                lower.contains("credited by") ||
                lower.contains("is credited") ||
                lower.contains("has been credited") ||
                lower.contains("credited to your account");

        boolean receivedFromPattern = Pattern.compile("\\b(?:received|recieved)\\b.*\\bfrom\\b", Pattern.CASE_INSENSITIVE).matcher(lower).find();
        boolean creditedFromPattern = Pattern.compile("\\bcredited\\b.*\\bfrom\\b", Pattern.CASE_INSENSITIVE).matcher(lower).find();

        boolean isInflow = hasInflowKeyword || receivedFromPattern || creditedFromPattern;

        // Conflict resolution: If both match, outflow takes priority when outgoing markers exist
        // E.g. "Rs 9,177.00 sent to RIZZLE PHARMACY, Meezan A/C: ... will be credited within 30 minutes"
        if (isOutflow && isInflow) {
            if (sentPattern || transferPattern || paidToPattern || youSentPattern || lower.contains("debited") || lower.contains("will be credited")) {
                return "outflow";
            }
        }

        if (isOutflow) return "outflow";
        if (isInflow) return "inflow";

        return null;
    }

    public static String detectChannel(String sender, String message) {
        String s = sender != null ? sender.toLowerCase().trim() : "";
        String m = message != null ? message.toLowerCase().trim() : "";

        // PRIORITY 1: OFFICIAL SENDER SHORTCODES & HEADER MASKS
        if (s.contains("8558") || s.contains("jazzcash") || s.contains("mobilink") || s.startsWith("jc")) {
            return "jazzcash";
        }
        if (s.contains("3737") || s.contains("easypaisa") || s.contains("telenor") || s.startsWith("ep")) {
            return "easypaisa";
        }
        if (s.contains("8222") || s.contains("meezan")) {
            return "meezan";
        }
        if (s.contains("2252") || s.contains("alfalah") || s.contains("bafl")) {
            if (m.contains("allied") || m.contains("abl")) {
                return "allied";
            }
            return "alfalah";
        }
        if (s.contains("4250") || s.contains("hbl")) {
            return "hbl";
        }
        if (s.contains("8257") || s.contains("ubl")) {
            return "ubl";
        }
        if (s.contains("4722") || s.contains("mcb")) {
            return "mcb";
        }
        if (s.contains("allied") || s.contains("abl")) {
            return "allied";
        }
        if (s.contains("2257") || s.contains("faysal") || s.contains("fbl")) {
            return "faysal";
        }
        if (s.contains("9225") || s.contains("askari") || s.contains("akbl")) {
            return "askari";
        }
        if (s.contains("8122") || s.contains("bop")) {
            return "bop";
        }
        if (s.contains("8489") || s.contains("jsbank") || s.contains("js bank")) {
            return "jsbank";
        }
        if (s.contains("8899") || s.contains("soneri")) {
            return "soneri";
        }
        if (s.contains("9922") || s.contains("sadapay")) {
            return "sadapay";
        }
        if (s.contains("8080") || s.contains("nayapay")) {
            return "nayapay";
        }
        if (s.contains("2242") || s.contains("bankislami")) {
            return "bankislami";
        }
        if (s.contains("9222") || s.contains("albaraka")) {
            return "albaraka";
        }
        if (s.contains("8282") || s.contains("dib")) {
            return "dib";
        }
        if (s.contains("scb") || s.contains("standard chartered")) {
            return "scb";
        }

        // PRIORITY 2: SENDER WAS GENERIC / UNIDENTIFIED -> CHECK MESSAGE CONTENT
        if (m.contains("jazzcash") || m.contains("till id")) return "jazzcash";
        if (m.contains("easypaisa")) return "easypaisa";
        if (m.contains("meezan bank") || m.contains("meezan account") || m.contains("meezan a/c")) return "meezan";
        if (m.contains("bank alfalah") || m.contains("alfalah")) return "alfalah";
        if (m.contains("habib bank") || m.contains("hbl alert") || m.contains("hbl a/c")) return "hbl";
        if (m.contains("ubl digital") || m.contains("ubl alert") || m.contains("ubl a/c")) return "ubl";
        if (m.contains("mcb bank") || m.contains("mcb alert")) return "mcb";
        if (m.contains("allied bank") || m.contains("abl alert")) return "allied";
        if (m.contains("faysal bank")) return "faysal";
        if (m.contains("askari bank")) return "askari";
        if (m.contains("bank of punjab") || m.contains("bop")) return "bop";
        if (m.contains("js bank") || m.contains("jsbank")) return "jsbank";
        if (m.contains("soneri bank")) return "soneri";
        if (m.contains("sadapay")) return "sadapay";
        if (m.contains("nayapay")) return "nayapay";
        if (m.contains("bankislami") || m.contains("bank islami")) return "bankislami";
        if (m.contains("al baraka") || m.contains("albaraka")) return "albaraka";
        if (m.contains("dubai islamic") || m.contains("dib alert")) return "dib";
        if (m.contains("standard chartered")) return "scb";

        if (m.contains("meezan")) return "meezan";
        if (m.contains("hbl")) return "hbl";
        if (m.contains("ubl")) return "ubl";
        if (m.contains("mcb")) return "mcb";
        if (m.contains("raast")) return "bank";

        return "jazzcash";
    }

    public static double extractAmount(String msg) {
        if (msg == null) return 0.0;
        try {
            // Pattern 1: Action followed by Rs/PKR
            // E.g. "Received Rs. 1,500.00", "Recieved Rs 60.00", "debited by Rs. 5,000", "sent Rs. 500"
            Pattern p1 = Pattern.compile("(?:Received|recieved|sent|Transferred|Payment of|credited with|credited by|credited for|withdrawal of|debited with|debited by|debited for|debited from|deposited)\\s*(?:Rs\\.?|PKR)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher m1 = p1.matcher(msg);
            if (m1.find()) {
                double val = parseCleanDouble(m1.group(1));
                if (val > 0.40) return val;
            }

            // Pattern 2: Rs/PKR followed by number and action
            // E.g. "Rs 60 received", "PKR 5,000.00 credited", "Rs 9,177.00 sent to", "QR Payment Rs 60 received"
            Pattern p2 = Pattern.compile("(?:Rs\\.?|PKR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:received|recieved|transferred|sent|has been received|paid|credited|debited)", Pattern.CASE_INSENSITIVE);
            Matcher m2 = p2.matcher(msg);
            if (m2.find()) {
                double val = parseCleanDouble(m2.group(1));
                if (val > 0.40) return val;
            }

            // Pattern 3: Bank debit/credit phrasing (e.g. "credited by PKR 5,000" or "is debited with PKR 2,500")
            Pattern p3 = Pattern.compile("(?:credited|debited)\\s+(?:by|with|for|from)?\\s*(?:Rs\\.?|PKR)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher m3 = p3.matcher(msg);
            if (m3.find()) {
                double val = parseCleanDouble(m3.group(1));
                if (val > 0.40) return val;
            }

            // Pattern 4: "QR Payment Rs 60" or "Transfer of Rs. 5,000"
            Pattern p4 = Pattern.compile("(?:QR\\s*Payment|Payment|Transfer)\\s*(?:of)?\\s*(?:Rs\\.?|PKR)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher m4 = p4.matcher(msg);
            if (m4.find()) {
                double val = parseCleanDouble(m4.group(1));
                if (val > 0.40) return val;
            }

            // Pattern 5: General Rs/PKR fallback
            Pattern p5 = Pattern.compile("(?:Rs\\.?|PKR)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher m5 = p5.matcher(msg);
            while (m5.find()) {
                double val = parseCleanDouble(m5.group(1));
                // Only consider real transactions (skip fees like 0.15)
                if (val >= 1.0) {
                    return val;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing amount: " + e.getMessage());
        }
        return 0.0;
    }

    private static double parseCleanDouble(String val) {
        if (val == null) return 0.0;
        return Double.parseDouble(val.replace(",", "").trim());
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
