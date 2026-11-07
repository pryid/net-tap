package xyz.winhok.nettap.ui.adapter;

import androidx.recyclerview.widget.DiffUtil;

import java.util.Collections;
import java.util.List;

import xyz.winhok.nettap.ui.data.CaptureUiEvent;

final class CaptureSequenceDiffCallback extends DiffUtil.Callback {
    private final List<CaptureUiEvent> oldEvents;
    private final List<CaptureUiEvent> newEvents;

    CaptureSequenceDiffCallback(List<CaptureUiEvent> oldEvents, List<CaptureUiEvent> newEvents) {
        this.oldEvents = oldEvents == null ? Collections.emptyList() : oldEvents;
        this.newEvents = newEvents == null ? Collections.emptyList() : newEvents;
    }

    @Override
    public int getOldListSize() {
        return oldEvents.size();
    }

    @Override
    public int getNewListSize() {
        return newEvents.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return oldEvents.get(oldItemPosition).getSessionId()
                == newEvents.get(newItemPosition).getSessionId();
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        CaptureUiEvent oldEvent = oldEvents.get(oldItemPosition);
        CaptureUiEvent newEvent = newEvents.get(newItemPosition);
        return oldEvent.getTimestamp().equals(newEvent.getTimestamp())
                && oldEvent.getMethod().equals(newEvent.getMethod())
                && oldEvent.getUrl().equals(newEvent.getUrl())
                && oldEvent.getDisplayStatus().equals(newEvent.getDisplayStatus())
                && oldEvent.getHook().equals(newEvent.getHook())
                && oldEvent.getPackageName().equals(newEvent.getPackageName())
                && oldEvent.getDurationMs() == newEvent.getDurationMs()
                && equals(oldEvent.getError(), newEvent.getError());
    }

    private static boolean equals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
