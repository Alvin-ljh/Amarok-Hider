package deltazero.amarok.ui.settings;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.utils.XHidePrefBridge;
import rikka.material.preference.MaterialSwitchPreference;

public class XHideCategory extends BaseCategory {
    public XHideCategory(@NonNull FragmentActivity activity, @NonNull PreferenceScreen screen) {
        super(activity, screen);
        setTitle(R.string.x_hide);

        var xHideStatus = new Preference(activity);
        // xHideStatus.setTitle(R.string.x_hide);
        xHideStatus.setSummary(R.string.x_hide_description);
        xHideStatus.setEnabled(XHidePrefBridge.isAvailable);
        addPreference(xHideStatus);

        var enableXHide = new MaterialSwitchPreference(activity);
        enableXHide.setKey(PrefMgr.ENABLE_X_HIDE);
        enableXHide.setTitle(R.string.enable_x_hide);
        enableXHide.setIcon(R.drawable.domino_mask_fill0_wght400_grad0_opsz24);
        enableXHide.setSummary(XHidePrefBridge.isAvailable ?
                activity.getString(R.string.xposed_active, XHidePrefBridge.xposedVersion)
                : activity.getString(R.string.xposed_inactive));
        enableXHide.setEnabled(XHidePrefBridge.isAvailable);
        addPreference(enableXHide);

        var hideSelf = new MaterialSwitchPreference(activity);
        hideSelf.setKey(XHidePrefBridge.HIDE_SELF_PREF);
        hideSelf.setTitle("隐藏 Amarok 自身");
        hideSelf.setSummary("从系统设置和其他应用的已安装列表里移除 Amarok（不影响启动）。");
        hideSelf.setChecked(PrefMgr.getPrefs().getBoolean(XHidePrefBridge.HIDE_SELF_PREF, true));
        hideSelf.setEnabled(XHidePrefBridge.isAvailable);
        hideSelf.setOnPreferenceChangeListener((preference, newValue) -> {
            PrefMgr.getPrefs().edit()
                    .putBoolean(XHidePrefBridge.HIDE_SELF_PREF, (Boolean) newValue)
                    .apply();
            new Handler(Looper.getMainLooper()).post(XHidePrefBridge::commitNewValues);
            return true;
        });
        addPreference(hideSelf);

        var disableOnlyWithXHide = new MaterialSwitchPreference(activity);
        disableOnlyWithXHide.setKey(PrefMgr.DISABLE_ONLY_WITH_XHIDE);
        disableOnlyWithXHide.setTitle(R.string.disable_only_with_xhide);
        disableOnlyWithXHide.setIcon(R.drawable.visibility_off_24dp);
        disableOnlyWithXHide.setSummary(R.string.disable_only_with_xhide_description);
        disableOnlyWithXHide.setEnabled(XHidePrefBridge.isAvailable && PrefMgr.isXHideEnabled());
        disableOnlyWithXHide.setChecked(PrefMgr.getDisableOnlyWithXHide());
        addPreference(disableOnlyWithXHide);

        // Update the skip hide step preference when XHide is toggled
        enableXHide.setOnPreferenceChangeListener((preference, newValue) -> {
            disableOnlyWithXHide.setEnabled(XHidePrefBridge.isAvailable && (boolean) newValue);
            new Handler(Looper.getMainLooper()).post(XHidePrefBridge::commitNewValues);
            return true;
        });
    }
}
