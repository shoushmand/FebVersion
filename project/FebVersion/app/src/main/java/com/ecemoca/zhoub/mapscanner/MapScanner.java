package com.ecemoca.zhoub.mapscanner;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.ecemoca.zhoub.mapscanner.acoustic.recording;
import com.ecemoca.zhoub.mapscanner.acoustic.sonic;
import com.ecemoca.zhoub.mapscanner.settings.guideLines;
import com.ecemoca.zhoub.mapscanner.settings.settingsActivity;
import com.ecemoca.zhoub.mapscanner.tracking.inertialTracking;
import com.ecemoca.zhoub.mapscanner.visualization.graphPlot;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingDeque;

public class MapScanner extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener{
    private DecimalFormat format = new DecimalFormat("0.00");
    private DecimalFormat format1 = new DecimalFormat("0");
    public final static String SHARED_PREFS_NAME="mapScannerSettings";
    private static String TAG = "PermissionDemo";
    private static final int RECORD_REQUEST_CODE = 101;
    // Setting parameters
    private float mapRange;        // length/width for map graph
    private float stepLength;      // step length for walking
    private float dCam = 0f;     // distance pop out from queue
    private float dMic = 0f;
    private int sensorScanRate;       // Inertial sensor scan rate
    private int pingInterval;      // Interval between pings
    private int carrierFreqency=8000;   // Carrier frequency
    private int bandFreqency=2000;      // Frequency band for modulation
    private int emitNumber;      // Continuous emitting
    // Variables
    private float[] distanceCam;              // Distance candidates
    private float[] distanceMic;
    private boolean singlePingFlag = false;
    private boolean contiFlag = false;
    private boolean sensorRunFlag = false;
    private SharedPreferences mPrefs;
    private ArrayList<Double> trace = new ArrayList<>();
    private ArrayList<Double> wallCam = new ArrayList<>();
    private ArrayList<Double> wallMic= new ArrayList<>();
    private Queue<Float> queueCam = new LinkedList<>();
    private Queue<Float> queueMic = new LinkedList<>();
    private graphPlot gv;
    int brSizeInc ;
    int sRate ;
    int brSize ;
    int bSize ;
    int sPeriod;
    short[] chirp ;

    // Create acoustic sensing signal design
    private sonic sp = new sonic(1, 79, 8000, 2000, 80, 200);

