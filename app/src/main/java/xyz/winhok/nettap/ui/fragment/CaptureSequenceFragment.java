package xyz.winhok.nettap.ui.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.CaptureFilterSummaryFormatter;
import xyz.winhok.nettap.ui.CaptureSessionSummaryFormatter;
import xyz.winhok.nettap.ui.Dimens;
import xyz.winhok.nettap.ui.MainActivity;
import xyz.winhok.nettap.ui.NetTapUiState;
import xyz.winhok.nettap.ui.SequenceEmptyState;
import xyz.winhok.nettap.ui.adapter.CaptureSequenceAdapter;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;
import xyz.winhok.nettap.ui.widget.FadeSpinnerView;
import xyz.winhok.nettap.ui.widget.SpacedRecyclerView;

import com.google.android.material.button.MaterialButton;

public final class CaptureSequenceFragment extends Fragment implements CapturePage {
    private static final int MENU_FILTER_HOOK = 1;
    private static final int MENU_FILTER_PACKAGE = 2;
    private static final int MENU_COPY_URL = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView summary;
    private MaterialButton clearFilters;
    private SpacedRecyclerView list;
    private LinearLayout emptyState;
    private FadeSpinnerView emptySpinner;
    private TextView emptyText;
    private CaptureSequenceAdapter adapter;
    private final Runnable refreshLoop = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000L);
        }
    };

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        summary = new TextView(requireContext());
        summary.setTextSize(13);
        Dimens.setPaddingDp(summary, 12, 8);
        root.addView(summary);

        clearFilters = new MaterialButton(requireContext());
        clearFilters.setText(R.string.action_clear_filters);
        clearFilters.setAllCaps(false);
        clearFilters.setVisibility(View.GONE);
        clearFilters.setOnClickListener(view -> {
            NetTapUiState.setHostFilter(null);
            NetTapUiState.setHookFilter(null);
            NetTapUiState.setPackageFilter(null);
            CaptureListFragment parent = (CaptureListFragment) getParentFragment();
            if (parent != null) {
                parent.clearQuery();
            }
            refresh();
        });
        root.addView(clearFilters);

        adapter = new CaptureSequenceAdapter(new CaptureSequenceAdapter.Callbacks() {
            @Override
            public boolean isRead(CaptureUiEvent event) {
                return NetTapUiState.store().isRead(event.getSessionId());
            }

            @Override
            public void onOpen(CaptureUiEvent event) {
                CaptureListFragment parent = (CaptureListFragment) getParentFragment();
                NetTapUiState.setDetailQuery(parent == null ? "" : parent.currentQuery());
                NetTapUiState.store().markRead(event.getSessionId());
                ((MainActivity) requireActivity()).showDetail(event.getSessionId());
            }

            @Override
            public void onShowActions(CaptureUiEvent event, View anchor) {
                showRowActions(event, anchor);
            }
        });
        FrameLayout body = new FrameLayout(requireContext());
        list = new SpacedRecyclerView(requireContext());
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        body.addView(list, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        emptyState = new LinearLayout(requireContext());
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setOrientation(LinearLayout.VERTICAL);
        emptyState.setVisibility(View.GONE);
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_empty_box);
        emptyState.addView(icon, new LinearLayout.LayoutParams(
                Dimens.dp(requireContext(), 64),
                Dimens.dp(requireContext(), 64)
        ));
        emptySpinner = new FadeSpinnerView(requireContext());
        int spinnerSize = Dimens.dp(requireContext(), 48);
        emptyState.addView(emptySpinner, new LinearLayout.LayoutParams(spinnerSize, spinnerSize));
        emptyText = new TextView(requireContext());
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setTextSize(16);
        emptyState.addView(emptyText);
        body.addView(emptyState, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        root.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.post(refreshLoop);
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(refreshLoop);
        super.onPause();
    }

    @Override
    public void refresh() {
        if (adapter == null || summary == null || clearFilters == null || list == null) {
            return;
        }
        CaptureListFragment parent = (CaptureListFragment) getParentFragment();
        String query = parent == null ? "" : parent.currentQuery();
        boolean newestFirst = parent == null || parent.isNewestFirst();
        List<CaptureUiEvent> events = NetTapUiState.filteredEvents(query, newestFirst);
        if (parent != null) {
            parent.refreshSessionStatus();
        }
        updateSummary(CaptureSessionSummaryFormatter.format(events.size(), NetTapUiState.store()), query);
        adapter.submit(events, query);
        updateEmptyState(SequenceEmptyState.from(
                events.size(),
                NetTapUiState.store().totalCount(),
                NetTapUiState.store().getState()
        ));
    }

    private void updateSummary(String text, String query) {
        String host = NetTapUiState.getHostFilter();
        String hook = NetTapUiState.getHookFilter();
        String packageName = NetTapUiState.getPackageFilter();
        summary.setText(CaptureFilterSummaryFormatter.format(text, host, hook, packageName, query));
        clearFilters.setVisibility(
                host == null && hook == null && packageName == null && isBlank(query)
                        ? View.GONE
                        : View.VISIBLE
        );
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private void updateEmptyState(SequenceEmptyState state) {
        if (emptyState == null || emptyText == null || emptySpinner == null || list == null) {
            return;
        }
        emptyState.setVisibility(state.isVisible() ? View.VISIBLE : View.GONE);
        list.setVisibility(state.isVisible() ? View.GONE : View.VISIBLE);
        if (state.isVisible()) {
            emptyText.setText(state.messageResId());
        }
        emptySpinner.setVisibility(state.isSpinnerVisible() ? View.VISIBLE : View.GONE);
    }

    private void showRowActions(CaptureUiEvent event, View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, MENU_FILTER_HOOK, 0, R.string.action_filter_by_hook);
        menu.getMenu().add(0, MENU_FILTER_PACKAGE, 1, R.string.action_filter_by_package);
        menu.getMenu().add(0, MENU_COPY_URL, 2, R.string.action_copy_url);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_FILTER_HOOK) {
                NetTapUiState.setHookFilter(event.getHook());
                refresh();
                return true;
            }
            if (item.getItemId() == MENU_FILTER_PACKAGE) {
                NetTapUiState.setPackageFilter(event.getPackageName());
                refresh();
                return true;
            }
            copyUrl(event);
            return true;
        });
        menu.show();
    }

    private void copyUrl(CaptureUiEvent event) {
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    getString(R.string.action_copy_url),
                    event.getUrl()
            ));
            Toast.makeText(requireContext(), R.string.clipboard_copied, Toast.LENGTH_SHORT).show();
        }
    }
}
