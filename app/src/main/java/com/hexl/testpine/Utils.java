package com.hexl.testpine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

public class Utils {

    public static final String LOG_TAG = "PineFramework";
    public static final boolean isMIUI = false;
//    public static final boolean isMIUI = !TextUtils.isEmpty(SystemProperties.get("ro.miui.ui.version.name"));
//    public static final boolean isLENOVO = !TextUtils.isEmpty(SystemProperties.get("ro.lenovo.region"));

    public static void logD(Object msg) {
        Log.d(LOG_TAG, msg.toString());
    }

    public static void logD(String msg, Throwable throwable) {
        Log.d(LOG_TAG, msg, throwable);
    }

    public static void logW(String msg) {
        Log.w(LOG_TAG, msg);
    }

    public static void logW(String msg, Throwable throwable) {
        Log.w(LOG_TAG, msg, throwable);
    }

    public static void logI(String msg) {
        Log.i(LOG_TAG, msg);
    }

    public static void logI(String msg, Throwable throwable) {
        Log.i(LOG_TAG, msg, throwable);
    }

    public static void logE(String msg) {
        Log.e(LOG_TAG, msg);
    }

    public static void logE(String msg, Throwable throwable) {
        Log.e(LOG_TAG, msg, throwable);
    }

    public static void showToast(Context context, String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
