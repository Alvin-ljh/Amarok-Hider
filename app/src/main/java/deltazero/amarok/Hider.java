package deltazero.amarok;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.MutableLiveData;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.topjohnwu.superuser.Shell;

import java.util.HashSet;
import java.util.Set;

import deltazero.amarok.ui.settings.SwitchAppHiderActivity;
import deltazero.amarok.utils.SecurityUtil;
import deltazero.amarok.utils.XHidePrefBridge;


public final class Hider {

    private static final String TAG = "Hider";
    private static final String PREF_HIDDEN_PKGS = "perAppHiddenPkgs";
    private static final HandlerThread hiderThread = new HandlerThread("HIDER_THREAD");
    private static final Handler threadHandler;

    public static boolean initialized = false;
    public static MutableLiveData<State> state;

    public enum State {
        HIDDEN,
        VISIBLE,
        PROCESSING
    }

    static {
        hiderThread.start();
        threadHandler = new Handler(hiderThread.getLooper());
    }

    /**
     * This method should be invoked in {@link AmarokApplication#onCreate()}, after {@link PrefMgr#init(Context)}.
     * Do not invoke this method in static part or before {@link PrefMgr#init()}.
     */
    public static void init() {
        assert PrefMgr.initialized;
        state = new MutableLiveData<>(PrefMgr.getIsHidden() ? State.HIDDEN : State.VISIBLE);
        state.observeForever(state -> {
            if (state != State.PROCESSING)
                PrefMgr.setIsHidden(state == State.HIDDEN);
        });
        initialized = true;
    }

    /**
     * NOTE: Calling this method on a background thread
     * does not guarantee that the latest value set will be received.
     */
    public static State getState() {
        return state.getValue();
    }

    // ------------------------------------------------------------------
    // Per-app hiding (root only)
    // ------------------------------------------------------------------

    /**
     * Package names that are currently hidden (either by the global hide
     * action or by a per-app hide action). This is what XHide filters out
     * of PackageManager results, so partial hiding stays "invisible".
     */
    public static Set<String> getHiddenPkgs() {
        return new HashSet<>(PrefMgr.getPrefs().getStringSet(PREF_HIDDEN_PKGS, new HashSet<>()));
    }

    public static void setHiddenPkgs(Set<String> pkgs) {
        PrefMgr.getPrefs().edit().putStringSet(PREF_HIDDEN_PKGS, new HashSet<>(pkgs)).apply();
    }

    private static void addHiddenPkg(String pkg) {
        Set<String> pkgs = getHiddenPkgs();
        pkgs.add(pkg);
        setHiddenPkgs(pkgs);
    }

    private static void removeHiddenPkg(String pkg) {
        Set<String> pkgs = getHiddenPkgs();
        pkgs.remove(pkg);
        setHiddenPkgs(pkgs);
    }

    /**
     * Hide a single app. Uses root directly (pm disable + pm hide) and blocks
     * until the shell command finished, so that the caller can immediately
     * launch the app afterwards.
     */
    public static void hideApp(Context context, String pkg, Runnable onDone) {
        threadHandler.post(() -> {
            Log.i(TAG, "Per-app hide: " + pkg);
            Shell.Result result = Shell.cmd("pm disable " + pkg + "; pm hide " + pkg).exec();
            Log.i(TAG, "Per-app hide result: " + result.getCode() + " " + result.getOut());
            addHiddenPkg(pkg);
            XHidePrefBridge.commitNewValues();
            postToMain(onDone);
        });
    }

    /**
     * Unhide a single app (pm enable + pm unhide), blocking until done.
     */
    public static void unhideApp(Context context, String pkg, Runnable onDone) {
        threadHandler.post(() -> {
            Log.i(TAG, "Per-app unhide: " + pkg);
            Shell.Result result = Shell.cmd("pm enable " + pkg + "; pm unhide " + pkg).exec();
            Log.i(TAG, "Per-app unhide result: " + result.getCode() + " " + result.getOut());
            removeHiddenPkg(pkg);
            XHidePrefBridge.commitNewValues();
            postToMain(onDone);
        });
    }

