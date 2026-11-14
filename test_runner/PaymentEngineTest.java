package test_runner;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentEngineTest {

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
        if (s.contains("allied") || s.contains("abl")) {
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
        if (m.contains("jazzcash") || m.contains("till id")) return "jazzcash";
        if (m.contains("easypaisa")) return "easypaisa";
        if (m.contains("meezan bank") || m.contains("meezan account") || m.contains("meezan a/c") || m.contains("meezan")) return "meezan";
        if (m.contains("bank alfalah") || m.contains("alfalah") || m.contains("bafl")) return "alfalah";
        if (m.contains("habib bank") || m.contains("hbl alert") || m.contains("hbl a/c") || m.contains("hbl")) return "hbl";
        if (m.contains("ubl digital") || m.contains("ubl alert") || m.contains("ubl a/c") || m.contains("ubl")) return "ubl";
        if (m.contains("mcb bank") || m.contains("mcb alert") || m.contains("mcb")) return "mcb";
        if (m.contains("allied bank") || Pattern.compile("\\babl\\b", Pattern.CASE_INSENSITIVE).matcher(m).find() || m.contains("allied")) return "allied";
        if (m.contains("faysal bank") || m.contains("faysal")) return "faysal";
        if (m.contains("askari bank") || m.contains("askari")) return "askari";
        if (m.contains("bank of punjab") || m.contains("bop")) return "bop";
        if (m.contains("js bank") || m.contains("jsbank")) return "jsbank";
        if (m.contains("soneri bank") || m.contains("soneri")) return "soneri";
        if (m.contains("sadapay")) return "sadapay";
        if (m.contains("nayapay")) return "nayapay";
        if (m.contains("bankislami") || m.contains("bank islami")) return "bankislami";
        if (m.contains("al baraka") || m.contains("albaraka")) return "albaraka";
        if (m.contains("dubai islamic") || m.contains("dib alert") || m.contains("dib")) return "dib";
        if (m.contains("standard chartered") || m.contains("scb")) return "scb";

        if (m.contains("raast") || m.contains("a/c") || m.contains("account")) return "bank";

        return "bank"; // SAFE DEFAULT: Never misattribute mystery SMS to JazzCash!
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

            // Pattern 3: Bank debit/credit phrasing
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
            // ignore
        }
        return 0.0;
    }

    private static double parseCleanDouble(String val) {
        if (val == null) return 0.0;
        return Double.parseDouble(val.replace(",", "").trim());
    }

    public static String getVoiceAnnouncement(String channel, String type, double amount) {
        String name = getChannelVoiceName(channel);
        long amt = (long) amount;
        if ("outflow".equalsIgnoreCase(type)) {
            return name + " se " + amt + " rupay send ho gaye hain";
        } else {
            return name + " par " + amt + " rupay receive ho gaye hain";
        }
    }

    public static String getChannelVoiceName(String channel) {
        if (channel == null) return "Bank";
        switch (channel.toLowerCase().trim()) {
            case "jazzcash": return "JazzCash";
            case "easypaisa": return "Easypaisa";
            case "meezan": return "Meezan Bank";
            case "alfalah": return "Bank Alfalah";
            case "hbl": return "HBL";
            case "ubl": return "UBL";
            case "mcb": return "MCB Bank";
            case "nbp": return "National Bank";
            case "alhabib":
            case "bahl": return "Bank AL Habib";
            case "allied": return "Allied Bank";
            case "faysal": return "Faysal Bank";
            case "askari": return "Askari Bank";
            case "bop": return "Bank of Punjab";
            case "jsbank": return "JS Bank";
            case "soneri": return "Soneri Bank";
            case "sadapay": return "SadaPay";
            case "nayapay": return "NayaPay";
            case "bankislami": return "BankIslami";
            case "albaraka": return "Al Baraka Bank";
            case "dib": return "Dubai Islamic Bank";
            case "scb": return "Standard Chartered";
            case "bank": return "Bank";
            default:
                if (channel.length() > 1) {
                    return Character.toUpperCase(channel.charAt(0)) + channel.substring(1);
                }
                return channel.toUpperCase();
        }
    }

    static class TestCase {
        String title;
        String sender;
        String message;
        String expectedChannel;
        String expectedType;
        double expectedAmount;
        String expectedVoice;

        TestCase(String title, String sender, String message, String expectedChannel, String expectedType, double expectedAmount, String expectedVoice) {
            this.title = title;
            this.sender = sender;
            this.message = message;
            this.expectedChannel = expectedChannel;
            this.expectedType = expectedType;
            this.expectedAmount = expectedAmount;
            this.expectedVoice = expectedVoice;
        }
    }

    public static void main(String[] args) {
        System.out.println("===================================================================");
        System.out.println("   PAYVOX COMPLETE FINANCIAL SMS PARSING & VOICE TEST SUITE v2.9");
        System.out.println("===================================================================\n");

        List<TestCase> tests = new ArrayList<>();

        // 1. JazzCash Sent to Meezan Bank (Outflow)
        tests.add(new TestCase("1. JazzCash Sent to Meezan Bank",
                "8558",
                "Rs 9,177.00 sent to RIZZLE PHARMACY, Meezan A/C: 22020108012057 will be credited within 30 minutes. Fee: Rs 0.00, Balance: Rs 480.55. TID: 730069976433",
                "jazzcash", "outflow", 9177.0, "JazzCash se 9177 rupay send ho gaye hain"));

        // 2. Meezan Bank Official Short Code 8079 Received via Raast (Inflow)
        tests.add(new TestCase("2. Meezan Bank Received via 8079 (Inflow)",
                "8079",
                "Dear Customer, A/C *12057 credited with PKR 9,177.00 on 14-Nov-2026 via Raast Ref 730069976433 Avail Bal PKR 15,000.00",
                "meezan", "inflow", 9177.0, "Meezan Bank par 9177 rupay receive ho gaye hain"));

        // 3. Meezan Bank Official Short Code 8078 Received (Inflow)
        tests.add(new TestCase("3. Meezan Bank Received via 8078 (Inflow)",
                "8078",
                "Dear Customer, your Account 02010108012057 has been credited with Rs. 10,000.00 on 14-Nov-2026 via IBFT Ref 99881122. Avail Bal: Rs. 25,000.00",
                "meezan", "inflow", 10000.0, "Meezan Bank par 10000 rupay receive ho gaye hain"));

        // 4. Meezan Bank Sent / Debit from 8079 (Outflow)
        tests.add(new TestCase("4. Meezan Bank Sent / Debit via 8079 (Outflow)",
                "8079",
                "Dear Customer, your Account 02010108012057 has been debited by Rs. 5,000.00 on 14-Nov-2026. Avail Bal: Rs. 20,000.00. Ref: 554433",
                "meezan", "outflow", 5000.0, "Meezan Bank se 5000 rupay send ho gaye hain"));

        // 5. JazzCash Sent to Mobile (Wallet-to-Wallet)
        tests.add(new TestCase("5. JazzCash Sent to Mobile (Outflow)",
                "8558",
                "You have successfully sent Rs. 1,000.00 to 03001234567. Fee: Rs. 0.00. Balance: Rs. 4,500.00. TID: 1234567890",
                "jazzcash", "outflow", 1000.0, "JazzCash se 1000 rupay send ho gaye hain"));

        // 6. JazzCash Payment to Till ID (Merchant Outflow)
        tests.add(new TestCase("6. JazzCash Payment to Till ID (Outflow)",
                "8558",
                "Payment of Rs. 350.00 to Till ID 0012345 was successful. Fee: Rs. 0.00. Balance: Rs. 4,150.00. TID: 1234567891",
                "jazzcash", "outflow", 350.0, "JazzCash se 350 rupay send ho gaye hain"));

        // 7. JazzCash Received via Raast (Inflow)
        tests.add(new TestCase("7. JazzCash Received via Raast (Inflow)",
                "8558",
                "Dear Customer, Thank you for using JazzCash. You have Recieved Rs 60.00 via RAAST. Fee for this transaction was Rs 0.15 . Your Transaction ID is 730123293206.",
                "jazzcash", "inflow", 60.0, "JazzCash par 60 rupay receive ho gaye hain"));

        // 8. JazzCash QR Payment Received (Inflow)
        tests.add(new TestCase("8. JazzCash QR Payment Received (Inflow)",
                "8558",
                "TxID:730123293206. QR Payment Rs 60 received from 03001234567",
                "jazzcash", "inflow", 60.0, "JazzCash par 60 rupay receive ho gaye hain"));

        // 9. Bank Alfalah Received from 2252 (Inflow)
        tests.add(new TestCase("9. Bank Alfalah Received via 2252 (Inflow)",
                "2252",
                "Dear Customer, your Account 1005... has been credited with PKR 3,000.00 on 14-Nov-2026. Available balance: PKR 12,000.00",
                "alfalah", "inflow", 3000.0, "Bank Alfalah par 3000 rupay receive ho gaye hain"));

        // 10. Bank Alfalah Sent / Debit (Outflow)
        tests.add(new TestCase("10. Bank Alfalah Sent / Debit (Outflow)",
                "BankAlfalah",
                "Dear Customer, your Account 1005... is debited with PKR 1,500.00 on 14-Nov-2026 for transfer to 03001234567. Balance: PKR 10,500.00",
                "alfalah", "outflow", 1500.0, "Bank Alfalah se 1500 rupay send ho gaye hain"));

        // 11. HBL Received from 4250 (Inflow)
        tests.add(new TestCase("11. HBL Received via 4250 (Inflow)",
                "4250",
                "Dear Customer, PKR 7,500.00 has been credited to your HBL Account 0042... on 14-Nov-2026 via Raast. Available Balance: PKR 45,000.00",
                "hbl", "inflow", 7500.0, "HBL par 7500 rupay receive ho gaye hain"));

        // 12. Easypaisa Sent (Outflow)
        tests.add(new TestCase("12. Easypaisa Sent (Outflow)",
                "3737",
                "Rs. 800.00 sent to ALI KHAN (03451234567) successfully. Fee: Rs. 0.00. Balance: Rs. 2,200. TID: 99881122",
                "easypaisa", "outflow", 800.0, "Easypaisa se 800 rupay send ho gaye hain"));

        // 13. Easypaisa Received (Inflow)
        tests.add(new TestCase("13. Easypaisa Received (Inflow)",
                "3737",
                "You have received Rs. 800.00 from ALI KHAN (03451234567). Balance: Rs. 3,000. TID: 99881123",
                "easypaisa", "inflow", 800.0, "Easypaisa par 800 rupay receive ho gaye hain"));

        // 14. Standard Chartered from 7227 (Inflow)
        tests.add(new TestCase("14. Standard Chartered Received (Inflow)",
                "7227",
                "Your SCB account has been credited with PKR 12,000.00 on 14-Nov-2026 Ref 887766. Balance: PKR 80,000.00",
                "scb", "inflow", 12000.0, "Standard Chartered par 12000 rupay receive ho gaye hain"));

        // 15. Raast from unidentified header (Safe Bank Fallback)
        tests.add(new TestCase("15. Unidentified Sender Raast Credit (Bank Inflow)",
                "Alerts",
                "Dear Customer, A/C *9988 credited with PKR 2,000.00 via Raast from 03001234567 Ref 556677",
                "bank", "inflow", 2000.0, "Bank par 2000 rupay receive ho gaye hain"));

        // 16. National Bank of Pakistan (NBP) (Inflow)
        tests.add(new TestCase("16. National Bank of Pakistan (NBP) Inflow",
                "NBP",
                "Dear Customer, your NBP Account 0123... has been credited with PKR 4,500.00 on 14-Nov-2026. Available balance: PKR 18,000.00",
                "nbp", "inflow", 4500.0, "National Bank par 4500 rupay receive ho gaye hain"));

        // 17. Bank AL Habib (BAHL) (Inflow)
        tests.add(new TestCase("17. Bank AL Habib (BAHL) Inflow",
                "BAHL",
                "Dear Customer, your Bank AL Habib Account 1010... has been credited with PKR 6,200.00 via Raast. Available balance: PKR 22,000.00",
                "alhabib", "inflow", 6200.0, "Bank AL Habib par 6200 rupay receive ho gaye hain"));

        int passed = 0;
        int failed = 0;

        for (TestCase tc : tests) {
            String ch = detectChannel(tc.sender, tc.message);
            String ty = detectTransactionType(tc.message);
            double amt = extractAmount(tc.message);
            String voice = getVoiceAnnouncement(ch, ty, amt);

            boolean chMatch = tc.expectedChannel.equalsIgnoreCase(ch);
            boolean tyMatch = tc.expectedType.equalsIgnoreCase(ty);
            boolean amtMatch = Math.abs(tc.expectedAmount - amt) < 0.01;
            boolean voiceMatch = tc.expectedVoice.equalsIgnoreCase(voice);

            if (chMatch && tyMatch && amtMatch && voiceMatch) {
                System.out.println("PASS: " + tc.title);
                System.out.println("   Channel: " + ch + " | Type: " + ty + " | Amount: " + amt);
                System.out.println("   Voice: \"" + voice + "\"\n");
                passed++;
            } else {
                System.out.println("FAIL: " + tc.title);
                System.out.println("   Channel: " + ch + " (Expected: " + tc.expectedChannel + ")");
                System.out.println("   Type: " + ty + " (Expected: " + tc.expectedType + ")");
                System.out.println("   Amount: " + amt + " (Expected: " + tc.expectedAmount + ")");
                System.out.println("   Voice: \"" + voice + "\"");
                System.out.println("   Expected Voice: \"" + tc.expectedVoice + "\"\n");
                failed++;
            }
        }

        System.out.println("===================================================================");
        System.out.println("   TEST RESULTS: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("===================================================================");
    }
}
