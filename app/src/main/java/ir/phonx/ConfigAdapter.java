package ir.phonx;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class ConfigAdapter extends RecyclerView.Adapter<ConfigAdapter.ViewHolder> {

    public interface Listener {
        void onConfigSelected(ConfigEntry entry);
        void onConfigRemoved(ConfigEntry entry);
    }

    private List<ConfigEntry> configs = new ArrayList<>();
    private String activeConfigId;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setConfigs(List<ConfigEntry> configs, String activeConfigId) {
        this.configs = new ArrayList<>(configs);
        this.activeConfigId = activeConfigId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_config, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConfigEntry entry = configs.get(position);
        boolean isActive = entry.id.equals(activeConfigId);

        holder.tvConfigName.setText(entry.displayName);
        holder.tvConfigDetail.setText(entry.protocol + " | " + entry.host + ":" + entry.port);
        holder.rbActive.setChecked(isActive);

        // Highlight active card
        MaterialCardView card = (MaterialCardView) holder.itemView;
        if (isActive) {
            card.setStrokeColor(card.getContext().getResources().getColor(R.color.md_primary, null));
            card.setStrokeWidth(dpToPx(card, 1));
        } else {
            card.setStrokeWidth(0);
        }

        holder.rbActive.setOnClickListener(v -> {
            if (listener != null) listener.onConfigSelected(entry);
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onConfigSelected(entry);
        });
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onConfigRemoved(entry);
        });
    }

    @Override
    public int getItemCount() {
        return configs.size();
    }

    private int dpToPx(View view, int dp) {
        return (int) (dp * view.getContext().getResources().getDisplayMetrics().density);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final RadioButton rbActive;
        final TextView tvConfigName;
        final TextView tvConfigDetail;
        final View btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            rbActive = itemView.findViewById(R.id.rbActive);
            tvConfigName = itemView.findViewById(R.id.tvConfigName);
            tvConfigDetail = itemView.findViewById(R.id.tvConfigDetail);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