    /**
     * Launch an app from within Amarok. Must be called on the main thread.
     */
    public static void launchApp(Context context, String pkg) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) {
            Toast.makeText(context, "无法启动该应用（可能仍处于隐藏状态）", Toast.LENGTH_SHORT).show();
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static void postToMain(Runnable runnable) {
        if (runnable != null)
            new Handler(Looper.getMainLooper()).post(runnable);
    }

    // ------------------------------------------------------------------
    // Global hiding
    // ------------------------------------------------------------------

    public static void hide(Context context) {
        PrefMgr.getAppHider(context).tryToActivate((appHiderClass, succeed, msg) -> {
            if (succeed) {
                processHide(context);
                return;
            }
            if (context instanceof Activity)
                showNoHiderDialog(context, msg);
            else
                showNoHiderToast(context, msg);
        });

        // Avoid password or disguise right after hide
        if (PrefMgr.getDisableSecurityWhenUnhidden()) {
            SecurityUtil.unlock();
            SecurityUtil.dismissDisguise();
        }
    }

    private static void processHide(Context context) {

        threadHandler.post(() -> {

            Log.i(TAG, "Process 'hide' start.");
            state.postValue(State.PROCESSING);

            try {
                // Determine if we should only disable apps (skip hide step) when XHide is enabled
                boolean disableOnly = PrefMgr.isXHideEnabled() && PrefMgr.getDisableOnlyWithXHide();

                PrefMgr.getAppHider(context).hide(PrefMgr.getHideApps(), disableOnly);
                setHiddenPkgs(PrefMgr.getHideApps());
                XHidePrefBridge.commitNewValues();
                PrefMgr.getFileHider(context).hide(PrefMgr.getHideFilePath());
            } catch (InterruptedException e) {
                Log.w(TAG, "Process 'hide' interrupted.");
                return;
            }

            Log.i(TAG, "Process 'hide' finish.");
            state.postValue(State.HIDDEN);

            if (!PrefMgr.getDisableToasts())
                Toast.makeText(context, R.string.hidden_toast, Toast.LENGTH_SHORT).show();

            QuickHideService.stopService(context);

        });
    }

    public static void unhide(Context context) {
        PrefMgr.getAppHider(context).tryToActivate((appHiderClass, succeed, msg) -> {
            if (succeed) {
                processUnhide(context);
                return;
            }
            if (context instanceof Activity)
                showNoHiderDialog(context, msg);
            else
                showNoHiderToast(context, msg);
        });
    }

    private static void processUnhide(Context context) {

        threadHandler.post(() -> {

            Log.i(TAG, "Process 'unhide' start.");
            state.postValue(State.PROCESSING);

            try {
                PrefMgr.getAppHider(context).unhide(PrefMgr.getHideApps());
                setHiddenPkgs(new HashSet<>());
                XHidePrefBridge.commitNewValues();
                PrefMgr.getFileHider(context).unhide(PrefMgr.getHideFilePath());
            } catch (InterruptedException e) {
                Log.w(TAG, "Process 'unhide' interrupted.");
                return;
            }

            Log.i(TAG, "Process 'unhide' finish.");
            state.postValue(State.VISIBLE);

            if (!PrefMgr.getDisableToasts())
                Toast.makeText(context, R.string.unhidden_toast, Toast.LENGTH_SHORT).show();

            // Important: The startService() method of QuickHideService must be invoked on the main thread.
            // If it's called from a background thread, the service might not get the most recent value from Hider.getState() in time.
            // As a result, if the state changes into VISIBLE from HIDDEN just before, the service won't start.
            new Handler(Looper.getMainLooper()).post(
                    () -> QuickHideService.startService(context));
        });
    }

    public static void forceUnhide(Context context) {
        if (state.getValue() == State.PROCESSING)
            hiderThread.interrupt();
        PrefMgr.setIsHidden(true);
        unhide(context);
    }

    public static void showNoHiderDialog(Context context, int message) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.apphider_not_ava_title)
                .setMessage(message)
                .setPositiveButton(R.string.switch_app_hider, (dialog, which)
                        -> context.startActivity(new Intent(context, SwitchAppHiderActivity.class)))
                .setNegativeButton(context.getString(R.string.ok), null)
                .show();
    }

    private static void showNoHiderToast(Context context, int message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

}
