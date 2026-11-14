package com.jazzcash.notifier;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfStatementGenerator {

    private static final int PAGE_WIDTH = 595;   // Standard A4 at 72dpi
    private static final int PAGE_HEIGHT = 842;  // Standard A4 at 72dpi
    private static final int MARGIN = 36;        // 0.5 inch margins
    private static final int CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2);

    private static final DecimalFormat CURRENCY_FMT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat INT_FMT = new DecimalFormat("#,##0");

    public static File generateStatement(
            Context context,
            String periodTitle,
            String dateRangeLabel,
            String bankFilterName,
            DatabaseHelper.KhataStats stats,
            List<DatabaseHelper.TransactionItem> transactions
    ) throws IOException {

        PdfDocument document = new PdfDocument();
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        SimpleDateFormat genFmt = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        String generationTime = genFmt.format(new Date());

        int pageNum = 1;
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        int y = MARGIN;

        // 1. Draw Header Banner
        y = drawHeaderBanner(canvas, paint, y, periodTitle, dateRangeLabel, bankFilterName, generationTime);

        // 2. Draw Financial Summary KPI Cards
        y = drawSummaryKpis(canvas, paint, y, stats);

        // 3. Draw Transactions Table Header
        y = drawTableHeader(canvas, paint, y);

        // 4. Draw Transaction Rows
        int rowIndex = 0;
        int rowHeight = 24;
        int footerHeight = 40;
        int maxY = PAGE_HEIGHT - MARGIN - footerHeight;

        for (DatabaseHelper.TransactionItem tx : transactions) {
            rowIndex++;

            // Check if row exceeds page bounds
            if (y + rowHeight > maxY) {
                drawPageFooter(canvas, paint, pageNum);
                document.finishPage(page);

                pageNum++;
                pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create();
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();

                // Continuation Header
                y = MARGIN;
                y = drawContinuationHeader(canvas, paint, y, periodTitle, bankFilterName);
                y = drawTableHeader(canvas, paint, y);
            }

            drawTransactionRow(canvas, paint, y, rowIndex, tx);
            y += rowHeight;
        }

        // Draw final page footer
        drawPageFooter(canvas, paint, pageNum);
        document.finishPage(page);

        // Save PDF to Documents directory
        File docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (docsDir == null) {
            docsDir = context.getFilesDir();
        }
        if (!docsDir.exists()) {
            docsDir.mkdirs();
        }

        SimpleDateFormat fileTimeFmt = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String safeBank = (bankFilterName != null ? bankFilterName.replaceAll("[^a-zA-Z0-9]", "_") : "All");
        String filename = "PayVox_Statement_" + safeBank + "_" + fileTimeFmt.format(new Date()) + ".pdf";
        File pdfFile = new File(docsDir, filename);

        FileOutputStream fos = new FileOutputStream(pdfFile);
        document.writeTo(fos);
        fos.flush();
        fos.close();
        document.close();

        return pdfFile;
    }

    private static int drawHeaderBanner(Canvas canvas, Paint paint, int startY,
                                        String periodTitle, String dateRangeLabel,
                                        String bankFilterName, String genTime) {
        int bannerHeight = 72;
        RectF bannerRect = new RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + bannerHeight);

        // Gradient or deep indigo/purple background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#4338CA")); // Deep Indigo
        canvas.drawRoundRect(bannerRect, 10, 10, paint);

        // App Brand Title
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(17f);
        canvas.drawText("PAYVOX  •  SMART KHATA STATEMENT", MARGIN + 14, startY + 28, paint);

        // Filter / Period Subtitle
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(10f);
        paint.setColor(Color.parseColor("#E0E7FF"));
        String sub = "Filter: " + (bankFilterName != null ? bankFilterName.toUpperCase() : "ALL") +
                "  |  Period: " + (periodTitle != null ? periodTitle.toUpperCase() : "ALL") +
                " (" + (dateRangeLabel != null ? dateRangeLabel : "") + ")";
        canvas.drawText(sub, MARGIN + 14, startY + 48, paint);

        // Right side: Generation time & Verification badge
        paint.setTextSize(8.5f);
        paint.setColor(Color.parseColor("#C7D2FE"));
        String genText = "Generated: " + genTime;
        float genWidth = paint.measureText(genText);
        canvas.drawText(genText, PAGE_WIDTH - MARGIN - 14 - genWidth, startY + 26, paint);

        String officialBadge = "Verified Business Khata Ledger";
        float badgeWidth = paint.measureText(officialBadge);
        paint.setColor(Color.parseColor("#A5B4FC"));
        canvas.drawText(officialBadge, PAGE_WIDTH - MARGIN - 14 - badgeWidth, startY + 46, paint);

        return startY + bannerHeight + 12;
    }

    private static int drawContinuationHeader(Canvas canvas, Paint paint, int startY,
                                              String periodTitle, String bankFilterName) {
        int height = 36;
        RectF rect = new RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + height);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#4338CA"));
        canvas.drawRoundRect(rect, 6, 6, paint);

        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(12f);
        canvas.drawText("PAYVOX STATEMENT (CONTINUATION)", MARGIN + 12, startY + 23, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(9f);
        paint.setColor(Color.parseColor("#E0E7FF"));
        String rightText = (bankFilterName != null ? bankFilterName.toUpperCase() : "ALL") + " • " + periodTitle;
        float rightW = paint.measureText(rightText);
        canvas.drawText(rightText, PAGE_WIDTH - MARGIN - 12 - rightW, startY + 23, paint);

        return startY + height + 10;
    }

    private static int drawSummaryKpis(Canvas canvas, Paint paint, int startY, DatabaseHelper.KhataStats stats) {
        int cardHeight = 48;
        int spacing = 8;
        int cardWidth = (CONTENT_WIDTH - (spacing * 2)) / 3;

        // Card 1: Inflow (Received)
        int x1 = MARGIN;
        RectF r1 = new RectF(x1, startY, x1 + cardWidth, startY + cardHeight);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#F0FDF4")); // Very light green
        canvas.drawRoundRect(r1, 6, 6, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.parseColor("#BBF7D0"));
        paint.setStrokeWidth(1f);
        canvas.drawRoundRect(r1, 6, 6, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(8.5f);
        paint.setColor(Color.parseColor("#166534"));
        canvas.drawText("TOTAL RECEIVED (IN)", x1 + 10, startY + 16, paint);
        paint.setTextSize(12f);
        paint.setColor(Color.parseColor("#15803D"));
        canvas.drawText("+ Rs. " + INT_FMT.format(stats.totalInflow), x1 + 10, startY + 34, paint);
        paint.setTextSize(8f);
        paint.setColor(Color.parseColor("#166534"));
        canvas.drawText("(" + stats.inflowCount + " txns)", x1 + cardWidth - 45, startY + 34, paint);

        // Card 2: Outflow (Sent)
        int x2 = x1 + cardWidth + spacing;
        RectF r2 = new RectF(x2, startY, x2 + cardWidth, startY + cardHeight);
        paint.setColor(Color.parseColor("#FEF2F2")); // Very light red
        canvas.drawRoundRect(r2, 6, 6, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.parseColor("#FECACA"));
        canvas.drawRoundRect(r2, 6, 6, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(8.5f);
        paint.setColor(Color.parseColor("#991B1B"));
        canvas.drawText("TOTAL SENT (OUT)", x2 + 10, startY + 16, paint);
        paint.setTextSize(12f);
        paint.setColor(Color.parseColor("#DC2626"));
        canvas.drawText("- Rs. " + INT_FMT.format(stats.totalOutflow), x2 + 10, startY + 34, paint);
        paint.setTextSize(8f);
        paint.setColor(Color.parseColor("#991B1B"));
        canvas.drawText("(" + stats.outflowCount + " txns)", x2 + cardWidth - 45, startY + 34, paint);

        // Card 3: Net Khata Balance
        int x3 = x2 + cardWidth + spacing;
        RectF r3 = new RectF(x3, startY, x3 + cardWidth, startY + cardHeight);
        paint.setColor(Color.parseColor("#F5F3FF")); // Very light purple
        canvas.drawRoundRect(r3, 6, 6, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.parseColor("#DDD6FE"));
        canvas.drawRoundRect(r3, 6, 6, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(8.5f);
        paint.setColor(Color.parseColor("#5B21B6"));
        canvas.drawText("NET KHATA BALANCE", x3 + 10, startY + 16, paint);
        paint.setTextSize(12f);
        String netSign = stats.netBalance >= 0 ? "+ " : "- ";
        paint.setColor(stats.netBalance >= 0 ? Color.parseColor("#4338CA") : Color.parseColor("#DC2626"));
        canvas.drawText(netSign + "Rs. " + CURRENCY_FMT.format(Math.abs(stats.netBalance)), x3 + 10, startY + 34, paint);

        return startY + cardHeight + 14;
    }

    private static int drawTableHeader(Canvas canvas, Paint paint, int startY) {
        int h = 22;
        RectF hr = new RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + h);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#1E293B")); // Dark slate
        canvas.drawRoundRect(hr, 4, 4, paint);

        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(8f);

        int textY = startY + 15;
        canvas.drawText("#", MARGIN + 6, textY, paint);
        canvas.drawText("DATE & TIME", MARGIN + 26, textY, paint);
        canvas.drawText("BANK / WALLET", MARGIN + 115, textY, paint);
        canvas.drawText("TYPE", MARGIN + 195, textY, paint);
        canvas.drawText("COUNTERPARTY / PURPOSE", MARGIN + 235, textY, paint);
        canvas.drawText("TID", MARGIN + 380, textY, paint);

        String amtHeader = "AMOUNT (PKR)";
        float amtW = paint.measureText(amtHeader);
        canvas.drawText(amtHeader, PAGE_WIDTH - MARGIN - 8 - amtW, textY, paint);

        return startY + h + 2;
    }

    private static void drawTransactionRow(Canvas canvas, Paint paint, int startY, int index, DatabaseHelper.TransactionItem tx) {
        int h = 24;
        RectF rr = new RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + h);

        // Zebra background
        paint.setStyle(Paint.Style.FILL);
        if (index % 2 == 0) {
            paint.setColor(Color.parseColor("#F8FAFC"));
            canvas.drawRect(rr, paint);
        } else {
            paint.setColor(Color.WHITE);
            canvas.drawRect(rr, paint);
        }

        // Bottom light border
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.parseColor("#E2E8F0"));
        paint.setStrokeWidth(0.5f);
        canvas.drawLine(MARGIN, startY + h, PAGE_WIDTH - MARGIN, startY + h, paint);

        // Cell Text
        paint.setStyle(Paint.Style.FILL);
        int textY = startY + 16;

        // #
        paint.setColor(Color.parseColor("#64748B"));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(7.5f);
        canvas.drawText(String.valueOf(index), MARGIN + 6, textY, paint);

        // Date & Time
        paint.setColor(Color.parseColor("#334155"));
        String timeStr = tx.createdAt != null ? tx.createdAt : "";
        if (timeStr.length() > 16) {
            timeStr = timeStr.substring(5, 16); // e.g. "11-14 14:15"
        }
        canvas.drawText(timeStr, MARGIN + 26, textY, paint);

        // Bank / Channel Badge
        String ch = VoiceEngine.getChannelVoiceName(tx.channel).toUpperCase();
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setColor(Color.parseColor("#0F172A"));
        canvas.drawText(truncateText(paint, ch, 72), MARGIN + 115, textY, paint);

        // Type (IN / OUT)
        boolean isInflow = "inflow".equalsIgnoreCase(tx.type);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        if (isInflow) {
            paint.setColor(Color.parseColor("#15803D")); // Green
            canvas.drawText("↓ IN", MARGIN + 195, textY, paint);
        } else {
            paint.setColor(Color.parseColor("#DC2626")); // Red
            canvas.drawText("↑ OUT", MARGIN + 195, textY, paint);
        }

        // Counterparty / Purpose
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setColor(Color.parseColor("#1E293B"));
        String party = isInflow ? ("From: " + (tx.sender != null ? tx.sender : "Customer"))
                : ("To: " + (tx.recipient != null ? tx.recipient : "Beneficiary"));
        if (tx.purpose != null && !tx.purpose.isEmpty() && !"Payment".equalsIgnoreCase(tx.purpose)) {
            party += " (" + tx.purpose + ")";
        }
        canvas.drawText(truncateText(paint, party, 140), MARGIN + 235, textY, paint);

        // TID
        paint.setColor(Color.parseColor("#64748B"));
        String tid = tx.trxId != null ? tx.trxId : "N/A";
        if (tid.contains("_")) {
            tid = tid.substring(tid.lastIndexOf('_') + 1);
        }
        canvas.drawText(truncateText(paint, tid, 65), MARGIN + 380, textY, paint);

        // Amount
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(8.5f);
        if (isInflow) {
            paint.setColor(Color.parseColor("#15803D"));
            String amtStr = "+ " + CURRENCY_FMT.format(tx.amount);
            float amtW = paint.measureText(amtStr);
            canvas.drawText(amtStr, PAGE_WIDTH - MARGIN - 8 - amtW, textY, paint);
        } else {
            paint.setColor(Color.parseColor("#DC2626"));
            String amtStr = "- " + CURRENCY_FMT.format(tx.amount);
            float amtW = paint.measureText(amtStr);
            canvas.drawText(amtStr, PAGE_WIDTH - MARGIN - 8 - amtW, textY, paint);
        }
    }

    private static void drawPageFooter(Canvas canvas, Paint paint, int pageNum) {
        int footerY = PAGE_HEIGHT - MARGIN - 6;

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.parseColor("#CBD5E1"));
        paint.setStrokeWidth(0.5f);
        canvas.drawLine(MARGIN, footerY - 12, PAGE_WIDTH - MARGIN, footerY - 12, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#64748B"));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(7.5f);

        canvas.drawText("PayVox Intelligent Business Audio Soundbox  •  Automated Khata Ledger", MARGIN, footerY, paint);

        String pageText = "Page " + pageNum;
        float pageW = paint.measureText(pageText);
        canvas.drawText(pageText, PAGE_WIDTH - MARGIN - pageW, footerY, paint);
    }

    private static String truncateText(Paint paint, String text, float maxWidth) {
        if (text == null) return "";
        if (paint.measureText(text) <= maxWidth) return text;
        while (text.length() > 3 && paint.measureText(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    public static void openPdf(Context context, File pdfFile) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", pdfFile);
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(uri, "application/pdf");
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Intent chooser = Intent.createChooser(viewIntent, "Open PDF Statement with");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(context, "Could not open PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
