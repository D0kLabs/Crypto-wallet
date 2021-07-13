package com.d0klabs.cryptowalt.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper {
    public DBHelper (Context context){
        super (context, "cpwal", null,  1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
