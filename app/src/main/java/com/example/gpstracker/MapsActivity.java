package com.example.gpstracker;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.text.DateFormat;
import java.util.Date;
import java.util.Set;

import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
    private final static String TAG = "WHERES POSEY";

    private GoogleMap mMap;
    private Marker mMarker;
    Button connectToDevice;
    TextView bottomText;
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mdevice;
    private MyBleManager mBluetoothManager;
    private boolean connected = false;

    private static final int REQUEST_ENABLE_BT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupBlueTooth();

        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //Don't forget you'll need the
        //<service android:name="com.example...BluetoothLeService"
        //in the AndroidManifest
        service_init();

        connectToDevice = (Button) findViewById(R.id.connect);
        connectToDevice.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                service_init();
            }
        });
        bottomText = (TextView) findViewById(R.id.textView);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);

        // Add a marker in Sydney and move the camera
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
//        locationManager.get
        LatLng home = new LatLng(47, -122);
        mMarker = mMap.addMarker(new MarkerOptions().position(home).title("Home"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(home, 17));
    }

    public void connectBT() {
        Set<BluetoothDevice> pairedDevices = mAdapter.getBondedDevices();
        if(pairedDevices.size()>0) {
            for (BluetoothDevice device : pairedDevices) {
                String devicename = device.getName();
                String macAddress = device.getAddress();
                //if it's my device we're already paird
                if (devicename.toLowerCase().contains("posey")) {
                    mdevice = device;
                    break;
                }
            }
        }

//        try {
//            Log.i(TAG, "attempting connection to GATT");
//            registerReceiver(mGattUpdateReceiver, makeIntentFilter());
//            Intent gattServiceIntent = new Intent(this, MyBleManager.class);
//            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothManager = ((MyBleManager.LocalBinder) service).getService();
            mBluetoothManager.initialize();
            mBluetoothManager.connect(mdevice.getAddress());
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothManager.disconnect();
            mBluetoothManager.close();
            mBluetoothManager = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (MyBleManager.ACTION_GATT_CONNECTED.equals(action)) {
                connected = true;
                connectToDevice.setText("Connected");
            }
            if (MyBleManager.ACTION_GATT_DISCONNECTED.equals(action)) {
                connected = false;
                connectToDevice.setText("Disconnected");
                unbindService(mServiceConnection);
            }
            if (MyBleManager.ACTION_DATA_AVAILABLE.equals(action)) {
                final byte[] txValue = intent.getByteArrayExtra(MyBleManager.EXTRA_DATA);
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            String text = new String(txValue, "UTF-8");
                            String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                            Log.i(TAG, currentDateTimeString+" : "+text);
                            bottomText.setText(text);
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                });
            }
        }
    };


    public void setupBlueTooth() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        Set<BluetoothDevice> pairedDevices = mAdapter.getBondedDevices();
        if(pairedDevices.size()>0) {
            for (BluetoothDevice device : pairedDevices) {
                String devicename = device.getName();
                //if it's my device we're already paird
                if (devicename.toLowerCase().contains("posey")) {
                    mdevice = device;
                    break;
                }
            }
        }
    }

    private void service_init() {
        Intent bindIntent = new Intent(this, MyBleManager.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mGattUpdateReceiver, makeIntentFilter());
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (!mAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

    }


    @Override
    protected void onPause() {
        super.onPause();
//        mMyBleManager.disconnect();
//        unregisterReceiver(mGattUpdateReceiver);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mGattUpdateReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mBluetoothManager.stopSelf();
        mBluetoothManager = null;

    }

    private IntentFilter makeIntentFilter() {
        IntentFilter gattIntentFilter = new IntentFilter();
        gattIntentFilter.addAction(MyBleManager.ACTION_DATA_AVAILABLE);
        gattIntentFilter.addAction(MyBleManager.ACTION_GATT_CONNECTED);
        gattIntentFilter.addAction(MyBleManager.ACTION_GATT_DISCONNECTED);
        gattIntentFilter.addAction(MyBleManager.EXTRA_DATA);
        return gattIntentFilter;
    }

}