    private recording rc;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);

        mPrefs = MapScanner.this.getSharedPreferences(SHARED_PREFS_NAME,MODE_PRIVATE);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(mPrefs,null);

        gv = (graphPlot) findViewById(R.id.SinglePingSurface);
        // Show labels of graph
        setGraphLabels(mapRange);
        int permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission to record denied");
            makeRequest();
        }

        brSizeInc = sp.getBrSizeInc();
        sRate = sp.getsRate();
        brSize = sp.getBrSize();
         bSize = sp.getbSize();
         sPeriod = sp.getsPeriod();
         chirp = sp.getChirp();

        // Acoustic measurement
        buttonListener();

    }
    protected void makeRequest() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                RECORD_REQUEST_CODE);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case RECORD_REQUEST_CODE: {

                if (grantResults.length == 0
                        || grantResults[0] !=
                        PackageManager.PERMISSION_GRANTED) {

                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    Log.i(TAG, "Permission has been granted by user");
                }
                return;
            }
        }
    }
    // Option menu
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.menu_main,menu);
        return true;
    }

    // React to menu click
    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()){
            case R.id.settings:
                startActivity(new Intent(MapScanner.this, settingsActivity.class));
                return true;
            case R.id.guidelines:
                startActivity(new Intent(MapScanner.this, guideLines.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        mapRange = Float.valueOf(mPrefs.getString("mapSize","40"));
        stepLength = Float.valueOf(mPrefs.getString("stepLength","1"));
        sensorScanRate = Integer.valueOf(mPrefs.getString("sensorRate","20"));
        pingInterval = Integer.valueOf(mPrefs.getString("pingIntvl","50"));
        carrierFreqency = Integer.valueOf(mPrefs.getString("carriFreq","8000"));
        bandFreqency = Integer.valueOf(mPrefs.getString("bandFreq", "2000"));
        emitNumber = Integer.valueOf(mPrefs.getString("emitNumber","200"));
        sp = new sonic(1, 79, carrierFreqency, bandFreqency, 80, emitNumber);
        setGraphLabels(mapRange);
    //    Toast.makeText(getBaseContext(),"Settings Saved",Toast.LENGTH_LONG).show();
    }

    // Set graph x,y labels
    private void setGraphLabels(Float range) {
        TextView textView = (TextView) findViewById(R.id.textY0);
        textView.setText(format1.format(range / 2));
        textView = (TextView) findViewById(R.id.textY1);
        textView.setText(format1.format(range / 4));
        textView = (TextView) findViewById(R.id.textY2);
        textView.setText(format1.format(0));
        textView = (TextView) findViewById(R.id.textY3);
        textView.setText(format1.format(-range / 4));
        textView = (TextView) findViewById(R.id.textY4);
        textView.setText(format1.format(-range / 2));
        textView = (TextView) findViewById(R.id.textX1);
        textView.setText(format1.format(-range / 4));
        textView = (TextView) findViewById(R.id.textX2);
        textView.setText(format1.format(0));
        textView = (TextView) findViewById(R.id.textX3);
        textView.setText(format1.format(range / 4));
        textView = (TextView) findViewById(R.id.textX4);
        textView.setText(format1.format(range / 2));
    }

    // Sensor collect runnable
    public class sensorRun implements Runnable {
        private Thread t;
        private String threadName;
        private Context mContext;
        private SensorManager mSensorManager = null;
        private SensorEventListener mListener;
        private List<Sensor> currentDevice = new ArrayList<>();
        private float[] orientationVals = new float[3];
        private int steps;
        private int oneStep;

        public sensorRun(Context context,String name) {
            this.mContext = context;
            this.threadName = name;
        }

        public void run() {
            mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            if (mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null)
                currentDevice.add(mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR));
            else
                Toast.makeText(getBaseContext(),"Game rotation vector sensor not found!",Toast.LENGTH_LONG).show();
//            if (mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null)
//                currentDevice.add(mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR));
//            if (mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)
//                currentDevice.add(mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
//            if (mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null)
//                currentDevice.add(mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION));
            if (mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE) != null)
                currentDevice.add(mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE));
//            if (mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null)
//                currentDevice.add(mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
            if (mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null)
                currentDevice.add(mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR));
            if (mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null)
                currentDevice.add(mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE));

            mListener = new SensorEventListener() {
                public void onSensorChanged(SensorEvent event) {
                    if (sensorRunFlag) {
                        if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
                            // Convert the rotation-vector to a 4x4 matrix.
                            float[] mRotationMatrix = new float[16];
                            SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
                            SensorManager.getOrientation(mRotationMatrix, orientationVals);
                            // Optionally convert the result from radians to degrees
                            orientationVals[0] = (float) Math.toDegrees(orientationVals[0]);
                            orientationVals[1] = (float) Math.toDegrees(orientationVals[1]);
                            orientationVals[2] = (float) Math.toDegrees(orientationVals[2]);
                            TextView textX = (TextView) findViewById(R.id.textYaw);
                            textX.setText("Yaw: " + format.format(orientationVals[0]));
                            TextView textY = (TextView) findViewById(R.id.textRoll);
                            textY.setText("Roll: " + format.format(orientationVals[1]));
                            TextView textZ = (TextView) findViewById(R.id.textPitch);
                            textZ.setText("Pitch: " + format.format(orientationVals[2]));
                            // Show real time graph
                            if (trace.size() == 0) {
                                trace.add(0.0);
                                trace.add(0.0);
                                wallCam.add(0.0);
                                wallCam.add(0.0);
                                wallMic.add(0.0);
                                wallMic.add(0.0);
                                gv.setGraph(mapRange, trace, wallCam, wallMic);
                            } else {
                                oneStep--;
                                if (oneStep > 0) {
                                    if(!queueCam.isEmpty()) {
                                        dCam = queueCam.poll();
                                    }
                                    if(!queueMic.isEmpty()) {
                                        dMic = queueMic.poll();
                                    }
                                    double x = trace.get(trace.size() - 2) + Math.cos(Math.toRadians(orientationVals[0])) / sensorScanRate * stepLength;
                                    double y = trace.get(trace.size() - 1) + Math.sin(Math.toRadians(orientationVals[0])) / sensorScanRate * stepLength;
                                    double xCam = 0f;; double yCam = 0f;; double xMic = 0f;; double yMic = 0f;;
                                    int delay = 49;
                                    if(trace.size()>delay){
                                        if(dCam!=0){
                                            xCam = trace.get(trace.size() - delay-1) - dCam*Math.sin(Math.toRadians(-orientationVals[0])+3.14);
                                            yCam = trace.get(trace.size() - delay) - dCam*Math.cos(Math.toRadians(-orientationVals[0])+3.14);
                                        }
                                        if(dMic!=0){
                                            xMic = trace.get(trace.size() - delay-1) + dMic*Math.sin(Math.toRadians(-orientationVals[0])+3.14);
                                            yMic = trace.get(trace.size() - delay) + dMic*Math.cos(Math.toRadians(-orientationVals[0])+3.14);
                                        }
                                    }
                                    trace.add(x);
                                    trace.add(y);
                                    wallCam.add(xCam);
                                    wallCam.add(yCam);
                                    wallMic.add(xMic);
                                    wallMic.add(yMic);
                                    textX = (TextView) findViewById(R.id.textCordx);
                                    textX.setText("X: " + format.format(x) + "m");
                                    textY = (TextView) findViewById(R.id.textCordy);
                                    textY.setText("Y: " + format.format(-y) + "m");
                                    if(mapRange/2-Math.abs(x)<5 || mapRange/2-Math.abs(y)<5)
                                        mapRange = mapRange+20;
                                    setGraphLabels(mapRange);
                                    gv.setGraph(mapRange, trace, wallCam, wallMic);
                                }
                            }
                        }

                        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                            // Convert the rotation-vector to a 4x4 matrix.
                            float[] mRotationMatrix = new float[16];
                            SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
                            SensorManager.getOrientation(mRotationMatrix, orientationVals);
                            // Optionally convert the result from radians to degrees
                            orientationVals[0] = (float) Math.toDegrees(orientationVals[0]);
                            orientationVals[1] = (float) Math.toDegrees(orientationVals[1]);
                            orientationVals[2] = (float) Math.toDegrees(orientationVals[2]);
                            TextView textX = (TextView) findViewById(R.id.textYaw);
                            textX.setText("Yaw: " + format.format(orientationVals[0]));
                            TextView textY = (TextView) findViewById(R.id.textRoll);
                            textY.setText("Roll: " + format.format(orientationVals[1]));
                            TextView textZ = (TextView) findViewById(R.id.textPitch);
                            textZ.setText("Pitch: " + format.format(orientationVals[2]));
                            // Show real time graph
                            if (mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) == null) {
                                if (trace.size() == 0) {
                                    trace.add(0.0);
                                    trace.add(0.0);
                                    wallCam.add(0.0);
                                    wallCam.add(0.0);
                                    gv.setGraph(mapRange, trace, wallCam,wallMic);
                                } else {
                                    oneStep--;
                                    if (oneStep > 0) {
                                        if(!queueCam.isEmpty()) {
                                            dCam = queueCam.poll();
                                        }
                                        double x = trace.get(trace.size() - 2) + Math.cos(Math.toRadians(orientationVals[0])) / sensorScanRate * stepLength;
                                        double y = trace.get(trace.size() - 1) + Math.sin(Math.toRadians(orientationVals[0])) / sensorScanRate * stepLength;
                                        double xCam; double yCam; double xMic; double yMic;
                                        int delay = 50;
                                        if(trace.size()<delay){
                                            xCam = 0f;
                                            yCam = 0f;
                                            xMic = 0f;
                                            yMic = 0f;
                                        }else{
                                            xCam = trace.get(trace.size() - delay-1) - dCam*Math.sin(Math.toRadians(-orientationVals[0])+3.14);
                                            yCam = trace.get(trace.size() - delay) - dCam*Math.cos(Math.toRadians(-orientationVals[0])+3.14);
                                            xMic = trace.get(trace.size() - delay-1) + dMic*Math.sin(Math.toRadians(-orientationVals[0])+3.14);
                                            yMic = trace.get(trace.size() - delay) + dMic*Math.cos(Math.toRadians(-orientationVals[0])+3.14);
                                        }
                                        trace.add(x);
                                        trace.add(y);
                                        wallCam.add(xCam);
                                        wallCam.add(yCam);
                                        wallMic.add(xMic);
                                        wallMic.add(yMic);
                                        textX = (TextView) findViewById(R.id.textCordx);
                                        textX.setText("X: " + format.format(x) + "m");
                                        textY = (TextView) findViewById(R.id.textCordy);
                                        textY.setText("Y: " + format.format(-y) + "m");
                                        if(mapRange/2-Math.abs(x)<5 || mapRange/2-Math.abs(y)<5) // Resize map size if it's too small
                                            mapRange = mapRange+20;
                                        setGraphLabels(mapRange);
                                        gv.setGraph(mapRange, trace, wallCam, wallMic);
                                    }
                                }
                            }
                        }

                        if (event.sensor == mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)) {
                            TextView textX = (TextView) findViewById(R.id.textAccx);
                            textX.setText("Accx: " + format.format(event.values[0]));
                            TextView textY = (TextView) findViewById(R.id.textAccy);
                            textY.setText("Accy: " + format.format(event.values[1]));
                            TextView textZ = (TextView) findViewById(R.id.textAccz);
                            textZ.setText("Accz: " + format.format(event.values[2]));
                        }

                        if (event.sensor == mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)) {
                            TextView textX = (TextView) findViewById(R.id.textLAx);
                            textX.setText("Linx: " + format.format(event.values[0]));
                            TextView textY = (TextView) findViewById(R.id.textLAy);
                            textY.setText("Liny: " + format.format(event.values[1]));
                            TextView textZ = (TextView) findViewById(R.id.textLAz);
                            textZ.setText("Linz: " + format.format(event.values[2]));
                            if(Math.abs(event.values[2])>2 && mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) == null)
                                oneStep = sensorScanRate;
                        }

                        if (event.sensor == mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)) {
                            TextView textX = (TextView) findViewById(R.id.textTemp);
                            textX.setText("Temp: " + format.format(event.values[0]));
                        }

                        if (event.sensor == mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)) {
                            TextView textX = (TextView) findViewById(R.id.textMagx);
                            textX.setText("Magx: " + format.format(event.values[0]));
                            TextView textY = (TextView) findViewById(R.id.textMagy);
                            textY.setText("Magy: " + format.format(event.values[1]));
                            TextView textZ = (TextView) findViewById(R.id.textMagz);
                            textZ.setText("Magz: " + format.format(event.values[2]));
                        }

                        if (event.sensor == mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)) {
                            float step = event.values[0];
                            steps++;
                            oneStep = sensorScanRate;
                            TextView textX = (TextView) findViewById(R.id.textStep);
                            textX.setText("Steps: " + steps);
                        }

                        if (event.sensor == mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)) {
                            float p = event.values[0];
                            float height = (float) ((1-Math.pow(p/1013.25,0.190284))*44307.69);
                            TextView textX = (TextView) findViewById(R.id.textHeight);
                            textX.setText("Alt: " + format.format(height)+"m");
                        }
                    }
                }
                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
            };

            if(sensorRunFlag){
                for (Sensor insert : currentDevice) {
                    mSensorManager.registerListener(mListener, insert, sensorScanRate, 100);
                }
            }

        }

        public void start() {
            if (t == null) {
                t = new Thread(this, threadName);
                t.start();
            }
        }

        public void stop(){
            for (Sensor insert : currentDevice) {
                mSensorManager.unregisterListener(mListener, insert);
                currentDevice.clear();
            }
        }
    }

    // Run continuous acoustic sensing
    public class contPing implements Runnable {
        private Thread t;
        private String threadName;
        BlockingDeque<float[]> distance;
        contPing(String name) {
            threadName = name;

        }
        public void setDistance(BlockingDeque<float[]> d){
            distance = d;
        }
        @Override
        public void run() {
            while (contiFlag) {
                boolean cam = false;
                boolean mic = false;
                sp.ping();
                Log.d("here", "error1");
                if (!distance.isEmpty()) {
                    distanceCam = distance.poll();
                    Log.d("distancecam", "" + distanceCam.length);
                    queueCam.clear();
                    cam = true;
                    for (float aDistanceCam : distanceCam) {
                        queueCam.add(aDistanceCam);
                        queueCam.add(aDistanceCam);
                        queueCam.add(aDistanceCam);
                        queueCam.add(aDistanceCam);
                        queueCam.add(aDistanceCam);
                    }
                }


                Log.d("queue remaining size:", "" + queueCam.size());
                if (!distance.isEmpty()) {
                    distanceMic = distance.poll();
                    Log.d("here","mic");
                    queueMic.clear();
                    mic = true;
                    for (float aDistanceMic : distanceMic) {
                        queueMic.add(aDistanceMic);
                        queueMic.add(aDistanceMic);
                        queueMic.add(aDistanceMic);
                        queueMic.add(aDistanceMic);
                        queueMic.add(aDistanceMic);
                    }
                }

                if (mic && cam) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView textTop = (TextView) findViewById(R.id.textTop);
                            TextView textBot = (TextView) findViewById(R.id.textBot);
                            String stringTop = "Top: " + format.format(distanceCam[0]) + "m," + format.format(distanceCam[1]) + "m," + format.format(distanceCam[2]) + "m," + format.format(distanceCam[3]) + "m," + format.format(distanceCam[4]) + "m";
                            String stringBot = "Bot: " + format.format(distanceMic[0]) + "m," + format.format(distanceMic[1]) + "m," + format.format(distanceMic[2]) + "m," + format.format(distanceMic[3]) + "m," + format.format(distanceMic[4]) + "m";
                            if (stringTop != null) {
                                textTop.setText(stringTop);
                                textBot.setText(stringBot);
                            }
                        }
                    });

               /* try {
                    Thread.sleep(pingInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }*/
                }
            }
        }
           /* public void stop(){
                sp.stop();
            }*/
          /*if (singlePingFlag) {
                singlePingFlag = false;
                sp.ping();
                distanceCam = sp.getDistanceCam();
                distanceMic = sp.getDistanceMic();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView textTop = (TextView) findViewById(R.id.textTop);
                        TextView textBot = (TextView) findViewById(R.id.textBot);
                        String stringTop = "Top: " + format.format(distanceCam[0]) + "m," + format.format(distanceCam[1]) + "m," + format.format(distanceCam[2]) + "m," + format.format(distanceCam[3]) + "m," + format.format(distanceCam[4]) + "m";
                        String stringBot = "Bot: " + format.format(distanceMic[0]) + "m," + format.format(distanceMic[1]) + "m," + format.format(distanceMic[2]) + "m," + format.format(distanceMic[3]) + "m," + format.format(distanceMic[4]) + "m";
                        textTop.setText(stringTop);
                        textBot.setText(stringBot);
                        final Button emitButton = (Button) findViewById(R.id.buttonEmit);
                        emitButton.setEnabled(true);
                    }
                });
            }*/


        public void start() {
            if (t == null) {
                t = new Thread(this, threadName);
                t.start();
            }
        }
    }

    // Button listener
    private void buttonListener() {
        final Button emitButton = (Button) findViewById(R.id.buttonEmit);
        final Button contiButton = (Button) findViewById(R.id.buttonConti);
        final Button inertialButton = (Button) findViewById(R.id.buttonInertial);
        final Button clearButton = (Button) findViewById(R.id.buttonClear);
        final Button loopButton = (Button) findViewById(R.id.buttonLoop);

        assert emitButton != null;
        emitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                contPing ping = new contPing("pingThread");
                if(!singlePingFlag){
                    emitButton.setEnabled(false);
                    ping.start();
                    singlePingFlag = true;
                }
            }
        });

        assert contiButton != null;
        contiButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v){
                contPing pingConti = new contPing("pingThreadConti");
                if(!contiFlag){

                   rc = new recording(brSizeInc,sRate, brSize, bSize, sPeriod, chirp);
                    rc.start();
                    pingConti.setDistance(rc.getDistance());
                    contiButton.setText("STOP");
                    pingConti.start();
                    contiFlag = true;
                }
                else{
                    contiButton.setText("CONTI");
                   // pingConti.stop();
                    sp.stop();
                    rc.stopRecording();
                    contiFlag = false;
                }
            }
        });

        inertialButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                sensorRun sensor = new sensorRun(MapScanner.this,"sensorThread");
                if(!sensorRunFlag){
                    inertialButton.setText("STOP");
                    sensor.start();
                    sensorRunFlag = true;
                }
                else{
                    inertialButton.setText("START");
                    sensorRunFlag = false;
                    sensor.stop();
                }
            }
        });

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                trace.clear();
                wallCam.clear();
                mapRange = Float.valueOf(mPrefs.getString("mapSize","40"));
                setGraphLabels(mapRange);
                gv.setGraph(mapRange, trace, wallCam,wallMic);
            }
        });

        loopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                inertialTracking tracking = new inertialTracking(trace,wallCam,wallMic);
                trace.clear();
                trace = tracking.loopClosure();
                gv.setGraph(mapRange, trace, wallCam,wallMic);
            }
        });

    }

    // Destroy
    public void onDestroy(){
        super.onDestroy();
        contiFlag = false;
        sensorRunFlag = false;
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        this.finishAffinity();
    }

}

