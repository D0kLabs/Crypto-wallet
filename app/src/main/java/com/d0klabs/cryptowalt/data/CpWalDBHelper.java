package com.d0klabs.cryptowalt.data;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.Date;

public class CpWalDBHelper extends SQLiteOpenHelper{
    public static final String LOG_TAG = CpWalDBHelper.class.getSimpleName();
    public static final String DATABASE_NAME = "cpwal.db";
    //public static final Date DATABASE_LAST_WRITABLE_TIME =
    @Override
    public void onCreate(SQLiteDatabase db) {

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
