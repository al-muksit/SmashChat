package com.smashchat.Utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;

/**
 * DatabaseHelper manages local SQLite storage for caching images.
 * This allows profile pictures to load instantly without waiting for the network.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "SmashChatLocal.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_IMAGES = "profile_images";
    private static final String COLUMN_UID = "uid";
    private static final String COLUMN_IMAGE = "image_blob";

    private static final String TABLE_MESSAGES = "chat_messages";
    private static final String COLUMN_MSG_ID = "msg_id";
    private static final String COLUMN_ROOM_ID = "room_id";
    private static final String COLUMN_SENDER_UID = "sender_uid";
    private static final String COLUMN_MESSAGE_TEXT = "message_text";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_TYPE = "type";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createImagesTable = "CREATE TABLE " + TABLE_IMAGES + " (" +
                COLUMN_UID + " TEXT PRIMARY KEY, " +
                COLUMN_IMAGE + " BLOB)";
        db.execSQL(createImagesTable);

        String createMessagesTable = "CREATE TABLE " + TABLE_MESSAGES + " (" +
                COLUMN_MSG_ID + " TEXT PRIMARY KEY, " +
                COLUMN_ROOM_ID + " TEXT, " +
                COLUMN_SENDER_UID + " TEXT, " +
                COLUMN_MESSAGE_TEXT + " TEXT, " +
                COLUMN_TIMESTAMP + " INTEGER, " +
                COLUMN_TYPE + " INTEGER)";
        db.execSQL(createMessagesTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            String createMessagesTable = "CREATE TABLE " + TABLE_MESSAGES + " (" +
                    COLUMN_MSG_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_ROOM_ID + " TEXT, " +
                    COLUMN_SENDER_UID + " TEXT, " +
                    COLUMN_MESSAGE_TEXT + " TEXT, " +
                    COLUMN_TIMESTAMP + " INTEGER, " +
                    COLUMN_TYPE + " INTEGER)";
            db.execSQL(createMessagesTable);
        }
    }

    /**
     * Save a message to the local database.
     */
    public void saveMessage(String roomId, String msgId, com.smashchat.Models.Messages message) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_MSG_ID, msgId);
        values.put(COLUMN_ROOM_ID, roomId);
        values.put(COLUMN_SENDER_UID, message.getuId());
        values.put(COLUMN_MESSAGE_TEXT, message.getMessage());
        values.put(COLUMN_TIMESTAMP, message.getTimestamp());
        values.put(COLUMN_TYPE, message.getType());

        db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Retrieve all messages for a specific chat room.
     */
    public java.util.ArrayList<com.smashchat.Models.Messages> getMessages(String roomId) {
        java.util.ArrayList<com.smashchat.Models.Messages> messages = new java.util.ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_MESSAGES, null, COLUMN_ROOM_ID + "=?", new String[]{roomId}, null, null, COLUMN_TIMESTAMP + " ASC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                com.smashchat.Models.Messages msg = new com.smashchat.Models.Messages();
                msg.setuId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENDER_UID)));
                msg.setMessage(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE_TEXT)));
                msg.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)));
                msg.setType(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TYPE)));
                messages.add(msg);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return messages;
    }

    /**
     * Save an image bitmap to the local database.
     */
    public void saveImage(String uid, android.graphics.Bitmap bitmap) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream);
        byte[] imageBlob = outputStream.toByteArray();

        values.put(COLUMN_UID, uid);
        values.put(COLUMN_IMAGE, imageBlob);

        db.insertWithOnConflict(TABLE_IMAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Retrieve an image bitmap from the local database.
     */
    public android.graphics.Bitmap getImage(String uid) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_IMAGES, new String[]{COLUMN_IMAGE}, COLUMN_UID + "=?", new String[]{uid}, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            byte[] imageBlob = cursor.getBlob(0);
            cursor.close();
            return android.graphics.BitmapFactory.decodeByteArray(imageBlob, 0, imageBlob.length);
        }
        return null;
    }
    
    public void clear() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_IMAGES);
    }
}
