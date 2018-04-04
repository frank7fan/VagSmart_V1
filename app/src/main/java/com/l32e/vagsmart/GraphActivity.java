package com.l32e.vagsmart;


import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.support.design.widget.Snackbar;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

/**
 * Created by frank.fan on 3/1/2018.
 */

public class GraphActivity extends AppCompatActivity implements InputDialog.InputDialogListenser{
    //constant
    public static final int DATA_LENGTH = 6;
    public static final int RANGE_11B = (int)Math.pow(2,11)-1;
    public static final int OFFSET = 20;
    public static final int OPTION_GAMING = 0, OPTION_ENGINEERING = 1, OPTION_SCREEN_RECORDING = 2;
    //UI Objects declare
    // Objects to access the sense values; left 3 and right 3
    private TextView mSenseAverageText;
    private Button barStartButton;
    private CheckBox dataLogBox;
    private ProgressBar[] progressBarCustom = new ProgressBar[DATA_LENGTH];
    private ProgressBar[] progressBarRaw = new ProgressBar[DATA_LENGTH];
    public static Button calMinButton, calMaxButton, calClearButton;
    private TextView textViewScreenRecording;
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
    //setting parameters
    private String user;
    private String sessionType;
    private int dataLogOption;
    //private Boolean isGaming = true;
    private Boolean isBleConnected = false;
    private Boolean isScreenRecording = false;
    private File fileCsvPathName;
    private CSVWriter writer;
    private int recordStartTime;

    //Firebase Database Object
    private DatabaseReference mDatabase;

    //graphic display parameters
    private int[] calMin = new int[] {0,0,0,0,0,0};
    private int[] calMax = new int[] {RANGE_11B,RANGE_11B,RANGE_11B,RANGE_11B,RANGE_11B,RANGE_11B};
    private int[] mappedData = new int[DATA_LENGTH];

