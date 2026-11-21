package deltazero.amarok.xposed.hooks;

import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;

import com.github.kyuubiran.ezxhelper.ClassUtils;
import com.github.kyuubiran.ezxhelper.Log;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import deltazero.amarok.xposed.utils.XPref;

/**
 * LauncherApps is a separate system service (used by launchers, some Settings
 * screens and app stores). It does not go through
 * IPackageManager.getInstalledPackages, so the PackageManager hooks do not
 * cover it. With "hide self" enabled we filter Amarok out here as well.
 */
public class LauncherAppsHooks implements IHook {

    @Override
    public String getName() {
        return "LauncherApps";
    }

    @Override
    public void load() {
        Class<?> clazz = null;
        for (String name : new String[]{
                "com.android.server.pm.LauncherAppsService$LauncherAppsImpl",
                "com.android.server.pm.LauncherAppsService"}) {
            try {
                clazz = ClassUtils.loadClass(name, null);
                Log.ix("LauncherAppsHooks: found " + name, null);
                break;
            } catch (ClassNotFoundException ignored) {
            }
        }
        if (clazz == null) {
            Log.ex("LauncherAppsHooks: LauncherAppsService class not found", null);
            return;
        }

        try {
            XposedBridge.hookAllMethods(clazz, "getApplicationInfo", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!shouldFilter()) return;
                    Object result = param.getResult();
                    if (result instanceof ApplicationInfo
                            && XPref.isSelfPackage(((ApplicationInfo) result).packageName))
                        param.setResult(null);
                }
            });
            Log.ix("LauncherAppsHooks: getApplicationInfo hooked", null);
        } catch (Throwable t) {
            Log.ex("LauncherAppsHooks: failed to hook getApplicationInfo", t);
        }

        try {
            XposedBridge.hookAllMethods(clazz, "getActivityList", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!shouldFilter()) return;
                    Object result = param.getResult();
                    if (!(result instanceof List)) return;

                    List<Object> filtered = new ArrayList<>();
                    for (Object item : (List<?>) result) {
                        if (item instanceof LauncherActivityInfo) {
                            ApplicationInfo ai = ((LauncherActivityInfo) item).getApplicationInfo();
                            if (ai != null && XPref.isSelfPackage(ai.packageName))
                                continue;
                        }
                        filtered.add(item);
                    }
                    param.setResult(filtered);
                }
            });
            Log.ix("LauncherAppsHooks: getActivityList hooked", null);
        } catch (Throwable t) {
            Log.ex("LauncherAppsHooks: failed to hook getActivityList", t);
        }
    }

    private static boolean shouldFilter() {
        XPref.refreshCache();
        return XPref.isHideSelfActive() && !XPref.isCallerExempt();
    }
}
