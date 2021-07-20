package com.d0klabs.cryptowalt.data;

import android.bluetooth.BluetoothDevice;

import com.db4o.Db4oEmbedded;
import com.db4o.ObjectContainer;

public class DBHelperQBE {
    ObjectContainer db;
    boolean openDb(String name) {
        if (name != null){
            db = Db4oEmbedded.openFile();
            return true;
        }else return false;
    }
    void closeDb(){
        db.close();
    }
    // THX sohaliaziz05
    public void setBluetoothAdapterObj (BluetoothDevice device){
        db.store(device);
        db.commit();
    }
    public BluetoothDevice getBluetoothAdapterObj (BluetoothDevice device){
        BluetoothDevice result = null;
        while (result.getAddress().equals(device.getAddress())){
           //result = db.queryByExample(device);
           if (result.getAddress() == device.getAddress()){
               result = device;
               break;
           }
        }
        return result;
    }
}
