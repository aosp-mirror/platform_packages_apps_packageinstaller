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
 * limitations under the License
 */

package com.android.packageinstaller.wear;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import com.android.packageinstaller.DeviceUtils;
import com.android.packageinstaller.PackageUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service that will install/uninstall packages. It will check for permissions and features as well.
 *
 * -----------
 *
 * Debugging information:
 *
 *  Install Action example:
 *  adb shell am startservice -a com.android.packageinstaller.wear.INSTALL_PACKAGE \
 *     -d package://com.google.android.gms \
 *     --eu com.google.android.clockwork.EXTRA_ASSET_URI content://com.google.android.clockwork.home.provider/host/com.google.android.wearable.app/wearable/com.google.android.gms/apk \
 *     --es android.intent.extra.INSTALLER_PACKAGE_NAME com.google.android.gms \
 *     --ez com.google.android.clockwork.EXTRA_CHECK_PERMS false \
 *     --eu com.google.android.clockwork.EXTRA_PERM_URI content://com.google.android.clockwork.home.provider/host/com.google.android.wearable.app/permissions \
 *     com.android.packageinstaller/com.android.packageinstaller.wear.WearPackageInstallerService
 *
 *  Uninstall Action example:
 *  adb shell am startservice -a com.android.packageinstaller.wear.UNINSTALL_PACKAGE \
 *     -d package://com.google.android.gms \
 *     com.android.packageinstaller/com.android.packageinstaller.wear.WearPackageInstallerService
 *
 *  Retry GMS:
 *  adb shell am startservice -a com.android.packageinstaller.wear.RETRY_GMS \
 *     com.android.packageinstaller/com.android.packageinstaller.wear.WearPackageInstallerService
 */
public class WearPackageInstallerService extends Service {
    private static final String TAG = "WearPkgInstallerService";

    private static final String KEY_PACKAGE_NAME =
            "com.google.android.clockwork.EXTRA_PACKAGE_NAME";
    private static final String KEY_APP_LABEL = "com.google.android.clockwork.EXTRA_APP_LABEL";
    private static final String KEY_APP_ICON_URI =
            "com.google.android.clockwork.EXTRA_APP_ICON_URI";
    private static final String KEY_PERMS_LIST = "com.google.android.clockwork.EXTRA_PERMS_LIST";
    private static final String KEY_HAS_LAUNCHER =
            "com.google.android.clockwork.EXTRA_HAS_LAUNCHER";

    private static final String HOME_APP_PACKAGE_NAME = "com.google.android.wearable.app";
    private static final String SHOW_PERMS_SERVICE_CLASS =
            "com.google.android.clockwork.packagemanager.ShowPermsService";

