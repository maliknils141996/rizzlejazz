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
            channel = "bank";
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
        // If a real TID exists, deduplicate against double alerts for that exact same channel, type & TID
        if (trxId != null && !trxId.isEmpty() && !trxId.startsWith("TX-GEN-")) {
            String dedupKey = channel + "_" + type + "_" + trxId;
            Long lastSeen = processedAlertsCache.get(dedupKey);
            if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) {
                Log.d(TAG, "Duplicate alert detected for same TID (" + dedupKey + "). Skipping.");
                return;
            }
            processedAlertsCache.put(dedupKey, now);
        }

        // 7. Extract Counterparty and Mentioned Destination Bank
        String counterparty = extractCounterparty(message, isOutflow);
        if (counterparty.isEmpty()) {
            counterparty = isOutflow ? "Recipient" : (VoiceEngine.getChannelVoiceName(channel) + " Customer");
        }

        String targetBank = detectMentionedBank(message);
        String channelVoice = VoiceEngine.getChannelVoiceName(channel);

        // If generic bank, upgrade to specific mentioned bank
        if ("bank".equalsIgnoreCase(channel) && targetBank != null) {
            channel = targetBank;
            channelVoice = VoiceEngine.getChannelVoiceName(channel);
        }

        String whoSent = isOutflow ? "Shop" : counterparty;
        String whoReceived = isOutflow ? counterparty : "Shop";
        String purpose;

        if (isOutflow) {
            if (targetBank != null && !"bank".equalsIgnoreCase(targetBank)) {
                String targetBankName = VoiceEngine.getChannelVoiceName(targetBank);
                whoReceived = targetBankName + (!counterparty.equalsIgnoreCase("Recipient") ? " - " + counterparty : "");
                purpose = "Sent to " + targetBankName;
            } else {
                purpose = channelVoice + " Outflow / Sent";
            }
        } else {
            purpose = channelVoice + " Payment Received";
        }

        // 8. CRITICAL: Voice Announcement MUST ALWAYS fire for soundbox alerts!
        if ("inflow".equalsIgnoreCase(type)) {
            VoiceEngine.getInstance(context).speakPayment(channel, amount);
        } else if ("outflow".equalsIgnoreCase(type)) {
            VoiceEngine.getInstance(context).speakOutflow(channel, amount);
        }

        // 9. Save to Local SQLite Database
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        boolean inserted = db.insertTransaction(trxId, amount, type, channel, whoSent, whoReceived, purpose);
        if (inserted) {
            Log.d(TAG, "Recorded " + type + " of Rs. " + amount + " on " + channel + " (TID: " + trxId + ")");
        } else {
            Log.w(TAG, "Transaction already in ledger or updated: " + trxId);
        }

        // 10. Live refresh MainActivity UI
        Intent refreshIntent = new Intent("com.rizzleshop.ACTION_REFRESH_KHATA");
        context.sendBroadcast(refreshIntent);
    }

    public static String detectTransactionType(String message) {
        if (message == null) return null;
        String lower = message.toLowerCase().trim();

        // 1. INFLOW SIGNALS (Money coming into the merchant account)
        boolean hasInflowKeyword =
                lower.contains("received") ||
                lower.contains("recieved") || // JazzCash official typo in SMS
                lower.contains("incoming payment") ||
                lower.contains("has sent you") ||
                lower.contains("sent you") ||
                lower.contains("sent to you") ||
                lower.contains("transferred to your account") ||
                lower.contains("transferred to you") ||
                lower.contains("deposited") ||
                lower.contains("deposit of") ||
                lower.contains("qr payment") ||
                (lower.contains("credited") && !lower.contains("will be credited"));

        // 2. OUTFLOW SIGNALS (Money sent, paid, debited, or transferred out)
        boolean hasOutflowKeyword =
                lower.contains("debited") ||
                lower.contains("debit of") ||
                lower.contains("cash withdrawal") ||
                lower.contains("withdrawn") ||
                lower.contains("will be credited") || // Outbound IBFT standard wording
                lower.contains("paid to") ||
                lower.contains("paid bill") ||
                lower.contains("bill payment") ||
                lower.contains("purchase at") ||
                lower.contains("payment to") ||
                lower.contains("you have sent") ||
                lower.contains("you sent") ||
                lower.contains("has been sent") ||
                lower.contains("is sent") ||
                lower.contains("sent successfully") ||
                lower.contains("transferred to") ||
                lower.contains("fund transfer to") ||
                lower.contains("transfer to");

        // Regex for "sent ... to" or "paid ... to" or "transfer ... to" or "payment ... to"
        boolean sentToPattern = Pattern.compile("\\b(?:sent|send|transferred|transfer|paid|payment)\\b.*\\bto\\b", Pattern.CASE_INSENSITIVE).matcher(lower).find();
        if (sentToPattern && !lower.contains("sent to you") && !lower.contains("transferred to you")) {
            hasOutflowKeyword = true;
        }

        // CONFLICT RESOLUTION:
        // E.g. "Rs 9,177.00 sent to RIZZLE PHARMACY... will be credited within 30 minutes"
        // Outflow takes absolute priority when outbound markers exist!
        if (hasOutflowKeyword && hasInflowKeyword) {
            if (lower.contains("will be credited") || sentToPattern || lower.contains("debited") || lower.contains("you have sent") || lower.contains("you sent")) {
                return "outflow";
            }
        }

        if (hasOutflowKeyword) return "outflow";
        if (hasInflowKeyword) return "inflow";

        return null;
    }

    public static String detectChannel(String sender, String message) {
        String s = sender != null ? sender.toLowerCase().trim() : "";
        String m = message != null ? message.toLowerCase().trim() : "";

        // PRIORITY 1: OFFICIAL SENDER SHORTCODES & HEADER MASKS
        // JazzCash (8558, JC, Mobilink)
        if (s.contains("8558") || s.contains("jazzcash") || s.contains("mobilink") || s.startsWith("jc")) {
            return "jazzcash";
        }
        // Easypaisa (3737, EP, Telenor)
        if (s.contains("3737") || s.contains("easypaisa") || s.contains("telenor") || s.startsWith("ep")) {
            return "easypaisa";
        }
        // Meezan Bank (Official alerts: 8079, 8078, 9060, 8222, MeezanBank, MBL)
        if (s.contains("8079") || s.contains("8078") || s.contains("9060") || s.contains("8222") || s.contains("meezan") || s.contains("mbl")) {
            return "meezan";
        }
        // Bank Alfalah (2252, BAFL, Alfalah)
        if (s.contains("2252") || s.contains("alfalah") || s.contains("bafl")) {
            boolean hasAblWord = Pattern.compile("\\babl\\b|allied\\s*bank", Pattern.CASE_INSENSITIVE).matcher(m).find();
            if (hasAblWord) {
                return "allied";
            }
            return "alfalah";
        }
        // HBL (4250, HBL, Habib)
        if (s.contains("4250") || s.contains("hbl") || s.contains("habib")) {
            return "hbl";
        }
        // UBL (8257, UBL)
        if (s.contains("8257") || s.contains("ubl")) {
            return "ubl";
        }
        // MCB Bank (4722, MCB)
        if (s.contains("4722") || s.contains("mcb")) {
            return "mcb";
        }
        // National Bank of Pakistan (NBP)
        if (s.contains("nbp") || s.contains("national bank")) {
            return "nbp";
        }
        // Bank AL Habib (BAHL)
        if (s.contains("bahl") || s.contains("alhabib") || s.contains("al habib")) {
            return "alhabib";
        }
        // Allied Bank ABL (2252, Allied, ABL)
        if (s.contains("allied") || Pattern.compile("\\babl\\b", Pattern.CASE_INSENSITIVE).matcher(s).find()) {
            return "allied";
        }
        // Faysal Bank (2257, FBL, Faysal)
        if (s.contains("2257") || s.contains("faysal") || s.contains("fbl")) {
            return "faysal";
        }
        // Askari Bank (9225, AKBL, Askari)
        if (s.contains("9225") || s.contains("askari") || s.contains("akbl")) {
            return "askari";
        }
        // Bank of Punjab (8122, BOP, Punjab)
        if (s.contains("8122") || s.contains("bop") || s.contains("punjab")) {
            return "bop";
        }
        // JS Bank (8489, JSBank)
        if (s.contains("8489") || s.contains("jsbank") || s.contains("js bank")) {
            return "jsbank";
        }
        // Soneri Bank (8899, Soneri)
        if (s.contains("8899") || s.contains("soneri")) {
            return "soneri";
        }
        // SadaPay (9922, SadaPay)
        if (s.contains("9922") || s.contains("sadapay")) {
            return "sadapay";
        }
        // NayaPay (8080, NayaPay)
        if (s.contains("8080") || s.contains("nayapay")) {
            return "nayapay";
        }
        // BankIslami (2242, BankIslami)
        if (s.contains("2242") || s.contains("bankislami")) {
            return "bankislami";
        }
        // Al Baraka (9222, AlBaraka)
        if (s.contains("9222") || s.contains("albaraka")) {
            return "albaraka";
        }
        // Dubai Islamic Bank (8282, DIB)
        if (s.contains("8282") || s.contains("dib")) {
            return "dib";
        }
        // Standard Chartered (7227, SCB)
        if (s.contains("7227") || s.contains("scb") || s.contains("standard chartered")) {
            return "scb";
        }

        // PRIORITY 2: SENDER WAS GENERIC / UNIDENTIFIED -> CHECK MESSAGE CONTENT
        // First check which specific bank account or wallet is credited/debited:
        if (m.contains("meezan account") || m.contains("meezan a/c") || m.contains("meezan bank") || m.contains("in your meezan")) return "meezan";
        if (m.contains("bank alfalah") || m.contains("alfalah account") || m.contains("alfalah a/c") || m.contains("bafl")) return "alfalah";
        if (m.contains("habib bank") || m.contains("hbl account") || m.contains("hbl a/c") || m.contains("hbl alert")) return "hbl";
        if (m.contains("ubl digital") || m.contains("ubl account") || m.contains("ubl a/c") || m.contains("ubl alert")) return "ubl";
        if (m.contains("mcb bank") || m.contains("mcb account") || m.contains("mcb a/c")) return "mcb";
        if (m.contains("national bank") || Pattern.compile("\\bnbp\\b", Pattern.CASE_INSENSITIVE).matcher(m).find()) return "nbp";
        if (m.contains("bank al habib") || m.contains("al habib account") || Pattern.compile("\\bbahl\\b", Pattern.CASE_INSENSITIVE).matcher(m).find()) return "alhabib";
        if (m.contains("allied bank") || Pattern.compile("\\babl\\b", Pattern.CASE_INSENSITIVE).matcher(m).find()) return "allied";
        if (m.contains("faysal bank") || m.contains("faysal account")) return "faysal";
        if (m.contains("askari bank") || m.contains("askari account")) return "askari";
        if (m.contains("bank of punjab") || m.contains("bop")) return "bop";
        if (m.contains("js bank") || m.contains("jsbank")) return "jsbank";
        if (m.contains("soneri bank") || m.contains("soneri")) return "soneri";
        if (m.contains("sadapay")) return "sadapay";
        if (m.contains("nayapay")) return "nayapay";
        if (m.contains("bankislami") || m.contains("bank islami")) return "bankislami";
        if (m.contains("al baraka") || m.contains("albaraka")) return "albaraka";
        if (m.contains("dubai islamic") || m.contains("dib alert") || m.contains("dib")) return "dib";
        if (m.contains("standard chartered") || m.contains("scb")) return "scb";

        // If the message has "meezan" anywhere and not "to meezan" (for an incoming alert):
        if (m.contains("meezan") && !m.contains("sent to meezan") && !m.contains("transfer to meezan")) return "meezan";

        boolean isJazzCashCounterparty = Pattern.compile("\\b(?:from|via\\s*raast)\\b.*?\\bjazzcash\\b", Pattern.CASE_INSENSITIVE).matcher(m).find();
        boolean isEasypaisaCounterparty = Pattern.compile("\\b(?:from|via\\s*raast)\\b.*?\\beasypaisa\\b", Pattern.CASE_INSENSITIVE).matcher(m).find();

        // Genuine JazzCash alerts (only if NOT an IBFT/Raast counterparty mention)
        if (m.contains("thank you for using jazzcash") ||
            m.contains("jazzcash mobile account") ||
            m.contains("jazzcash business account") ||
            m.contains("in your jazzcash") ||
            m.contains("till id") ||
            (m.startsWith("txid:") && m.contains("qr payment")) ||
            (m.contains("jazzcash") && !isJazzCashCounterparty)) {
            return "jazzcash";
        }

        // Genuine Easypaisa alerts (only if NOT an IBFT/Raast counterparty mention)
        if (m.contains("in your easypaisa") ||
            m.contains("easypaisa mobile account") ||
            (m.contains("easypaisa") && !isEasypaisaCounterparty)) {
            return "easypaisa";
        }

        if (m.contains("raast") || m.contains("a/c") || m.contains("account")) return "bank";

        return "bank"; // SAFE DEFAULT: Never misattribute mystery SMS to JazzCash!
    }

    public static String detectMentionedBank(String text) {
        if (text == null) return null;
        String t = text.toLowerCase();
        if (t.contains("meezan")) return "meezan";
        if (t.contains("ubl")) return "ubl";
        if (t.contains("hbl") || t.contains("habib bank limited")) return "hbl";
        if (t.contains("alfalah") || t.contains("bafl")) return "alfalah";
        if (t.contains("national bank") || Pattern.compile("\\bnbp\\b", Pattern.CASE_INSENSITIVE).matcher(t).find()) return "nbp";
        if (t.contains("bank al habib") || t.contains("al habib") || Pattern.compile("\\bbahl\\b", Pattern.CASE_INSENSITIVE).matcher(t).find()) return "alhabib";
        if (t.contains("mcb")) return "mcb";
        if (t.contains("allied") || Pattern.compile("\\babl\\b", Pattern.CASE_INSENSITIVE).matcher(t).find()) return "allied";
        if (t.contains("askari") || t.contains("akbl")) return "askari";
        if (t.contains("bop") || t.contains("punjab")) return "bop";
        if (t.contains("faysal") || t.contains("fbl")) return "faysal";
        if (t.contains("jsbank") || t.contains("js bank")) return "jsbank";
        if (t.contains("soneri")) return "soneri";
        if (t.contains("sadapay")) return "sadapay";
        if (t.contains("nayapay")) return "nayapay";
        if (t.contains("bankislami")) return "bankislami";
        if (t.contains("albaraka")) return "albaraka";
        if (t.contains("dib") || t.contains("dubai islamic")) return "dib";
        if (t.contains("standard chartered") || t.contains("scb")) return "scb";
        return null;
    }

    public static double extractAmount(String msg) {
        if (msg == null) return 0.0;
        try {
            // Pattern 1: Action followed by Rs/PKR
            Pattern p1 = Pattern.compile("(?:Received|recieved|sent|Transferred|Payment of|credited with|credited by|credited for|withdrawal of|debited with|debited by|debited for|debited from|deposited)\\s*(?:Rs\\.?|PKR)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
            Matcher m1 = p1.matcher(msg);
            if (m1.find()) {
                double val = parseCleanDouble(m1.group(1));
                if (val > 0.40) return val;
            }

            // Pattern 2: Rs/PKR followed by number and action
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
