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
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_IMAGES = "profile_images";
    private static final String COLUMN_UID = "uid";
    private static final String COLUMN_IMAGE = "image_blob";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_IMAGES + " (" +
                COLUMN_UID + " TEXT PRIMARY KEY, " +
                COLUMN_IMAGE + " BLOB)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_IMAGES);
        onCreate(db);
    }

    /**
     * Save an image bitmap to the local database.
     */
    public void saveImage(String uid, Bitmap bitmap) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        byte[] imageBlob = outputStream.toByteArray();

        values.put(COLUMN_UID, uid);
        values.put(COLUMN_IMAGE, imageBlob);

        db.insertWithOnConflict(TABLE_IMAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Retrieve an image bitmap from the local database.
     */
    public Bitmap getImage(String uid) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_IMAGES, new String[]{COLUMN_IMAGE}, COLUMN_UID + "=?", new String[]{uid}, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            byte[] imageBlob = cursor.getBlob(0);
            cursor.close();
            return BitmapFactory.decodeByteArray(imageBlob, 0, imageBlob.length);
        }
        return null;
    }
    
    public void clear() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_IMAGES);
    }
}
