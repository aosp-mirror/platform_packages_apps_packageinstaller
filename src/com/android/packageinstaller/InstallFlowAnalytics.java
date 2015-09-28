/*
**
** Copyright 2013, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
package com.android.packageinstaller;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.EventLog;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import libcore.io.IoUtils;

/**
 * Analytics about an attempt to install a package via {@link PackageInstallerActivity}.
 *
 * <p>An instance of this class is created at the beginning of the install flow and gradually filled
 * as the user progresses through the flow. When the flow terminates (regardless of the reason),
 * {@link #setFlowFinished(byte)} is invoked which reports the installation attempt as an event
 * to the Event Log.
 */
public class InstallFlowAnalytics implements Parcelable {

    private static final String TAG = "InstallFlowAnalytics";

    /** Installation has not yet terminated. */
    static final byte RESULT_NOT_YET_AVAILABLE = -1;

    /** Package successfully installed. */
    static final byte RESULT_SUCCESS = 0;

    /** Installation failed because scheme unsupported. */
    static final byte RESULT_FAILED_UNSUPPORTED_SCHEME = 1;

    /**
     * Installation of an APK failed because of a failure to obtain information from the provided
     * APK.
     */
    static final byte RESULT_FAILED_TO_GET_PACKAGE_INFO = 2;

    /**
     * Installation of an already installed package into the current user profile failed because the
     * specified package is not installed.
     */
    static final byte RESULT_FAILED_PACKAGE_MISSING = 3;

    /**
     * Installation failed because installation from unknown sources is prohibited by the Unknown
     * Sources setting.
     */
    static final byte RESULT_BLOCKED_BY_UNKNOWN_SOURCES_SETTING = 4;

    /** Installation cancelled by the user. */
    static final byte RESULT_CANCELLED_BY_USER = 5;

    /**
     * Installation failed due to {@code PackageManager} failure. PackageManager error code is
     * provided in {@link #mPackageManagerInstallResult}).
     */
    static final byte RESULT_PACKAGE_MANAGER_INSTALL_FAILED = 6;

    /**
     * Installation blocked since this feature is not allowed on Android Wear devices yet.
     */
    static final byte RESULT_NOT_ALLOWED_ON_WEAR = 7;

    private static final int FLAG_INSTALLS_FROM_UNKNOWN_SOURCES_PERMITTED = 1 << 0;
    private static final int FLAG_INSTALL_REQUEST_FROM_UNKNOWN_SOURCE = 1 << 1;
    private static final int FLAG_VERIFY_APPS_ENABLED = 1 << 2;
    private static final int FLAG_APP_VERIFIER_INSTALLED = 1 << 3;
    private static final int FLAG_FILE_URI = 1 << 4;
    private static final int FLAG_REPLACE = 1 << 5;
    private static final int FLAG_SYSTEM_APP = 1 << 6;
    private static final int FLAG_PACKAGE_INFO_OBTAINED = 1 << 7;
    private static final int FLAG_INSTALL_BUTTON_CLICKED = 1 << 8;
    private static final int FLAG_NEW_PERMISSIONS_FOUND = 1 << 9;
    private static final int FLAG_PERMISSIONS_DISPLAYED = 1 << 10;
    private static final int FLAG_NEW_PERMISSIONS_DISPLAYED = 1 << 11;
    private static final int FLAG_ALL_PERMISSIONS_DISPLAYED = 1 << 12;

    /**
     * Information about this flow expressed as a collection of flags. See {@code FLAG_...}
     * constants.
     */
    private int mFlags;

    /** Outcome of the flow. See {@code RESULT_...} constants. */
    private byte mResult = RESULT_NOT_YET_AVAILABLE;

    /**
     * Result code returned by {@code PackageManager} to install the package or {@code 0} if
     * {@code PackageManager} has not yet been invoked to install the package.
     */
    private int mPackageManagerInstallResult;

    /**
     * Time instant when the installation request arrived, measured in elapsed realtime
     * milliseconds. See {@link SystemClock#elapsedRealtime()}.
     */
    private long mStartTimestampMillis;

