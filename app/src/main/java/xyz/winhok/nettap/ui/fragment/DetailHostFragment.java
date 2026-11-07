package xyz.winhok.nettap.ui.fragment;

import androidx.fragment.app.Fragment;

public final class DetailHostFragment {
    private static Long selectedEventId;

    private DetailHostFragment() {
    }

    public static void setSelectedEventId(Long eventId) {
        selectedEventId = eventId;
    }

    public static Long selectedEventId() {
        return selectedEventId;
    }

    static Fragment[] detailTabs() {
        return new Fragment[]{
                new DetailGeneralFragment(),
                new DetailRequestFragment(),
                new DetailResponseFragment(),
                new DetailCookiesFragment()
        };
    }
}
