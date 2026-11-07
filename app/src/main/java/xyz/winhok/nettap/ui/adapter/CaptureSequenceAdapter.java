package xyz.winhok.nettap.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.CaptureRowSubtitleFormatter;
import xyz.winhok.nettap.ui.MethodIcon;
import xyz.winhok.nettap.ui.StatusColor;
import xyz.winhok.nettap.ui.TextHighlighter;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;

public final class CaptureSequenceAdapter extends RecyclerView.Adapter<CaptureSequenceAdapter.Holder> {
    private final ArrayList<CaptureUiEvent> events = new ArrayList<>();
    private final Callbacks callbacks;
    private String query = "";

    public CaptureSequenceAdapter(Callbacks callbacks) {
        this.callbacks = callbacks;
        setHasStableIds(true);
    }

    public void submit(List<CaptureUiEvent> next, String query) {
        String nextQuery = query == null ? "" : query;
        String previousQuery = this.query;
        ArrayList<CaptureUiEvent> nextEvents = new ArrayList<>();
        if (next != null) {
            nextEvents.addAll(next);
        }
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new CaptureSequenceDiffCallback(
                new ArrayList<>(events),
                nextEvents
        ));
        events.clear();
        events.addAll(nextEvents);
        this.query = nextQuery;
        diff.dispatchUpdatesTo(this);
        if (shouldRebindForQueryChange(previousQuery, nextQuery, events.size())) {
            notifyItemRangeChanged(0, events.size(), nextQuery);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_capture_row, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CaptureUiEvent event = events.get(position);
        holder.methodBadge.setImageResource(MethodIcon.forMethod(event.getMethod()));
        String title = event.getMethod() + " " + event.getDisplayStatus() + " " + event.getUrl();
        holder.title.setText(TextHighlighter.highlight(holder.title.getContext(), title, query));
        holder.subtitle.setText(CaptureRowSubtitleFormatter.format(position, event));
        holder.statusChip.setText(event.getDisplayStatus());
        holder.statusChip.setChipBackgroundColor(StatusColor.forCode(
                event.getResponseCode(),
                holder.itemView.getContext()
        ));
        holder.itemView.setAlpha(callbacks.isRead(event) ? 0.55f : 1f);
        holder.itemView.setOnClickListener(view -> callbacks.onOpen(event));
        holder.itemView.setOnLongClickListener(view -> {
            callbacks.onShowActions(event, view);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= events.size()) {
            return RecyclerView.NO_ID;
        }
        return events.get(position).getSessionId();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView methodBadge;
        final TextView title;
        final TextView subtitle;
        final Chip statusChip;

        Holder(@NonNull View itemView) {
            super(itemView);
            methodBadge = itemView.findViewById(R.id.method_badge);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            statusChip = itemView.findViewById(R.id.status_chip);
        }
    }

    static boolean shouldRebindForQueryChange(String previousQuery, String nextQuery, int itemCount) {
        String previous = previousQuery == null ? "" : previousQuery;
        String next = nextQuery == null ? "" : nextQuery;
        return itemCount > 0 && !previous.equals(next);
    }

    public interface Callbacks {
        boolean isRead(CaptureUiEvent event);

        void onOpen(CaptureUiEvent event);

        void onShowActions(CaptureUiEvent event, View anchor);
    }
}
