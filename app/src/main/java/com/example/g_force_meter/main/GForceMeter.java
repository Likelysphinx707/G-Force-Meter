package com.example.g_force_meter.main;

import android.app.Activity;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.util.Log;

import com.example.g_force_meter.R;

public class GForceMeter extends Activity implements SensorEventListener, OnClickListener {
    private static CalcThread calcThread;

    private static TextView gCurTxt, gMinTxt, gMaxTxt;
    private static Button calibrateButton, exitButton;
    private static String TAG;
    private float lastUpdate;

    private static SensorManager sensorMgr = null;

    private static boolean calibrate = false;

    private FrameLayout circleContainer;
    private CircleView circleView;
    private View redDot;


    private class MyHandler extends Handler {
        public void handleMessage(Message msg) {
            String[] values = (String[]) msg.obj;
            gCurTxt.setText(values[0]);
            gMinTxt.setText(values[1]);
            gMaxTxt.setText(values[2]);

            // Update the UI for the circle and moving red dot
            updateCircleAndDot(Float.parseFloat(values[0]));
        }

        private CircleView circleView;
        private View redDotView;

        private void updateCircleAndDot(float currentGForce) {
            // Get references to the parent layout
            FrameLayout parentLayout = findViewById(R.id.circleContainer);

            // Create or get the existing CircleView
            if (circleView == null) {
                circleView = new CircleView(GForceMeter.this);
                RelativeLayout.LayoutParams circleParams = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                circleView.setLayoutParams(circleParams);

                // Add the CircleView to the parent layout
                parentLayout.addView(circleView);
            }

            // Calculate the position of the red dot within the circle based on currentGForce
            float maxGForce = 1.5f; // Adjust this value based on your requirements
            float normalizedGForce = currentGForce / maxGForce;
            float angle = 360 * normalizedGForce;

            // Create or get the existing redDotView
            if (redDot == null) {
                redDot = new View(GForceMeter.this);
                int dotSize = 24; // Set the size of the red dot
                RelativeLayout.LayoutParams dotParams = new RelativeLayout.LayoutParams(dotSize, dotSize);
                dotParams.addRule(RelativeLayout.CENTER_IN_PARENT);
                redDot.setLayoutParams(dotParams);
                redDot.setBackgroundColor(Color.RED); // Set the color of the red dot

                // Add the red dot to the parent layout
                parentLayout.addView(redDot);
            }

            // Rotate the red dot based on the angle
            redDot.setRotation(angle);
        }


    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        TAG = getString(R.string.app_name);

        calibrateButton = findViewById(R.id.CalibrateButton);
        calibrateButton.setOnClickListener(this);

        exitButton = findViewById(R.id.ExitButton);
        exitButton.setOnClickListener(this);

        gCurTxt = findViewById(R.id.gCurTxt);
        gMinTxt = findViewById(R.id.gMinTxt);
        gMaxTxt = findViewById(R.id.gMaxTxt);

        TAG = getString(R.string.app_name);

        lastUpdate = 0;
        calibrate = true;

        Log.d(TAG, "onCreate done");
    }

    public void onAccuracyChanged(Sensor arg0, int arg1) {
    }

    public void onSensorChanged(SensorEvent arg0) {

        switch (arg0.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                float currTime = System.currentTimeMillis();
                if (currTime < (lastUpdate + 500))
                    return;

                lastUpdate = currTime;

                if (calcThread == null) {
                    Log.d(TAG, "CalcThread not running");
                    return;
                }

                Handler h = calcThread.getHandler();
                if (h == null) {
                    Log.e(TAG, "Failed to get CalcThread Handler");
                    return;
                }

                Message m = Message.obtain(h);
                if (m == null) {
                    Log.e(TAG, "Failed to get Message instance");
                    return;
                }

                m.obj = (Object) arg0.values[1];
                if (calibrate) {
                    calibrate = false;

                    m.what = CalcThread.CALIBRATE;
                    h.sendMessageAtFrontOfQueue(m);
                } else {
                    // Determine the force type based on your logic
                    int forceType = determineForceType((float) arg0.values[1]);
                    m.what = forceType;
                    m.sendToTarget();
                }

                break;
        }
    }

    private int determineForceType(float accelerationValue) {
        // Implement your logic to determine the force type based on the acceleration value
        // You might use thresholds or other criteria to categorize the force type

        if (accelerationValue > 0.5) {
            return CalcThread.ACCELERATION;
        } else if (accelerationValue < -0.5) {
            return CalcThread.BRAKING;
        } else if (accelerationValue > 0) {
            return CalcThread.RIGHT_FORCE;
        } else if (accelerationValue < 0) {
            return CalcThread.LEFT_FORCE;
        } else {
            // Default case, you can adjust it based on your requirements
            return CalcThread.ACCELERATION;
        }
    }


    private void startSensing()
    {
        if(calcThread == null)
        {
            calcThread = new CalcThread(TAG, new MyHandler());
            calcThread.start();
        }

        if(sensorMgr == null)
        {
            sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor sensor = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

            if (!sensorMgr.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI))
            {
                // on accelerometer on this device
                Log.e(TAG, "No accelerometer available");
                sensorMgr.unregisterListener(this, sensor);
                sensorMgr = null;
                return;
            }
        }

        calibrate = true;
    }

    private void stopSensing()
    {
        if(sensorMgr != null)
        {
            sensorMgr.unregisterListener(this);
            sensorMgr = null;
        }

        if(calcThread != null)
        {
            Handler h = calcThread.getHandler();
            if(h != null)
            {
                Message m = Message.obtain(h);
                if(m != null)
                {
                    m.what = CalcThread.SENSOR_STOP;
                    h.sendMessageAtFrontOfQueue(m);
                }
            }

            calcThread = null;
        }
    }

    public void onClick(View arg0) {
        // TODO Auto-generated method stub

        if(arg0.getId() == R.id.CalibrateButton)
        {
            Log.d(TAG, "----Calibrate button clicked----");
            calibrate = true;
        }
        else if(arg0.getId() == R.id.ExitButton)
        {
            Log.d(TAG, "----Exit button clicked----");
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "----onPause called----");
        stopSensing();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "----onResume called----");
        startSensing();
    }
}