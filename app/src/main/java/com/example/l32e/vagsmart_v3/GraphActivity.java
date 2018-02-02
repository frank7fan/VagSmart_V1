package com.example.l32e.vagsmart_v3;


import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Created by frank.fan on 8/9/2017.
 */

public class GraphActivity extends AppCompatActivity {
    //UI Objects declare
    // Objects to access the sense values; left 3 and right 3
    private TextView mSenseAverageText;
    private Button barStartButton;
    private CheckBox dataLogBox;
    private ProgressBar progressBarCustom1;
    private ProgressBar progressBarCustom2;
    private ProgressBar progressBarCustom3;
    private ProgressBar progressBarCustom4;
    private ProgressBar progressBarCustom5;
    private ProgressBar progressBarCustom6;

    // Keep track of whether reading Notifications are on or off
    private boolean NotifyState = false;

    // This tag is used for debug messages
    private static final String TAG = GraphActivity.class.getSimpleName();

    //BLE parameters pass over from SCAN Activity
    private static String mDeviceAddress;
    private static String mDeviceName;
    private static PSoCBleRobotService mPSoCBleRobotService;


    //Firebase Database Object
    private DatabaseReference mDatabase;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);
        //lock screen orientation to avoid lose BLE connection after connected
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        progressBarCustom1 = (ProgressBar) findViewById(R.id.progressBar1);
        progressBarCustom2 = (ProgressBar) findViewById(R.id.progressBar2);
        progressBarCustom3 = (ProgressBar) findViewById(R.id.progressBar3);
        progressBarCustom4 = (ProgressBar) findViewById(R.id.progressBar4);
        progressBarCustom5 = (ProgressBar) findViewById(R.id.progressBar5);
        progressBarCustom6 = (ProgressBar) findViewById(R.id.progressBar6);

        // Assign the various layout objects to the appropriate variables
        mSenseAverageText = (TextView) findViewById(R.id.senseAverage);
        barStartButton = (Button) findViewById(R.id.bar_start_button);
        dataLogBox = (CheckBox) findViewById(R.id.radioButton);

        final Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra(ScanActivity.EXTRAS_BLE_ADDRESS);
        mDeviceName = intent.getStringExtra(ScanActivity.EXTRA_BLE_DEVICE_NAME);

        // Bind to the BLE service
        Log.i(TAG, "Binding Service");
        Intent RobotServiceIntent = new Intent(this, PSoCBleRobotService.class);
        bindService(RobotServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

    }
    /**
     * This manages the lifecycle of the BLE service.
     * When the service starts we get the service object, initialize the service, and connect.
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {

            mPSoCBleRobotService = ((PSoCBleRobotService.LocalBinder) service).getService();
            if (!mPSoCBleRobotService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the peripherial database upon successful start-up initialization.
            mPSoCBleRobotService.connect(mDeviceAddress);
            Log.i(TAG, "onServiceConnected with mDeviceAddress: " + mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mPSoCBleRobotService = null;
        }
    };

    /* This will be the place for the code when button get pushed */
    public void goStart(View view) {
        if(!NotifyState) {
            NotifyState = true;
            //enable notification and start receiving sense reading
            mPSoCBleRobotService.writeNotification(true);
            barStartButton.setText("Pause");

        } else { //NotifyState = true
            NotifyState = false;
            //set BLE cccd to false
            mPSoCBleRobotService.writeNotification(false);
            barStartButton.setText("Start");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mRobotUpdateReceiver, makeRobotUpdateIntentFilter());
        if (mPSoCBleRobotService != null) {
            final boolean result = mPSoCBleRobotService.connect(mDeviceAddress);
            Log.i(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mRobotUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mPSoCBleRobotService = null;
    }

    /**
     * Handle broadcasts from the Sensor service object. The events are:
     * ACTION_CONNECTED: connected to the Sensors.
     * ACTION_DISCONNECTED: disconnected from the Sensors.
     * ACTION_DATA_AVAILABLE: received data from the Sensors.  This can be a result of a read
     * or notify operation.
     */
    private final BroadcastReceiver mRobotUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            //final String VagDeviceID = "101";
            switch (action) {
                case PSoCBleRobotService.ACTION_CONNECTED:
                    // No need to do anything here. Service discovery is started by the service.
                    Log.i(TAG, "BroadcastReceiver ACTION_CONNECTED");
                    if (mPSoCBleRobotService != null) {
                        final boolean result = mPSoCBleRobotService.connect(mDeviceAddress);
                        Log.i(TAG, "Connect request result=" + result);
                    }
                    //mPSoCBleRobotService.writeNotification(true);
                    break;
                case PSoCBleRobotService.ACTION_DISCONNECTED:
                    mPSoCBleRobotService.close();
                    //finish();
                    break;
                case PSoCBleRobotService.ACTION_DATA_AVAILABLE:
                    // This is called after a Notify completes
                    mSenseAverageText.setText(String.format("%d", 1023-PSoCBleRobotService.getTach(PSoCBleRobotService.Motor.LEFT)));
                    //progressBarCustom1.setProgress((Integer.parseInt(mSenseAverageText.getText().toString()))/10);
                    progressBarCustom1.setProgress((int)(1023-PSoCBleRobotService.getTach(PSoCBleRobotService.Motor.LEFT))/10);
                    progressBarCustom2.setProgress((int)(1023-PSoCBleRobotService.getTach(PSoCBleRobotService.Motor.LEFT2))/10);
                    progressBarCustom3.setProgress((int)(1023-PSoCBleRobotService.getTach(PSoCBleRobotService.Motor.LEFT3))/10);
                    progressBarCustom4.setProgress((int)(1023-PSoCBleRobotService.getTach(PSoCBleRobotService.Motor.RIGHT))/10);
                    progressBarCustom5.setProgress((int)(1023-PSoCBleRobotService.getTach(PSoCBleRobotService.Motor.RIGHT2))/10);
                    progressBarCustom6.setProgress((int)(1023-PSoCBleRobotService.getTach(PSoCBleRobotService.Motor.RIGHT3))/10);

                    //check if there is internet connection
                    ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                    boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

                    // write sensor readings to Firebase Realtime database
                    if (isConnected & dataLogBox.isChecked()) {
                        mDatabase = FirebaseDatabase.getInstance().getReference();
                        mDatabase.child(mDeviceName).child("sensor0").setValue(1023-PSoCBleRobotService.getTach(PSoCBleRobotService.Motor.LEFT));
                        mDatabase.child(mDeviceName).child("sensor1").setValue(1023-PSoCBleRobotService.getTach(PSoCBleRobotService.Motor.LEFT2));
                        mDatabase.child(mDeviceName).child("sensor2").setValue(1023-PSoCBleRobotService.getTach(PSoCBleRobotService.Motor.LEFT3));
                        mDatabase.child(mDeviceName).child("sensor3").setValue(1023-PSoCBleRobotService.getTach(PSoCBleRobotService.Motor.RIGHT));
                        mDatabase.child(mDeviceName).child("sensor4").setValue(1023-PSoCBleRobotService.getTach(PSoCBleRobotService.Motor.RIGHT2));
                        mDatabase.child(mDeviceName).child("sensor5").setValue(1023-PSoCBleRobotService.getTach(PSoCBleRobotService.Motor.RIGHT3));

                }
                    break;
            }
        }
    };

    /**
     * This sets up the filter for broadcasts that we want to be notified of.
     * This needs to match the broadcast receiver cases.
     *
     * @return intentFilter
     */
    private static IntentFilter makeRobotUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(PSoCBleRobotService.ACTION_CONNECTED);
        intentFilter.addAction(PSoCBleRobotService.ACTION_DISCONNECTED);
        intentFilter.addAction(PSoCBleRobotService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

}
