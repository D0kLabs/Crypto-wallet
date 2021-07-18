package com.d0klabs.cryptowalt.data;

import android.provider.BaseColumns;

public class WalletContract {
    private WalletContract(){}

    public static final class TrustedEntry implements BaseColumns{
        public final static String TABLE_NAME = "trusted";
        public final static String _ID = BaseColumns._ID;
        public final static String COLUMN_NAME = "name";
        //public final static BluetoothDevice TRUST_DEVICE = new BluetoothDevice();
    }
}
