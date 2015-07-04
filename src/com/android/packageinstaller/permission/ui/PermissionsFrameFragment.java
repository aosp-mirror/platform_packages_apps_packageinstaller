package com.android.packageinstaller.permission.ui;

import android.annotation.Nullable;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.AdapterDataObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.packageinstaller.R;

public abstract class PermissionsFrameFragment extends PreferenceFragment {

    private static final float WINDOW_ALIGNMENT_OFFSET_PERCENT = 50;

    private ViewGroup mPreferencesContainer;

    // TV-specific instance variables
    @Nullable private VerticalGridView mGridView;

    /**
     * Returns the view group that holds the preferences objects. This will
     * only be set after {@link #onCreateView} has been called.
     */
    protected final ViewGroup getPreferencesContainer() {
        return mPreferencesContainer;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.permissions_frame, container,
                        false);
        ViewGroup prefsContainer = (ViewGroup) rootView.findViewById(R.id.prefs_container);
        if (prefsContainer == null) {
            prefsContainer = rootView;
        }
        mPreferencesContainer = (ViewGroup) super.onCreateView(
                inflater, prefsContainer, savedInstanceState);
        prefsContainer.addView(mPreferencesContainer);
        return rootView;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
        PreferenceScreen preferences = getPreferenceScreen();
        if (preferences == null) {
            preferences = getPreferenceManager().createPreferenceScreen(getActivity());
            setPreferenceScreen(preferences);
        }
    }

    @Override
    public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
            Bundle savedInstanceState) {
        int uiMode = getResources().getConfiguration().uiMode;
        if ((uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION) {
            mGridView = (VerticalGridView) inflater.inflate(
                    R.layout.leanback_preferences_list, parent, false);
            mGridView.setWindowAlignmentOffset(0);
            mGridView.setWindowAlignmentOffsetPercent(WINDOW_ALIGNMENT_OFFSET_PERCENT);
            mGridView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
            mGridView.setFocusScrollStrategy(VerticalGridView.FOCUS_SCROLL_ALIGNED);
            return mGridView;
        } else {
            return super.onCreateRecyclerView(inflater, parent, savedInstanceState);
        }
    }

    @Override
    protected RecyclerView.Adapter<?> onCreateAdapter(PreferenceScreen preferenceScreen) {
        final RecyclerView.Adapter<?> adapter = super.onCreateAdapter(preferenceScreen);

        if (adapter != null) {
            final TextView emptyView = (TextView) getView().findViewById(R.id.no_permissions);
            onSetEmptyText(emptyView);
            final RecyclerView recyclerView = getListView();
            adapter.registerAdapterDataObserver(new AdapterDataObserver() {
                @Override
                public void onChanged() {
                    checkEmpty();
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    checkEmpty();
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    checkEmpty();
                }

                private void checkEmpty() {
                    boolean isEmpty = adapter.getItemCount() == 0;
                    emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                    recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                    if (!isEmpty && mGridView != null) {
                        mGridView.requestFocus();
                    }
                }
            });

            boolean isEmpty = adapter.getItemCount() == 0;
            emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            if (!isEmpty && mGridView != null) {
                mGridView.requestFocus();
            }
        }

        return adapter;
    }

    /**
     * Hook for subclasses to change the default text of the empty view.
     * Base implementation leaves the default empty view text.
     *
     * @param textView the empty text view
     */
    protected void onSetEmptyText(TextView textView) {
    }
}

