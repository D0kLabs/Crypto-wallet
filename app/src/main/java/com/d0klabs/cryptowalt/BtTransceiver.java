package com.d0klabs.cryptowalt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.IntentFilter;
import android.os.Handler;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class BtTransceiver {
    public static BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    public static BluetoothDevice[] sDevices = new BluetoothDevice[40];
    private Handler handler;
    public static String[] trusted = new String[1024];
    public static String[][] found = new String[1024][3];
    public static Queue<String> current = new LinkedBlockingQueue<>();
    public static int n =0;

    public static void BtFinder(IntentFilter mBTFilter) {
        //Switch On and find paired
        mBluetoothAdapter.enable();
        mBluetoothAdapter.startDiscovery();
        Set<BluetoothDevice> pairedCards = mBluetoothAdapter.getBondedDevices();
        if (pairedCards.size() > 0) {
            for (BluetoothDevice device : pairedCards) {
                trusted[n] = device.getName();
                sDevices[n] = device;
                n++;
            }

        }
        mBluetoothAdapter.cancelDiscovery();
    }

}
