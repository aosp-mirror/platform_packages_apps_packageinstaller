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

package com.android.packageinstaller.permission.ui.wear.settings;

import android.content.Context;
import android.support.wearable.view.CircledImageView;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.packageinstaller.R;

import java.util.ArrayList;

/**
 * Common adapter for settings views. Maintains a list of 'Settings', consisting of a name,
 * icon and optional activity-specific data.
 */
public class SettingsAdapter<T> extends WearableListView.Adapter {
    private static final String TAG = "SettingsAdapter";
    private final Context mContext;

    public static final class Setting<S> {
        public static final int ID_INVALID = -1;

        public final int id;
        public int nameResourceId;
        public CharSequence name;
        public int iconResource;
        public boolean inProgress;
        public S data;

        public Setting(CharSequence name, int iconResource, S data) {
            this(name, iconResource, data, ID_INVALID);
        }

        public Setting(CharSequence name, int iconResource, S data, int id) {
            this.name = name;
            this.iconResource = iconResource;
            this.data = data;
            this.inProgress = false;
            this.id = id;
        }

        public Setting(int nameResource, int iconResource, S data, int id) {
            this.nameResourceId = nameResource;
            this.iconResource = iconResource;
            this.data = data;
            this.inProgress = false;
            this.id = id;
        }

        public Setting(int nameResource, int iconResource, int id) {
            this.nameResourceId = nameResource;
            this.iconResource = iconResource;
            this.data = null;
            this.inProgress = false;
            this.id = id;
        }

        public Setting(CharSequence name, int iconResource, int id) {
            this(name, iconResource, null, id);
        }

    }

    private final int mItemLayoutId;
    private final float mDefaultCircleRadiusPercent;
    private final float mSelectedCircleRadiusPercent;

    protected ArrayList<Setting<T>> mSettings = new ArrayList<Setting<T>>();

    public SettingsAdapter(Context context, int itemLayoutId) {
        mContext = context;
        mItemLayoutId = itemLayoutId;
        mDefaultCircleRadiusPercent = context.getResources().getFraction(
                R.dimen.default_settings_circle_radius_percent, 1, 1);
        mSelectedCircleRadiusPercent = context.getResources().getFraction(
                R.dimen.selected_settings_circle_radius_percent, 1, 1);
    }

    @Override
    public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new SettingsItemHolder(new SettingsItem(parent.getContext()));
    }

    @Override
    public void onBindViewHolder(WearableListView.ViewHolder holder, int position) {
        Setting<T> setting = mSettings.get(position);
        if (setting.iconResource == -1) {
            ((SettingsItemHolder) holder).imageView.setVisibility(View.GONE);
        } else {
            ((SettingsItemHolder) holder).imageView.setVisibility(View.VISIBLE);
            ((SettingsItemHolder) holder).imageView.setImageResource(
                    mSettings.get(position).iconResource);
        }
        Log.d(TAG, "onBindViewHolder " + setting.name + " " + setting.id + " " + setting
                .nameResourceId);
        if (setting.name == null && setting.nameResourceId != 0) {
            setting.name = mContext.getString(setting.nameResourceId);
        }
        ((SettingsItemHolder) holder).textView.setText(setting.name);
    }

    @Override
    public int getItemCount() {
        return mSettings.size();
    }

    public void addSetting(CharSequence name, int iconResource) {
        addSetting(name, iconResource, null);
    }

    public void addSetting(CharSequence name, int iconResource, T intent) {
        addSetting(mSettings.size(), name, iconResource, intent);
    }

    public void addSetting(int index, CharSequence name, int iconResource, T intent) {
        addSetting(Setting.ID_INVALID, index, name, iconResource, intent);
    }

    public void addSetting(int id, int index, CharSequence name, int iconResource, T intent) {
        mSettings.add(index, new Setting<T>(name, iconResource, intent, id));
        notifyItemInserted(index);
    }

    public void addSettingDontNotify(Setting<T> setting) {
        mSettings.add(setting);
    }

    public void addSetting(Setting<T> setting) {
        mSettings.add(setting);
        notifyItemInserted(mSettings.size() - 1);
    }

    public void addSetting(int index, Setting<T> setting) {
        mSettings.add(index, setting);
        notifyItemInserted(index);
    }

    /**
     * Returns the index of the setting in the adapter based on the ID supplied when it was
     * originally added.
     * @param id the setting's id
     * @return index in the adapter of the setting. -1 if not found.
     */
    public int findSetting(int id) {
        for (int i = mSettings.size() - 1; i >= 0; --i) {
            Setting setting = mSettings.get(i);

            if (setting.id == id) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Removes a setting at the given index.
     * @param index the index of the setting to be removed
     */
    public void removeSetting(int index) {
        mSettings.remove(index);
        notifyDataSetChanged();
    }

    public void clearSettings() {
        mSettings.clear();
        notifyDataSetChanged();
    }

    /**
     * Updates a setting in place.
     * @param index the index of the setting
     * @param name the updated setting name
     * @param iconResource the update setting icon
     * @param intent the updated intent for the setting
     */
    public void updateSetting(int index, CharSequence name, int iconResource, T intent) {
        Setting<T> setting = mSettings.get(index);
        setting.iconResource = iconResource;
        setting.name = name;
        setting.data = intent;
        notifyItemChanged(index);
    }

    public Setting<T> get(int position) {
        return mSettings.get(position);
    }

    protected static class SettingsItemHolder extends ExtendedViewHolder {
        public final CircledImageView imageView;
        public final TextView textView;

        public SettingsItemHolder(View itemView) {
            super(itemView);

            imageView = ((CircledImageView) itemView.findViewById(R.id.image));
            textView = ((TextView) itemView.findViewById(R.id.text));
        }
    }

    protected class SettingsItem extends FrameLayout implements ExtendedOnCenterProximityListener {

        protected final CircledImageView mImage;
        protected final TextView mText;

        public SettingsItem(Context context) {
            super(context);
            View view = View.inflate(context, mItemLayoutId, null);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            params.gravity = Gravity.CENTER_VERTICAL;
            addView(view, params);
            mImage = (CircledImageView) findViewById(R.id.image);
            mText = (TextView) findViewById(R.id.text);
        }

        @Override
        public float getProximityMinValue() {
            return mDefaultCircleRadiusPercent;
        }

        @Override
        public float getProximityMaxValue() {
            return mSelectedCircleRadiusPercent;
        }

        @Override
        public float getCurrentProximityValue() {
            return mImage.getCircleRadiusPressedPercent();
        }

        @Override
        public void setScalingAnimatorValue(float value) {
            mImage.setCircleRadiusPercent(value);
            mImage.setCircleRadiusPressedPercent(value);
        }

        @Override
        public void onCenterPosition(boolean animate) {
            mImage.setAlpha(1f);
            mText.setAlpha(1f);
        }

        @Override
        public void onNonCenterPosition(boolean animate) {
            mImage.setAlpha(0.5f);
            mText.setAlpha(0.5f);
        }

        TextView getTextView() {
            return mText;
        }
    }
}
