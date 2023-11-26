package com.hexl.testpine;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.lang.reflect.Method;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import top.canyie.pine.PineConfig;

public class Startup {

    public static Context createAppContext(Object activityThreadObj, Object loadedApkObj) {
        //        LoadedApk.makeApplication()
//        ContextImpl appContext = ContextImpl.createAppContext(mActivityThread, this);

        try {
            Utils.logD("createAppContext");

            @SuppressLint("PrivateApi") Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
//            @SuppressLint("DiscouragedPrivateApi") Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
//            currentActivityThreadMethod.setAccessible(true);
//
//            Object activityThreadObj = currentActivityThreadMethod.invoke(null);
//
//            Field boundApplicationField = activityThreadClass.getDeclaredField("mBoundApplication");
//            boundApplicationField.setAccessible(true);
//            Object mBoundApplication = boundApplicationField.get(activityThreadObj);   // AppBindData
//
//            Field infoField = mBoundApplication.getClass().getDeclaredField("info");   // info
//            infoField.setAccessible(true);
//            Object loadedApkObj = infoField.get(mBoundApplication);  // LoadedApk

            @SuppressLint("PrivateApi") Class<?> contextImplClass = Class.forName("android.app.ContextImpl");
            Method createAppContextMethod = contextImplClass.getDeclaredMethod("createAppContext", activityThreadClass, loadedApkObj.getClass());
            createAppContextMethod.setAccessible(true);

            Object context = createAppContextMethod.invoke(null, activityThreadObj, loadedApkObj);

            if (context instanceof Context) {
                return (Context) context;
            }

        } catch (Exception e) {
            e.printStackTrace();
            Utils.logD(Objects.requireNonNull(e.getMessage()));
        }
        return null;
    }

    @SuppressLint("PrivateApi")
    private static void startBootstrapHook(boolean isSystem){
        Utils.logD("startBootstrapHook1");
        try {
            PineConfig.debuggable = true;

            XposedHelpers.findAndHookConstructor(Class.forName("android.app.LoadedApk"),
                    Class.forName("android.app.ActivityThread"), ApplicationInfo.class, Class.forName("android.content.res.CompatibilityInfo"),
                    ClassLoader.class, boolean.class, boolean.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
                            Utils.logD("LoadedApk");

                            Context context = createAppContext(param.args[0], param.thisObject);
                            PineXposedExt.init(context);
                        }
                    });
        }catch (Exception e){
            Utils.logD("startBootstrapHook error: " + e.getMessage());
        }
    }

    public static void start(boolean isSystem){
        Utils.logD("start");
        startBootstrapHook(isSystem);
    }
}
