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

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageParser;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.util.Log;

import org.tukaani.xz.LZMAInputStream;
import org.tukaani.xz.XZInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class WearPackageUtil {
    private static final String TAG = "WearablePkgInstaller";

    private static final String COMPRESSION_LZMA = "lzma";
    private static final String COMPRESSION_XZ = "xz";

    private static final String SHOW_PERMS_SERVICE_PKG_NAME = "com.google.android.wearable.app";
    private static final String SHOW_PERMS_SERVICE_CLASS_NAME =
            "com.google.android.clockwork.packagemanager.ShowPermsService";
    private static final String EXTRA_PACKAGE_NAME
            = "com.google.android.clockwork.EXTRA_PACKAGE_NAME";

    public static File getTemporaryFile(Context context, String packageName) {
        try {
            File newFileDir = new File(context.getFilesDir(), "tmp");
            newFileDir.mkdirs();
            Os.chmod(newFileDir.getAbsolutePath(), 0771);
            File newFile = new File(newFileDir, packageName + ".apk");
            return newFile;
        }   catch (ErrnoException e) {
            Log.e(TAG, "Failed to open.", e);
            return null;
        }
    }

    public static File getIconFile(final Context context, final String packageName) {
        try {
            File newFileDir = new File(context.getFilesDir(), "images/icons");
            newFileDir.mkdirs();
            Os.chmod(newFileDir.getAbsolutePath(), 0771);
            return new File(newFileDir, packageName + ".icon");
        }   catch (ErrnoException e) {
            Log.e(TAG, "Failed to open.", e);
            return null;
        }
    }

    /**
     * In order to make sure that the Wearable Asset Manager has a reasonable apk that can be used
     * by the PackageManager, we will parse it before sending it to the PackageManager.
     * Unfortunately, PackageParser needs a file to parse. So, we have to temporarily convert the fd
     * to a File.
     *
     * @param context
     * @param fd FileDescriptor to convert to File
     * @param packageName Name of package, will define the name of the file
     * @param compressionAlg Can be null. For ALT mode the APK will be compressed. We will
     *                       decompress it here
     */
    public static File getFileFromFd(Context context, ParcelFileDescriptor fd,
            String packageName, @Nullable String compressionAlg) {
        File newFile = getTemporaryFile(context, packageName);
        if (fd == null || fd.getFileDescriptor() == null)  {
            return null;
        }
        InputStream fr = new ParcelFileDescriptor.AutoCloseInputStream(fd);
        try {
            if (TextUtils.equals(compressionAlg, COMPRESSION_XZ)) {
                fr = new XZInputStream(fr);
            } else if (TextUtils.equals(compressionAlg, COMPRESSION_LZMA)) {
                fr = new LZMAInputStream(fr);
            }
        } catch (IOException e) {
            Log.e(TAG, "Compression was set to " + compressionAlg + ", but could not decode ", e);
            return null;
        }

        int nRead;
        byte[] data = new byte[1024];
        try {
            final FileOutputStream fo = new FileOutputStream(newFile);
            while ((nRead = fr.read(data, 0, data.length)) != -1) {
                fo.write(data, 0, nRead);
            }
            fo.flush();
            fo.close();
            Os.chmod(newFile.getAbsolutePath(), 0644);
            return newFile;
        } catch (IOException e) {
            Log.e(TAG, "Reading from Asset FD or writing to temp file failed ", e);
            return null;
        }   catch (ErrnoException e) {
            Log.e(TAG, "Could not set permissions on file ", e);
            return null;
        } finally {
            try {
                fr.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close the file from FD ", e);
            }
        }
    }

    public static boolean hasLauncherActivity(PackageParser.Package pkg) {
        if (pkg == null || pkg.activities == null) {
            return false;
        }

        final int activityCount = pkg.activities.size();
        for (int i = 0; i < activityCount; ++i) {
            if (pkg.activities.get(i).intents != null) {
                ArrayList<PackageParser.ActivityIntentInfo> intents =
                        pkg.activities.get(i).intents;
                final int intentsCount = intents.size();
                for (int j = 0; j < intentsCount; ++j) {
                    final PackageParser.ActivityIntentInfo intentInfo = intents.get(j);
                    if (intentInfo.hasAction(Intent.ACTION_MAIN)) {
                        if (intentInfo.hasCategory(Intent.CATEGORY_INFO) ||
                                intentInfo .hasCategory(Intent.CATEGORY_LAUNCHER)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static void removeFromPermStore(Context context, String wearablePackageName) {
        Intent newIntent = new Intent()
                .setComponent(new ComponentName(
                        SHOW_PERMS_SERVICE_PKG_NAME, SHOW_PERMS_SERVICE_CLASS_NAME))
                .setAction(Intent.ACTION_UNINSTALL_PACKAGE);
        newIntent.putExtra(EXTRA_PACKAGE_NAME, wearablePackageName);
        Log.i(TAG, "Sending removeFromPermStore to ShowPermsService " + newIntent
                + " for " + wearablePackageName);
        context.startService(newIntent);
    }
}