    private final int START_INSTALL = 1;
    private final int START_UNINSTALL = 2;

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case START_INSTALL:
                    installPackage(msg.getData());
                    break;
                case START_UNINSTALL:
                    uninstallPackage(msg.getData());
                    break;
            }
        }
    }
    private ServiceHandler mServiceHandler;

    private static volatile PowerManager.WakeLock lockStatic = null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread thread = new HandlerThread("PackageInstallerThread",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mServiceHandler = new ServiceHandler(thread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!DeviceUtils.isWear(this)) {
            Log.w(TAG, "Not running on wearable.");
            return START_NOT_STICKY;
        }

        if (intent == null) {
            Log.w(TAG, "Got null intent.");
            return START_NOT_STICKY;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Got install/uninstall request " + intent);
        }

        Uri packageUri = intent.getData();
        if (packageUri == null) {
            Log.e(TAG, "No package URI in intent");
            return START_NOT_STICKY;
        }
        final String packageName = WearPackageUtil.getSanitizedPackageName(packageUri);
        if (packageName == null) {
            Log.e(TAG, "Invalid package name in URI (expected package:<pkgName>): " + packageUri);
            return START_NOT_STICKY;
        }

        PowerManager.WakeLock lock = getLock(this.getApplicationContext());
        if (!lock.isHeld()) {
            lock.acquire();
        }

        Bundle intentBundle = intent.getExtras();
        if (intentBundle == null) {
            intentBundle = new Bundle();
        }
        WearPackageArgs.setStartId(intentBundle, startId);
        WearPackageArgs.setPackageName(intentBundle, packageName);
        if (Intent.ACTION_INSTALL_PACKAGE.equals(intent.getAction())) {
            Message msg = mServiceHandler.obtainMessage(START_INSTALL);
            msg.setData(intentBundle);
            mServiceHandler.sendMessage(msg);
        } else if (Intent.ACTION_UNINSTALL_PACKAGE.equals(intent.getAction())) {
            Message msg = mServiceHandler.obtainMessage(START_UNINSTALL);
            msg.setData(intentBundle);
            mServiceHandler.sendMessage(msg);
        }
        return START_NOT_STICKY;
    }

    private void installPackage(Bundle argsBundle) {
        int startId = WearPackageArgs.getStartId(argsBundle);
        final String packageName = WearPackageArgs.getPackageName(argsBundle);
        final Uri assetUri = WearPackageArgs.getAssetUri(argsBundle);
        final Uri permUri = WearPackageArgs.getPermUri(argsBundle);
        boolean checkPerms = WearPackageArgs.checkPerms(argsBundle);
        boolean skipIfSameVersion = WearPackageArgs.skipIfSameVersion(argsBundle);
        int companionSdkVersion = WearPackageArgs.getCompanionSdkVersion(argsBundle);
        int companionDeviceVersion = WearPackageArgs.getCompanionDeviceVersion(argsBundle);
        String compressionAlg = WearPackageArgs.getCompressionAlg(argsBundle);
        boolean skipIfLowerVersion = WearPackageArgs.skipIfLowerVersion(argsBundle);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Installing package: " + packageName + ", assetUri: " + assetUri +
                    ",permUri: " + permUri + ", startId: " + startId + ", checkPerms: " +
                    checkPerms + ", skipIfSameVersion: " + skipIfSameVersion +
                    ", compressionAlg: " + compressionAlg + ", companionSdkVersion: " +
                    companionSdkVersion + ", companionDeviceVersion: " + companionDeviceVersion +
                    ", skipIfLowerVersion: " + skipIfLowerVersion);
        }
        final PackageManager pm = getPackageManager();
        File tempFile = null;
        int installFlags = 0;
        PowerManager.WakeLock lock = getLock(this.getApplicationContext());
        boolean messageSent = false;
        try {
            PackageInfo existingPkgInfo = null;
            try {
                existingPkgInfo = pm.getPackageInfo(packageName,
                        PackageManager.GET_UNINSTALLED_PACKAGES | PackageManager.GET_PERMISSIONS);
                if(existingPkgInfo != null) {
                    installFlags |= PackageManager.INSTALL_REPLACE_EXISTING;
                }
            } catch (PackageManager.NameNotFoundException e) {
                // Ignore this exception. We could not find the package, will treat as a new
                // installation.
            }
            if((installFlags & PackageManager.INSTALL_REPLACE_EXISTING )!= 0) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Replacing package:" + packageName);
                }
            }
            // TODO(28021618): This was left as a temp file due to the fact that this code is being
            //       deprecated and that we need the bare minimum to continue working moving forward
            //       If this code is used as reference, this permission logic might want to be
            //       reworked to use a stream instead of a file so that we don't need to write a
            //       file at all.  Note that there might be some trickiness with opening a stream
            //       for multiple users.
            ParcelFileDescriptor parcelFd = getContentResolver()
                    .openFileDescriptor(assetUri, "r");
            tempFile = WearPackageUtil.getFileFromFd(WearPackageInstallerService.this,
                    parcelFd, packageName, compressionAlg);
            if (tempFile == null) {
                Log.e(TAG, "Could not create a temp file from FD for " + packageName);
                return;
            }
            PackageParser.Package pkg = PackageUtil.getPackageInfo(tempFile);
            if (pkg == null) {
                Log.e(TAG, "Could not parse apk information for " + packageName);
                return;
            }

            if (!pkg.packageName.equals(packageName)) {
                Log.e(TAG, "Wearable Package Name has to match what is provided for " +
                        packageName);
                return;
            }

            List<String> wearablePerms = pkg.requestedPermissions;

            // Log if the installed pkg has a higher version number.
            if (existingPkgInfo != null) {
                if (existingPkgInfo.versionCode == pkg.mVersionCode) {
                    if (skipIfSameVersion) {
                        Log.w(TAG, "Version number (" + pkg.mVersionCode +
                                ") of new app is equal to existing app for " + packageName +
                                "; not installing due to versionCheck");
                        return;
                    } else {
                        Log.w(TAG, "Version number of new app (" + pkg.mVersionCode +
                                ") is equal to existing app for " + packageName);
                    }
                } else if (existingPkgInfo.versionCode > pkg.mVersionCode) {
                    if (skipIfLowerVersion) {
                        // Starting in Feldspar, we are not going to allow downgrades of any app.
                        Log.w(TAG, "Version number of new app (" + pkg.mVersionCode +
                                ") is lower than existing app ( " + existingPkgInfo.versionCode +
                                ") for " + packageName + "; not installing due to versionCheck");
                        return;
                    } else {
                        Log.w(TAG, "Version number of new app (" + pkg.mVersionCode +
                                ") is lower than existing app ( " + existingPkgInfo.versionCode +
                                ") for " + packageName);
                    }
                }

                // Following the Android Phone model, we should only check for permissions for any
                // newly defined perms.
                if (existingPkgInfo.requestedPermissions != null) {
                    for (int i = 0; i < existingPkgInfo.requestedPermissions.length; ++i) {
                        // If the permission is granted, then we will not ask to request it again.
                        if ((existingPkgInfo.requestedPermissionsFlags[i] &
                                PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, existingPkgInfo.requestedPermissions[i] +
                                        " is already granted for " + packageName);
                            }
                            wearablePerms.remove(existingPkgInfo.requestedPermissions[i]);
                        }
                    }
                }
            }

            // Check that the wearable has all the features.
            boolean hasAllFeatures = true;
            if (pkg.reqFeatures != null) {
                for (FeatureInfo feature : pkg.reqFeatures) {
                    if (feature.name != null && !pm.hasSystemFeature(feature.name) &&
                            (feature.flags & FeatureInfo.FLAG_REQUIRED) != 0) {
                        Log.e(TAG, "Wearable does not have required feature: " + feature +
                                " for " + packageName);
                        hasAllFeatures = false;
                    }
                }
            }

            if (!hasAllFeatures) {
                return;
            }

            // Check permissions on both the new wearable package and also on the already installed
            // wearable package.
            // If the app is targeting API level 23, we will also start a service in ClockworkHome
            // which will ultimately prompt the user to accept/reject permissions.
            if (checkPerms && !checkPermissions(pkg, companionSdkVersion, companionDeviceVersion,
                    permUri, wearablePerms, tempFile)) {
                Log.w(TAG, "Wearable does not have enough permissions.");
                return;
            }

            // Finally install the package.
            ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(assetUri, "r");
            PackageInstallerFactory.getPackageInstaller(this).install(packageName, fd,
                    new PackageInstallListener(this, lock, startId, packageName));

            messageSent = true;
            Log.i(TAG, "Sent installation request for " + packageName);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Could not find the file with URI " + assetUri, e);
        } finally {
            if (!messageSent) {
                // Some error happened. If the message has been sent, we can wait for the observer
                // which will finish the service.
                if (tempFile != null) {
                    tempFile.delete();
                }
                finishService(lock, startId);
            }
        }
    }

    // TODO: This was left using the old PackageManager API due to the fact that this code is being
    //       deprecated and that we need the bare minimum to continue working moving forward
    //       If this code is used as reference, this logic should be reworked to use the new
    //       PackageInstaller APIs similar to how installPackage was reworked
    private void uninstallPackage(Bundle argsBundle) {
        int startId = WearPackageArgs.getStartId(argsBundle);
        final String packageName = WearPackageArgs.getPackageName(argsBundle);

        final PackageManager pm = getPackageManager();
        try {
            // Result ignored.
            pm.getPackageInfo(packageName, 0);

            // Found package, send uninstall request.
            PowerManager.WakeLock lock = getLock(this.getApplicationContext());

            try {
                pm.deletePackage(packageName, new PackageDeleteObserver(lock, startId),
                        PackageManager.DELETE_ALL_USERS);
            } catch (IllegalArgumentException e) {
                // Couldn't find the package, no need to call uninstall.
                Log.w(TAG, "Could not find package, not deleting " + packageName, e);
            }

            startPermsServiceForUninstall(packageName);
            Log.i(TAG, "Sent delete request for " + packageName);
        } catch (PackageManager.NameNotFoundException e) {
            // Couldn't find the package, no need to call uninstall.
            Log.w(TAG, "Could not find package, not deleting " + packageName, e);
        }
    }

    private boolean checkPermissions(PackageParser.Package pkg, int companionSdkVersion,
            int companionDeviceVersion, Uri permUri, List<String> wearablePermissions,
            File apkFile) {
        // If the Wear App is targeted for M-release, since the permission model has been changed,
        // permissions may not be granted on the phone yet. We need a different flow for user to
        // accept these permissions.
        //
        // Assumption: Code is running on E-release, so Wear is always running M.
        // - Case 1: If the Wear App(WA) is targeting 23, always choose the M model (4 cases)
        // - Case 2: Else if the Phone App(PA) is targeting 23 and Phone App(P) is running on M,
        // show a Dialog so that the user can accept all perms (1 case)
        //   - Also show a warning to the developer if the watch is targeting M
        // - Case 3: If Case 2 is false, then the behavior on the phone is pre-M. Stick to pre-M
        // behavior on watch (as long as we don't hit case 1).
        //   - 3a: WA(22) PA(22) P(22) -> watch app is not targeting 23
        //   - 3b: WA(22) PA(22) P(23) -> watch app is not targeting 23
        //   - 3c: WA(22) PA(23) P(22) -> watch app is not targeting 23
        // - Case 4: We did not get Companion App's/Device's version, always show dialog to user to
        // accept permissions. (This happens if the AndroidWear Companion App is really old).
        boolean isWearTargetingM =
                pkg.applicationInfo.targetSdkVersion > Build.VERSION_CODES.LOLLIPOP_MR1;
        if (isWearTargetingM) { // Case 1
            // Install the app if Wear App is ready for the new perms model.
            return true;
        }

        List<String> unavailableWearablePerms = getWearPermsNotGrantedOnPhone(pkg.packageName,
                permUri, wearablePermissions);
        if (unavailableWearablePerms == null) {
            return false;
        }

        if (unavailableWearablePerms.size() == 0) {
            // All permissions requested by the watch are already granted on the phone, no need
            // to do anything.
            return true;
        }

        // Cases 2 and 4.
        boolean isCompanionTargetingM = companionSdkVersion > Build.VERSION_CODES.LOLLIPOP_MR1;
        boolean isCompanionRunningM = companionDeviceVersion > Build.VERSION_CODES.LOLLIPOP_MR1;
        if (isCompanionTargetingM) { // Case 2 Warning
            Log.w(TAG, "MNC: Wear app's targetSdkVersion should be at least 23, if " +
                    "phone app is targeting at least 23, will continue.");
        }
        if ((isCompanionTargetingM && isCompanionRunningM) || // Case 2
                companionSdkVersion == 0 || companionDeviceVersion == 0) { // Case 4
            startPermsServiceForInstall(pkg, apkFile, unavailableWearablePerms);
        }

        // Case 3a-3c.
        return false;
    }

    /**
     * Given a {@string packageName} corresponding to a phone app, query the provider for all the
     * perms that are granted.
     * @return null if there is an error retrieving this info
     *         else, a list of all the wearable perms that are not in the list of granted perms of
     * the phone.
     */
    private List<String> getWearPermsNotGrantedOnPhone(String packageName, Uri permUri,
            List<String> wearablePermissions) {
        if (permUri == null) {
            Log.e(TAG, "Permission URI is null");
            return null;
        }
        Cursor permCursor = getContentResolver().query(permUri, null, null, null, null);
        if (permCursor == null) {
            Log.e(TAG, "Could not get the cursor for the permissions");
            return null;
        }

        Set<String> grantedPerms = new HashSet<>();
        Set<String> ungrantedPerms = new HashSet<>();
        while(permCursor.moveToNext()) {
            // Make sure that the MatrixCursor returned by the ContentProvider has 2 columns and
            // verify their types.
            if (permCursor.getColumnCount() == 2
                    && Cursor.FIELD_TYPE_STRING == permCursor.getType(0)
                    && Cursor.FIELD_TYPE_INTEGER == permCursor.getType(1)) {
                String perm = permCursor.getString(0);
                Integer granted = permCursor.getInt(1);
                if (granted == 1) {
                    grantedPerms.add(perm);
                } else {
                    ungrantedPerms.add(perm);
                }
            }
        }
        permCursor.close();

        ArrayList<String> unavailableWearablePerms = new ArrayList<>();
        for (String wearablePerm : wearablePermissions) {
            if (!grantedPerms.contains(wearablePerm)) {
                unavailableWearablePerms.add(wearablePerm);
                if (!ungrantedPerms.contains(wearablePerm)) {
                    // This is an error condition. This means that the wearable has permissions that
                    // are not even declared in its host app. This is a developer error.
                    Log.e(TAG, "Wearable " + packageName + " has a permission \"" + wearablePerm
                            + "\" that is not defined in the host application's manifest.");
                } else {
                    Log.w(TAG, "Wearable " + packageName + " has a permission \"" + wearablePerm +
                            "\" that is not granted in the host application.");
                }
            }
        }
        return unavailableWearablePerms;
    }

    private void finishService(PowerManager.WakeLock lock, int startId) {
        if (lock.isHeld()) {
            lock.release();
        }
        stopSelf(startId);
    }

    private synchronized PowerManager.WakeLock getLock(Context context) {
        if (lockStatic == null) {
            PowerManager mgr =
                    (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            lockStatic = mgr.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, context.getClass().getSimpleName());
            lockStatic.setReferenceCounted(true);
        }
        return lockStatic;
    }

    private void startPermsServiceForInstall(final PackageParser.Package pkg, final File apkFile,
            List<String> unavailableWearablePerms) {
        final String packageName = pkg.packageName;

        Intent showPermsIntent = new Intent()
                .setComponent(new ComponentName(HOME_APP_PACKAGE_NAME, SHOW_PERMS_SERVICE_CLASS))
                .setAction(Intent.ACTION_INSTALL_PACKAGE);
        final PackageManager pm = getPackageManager();
        pkg.applicationInfo.publicSourceDir = apkFile.getPath();
        final CharSequence label = pkg.applicationInfo.loadLabel(pm);
        final Uri iconUri = getIconFileUri(packageName, pkg.applicationInfo.loadIcon(pm));
        if (TextUtils.isEmpty(label) || iconUri == null) {
            Log.e(TAG, "MNC: Could not launch service since either label " + label +
                    ", or icon Uri " + iconUri + " is invalid.");
        } else {
            showPermsIntent.putExtra(KEY_APP_LABEL, label);
            showPermsIntent.putExtra(KEY_APP_ICON_URI, iconUri);
            showPermsIntent.putExtra(KEY_PACKAGE_NAME, packageName);
            showPermsIntent.putExtra(KEY_PERMS_LIST,
                    unavailableWearablePerms.toArray(new String[0]));
            showPermsIntent.putExtra(KEY_HAS_LAUNCHER, WearPackageUtil.hasLauncherActivity(pkg));

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "MNC: Launching Intent " + showPermsIntent + " for " + packageName +
                        " with name " + label);
            }
            startService(showPermsIntent);
        }
    }

    private void startPermsServiceForUninstall(final String packageName) {
        Intent showPermsIntent = new Intent()
                .setComponent(new ComponentName(HOME_APP_PACKAGE_NAME, SHOW_PERMS_SERVICE_CLASS))
                .setAction(Intent.ACTION_UNINSTALL_PACKAGE);
        showPermsIntent.putExtra(KEY_PACKAGE_NAME, packageName);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Launching Intent " + showPermsIntent + " for " + packageName);
        }
        startService(showPermsIntent);
    }

    private Uri getIconFileUri(final String packageName, final Drawable d) {
        if (d == null || !(d instanceof BitmapDrawable)) {
            Log.e(TAG, "Drawable is not a BitmapDrawable for " + packageName);
            return null;
        }
        File iconFile = WearPackageUtil.getIconFile(this, packageName);

        if (iconFile == null) {
            Log.e(TAG, "Could not get icon file for " + packageName);
            return null;
        }

        FileOutputStream fos = null;
        try {
            // Convert bitmap to byte array
            Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, bos);

            // Write the bytes into the file
            fos = new FileOutputStream(iconFile);
            fos.write(bos.toByteArray());
            fos.flush();

            return WearPackageIconProvider.getUriForPackage(packageName);
        } catch (IOException e) {
            Log.e(TAG, "Could not convert drawable to icon file for package " + packageName, e);
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private class PackageInstallListener implements PackageInstallerImpl.InstallListener {
        private Context mContext;
        private PowerManager.WakeLock mWakeLock;
        private int mStartId;
        private String mApplicationPackageName;
        private PackageInstallListener(Context context, PowerManager.WakeLock wakeLock,
                int startId, String applicationPackageName) {
            mContext = context;
            mWakeLock = wakeLock;
            mStartId = startId;
            mApplicationPackageName = applicationPackageName;
        }

        @Override
        public void installBeginning() {
            Log.i(TAG, "Package " + mApplicationPackageName + " is being installed.");
        }

        @Override
        public void installSucceeded() {
            try {
                Log.i(TAG, "Package " + mApplicationPackageName + " was installed.");

                // Delete tempFile from the file system.
                File tempFile = WearPackageUtil.getTemporaryFile(mContext, mApplicationPackageName);
                if (tempFile != null) {
                    tempFile.delete();
                }
            } finally {
                finishService(mWakeLock, mStartId);
            }
        }

        @Override
        public void installFailed(int errorCode, String errorDesc) {
            Log.e(TAG, "Package install failed " + mApplicationPackageName
                    + ", errorCode " + errorCode);
            WearPackageUtil.removeFromPermStore(mContext, mApplicationPackageName);
            finishService(mWakeLock, mStartId);
        }
    }

    private class PackageDeleteObserver extends IPackageDeleteObserver.Stub {
        private PowerManager.WakeLock mWakeLock;
        private int mStartId;

        private PackageDeleteObserver(PowerManager.WakeLock wakeLock, int startId) {
            mWakeLock = wakeLock;
            mStartId = startId;
        }

        public void packageDeleted(String packageName, int returnCode) {
            try {
                if (returnCode >= 0) {
                    Log.i(TAG, "Package " + packageName + " was uninstalled.");
                } else {
                    Log.e(TAG, "Package uninstall failed " + packageName + ", returnCode " +
                            returnCode);
                }
            } finally {
                finishService(mWakeLock, mStartId);
            }
        }
    }
}
