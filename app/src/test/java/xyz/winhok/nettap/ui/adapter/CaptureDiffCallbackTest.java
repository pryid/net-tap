package xyz.winhok.nettap.ui.adapter;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import xyz.winhok.nettap.ui.data.CaptureSessionStore;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;
import xyz.winhok.nettap.ui.data.DomainBucket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CaptureDiffCallbackTest {
    @Test
    public void sequenceDiffDistinguishesRepeatedProducerIds() {
        CaptureUiEvent oldEvent = event("same", 200);
        CaptureUiEvent updatedEvent = event("same", 201);
        CaptureUiEvent newEvent = event("other", 200);

        CaptureSequenceDiffCallback diff = new CaptureSequenceDiffCallback(
                Collections.singletonList(oldEvent),
                Arrays.asList(updatedEvent, newEvent)
        );

        assertEquals(1, diff.getOldListSize());
        assertEquals(2, diff.getNewListSize());
        assertFalse(diff.areItemsTheSame(0, 0));
        assertFalse(diff.areContentsTheSame(0, 0));
        CaptureSequenceDiffCallback unchanged = new CaptureSequenceDiffCallback(
                Collections.singletonList(oldEvent), Collections.singletonList(oldEvent));
        assertTrue(unchanged.areItemsTheSame(0, 0));
    }

    @Test
    public void domainDiffUsesHostForIdentityAndCountsForUpdates() {
        DomainBucket oldBucket = bucketWithCount(1);
        DomainBucket updatedBucket = bucketWithCount(2);

        CaptureDomainsDiffCallback diff = new CaptureDomainsDiffCallback(
                Collections.singletonList(oldBucket),
                Collections.singletonList(updatedBucket)
        );

        assertTrue(diff.areItemsTheSame(0, 0));
        assertFalse(diff.areContentsTheSame(0, 0));
    }

    @Test
    public void sequenceAdapterRebindsRowsWhenOnlyQueryChanges() {
        assertTrue(CaptureSequenceAdapter.shouldRebindForQueryChange("", "same", 1));
        assertFalse(CaptureSequenceAdapter.shouldRebindForQueryChange("same", "same", 1));
        assertFalse(CaptureSequenceAdapter.shouldRebindForQueryChange("", "same", 0));
    }

    @Test
    public void sessionIdsAreDistinctForIdenticalProducerIds() {
        CaptureUiEvent first = event("same", 200);
        CaptureUiEvent second = event("same", 200);
        assertFalse(first.getSessionId() == second.getSessionId());
        assertFalse(first.getSessionId() == androidx.recyclerview.widget.RecyclerView.NO_ID);
    }

    @Test
    public void domainsAdapterDoesNotUseCollisionProneStableIds() {
        CaptureDomainsAdapter adapter = new CaptureDomainsAdapter(null);
        assertFalse(adapter.hasStableIds());
    }

    @Test
    public void domainAdapterFormatsOnlyNonZeroStatusBucketsAndTimestamp() {
        CaptureSessionStore store = new CaptureSessionStore(20, 1024L * 1024L);
        store.addFromRealtime(event("ok", 200));
        store.addFromRealtime(event("missing", 404));
        DomainBucket bucket = store.domainBuckets().get(0);

        String text = CaptureDomainsAdapter.format(bucket);

        assertTrue(text.contains("api.example.com  2"));
        assertTrue(text.contains("2xx x1"));
        assertTrue(text.contains("4xx x1"));
        assertTrue(text.contains("2026-05-14T00:00:00.000Z"));
        assertFalse(text.contains("3xx x0"));
        assertFalse(text.contains("5xx x0"));
        assertFalse(text.contains("err x0"));
    }

    private static CaptureUiEvent event(String id, int responseCode) {
        return CaptureUiEvent.fromJson("{"
                + "\"schemaVersion\":2,"
                + "\"id\":\"" + id + "\","
                + "\"timestamp\":\"2026-05-14T00:00:00.000Z\","
                + "\"packageName\":\"com.example\","
                + "\"hook\":\"okhttp\","
                + "\"method\":\"GET\","
                + "\"url\":\"https://api.example.com/" + id + "\","
                + "\"requestHeaders\":{},"
                + "\"requestBody\":{},"
                + "\"responseCode\":" + responseCode + ","
                + "\"responseMessage\":\"OK\","
                + "\"responseHeaders\":{},"
                + "\"responseBody\":{},"
                + "\"durationMs\":1,"
                + "\"error\":null"
                + "}");
    }

    private static DomainBucket bucketWithCount(int count) {
        CaptureSessionStore store = new CaptureSessionStore(20, 1024L * 1024L);
        for (int i = 0; i < count; i++) {
            store.addFromRealtime(event("id-" + i, 200));
        }
        return store.domainBuckets().get(0);
    }

}
