package xyz.winhok.nettap.ui.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import xyz.winhok.nettap.R;
import xyz.winhok.nettap.ui.Dimens;
import xyz.winhok.nettap.ui.MainActivity;
import xyz.winhok.nettap.ui.NetTapUiState;
import xyz.winhok.nettap.ui.adapter.CaptureDomainsAdapter;
import xyz.winhok.nettap.ui.data.CaptureUiEvent;
import xyz.winhok.nettap.ui.data.CaptureSessionStore;
import xyz.winhok.nettap.ui.data.DomainBucket;
import xyz.winhok.nettap.ui.widget.SpacedRecyclerView;

public final class CaptureDomainsFragment extends Fragment implements CapturePage {
    private static final int MENU_CLEAR_BUCKET = 1;
    private static final int MENU_EXPORT_HOST_HAR = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView summary;
    private CaptureDomainsAdapter adapter;
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

        adapter = new CaptureDomainsAdapter(new CaptureDomainsAdapter.Callbacks() {
            @Override
            public void onOpen(DomainBucket bucket) {
                NetTapUiState.setHostFilter(bucket.getHost());
                CaptureListFragment parent = (CaptureListFragment) getParentFragment();
                if (parent != null) {
                    parent.showSequenceTab();
                }
            }

            @Override
            public void onShowActions(DomainBucket bucket, View anchor) {
                showDomainActions(bucket, anchor);
            }
        });
        SpacedRecyclerView list = new SpacedRecyclerView(requireContext());
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
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
        if (adapter == null || summary == null) {
            return;
        }
        CaptureListFragment parent = (CaptureListFragment) getParentFragment();
        String query = parent == null ? "" : parent.currentQuery();
        List<DomainBucket> buckets = CaptureSessionStore.domainBucketsFor(
                NetTapUiState.filteredEvents(query, false));
        summary.setText(getString(xyz.winhok.nettap.R.string.domains_summary, buckets.size()));
        adapter.submit(buckets);
    }

    private void showDomainActions(DomainBucket bucket, View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, MENU_CLEAR_BUCKET, 0, R.string.action_clear_bucket);
        menu.getMenu().add(0, MENU_EXPORT_HOST_HAR, 1, R.string.action_export_host_har);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_CLEAR_BUCKET) {
                NetTapUiState.store().clearHostBucket(bucket.getHost());
                if (bucket.getHost().equals(NetTapUiState.getHostFilter())) {
                    NetTapUiState.setHostFilter(null);
                }
                refresh();
                return true;
            }
            if (item.getItemId() == MENU_EXPORT_HOST_HAR) {
                List<CaptureUiEvent> events = NetTapUiState.store().eventsForHost(bucket.getHost());
                ((MainActivity) requireActivity()).exportHar(events);
                return true;
            }
            return false;
        });
        menu.show();
    }
}
