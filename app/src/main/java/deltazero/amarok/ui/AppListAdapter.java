package deltazero.amarok.ui;

import static deltazero.amarok.utils.AppInfoUtil.AppInfo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;

public class AppListAdapter extends ListAdapter<AppInfo, AppListAdapter.AppListHolder> {

    public interface OnAppToggleListener {
        void onAppToggled(AppInfo app);
    }

    public interface OnAppClickListener {
        void onAppClicked(AppInfo app);
    }

    private final OnAppToggleListener toggleListener;
    private final OnAppClickListener clickListener;

    public AppListAdapter(OnAppToggleListener toggleListener, OnAppClickListener clickListener) {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull AppInfo oldItem, @NonNull AppInfo newItem) {
                return oldItem.packageName().equals(newItem.packageName());
            }

            @Override
            public boolean areContentsTheSame(@NonNull AppInfo oldItem, @NonNull AppInfo newItem) {
                return oldItem.packageName().equals(newItem.packageName()) && oldItem.label().equals(newItem.label());
            }
        });
        this.toggleListener = toggleListener;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public AppListHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hideapps, parent, false);
        return new AppListHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppListHolder holder, int position) {
        AppInfo app = getCurrentList().get(position);
        holder.bind(app, toggleListener, clickListener);
    }

    public static class AppListHolder extends RecyclerView.ViewHolder {
        private final TextView tvAppName;
        private final TextView tvPkgName;
        private final MaterialCheckBox cbIsHidden;
        private final ImageView ivAppIcon;
        private AppInfo currentApp;

        AppListHolder(View view) {
            super(view);
            tvAppName = view.findViewById(R.id.hideapp_tv_appname);
            tvPkgName = view.findViewById(R.id.hideapp_tv_pkgname);
            cbIsHidden = view.findViewById(R.id.hideapp_cb_ishidden);
            ivAppIcon = view.findViewById(R.id.hideapp_iv_appicon);
        }

        void bind(AppInfo app, OnAppToggleListener toggleListener, OnAppClickListener clickListener) {
            currentApp = app;
            tvAppName.setText(app.label());
            tvPkgName.setText(app.packageName());
            ivAppIcon.setImageDrawable(app.icon());
            cbIsHidden.setChecked(PrefMgr.getHideApps().contains(app.packageName()));

            cbIsHidden.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (currentApp != null && buttonView.isPressed()) {
                    toggleListener.onAppToggled(currentApp);
                }
            });

            // Single tap on the row opens the per-app action dialog
            // (launch / hide now / unhide now).
            itemView.setOnClickListener(v -> {
                if (currentApp != null && clickListener != null)
                    clickListener.onAppClicked(currentApp);
            });
        }
    }
}
