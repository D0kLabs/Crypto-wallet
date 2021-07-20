package com.d0klabs.cryptowalt;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.databinding.DataBindingUtil;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.d0klabs.cryptowalt.data.DBHelperQBE;
import com.d0klabs.cryptowalt.databinding.ActivityMainBinding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    // прийом 128 біт і перетворити в Base64
    public short[] btMsgInput = {(1),(0),(0),(1),(1),(0),(0),(1),(1),(0),(1),(0),(1),(1),(0),(0),(1),(1),(0),(1),(1),(0),(1),(0),(1),(1),(1),(0),(0),(0),(0),(0),(1),(1),(0),(1),(0),(1),(0),(0),(1),(0),(0),(1),(0),(1),(1),(0),(1),(1),(0),(0),(0),(0),(1),(0),(1),(1),(0),(1),(0),(1),(0),(1),(1),(0),(1),(1),(0),(1),(0),(1),(1),(0),(1),(1),(1),(1),(0),(0),(1),(1),(1),(0),(0),(0),(0),(0),(0),(1),(1),(0),(1),(1),(1),(1),(0),(0),(1),(1),(1),(1),(0),(1),(0),(1),(0),(1),(1),(0),(1),(1),(0),(1),(1),(1),(0),(1),(1),(0),(1),(0),(0),(0),(0),(0),(0),(0)};// maza4akthatrecDeerbt by Base64

    // кодувати все в Ліс цим Base64
    //TODO: need IO impl
    //TODO: need Keygen impl
    //TODO: need some packetSender

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_OBJECT = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_READ_SET = 6;
    public static final String DEVICE_OBJECT = "device_name";
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    public String passwd = null;
    public String readMessage = null;
    DBHelperQBE CpWalDBHelper;
    private ActivityMainBinding binding;
    private AppBarConfiguration mAppBarConfiguration;
    private BtTransceiver.ChatController chatController;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice connectingDevice;
    private ArrayAdapter<String> chatAdapter;
    private ArrayList<String> chatMessages;
    private Dialog dialog;
    private DrawerLayout drawer;
    public Context currentContext;
    NavigationView navigationView;
    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuf);
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    readMessage = new String(readBuf, 0, msg.arg1);
                    Toast.makeText(getApplicationContext(), "You have new message, please enter pass and tap on it", Toast.LENGTH_LONG).show();
                    chatMessages.add(connectingDevice.getName() + ":  " + readMessage);
                    chatAdapter.notifyDataSetChanged();
                    break;
                    /*
                case MESSAGE_READ_SET:
                    String pass = new String ((String) msg.obj);
                    pass = Box.decompressor(pass);
                    //loadP(pass);
                    P.notifyAll();
                    setReadMessage();
                    break;
                     */

                case MESSAGE_DEVICE_OBJECT:
                    connectingDevice = msg.getData().getParcelable(DEVICE_OBJECT);
                    Toast.makeText(getApplicationContext(), "Connected to " + connectingDevice.getName(),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            return false;
        }
    });
    private ArrayAdapter<String> discoveredDevicesAdapter;

    public MainActivity() throws IOException {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activitymain);
        binding = DataBindingUtil.setContentView(this, R.layout.activitymain);
        setContentView(binding.getRoot());
        currentContext=getApplicationContext();

        setSupportActionBar(binding.uppertools);
       /* binding.uppertools.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        */
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setDrawerLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        for (int i=0;i<100;i++){
            new SaveObjectOnDb4o(currentContext).execute(null, null, null);
        }
    }
    public class SaveObjectOnDb4o extends AsyncTask<Void, Void, Void> {
        public SaveObjectOnDb4o(Context mContext) {

        }

        @Override
        protected Void doInBackground(Void... voids) {
            try{
                UserDao dao=new UserDao(currentContext);
                dao.store(User.randomUser());

                dao.close();
                String str= "Storing object: success";
                //mListStr.add(str);
            }catch (Throwable e){
                String str= "Storing objects: FAILED !!!!!!!!!!!";
                //mListStr.add(str);
                //mListStr.add(e.getMessage());
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            super.onPostExecute(v);
            //mListAdapter.notifyDataSetChanged();
        }
    }

    private void connectToDevice(String deviceAddress) {
        bluetoothAdapter.cancelDiscovery();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        chatController.connect(device);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        } else {
            chatController = new BtTransceiver.ChatController(this, handler);
        }
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (chatController != null) {
            if (chatController.getState() == BtTransceiver.ChatController.STATE_NONE) {
                chatController.start();
            }
        }
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (chatController != null)
            chatController.stop();
    }

    private void showPrinterPickDialog() {
        dialog = new Dialog(this);
        dialog.setContentView(R.layout.layout_bluetooth);
        dialog.setTitle("Bluetooth Devices");

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();

        //Initializing bluetooth adapters
        ArrayAdapter<String> pairedDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        discoveredDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        //locate listviews and attatch the adapters
        ListView listView = (ListView) dialog.findViewById(R.id.pairedDeviceList);
        ListView listView2 = (ListView) dialog.findViewById(R.id.discoveredDeviceList);
        listView.setAdapter(pairedDevicesAdapter);
        listView2.setAdapter(discoveredDevicesAdapter);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(discoveryFinishReceiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryFinishReceiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                pairedDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            pairedDevicesAdapter.add(getString(R.string.none_paired));
        }

        //Handling listview item click event
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                connectToDevice(address);
                dialog.dismiss();
            }

        });

        listView2.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                connectToDevice(address);
                dialog.dismiss();
            }
        });

        dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.setCancelable(false);
        dialog.show();
    }
    private final BroadcastReceiver discoveryFinishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    discoveredDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (discoveredDevicesAdapter.getCount() == 0) {
                    discoveredDevicesAdapter.add(getString(R.string.none_found));
                }
            }
        }
    };
}