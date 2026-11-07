package xyz.winhok.nettap.ui;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.RuntimeCaptureConfig;
import xyz.winhok.nettap.ui.fragment.DetailHostFragment;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MainActivitySmokeTest {
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Before
    public void resetSession() {
        activityRule.getScenario().onActivity(activity -> {
            new UiPreferences(activity).resetDefaults();
            NetTapUiState.store().clear();
            NetTapUiState.store().resume();
            NetTapUiState.setHostFilter(null);
            NetTapUiState.setHookFilter(null);
            NetTapUiState.setPackageFilter(null);
            DetailHostFragment.setSelectedEventId(null);
            NetTapUiState.startRealtimeServer(RuntimeCaptureConfig.DEFAULT_REALTIME_PORT);
        });
    }

    @Test
    public void overflowMenuIsClickable() {
        onView(withContentDescription(androidx.appcompat.R.string.abc_action_menu_overflow_description))
                .perform(click());
        onView(withText(R.string.action_sort)).check(matches(isDisplayed()));
    }

    @Test
    public void launchesCaptureScreenAndNavigatesBottomNavItems() {
        onView(withHint(R.string.search_hint)).check(matches(isDisplayed()));

        activityRule.getScenario().onActivity(activity -> {
            com.google.android.material.bottomnavigation.BottomNavigationView bottomNav =
                    activity.findViewById(R.id.bottom_nav);
            bottomNav.setSelectedItemId(R.id.tab_hooks);
        });
        onView(withText(R.string.settings_realtime_transport)).check(matches(isDisplayed()));
    }

    @Test
    public void receivesRealtimeNdjsonAndShowsSequenceAndInvalidDomainRows() throws Exception {
        sendNdjson(sampleJson("request-valid", "https://api.example.com/v1/users", 201, null));
        sendNdjson(sampleJson("request-invalid", "", 0, "boom"));

        long startNanos = System.nanoTime();
        waitUntil(() -> NetTapUiState.store().totalCount() == 2, 2000L);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        assertTrue("realtime event should be accepted within 2s, actual=" + elapsedMs, elapsedMs <= 2000L);

        waitForDisplayedText("https://api.example.com/v1/users", 2000L);
        waitForDisplayedText("ERR", 2000L);

        onView(withText(UiTabs.captureTitle(1))).perform(click());
        waitForDisplayedText("api.example.com", 2000L);
        waitForDisplayedText("(invalid URL)", 2000L);
    }

    @Test
    public void opensRealtimeEventDetailAndRendersFourTabs() throws Exception {
        sendNdjson(sampleJson("request-detail", "https://api.example.com/v1/detail?active=true", 201, null));

        waitUntil(() -> NetTapUiState.store().totalCount() == 1, 2000L);
        waitForDisplayedText("https://api.example.com/v1/detail?active=true", 2000L);
        onView(withText(containsString("https://api.example.com/v1/detail?active=true"))).perform(click());

        waitForDisplayedText(UiTabs.detailTitle(0), 2000L);
        waitForDisplayedText(UiTabs.detailTitle(1), 2000L);
        waitForDisplayedText(UiTabs.detailTitle(2), 2000L);
        waitForDisplayedText(UiTabs.detailTitle(3), 2000L);
        assertEquals("request-detail", DetailHostFragment.selectedEventId());
    }

    private static void sendNdjson(String json) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", RuntimeCaptureConfig.DEFAULT_REALTIME_PORT), 2000);
            try (OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(json);
                writer.write('\n');
                writer.flush();
            }
        }
    }

    private static void waitUntil(Condition condition, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() <= deadline) {
            if (condition.isMet()) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("condition was not met within " + timeoutMs + "ms");
    }

    private static void waitForDisplayedText(String text, long timeoutMs) throws Exception {
        waitUntil(() -> {
            try {
                onView(withText(containsString(text))).check(matches(isDisplayed()));
                return true;
            } catch (AssertionError | RuntimeException e) {
                return false;
            }
        }, timeoutMs);
    }

    private static String sampleJson(String id, String url, int responseCode, String error) {
        return "{"
                + "\"schemaVersion\":2,"
                + "\"id\":\"" + id + "\","
                + "\"timestamp\":\"2026-05-14T00:00:00.000Z\","
                + "\"packageName\":\"com.example.okhttp-demo\","
                + "\"hook\":\"okhttp\","
                + "\"method\":\"POST\","
                + "\"url\":\"" + url + "\","
                + "\"requestHeaders\":{\"Cookie\":\"sid=abc; theme=dark\"},"
                + "\"requestBody\":{\"contentType\":\"application/json\","
                + "\"contentLength\":7,"
                + "\"encoding\":null,"
                + "\"truncated\":false,"
                + "\"text\":\"{\\\"a\\\":1}\","
                + "\"omittedReason\":null},"
                + "\"responseCode\":" + responseCode + ","
                + "\"responseMessage\":\"Created\","
                + "\"responseHeaders\":{\"Content-Type\":\"application/json\","
                + "\"Set-Cookie\":\"id=1; Secure; HttpOnly; SameSite=Lax\"},"
                + "\"responseBody\":{\"contentType\":\"application/json\","
                + "\"contentLength\":11,"
                + "\"encoding\":null,"
                + "\"truncated\":false,"
                + "\"text\":\"{\\\"ok\\\":true}\","
                + "\"omittedReason\":null},"
                + "\"durationMs\":25,"
                + "\"error\":" + (error == null ? "null" : "\"" + error + "\"")
                + "}";
    }

    private interface Condition {
        boolean isMet();
    }
}
