/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.packageinstaller.permission.ui.handheld;

import static android.provider.Settings.ACTION_APP_SEARCH_SETTINGS;
import static android.view.MenuItem.SHOW_AS_ACTION_ALWAYS;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.permissioncontroller.R;

public abstract class PermissionsFrameFragment extends PreferenceFragmentCompat {
    private static final String LOG_TAG = PermissionsFrameFragment.class.getSimpleName();

    private static final int MENU_SEARCH_SETTINGS = Menu.FIRST;
    static final int MENU_ALL_PERMS = Menu.FIRST + 1;
    static final int MENU_SHOW_SYSTEM = Menu.FIRST + 2;
    static final int MENU_HIDE_SYSTEM = Menu.FIRST + 3;

    private ViewGroup mPreferencesContainer;

    private TextView mEmptyView;
    private View mLoadingView;
    private ViewGroup mPrefsView;
    private boolean mIsLoading;

    /**
     * Returns the view group that holds the preferences objects. This will
     * only be set after {@link #onCreateView} has been called.
     */
    protected final ViewGroup getPreferencesContainer() {
        return mPreferencesContainer;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        if (getContext().getPackageManager().resolveActivity(new Intent(ACTION_APP_SEARCH_SETTINGS),
                0) != null) {
            MenuItem searchItem = menu.add(Menu.NONE, MENU_SEARCH_SETTINGS, Menu.NONE,
                    R.string.search_menu);
            searchItem.setIcon(R.drawable.ic_search_24dp);
            searchItem.setShowAsAction(SHOW_AS_ACTION_ALWAYS);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SEARCH_SETTINGS:
                try {
                    getActivity().startActivity(new Intent(ACTION_APP_SEARCH_SETTINGS));
                } catch (ActivityNotFoundException e) {
                    Log.e(LOG_TAG, "Cannot search settings", e);
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.permissions_frame, container,
                        false);
        mPrefsView = (ViewGroup) rootView.findViewById(R.id.prefs_container);
        if (mPrefsView == null) {
            mPrefsView = rootView;
        }
        mEmptyView = mPrefsView.findViewById(R.id.no_permissions);
        mEmptyView.setText(getEmptyViewString());
        mLoadingView = rootView.findViewById(R.id.loading_container);
        mPreferencesContainer = (ViewGroup) super.onCreateView(
                inflater, mPrefsView, savedInstanceState);
        setLoading(mIsLoading, false, true /* force */);
        mPrefsView.addView(mPreferencesContainer);
        return rootView;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        // empty
    }

    protected void setLoading(boolean loading, boolean animate) {
        setLoading(loading, animate, false);
    }

    private void setLoading(boolean loading, boolean animate, boolean force) {
        if (mIsLoading != loading || force) {
            mIsLoading = loading;
            if (getView() == null) {
                // If there is no created view, there is no reason to animate.
                animate = false;
            }
            if (mPrefsView != null) {
                setViewShown(mPrefsView, !loading, animate);
            }
            if (mLoadingView != null) {
                setViewShown(mLoadingView, loading, animate);
            }
        }
    }

    /**
     * Either show the empty view or the recycler view. To be called any time the adapter changes.
     */
    void updateEmptyState() {
        RecyclerView prefs = getListView();

        // This might be called before onCreateView, hence emptyView and prefs can be null
        if (mEmptyView != null && prefs != null) {
            if (prefs.getAdapter() != null && prefs.getAdapter().getItemCount() != 0) {
                mEmptyView.setVisibility(View.GONE);
                prefs.setVisibility(View.VISIBLE);
            } else {
                mEmptyView.setVisibility(View.VISIBLE);
                prefs.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onBindPreferences() {
        super.onBindPreferences();

        RecyclerView.Adapter adapter = getListView().getAdapter();

        if (adapter != null) {
            adapter.registerAdapterDataObserver(
                    new RecyclerView.AdapterDataObserver() {
                        @Override
                        public void onChanged() {
                            updateEmptyState();
                        }

                        @Override
                        public void onItemRangeInserted(int positionStart, int itemCount) {
                            updateEmptyState();
                        }

                        @Override
                        public void onItemRangeRemoved(int positionStart, int itemCount) {
                            updateEmptyState();
                        }
                    });
        }

        updateEmptyState();
    }

    private void setViewShown(final View view, boolean shown, boolean animate) {
        if (animate) {
            Animation animation = AnimationUtils.loadAnimation(getContext(),
                    shown ? android.R.anim.fade_in : android.R.anim.fade_out);
            if (shown) {
                view.setVisibility(View.VISIBLE);
            } else {
                animation.setAnimationListener(new AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        view.setVisibility(View.INVISIBLE);
                    }
                });
            }
            view.startAnimation(animation);
        } else {
            view.clearAnimation();
            view.setVisibility(shown ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /**
     * @return the id of the string to display when there are no entries to show.
     */
    public int getEmptyViewString() {
        return R.string.no_permissions;
    }
}
