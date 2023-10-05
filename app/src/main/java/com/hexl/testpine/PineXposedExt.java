package com.hexl.testpine;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import top.canyie.pine.PineConfig;
import top.canyie.pine.xposed.ModuleClassLoader;
import top.canyie.pine.xposed.PineXposed;

public class PineXposedExt {

    public static void init(Context context){
        Utils.logD("init: context = " + context);
        List<String> apkPathList = getModulePathListByConfig(context);
        if (!apkPathList.isEmpty()){
            loadModuleFromList(apkPathList);
            onPackageLoad(context, true);
        }
    }

    public static List<String> getModulePathListByConfig(Context context) {
        PackageManager pm = context.getPackageManager();
        List<String> modulePathList = new ArrayList<>();
        String configPath="/data/local/tmp/pine_module.conf";
        try {
            BufferedReader br = new BufferedReader(new FileReader(configPath));
            String line;
            while ((line = br.readLine()) != null) {
                if(line.length()>=2){
                    try {
                        Utils.logD("getModulePathListByConfig: " + line);
                        ApplicationInfo app = pm.getApplicationInfo(line, PackageManager.GET_META_DATA);
                        String moduleInfo = getModuleInfo(app);
                        Utils.logD("getModulePathListByConfig: " + moduleInfo);
                        if (!TextUtils.isEmpty(moduleInfo)){
                            modulePathList.add(moduleInfo);
                        }
                    }catch (Exception ex){
                        Utils.logE("getModulePathListByConfig err1:" + ex.getMessage());
                    }
                }
            }
            br.close();
        }
        catch (Exception ex) {
            Utils.logE("getModulePathListByConfig err2:" + ex.getMessage());
        }
        return modulePathList;
    }

    public static String getModuleInfo(ApplicationInfo app){
        Utils.logD("getModuleInfo: " + app + " , " + app.enabled + " , " + app.metaData);
        if (!app.enabled){
            return null;
        }
        if (app.metaData == null){
            return null;
        }
        try {
            boolean isModuleResult = app.metaData.getBoolean("xposedmodule", false);
            Utils.logD(" --------------find " + app.packageName + " -> isModule = " + isModuleResult);
            if (isModuleResult){
                String apkPath = app.publicSourceDir;
                if (TextUtils.isEmpty(apkPath)) {
                    apkPath = app.sourceDir;
                }
                if (!TextUtils.isEmpty(apkPath)) {
                    Utils.logD(" query installed module path -> " + apkPath);
                    Utils.logD(" query installed module path nativeLibraryDir -> " + app.nativeLibraryDir);
                    return String.format("%s&%s", apkPath, app.nativeLibraryDir);
                }
            }
        }catch (Exception e){
            Utils.logD(" find " + app.packageName + " -> Exception = " + e.getMessage());
        }
        return null;
    }


    public static void loadModuleFromList(List<String> moduleList) {
        for (String module: moduleList) {
            String[] moduleInfo = module.split("&");
            loadModule(new File(moduleInfo[0]), moduleInfo[1]);
        }
    }

    public static void onPackageLoad(Context context, boolean isFirstApp) {
        PineConfig.debug = false; // 是否debug，true会输出较详细log
        PineConfig.debuggable = isApkInDebug(context);
        Utils.logD("onPackageLoad: " + context.getPackageName() + " , isApkInDebug: " + isApkInDebug(context));

        String packageName = context.getPackageName();
        ApplicationInfo appInfo = context.getApplicationInfo();
        String processName = getCurrentProcessName(appInfo);
        ClassLoader classLoader = context.getClassLoader();

        PineXposed.onPackageLoad(packageName, processName, appInfo, isFirstApp, classLoader);
    }

    public static boolean isApkInDebug(Context context) {
        try {
            ApplicationInfo info = context.getApplicationInfo();
            return (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String currentProcessName = null;

    private static String getCurrentProcessName(ApplicationInfo applicationInfo) {
        if (currentProcessName != null) return currentProcessName;

        currentProcessName = applicationInfo.packageName;
        try {
            @SuppressLint("PrivateApi") Class<?> activityThread_clazz = Class.forName("android.app.ActivityThread");
            @SuppressLint("DiscouragedPrivateApi") Method method = activityThread_clazz.getDeclaredMethod("currentProcessName");
            method.setAccessible(true);
            currentProcessName = (String) method.invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return currentProcessName;
    }

    public static void loadModule(File module, String nativePath) {
        loadModule(module, nativePath, false);
    }

    public static void loadModule(File module, String nativePath, boolean startsSystemServer) {
        if (!module.exists()) {
            Utils.logE("  File " + module + " does not exist");
            return;
        }
        ClassLoader initCl = PineXposed.class.getClassLoader();
        String modulePath = module.getAbsolutePath();
        ModuleClassLoader mcl = new ModuleClassLoader(modulePath, nativePath, initCl);
        Utils.logD("  new ModuleClassLoader = " + mcl);
        PineXposed.loadOpenedModule(modulePath, mcl, startsSystemServer);
    }
}
