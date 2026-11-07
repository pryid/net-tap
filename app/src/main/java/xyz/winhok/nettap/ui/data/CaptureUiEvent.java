package xyz.winhok.nettap.ui.data;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class CaptureUiEvent {
    public static final String INVALID_HOST = "__invalid_url__";
    private static final int SUPPORTED_SCHEMA_VERSION = 2;
    private static final AtomicLong NEXT_SESSION_ID = new AtomicLong();

    private final String id;
    private final long sessionId = NEXT_SESSION_ID.incrementAndGet();
    private final String timestamp;
    private final String packageName;
    private final String hook;
    private final String method;
    private final String url;
    private final Map<String, String> requestHeaders;
    private final CaptureBodySnapshot requestBody;
    private final int responseCode;
    private final String responseMessage;
    private final Map<String, String> responseHeaders;
    private final CaptureBodySnapshot responseBody;
    private final long durationMs;
    private final String error;
    private final String host;
    private final String path;
    private final String query;
    private final long approximateBytes;
    private final String rawJson;

    private CaptureUiEvent(
            String id,
            String timestamp,
            String packageName,
            String hook,
            String method,
            String url,
            Map<String, String> requestHeaders,
            CaptureBodySnapshot requestBody,
            int responseCode,
            String responseMessage,
            Map<String, String> responseHeaders,
            CaptureBodySnapshot responseBody,
            long durationMs,
            String error,
            String rawJson
    ) {
        this.id = fallback(id, "unknown");
        this.timestamp = fallback(timestamp, "");
        this.packageName = fallback(packageName, "");
        this.hook = fallback(hook, "");
        this.method = fallback(method, "");
        this.url = fallback(url, "");
        this.requestHeaders = immutableCopy(requestHeaders);
        this.requestBody = requestBody == null ? CaptureBodySnapshot.empty() : requestBody;
        this.responseCode = responseCode;
        this.responseMessage = fallback(responseMessage, "");
        this.responseHeaders = immutableCopy(responseHeaders);
        this.responseBody = responseBody == null ? CaptureBodySnapshot.empty() : responseBody;
        this.durationMs = durationMs;
        this.error = error;
        this.rawJson = fallback(rawJson, "");
        UrlParts parts = parseUrl(this.url);
        this.host = parts.host;
        this.path = parts.path;
        this.query = parts.query;
        this.approximateBytes = computeApproximateBytes();
    }

    public static CaptureUiEvent fromJson(String json) {
        Map<String, Object> root = SimpleJsonParser.parseObject(json);
        int schemaVersion = intValue(root.get("schemaVersion"), -1);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported schemaVersion " + schemaVersion);
        }
        return new CaptureUiEvent(
                stringValue(root.get("id")),
                stringValue(root.get("timestamp")),
                stringValue(root.get("packageName")),
                stringValue(root.get("hook")),
                stringValue(root.get("method")),
                stringValue(root.get("url")),
                stringMap(root.get("requestHeaders")),
                CaptureBodySnapshot.fromValue(root.get("requestBody")),
                intValue(root.get("responseCode"), 0),
                stringValue(root.get("responseMessage")),
                stringMap(root.get("responseHeaders")),
                CaptureBodySnapshot.fromValue(root.get("responseBody")),
                longValue(root.get("durationMs"), 0L),
                stringValue(root.get("error")),
                json
        );
    }

    CaptureUiEvent copyForNewSessionEntry() {
        return new CaptureUiEvent(id, timestamp, packageName, hook, method, url,
                requestHeaders, requestBody, responseCode, responseMessage, responseHeaders,
                responseBody, durationMs, error, rawJson);
    }

    /** Unique within this UI process, independent of the producer's event ID. */
    public long getSessionId() {
        return sessionId;
    }

    static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static int intValue(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static long longValue(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static Map<String, String> stringMap(Object value) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : SimpleJsonParser.castMap(value).entrySet()) {
            if (entry.getKey() != null) {
                result.put(entry.getKey(), stringValue(entry.getValue()));
            }
        }
        return result;
    }

    private static Map<String, String> immutableCopy(Map<String, String> input) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (input != null) {
            result.putAll(input);
        }
        return Collections.unmodifiableMap(result);
    }

    private static UrlParts parseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return UrlParts.invalid();
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return UrlParts.invalid();
            }
            String path = uri.getRawPath();
            return new UrlParts(
                    host.toLowerCase(Locale.US),
                    path == null || path.isEmpty() ? "/" : path,
                    uri.getRawQuery()
            );
        } catch (RuntimeException e) {
            return UrlParts.invalid();
        }
    }

    private long computeApproximateBytes() {
        long total = 1024L;
        total += size(id) + size(timestamp) + size(packageName) + size(hook);
        total += size(method) + size(url) + size(responseMessage) + size(error);
        total += headersSize(requestHeaders) + headersSize(responseHeaders);
        total += requestBody.approximateBytes() + responseBody.approximateBytes();
        total += size(rawJson);
        return Math.max(0L, total);
    }

    private static long headersSize(Map<String, String> headers) {
        long total = 0L;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            total += size(entry.getKey()) + size(entry.getValue());
        }
        return total;
    }

    private static long size(String value) {
        return value == null ? 0L : value.length() * 2L;
    }

    private static String fallback(String value, String fallback) {
        return value == null ? fallback : value;
    }

    public String searchableText() {
        StringBuilder text = new StringBuilder();
        append(text, id);
        append(text, timestamp);
        append(text, packageName);
        append(text, hook);
        append(text, method);
        append(text, url);
        append(text, getDisplayStatus());
        append(text, statusBand());
        append(text, responseMessage);
        append(text, error);
        appendHeaders(text, requestHeaders);
        appendHeaders(text, responseHeaders);
        append(text, requestBody.getText());
        append(text, responseBody.getText());
        return text.toString().toLowerCase(Locale.US);
    }

    private static void appendHeaders(StringBuilder text, Map<String, String> headers) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            append(text, entry.getKey());
            append(text, entry.getValue());
        }
    }

    private static void append(StringBuilder text, String value) {
        if (value != null && !value.isEmpty()) {
            text.append(' ').append(value);
        }
    }

    public String getId() {
        return id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getHook() {
        return hook;
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    public CaptureBodySnapshot getRequestBody() {
        return requestBody;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public CaptureBodySnapshot getResponseBody() {
        return responseBody;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getError() {
        return error;
    }

    public String getHost() {
        return host;
    }

    public String getHostLabel() {
        return INVALID_HOST.equals(host) ? "(invalid URL)" : host;
    }

    public String getPath() {
        return path;
    }

    public String getQuery() {
        return query;
    }

    public String getDisplayStatus() {
        if (error != null && !error.isEmpty()) {
            return "ERR";
        }
        return responseCode <= 0 ? "-" : Integer.toString(responseCode);
    }

    private String statusBand() {
        if (error != null && !error.isEmpty()) {
            return "err";
        }
        if (responseCode >= 200 && responseCode < 300) {
            return "2xx";
        }
        if (responseCode >= 300 && responseCode < 400) {
            return "3xx";
        }
        if (responseCode >= 400 && responseCode < 500) {
            return "4xx";
        }
        if (responseCode >= 500 && responseCode < 600) {
            return "5xx";
        }
        return "";
    }

    public long getApproximateBytes() {
        return approximateBytes;
    }

    public String getRawJson() {
        return rawJson;
    }

    private static final class UrlParts {
        final String host;
        final String path;
        final String query;

        UrlParts(String host, String path, String query) {
            this.host = host;
            this.path = path;
            this.query = query;
        }

        static UrlParts invalid() {
            return new UrlParts(INVALID_HOST, "", null);
        }
    }
}
