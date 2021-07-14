package com.d0klabs.cryptowalt.data;

import android.bluetooth.BluetoothDevice;

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
    public void setBluetoothAdapterData (BluetoothDevice device){
        String name = device.getAddress();
        Object obj = device;
        db.store(obj);
        db.commit();
    }
    public BluetoothDevice getBluetoothAdapterData (String name){
        BluetoothDevice device;
        db.query(); //....
        return device;
    }
}
