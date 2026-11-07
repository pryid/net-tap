package xyz.winhok.nettap.ui.data;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CaptureSessionStoreTest {
    @Test
    public void repeatedEventObjectGetsDistinctSessionIdentity() {
        CaptureSessionStore store = new CaptureSessionStore(10, 1024 * 1024);
        CaptureUiEvent event = event("same", "https://example.com", 200);
        store.addFromRealtime(event);
        store.addFromRealtime(event);
        List<CaptureUiEvent> entries = store.sequenceOldestFirst();
        assertEquals(2, entries.size());
        assertFalse(entries.get(0).getSessionId() == entries.get(1).getSessionId());
        store.markRead(entries.get(0).getSessionId());
        assertFalse(store.isRead(entries.get(1).getSessionId()));
    }

    @Test
    public void freezeDropsNewEventsAndResumeAcceptsOnlyFutureEvents() {
        CaptureSessionStore store = new CaptureSessionStore(10, 1024 * 1024);
        CaptureUiEvent first = event("1", "https://a.example/one", 200);
        CaptureUiEvent frozen = event("2", "https://a.example/two", 200);
        CaptureUiEvent resumed = event("3", "https://a.example/three", 200);

        assertTrue(store.addFromRealtime(first));
        store.freeze();
        assertFalse(store.addFromRealtime(frozen));
        store.resume();
        assertTrue(store.addFromRealtime(resumed));

        List<CaptureUiEvent> sequence = store.sequenceOldestFirst();
        assertEquals(2, sequence.size());
        assertEquals("1", sequence.get(0).getId());
        assertEquals("3", sequence.get(1).getId());
    }

    @Test
    public void countEvictionRebuildsDomainBuckets() {
        CaptureSessionStore store = new CaptureSessionStore(2, 1024 * 1024);

        store.addFromRealtime(event("1", "https://old.example/path", 200));
        store.addFromRealtime(event("2", "https://new.example/path", 404));
        store.addFromRealtime(event("3", "https://new.example/next", 500));

        assertEquals(2, store.sequenceOldestFirst().size());
        assertEquals(1, store.domainBuckets().size());
        DomainBucket bucket = store.domainBuckets().get(0);
        assertEquals("new.example", bucket.getHost());
        assertEquals(2, bucket.getCount());
        assertEquals(1, bucket.getStatus5xxCount());
        assertEquals(1, bucket.getStatus4xxCount());
    }

    @Test
    public void specDefaultCountEvictionTrimsTwentyFiveHundredEventsToTwoThousand() {
        CaptureSessionStore store = new CaptureSessionStore(2000, 100 * 1024 * 1024L);

        for (int i = 0; i < 2500; i++) {
            store.addFromRealtime(event("id-" + i, "https://api.example/path/" + i, 200));
        }

        List<CaptureUiEvent> sequence = store.sequenceOldestFirst();
        assertEquals(2000, sequence.size());
        assertEquals("id-500", sequence.get(0).getId());
        assertEquals("id-2499", sequence.get(sequence.size() - 1).getId());
        assertEquals(1, store.domainBuckets().size());
        assertEquals(2000, store.domainBuckets().get(0).getCount());
    }

    @Test
    public void memoryEvictionRemovesOldestEventsAndReadState() {
        CaptureUiEvent oldEvent = event("old", "https://old.example/path", 200);
        CaptureUiEvent newEvent = event("new", "https://new.example/path", 200);
        long oneEventBudget = Math.max(1024L, newEvent.getApproximateBytes());
        CaptureSessionStore store = new CaptureSessionStore(10, oneEventBudget);

        store.addFromRealtime(oldEvent);
        store.markRead(oldEvent.getSessionId());
        store.addFromRealtime(newEvent);

        assertEquals(1, store.sequenceOldestFirst().size());
        assertEquals("new", store.sequenceOldestFirst().get(0).getId());
        assertFalse(store.isRead(oldEvent.getSessionId()));
        assertEquals(1, store.domainBuckets().size());
        assertEquals("new.example", store.domainBuckets().get(0).getHost());
    }

    @Test
    public void clearRemovesSessionAndDomainState() {
        CaptureSessionStore store = new CaptureSessionStore(10, 1024 * 1024);
        CaptureUiEvent first = event("1", "https://example.com", 200);
        store.addFromRealtime(first);
        store.markRead(first.getSessionId());

        store.clear();

        assertTrue(store.sequenceOldestFirst().isEmpty());
        assertTrue(store.domainBuckets().isEmpty());
        assertFalse(store.isRead(first.getSessionId()));
        assertEquals(CaptureSessionStore.State.CLEARED, store.getState());
    }

    @Test
    public void readStateIsSessionLocal() {
        CaptureSessionStore store = new CaptureSessionStore(10, 1024 * 1024);
        CaptureUiEvent first = event("1", "https://example.com", 200);
        CaptureUiEvent second = event("2", "https://example.com", 200);
        store.addFromRealtime(first);

        store.markRead(first.getSessionId());

        assertTrue(store.isRead(first.getSessionId()));
        assertFalse(store.isRead(second.getSessionId()));
    }

    @Test
    public void clearHostBucketRemovesOnlyMatchingEventsAndReadState() {
        CaptureSessionStore store = new CaptureSessionStore(10, 1024 * 1024);
        CaptureUiEvent old = event("old", "https://old.example/path", 200);
        CaptureUiEvent current = event("new", "https://new.example/path", 200);
        store.addFromRealtime(old);
        store.addFromRealtime(current);
        store.markRead(old.getSessionId());
        store.markRead(current.getSessionId());

        int removed = store.clearHostBucket("old.example");

        assertEquals(1, removed);
        assertEquals(1, store.sequenceOldestFirst().size());
        assertEquals("new", store.sequenceOldestFirst().get(0).getId());
        assertFalse(store.isRead(old.getSessionId()));
        assertTrue(store.isRead(current.getSessionId()));
        assertEquals(1, store.domainBuckets().size());
        assertEquals("new.example", store.domainBuckets().get(0).getHost());
    }

    @Test
    public void invalidUrlEventsAreGroupedIntoInvalidUrlBucket() {
        CaptureSessionStore store = new CaptureSessionStore(10, 1024 * 1024);

        store.addFromRealtime(event("invalid", "", 0));

        assertEquals(1, store.domainBuckets().size());
        DomainBucket bucket = store.domainBuckets().get(0);
        assertEquals(CaptureUiEvent.INVALID_HOST, bucket.getHost());
        assertEquals("(invalid URL)", bucket.getLabel());
        assertEquals(1, bucket.getErrorCount());
    }

    private static CaptureUiEvent event(String id, String url, int status) {
        return CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(url, status, null)
                .replace("\"request-1\"", "\"" + id + "\""));
    }
}
