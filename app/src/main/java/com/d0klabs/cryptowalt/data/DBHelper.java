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
    String dbPath;
    ObjectContainer db;
    boolean openDb(String name) {
        if (name != null){
            db = Db4oEmbedded.openFile(Db4oEmbedded.newConfiguration(),DATABASE_NAME);
            return true;
        }else return false;
    }
    void closeDb(){
        db.close();
    }
    void emptyDb(){
        ObjectSet result= db.queryByExample(new Object());
        while(result.hasNext()){
            db.delete(result.next());
        }
    }
// THX sohaliaziz05

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
