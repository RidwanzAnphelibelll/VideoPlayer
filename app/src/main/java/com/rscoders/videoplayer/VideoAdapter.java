package com.rscoders.videoplayer;

import android.content.Context;
import androidx.core.content.ContextCompat;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VH> {

    public interface Listener {
        void onItemClick(VideoItem item, int position);
        void onItemLongClick(VideoItem item, int position);
        void onSelectionChanged(int count);
    }

    private List<VideoItem> items = new ArrayList<>();
    private final Set<Integer> selectedPositions = new LinkedHashSet<>();
    private boolean multiSelectMode = false;
    private Listener listener;

    public void setListener(Listener l) {
        listener = l;
    }

    public void setItems(List<VideoItem> list) {
        items = new ArrayList<>(list);
        selectedPositions.clear();
        multiSelectMode = false;
        notifyDataSetChanged();
    }

    public void exitMultiSelect() {
        multiSelectMode = false;
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    public void selectAll() {
        selectedPositions.clear();
        for (int i = 0; i < items.size(); i++) selectedPositions.add(i);
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(selectedPositions.size());
    }

    public List<VideoItem> getSelectedItems() {
        List<VideoItem> result = new ArrayList<>();
        for (int pos : selectedPositions) result.add(items.get(pos));
        return result;
    }

    public boolean isMultiSelectMode() {
        return multiSelectMode;
    }

    public int getSelectedCount() {
        return selectedPositions.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        VideoItem item = items.get(pos);
        Context ctx = h.itemView.getContext();

        int radius = (int) (10 * ctx.getResources().getDisplayMetrics().density);

        Uri thumbUri = Uri.withAppendedPath(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(item.id));
        Glide.with(ctx)
            .load(thumbUri)
            .apply(RequestOptions.bitmapTransform(new RoundedCorners(radius)))
            .placeholder(R.drawable.bg_thumb_placeholder)
            .into(h.ivThumb);

        String displayTitle = item.title.isEmpty() ? extractName(item.path) : item.title;
        h.tvTitle.setText(displayTitle);

        String dateStr = new SimpleDateFormat("dd/MM/yy", Locale.getDefault())
            .format(new Date(item.dateAdded * 1000));
        h.tvDate.setText(dateStr);

        h.tvDuration.setText(item.getFormattedDuration());
        h.tvDuration.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));

        boolean selected = selectedPositions.contains(pos);
        h.cbSelect.setVisibility(multiSelectMode ? View.VISIBLE : View.GONE);
        h.cbSelect.setChecked(selected);
        h.itemView.setBackgroundResource(selected ? R.drawable.bg_item_selected : R.drawable.bg_item_normal);

        h.itemView.setOnClickListener(v -> {
            int p = h.getAdapterPosition();
            if (p == RecyclerView.NO_ID) return;
            if (multiSelectMode) {
                toggleSelect(p);
            } else {
                if (listener != null) listener.onItemClick(items.get(p), p);
            }
        });

        h.itemView.setOnLongClickListener(v -> {
            int p = h.getAdapterPosition();
            if (p == RecyclerView.NO_ID) return false;
            if (!multiSelectMode) {
                multiSelectMode = true;
                notifyDataSetChanged();
            }
            toggleSelect(p);
            if (listener != null) listener.onItemLongClick(items.get(p), p);
            return true;
        });
    }

    private void toggleSelect(int pos) {
        if (selectedPositions.contains(pos)) {
            selectedPositions.remove(pos);
        } else {
            selectedPositions.add(pos);
        }
        notifyItemChanged(pos);
        if (listener != null) listener.onSelectionChanged(selectedPositions.size());
    }

    private String extractName(String path) {
        if (path == null) return "";
        int i = path.lastIndexOf('/');
        String name = i >= 0 ? path.substring(i + 1) : path;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvTitle, tvDuration, tvDate;
        CheckBox cbSelect;

        VH(View v) {
            super(v);
            ivThumb = v.findViewById(R.id.ivThumb);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvDuration = v.findViewById(R.id.tvDuration);
            tvDate = v.findViewById(R.id.tvDate);
            cbSelect = v.findViewById(R.id.cbSelect);
        }
    }
}
