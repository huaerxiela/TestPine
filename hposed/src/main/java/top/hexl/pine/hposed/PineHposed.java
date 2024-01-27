package top.hexl.pine.hposed;

import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import de.hexl.android.hposed.IHposedHookLoadPackage;
import de.hexl.android.hposed.IHposedHookZygoteInit;
import de.hexl.android.hposed.IHposedMod;
import de.hexl.android.hposed.HposedBridge;
import de.hexl.android.hposed.HposedBridge.CopyOnWriteSortedSet;
import de.hexl.android.hposed.callbacks.XC_LoadPackage;

public final class PineHposed {
    public static final String TAG = "PineXposed";
    public static boolean disableHooks = false;
    public static boolean disableZygoteInitCallbacks = false;
    private static ExtHandler sExtHandler;

    public static ExtHandler getExtHandler() {
        return sExtHandler;
    }

    public static void setExtHandler(ExtHandler n) {
        sExtHandler = n;
    }

    private static final CopyOnWriteSortedSet<XC_LoadPackage> sLoadedPackageCallbacks = new CopyOnWriteSortedSet<>();

    private PineHposed() {
    }

    public static void loadModule(String module) {
        loadModule(new File(module));
    }

    public static void loadModule(File module) {
        loadModule(module, false);
    }

    public static void loadModule(File module, boolean startsSystemServer) {
        if (!module.exists()) {
            Log.e(TAG, "  File " + module + " does not exist");
            return;
        }
        ClassLoader initCl = PineHposed.class.getClassLoader();
        String modulePath = module.getAbsolutePath();
        ModuleClassLoader mcl = new ModuleClassLoader(modulePath, initCl);
        loadOpenedModule(modulePath, mcl, startsSystemServer);
    }

    public static void loadOpenedModule(String modulePath, ClassLoader mcl, boolean startsSystemServer) {
        if (!checkModule(mcl)) return;
        InputStream initIs;
        try {
            final String filename = "assets/xposed_init";
            if (mcl instanceof ModuleClassLoader) {
                // Fast and provided more error info
                URL url = ((ModuleClassLoader) mcl).findResource(filename);
                initIs = url != null ? url.openStream() : null;
            } else {
                initIs = mcl.getResourceAsStream(filename);
            }
            if (initIs == null) {
                Log.e(TAG, "  Failed to load module " + modulePath);
                Log.e(TAG, "  assets/xposed_init not found in the module APK");
                return;
            }
        } catch (IOException e) {
            Log.e(TAG, "  Failed to load module " + modulePath);
            Log.e(TAG, "  Cannot open assets/xposed_init in the module APK", e);
            return;
        }

        BufferedReader xposedInitReader = new BufferedReader(new InputStreamReader(initIs));
        try {
            String className;
            while ((className = xposedInitReader.readLine()) != null) {
                className = className.trim();
                if (className.isEmpty() || className.startsWith("#"))
                    continue;

                try {
                    Class<?> c = mcl.loadClass(className);

                    if (!IHposedMod.class.isAssignableFrom(c)) {
                        Log.e(TAG, "    Cannot load callback class " + className + " in module " + modulePath + " :");
                        Log.e(TAG, "    This class doesn't implement any sub-interface of IXposedMod, skipping it");
                        continue;
                    }

                    IHposedMod callback = (IHposedMod) c.newInstance();

                    if (callback instanceof IHposedHookZygoteInit && !disableZygoteInitCallbacks) {
                        IHposedHookZygoteInit.StartupParam param = new IHposedHookZygoteInit.StartupParam();
                        param.modulePath = modulePath;
                        param.startsSystemServer = startsSystemServer;
                        ((IHposedHookZygoteInit) callback).initZygote(param);
                    }

                    if (callback instanceof IHposedHookLoadPackage)
                        hookLoadPackage((IHposedHookLoadPackage) callback);

                    ExtHandler extHandler = sExtHandler;
                    if (extHandler != null)
                        extHandler.handle(callback);
                } catch (Throwable e) {
                    Log.e(TAG, "    Failed to load class " + className + " from module " + modulePath + " :", e);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "  Failed to load module " + modulePath);
            Log.e(TAG, "  Cannot read assets/xposed_init in the module APK", e);
        } finally {
            closeQuietly(xposedInitReader);
        }
    }

    public static boolean checkModule(ClassLoader mcl) {
        boolean fastPath = mcl instanceof ModuleClassLoader;
        try {
            String name = "com.android.tools.fd.runtime.BootstrapApplication";
            Class<?> cls = fastPath ? ((ModuleClassLoader) mcl).findClass(name) : mcl.loadClass(name);
            if (cls != null) {
                Log.e(TAG, "  Cannot load module, please disable \"Instant Run\" in Android Studio.");
                return false;
            }
        } catch (ClassNotFoundException ignored) {
        }

        boolean conflict;
        if (fastPath) {
            try {
                conflict = ((ModuleClassLoader) mcl).findClass(HposedBridge.class.getName()) != null;
            } catch (ClassNotFoundException ignored) {
                conflict = false;
            }
        } else {
            try {
                conflict = mcl.loadClass(HposedBridge.class.getName()) != HposedBridge.class;
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "  Cannot load module, XposedBridge is not available on the class loader", e);
                Log.e(TAG, "  Make sure you have set parent of the class loader");
                return false;
            }
        }
        if (conflict) {
            Log.e(TAG, "  Cannot load module:");
            Log.e(TAG, "  The Xposed API classes are compiled into the module's APK.");
            Log.e(TAG, "  This may cause strange issues and must be fixed by the module developer.");
            Log.e(TAG, "  For details, see: http://api.xposed.info/using.html");
            return false;
        }
        return true;
    }

    public static void hookLoadPackage(IHposedHookLoadPackage callback) {
        sLoadedPackageCallbacks.add(new XC_LoadPackage.Wrapper(callback));
    }

    public static void onPackageLoad(String packageName, String processName, ApplicationInfo appInfo,
                                     boolean isFirstApp, ClassLoader classLoader) {
        XC_LoadPackage.LoadPackageParam param = new XC_LoadPackage.LoadPackageParam(sLoadedPackageCallbacks);
        param.packageName = packageName;
        param.processName = processName;
        param.appInfo = appInfo;
        param.isFirstApplication = isFirstApp;
        param.classLoader = classLoader;
        XC_LoadPackage.callAll(param);
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null)
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
    }

    public interface ExtHandler {
        void handle(IHposedMod callback);
    }
}
