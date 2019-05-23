/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.packageinstaller.permission.ui;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.text.BidiFormatter;
import androidx.fragment.app.FragmentActivity;

import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;

import java.util.List;

/**
 * A dialog listing the currently enabled accessibility services and their last access times.
 */
public final class ReviewAccessibilityServicesActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AccessibilityManager accessibilityManager = getSystemService(
                AccessibilityManager.class);
        List<AccessibilityServiceInfo> services = accessibilityManager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        new AlertDialog.Builder(this)
                .setView(createDialogView(services))
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton(R.string.settings, (dialog, which) -> {
                    if (services.size() == 1) {
                        startAccessibilityScreen(services.get(0).getResolveInfo().serviceInfo);
                    } else {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    }
                })
                .setOnDismissListener((dialog) -> finish())
                .show();
    }

    private @NonNull View createDialogView(List<AccessibilityServiceInfo> services) {
        AppOpsManager appOpsManager = getSystemService(AppOpsManager.class);

        LayoutInflater layoutInflater = LayoutInflater.from(this);
        View view = layoutInflater.inflate(R.layout.accessibility_service_dialog, null);

        int numServices = services.size();
        for (int i = 0; i < numServices; i++) {
            ResolveInfo resolveInfo = services.get(i).getResolveInfo();
            ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            ApplicationInfo appInfo = serviceInfo.applicationInfo;
            CharSequence label = getLabel(resolveInfo);
            long lastAccessTime = getLastAccessTime(appInfo, appOpsManager);

            if (numServices == 1) {
                // If there is only one enabled service, the dialog has its icon as a header.

                ((TextView) view.requireViewById(R.id.title)).setText(
                        getString(R.string.accessibility_service_dialog_title_single, label));
                ((TextView) view.requireViewById(R.id.bottom_text)).setText(
                        getString(R.string.accessibility_service_dialog_bottom_text_single, label));

                ImageView headerIcon = view.requireViewById(R.id.header_icon);
                headerIcon.setImageDrawable(Utils.getBadgedIcon(this, appInfo));
                headerIcon.setVisibility(View.VISIBLE);

                if (lastAccessTime != 0) {
                    TextView middleText = view.requireViewById(R.id.middle_text);
                    middleText.setText(getString(R.string.app_permission_most_recent_summary,
                            Utils.getAbsoluteTimeString(this, lastAccessTime)));
                    middleText.setVisibility(View.VISIBLE);
                }
            } else {
                // Add an entry for each enabled service.

                ((TextView) view.requireViewById(R.id.title)).setText(
                        getString(R.string.accessibility_service_dialog_title_multiple,
                                services.size()));
                ((TextView) view.requireViewById(R.id.bottom_text)).setText(
                        getString(R.string.accessibility_service_dialog_bottom_text_multiple));

                ViewGroup servicesListView = view.requireViewById(R.id.items_container);
                View itemView = layoutInflater.inflate(R.layout.accessibility_service_dialog_item,
                        servicesListView, false);

                ((TextView) itemView.requireViewById(R.id.title)).setText(label);
                ((ImageView) itemView.requireViewById(R.id.icon)).setImageDrawable(
                        Utils.getBadgedIcon(this, appInfo));

                if (lastAccessTime == 0) {
                    itemView.requireViewById(R.id.summary).setVisibility(View.GONE);
                } else {
                    ((TextView) itemView.requireViewById(R.id.summary)).setText(
                            getString(R.string.app_permission_most_recent_summary,
                                    Utils.getAbsoluteTimeString(this, lastAccessTime)));
                }

                itemView.setOnClickListener((v) -> startAccessibilityScreen(serviceInfo));

                servicesListView.addView(itemView);
            }
        }

        return view;
    }

    private void startAccessibilityScreen(ServiceInfo serviceInfo) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS);
        intent.putExtra(Intent.EXTRA_COMPONENT_NAME,
                new ComponentName(serviceInfo.packageName, serviceInfo.name).flattenToString());
        startActivity(intent);
    }

    private @NonNull CharSequence getLabel(@NonNull ResolveInfo resolveInfo) {
        return BidiFormatter.getInstance().unicodeWrap(
                TextUtils.makeSafeForPresentation(
                        resolveInfo.loadLabel(getPackageManager()).toString(), 0, 0,
                        TextUtils.SAFE_STRING_FLAG_TRIM | TextUtils.SAFE_STRING_FLAG_FIRST_LINE));
    }

    private static long getLastAccessTime(@NonNull ApplicationInfo appInfo,
            @NonNull AppOpsManager appOpsManager) {
        List<AppOpsManager.PackageOps> ops = appOpsManager.getOpsForPackage(appInfo.uid,
                appInfo.packageName, AppOpsManager.OPSTR_ACCESS_ACCESSIBILITY);
        long lastAccessTime = 0;
        int numPkgOps = ops.size();
        for (int pkgOpNum = 0; pkgOpNum < numPkgOps; pkgOpNum++) {
            AppOpsManager.PackageOps pkgOp = ops.get(pkgOpNum);
            int numOps = pkgOp.getOps().size();
            for (int opNum = 0; opNum < numOps; opNum++) {
                AppOpsManager.OpEntry op = pkgOp.getOps().get(opNum);
                lastAccessTime = Math.max(lastAccessTime,
                        op.getLastAccessTime(AppOpsManager.OP_FLAGS_ALL_TRUSTED));
            }
        }
        return lastAccessTime;
    }
}