    /**
     * Time instant when the information about the package being installed was obtained, measured in
     * elapsed realtime milliseconds. See {@link SystemClock#elapsedRealtime()}.
     */
    private long mPackageInfoObtainedTimestampMillis;

    /**
     * Time instant when the user clicked the Install button, measured in elapsed realtime
     * milliseconds. See {@link SystemClock#elapsedRealtime()}. This field is only valid if the
     * Install button has been clicked, as signaled by {@link #FLAG_INSTALL_BUTTON_CLICKED}.
     */
    private long mInstallButtonClickTimestampMillis;

    /**
     * Time instant when this flow terminated, measured in elapsed realtime milliseconds. See
     * {@link SystemClock#elapsedRealtime()}.
     */
    private long mEndTimestampMillis;

    /** URI of the package being installed. */
    private String mPackageUri;

    /** Whether this attempt has been logged to the Event Log. */
    private boolean mLogged;

    private Context mContext;

    public static final Parcelable.Creator<InstallFlowAnalytics> CREATOR =
            new Parcelable.Creator<InstallFlowAnalytics>() {
        @Override
        public InstallFlowAnalytics createFromParcel(Parcel in) {
            return new InstallFlowAnalytics(in);
        }

        @Override
        public InstallFlowAnalytics[] newArray(int size) {
            return new InstallFlowAnalytics[size];
        }
    };

    public InstallFlowAnalytics() {}

    public InstallFlowAnalytics(Parcel in) {
        mFlags = in.readInt();
        mResult = in.readByte();
        mPackageManagerInstallResult = in.readInt();
        mStartTimestampMillis = in.readLong();
        mPackageInfoObtainedTimestampMillis = in.readLong();
        mInstallButtonClickTimestampMillis = in.readLong();
        mEndTimestampMillis = in.readLong();
        mPackageUri = in.readString();
        mLogged = readBoolean(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mFlags);
        dest.writeByte(mResult);
        dest.writeInt(mPackageManagerInstallResult);
        dest.writeLong(mStartTimestampMillis);
        dest.writeLong(mPackageInfoObtainedTimestampMillis);
        dest.writeLong(mInstallButtonClickTimestampMillis);
        dest.writeLong(mEndTimestampMillis);
        dest.writeString(mPackageUri);
        writeBoolean(dest, mLogged);
    }

    private static void writeBoolean(Parcel dest, boolean value) {
        dest.writeByte((byte) (value ? 1 : 0));
    }

