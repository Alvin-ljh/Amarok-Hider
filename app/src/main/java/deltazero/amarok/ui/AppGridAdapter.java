package deltazero.amarok.ui;

import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import deltazero.amarok.R;

/**
 * Adapter for the 4-column icon grid on the main screen.
 */
public class AppGridAdapter extends ListAdapter<AppGridAdapter.GridApp, AppGridAdapter.Holder> {

    public interface OnAppClickListener {
        void onAppClicked(GridApp app);
    }

    public interface OnAppLongClickListener {
        void onAppLongClicked(GridApp app);
    }

    public record GridApp(String packageName, String label, Drawable icon, boolean hidden,
                          float labelSize) {
    }

    private final OnAppClickListener clickListener;
    private final OnAppLongClickListener longClickListener;

    public AppGridAdapter(OnAppClickListener clickListener, OnAppLongClickListener longClickListener) {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull GridApp oldItem, @NonNull GridApp newItem) {
                return oldItem.packageName().equals(newItem.packageName());
            }

            @Override
            public boolean areContentsTheSame(@NonNull GridApp oldItem, @NonNull GridApp newItem) {
                return oldItem.packageName().equals(newItem.packageName())
                        && oldItem.hidden() == newItem.hidden()
                        && oldItem.label().equals(newItem.label())
                        && oldItem.labelSize() == newItem.labelSize();
            }
        });
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_grid, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        GridApp app = getCurrentList().get(position);
        holder.icon.setImageDrawable(app.icon());
        holder.icon.setAlpha(app.hidden() ? 0.35f : 1f);
        holder.label.setText(app.label());
        holder.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, app.labelSize());
        holder.label.setAlpha(app.hidden() ? 0.5f : 1f);
        holder.itemView.setOnClickListener(v -> clickListener.onAppClicked(app));
        holder.itemView.setOnLongClickListener(v -> {
            longClickListener.onAppLongClicked(app);
            return true;
        });
    }

    public static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.grid_item_icon);
            label = itemView.findViewById(R.id.grid_item_label);
        }
    }
}
