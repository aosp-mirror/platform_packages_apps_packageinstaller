/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.android.packageinstaller;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.VerificationParams;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Select which activity is the first visible activity of the installation and forward the intent to
 * it.
 */
public class InstallStart extends Activity {
    private static final String LOG_TAG = InstallStart.class.getSimpleName();

    private static final String SCHEME_CONTENT = "content";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String callingPackage = getCallingPackage();

        // If the activity was started via a PackageInstaller session, we retrieve the calling
        // package from that session
        int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        if (sessionId != -1) {
            PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
            callingPackage = packageInstaller.getSessionInfo(sessionId).getInstallerPackageName();
        }

        ApplicationInfo sourceInfo = getSourceInfo(callingPackage);
        int originatingUid = getOriginatingUid(sourceInfo);

        Intent nextActivity = new Intent(intent);
        nextActivity.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        Bundle extras = intent.getExtras();
        if (extras != null) {
            nextActivity.putExtras(intent.getExtras());
        }

        // The the installation source as the nextActivity thinks this activity is the source, hence
        // set the originating UID and sourceInfo explicitly
        nextActivity.putExtra(PackageInstallerActivity.EXTRA_CALLING_PACKAGE, callingPackage);
        nextActivity.putExtra(PackageInstallerActivity.EXTRA_ORIGINAL_SOURCE_INFO, sourceInfo);
        nextActivity.putExtra(Intent.EXTRA_ORIGINATING_UID, originatingUid);

        // STOPSHIP(http://b/34599122): Remove this special casing once GmsCore is updated
        if ("com.google.android.gms".equals(callingPackage)) {
            nextActivity.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
        }

        if (PackageInstaller.ACTION_CONFIRM_PERMISSIONS.equals(intent.getAction())) {
            nextActivity.setClass(this, PackageInstallerActivity.class);
        } else {
            Uri packageUri = intent.getData();

            if (packageUri == null) {
                // if there's nothing to do, quietly slip into the ether
                Intent result = new Intent();
                result.putExtra(Intent.EXTRA_INSTALL_RESULT,
                        PackageManager.INSTALL_FAILED_INVALID_URI);
                setResult(RESULT_FIRST_USER, result);

                nextActivity = null;
            } else {
                if (packageUri.getScheme().equals(SCHEME_CONTENT)) {
                    nextActivity.setClass(this, InstallStaging.class);
                } else {
                    nextActivity.setClass(this, PackageInstallerActivity.class);
                }
            }
        }

        if (nextActivity != null) {
            startActivity(nextActivity);
        }
        finish();
    }

    /**
     * @return the ApplicationInfo for the installation source (the calling package), if available
     */
    private ApplicationInfo getSourceInfo(@Nullable String callingPackage) {
        if (callingPackage != null) {
            try {
                return getPackageManager().getApplicationInfo(callingPackage, 0);
            } catch (PackageManager.NameNotFoundException ex) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Get the originating uid if possible, or VerificationParams.NO_UID if not available
     *
     * @param sourceInfo The source of this installation
     *
     * @return The UID of the installation source or VerificationParams.NO_UID
     */
    private int getOriginatingUid(@Nullable ApplicationInfo sourceInfo) {
        // The originating uid from the intent. We only trust/use this if it comes from a
        // system application
        int uidFromIntent = getIntent().getIntExtra(Intent.EXTRA_ORIGINATING_UID,
                VerificationParams.NO_UID);

        // Get the source info from the calling package, if available. This will be the
        // definitive calling package, but it only works if the intent was started using
        // startActivityForResult,
        if (sourceInfo != null) {
            if (uidFromIntent != VerificationParams.NO_UID &&
                    (sourceInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_PRIVILEGED) != 0) {
                return uidFromIntent;

            }
            // We either didn't get a uid in the intent, or we don't trust it. Use the
            // uid of the calling package instead.
            return sourceInfo.uid;
        }

        // We couldn't get the specific calling package. Let's get the uid instead
        int callingUid;
        try {
            callingUid = ActivityManager.getService()
                    .getLaunchedFromUid(getActivityToken());
        } catch (android.os.RemoteException ex) {
            Log.w(LOG_TAG, "Could not determine the launching uid.");
            // nothing else we can do
            return VerificationParams.NO_UID;
        }

        // If we got a uid from the intent, we need to verify that the caller is a
        // privileged system package before we use it
        if (uidFromIntent != VerificationParams.NO_UID) {
            String[] callingPackages = getPackageManager().getPackagesForUid(callingUid);
            if (callingPackages != null) {
                for (String packageName: callingPackages) {
                    try {
                        ApplicationInfo applicationInfo =
                                getPackageManager().getApplicationInfo(packageName, 0);

                        if ((applicationInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_PRIVILEGED)
                                != 0) {
                            return uidFromIntent;
                        }
                    } catch (PackageManager.NameNotFoundException ex) {
                        // ignore it, and try the next package
                    }
                }
            }
        }
        // We either didn't get a uid from the intent, or we don't trust it. Use the
        // calling uid instead.
        return callingUid;
    }
}
