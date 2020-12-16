package com.example.gpstracker;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

//import org.simpleframework.xml.Serializer;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
    private final static String TAG = "WHERES POSEY";

    private GoogleMap mMap;
    private Marker mMarker;
    private LatLng currentCoords;
    Button connectToDevice;
    TextView gpsText;
    TextView dateText;
    private ClipboardManager myClipboard;
    private ClipData myClip;
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mdevice;
    private MyBleManager mBluetoothManager;
    private boolean connected = false;

    private static final int REQUEST_ENABLE_BT = 1;
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupBlueTooth();

        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        gpsText = (TextView) findViewById(R.id.textView);
        dateText = (TextView) findViewById(R.id.textViewDate);

        connectToDevice = (Button) findViewById(R.id.connect);
        connectToDevice.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                service_init();
            }
        });
        myClipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        gpsText = (TextView) findViewById(R.id.textView);
        gpsText.setLongClickable(true);
        gpsText.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String text;
                text = gpsText.getText().toString();

                myClip = ClipData.newPlainText("text", text);
                myClipboard.setPrimaryClip(myClip);

                Toast.makeText(getApplicationContext(), "Text Copied",
                        Toast.LENGTH_SHORT).show();

                return true;
            }
        });

        //Don't forget you'll need the
        //<service android:name="com.example...BluetoothLeService"
        //in the AndroidManifest
        service_init();
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_LOCATION);
            return;
        }
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        double longitude = location.getLongitude();
        double latitude = location.getLatitude();
        LatLng home = new LatLng(latitude, longitude);
        mMarker = mMap.addMarker(new MarkerOptions().position(home).title("Home"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(home, 17));
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
            }
            if (MyBleManager.ACTION_DATA_AVAILABLE.equals(action)) {
                final byte[] txValue = intent.getByteArrayExtra(MyBleManager.EXTRA_DATA);
                runOnUiThread(new ReceiveDataThread(context, txValue));
            }
        }
    };

    private class ReceiveDataThread implements Runnable {

        LocationManager lm;
        Context ctx;
        final byte[] txValue;

        public ReceiveDataThread(Context context, final byte[] txValue) {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            ctx = context;
            this.txValue = txValue;
        }
        public void run() {
            try {
                String text = new String(txValue, "UTF-8");
                String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                Log.i(TAG, currentDateTimeString + " : " + txValue.length);
                Log.i(TAG, currentDateTimeString + " : " + text);
//                            writeToGPX(text);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
                String currentDateandTime = sdf.format(new Date());
                dateText.setText(currentDateandTime);
                gpsText.setText(text);

                String[] coordArray = text.split(":");
                LatLng newCoords = new LatLng(Double.parseDouble(coordArray[0]), Double.parseDouble(coordArray[1]));
                if (newCoords.longitude > 0.0) {
                    currentCoords = newCoords;
                }

                mMarker.setPosition(currentCoords);
                if (!mMap.getProjection().getVisibleRegion().latLngBounds.contains(newCoords)) {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newCoords, 17));
                }

            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    private void writeToGPX(String coords) {
        String[] coordArray = coords.split(":");

        //FOR UTC
        DateFormat df = DateFormat.getTimeInstance();
        df.setTimeZone(TimeZone.getTimeZone("gmt"));
        String gmtTime = df.format(new Date());

//        Serializer xmlSerializer = new XMLS
    }


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

//        unbindService(mServiceConnection);
//        unregisterReceiver(mGattUpdateReceiver);
        connectToDevice.setText("CONNECTING...");
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
