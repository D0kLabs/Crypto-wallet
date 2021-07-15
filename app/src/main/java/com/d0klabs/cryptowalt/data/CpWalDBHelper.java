package com.d0klabs.cryptowalt.data;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.Nullable;

public class CpWalDBHelper extends SQLiteOpenHelper{

    public static final String DATABASE_NAME = "cpwal.db";

    public CpWalDBHelper(@Nullable Context context, @Nullable String name, @Nullable SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    //public static final Date DATABASE_LAST_WRITABLE_TIME =
    @Override
    public void onCreate(SQLiteDatabase db) {
        //db.execSQL();

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
