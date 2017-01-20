/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.packageinstaller;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.text.TextUtils;
import android.util.Log;

import com.android.packageinstaller.handheld.ErrorDialogFragment;
import com.android.packageinstaller.television.ErrorFragment;

/**
 * This activity can be called to prompt the user to verify that they want to clear the data for
 * the provided package. We expect an intent with URI of the form package://<packageName>.
 */
public class ClearApplicationUserDataDialogActivity extends Activity {
    private static final String TAG = "ClearAppUserDataDialog";

    private String mPackageName;
    private IPackageManager mPackageManager;
    private int mUserId;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        // Get intent information.
        // We expect an intent with URI of the form package://<packageName>.
        final Intent intent = getIntent();
        final Uri packageUri = intent.getData();
        if (packageUri == null) {
            Log.e(TAG, "No package URI in intent");
            showAppNotFound();
            return;
        }

        mPackageName = packageUri.getEncodedSchemeSpecificPart();
        if (TextUtils.isEmpty(mPackageName)) {
            Log.e(TAG, "Invalid package name in URI: " + packageUri);
            showAppNotFound();
            return;
        }

        mPackageManager = IPackageManager.Stub.asInterface(
                ServiceManager.getService("package"));
        UserHandle user = android.os.Process.myUserHandle();
        ApplicationInfo appInfo = null;

        try {
            mUserId = user.getIdentifier();
            appInfo = mPackageManager.getApplicationInfo(mPackageName,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES, mUserId);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to get packageName. Package manager is dead?");
        }

        if (appInfo == null) {
            Log.e(TAG, "Invalid packageName: " + mPackageName);
            showAppNotFound();
            return;
        }

        showConfirmationDialog();
    }

    private void showConfirmationDialog() {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String message = getString(R.string.clear_app_data_confirm_dialog, mPackageName);
        int okId = com.android.internal.R.string.ok;
        int cancelId = com.android.internal.R.string.cancel;
        builder.setMessage(message)
                .setPositiveButton(okId, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        new Handler().post(new Runnable() {
                            public void run() {
                                try {
                                    mPackageManager.clearApplicationUserData(
                                            mPackageName, null /* observer */, mUserId);
                                } catch (RemoteException e) {
                                    Log.e(TAG,
                                            "Unable to clear user data. Package manager is dead?");
                                }
                            }
                        });
                        finish();
                    }
                })
                .setNegativeButton(cancelId, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                });
        // Create the AlertDialog object and return it
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showAppNotFound() {
        if (isTv()) {
            showContentFragment(new ErrorFragment(), R.string.app_not_found_dlg_title,
                    R.string.app_not_found_dlg_text);
        } else {
            showDialogFragment(new ErrorDialogFragment(), R.string.app_not_found_dlg_title,
                    R.string.app_not_found_dlg_text);
        }
    }

    private boolean isTv() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK)
                == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private void showContentFragment(@NonNull Fragment fragment, @StringRes int title,
            @StringRes int text) {
        Bundle args = new Bundle();
        args.putInt(ErrorFragment.TITLE, title);
        args.putInt(ErrorFragment.TEXT, text);
        fragment.setArguments(args);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit();
    }

    private void showDialogFragment(@NonNull DialogFragment fragment,
            @StringRes int title, @StringRes int text) {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }

        Bundle args = new Bundle();
        if (title != 0) {
            args.putInt(ErrorDialogFragment.TITLE, title);
        }
        args.putInt(ErrorDialogFragment.TEXT, text);

        fragment.setArguments(args);
        fragment.show(ft, "dialog");
    }
}
