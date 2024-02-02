package com.example.g_force_meter.main;

import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class CalcThread extends Thread {
    public static final int CALIBRATE = 0;
    public static final int SENSOR_STOP = 1;
    public static final int GRAVITY_CHANGE = 2;
    public static final int ACCELERATION = 3;
    public static final int BRAKING = 4;
    public static final int RIGHT_FORCE = 5;
    public static final int LEFT_FORCE = 6;

    private float accelMinGVal, accelMaxGVal, accelLastGVal;
    private float brakeMinGVal, brakeMaxGVal, brakeLastGVal;
    private float rightMinGVal, rightMaxGVal, rightLastGVal;
    private float leftMinGVal, leftMaxGVal, leftLastGVal;

    private float minGVal, maxGVal, lastGVal;
    private float gOffset;

    private Handler uiHandler;
    private static Handler mHandler;

    private String TAG;

    CalcThread(String TAG, Handler uiHandler) {
        super("CalcThread");
        this.uiHandler = uiHandler;
        this.TAG = TAG;
    }

    public Handler getHandler() {
        return mHandler;
    }

    private class MyHandler extends Handler {
        private float calcGVal(float value) {
            Log.d(TAG, String.format("Value of value: %+f", value));

            float GRAVITY = SensorManager.GRAVITY_EARTH;
            float curGVal = value;
            curGVal /= GRAVITY;
            Log.d(TAG, String.format("Value of curGVal: %+f", curGVal));

            return curGVal;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ACCELERATION:
                case BRAKING:
                case RIGHT_FORCE:
                case LEFT_FORCE:
                    handleForceChange(msg.what, (Float) msg.obj);
                    break;
                case CALIBRATE:
                    Log.d(TAG, "Calibrating");
                    mHandler.removeMessages(GRAVITY_CHANGE);

                    gOffset = (Float) msg.obj - SensorManager.GRAVITY_EARTH;
                    lastGVal = minGVal = maxGVal = 1.0f;
                    updateTextViews();
                    break;
                case GRAVITY_CHANGE:
                    mHandler.removeMessages(GRAVITY_CHANGE);

                    Log.d(TAG, String.format("Value of msg.obj: %+f", (Float) msg.obj));
                    Log.d(TAG, String.format("Offset: %+.2f", gOffset));

                    float curGVal = ((Float) msg.obj - gOffset) / SensorManager.GRAVITY_EARTH;

                    Log.d(TAG, String.format("Value of curGVal after offset: %.2f", curGVal));
                    Log.d(TAG, String.format("Value of lastGVal: %+.2f", lastGVal));

                    if (curGVal >= (lastGVal - 0.01) || curGVal <= (lastGVal + 0.01)) {
                        lastGVal = curGVal;
                        Log.d(TAG, "Updating sensor data");

                        minGVal = (minGVal > lastGVal) ? lastGVal : minGVal;
                        maxGVal = (maxGVal < lastGVal) ? lastGVal : maxGVal;
                        updateTextViews();
                    } else {
                        Log.v(TAG, "Difference not enough");
                    }

                    break;
                case SENSOR_STOP:
                    Log.d(TAG, "Sensor stopping");
                    Looper.myLooper().quit();
                    mHandler = null;
                    break;
                default:
                    Log.e(TAG, "Got unknown message type " + msg.what);
                    break;
            }
        }

        private void handleForceChange(int forceType, float value) {
            mHandler.removeMessages(forceType);

            float curGVal = ((value - gOffset) / SensorManager.GRAVITY_EARTH);

            float[] minMaxLast = getMinMaxLastValues(forceType);
            float minGVal = minMaxLast[0];
            float maxGVal = minMaxLast[1];
            float lastGVal = minMaxLast[2];

            if (curGVal >= (lastGVal - 0.01) || curGVal <= (lastGVal + 0.01)) {
                lastGVal = curGVal;
                updateForceTextViews(forceType, lastGVal);

                setMinAndMaxValues(forceType, minGVal, maxGVal, lastGVal);
            } else {
                Log.v(TAG, "Difference not enough");
            }
        }

        private void updateForceTextViews(int forceType, float value) {
            Message m = uiHandler.obtainMessage();
            String[] values = new String[3];
            values[0] = String.format("%+.2f", value);

            // Update the corresponding TextViews based on forceType
            switch (forceType) {
                case ACCELERATION:
                    values[1] = String.format("Acceleration: %+.2f", value);
                    break;
                case BRAKING:
                    values[1] = String.format("Braking: %+.2f", value);
                    break;
                case RIGHT_FORCE:
                    values[1] = String.format("Right Force: %+.2f", value);
                    break;
                case LEFT_FORCE:
                    values[1] = String.format("Left Force: %+.2f", value);
                    break;
            }

            values[2] = String.format("%+.2f", getMaxValue(forceType));
            m.obj = values;
            m.sendToTarget();
        }


        private float getMinValue(int forceType) {
            switch (forceType) {
                case ACCELERATION:
                    return accelMinGVal;
                case BRAKING:
                    return brakeMinGVal;
                case RIGHT_FORCE:
                    return rightMinGVal;
                case LEFT_FORCE:
                    return leftMinGVal;
                default:
                    return 0;
            }
        }

        private float getMaxValue(int forceType) {
            switch (forceType) {
                case ACCELERATION:
                    return accelMaxGVal;
                case BRAKING:
                    return brakeMaxGVal;
                case RIGHT_FORCE:
                    return rightMaxGVal;
                case LEFT_FORCE:
                    return leftMaxGVal;
                default:
                    return 0;
            }
        }

        private float[] getMinMaxLastValues(int forceType) {
            switch (forceType) {
                case ACCELERATION:
                    return new float[]{accelMinGVal, accelMaxGVal, accelLastGVal};
                case BRAKING:
                    return new float[]{brakeMinGVal, brakeMaxGVal, brakeLastGVal};
                case RIGHT_FORCE:
                    return new float[]{rightMinGVal, rightMaxGVal, rightLastGVal};
                case LEFT_FORCE:
                    return new float[]{leftMinGVal, leftMaxGVal, leftLastGVal};
                default:
                    return new float[]{0, 0, 0};
            }
        }

        private void setMinAndMaxValues(int forceType, float minGVal, float maxGVal, float lastGVal) {
            switch (forceType) {
                case ACCELERATION:
                    accelMinGVal = (minGVal > lastGVal) ? lastGVal : minGVal;
                    accelMaxGVal = (maxGVal < lastGVal) ? lastGVal : maxGVal;
                    break;
                case BRAKING:
                    brakeMinGVal = (minGVal > lastGVal) ? lastGVal : minGVal;
                    brakeMaxGVal = (maxGVal < lastGVal) ? lastGVal : maxGVal;
                    break;
                case RIGHT_FORCE:
                    rightMinGVal = (minGVal > lastGVal) ? lastGVal : minGVal;
                    rightMaxGVal = (maxGVal < lastGVal) ? lastGVal : maxGVal;
                    break;
                case LEFT_FORCE:
                    leftMinGVal = (minGVal > lastGVal) ? lastGVal : minGVal;
                    leftMaxGVal = (maxGVal < lastGVal) ? lastGVal : maxGVal;
                    break;
            }
        }
    }

    private void updateTextViews() {
        Message m = uiHandler.obtainMessage();
        String[] values = {
                String.format("%+.2f", lastGVal),
                String.format("%+.2f", minGVal),
                String.format("%+.2f", maxGVal)
        };
        m.obj = values;
        m.sendToTarget();
    }

    @Override
    public void run() {
        Looper.prepare();
        Log.v(TAG, "Starting CalcThread");
        mHandler = new MyHandler();
        Looper.loop();
        Log.v(TAG, "Past Looper.loop()");
    }
}
