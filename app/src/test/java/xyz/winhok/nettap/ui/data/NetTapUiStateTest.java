package xyz.winhok.nettap.ui.data;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import xyz.winhok.nettap.ui.NetTapUiState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class NetTapUiStateTest {
    @Before
    public void setUp() {
        NetTapUiState.stopRealtimeServer();
        NetTapUiState.configureStore(20, 10);
        NetTapUiState.store().clear();
        NetTapUiState.store().resume();
        NetTapUiState.setHostFilter(null);
        NetTapUiState.setHookFilter(null);
        NetTapUiState.setPackageFilter(null);
    }

    @After
    public void tearDown() {
        NetTapUiState.stopRealtimeServer();
        NetTapUiState.setHostFilter(null);
        NetTapUiState.setHookFilter(null);
        NetTapUiState.setPackageFilter(null);
        NetTapUiState.store().clear();
        NetTapUiState.store().resume();
    }

    @Test
    public void hostFilteredEventsRespectNewestFirstSort() {
        NetTapUiState.store().addFromRealtime(event("old", "2026-05-14T00:00:00.000Z"));
        NetTapUiState.store().addFromRealtime(event("new", "2026-05-14T00:00:01.000Z"));
        NetTapUiState.setHostFilter("api.example.com");

        List<CaptureUiEvent> events = NetTapUiState.filteredEvents("", true);

        assertEquals("new", events.get(0).getId());
        assertEquals("old", events.get(1).getId());
    }

    @Test
    public void filteredEventsRespectOldestFirstSort() {
        NetTapUiState.store().addFromRealtime(event("old", "2026-05-14T00:00:00.000Z"));
        NetTapUiState.store().addFromRealtime(event("new", "2026-05-14T00:00:01.000Z"));

        List<CaptureUiEvent> events = NetTapUiState.filteredEvents("", false);

        assertEquals("old", events.get(0).getId());
        assertEquals("new", events.get(1).getId());
    }

    @Test
    public void configureStorePreservesReadAndFrozenState() {
        CaptureUiEvent old = event("old", "2026-05-14T00:00:00.000Z");
        CaptureUiEvent duplicateId = event("old", "2026-05-14T00:00:01.000Z");
        NetTapUiState.store().addFromRealtime(old);
        NetTapUiState.store().addFromRealtime(duplicateId);
        NetTapUiState.store().markRead(old.getSessionId());
        NetTapUiState.store().freeze();

        NetTapUiState.configureStore(20, 10);

        assertEquals(CaptureSessionStore.State.FROZEN, NetTapUiState.store().getState());
        assertEquals(true, NetTapUiState.store().isRead(old.getSessionId()));
        assertEquals(false, NetTapUiState.store().isRead(duplicateId.getSessionId()));
        assertEquals(duplicateId.getSessionId(),
                NetTapUiState.store().sequenceOldestFirst().get(1).getSessionId());
    }

    @Test
    public void hookAndPackageFiltersNarrowEvents() {
        NetTapUiState.store().addFromRealtime(event(
                "okhttp-demo",
                "2026-05-14T00:00:00.000Z",
                "okhttp",
                "com.demo"
        ));
        NetTapUiState.store().addFromRealtime(event(
                "cronet-demo",
                "2026-05-14T00:00:01.000Z",
                "cronet",
                "com.demo"
        ));
        NetTapUiState.store().addFromRealtime(event(
                "okhttp-other",
                "2026-05-14T00:00:02.000Z",
                "okhttp",
                "com.other"
        ));

        NetTapUiState.setHookFilter("okhttp");
        NetTapUiState.setPackageFilter("com.demo");

        List<CaptureUiEvent> events = NetTapUiState.filteredEvents("", true);

        assertEquals(1, events.size());
        assertEquals("okhttp-demo", events.get(0).getId());
    }

    @Test
    public void detailQueryIsTrimmedAndStoredForDetailTabs() {
        NetTapUiState.setDetailQuery("  api  ");

        assertEquals("api", NetTapUiState.getDetailQuery());

        NetTapUiState.setDetailQuery(" ");

        assertEquals("", NetTapUiState.getDetailQuery());
    }

    @Test
    public void frozenRealtimeLinesAreDroppedWithoutCorruptDiagnostics() throws Exception {
        NetTapUiState.store().freeze();
        assertTrue(NetTapUiState.startRealtimeServer(0));

        sendLine(
                NetTapUiState.realtimePort(),
                CaptureUiEventTest.sampleJson("https://api.example.com/frozen", 200, null)
        );

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (NetTapUiState.acceptedLineCount() == 0L && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }

        assertEquals(0, NetTapUiState.store().totalCount());
        assertEquals(0L, NetTapUiState.corruptLineCount());
        assertEquals(1L, NetTapUiState.acceptedLineCount());
    }

    private static CaptureUiEvent event(String id, String timestamp) {
        return event(id, timestamp, "okhttp", "com.example");
    }

    private static CaptureUiEvent event(String id, String timestamp, String hook, String packageName) {
        return CaptureUiEvent.fromJson(CaptureUiEventTest.sampleJson(
                "https://api.example.com/" + id,
                200,
                null
        ).replace("\"id\":\"request-1\"", "\"id\":\"" + id + "\"")
                .replace("\"packageName\":\"com.example\"", "\"packageName\":\"" + packageName + "\"")
                .replace("\"hook\":\"okhttp\"", "\"hook\":\"" + hook + "\"")
                .replace("\"timestamp\":\"2026-05-14T00:00:00.000Z\"", "\"timestamp\":\"" + timestamp + "\""));
    }

    private static void sendLine(int port, String line) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                     socket.getOutputStream(),
                     StandardCharsets.UTF_8
             ))) {
            writer.print(line);
            writer.print('\n');
            writer.flush();
        }
    }
}
