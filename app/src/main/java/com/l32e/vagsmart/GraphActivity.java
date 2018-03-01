package com.l32e.vagsmart;


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
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by frank.fan on 3/1/2018.
 */

public class GraphActivity extends AppCompatActivity implements InputDialog.InputDialogListenser{
    //UI Objects declare
    // Objects to access the sense values; left 3 and right 3
    private TextView mSenseAverageText;
    private Button barStartButton;
    private CheckBox dataLogBox;
    private ProgressBar[] progressBarCustom = new ProgressBar[6];
    private ProgressBar[] progressBarRaw = new ProgressBar[6];
    private Button calMinButton, calMaxButton, calClearButton;

    // Keep track of whether reading Notifications are on or off
    private boolean NotifyState = false;
    //Track Cal status
    private boolean CalMinState = false;
    private boolean CalMaxState = false;

    // This tag is used for debug messages
    private static final String TAG = GraphActivity.class.getSimpleName();

    //BLE parameters pass over from SCAN Activity
    private static String mDeviceAddress;
    private static String mDeviceName;
    private static BleService mBleService;
    private String user;
    private String sessionType;
    private Boolean isGaming = true;
    private Boolean isUserInfoValid = false;
    private Boolean isBleConnected = false;

    //Firebase Database Object
    private DatabaseReference mDatabase;

