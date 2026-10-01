package com.jocude.vectorscan;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Usuarios locales de la app. Las contraseñas se guardan con hash PBKDF2 (ver
 * {@link PasswordHasher}); nunca en claro.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "VectorScanDB";
    /** v1: contraseñas en claro y usuario admin/1234 por defecto. v2: hash y sin usuario por defecto. */
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_USERS = "users";
    private static final String COL_ID = "id";
    private static final String COL_USERNAME = "username";
    private static final String COL_PASSWORD = "password";

    public static final int MIN_PASSWORD_LENGTH = 6;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_USERS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_USERNAME + " TEXT UNIQUE NOT NULL, "
                + COL_PASSWORD + " TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Quitar la cuenta de pruebas y pasar a hash las contraseñas guardadas en claro.
            db.delete(TABLE_USERS, COL_USERNAME + " = ? AND " + COL_PASSWORD + " = ?",
                    new String[]{"admin", "1234"});
            try (Cursor c = db.query(TABLE_USERS, new String[]{COL_ID, COL_PASSWORD},
                    null, null, null, null, null)) {
                while (c.moveToNext()) {
                    String stored = c.getString(1);
                    if (PasswordHasher.isHash(stored)) continue;
                    ContentValues values = new ContentValues();
                    values.put(COL_PASSWORD, PasswordHasher.hash(stored));
                    db.update(TABLE_USERS, values, COL_ID + " = ?", new String[]{String.valueOf(c.getLong(0))});
                }
            }
        }
    }

    /** @return true si se ha creado; false si el usuario ya existe. */
    public boolean addUser(String username, String password) {
        ContentValues values = new ContentValues();
        values.put(COL_USERNAME, username);
        values.put(COL_PASSWORD, PasswordHasher.hash(password));
        return getWritableDatabase().insert(TABLE_USERS, null, values) != -1;
    }

    /** @return true si el usuario existe y la contraseña coincide. */
    public boolean checkUser(String username, String password) {
        try (Cursor c = getReadableDatabase().query(TABLE_USERS, new String[]{COL_PASSWORD},
                COL_USERNAME + " = ?", new String[]{username}, null, null, null)) {
            return c.moveToFirst() && PasswordHasher.verify(password, c.getString(0));
        }
    }
}
