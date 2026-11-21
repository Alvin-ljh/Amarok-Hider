package deltazero.amarok.xposed.utils;

import android.annotation.SuppressLint;

import com.github.kyuubiran.ezxhelper.Log;
import com.github.kyuubiran.ezxhelper.ObjectUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FilterUtils {

    // NOTE: This method will not refresh XPref cache. Call refreshCache() before calling this method
    @SuppressLint("DefaultLocale")
    public static List<Object> filterAppsOrPkgs(List<Object> appsOrPkgs, Method m) {
        final boolean hideHiddenApps = XPref.isXHideActive();
        final boolean hideSelf = XPref.isHideSelfActive() && !XPref.isCallerExempt();

        if (!hideHiddenApps && !hideSelf) return appsOrPkgs;

        AtomicInteger filteredCount = new AtomicInteger();
        var filteredPackages = appsOrPkgs.stream()
                .filter(appOrPkg -> {
                    boolean shouldHide;
                    try {
                        String pkgName = ObjectUtils.getObjectOrNullUntilSuperclassAs(
                                appOrPkg, "packageName", null);
                        shouldHide = (hideHiddenApps && XPref.shouldHide(pkgName))
                                || (hideSelf && XPref.isSelfPackage(pkgName));
                    } catch (NoSuchFieldException e) {
                        throw new RuntimeException(e);
                    }
                    if (shouldHide) filteredCount.getAndIncrement();
                    return !shouldHide;
                })
                .collect(java.util.stream.Collectors.toList());

        Log.d(String.format("%s$%s(%s): Filtered %d packages",
                m.getDeclaringClass().getSimpleName(), m.getName(), Arrays.toString(m.getParameterTypes()),
                filteredCount.get()), null);
        return filteredPackages;
    }

    // NOTE: This method will not refresh XPref cache. Call refreshCache() before calling this method
    public static Object filterAppsOrPkgsInSlices(Object /* ParceledListSlice<*> */ appsOrPkgs, Method m) {
        List<Object> slice = ParceledListSliceUtil.sliceToList(appsOrPkgs);
        return ParceledListSliceUtil.listToSlice(filterAppsOrPkgs(slice, m));
    }
}
