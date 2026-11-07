package xyz.winhok.nettap.ui;

import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.tabs.TabLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.RuntimeCaptureConfig;
import xyz.winhok.nettap.ui.data.CaptureBodySnapshot;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;
import xyz.winhok.nettap.ui.fragment.DetailHostFragment;
import xyz.winhok.nettap.ui.fragment.CaptureSequenceFragment;
import xyz.winhok.nettap.ui.fragment.DetailRequestFragment;
import xyz.winhok.nettap.ui.fragment.DetailResponseFragment;
import xyz.winhok.nettap.ui.fragment.TlsKeylogFragment;
import xyz.winhok.nettap.ui.fragment.UrlFilterFragment;

import io.github.rosemoe.sora.widget.CodeEditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@LooperMode(LooperMode.Mode.PAUSED)
public final class MainActivityRobolectricTest {
    @Before
    public void setUp() {
        RuntimeCaptureConfig.resetForTests();
        RuntimeEnvironment.getApplication()
                .getSharedPreferences(UiPreferences.NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        deleteRecursively(RuntimeEnvironment.getApplication().getExternalFilesDir("export"));
        resetUiState();
    }

    @After
    public void tearDown() {
        NetTapUiState.stopRealtimeServer();
        RuntimeEnvironment.getApplication()
                .getSharedPreferences(UiPreferences.NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        deleteRecursively(RuntimeEnvironment.getApplication().getExternalFilesDir("export"));
        resetUiState();
    }

    @Test
    public void systemInsetsKeepAllScreensInsideSafeAreaWithoutAccumulating() {
        MainActivity activity = launch();
        android.view.View root = activity.findViewById(R.id.main_root);
        int safeAreas = WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout();
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(3, 30, 5, 20))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(8, 40, 0, 0))
                .build();

        WindowInsetsCompat remaining = ViewCompat.dispatchApplyWindowInsets(root, insets);
        assertEquals(8, root.getPaddingLeft());
        assertEquals(40, root.getPaddingTop());
        assertEquals(5, root.getPaddingRight());
        assertEquals(20, root.getPaddingBottom());
        assertEquals(Insets.NONE, remaining.getInsets(safeAreas));

        ViewCompat.dispatchApplyWindowInsets(root, insets);
        assertEquals(40, root.getPaddingTop());
        activity.showDetail(-1L);
        activity.getSupportFragmentManager().executePendingTransactions();
        assertEquals(40, root.getPaddingTop());

        WindowInsetsCompat changed = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 18, 0, 12))
                .build();
        ViewCompat.dispatchApplyWindowInsets(root, changed);
        assertEquals(0, root.getPaddingLeft());
        assertEquals(18, root.getPaddingTop());
        assertEquals(0, root.getPaddingRight());
        assertEquals(12, root.getPaddingBottom());
    }

    @Test
    public void launchShowsCaptureSearchAndControls() {
        MainActivity activity = launch();





        assertNotNull(activity.findViewById(R.id.bottom_nav));
        assertNotNull(findSearchView(activity));
        assertEquals(
                activity.getString(R.string.search_hint),
                findSearchView(activity).getQueryHint()
        );
        assertNotNull(findButtonByText(activity, R.string.action_freeze));
        assertTrue(toolbarHasAction(activity, R.id.action_clear));
        assertTrue(toolbarHasAction(activity, R.id.action_sort));
    }

    @Test
    public void subpageShowsItsTitleAndUpReturnsToParent() {
        MainActivity activity = launch();
        selectBottomTab(activity, R.id.tab_config);
        activity.showPage("url-filter", new UrlFilterFragment());
        activity.getSupportFragmentManager().executePendingTransactions();

        assertEquals(activity.getString(R.string.nav_url_filter), activity.getTitle());
        assertTrue((activity.getSupportActionBar().getDisplayOptions()
                & androidx.appcompat.app.ActionBar.DISPLAY_HOME_AS_UP) != 0);

        activity.onSupportNavigateUp();
        activity.getSupportFragmentManager().executePendingTransactions();
        assertEquals(activity.getString(R.string.nav_config), activity.getTitle());
        assertEquals(0, activity.getSupportActionBar().getDisplayOptions()
                & androidx.appcompat.app.ActionBar.DISPLAY_HOME_AS_UP);
    }

    @Test
    public void bottomNavHookNavigationShowsSettingsControls() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class)
                .setup()
                .get();

        selectBottomTab(activity, R.id.tab_hooks);

        assertNotNull(findTextViewByText(activity, R.string.settings_realtime_transport));
        assertNotNull(findTextViewByText(activity, R.string.settings_builder_hook));
    }

    @Test
    public void hookSettingsSaveAppliesSwitchesToRuntimeConfig() {
        MainActivity activity = launch();

        selectBottomTab(activity, R.id.tab_hooks);

        findSwitchByText(activity, R.string.settings_realtime_transport).setChecked(false);
        findSwitchByText(activity, R.string.settings_builder_hook).setChecked(false);
        findSwitchByText(activity, R.string.settings_tls_keylog).setChecked(false);
        findSwitchByText(activity, R.string.settings_cronet_keylog).setChecked(false);
        findButtonByText(activity, R.string.action_save).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertTrue(!RuntimeCaptureConfig.isRealtimeTransportEnabled());
        assertTrue(!RuntimeCaptureConfig.isBuilderInterceptorHookEnabled());
        assertTrue(!RuntimeCaptureConfig.isTlsKeylogEnabled());
        assertTrue(!RuntimeCaptureConfig.isCronetQuicKeylogEnabled());
    }

    @Test
    public void configTlsNavigationShowsPathDiagnosticsAndDisabledRootActions() {
        MainActivity activity = launch();

        activity.showPage("tls", new TlsKeylogFragment());
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertNotNull(findTextViewContaining(activity, "/data/data/<host-package>/files/_tls_keylog.log"));
        assertNotNull(findTextViewContaining(activity, activity.getString(R.string.tls_root_helper_note)));
        assertNotNull(findButtonByText(activity, R.string.tls_copy_path));
        assertTrue(!findButtonByText(activity, R.string.tls_share_disabled).isEnabled());
        assertTrue(!findButtonByText(activity, R.string.tls_clear_disabled).isEnabled());
    }

    @Test
    public void seededSessionShowsSequenceRowsAndSearchFiltersThem() {
        NetTapUiState.store().addFromRealtime(event(
                "request-1",
                "https://api.example.com/v1/users?active=true",
                201
        ));
        NetTapUiState.store().addFromRealtime(event(
                "request-2",
                "https://static.example.com/logo.png",
                404
        ));
        MainActivity activity = launch();
        RecyclerView list = findRecyclerViewWithAdapter(
                activity,
                "CaptureSequenceAdapter"
        );
        assertNotNull(list);
        assertEquals(2, list.getAdapter().getItemCount());

        findSearchView(activity).setQuery("static", true);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        list = findRecyclerViewWithAdapter(activity, "CaptureSequenceAdapter");

        assertEquals(1, list.getAdapter().getItemCount());
    }

    @Test
    public void exportVisibleWritesOnlyFilteredHarEntries() throws Exception {
        NetTapUiState.store().addFromRealtime(event(
                "request-1",
                "https://api.example.com/v1/users?active=true",
                201
        ));
        NetTapUiState.store().addFromRealtime(event(
                "request-2",
                "https://static.example.com/logo.png",
                404
        ));
        MainActivity activity = launch();

        findSearchView(activity).setQuery("static", true);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        performToolbarAction(activity, R.id.action_export_visible);

        String har = readNewHar(activity, 0);
        assertTrue(har.contains("https://static.example.com/logo.png"));
        assertFalse(har.contains("https://api.example.com/v1/users"));
    }

    @Test
    public void exportAllWritesWholeSessionHarEntries() throws Exception {
        NetTapUiState.store().addFromRealtime(event(
                "request-1",
                "https://api.example.com/v1/users?active=true",
                201
        ));
        NetTapUiState.store().addFromRealtime(event(
                "request-2",
                "https://static.example.com/logo.png",
                404
        ));
        MainActivity activity = launch();

        performToolbarAction(activity, R.id.action_export_all);

        String har = readNewHar(activity, 0);
        assertTrue(har.contains("https://api.example.com/v1/users"));
        assertTrue(har.contains("https://static.example.com/logo.png"));
    }

    @Test
    public void requestAndResponseDetailsScrollWhenContentExceedsScreen() {
        NetTapUiState.store().addFromRealtime(event("request-1", "https://api.example.com/v1/users?active=true", 201));
        MainActivity activity = launch();
        activity.showDetail(NetTapUiState.store().sequenceOldestFirst().get(0).getSessionId());
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        TabLayout tabs = findView(activity.getWindow().getDecorView(), TabLayout.class);

        for (int index = 1; index <= 2; index++) {
            tabs.getTabAt(index).select();
            activity.getSupportFragmentManager().executePendingTransactions();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            androidx.fragment.app.Fragment detail = activity.getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host);
            detail.getChildFragmentManager().executePendingTransactions();
            androidx.fragment.app.Fragment page = null;
            for (androidx.fragment.app.Fragment child : detail.getChildFragmentManager().getFragments()) {
                if ((index == 1 && child instanceof DetailRequestFragment)
                        || (index == 2 && child instanceof DetailResponseFragment)) {
                    page = child;
                    break;
                }
            }
            assertNotNull(page);
            assertTrue(page.getView() instanceof ScrollView);
            ScrollView scroll = (ScrollView) page.getView();
            assertNotNull(findView(scroll, HorizontalScrollView.class));
            scroll.measure(android.view.View.MeasureSpec.makeMeasureSpec(320, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY));
            scroll.layout(0, 0, 320, 100);
            assertTrue(scroll.canScrollVertically(1));
        }
    }

    @Test
    public void detailResponseExportWritesSingleEntryHar() throws Exception {
        NetTapUiState.store().addFromRealtime(event(
                "request-1",
                "https://api.example.com/v1/users?active=true",
                201
        ));
        NetTapUiState.store().addFromRealtime(event(
                "request-2",
                "https://static.example.com/logo.png",
                404
        ));
        MainActivity activity = launch();

        activity.showDetail(NetTapUiState.store().sequenceOldestFirst().get(0).getSessionId());
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        TabLayout tabs = findView(activity.getWindow().getDecorView(), TabLayout.class);
        assertNotNull(tabs);
        assertNotNull(tabs.getTabAt(2));
        tabs.getTabAt(2).select();
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        performToolbarAction(activity, R.id.action_export_entry);

        String har = readNewHar(activity, 0);
        assertTrue(har.contains("https://api.example.com/v1/users"));
        assertFalse(har.contains("https://static.example.com/logo.png"));
    }

    @Test
    public void freezeAndClearControlsUpdateSessionState() {
        NetTapUiState.store().addFromRealtime(event(
                "request-1",
                "https://api.example.com/v1/users",
                200
        ));
        MainActivity activity = launch();

        Button freeze = findButtonByText(activity, R.string.action_freeze);
        freeze.performClick();
        assertEquals(activity.getString(R.string.action_resume), freeze.getText().toString());
        assertTrue(findTextViewContaining(activity, activity.getString(R.string.session_state_frozen)) != null);

        performToolbarAction(activity, R.id.action_clear);
        androidx.appcompat.app.AlertDialog dialog =
                (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertNotNull(findTextViewByText(
                dialog.getWindow().getDecorView(),
                activity.getString(R.string.clear_confirm_title)
        ));
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).callOnClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertTrue(NetTapUiState.store().sequenceOldestFirst().isEmpty());
        assertTrue(findTextViewContaining(activity, activity.getString(R.string.session_state_cleared)) != null);
    }

    @Test
    public void detailNavigationShowsFourTabsAndEventSummary() {
        NetTapUiState.store().addFromRealtime(event(
                "request-1",
                "https://api.example.com/v1/users?active=true",
                201
        ));
        MainActivity activity = launch();

        activity.showDetail(NetTapUiState.store().sequenceOldestFirst().get(0).getSessionId());
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertNotNull(findTextViewByText(activity, "General"));
        assertNotNull(findTextViewByText(activity, "Request"));
        assertNotNull(findTextViewByText(activity, "Response"));
        assertNotNull(findTextViewByText(activity, "Cookies"));
        assertNotNull(findTextViewContaining(activity, "https://api.example.com/v1/users"));
    }

    @Test
    public void bodyViewerShowsOmittedAndBinaryStates() {
        MainActivity activity = launch();

        NetTapUiState.setSelectedBody(CaptureBodySnapshot.omitted(
                "application/octet-stream",
                12L,
                null,
                "binary body"
        ));
        activity.showBodyViewer();
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertNotNull(findTextViewContaining(activity, "Omitted: binary body"));
        assertNotNull(findTextViewContaining(activity, "Language: text"));

        NetTapUiState.setSelectedBody(CaptureBodySnapshot.text(
                "image/png",
                8L,
                null,
                false,
                "aGVsbG8="
        ));
        activity.showBodyViewer();
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertNotNull(findTextViewContaining(activity, "Binary (base64)"));
        Button decode = findButtonByText(activity, R.string.body_decode_to_file);
        assertNotNull(decode);
        assertTrue(!decode.isEnabled());
    }

    @Test
    public void bodyViewerOpensFiftyKbJsonInEditor() {
        MainActivity activity = launch();
        String body = fiftyKbJson();
        assertTrue(body.length() > 50 * 1024);

        NetTapUiState.setSelectedBody(CaptureBodySnapshot.text(
                "application/json",
                body.length(),
                null,
                false,
                body
        ));
        activity.showBodyViewer();
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        CodeEditor editor = findView(activity.getWindow().getDecorView(), CodeEditor.class);
        assertNotNull(editor);
        String editorText = editor.getText().toString();
        assertFalse(editorText.contains(activity.getString(R.string.body_large_placeholder)));
        assertTrue(editorText.contains("\"items\""));
        assertTrue(editorText.contains("\"id\": 2499"));
    }

    @Test
    public void captureTabsExposeSequenceAndDomains() {
        MainActivity activity = launch();
        TabLayout tabs = findView(activity.getWindow().getDecorView(), TabLayout.class);

        assertNotNull(tabs);
        assertEquals("Sequence", String.valueOf(tabs.getTabAt(0).getText()));
        assertEquals("Domains", String.valueOf(tabs.getTabAt(1).getText()));
    }

    @Test
    public void duplicateProducerIdsHaveDistinctRowsReadStateAndDetails() {
        CaptureUiEvent first = event("same", "https://first.example/one", 200);
        CaptureUiEvent second = event("same", "https://second.example/two", 201);
        NetTapUiState.store().addFromRealtime(first);
        MainActivity activity = launch();
        RecyclerView list = findRecyclerViewWithAdapter(activity, "CaptureSequenceAdapter");
        assertNotNull(list);
        list.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1200, android.view.View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 1200);
        NetTapUiState.store().addFromRealtime(second);
        androidx.fragment.app.Fragment capture = activity.getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        for (androidx.fragment.app.Fragment child : capture.getChildFragmentManager().getFragments()) {
            if (child instanceof CaptureSequenceFragment) {
                ((CaptureSequenceFragment) child).refresh();
            }
        }
        list.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1200, android.view.View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 1200);
        assertEquals(2, list.getAdapter().getItemCount());
        assertEquals(second.getSessionId(), list.getAdapter().getItemId(0));
        assertEquals(first.getSessionId(), list.getAdapter().getItemId(1));
        assertFalse(list.getAdapter().getItemId(0) == list.getAdapter().getItemId(1));

        clickAdapterRow(list, 0);
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertNotNull(findTextViewContaining(activity, "https://second.example/two"));
        assertTrue(NetTapUiState.store().isRead(second.getSessionId()));
        assertFalse(NetTapUiState.store().isRead(first.getSessionId()));
    }

    @Test
    public void domainListFollowsSearchAndSessionFilters() {
        NetTapUiState.store().addFromRealtime(event("request-1", "https://api.example.com/users", 200));
        NetTapUiState.store().addFromRealtime(event("request-2", "https://static.example.com/logo", 200));
        MainActivity activity = launch();
        TabLayout tabs = findView(activity.getWindow().getDecorView(), TabLayout.class);
        tabs.getTabAt(1).select();
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        RecyclerView domains = findRecyclerViewWithAdapter(activity, "CaptureDomainsAdapter");
        assertNotNull(domains);
        assertEquals(2, domains.getAdapter().getItemCount());
        findSearchView(activity).setQuery("static", false);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(1, domains.getAdapter().getItemCount());
        NetTapUiState.setPackageFilter("another.package");
        findSearchView(activity).setQuery("", false);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(0, domains.getAdapter().getItemCount());
        NetTapUiState.setPackageFilter(null);
        findSearchView(activity).setQuery("static", false);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        clickAdapterRow(domains, 0);
        assertEquals("static.example.com", NetTapUiState.getHostFilter());
    }

    @Test
    public void domainRowClickAppliesHostFilterAndReturnsToSequence() {
        NetTapUiState.store().addFromRealtime(event(
                "request-1",
                "https://api.example.com/v1/users",
                200
        ));
        NetTapUiState.store().addFromRealtime(event(
                "request-2",
                "https://static.example.com/logo.png",
                200
        ));
        MainActivity activity = launch();
        TabLayout tabs = findView(activity.getWindow().getDecorView(), TabLayout.class);
        assertNotNull(tabs);
        assertNotNull(tabs.getTabAt(1));

        tabs.getTabAt(1).select();
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        RecyclerView domains = findRecyclerViewWithAdapter(activity, "CaptureDomainsAdapter");
        assertNotNull(domains);
        clickAdapterRow(domains, 0);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertEquals("api.example.com", NetTapUiState.getHostFilter());
        assertEquals(0, tabs.getSelectedTabPosition());
    }

    private static SearchView findSearchView(MainActivity activity) {
        SearchView sv = findView(activity.getWindow().getDecorView(), SearchView.class);
        if (sv != null) return sv;
        MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null && toolbar.getMenu() != null) {
            android.view.MenuItem item = toolbar.getMenu().findItem(R.id.action_search);
            if (item != null && item.getActionView() instanceof SearchView) {
                return (SearchView) item.getActionView();
            }
        }
        return null;
    }

    private static void selectBottomTab(MainActivity activity, int itemId) {
        BottomNavigationView bottomNavigationView = activity.findViewById(R.id.bottom_nav);
        assertNotNull(bottomNavigationView);
        bottomNavigationView.setSelectedItemId(itemId);
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static boolean toolbarHasAction(MainActivity activity, int itemId) {
        MaterialToolbar toolbar = toolbarWithMenuItem(activity.getWindow().getDecorView(), itemId);
        return toolbar != null;
    }

    private static void performToolbarAction(MainActivity activity, int itemId) {
        MaterialToolbar toolbar = toolbarWithMenuItem(activity.getWindow().getDecorView(), itemId);
        assertNotNull(toolbar);
        assertTrue(toolbar.getMenu().performIdentifierAction(itemId, 0));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static MaterialToolbar toolbarWithMenuItem(android.view.View root, int itemId) {
        if (root instanceof MaterialToolbar) {
            MaterialToolbar toolbar = (MaterialToolbar) root;
            if (toolbar.getMenu().findItem(itemId) != null) {
                return toolbar;
            }
        }
        if (!(root instanceof android.view.ViewGroup)) {
            return null;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            MaterialToolbar match = toolbarWithMenuItem(group.getChildAt(i), itemId);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static MainActivity launch() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class)
                .setup()
                .get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        activity.invalidateOptionsMenu();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        return activity;
    }

    private static void resetUiState() {
        NetTapUiState.stopRealtimeServer();
        NetTapUiState.store().clear();
        NetTapUiState.setHostFilter(null);
        NetTapUiState.setHookFilter(null);
        NetTapUiState.setPackageFilter(null);
        NetTapUiState.setDetailQuery("");
        DetailHostFragment.setSelectedEventId(null);
    }

    private static CaptureUiEvent event(String id, String url, int status) {
        return CaptureUiEvent.fromJson(sampleJson(url, status)
                .replace("\"id\":\"request-1\"", "\"id\":\"" + id + "\""));
    }

    private static String sampleJson(String url, int responseCode) {
        return "{"
                + "\"schemaVersion\":2,"
                + "\"id\":\"request-1\","
                + "\"timestamp\":\"2026-05-14T00:00:00.000Z\","
                + "\"packageName\":\"com.example\","
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
                + "\"error\":null"
                + "}";
    }

    private static String fiftyKbJson() {
        StringBuilder json = new StringBuilder("{\"items\":[");
        for (int i = 0; i < 2500; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"id\":").append(i).append(",\"ok\":true}");
        }
        json.append("]}");
        return json.toString();
    }

    private static Button findButtonByText(MainActivity activity, int textRes) {
        return findButtonByText(
                activity.getWindow().getDecorView(),
                activity.getString(textRes)
        );
    }

    private static TextView findTextViewByText(MainActivity activity, int textRes) {
        return findTextViewByText(
                activity.getWindow().getDecorView(),
                activity.getString(textRes)
        );
    }

    private static SwitchMaterial findSwitchByText(MainActivity activity, int textRes) {
        SwitchMaterial match = findSwitchByText(
                activity.getWindow().getDecorView(),
                activity.getString(textRes)
        );
        assertNotNull(match);
        return match;
    }

    private static TextView findTextViewByText(MainActivity activity, String text) {
        return findTextViewByText(activity.getWindow().getDecorView(), text);
    }

    private static RecyclerView findRecyclerViewWithAdapter(MainActivity activity, String adapterName) {
        return findRecyclerViewWithAdapter(
                activity.getWindow().getDecorView(),
                adapterName
        );
    }

    private static RecyclerView findRecyclerViewWithAdapter(android.view.View root, String adapterName) {
        if (root instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) root;
            RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
            if (adapter != null && adapter.getClass().getSimpleName().contains(adapterName)) {
                return recyclerView;
            }
        }
        if (!(root instanceof android.view.ViewGroup)) {
            return null;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            RecyclerView match = findRecyclerViewWithAdapter(group.getChildAt(i), adapterName);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void clickAdapterRow(RecyclerView recyclerView, int position) {
        RecyclerView.Adapter adapter = recyclerView.getAdapter();
        assertNotNull(adapter);
        assertTrue(adapter.getItemCount() > position);
        FrameLayout parent = new FrameLayout(recyclerView.getContext());
        RecyclerView.ViewHolder holder = adapter.onCreateViewHolder(
                parent,
                adapter.getItemViewType(position)
        );
        adapter.onBindViewHolder(holder, position);
        holder.itemView.performClick();
    }

    private static String readNewHar(MainActivity activity, int existingCount) throws Exception {
        File dir = activity.getExternalFilesDir("export");
        File[] files = waitForHarFiles(dir, existingCount + 1);
        File newest = files[0];
        for (File file : files) {
            if (file.lastModified() > newest.lastModified()) {
                newest = file;
            }
        }
        return new String(Files.readAllBytes(newest.toPath()), StandardCharsets.UTF_8);
    }

    private static File[] waitForHarFiles(File dir, int expectedCount) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        File[] files = harFiles(dir);
        while (files.length < expectedCount && System.nanoTime() < deadline) {
            Thread.sleep(10L);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            files = harFiles(dir);
        }
        assertTrue(files.length >= expectedCount);
        return files;
    }

    private static File[] harFiles(File dir) {
        if (dir == null || !dir.exists()) {
            return new File[0];
        }
        File[] files = dir.listFiles((file, name) -> name.endsWith(".har"));
        return files == null ? new File[0] : files;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        assertTrue(file.delete());
    }

    private static Button findButtonByText(android.view.View root, String text) {
        if (root instanceof Button && text.contentEquals(((Button) root).getText())) {
            return (Button) root;
        }
        if (!(root instanceof android.view.ViewGroup)) {
            return null;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            Button match = findButtonByText(group.getChildAt(i), text);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static TextView findTextViewByText(android.view.View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) {
            return (TextView) root;
        }
        if (!(root instanceof android.view.ViewGroup)) {
            return null;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView match = findTextViewByText(group.getChildAt(i), text);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static SwitchMaterial findSwitchByText(android.view.View root, String text) {
        if (root instanceof SwitchMaterial && text.contentEquals(((SwitchMaterial) root).getText())) {
            return (SwitchMaterial) root;
        }
        if (!(root instanceof android.view.ViewGroup)) {
            return null;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            SwitchMaterial match = findSwitchByText(group.getChildAt(i), text);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static TextView findTextViewContaining(MainActivity activity, String text) {
        return findTextViewContaining(activity.getWindow().getDecorView(), text);
    }

    private static TextView findTextViewContaining(android.view.View root, String text) {
        if (root instanceof TextView && ((TextView) root).getText().toString().contains(text)) {
            return (TextView) root;
        }
        if (!(root instanceof android.view.ViewGroup)) {
            return null;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView match = findTextViewContaining(group.getChildAt(i), text);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static <T extends android.view.View> T findView(android.view.View root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (!(root instanceof android.view.ViewGroup)) {
            return null;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            T match = findView(group.getChildAt(i), type);
            if (match != null) {
                return match;
            }
        }
        return null;
    }
}
