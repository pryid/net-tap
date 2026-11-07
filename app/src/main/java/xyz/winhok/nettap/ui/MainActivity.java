package xyz.winhok.nettap.ui;

import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.content.FileProvider;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;
import java.io.IOException;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;
import xyz.winhok.nettap.ui.data.HarExportResult;
import xyz.winhok.nettap.ui.data.HarExportRequest;
import xyz.winhok.nettap.ui.data.HarExportWorker;
import xyz.winhok.nettap.ui.data.RawJsonFileExporter;
import xyz.winhok.nettap.ui.fragment.AboutFragment;
import xyz.winhok.nettap.ui.fragment.BodyViewerFragment;
import xyz.winhok.nettap.ui.fragment.CaptureDetailFragment;
import xyz.winhok.nettap.ui.fragment.CaptureListFragment;
import xyz.winhok.nettap.ui.fragment.ConfigFragment;
import xyz.winhok.nettap.ui.fragment.DetailHostFragment;
import xyz.winhok.nettap.ui.fragment.HookSettingsFragment;
import xyz.winhok.nettap.ui.fragment.SettingsFragment;
import xyz.winhok.nettap.ui.fragment.TlsKeylogFragment;
import xyz.winhok.nettap.ui.fragment.UrlFilterFragment;

public final class MainActivity extends AppCompatActivity {
    private static final String TAG = "NetTapMain";