    //************************************************************
    //for screen capture
    //************************************************************
    private static final int REQUEST_CODE = 1000;
    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;
    private static final int DISPLAY_WIDTH = 720;
    private static final int DISPLAY_HEIGHT = 1280;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionCallback mMediaProjectionCallback;
    private MediaRecorder mMediaRecorder;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_PERMISSIONS = 10;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);
        //lock screen orientation to avoid lose BLE connection after connected
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        progressBarCustom[0] =  findViewById(R.id.progressBar0);
        progressBarCustom[1] =  findViewById(R.id.progressBar1);
        progressBarCustom[2] =  findViewById(R.id.progressBar2);
        progressBarCustom[3] =  findViewById(R.id.progressBar3);
        progressBarCustom[4] =  findViewById(R.id.progressBar4);
        progressBarCustom[5] =  findViewById(R.id.progressBar5);
        progressBarRaw[0] =  findViewById(R.id.progressBarA0);
        progressBarRaw[1] =  findViewById(R.id.progressBarA1);
        progressBarRaw[2] =  findViewById(R.id.progressBarA2);
        progressBarRaw[3] =  findViewById(R.id.progressBarA3);
        progressBarRaw[4] =  findViewById(R.id.progressBarA4);
        progressBarRaw[5] =  findViewById(R.id.progressBarA5);

        // Assign the various layout objects to the appropriate variables
        mSenseAverageText = findViewById(R.id.senseAverage);
        barStartButton = findViewById(R.id.bar_start_button);
        dataLogBox = findViewById(R.id.radioButton);
        calMaxButton = findViewById(R.id.btn_cal_max);
        calMinButton = findViewById(R.id.btn_cal_min);
        calClearButton = findViewById(R.id.btn_cal_clr);
        Utils.enableCalButtons(false);
        textViewScreenRecording = findViewById(R.id.screenRecording);
        textViewScreenRecording.setVisibility(View.INVISIBLE);

        final Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra(ScanActivity.EXTRAS_BLE_ADDRESS);
        mDeviceName = intent.getStringExtra(ScanActivity.EXTRA_BLE_DEVICE_NAME);

        // Bind to the BLE service
        Log.i(TAG, "Binding Service");
        Intent SensorServiceIntent = new Intent(this, BleService.class);
        bindService(SensorServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        //************************************************************
        //for screen capture
        //************************************************************
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mMediaRecorder = new MediaRecorder();
        mProjectionManager = (MediaProjectionManager) getSystemService
                (Context.MEDIA_PROJECTION_SERVICE);
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
        destroyMediaProjection();
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

                    //Display mapping data to Bar graph
                    for (int i = 0; i < DATA_LENGTH; i++){
                    progressBarRaw[i].setProgress(BleService.getPressure()[i]*100/RANGE_11B);
                        mappedData[i] = Utils.map(BleService.getPressure()[i],calMin[i], calMax[i]);
                        progressBarCustom[i].setProgress(mappedData[i]);
                    }

                    //check if there is internet connection
                    ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                    boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

                    // write sensor readings to Firebase Realtime database
                    if (dataLogBox.isChecked()){
                        if (isConnected){
                            switch(dataLogOption) {
                                case OPTION_GAMING:
                                    mDatabase = FirebaseDatabase.getInstance().getReference(mDeviceName);
                                    for (int i=0; i<6; i++){
                                        mDatabase.child(mDeviceName).child("sensor"+(i+1)).setValue(BleService.getPressure()[i]);
                                    }
                                    //mDatabase.child(mDeviceName).child("sensor0").setValue(sensorReading[0]);
                                    mDatabase.child(mDeviceName).child("time").setValue(ServerValue.TIMESTAMP);
                                    mDatabase.child(mDeviceName).child("user").setValue(user);
                                    mDatabase.child(mDeviceName).child("session").setValue(sessionType);
                                    break;

                                case OPTION_ENGINEERING:
                                    mDatabase = FirebaseDatabase.getInstance().getReference(mDeviceName+"RTData");
                                    HashMap<String, Object> message = new HashMap<>();
                                    message.put("user", user);
                                    message.put("Time", ServerValue.TIMESTAMP);
                                    message.put("session", sessionType);
                                    for (int i=0; i<DATA_LENGTH; i++){
                                        message.put("s"+i, BleService.getPressure()[i]);
                                    }
                                    //message.put("S0", sensorReading[0]);
                                    mDatabase.push().setValue(message);
                                    break;

                                case OPTION_SCREEN_RECORDING:
                                    if (isScreenRecording){
                                            String[] dataString = new String[DATA_LENGTH+1];
                                            for (int i=0; i<DATA_LENGTH; i++){
                                                dataString[i+1] = String.valueOf(BleService.getPressure()[i]);
                                            }
                                            dataString[0] = String.valueOf((int)System.currentTimeMillis()-recordStartTime);
                                        try {
                                            writer.writeNext(dataString);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    break;
                            }

                        } else {
                            //send toast message saying NO INTERNET CONNECTION
                            Toast.makeText(GraphActivity.this, "NO internet Connect, no data sent", Toast.LENGTH_LONG).show();
                        }
                    }

                    //Display number on top of screen
                    //keep this is the end since sort will rearrange the Array sequency
                    //mSenseAverageText.setText(String.format("%d", displayNumber("Max2", sensorReading)/10));
                    mSenseAverageText.setText("Average 2 Max: " + Utils.displayNumber("Max2", mappedData));
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
    public void dialogReturnInfo(String username, String session, int selectedId) {
        user = username;
        sessionType = session;
        dataLogOption = selectedId;
        Toast.makeText(getApplicationContext(), "selectedId="+selectedId, Toast.LENGTH_LONG).show();
        //handle if Record Screen option is selected
        if (dataLogOption == OPTION_SCREEN_RECORDING){
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) +
                    ContextCompat.checkSelfPermission(GraphActivity.this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED){
                if (ActivityCompat.shouldShowRequestPermissionRationale
                        (GraphActivity.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                        ActivityCompat.shouldShowRequestPermissionRationale
                                (GraphActivity.this, android.Manifest.permission.RECORD_AUDIO)) {
                    Snackbar.make(findViewById(android.R.id.content), "Please enable Microphone and Storage permissions." ,
                            Snackbar.LENGTH_INDEFINITE).setAction("ENABLE",
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    ActivityCompat.requestPermissions(GraphActivity.this,
                                            new String[]{android.Manifest.permission
                                                    .WRITE_EXTERNAL_STORAGE, android.Manifest.permission.RECORD_AUDIO},
                                            REQUEST_PERMISSIONS);
                                }
                            }).show();
                } else {
                    ActivityCompat.requestPermissions(GraphActivity.this,
                            new String[]{android.Manifest.permission
                                    .WRITE_EXTERNAL_STORAGE, android.Manifest.permission.RECORD_AUDIO},
                            REQUEST_PERMISSIONS);
                }
            } else {
                //start screen recording
                isScreenRecording = true;
                textViewScreenRecording.setVisibility(View.VISIBLE);
                initRecorder();
                shareScreen();

            }
        }
    }
    /**
     * Handle when Start button get pushed
     *
     */
    public void goStart(View view) {
        if(isBleConnected){
            //START BUTTON get pushed
            //start receiving and displaying data
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
                Utils.enableCalButtons(true);

                //check if Calmin has non-zero value, set boarder for button
                if (CalMinState) calMinButton.setBackgroundResource(R.drawable.button_border);
                //check if Calmin has non-1023 value, set boarder for button
                if (CalMaxState) calMaxButton.setBackgroundResource(R.drawable.button_border);

            //PAUSE BUTTON gets pushed
            //stop receiving and display data
            } else {
                NotifyState = false;
                //set BLE cccd to false
                mBleService.writeNotification(false);
                barStartButton.setText("Start");
                dataLogBox.setEnabled(true);
                //disbale Calibration buttons
                Utils.enableCalButtons(false);
                //stop screen recording
                if (isScreenRecording){
                    mMediaRecorder.stop();
                    mMediaRecorder.reset();
                    Log.v(TAG, "Stopping Recording");
                    stopScreenSharing();
                    isScreenRecording = false;
                    textViewScreenRecording.setVisibility(View.INVISIBLE);
                    //close csv file
                    try {
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
        }else{
            Toast.makeText(getApplicationContext(), "Device is no longer available", Toast.LENGTH_LONG).show();
            finish();
        }
    }
    /**
     * Handle when Cal Min button get pushed
     *
     */
    public void calSetMin(View view){
        //take current reading and set them to calMin

        //check if higher than MAX
        for (int i=0; i<calMin.length; i++){
            if (BleService.getPressure()[i] > calMax[i] - 20){
                calMin[i] = calMax[i] - 20;
            }else{
                calMin[i] = BleService.getPressure()[i];
            }
        }
        Toast.makeText(getApplicationContext(), "set Min", Toast.LENGTH_SHORT).show();
        calMinButton.setBackgroundResource(R.drawable.button_border);
        CalMinState = true;
    }

    /**
     * Handle when Cal Clear button get pushed
     *
     */
    public void calClear(View view){
        //set calMin to 0; cal<ax to 1023
        for (int i=0; i<DATA_LENGTH; i++){
            calMax[i] = RANGE_11B;
            calMin[i] =0;}
        Toast.makeText(getApplicationContext(), "clear min Max", Toast.LENGTH_SHORT).show();
        calMaxButton.setBackgroundResource(R.drawable.button_calbrations);
        calMinButton.setBackgroundResource(R.drawable.button_calbrations);
        CalMaxState = false;
        CalMinState = false;
    }
    /**
     * Handle when Cal Set Max button get pushed
     *
     */
    public void calSetMax(View view){
        //take current reading and set them to calMax

        //check if smaller than MIN
        for (int i=0; i<calMax.length; i++){
            if (BleService.getPressure()[i] < calMin[i] + OFFSET){
                calMax[i] = calMin[i] + OFFSET;
            }else{
                calMax[i] = BleService.getPressure()[i];
            }
        }
        Toast.makeText(getApplicationContext(), "set Max", Toast.LENGTH_SHORT).show();
        calMaxButton.setBackgroundResource(R.drawable.button_border);
        CalMaxState = true;
    }

    //*****************************************************************
    //for screen capture
    //*****************************************************************
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this,
                    "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();
            return;
        }
        mMediaProjectionCallback = new MediaProjectionCallback();
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
    }

    private void shareScreen() {
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
            return;
        }
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
    }

    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("MainActivity",
                DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), null /*Callbacks*/, null
                /*Handler*/);
    }

    private void initRecorder() {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
            String currentDateTime = dateFormat.format(new Date());
            File saveDirectory = new File
                    (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()
                            ,File.separator);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            //need to add file separator again here for unknown reason, otherwise it will "Download" as filename, not folder
            mMediaRecorder.setOutputFile(saveDirectory + File.separator + currentDateTime + ".mp4");
            mMediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mMediaRecorder.setVideoEncodingBitRate(512 * 1000);
            mMediaRecorder.setVideoFrameRate(30);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int orientation = ORIENTATIONS.get(rotation + 90);
            mMediaRecorder.setOrientationHint(orientation);
            mMediaRecorder.prepare();

            //init save data to storage file
            fileCsvPathName = new File(saveDirectory,currentDateTime +".csv");
            FileWriter mfileWriter = new FileWriter(fileCsvPathName, true);
            writer = new CSVWriter(mfileWriter);
            dateFormat = new SimpleDateFormat("yyyy-MMdd-HH:mm:ss");
            currentDateTime = dateFormat.format(new Date());
            String[] title1 = {"time:", currentDateTime, "User:", user, "Session:", sessionType};
            writer.writeNext(title1);
            String[] title2 = {"Time","Sensor 1", "Sensor 2","Sensor 3","Sensor 4","Sensor 5","Sensor 6"};
            writer.writeNext(title2);
            recordStartTime = (int) System.currentTimeMillis();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            Log.v(TAG, "Recording Stopped");
            mMediaProjection = null;
            stopScreenSharing();
        }
    }

    private void stopScreenSharing() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        //mMediaRecorder.release(); //If used: mMediaRecorder object cannot
        // be reused again
        destroyMediaProjection();
    }

    private void destroyMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mMediaProjectionCallback);
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        Log.i(TAG, "MediaProjection Stopped");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSIONS: {
                if ((grantResults.length > 0) && (grantResults[0] +
                        grantResults[1]) == PackageManager.PERMISSION_GRANTED) {
                    //start screen recording
                    isScreenRecording = true;
                    textViewScreenRecording.setVisibility(View.VISIBLE);
                    initRecorder();
                    shareScreen();
                } else {
                    //mToggleButton.setChecked(false);
                    Snackbar.make(findViewById(android.R.id.content), R.string.label_permissions,
                            Snackbar.LENGTH_INDEFINITE).setAction("ENABLE",
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    Intent intent = new Intent();
                                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                                    intent.setData(Uri.parse("package:" + getPackageName()));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                                    startActivity(intent);
                                }
                            }).show();
                }
                return;
            }
        }
    }
}

