package com.d0klabs.cryptowalt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.d0klabs.cryptowalt.data.DBHelperQBE;
import com.d0klabs.cryptowalt.databinding.ActivityMainBinding;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    // прийом 128 біт і перетворити в Base64
    // кодувати все в Ліс цим Base64

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    DBHelperQBE CpWalDBHelper;

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_OBJECT = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_READ_SET = 6;
    public static final String DEVICE_OBJECT = "device_name";
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private BtTransceiver.ChatController chatController;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice connectingDevice;
    private TextView status;
    private Button btnConnect;
    private ListView listView;
    private ArrayAdapter<String> chatAdapter;
    private ArrayList<String> chatMessages;
    private EditText inputLayout;
    private ArrayAdapter<String> discoveredDevicesAdapter;
    public String passwd=null;
    public String readMessage = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        status = (TextView) findViewById(R.id.status);
        btnConnect = (Button) findViewById(R.id.btn_connect);
        listView = (ListView) findViewById(R.id.list);
        inputLayout = (EditText) findViewById(R.id.message_edit);
        View btnSend = findViewById(R.id.Send);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
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
        private void connectToDevice (String deviceAddress){
            bluetoothAdapter.cancelDiscovery();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            chatController.connect(device);
        }

        //CpWalDBHelper = new SQLiteOpenHelper(this);
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPrinterPickDialog();
            }
        });
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //setP();
                if (inputLayout.getText().toString().equals("")) {
                    Toast.makeText(MainActivity.this, "Please input some texts", Toast.LENGTH_SHORT).show();
                } else {
                    sendMessage(inputLayout.getText().toString());
                    inputLayout.setText("");
                    //String sCompressedSP = Box.compressor(fullSP);
                    //mPass.setText(sCompressedSP);
                }
            }
        });
    }
        Handler handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_STATE_CHANGE:
                        switch (msg.arg1) {
                            case BtTransceiver.ChatController.STATE_CONNECTED:
                                setStatus("Connected to: " + connectingDevice.getName());
                                btnConnect.setEnabled(false);
                                break;
                            case BtTransceiver.ChatController.STATE_CONNECTING:
                                setStatus("Connecting...");
                                btnConnect.setEnabled(false);
                                break;
                            case BtTransceiver.ChatController.STATE_LISTEN:
                            case BtTransceiver.ChatController.STATE_NONE:
                                setStatus("Not connected");
                                break;
                        }
                        break;
                    case MESSAGE_WRITE:
                        byte[] writeBuf = (byte[]) msg.obj;
                        String writeMessage = new String(writeBuf);
                        break;
                    case MESSAGE_READ:
                        byte[] readBuf = (byte[]) msg.obj;
                        readMessage = new String(readBuf, 0, msg.arg1);
                        Toast.makeText(getApplicationContext(),"You have new message, please enter pass and tap on it", Toast.LENGTH_LONG).show();
                        chatMessages.add(connectingDevice.getName()+":  " + readMessage);
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
    public void setStatus(String s) {
        status.setText(s);
    }
}