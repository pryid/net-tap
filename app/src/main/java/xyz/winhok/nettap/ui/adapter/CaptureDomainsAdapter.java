package xyz.winhok.nettap.ui.adapter;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import xyz.winhok.nettap.ui.Dimens;
import xyz.winhok.nettap.ui.data.DomainBucket;

public final class CaptureDomainsAdapter extends RecyclerView.Adapter<CaptureDomainsAdapter.Holder> {
    private final ArrayList<DomainBucket> buckets = new ArrayList<>();
    private final Callbacks callbacks;

    public CaptureDomainsAdapter(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void submit(List<DomainBucket> next) {
        ArrayList<DomainBucket> nextBuckets = new ArrayList<>();
        if (next != null) {
            nextBuckets.addAll(next);
        }
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new CaptureDomainsDiffCallback(
                new ArrayList<>(buckets),
                nextBuckets
        ));
        buckets.clear();
        buckets.addAll(nextBuckets);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView view = new TextView(parent.getContext());
        Dimens.setPaddingDp(view, 12, 12);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DomainBucket bucket = buckets.get(position);
        holder.text.setText(format(bucket));
        holder.text.setOnClickListener(view -> callbacks.onOpen(bucket));
        holder.text.setOnLongClickListener(view -> {
            callbacks.onShowActions(bucket, view);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return buckets.size();
    }

    static String format(DomainBucket bucket) {
        StringBuilder text = new StringBuilder();
        text.append(bucket.getLabel()).append("  ").append(bucket.getCount());
        StringBuilder counts = new StringBuilder();
        appendCount(counts, "2xx", bucket.getStatus2xxCount());
        appendCount(counts, "3xx", bucket.getStatus3xxCount());
        appendCount(counts, "4xx", bucket.getStatus4xxCount());
        appendCount(counts, "5xx", bucket.getStatus5xxCount());
        appendCount(counts, "err", bucket.getErrorCount());
        if (counts.length() > 0) {
            text.append('\n').append(counts);
        }
        if (bucket.getLatestTimestamp() != null && !bucket.getLatestTimestamp().isEmpty()) {
            text.append('\n').append(bucket.getLatestTimestamp());
        }
        return text.toString();
    }

    private static void appendCount(StringBuilder text, String label, int count) {
        if (count <= 0) {
            return;
        }
        if (text.length() > 0) {
            text.append("  ");
        }
        text.append(label).append(" x").append(count);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView text;

        Holder(@NonNull TextView itemView) {
            super(itemView);
            text = itemView;
        }
    }

    public interface Callbacks {
        void onOpen(DomainBucket bucket);

        void onShowActions(DomainBucket bucket, View anchor);
    }
}
