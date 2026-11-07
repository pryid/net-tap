package xyz.winhok.nettap.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import xyz.winhok.nettap.RuntimeCaptureConfig;
import xyz.winhok.nettap.ui.data.CaptureNdjsonParser;
import xyz.winhok.nettap.ui.data.BodyDisplayState;
import xyz.winhok.nettap.ui.data.CaptureBodySnapshot;
import xyz.winhok.nettap.ui.data.CaptureSessionStore;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;
import xyz.winhok.nettap.ui.data.RealtimeCaptureServer;
import xyz.winhok.nettap.ui.data.SearchEngine;

public final class NetTapUiState {
    private static volatile CaptureSessionStore store = new CaptureSessionStore(2000, 100L * 1024L * 1024L);
    private static RealtimeCaptureServer server;
    private static volatile String hostFilter;
    private static volatile String hookFilter;
    private static volatile String packageFilter;
    private static volatile String detailQuery = "";
    private static volatile BodyDisplayState selectedBody = BodyDisplayState.empty();
    private static volatile String lastServerError = "";

    private NetTapUiState() {
    }

    public static synchronized CaptureSessionStore store() {
        return store;
    }

    public static synchronized void configureStore(int maxRecords, int maxMemoryMb) {
        CaptureSessionStore previous = store;
        CaptureSessionStore.State previousState = previous.getState();
        CaptureSessionStore next = new CaptureSessionStore(
                Math.max(1, maxRecords),
                Math.max(1L, maxMemoryMb) * 1024L * 1024L
        );
        for (CaptureUiEvent event : previous.sequenceOldestFirst()) {
            next.addFromRealtime(event);
            if (previous.isRead(event.getSessionId())) {
                next.markRead(event.getSessionId());
            }
        }
        if (previousState == CaptureSessionStore.State.FROZEN) {
            next.freeze();
        } else if (previousState == CaptureSessionStore.State.CLEARED) {
            next.clear();
        }
        store = next;
    }

    public static synchronized boolean startRealtimeServer(int port) {
        if (server != null && server.getPort() == port) {
            return true;
        }
        stopRealtimeServer();
        server = new RealtimeCaptureServer(port, line -> {
            CaptureUiEvent event = CaptureNdjsonParser.parseLineOrNull(line);
            if (event == null) {
                return false;
            }
            CaptureSessionStore currentStore = store;
            if (currentStore.getState() == CaptureSessionStore.State.FROZEN) {
                return true;
            }
            return currentStore.addFromRealtime(event)
                    || currentStore.getState() == CaptureSessionStore.State.FROZEN;
        });
        try {
            server.start();
            lastServerError = "";
            return true;
        } catch (IllegalStateException e) {
            lastServerError = e.getMessage();
            server = null;
            return false;
        }
    }

    public static synchronized void stopRealtimeServer() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    public static int realtimePort() {
        RealtimeCaptureServer current = server;
        return current == null ? RuntimeCaptureConfig.getRealtimePort() : current.getPort();
    }

    public static long corruptLineCount() {
        RealtimeCaptureServer current = server;
        return current == null ? 0L : current.getCorruptLineCount();
    }

    public static long oversizedLineCount() {
        RealtimeCaptureServer current = server;
        return current == null ? 0L : current.getOversizedLineCount();
    }

    public static long acceptedLineCount() {
        RealtimeCaptureServer current = server;
        return current == null ? 0L : current.getAcceptedLineCount();
    }

    public static String lastServerError() {
        return lastServerError;
    }

    public static void setHostFilter(String host) {
        hostFilter = host;
    }

    public static String getHostFilter() {
        return hostFilter;
    }

    public static void setHookFilter(String hook) {
        hookFilter = emptyToNull(hook);
    }

    public static String getHookFilter() {
        return hookFilter;
    }

    public static void setPackageFilter(String packageName) {
        packageFilter = emptyToNull(packageName);
    }

    public static String getPackageFilter() {
        return packageFilter;
    }

    public static void setDetailQuery(String query) {
        detailQuery = query == null ? "" : query.trim();
    }

    public static String getDetailQuery() {
        return detailQuery;
    }

    public static List<CaptureUiEvent> filteredEvents(String query, boolean newestFirst) {
        List<CaptureUiEvent> base;
        if (hostFilter == null || hostFilter.isEmpty()) {
            base = newestFirst ? store.sequenceNewestFirst() : store.sequenceOldestFirst();
        } else {
            ArrayList<CaptureUiEvent> filteredByHost = new ArrayList<>(store.eventsForHost(hostFilter));
            if (newestFirst) {
                Collections.reverse(filteredByHost);
            }
            base = filteredByHost;
        }
        return SearchEngine.filter(applySessionFilters(base), query);
    }

    public static void setSelectedBody(CaptureBodySnapshot body) {
        selectedBody = BodyDisplayState.from(body);
    }

    public static BodyDisplayState getSelectedBody() {
        return selectedBody;
    }

    private static List<CaptureUiEvent> applySessionFilters(List<CaptureUiEvent> events) {
        if (hookFilter == null && packageFilter == null) {
            return events;
        }
        ArrayList<CaptureUiEvent> filtered = new ArrayList<>();
        for (CaptureUiEvent event : events) {
            if (hookFilter != null && !hookFilter.equals(event.getHook())) {
                continue;
            }
            if (packageFilter != null && !packageFilter.equals(event.getPackageName())) {
                continue;
            }
            filtered.add(event);
        }
        return filtered;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
