package com.d0klabs.cryptowalt.data;

import android.content.Context;
import java.util.ArrayList;
import android.util.Log;
import com.db4o.Db4oEmbedded;
import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;

public class DBHelperQBE {
    public static final String LOG_TAG = DBHelperQBE.class.getSimpleName();
    public static final String DATABASE_NAME = "btCPwal.db";
    ObjectContainer db;
    public DBHelperQBE (Context context){
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
