package deltazero.amarok.ui;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.MenuProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import deltazero.amarok.AmarokActivity;
import deltazero.amarok.Hider;
import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.ui.settings.SettingsActivity;

/**
 * Main screen: a 4-column grid with the icons of every app that is set to be
 * hidden, plus a floating button:
 * <p>
 * - tap  : hide all apps in the list
 * - long : unhide all apps
 * <p>
 * - tap an icon      : unhide (if needed) and launch that app
 * - long-press icon  : per-app menu (hide now / unhide now)
 */
public class MainActivity extends AmarokActivity {

    private RecyclerView rvApps;
    private TextView tvEmpty;
    private AppGridAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);
        setupMenu();

        rvApps = findViewById(R.id.main_rv_apps);
        tvEmpty = findViewById(R.id.main_tv_empty);

        adapter = new AppGridAdapter(this::onAppClicked, this::onAppLongClicked);
        rvApps.setLayoutManager(new GridLayoutManager(this, 4));
        rvApps.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.main_fab);
        fab.setOnClickListener(v -> Hider.hide(this));
        fab.setOnLongClickListener(v -> {
            Hider.unhide(this);
            return true;
        });

        Hider.state.observe(this, state -> loadApps());

        // Make sure the app hider (Root mode) is available before the user tries to hide.
        PrefMgr.getAppHider(this).tryToActivate((appHiderClass, succeed, msg) -> {
            if (!succeed)
                Hider.showNoHiderDialog(this, msg);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
    }

    private void setupMenu() {
        addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_app_grid, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.action_select_apps) {
                    startActivity(new Intent(MainActivity.this, SetHideAppActivity.class));
                    return true;
                }
                if (menuItem.getItemId() == R.id.action_settings) {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Load the apps to show: everything in the hide list plus anything that is
     * currently hidden (so a per-app hidden app can always be found again).
     */
    private void loadApps() {
        executor.execute(() -> {
            final PackageManager pm = getPackageManager();
            final Set<String> hidden = Hider.getHiddenPkgs();
            final Set<String> pkgs = new HashSet<>(PrefMgr.getHideApps());
            pkgs.addAll(hidden);

            final List<AppGridAdapter.GridApp> list = new ArrayList<>();
            for (String pkg : pkgs) {
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg,
                            PackageManager.MATCH_UNINSTALLED_PACKAGES
                                    | PackageManager.MATCH_DISABLED_COMPONENTS);
                    Drawable icon = pm.getApplicationIcon(ai);
                    String label = pm.getApplicationLabel(ai).toString();
                    list.add(new AppGridAdapter.GridApp(pkg, label, icon, hidden.contains(pkg)));
                } catch (Exception ignored) {
                    // Package no longer installed
                }
            }
            list.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));

            runOnUiThread(() -> {
                adapter.submitList(list);
                tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void onAppClicked(AppGridAdapter.GridApp app) {
        if (app.hidden()) {
            Hider.unhideApp(this, app.packageName(), () -> {
                Hider.launchApp(this, app.packageName());
                loadApps();
            });
        } else {
            Hider.launchApp(this, app.packageName());
        }
    }

    private void onAppLongClicked(AppGridAdapter.GridApp app) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(app.label())
                .setItems(new CharSequence[]{"立即隐藏", "取消隐藏"}, (dialog, which) -> {
                    if (which == 0) {
                        Hider.hideApp(this, app.packageName(), this::loadApps);
                    } else {
                        Hider.unhideApp(this, app.packageName(), this::loadApps);
                    }
                })
                .show();
    }
}
