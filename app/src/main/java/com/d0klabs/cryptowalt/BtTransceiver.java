package com.d0klabs.cryptowalt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.ParcelUuid;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public class BtTransceiver {
    public static BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    public static BluetoothDevice[] foundBTDev = new BluetoothDevice[40];
    private Handler handler;
    UUID[] uuids = new UUID[120];
    public static int n =0;


    public static void BtFinder(IntentFilter mBTFilter) {
        if (mBluetoothAdapter == null) {
            //SEND over handler: Toast.makeText(this, "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
        }
        // Check if it was BT enabled already
        mBluetoothAdapter.enable();
        mBluetoothAdapter.startDiscovery(); //Check time to searching anything
        Set<BluetoothDevice> foundedDevices = mBluetoothAdapter.getBondedDevices();
        if (foundedDevices.size() > 0) {
            for (BluetoothDevice device : foundedDevices) {
                foundBTDev[n] = device;
                try {
                    Method getUuidsMethod = foundBTDev[n].getClass().getDeclaredMethod("getUuids", null);
                    ParcelUuid[] uuids = (ParcelUuid[]) getUuidsMethod.invoke(foundBTDev[n], null);
                    // Compare uuid of card by trusted
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
                n++;
            }

        }
        mBluetoothAdapter.cancelDiscovery();
    }

}