    private NavigationController navigationController;
    private UiPreferences preferences;
    private View mainRoot;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNavigationView;
    private int currentTabId = -1;
    private boolean chromeVisible = true;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService harExportExecutor = Executors.newSingleThreadExecutor();
    private final HarExportWorker harExportWorker = new HarExportWorker(
            harExportExecutor,
            mainHandler::post
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        preferences = new UiPreferences(this);
        applyThemeMode(preferences.getThemeMode());
        preferences.applyToRuntime();
        preferences.applyRealtimeTransport();
        setContentView(R.layout.activity_main);
        mainRoot = findViewById(R.id.main_root);
        ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (view, insets) -> {
            int safeAreas = WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout();
            Insets safeInsets = insets.getInsets(safeAreas);
            view.setPadding(safeInsets.left, safeInsets.top, safeInsets.right, safeInsets.bottom);
            // The root has applied these insets for all screens, including the detail view.
            // Do not let child views apply the same system bar padding a second time.
            return new WindowInsetsCompat.Builder(insets)
                    .setInsets(safeAreas, Insets.NONE)
                    .build();
        });
        toolbar = findViewById(R.id.toolbar);
        bottomNavigationView = findViewById(R.id.bottom_nav);
        setSupportActionBar(toolbar);
        navigationController = new NavigationController(getSupportFragmentManager(), R.id.nav_host);
        getSupportFragmentManager().addOnBackStackChangedListener(this::syncChromeForCurrentFragment);
        bottomNavigationView.setOnItemSelectedListener(item -> switchTab(item.getItemId()));
        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.tab_captures);
            if (getSupportFragmentManager().findFragmentById(R.id.nav_host) == null) {
                switchTab(R.id.tab_captures);
            }
        } else {
            syncChromeForCurrentFragment();
        }
    }

    public void showDetail(long eventId) {
        DetailHostFragment.setSelectedEventId(eventId);
        navigationController.show("detail", new CaptureDetailFragment(), true);
    }

    public void showBodyViewer() {
        navigationController.show("body", new BodyViewerFragment(), true);
    }

    public void showPage(String tag, androidx.fragment.app.Fragment fragment) {
        navigationController.show(tag, fragment, true);
    }

    public void exportHar(List<CaptureUiEvent> events) {
        exportHar(HarExportRequest.from(events));
    }

    public void shareRawJson(CaptureUiEvent event) {
        harExportExecutor.execute(() -> {
            try {
                File file = RawJsonFileExporter.write(getExternalFilesDir("export"), event);
                mainHandler.post(() -> showRawJsonShareResult(file));
            } catch (IOException | IllegalArgumentException e) {
                Log.w(TAG, "Raw JSON share failed", e);
                mainHandler.post(this::showRawJsonShareFailure);
            }
        });
    }

    private void exportHar(HarExportRequest request) {
        showSnackbar(R.string.har_export_started);
        harExportWorker.export(getExternalFilesDir("export"), request.events(), new HarExportWorker.Callback() {
            @Override
            public void onSuccess(HarExportResult result) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                showHarExportResult(result);
            }

            @Override
            public void onFailure(Exception exception) {
                Log.w(TAG, "HAR export failed", exception);
                if (!isFinishing() && !isDestroyed()) {
                    showHarExportFailure(request);
                }
            }
        });
    }

    private void showHarExportFailure(HarExportRequest request) {
        if (mainRoot == null) {
            Toast.makeText(this, R.string.har_export_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Snackbar.make(mainRoot, R.string.har_export_failed, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, view -> exportHar(request))
                .show();
    }

    private void showHarExportResult(HarExportResult result) {
        Snackbar.make(
                mainRoot,
                getResources().getQuantityString(R.plurals.har_export_entries, result.getEntryCount(), result.getEntryCount()),
                Snackbar.LENGTH_LONG
        ).setAction(R.string.action_open, view -> openHar(result)).show();
    }

    private void openHar(HarExportResult result) {
        try {
            Intent open = new Intent(Intent.ACTION_VIEW);
            open.setDataAndType(harUri(result), "application/json");
            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(open);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.har_open_unavailable, Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "HAR open failed", e);
            showSnackbar(R.string.har_export_failed);
        }
    }

    private Uri harUri(HarExportResult result) {
        return FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                result.getFile()
        );
    }

    private void showRawJsonShareResult(File file) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.action_share_raw_json)));
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            Log.w(TAG, "Raw JSON share failed", e);
            showRawJsonShareFailure();
        }
    }

    private void showRawJsonShareFailure() {
        Toast.makeText(this, R.string.raw_json_share_failed, Toast.LENGTH_SHORT).show();
    }

    private void showSnackbar(int message) {
        if (mainRoot == null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return;
        }
        Snackbar.make(mainRoot, message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            NetTapUiState.stopRealtimeServer();
        }
        harExportExecutor.shutdownNow();
    }

    private boolean switchTab(int id) {
        if (id == currentTabId && getSupportFragmentManager().getBackStackEntryCount() == 0) {
            return true;
        }
        currentTabId = id;
        setMainChromeVisible(true);
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        if (id == R.id.tab_captures) {
            setTitle(R.string.nav_capture);
            navigationController.show("capture", new CaptureListFragment(), false);
            return true;
        } else if (id == R.id.tab_hooks) {
            setTitle(R.string.nav_hooks);
            navigationController.show("hooks", new HookSettingsFragment(), false);
            return true;
        } else if (id == R.id.tab_config) {
            setTitle(R.string.nav_config);
            navigationController.show("config", new ConfigFragment(), false);
            return true;
        } else if (id == R.id.tab_settings) {
            setTitle(R.string.nav_settings);
            navigationController.show("settings", new SettingsFragment(), false);
            return true;
        }
        return false;
    }

    private void syncChromeForCurrentFragment() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.nav_host);
        boolean immersive = current instanceof CaptureDetailFragment || current instanceof BodyViewerFragment;
        setMainChromeVisible(!immersive);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(!immersive
                    && getSupportFragmentManager().getBackStackEntryCount() > 0);
        }
        if (current instanceof UrlFilterFragment) {
            setTitle(R.string.nav_url_filter);
        } else if (current instanceof TlsKeylogFragment) {
            setTitle(R.string.nav_tls_keylog);
        } else if (current instanceof AboutFragment) {
            setTitle(R.string.nav_about);
        } else if (current instanceof CaptureListFragment) {
            setTitle(R.string.nav_capture);
        } else if (current instanceof HookSettingsFragment) {
            setTitle(R.string.nav_hooks);
        } else if (current instanceof ConfigFragment) {
            setTitle(R.string.nav_config);
        } else if (current instanceof SettingsFragment) {
            setTitle(R.string.nav_settings);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    private void setMainChromeVisible(boolean visible) {
        if (visible == chromeVisible) {
            return;
        }
        chromeVisible = visible;
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (toolbar != null) {
            toolbar.setVisibility(visibility);
        }
        if (bottomNavigationView != null) {
            bottomNavigationView.setVisibility(visibility);
        }
    }

    public static void applyThemeMode(String themeMode) {
        if (UiPreferences.THEME_LIGHT.equals(themeMode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            return;
        }
        if (UiPreferences.THEME_DARK.equals(themeMode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            return;
        }
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
