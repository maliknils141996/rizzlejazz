package test_runner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentEngineTest {

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

    public static String detectTransactionType(String message) {
        if (message == null) return "inflow";
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

        // Regex patterns:
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

        // Conflict resolution: If both match, check priority
        if (isOutflow && isInflow) {
            // E.g. "Rs 9,177.00 sent to RIZZLE PHARMACY... will be credited within 30 mins"
            if (sentPattern || transferPattern || paidToPattern || youSentPattern || lower.contains("debited") || lower.contains("will be credited")) {
                return "outflow";
            }
        }

        if (isOutflow) return "outflow";
        if (isInflow) return "inflow";

        return "inflow";
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

            // Pattern 4: "QR Payment Rs 60" or "Fund Transfer of Rs. 5,000"
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
            case "allied":
            case "abl": return "Allied Bank";
            case "faysal": return "Faysal Bank";
            case "askari": return "Askari Bank";
            case "bop": return "Bank of Punjab";
            case "jsbank":
            case "js": return "JS Bank";
            case "soneri": return "Soneri Bank";
            case "sadapay": return "SadaPay";
            case "nayapay": return "NayaPay";
            case "bankislami": return "BankIslami";
            case "albaraka": return "Al Baraka Bank";
            case "dib": return "Dubai Islamic Bank";
            case "scb": return "Standard Chartered";
            default:
                return "Bank";
        }
    }

    public static void main(String[] args) {
        System.out.println("===================================================================");
        System.out.println("   PAYVOX COMPLETE FINANCIAL SMS PARSING & VOICE TEST SUITE");
        System.out.println("===================================================================\n");

        TestCase[] cases = new TestCase[]{
            // 1. JazzCash Sent to Bank (Image 2)
            new TestCase("1. JazzCash Sent to Meezan Bank",
                "8558",
                "Rs 9,177.00 sent to RIZZLE PHARMACY, Meezan A/C: 22020108012057 will be credited within 30 minutes. Fee: Rs 0.00, Balance: Rs 480.55. TID: 730069976433",
                "jazzcash", "outflow", 9177.0, "JazzCash se 9177 rupay send ho gaye hain"),

            // 2. JazzCash Sent to Mobile
            new TestCase("2. JazzCash Sent to Mobile (Wallet-to-Wallet)",
                "8558",
                "You have successfully sent Rs. 1,000.00 to 03001234567 (AHMED ALI). Fee: Rs 0.00. Balance: Rs 4,500.00. TID: 730129849201",
                "jazzcash", "outflow", 1000.0, "JazzCash se 1000 rupay send ho gaye hain"),

            // 3. JazzCash Payment to Till ID
            new TestCase("3. JazzCash Payment to Till ID (Merchant Outflow)",
                "8558",
                "Payment of Rs. 350.00 to Till ID 0012345 (RIZZLE SHOP) is successful. Fee: Rs. 0.00. Balance: Rs 4,150. TID: 730129851122",
                "jazzcash", "outflow", 350.0, "JazzCash se 350 rupay send ho gaye hain"),

            // 4. JazzCash Received via Raast (Image 1)
            new TestCase("4. JazzCash Received via Raast (Inflow)",
                "8558",
                "Dear Customer, Thank you for using JazzCash. You have Recieved Rs 60.00 via RAAST. Fee for this transaction was Rs 0.15. Your Transaction ID is 730123293206.",
                "jazzcash", "inflow", 60.0, "JazzCash par 60 rupay receive ho gaye hain"),

            // 5. JazzCash QR Payment Received (Image 1)
            new TestCase("5. JazzCash QR Payment Received (Inflow)",
                "8558",
                "TxID:730123293206. QR Payment Rs 60 received from 03001234567",
                "jazzcash", "inflow", 60.0, "JazzCash par 60 rupay receive ho gaye hain"),

            // 6. Meezan Bank Sent / Debit (Rs format)
            new TestCase("6. Meezan Bank Sent / Debit (Transferred out)",
                "8222",
                "Dear Customer, your Account 02010108012057 has been debited by Rs. 5,000.00 on 14-Nov-2026. Transferred to JazzCash A/C 03001234567. Ref: 987654321. Avail Bal: Rs. 45,000.00",
                "meezan", "outflow", 5000.0, "Meezan Bank se 5000 rupay send ho gaye hain"),

            // 7. Meezan Bank Sent / Debit (PKR format)
            new TestCase("7. Meezan Bank Sent / Debit (PKR format)",
                "MeezanBank",
                "Dear Customer, your Account 02010108012057 is debited with PKR 2,500.00 on 14-Nov-2026. Ref: 87654321. Avail Bal: PKR 42,500.00",
                "meezan", "outflow", 2500.0, "Meezan Bank se 2500 rupay send ho gaye hain"),

            // 8. Meezan Bank Received / Credit
            new TestCase("8. Meezan Bank Received / Credit (Inflow)",
                "8222",
                "Dear Customer, your Account 02010108012057 has been credited with Rs. 10,000.00 on 14-Nov-2026 from USMAN ALI via Raast. Ref: 11223344. Avail Bal: Rs. 52,500.00",
                "meezan", "inflow", 10000.0, "Meezan Bank par 10000 rupay receive ho gaye hain"),

            // 9. Bank Alfalah Sent / Debit
            new TestCase("9. Bank Alfalah Sent / Debit (Outflow)",
                "2252",
                "Alfalah Alert: PKR 1,500.00 debited from Account ending 1234 for transfer to Meezan Bank. Ref: 55443322. Balance: PKR 12,000",
                "alfalah", "outflow", 1500.0, "Bank Alfalah se 1500 rupay send ho gaye hain"),

            // 10. Bank Alfalah Received / Credit
            new TestCase("10. Bank Alfalah Received / Credit (Inflow)",
                "Alfalah",
                "Alfalah Alert: PKR 3,000.00 credited to Account ending 1234 from HAMZA KHAN. Ref: 99887766. Balance: PKR 15,000",
                "alfalah", "inflow", 3000.0, "Bank Alfalah par 3000 rupay receive ho gaye hain"),

            // 11. HBL Received / Credit
            new TestCase("11. HBL Received / Credit (Inflow)",
                "4250",
                "HBL Alert: Account 1234 is credited by Rs. 7,500.00 on 14-Nov-2026. TID: 44556677",
                "hbl", "inflow", 7500.0, "HBL par 7500 rupay receive ho gaye hain"),

            // 12. Easypaisa Sent (Outflow)
            new TestCase("12. Easypaisa Sent (Outflow)",
                "3737",
                "You have successfully sent Rs. 800.00 to 03451234567. Fee: Rs. 0.00. Balance: Rs. 2,200. TID: 99881122",
                "easypaisa", "outflow", 800.0, "Easypaisa se 800 rupay send ho gaye hain"),

            // 13. Easypaisa Received (Inflow)
            new TestCase("13. Easypaisa Received (Inflow)",
                "3737",
                "You have received Rs. 800.00 from ALI KHAN (03451234567). Balance: Rs. 3,000. TID: 99881123",
                "easypaisa", "inflow", 800.0, "Easypaisa par 800 rupay receive ho gaye hain")
        };

        int passed = 0;
        int failed = 0;

        for (TestCase tc : cases) {
            String ch = detectChannel(tc.sender, tc.message);
            String type = detectTransactionType(tc.message);
            double amt = extractAmount(tc.message);
            String voice = getVoiceAnnouncement(ch, type, amt);

            boolean chOk = ch.equalsIgnoreCase(tc.expectedChannel);
            boolean typeOk = type.equalsIgnoreCase(tc.expectedType);
            boolean amtOk = Math.abs(amt - tc.expectedAmount) < 0.01;
            boolean voiceOk = voice.equalsIgnoreCase(tc.expectedVoice);

            if (chOk && typeOk && amtOk && voiceOk) {
                System.out.println("✅ PASS: " + tc.title);
                System.out.println("   Channel: " + ch + " | Type: " + type + " | Amount: " + amt);
                System.out.println("   Voice: \"" + voice + "\"\n");
                passed++;
            } else {
                System.out.println("❌ FAIL: " + tc.title);
                System.out.println("   Expected: [" + tc.expectedChannel + ", " + tc.expectedType + ", " + tc.expectedAmount + ", \"" + tc.expectedVoice + "\"]");
                System.out.println("   Got:      [" + ch + ", " + type + ", " + amt + ", \"" + voice + "\"]\n");
                failed++;
            }
        }

        System.out.println("===================================================================");
        System.out.println("   TEST RESULTS: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("===================================================================");
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
}