    private static boolean readBoolean(Parcel dest) {
        return dest.readByte() != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    void setContext(Context context) {
        mContext = context;
    }

    /** Sets whether the Unknown Sources setting is checked. */
    void setInstallsFromUnknownSourcesPermitted(boolean permitted) {
        setFlagState(FLAG_INSTALLS_FROM_UNKNOWN_SOURCES_PERMITTED, permitted);
    }

    /** Gets whether the Unknown Sources setting is checked. */
    private boolean isInstallsFromUnknownSourcesPermitted() {
        return isFlagSet(FLAG_INSTALLS_FROM_UNKNOWN_SOURCES_PERMITTED);
    }

    /** Sets whether this install attempt is from an unknown source. */
    void setInstallRequestFromUnknownSource(boolean unknownSource) {
        setFlagState(FLAG_INSTALL_REQUEST_FROM_UNKNOWN_SOURCE, unknownSource);
    }

    /** Gets whether this install attempt is from an unknown source. */
    private boolean isInstallRequestFromUnknownSource() {
        return isFlagSet(FLAG_INSTALL_REQUEST_FROM_UNKNOWN_SOURCE);
    }

    /** Sets whether app verification is enabled. */
    void setVerifyAppsEnabled(boolean enabled) {
        setFlagState(FLAG_VERIFY_APPS_ENABLED, enabled);
    }

    /** Gets whether app verification is enabled. */
    private boolean isVerifyAppsEnabled() {
        return isFlagSet(FLAG_VERIFY_APPS_ENABLED);
    }

    /** Sets whether at least one app verifier is installed. */
    void setAppVerifierInstalled(boolean installed) {
        setFlagState(FLAG_APP_VERIFIER_INSTALLED, installed);
    }

    /** Gets whether at least one app verifier is installed. */
    private boolean isAppVerifierInstalled() {
        return isFlagSet(FLAG_APP_VERIFIER_INSTALLED);
    }

    /**
     * Sets whether an APK file is being installed.
     *
     * @param fileUri {@code true} if an APK file is being installed, {@code false} if an already
     *        installed package is being installed to this user profile.
     */
    void setFileUri(boolean fileUri) {
        setFlagState(FLAG_FILE_URI, fileUri);
    }

    /**
     * Sets the URI of the package being installed.
     */
    void setPackageUri(String packageUri) {
        mPackageUri = packageUri;
    }

    /**
     * Gets whether an APK file is being installed.
     *
     * @return {@code true} if an APK file is being installed, {@code false} if an already
     *         installed package is being installed to this user profile.
     */
    private boolean isFileUri() {
        return isFlagSet(FLAG_FILE_URI);
    }

    /** Sets whether this is an attempt to replace an existing package. */
    void setReplace(boolean replace) {
        setFlagState(FLAG_REPLACE, replace);
    }

    /** Gets whether this is an attempt to replace an existing package. */
    private boolean isReplace() {
        return isFlagSet(FLAG_REPLACE);
    }

    /** Sets whether the package being updated is a system package. */
    void setSystemApp(boolean systemApp) {
        setFlagState(FLAG_SYSTEM_APP, systemApp);
    }

    /** Gets whether the package being updated is a system package. */
    private boolean isSystemApp() {
        return isFlagSet(FLAG_SYSTEM_APP);
    }

    /**
     * Sets whether the package being installed is requesting more permissions than the already
     * installed version of the package.
     */
    void setNewPermissionsFound(boolean found) {
        setFlagState(FLAG_NEW_PERMISSIONS_FOUND, found);
    }

    /**
     * Gets whether the package being installed is requesting more permissions than the already
     * installed version of the package.
     */
    private boolean isNewPermissionsFound() {
        return isFlagSet(FLAG_NEW_PERMISSIONS_FOUND);
    }

    /** Sets whether permissions were displayed to the user. */
    void setPermissionsDisplayed(boolean displayed) {
        setFlagState(FLAG_PERMISSIONS_DISPLAYED, displayed);
    }

    /** Gets whether permissions were displayed to the user. */
    private boolean isPermissionsDisplayed() {
        return isFlagSet(FLAG_PERMISSIONS_DISPLAYED);
    }

    /**
     * Sets whether new permissions were displayed to the user (if permissions were displayed at
     * all).
     */
    void setNewPermissionsDisplayed(boolean displayed) {
        setFlagState(FLAG_NEW_PERMISSIONS_DISPLAYED, displayed);
    }

    /**
     * Gets whether new permissions were displayed to the user (if permissions were displayed at
     * all).
     */
    private boolean isNewPermissionsDisplayed() {
        return isFlagSet(FLAG_NEW_PERMISSIONS_DISPLAYED);
    }

    /**
     * Sets whether all permissions were displayed to the user (if permissions were displayed at
     * all).
     */
    void setAllPermissionsDisplayed(boolean displayed) {
        setFlagState(FLAG_ALL_PERMISSIONS_DISPLAYED, displayed);
    }

    /**
     * Gets whether all permissions were displayed to the user (if permissions were displayed at
     * all).
     */
    private boolean isAllPermissionsDisplayed() {
        return isFlagSet(FLAG_ALL_PERMISSIONS_DISPLAYED);
    }

    /**
     * Sets the time instant when the installation request arrived, measured in elapsed realtime
     * milliseconds. See {@link SystemClock#elapsedRealtime()}.
     */
    void setStartTimestampMillis(long timestampMillis) {
        mStartTimestampMillis = timestampMillis;
    }

    /**
     * Records that the information about the package info has been obtained or that there has been
     * a failure to obtain the information.
     */
    void setPackageInfoObtained() {
        setFlagState(FLAG_PACKAGE_INFO_OBTAINED, true);
        mPackageInfoObtainedTimestampMillis = SystemClock.elapsedRealtime();
    }

    /**
     * Checks whether the information about the package info has been obtained or that there has
     * been a failure to obtain the information.
     */
    private boolean isPackageInfoObtained() {
        return isFlagSet(FLAG_PACKAGE_INFO_OBTAINED);
    }

    /**
     * Records that the Install button has been clicked.
     */
    void setInstallButtonClicked() {
        setFlagState(FLAG_INSTALL_BUTTON_CLICKED, true);
        mInstallButtonClickTimestampMillis = SystemClock.elapsedRealtime();
    }

    /**
     * Checks whether the Install button has been clicked.
     */
    private boolean isInstallButtonClicked() {
        return isFlagSet(FLAG_INSTALL_BUTTON_CLICKED);
    }

    /**
     * Marks this flow as finished due to {@code PackageManager} succeeding or failing to install
     * the package and reports this to the Event Log.
     */
    void setFlowFinishedWithPackageManagerResult(int packageManagerResult) {
        mPackageManagerInstallResult = packageManagerResult;
        if (packageManagerResult == PackageManager.INSTALL_SUCCEEDED) {
            setFlowFinished(
                    InstallFlowAnalytics.RESULT_SUCCESS);
        } else {
            setFlowFinished(
                    InstallFlowAnalytics.RESULT_PACKAGE_MANAGER_INSTALL_FAILED);
        }
    }

    /**
     * Marks this flow as finished and reports this to the Event Log.
     */
    void setFlowFinished(byte result) {
        if (mLogged) {
            return;
        }
        mResult = result;
        mEndTimestampMillis = SystemClock.elapsedRealtime();
        writeToEventLog();
    }

    private void writeToEventLog() {
        byte packageManagerInstallResultByte = 0;
        if (mResult == RESULT_PACKAGE_MANAGER_INSTALL_FAILED) {
            // PackageManager install error codes are negative, starting from -1 and going to
            // -111 (at the moment). We thus store them in negated form.
            packageManagerInstallResultByte = clipUnsignedValueToUnsignedByte(
                    -mPackageManagerInstallResult);
        }

        final int resultAndFlags = (mResult & 0xff)
                | ((packageManagerInstallResultByte & 0xff) << 8)
                | ((mFlags & 0xffff) << 16);

        // Total elapsed time from start to end, in milliseconds.
        final int totalElapsedTime =
                clipUnsignedLongToUnsignedInt(mEndTimestampMillis - mStartTimestampMillis);

        // Total elapsed time from start till information about the package being installed was
        // obtained, in milliseconds.
        final int elapsedTimeTillPackageInfoObtained = (isPackageInfoObtained())
                ? clipUnsignedLongToUnsignedInt(
                        mPackageInfoObtainedTimestampMillis - mStartTimestampMillis)
                : 0;

        // Total elapsed time from start till Install button clicked, in milliseconds
        // milliseconds.
        final int elapsedTimeTillInstallButtonClick = (isInstallButtonClicked())
                ? clipUnsignedLongToUnsignedInt(
                            mInstallButtonClickTimestampMillis - mStartTimestampMillis)
                : 0;

        // If this user has consented to app verification, augment the logged event with the hash of
        // the contents of the APK.
        if (((mFlags & FLAG_FILE_URI) != 0)
                && ((mFlags & FLAG_VERIFY_APPS_ENABLED) != 0)
                && (isUserConsentToVerifyAppsGranted())) {
            // Log the hash of the APK's contents.
            // Reading the APK may take a while -- perform in background.
            AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    byte[] digest = null;
                    try {
                        digest = getPackageContentsDigest();
                    } catch (IOException e) {
                        Log.w(TAG, "Failed to hash APK contents", e);
                    } finally {
                        String digestHex = (digest != null)
                                ? IntegralToString.bytesToHexString(digest, false)
                                : "";
                        EventLogTags.writeInstallPackageAttempt(
                                resultAndFlags,
                                totalElapsedTime,
                                elapsedTimeTillPackageInfoObtained,
                                elapsedTimeTillInstallButtonClick,
                                digestHex);
                    }
                }
            });
        } else {
            // Do not log the hash of the APK's contents
            EventLogTags.writeInstallPackageAttempt(
                    resultAndFlags,
                    totalElapsedTime,
                    elapsedTimeTillPackageInfoObtained,
                    elapsedTimeTillInstallButtonClick,
                    "");
        }
        mLogged = true;

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Analytics:"
                    + "\n\tinstallsFromUnknownSourcesPermitted: "
                        + isInstallsFromUnknownSourcesPermitted()
                    + "\n\tinstallRequestFromUnknownSource: " + isInstallRequestFromUnknownSource()
                    + "\n\tverifyAppsEnabled: " + isVerifyAppsEnabled()
                    + "\n\tappVerifierInstalled: " + isAppVerifierInstalled()
                    + "\n\tfileUri: " + isFileUri()
                    + "\n\treplace: " + isReplace()
                    + "\n\tsystemApp: " + isSystemApp()
                    + "\n\tpackageInfoObtained: " + isPackageInfoObtained()
                    + "\n\tinstallButtonClicked: " + isInstallButtonClicked()
                    + "\n\tpermissionsDisplayed: " + isPermissionsDisplayed()
                    + "\n\tnewPermissionsDisplayed: " + isNewPermissionsDisplayed()
                    + "\n\tallPermissionsDisplayed: " + isAllPermissionsDisplayed()
                    + "\n\tnewPermissionsFound: " + isNewPermissionsFound()
                    + "\n\tresult: " + mResult
                    + "\n\tpackageManagerInstallResult: " + mPackageManagerInstallResult
                    + "\n\ttotalDuration: " + (mEndTimestampMillis - mStartTimestampMillis) + " ms"
                    + "\n\ttimeTillPackageInfoObtained: "
                        + ((isPackageInfoObtained())
                            ? ((mPackageInfoObtainedTimestampMillis - mStartTimestampMillis)
                                    + " ms")
                            : "n/a")
                    + "\n\ttimeTillInstallButtonClick: "
                        + ((isInstallButtonClicked())
                            ? ((mInstallButtonClickTimestampMillis - mStartTimestampMillis) + " ms")
                            : "n/a"));
            Log.v(TAG, "Wrote to Event Log: 0x" + Long.toString(resultAndFlags & 0xffffffffL, 16)
                    + ", " + totalElapsedTime
                    + ", " + elapsedTimeTillPackageInfoObtained
                    + ", " + elapsedTimeTillInstallButtonClick);
        }
    }

    private static final byte clipUnsignedValueToUnsignedByte(long value) {
        if (value < 0) {
            return 0;
        } else if (value > 0xff) {
            return (byte) 0xff;
        } else {
            return (byte) value;
        }
    }

    private static final int clipUnsignedLongToUnsignedInt(long value) {
        if (value < 0) {
            return 0;
        } else if (value > 0xffffffffL) {
            return 0xffffffff;
        } else {
            return (int) value;
        }
    }

    /**
     * Sets or clears the specified flag in the {@link #mFlags} field.
     */
    private void setFlagState(int flag, boolean set) {
        if (set) {
            mFlags |= flag;
        } else {
            mFlags &= ~flag;
        }
    }

    /**
     * Checks whether the specified flag is set in the {@link #mFlags} field.
     */
    private boolean isFlagSet(int flag) {
        return (mFlags & flag) == flag;
    }

    /**
     * Checks whether the user has consented to app verification.
     */
    private boolean isUserConsentToVerifyAppsGranted() {
        return Settings.Secure.getInt(
                mContext.getContentResolver(),
                Settings.Secure.PACKAGE_VERIFIER_USER_CONSENT, 0) != 0;
    }

    /**
     * Gets the digest of the contents of the package being installed.
     */
    private byte[] getPackageContentsDigest() throws IOException {
        File file = new File(Uri.parse(mPackageUri).getPath());
        return getSha256ContentsDigest(file);
    }

    /**
     * Gets the SHA-256 digest of the contents of the specified file.
     */
    private static byte[] getSha256ContentsDigest(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }

        byte[] buf = new byte[8192];
        InputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(file), buf.length);
            int chunkSize;
            while ((chunkSize = in.read(buf)) != -1) {
                digest.update(buf, 0, chunkSize);
            }
        } finally {
            IoUtils.closeQuietly(in);
        }
        return digest.digest();
    }
}
