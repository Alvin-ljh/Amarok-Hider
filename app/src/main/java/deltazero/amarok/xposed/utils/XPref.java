package deltazero.amarok.xposed.utils;

import android.os.Binder;

import com.github.kyuubiran.ezxhelper.Log;

import java.util.Collections;
import java.util.Set;

import de.robv.android.xposed.XSharedPreferences;
import deltazero.amarok.BuildConfig;

public class XPref {
    private static XSharedPreferences xPref;

    public static final String XPREF_PATH = "deltazero.amarok.xposed.prefs";
    public static final String HIDE_PKG_NAMES = "hidePkgNames";
    public static final String IS_ACTIVE = "isActive";
    /** Whether Amarok itself should be filtered out of package lists. */
    public static final String HIDE_SELF = "hideSelf";
    /** UID of the Amarok process (written by the app, used to exempt its own queries). */
    public static final String SELF_UID = "selfUid";

    private static boolean isActiveCache = false;
    private static boolean hideSelfCache = false;
    private static int selfUidCache = -1;
    private static Set<String> hidePkgNamesCache = Collections.emptySet();

    public static void init() {
        Log.d("Initializing XPref...", null);
        xPref = new XSharedPreferences(BuildConfig.APPLICATION_ID, XPREF_PATH);
        Log.d("xPref path: " + xPref.getFile().getAbsolutePath(), null);

        if (xPref.getFile().canRead()) {
            xPref.reload();
            Log.d("xPref content: " + xPref.getAll(), null);
        } else {
            Log.wx("No XPref found. Launch Amarok once to activate XHide.", null);
        }

        Log.ix("XPref initialized.", null);
    }

    public static void refreshCache() {
        xPref.reload();
        if (!xPref.getFile().canRead()) return;
        isActiveCache = xPref.getBoolean(IS_ACTIVE, false);
        hideSelfCache = xPref.getBoolean(HIDE_SELF, false);
        selfUidCache = xPref.getInt(SELF_UID, -1);
        hidePkgNamesCache = xPref.getStringSet(HIDE_PKG_NAMES, Collections.emptySet());
    }

    public static boolean isXHideActive() {
        return isActiveCache;
    }

    public static boolean shouldHide(String pkgName) {
        return hidePkgNamesCache.contains(pkgName);
    }

    /**
     * Hide Amarok itself from package lists (Settings, other apps), even when no
     * app is hidden right now.
     */
    public static boolean isHideSelfActive() {
        return hideSelfCache;
    }

    public static boolean isSelfPackage(String pkgName) {
        return BuildConfig.APPLICATION_ID.equals(pkgName);
    }

    /**
     * Calls coming from Amarok itself, the system, adb shell or root must not be
     * filtered, otherwise the app (and tools managing it) would break.
     */
    public static boolean isCallerExempt() {
        int uid = Binder.getCallingUid();
        return uid == selfUidCache || uid == 0 || uid == 1000 || uid == 2000;
    }

    public static String getXPrefDir() {
        // This function can be called without initializing XPref
        return new XSharedPreferences(BuildConfig.APPLICATION_ID, XPREF_PATH)
                .getFile().getParentFile().getAbsolutePath();
    }
}
