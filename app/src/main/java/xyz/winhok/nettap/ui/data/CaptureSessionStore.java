package xyz.winhok.nettap.ui.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public final class CaptureSessionStore {
    public enum State {
        RUNNING,
        FROZEN,
        CLEARED
    }

    private final int maxRecords;
    private final long maxMemoryBytes;
    private final ArrayList<CaptureUiEvent> events = new ArrayList<>();
    private final Set<String> readIds = new HashSet<>();
    private State state = State.RUNNING;
    private long approximateBytes;

    public CaptureSessionStore(int maxRecords, long maxMemoryBytes) {
        this.maxRecords = Math.max(1, maxRecords);
        this.maxMemoryBytes = Math.max(1024L, maxMemoryBytes);
    }

    public synchronized boolean addFromRealtime(CaptureUiEvent event) {
        if (event == null || state == State.FROZEN) {
            return false;
        }
        events.add(event);
        approximateBytes += event.getApproximateBytes();
        state = State.RUNNING;
        evictOverflow();
        return true;
    }

    public synchronized void freeze() {
        state = State.FROZEN;
    }

    public synchronized void resume() {
        state = State.RUNNING;
    }

    public synchronized void clear() {
        events.clear();
        readIds.clear();
        approximateBytes = 0L;
        state = State.CLEARED;
    }

    public synchronized void markRead(String eventId) {
        if (eventId != null) {
            readIds.add(eventId);
        }
    }

    public synchronized boolean isRead(String eventId) {
        return eventId != null && readIds.contains(eventId);
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized int totalCount() {
        return events.size();
    }

    public synchronized long approximateBytes() {
        return approximateBytes;
    }

    public synchronized List<CaptureUiEvent> sequenceOldestFirst() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public synchronized List<CaptureUiEvent> sequenceNewestFirst() {
        ArrayList<CaptureUiEvent> result = new ArrayList<>(events);
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    public synchronized List<DomainBucket> domainBuckets() {
        return domainBucketsFor(events);
    }

    public static List<DomainBucket> domainBucketsFor(List<CaptureUiEvent> events) {
        LinkedHashMap<String, MutableBucket> buckets = new LinkedHashMap<>();
        for (CaptureUiEvent event : events) {
            MutableBucket bucket = buckets.get(event.getHost());
            if (bucket == null) {
                bucket = new MutableBucket(event.getHost(), event.getHostLabel());
                buckets.put(event.getHost(), bucket);
            }
            bucket.add(event);
        }
        ArrayList<DomainBucket> result = new ArrayList<>();
        for (MutableBucket bucket : buckets.values()) {
            result.add(bucket.toImmutable());
        }
        result.sort((left, right) -> right.getLatestTimestamp().compareTo(left.getLatestTimestamp()));
        return Collections.unmodifiableList(result);
    }

    public synchronized List<CaptureUiEvent> eventsForHost(String host) {
        ArrayList<CaptureUiEvent> result = new ArrayList<>();
        for (CaptureUiEvent event : events) {
            if (event.getHost().equals(host)) {
                result.add(event);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized int clearHostBucket(String host) {
        if (host == null || host.isEmpty()) {
            return 0;
        }
        int removedCount = 0;
        for (int index = events.size() - 1; index >= 0; index--) {
            CaptureUiEvent event = events.get(index);
            if (host.equals(event.getHost())) {
                events.remove(index);
                readIds.remove(event.getId());
                approximateBytes = Math.max(0L, approximateBytes - event.getApproximateBytes());
                removedCount++;
            }
        }
        return removedCount;
    }

    private void evictOverflow() {
        while (events.size() > maxRecords || approximateBytes > maxMemoryBytes) {
            if (events.isEmpty()) {
                approximateBytes = 0L;
                return;
            }
            CaptureUiEvent removed = events.remove(0);
            readIds.remove(removed.getId());
            approximateBytes = Math.max(0L, approximateBytes - removed.getApproximateBytes());
        }
    }

    private static final class MutableBucket {
        private final String host;
        private final String label;
        private int count;
        private String latestTimestamp = "";
        private int status2xxCount;
        private int status3xxCount;
        private int status4xxCount;
        private int status5xxCount;
        private int errorCount;

        MutableBucket(String host, String label) {
            this.host = host;
            this.label = label;
        }

        void add(CaptureUiEvent event) {
            count++;
            if (event.getTimestamp().compareTo(latestTimestamp) > 0) {
                latestTimestamp = event.getTimestamp();
            }
            if (event.getError() != null && !event.getError().isEmpty()) {
                errorCount++;
                return;
            }
            int code = event.getResponseCode();
            if (code >= 200 && code < 300) {
                status2xxCount++;
            } else if (code >= 300 && code < 400) {
                status3xxCount++;
            } else if (code >= 400 && code < 500) {
                status4xxCount++;
            } else if (code >= 500 && code < 600) {
                status5xxCount++;
            } else {
                errorCount++;
            }
        }

        DomainBucket toImmutable() {
            return new DomainBucket(
                    host,
                    label,
                    count,
                    latestTimestamp,
                    status2xxCount,
                    status3xxCount,
                    status4xxCount,
                    status5xxCount,
                    errorCount
            );
        }
    }
}
