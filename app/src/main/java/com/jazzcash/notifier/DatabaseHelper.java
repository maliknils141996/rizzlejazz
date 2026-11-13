package com.jazzcash.notifier;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "PayVoxShopKhata.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_TX = "transactions";
    public static final String COL_ID = "id";
    public static final String COL_TRX_ID = "trx_id";
    public static final String COL_AMOUNT = "amount";
    public static final String COL_TYPE = "type"; // 'inflow' or 'outflow'
    public static final String COL_CHANNEL = "channel"; // 'jazzcash', 'easypaisa'
    public static final String COL_SENDER = "sender";
    public static final String COL_RECIPIENT = "recipient";
    public static final String COL_PURPOSE = "purpose";
    public static final String COL_CREATED_AT = "created_at";

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_TX + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TRX_ID + " TEXT UNIQUE, "
                + COL_AMOUNT + " REAL NOT NULL, "
                + COL_TYPE + " TEXT DEFAULT 'inflow', "
                + COL_CHANNEL + " TEXT DEFAULT 'jazzcash', "
                + COL_SENDER + " TEXT, "
                + COL_RECIPIENT + " TEXT, "
                + COL_PURPOSE + " TEXT, "
                + COL_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TX);
        onCreate(db);
    }

    public synchronized boolean insertTransaction(String trxId, double amount, String type, String channel, String sender, String recipient, String purpose) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Avoid duplicate by real Trx ID only
        if (trxId != null && !trxId.trim().isEmpty() && !trxId.equalsIgnoreCase("N/A") && !trxId.startsWith("TX-GEN-")) {
            trxId = trxId.trim();
            Cursor c = db.rawQuery("SELECT id FROM " + TABLE_TX + " WHERE trx_id = ?", new String[]{trxId});
            if (c.moveToFirst()) {
                c.close();
                return false;
            }
            c.close();
        } else {
            // Generate unique guaranteed fallback ID so empty strings NEVER collide or fail SQLite UNIQUE constraint
            trxId = "TX-GEN-" + System.currentTimeMillis() + "-" + ((int)(Math.random() * 9000) + 1000);
        }

        ContentValues cv = new ContentValues();
        cv.put(COL_TRX_ID, trxId);
        cv.put(COL_AMOUNT, amount);
        cv.put(COL_TYPE, type);
        cv.put(COL_CHANNEL, channel != null ? channel.toLowerCase() : "jazzcash");
        cv.put(COL_SENDER, sender);
        cv.put(COL_RECIPIENT, recipient);
        cv.put(COL_PURPOSE, purpose);

        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        cv.put(COL_CREATED_AT, now);

        long res = db.insert(TABLE_TX, null, cv);
        return res != -1;
    }

    public synchronized KhataStats getStats(String startDate, String endDate) {
        return getStats(startDate, endDate, "all");
    }

    public synchronized KhataStats getStats(String startDate, String endDate, String channelFilter) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT "
                + "COALESCE(SUM(CASE WHEN type = 'inflow' THEN amount ELSE 0 END), 0) AS total_inflow, "
                + "COALESCE(SUM(CASE WHEN type = 'outflow' THEN amount ELSE 0 END), 0) AS total_outflow, "
                + "COUNT(CASE WHEN type = 'inflow' THEN 1 END) AS inflow_count, "
                + "COUNT(CASE WHEN type = 'outflow' THEN 1 END) AS outflow_count "
                + "FROM " + TABLE_TX + " "
                + "WHERE created_at BETWEEN ? AND ?";

        List<String> args = new ArrayList<>();
        args.add(startDate);
        args.add(endDate);

        if (channelFilter != null && !"all".equalsIgnoreCase(channelFilter)) {
            query += " AND channel = ?";
            args.add(channelFilter.toLowerCase());
        }

        Cursor c = db.rawQuery(query, args.toArray(new String[0]));
        double inflow = 0, outflow = 0;
        int inCount = 0, outCount = 0;

        if (c.moveToFirst()) {
            inflow = c.getDouble(0);
            outflow = c.getDouble(1);
            inCount = c.getInt(2);
            outCount = c.getInt(3);
        }
        c.close();

        return new KhataStats(inflow, outflow, inCount, outCount);
    }

    public synchronized List<TransactionItem> getTransactions(String startDate, String endDate, String channelFilter) {
        List<TransactionItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_TX + " WHERE created_at BETWEEN ? AND ?";
        List<String> args = new ArrayList<>();
        args.add(startDate);
        args.add(endDate);

        if (channelFilter != null && !"all".equalsIgnoreCase(channelFilter)) {
            query += " AND channel = ?";
            args.add(channelFilter.toLowerCase());
        }

        query += " ORDER BY id DESC";

        Cursor c = db.rawQuery(query, args.toArray(new String[0]));
        if (c.moveToFirst()) {
            do {
                TransactionItem item = new TransactionItem(
                        c.getLong(c.getColumnIndexOrThrow(COL_ID)),
                        c.getString(c.getColumnIndexOrThrow(COL_TRX_ID)),
                        c.getDouble(c.getColumnIndexOrThrow(COL_AMOUNT)),
                        c.getString(c.getColumnIndexOrThrow(COL_TYPE)),
                        c.getString(c.getColumnIndexOrThrow(COL_CHANNEL)),
                        c.getString(c.getColumnIndexOrThrow(COL_SENDER)),
                        c.getString(c.getColumnIndexOrThrow(COL_RECIPIENT)),
                        c.getString(c.getColumnIndexOrThrow(COL_PURPOSE)),
                        c.getString(c.getColumnIndexOrThrow(COL_CREATED_AT))
                );
                list.add(item);
            } while (c.moveToNext());
        }
        c.close();
        return list;
    }

    public static class KhataStats {
        public final double totalInflow;
        public final double totalOutflow;
        public final double netBalance;
        public final int inflowCount;
        public final int outflowCount;

        public KhataStats(double totalInflow, double totalOutflow, int inflowCount, int outflowCount) {
            this.totalInflow = totalInflow;
            this.totalOutflow = totalOutflow;
            this.netBalance = totalInflow - totalOutflow;
            this.inflowCount = inflowCount;
            this.outflowCount = outflowCount;
        }
    }

    public static class TransactionItem {
        public final long id;
        public final String trxId;
        public final double amount;
        public final String type;
        public final String channel;
        public final String sender;
        public final String recipient;
        public final String purpose;
        public final String createdAt;

        public TransactionItem(long id, String trxId, double amount, String type, String channel, String sender, String recipient, String purpose, String createdAt) {
            this.id = id;
            this.trxId = trxId;
            this.amount = amount;
            this.type = type;
            this.channel = channel;
            this.sender = sender;
            this.recipient = recipient;
            this.purpose = purpose;
            this.createdAt = createdAt;
        }
    }
}