    //graphic display parameters
    private int[] calMin = new int[] {0,0,0,0,0,0};
    private int[] calMax = new int[] {1023,1023,1023,1023,1023,1023};
    private int[] sensorReading = new int[6];
    private int[] mappedData = new int[6];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);
        //lock screen orientation to avoid lose BLE connection after connected
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        progressBarCustom[0] = (ProgressBar) findViewById(R.id.progressBar1);
        progressBarCustom[1] = (ProgressBar) findViewById(R.id.progressBar2);
        progressBarCustom[2] = (ProgressBar) findViewById(R.id.progressBar3);
        progressBarCustom[3] = (ProgressBar) findViewById(R.id.progressBar4);
        progressBarCustom[4] = (ProgressBar) findViewById(R.id.progressBar5);
        progressBarCustom[5] = (ProgressBar) findViewById(R.id.progressBar6);
        progressBarRaw[0] = (ProgressBar) findViewById(R.id.progressBar1a);
        progressBarRaw[1] = (ProgressBar) findViewById(R.id.progressBar2a);
        progressBarRaw[2] = (ProgressBar) findViewById(R.id.progressBar3a);
        progressBarRaw[3] = (ProgressBar) findViewById(R.id.progressBar4a);
        progressBarRaw[4] = (ProgressBar) findViewById(R.id.progressBar5a);
        progressBarRaw[5] = (ProgressBar) findViewById(R.id.progressBar6a);

        // Assign the various layout objects to the appropriate variables
        mSenseAverageText = (TextView) findViewById(R.id.senseAverage);
        barStartButton = (Button) findViewById(R.id.bar_start_button);
        dataLogBox = (CheckBox) findViewById(R.id.radioButton);
        calMaxButton = (Button) findViewById(R.id.btn_cal_max);
        calMinButton = (Button) findViewById(R.id.btn_cal_min);
        calClearButton = (Button) findViewById(R.id.btn_cal_clr);
        calMaxButton.setEnabled(false);
        calMinButton.setEnabled(false);
        calClearButton.setEnabled(false);

        final Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra(ScanActivity.EXTRAS_BLE_ADDRESS);
        mDeviceName = intent.getStringExtra(ScanActivity.EXTRA_BLE_DEVICE_NAME);

        // Bind to the BLE service
        Log.i(TAG, "Binding Service");
        Intent SensorServiceIntent = new Intent(this, BleService.class);
        bindService(SensorServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }
    /**
     * This manages the lifecycle of the BLE service.
     * When the service starts we get the service object, initialize the service, and connect.
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {

            mBleService = ((BleService.LocalBinder) service).getService();
            try {
                if (!mBleService.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth");
                    finish();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // Automatically connects to the peripheral database upon successful start-up initialization.
            try {
                if(mBleService.connect(mDeviceAddress)) {
                    Log.i(TAG, "onServiceConnected with mDeviceAddress: " + mDeviceAddress);
                } else{
                    Log.i(TAG, "device is no longer there");
                    Toast.makeText(getApplicationContext(), "Device is gone", Toast.LENGTH_LONG).show();
                    finish();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBleService = null;
            //go back to scan activity
            onBackPressed();
        }
    };

    public void openDialog(){
        InputDialog inputDialog = new InputDialog();
        inputDialog.show(getSupportFragmentManager(), "input dialog");
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mSensorUpdateReceiver, makeSensorUpdateIntentFilter());
        if (mBleService != null) {
            final boolean result = mBleService.connect(mDeviceAddress);
            Log.i(TAG, "Connect request result=" + result);
            if (!result){
                Toast.makeText(this, "Cannot find device", Toast.LENGTH_SHORT).show();
                onBackPressed();
            }
        } else{
            Log.i(TAG, "mBleService is NULL");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mSensorUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBleService = null;
    }

    /**
     * Handle broadcasts from the Sensor service object. The events are:
     * ACTION_CONNECTED: connected to the Sensors.
     * ACTION_DISCONNECTED: disconnected from the Sensors.
     * ACTION_DATA_AVAILABLE: received data from the Sensors.  This can be a result of a read
     * or notify operation.
     */
    private final BroadcastReceiver mSensorUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            switch (action) {
                case BleService.ACTION_CONNECTED:
                    // No need to do anything here. Service discovery is started by the service.
                    Log.i(TAG, "BroadcastReceiver ACTION_CONNECTED");
                    if (mBleService != null) {
                        final boolean result = mBleService.connect(mDeviceAddress);
                        Log.i(TAG, "Connect request result=" + result);
                        isBleConnected = true;
                    }
                    //mBleService.writeNotification(true);
                    break;
                case BleService.ACTION_DISCONNECTED:
                    mBleService.close();
                    isBleConnected = false;
                    Toast.makeText(getApplicationContext(), "Device gets disconnected", Toast.LENGTH_LONG).show();
                    //go back to scan activity
                    finish();
                    break;
                case BleService.ACTION_DATA_AVAILABLE:
                    sensorReading[0] = (int)(1023- BleService.getPressure(BleService.Sensor.ONE));
                    sensorReading[1] = (int)(1023- BleService.getPressure(BleService.Sensor.THREE));
                    sensorReading[2] = (int)(1023- BleService.getPressure(BleService.Sensor.FIVE));
                    sensorReading[3] = (int)(1023- BleService.getPressure(BleService.Sensor.TWO));
                    sensorReading[4] = (int)(1023- BleService.getPressure(BleService.Sensor.FOUR));
                    sensorReading[5] = (int)(1023- BleService.getPressure(BleService.Sensor.SIX));

                    //Display mapping data to Bar graph
                    for (int i = 0; i < sensorReading.length; i++){
                        progressBarRaw[i].setProgress(sensorReading[i]*100/1023);
                        mappedData[i] = map(sensorReading[i],calMin[i], calMax[i]);
                        progressBarCustom[i].setProgress(mappedData[i]);
                        //progressBarCustom[i].setProgress(sensorReading[i]/10);

                    }
                    //for debugging------------------------------------------
                    int j=1;
                    Log.d("Sensor","mapReading "+map(sensorReading[j],calMin[j], calMax[j])
                            +" ,sensorReading " + sensorReading[j] + "  calMin "+ calMin[j] + "   calMax " + calMax[j]);
                    //-------------------------------------------------------
                    //check if there is internet connection
                    ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                    boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

                    // write sensor readings to Firebase Realtime database
                    if (isConnected & dataLogBox.isChecked() & isUserInfoValid) {

                        if(isGaming) {
                            mDatabase = FirebaseDatabase.getInstance().getReference(mDeviceName);
                            for (int i=0; i<sensorReading.length; i++){
                                mDatabase.child(mDeviceName).child("sensor"+i).setValue(sensorReading[i]);
                            }
                            //mDatabase.child(mDeviceName).child("sensor0").setValue(sensorReading[0]);
                            mDatabase.child(mDeviceName).child("time").setValue(ServerValue.TIMESTAMP);
                            mDatabase.child(mDeviceName).child("user").setValue(user);
                            mDatabase.child(mDeviceName).child("session").setValue(sessionType);

                        }else {
                            mDatabase = FirebaseDatabase.getInstance().getReference(mDeviceName+"RTData");
                            HashMap<String, Object> message = new HashMap<>();
                            message.put("user", user);
                            message.put("Time", ServerValue.TIMESTAMP);
                            message.put("session", sessionType);
                            for (int i=0; i<sensorReading.length; i++){
                                message.put("s"+i, sensorReading[i]);
                            }
                            //message.put("S0", sensorReading[0]);
                            mDatabase.push().setValue(message);
                        }
                    }
                    //Display number on top of screen
                    //keep this is the end since sort will rearrange the Array sequency
                    //mSenseAverageText.setText(String.format("%d", displayNumber("Max2", sensorReading)/10));
                    mSenseAverageText.setText("Average 2 Max: " + displayNumber("Max2", mappedData));
                    //progressBarCustom1.setProgress((Integer.parseInt(mSenseAverageText.getText().toString()))/10);
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
    private static IntentFilter makeSensorUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_CONNECTED);
        intentFilter.addAction(BleService.ACTION_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public void applyTexts(String username, String session, int selectedId) {
        user = username;
        sessionType = session;
        Toast.makeText(getApplicationContext(), "selectedId="+selectedId, Toast.LENGTH_LONG).show();
        isGaming = selectedId == 0;
        if (TextUtils.isEmpty(username)|TextUtils.isEmpty(session)) {
            Toast.makeText(getApplicationContext(), "Input User Info Blank, no data sent", Toast.LENGTH_LONG).show();
            isUserInfoValid = false;
        }else{
            isUserInfoValid = true;
        }

    }
    /* This will be the place for the code when button get pushed */
    public void goStart(View view) {
        if(isBleConnected){
            if(!NotifyState) {
                //id data logging enabled, pop up window for input information
                if(dataLogBox.isChecked()){
                    openDialog();
                }
                //disable Datalog checkbox while running data sensing;
                //once start data sensing, do not allow to enable/disable data logging
                dataLogBox.setEnabled(false);

                NotifyState = true;
                //enable notification and start receiving sense reading
                mBleService.writeNotification(true);

                barStartButton.setText("Pause");
                //enableCalibration buttons
                calMaxButton.setEnabled(true);
                calMinButton.setEnabled(true);
                calClearButton.setEnabled(true);

                //check if Calmin has non-zero value, set boarder for button
                if (CalMinState) calMinButton.setBackgroundResource(R.drawable.button_border);
                //check if Calmin has non-1023 value, set boarder for button
                if (CalMaxState) calMaxButton.setBackgroundResource(R.drawable.button_border);
                //clear Cal data
                //for (int i=0; i<6; i++){
                //    calMax[i] = 1023;
                //    calMin[i] =0;}

            } else {
                isUserInfoValid = false;
                NotifyState = false;
                //set BLE cccd to false
                mBleService.writeNotification(false);
                barStartButton.setText("Start");
                dataLogBox.setEnabled(true);
                //disbale Calibration buttons
                calMaxButton.setEnabled(false);
                calMinButton.setEnabled(false);
                calClearButton.setEnabled(false);
                calMaxButton.setBackgroundResource(R.drawable.button_calbrations);
                calMinButton.setBackgroundResource(R.drawable.button_calbrations);
            }
        }else{
            Toast.makeText(getApplicationContext(), "Device is no longer available", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    public void calSetMin(View view){
        //take current reading and set them to calMin

        //check if higher than MAX
        for (int i=0; i<sensorReading.length; i++){
            if (sensorReading[i] > calMax[i] - 10){
                calMin[i] = calMax[i] - 10;
            }else{
                calMin[i] = sensorReading[i];
            }
        }
        Toast.makeText(getApplicationContext(), "set Min", Toast.LENGTH_SHORT).show();
        calMinButton.setBackgroundResource(R.drawable.button_border);
        CalMinState = true;
    }
    public void calClear(View view){
        //set calMin to 0; cal<ax to 1023
        for (int i=0; i<6; i++){
            calMax[i] = 1023;
            calMin[i] =0;}
        Toast.makeText(getApplicationContext(), "clear min Max", Toast.LENGTH_SHORT).show();
        calMaxButton.setBackgroundResource(R.drawable.button_calbrations);
        calMinButton.setBackgroundResource(R.drawable.button_calbrations);
        CalMaxState = false;
        CalMinState = false;
    }

    public void calSetMax(View view){
        //take current reading and set them to calMax

        //check if smaller than MIN
        for (int i=0; i<sensorReading.length; i++){
            if (sensorReading[i] < calMin[i] + 10){
                calMax[i] = calMin[i] + 10;
            }else{
                calMax[i] = sensorReading[i];
            }
        }
        Toast.makeText(getApplicationContext(), "set Max", Toast.LENGTH_SHORT).show();
        calMaxButton.setBackgroundResource(R.drawable.button_border);
        CalMaxState = true;
    }

    //map function take ADC reading from [0:1023] and map to [0:100] base on CalMin and CalMax
    public static int map(int inputValue, int inputMin, int inputMax){
        int extendMin, extendMax;
        //if inputMin means 10%; inputMax means 90%
        if (inputMin!=0 | inputMax!=1023){
            extendMin = inputMin - ((inputMax - inputMin) / 8);
            extendMax = inputMax + ((inputMax - inputMin) / 8);
        }else{
            extendMin = inputMin;
            extendMax = inputMax;
        }
        //if inputMin means 0%; inputMax means 100%
        //int extendMin = inputMin;
        //int extendMax = inputMax;
        //mapping from (extendMin,extendMax) to (0, 100)
        if (inputValue < extendMin){
            return 0;}
        else if (inputValue > extendMax){
            return 100;}
        else return (inputValue-extendMin)*100/(extendMax-extendMin);
        //else return inputValue/10;
    }

    private int displayNumber(String method, int[] inputArray){
        Arrays.sort(inputArray);
        return (inputArray[inputArray.length-1]+inputArray[inputArray.length-2])/2;
    }
}

