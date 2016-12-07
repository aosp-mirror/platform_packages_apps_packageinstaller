/*
**
** Copyright 2007, The Android Open Source Project
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

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageParser;
import android.content.pm.PackageUserState;
import android.content.pm.VerificationParams;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserManager;
import android.provider.Settings;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AppSecurityPermissions;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import com.android.packageinstaller.permission.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/*
 * This activity is launched when a new application is installed via side loading
 * The package is first parsed and the user is notified of parse errors via a dialog.
 * If the package is successfully parsed, the user is notified to turn on the install unknown
 * applications setting. A memory check is made at this point and the user is notified of out
 * of memory conditions if any. If the package is already existing on the device,
 * a confirmation dialog (to replace the existing package) is presented to the user.
 * Based on the user response the package is then installed by launching InstallAppConfirm
 * sub activity. All state transitions are handled in this activity
 */
public class PackageInstallerActivity extends Activity implements OnCancelListener, OnClickListener {
    private static final String TAG = "PackageInstaller";

    private static final int REQUEST_ENABLE_UNKNOWN_SOURCES = 1;

    private static final String SCHEME_FILE = "file";
    private static final String SCHEME_CONTENT = "content";
    private static final String SCHEME_PACKAGE = "package";

    private int mSessionId = -1;
    private Uri mPackageURI;
    private Uri mOriginatingURI;
    private Uri mReferrerURI;
    private int mOriginatingUid = VerificationParams.NO_UID;
    private File mContentUriApkStagingFile;

    private AsyncTask<Uri, Void, File> mStagingAsynTask;

    private boolean localLOGV = false;
    PackageManager mPm;
    UserManager mUserManager;
    PackageInstaller mInstaller;
    PackageInfo mPkgInfo;
    ApplicationInfo mSourceInfo;

    // ApplicationInfo object primarily used for already existing applications
    private ApplicationInfo mAppInfo = null;

    // View for install progress
    View mInstallConfirm;
    // Buttons to indicate user acceptance
    private Button mOk;
    private Button mCancel;
    CaffeinatedScrollView mScrollView = null;
    private boolean mOkCanInstall = false;

    static final String PREFS_ALLOWED_SOURCES = "allowed_sources";

    private static final String TAB_ID_ALL = "all";
    private static final String TAB_ID_NEW = "new";

    // Dialog identifiers used in showDialog
    private static final int DLG_BASE = 0;
    private static final int DLG_UNKNOWN_SOURCES = DLG_BASE + 1;
    private static final int DLG_PACKAGE_ERROR = DLG_BASE + 2;
    private static final int DLG_OUT_OF_SPACE = DLG_BASE + 3;
    private static final int DLG_INSTALL_ERROR = DLG_BASE + 4;
    private static final int DLG_ADMIN_RESTRICTS_UNKNOWN_SOURCES = DLG_BASE + 6;
    private static final int DLG_NOT_SUPPORTED_ON_WEAR = DLG_BASE + 7;

    private void startInstallConfirm() {
        ((TextView) findViewById(R.id.install_confirm_question))
                .setText(R.string.install_confirm_question);
        findViewById(R.id.spacer).setVisibility(View.GONE);
        TabHost tabHost = (TabHost)findViewById(android.R.id.tabhost);
        tabHost.setup();
        tabHost.setVisibility(View.VISIBLE);
        ViewPager viewPager = (ViewPager)findViewById(R.id.pager);
        TabsAdapter adapter = new TabsAdapter(this, tabHost, viewPager);
        // If the app supports runtime permissions the new permissions will
        // be requested at runtime, hence we do not show them at install.
        boolean supportsRuntimePermissions = mPkgInfo.applicationInfo.targetSdkVersion
                >= Build.VERSION_CODES.M;
        boolean permVisible = false;
        mScrollView = null;
        mOkCanInstall = false;
        int msg = 0;

        AppSecurityPermissions perms = new AppSecurityPermissions(this, mPkgInfo);
        final int N = perms.getPermissionCount(AppSecurityPermissions.WHICH_ALL);
        if (mAppInfo != null) {
            msg = (mAppInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    ? R.string.install_confirm_question_update_system
                    : R.string.install_confirm_question_update;
            mScrollView = new CaffeinatedScrollView(this);
            mScrollView.setFillViewport(true);
            boolean newPermissionsFound = false;
            if (!supportsRuntimePermissions) {
                newPermissionsFound =
                        (perms.getPermissionCount(AppSecurityPermissions.WHICH_NEW) > 0);
                if (newPermissionsFound) {
                    permVisible = true;
                    mScrollView.addView(perms.getPermissionsView(
                            AppSecurityPermissions.WHICH_NEW));
                }
            }
            if (!supportsRuntimePermissions && !newPermissionsFound) {
                LayoutInflater inflater = (LayoutInflater)getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                TextView label = (TextView)inflater.inflate(R.layout.label, null);
                label.setText(R.string.no_new_perms);
                mScrollView.addView(label);
            }
            adapter.addTab(tabHost.newTabSpec(TAB_ID_NEW).setIndicator(
                    getText(R.string.newPerms)), mScrollView);
        } else  {
            findViewById(R.id.tabscontainer).setVisibility(View.GONE);
            findViewById(R.id.spacer).setVisibility(View.VISIBLE);
        }
        if (!supportsRuntimePermissions && N > 0) {
            permVisible = true;
            LayoutInflater inflater = (LayoutInflater)getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            View root = inflater.inflate(R.layout.permissions_list, null);
            if (mScrollView == null) {
                mScrollView = (CaffeinatedScrollView)root.findViewById(R.id.scrollview);
            }
            ((ViewGroup)root.findViewById(R.id.permission_list)).addView(
                        perms.getPermissionsView(AppSecurityPermissions.WHICH_ALL));
            adapter.addTab(tabHost.newTabSpec(TAB_ID_ALL).setIndicator(
                    getText(R.string.allPerms)), root);
        }
        if (!permVisible) {
            if (mAppInfo != null) {
                // This is an update to an application, but there are no
                // permissions at all.
                msg = (mAppInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        ? R.string.install_confirm_question_update_system_no_perms
                        : R.string.install_confirm_question_update_no_perms;

                findViewById(R.id.spacer).setVisibility(View.VISIBLE);
            } else {
                // This is a new application with no permissions.
                msg = R.string.install_confirm_question_no_perms;
            }
            tabHost.setVisibility(View.INVISIBLE);
            mScrollView = null;
        }
        if (msg != 0) {
            ((TextView)findViewById(R.id.install_confirm_question)).setText(msg);
        }
        mInstallConfirm.setVisibility(View.VISIBLE);
        mOk.setEnabled(true);
        if (mScrollView == null) {
            // There is nothing to scroll view, so the ok button is immediately
            // set to install.
            mOk.setText(R.string.install);
            mOkCanInstall = true;
        } else {
            mScrollView.setFullScrollAction(new Runnable() {
                @Override
                public void run() {
                    mOk.setText(R.string.install);
                    mOkCanInstall = true;
                }
            });
        }
    }

    private void showDialogInner(int id) {
        // TODO better fix for this? Remove dialog so that it gets created again
        removeDialog(id);
        showDialog(id);
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
        case DLG_UNKNOWN_SOURCES:
            return new AlertDialog.Builder(this)
                    .setMessage(R.string.unknown_apps_dlg_text)
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Log.i(TAG, "Finishing off activity so that user can navigate to settings manually");
                            finishAffinity();
                        }})
                    .setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Log.i(TAG, "Launching settings");
                            launchSecuritySettings();
                        }
                    })
                    .setOnCancelListener(this)
                    .create();
        case DLG_ADMIN_RESTRICTS_UNKNOWN_SOURCES:
            return new AlertDialog.Builder(this)
                    .setMessage(R.string.unknown_apps_admin_dlg_text)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setOnCancelListener(this)
                    .create();
        case DLG_PACKAGE_ERROR :
            return new AlertDialog.Builder(this)
                    .setMessage(R.string.Parse_error_dlg_text)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setOnCancelListener(this)
                    .create();
        case DLG_OUT_OF_SPACE:
            // Guaranteed not to be null. will default to package name if not set by app
            CharSequence appTitle = mPm.getApplicationLabel(mPkgInfo.applicationInfo);
            String dlgText = getString(R.string.out_of_space_dlg_text,
                    appTitle.toString());
            return new AlertDialog.Builder(this)
                    .setMessage(dlgText)
                    .setPositiveButton(R.string.manage_applications, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            //launch manage applications
                            Intent intent = new Intent("android.intent.action.MANAGE_PACKAGE_STORAGE");
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Log.i(TAG, "Canceling installation");
                            finish();
                        }
                })
                  .setOnCancelListener(this)
                  .create();
        case DLG_INSTALL_ERROR :
            // Guaranteed not to be null. will default to package name if not set by app
            CharSequence appTitle1 = mPm.getApplicationLabel(mPkgInfo.applicationInfo);
            String dlgText1 = getString(R.string.install_failed_msg,
                    appTitle1.toString());
            return new AlertDialog.Builder(this)
                    .setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setMessage(dlgText1)
                    .setOnCancelListener(this)
                    .create();
        case DLG_NOT_SUPPORTED_ON_WEAR:
            return new AlertDialog.Builder(this)
                    .setMessage(R.string.wear_not_allowed_dlg_text)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setResult(RESULT_OK);
                            clearCachedApkIfNeededAndFinish();
                        }
                    })
                    .setOnCancelListener(this)
                    .create();
       }
       return null;
    }

    private void launchSecuritySettings() {
        Intent launchSettingsIntent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
        startActivityForResult(launchSettingsIntent, REQUEST_ENABLE_UNKNOWN_SOURCES);
    }

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        // If the settings app approved the install we are good to go regardless
        // whether the untrusted sources setting is on. This allows partners to
        // implement a "allow untrusted source once" feature.
        if (request == REQUEST_ENABLE_UNKNOWN_SOURCES && result == RESULT_OK) {
            checkIfAllowedAndInitiateInstall(true);
        } else {
            clearCachedApkIfNeededAndFinish();
        }
    }

    private boolean isInstallRequestFromUnknownSource(Intent intent) {
        String callerPackage = getCallingPackage();
        if (callerPackage != null && intent.getBooleanExtra(
                Intent.EXTRA_NOT_UNKNOWN_SOURCE, false)) {
            try {
                mSourceInfo = mPm.getApplicationInfo(callerPackage, 0);
                if (mSourceInfo != null) {
                    if ((mSourceInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_PRIVILEGED)
                            != 0) {
                        // Privileged apps are not considered an unknown source.
                        return false;
                    }
                }
            } catch (NameNotFoundException e) {
            }
        }

        return true;
    }

    /**
     * @return whether unknown sources is enabled by user in Settings
     */
    private boolean isUnknownSourcesEnabled() {
        return Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.INSTALL_NON_MARKET_APPS, 0) > 0;
    }

    /**
     * @return whether the device admin restricts installation from unknown sources
     */
    private boolean isUnknownSourcesDisallowed() {
        return mUserManager.hasUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
    }

    private void initiateInstall() {
        String pkgName = mPkgInfo.packageName;
        // Check if there is already a package on the device with this name
        // but it has been renamed to something else.
        String[] oldName = mPm.canonicalToCurrentPackageNames(new String[] { pkgName });
        if (oldName != null && oldName.length > 0 && oldName[0] != null) {
            pkgName = oldName[0];
            mPkgInfo.packageName = pkgName;
            mPkgInfo.applicationInfo.packageName = pkgName;
        }
        // Check if package is already installed. display confirmation dialog if replacing pkg
        try {
            // This is a little convoluted because we want to get all uninstalled
            // apps, but this may include apps with just data, and if it is just
            // data we still want to count it as "installed".
            mAppInfo = mPm.getApplicationInfo(pkgName,
                    PackageManager.GET_UNINSTALLED_PACKAGES);
            if ((mAppInfo.flags&ApplicationInfo.FLAG_INSTALLED) == 0) {
                mAppInfo = null;
            }
        } catch (NameNotFoundException e) {
            mAppInfo = null;
        }

        startInstallConfirm();
    }

    void setPmResult(int pmResult) {
        Intent result = new Intent();
        result.putExtra(Intent.EXTRA_INSTALL_RESULT, pmResult);
        setResult(pmResult == PackageManager.INSTALL_SUCCEEDED
                ? RESULT_OK : RESULT_FIRST_USER, result);
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPm = getPackageManager();
        mInstaller = mPm.getPackageInstaller();
        mUserManager = (UserManager) getSystemService(Context.USER_SERVICE);

        final Intent intent = getIntent();
        mOriginatingUid = getOriginatingUid(intent);

        final Uri packageUri;

        if (PackageInstaller.ACTION_CONFIRM_PERMISSIONS.equals(intent.getAction())) {
            final int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            final PackageInstaller.SessionInfo info = mInstaller.getSessionInfo(sessionId);
            if (info == null || !info.sealed || info.resolvedBaseCodePath == null) {
                Log.w(TAG, "Session " + mSessionId + " in funky state; ignoring");
                finish();
                return;
            }

            mSessionId = sessionId;
            packageUri = Uri.fromFile(new File(info.resolvedBaseCodePath));
            mOriginatingURI = null;
            mReferrerURI = null;
        } else {
            mSessionId = -1;
            packageUri = intent.getData();
            mOriginatingURI = intent.getParcelableExtra(Intent.EXTRA_ORIGINATING_URI);
            mReferrerURI = intent.getParcelableExtra(Intent.EXTRA_REFERRER);
        }

        // if there's nothing to do, quietly slip into the ether
        if (packageUri == null) {
            Log.w(TAG, "Unspecified source");
            setPmResult(PackageManager.INSTALL_FAILED_INVALID_URI);
            finish();
            return;
        }

        if (DeviceUtils.isWear(this)) {
            showDialogInner(DLG_NOT_SUPPORTED_ON_WEAR);
            return;
        }

        //set view
        setContentView(R.layout.install_start);
        mInstallConfirm = findViewById(R.id.install_confirm_panel);
        mInstallConfirm.setVisibility(View.INVISIBLE);
        mOk = (Button)findViewById(R.id.ok_button);
        mCancel = (Button)findViewById(R.id.cancel_button);
        mOk.setOnClickListener(this);
        mCancel.setOnClickListener(this);

        boolean wasSetUp = processPackageUri(packageUri);
        if (!wasSetUp) {
            return;
        }

        checkIfAllowedAndInitiateInstall(false);
    }

    /**
     * Check if it is allowed to install the package and initiate install if allowed. If not allowed
     * show the appropriate dialog.
     *
     * @param ignoreUnknownSourcesSettings Ignore {@link #isUnknownSourcesEnabled()} and proceed
     *                                     even if this would prevented installation.
     */
    private void checkIfAllowedAndInitiateInstall(boolean ignoreUnknownSourcesSettings) {
        // Block the install attempt on the Unknown Sources setting if necessary.
        final boolean requestFromUnknownSource = isInstallRequestFromUnknownSource(getIntent());
        if (!requestFromUnknownSource) {
            initiateInstall();
            return;
        }

        // If the admin prohibits it, or we're running in a managed profile, just show error
        // and exit. Otherwise show an option to take the user to Settings to change the setting.
        final boolean isManagedProfile = mUserManager.isManagedProfile();
        if (isUnknownSourcesDisallowed()) {
            if ((mUserManager.getUserRestrictionSource(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                    Process.myUserHandle()) & UserManager.RESTRICTION_SOURCE_SYSTEM) != 0) {
                if (ignoreUnknownSourcesSettings) {
                    initiateInstall();
                } else {
                    showDialogInner(DLG_UNKNOWN_SOURCES);
                }
            } else {
                startActivity(new Intent(Settings.ACTION_SHOW_ADMIN_SUPPORT_DETAILS));
                clearCachedApkIfNeededAndFinish();
            }
        } else if (!isUnknownSourcesEnabled() && isManagedProfile) {
            showDialogInner(DLG_ADMIN_RESTRICTS_UNKNOWN_SOURCES);
        } else if (!isUnknownSourcesEnabled()) {
            if (ignoreUnknownSourcesSettings) {
                initiateInstall();
            } else {
                // Ask user to enable setting first
                showDialogInner(DLG_UNKNOWN_SOURCES);
            }
        } else {
            initiateInstall();
        }
    }

    @Override
    protected void onDestroy() {
        if (mStagingAsynTask != null) {
            mStagingAsynTask.cancel(true);
            mStagingAsynTask = null;
        }
        super.onDestroy();
    }

    /**
     * Parse the Uri and set up the installer for this package.
     *
     * @param packageUri The URI to parse
     *
     * @return {@code true} iff the installer could be set up
     */
    private boolean processPackageUri(final Uri packageUri) {
        mPackageURI = packageUri;

        final String scheme = packageUri.getScheme();
        final PackageUtil.AppSnippet as;

        switch (scheme) {
            case SCHEME_PACKAGE: {
                try {
                    mPkgInfo = mPm.getPackageInfo(packageUri.getSchemeSpecificPart(),
                            PackageManager.GET_PERMISSIONS
                                    | PackageManager.GET_UNINSTALLED_PACKAGES);
                } catch (NameNotFoundException e) {
                }
                if (mPkgInfo == null) {
                    Log.w(TAG, "Requested package " + packageUri.getScheme()
                            + " not available. Discontinuing installation");
                    showDialogInner(DLG_PACKAGE_ERROR);
                    setPmResult(PackageManager.INSTALL_FAILED_INVALID_APK);
                    return false;
                }
                as = new PackageUtil.AppSnippet(mPm.getApplicationLabel(mPkgInfo.applicationInfo),
                        mPm.getApplicationIcon(mPkgInfo.applicationInfo));
            } break;

            case SCHEME_FILE: {
                File sourceFile = new File(packageUri.getPath());
                PackageParser.Package parsed = PackageUtil.getPackageInfo(sourceFile);

                // Check for parse errors
                if (parsed == null) {
                    Log.w(TAG, "Parse error when parsing manifest. Discontinuing installation");
                    showDialogInner(DLG_PACKAGE_ERROR);
                    setPmResult(PackageManager.INSTALL_FAILED_INVALID_APK);
                    return false;
                }
                mPkgInfo = PackageParser.generatePackageInfo(parsed, null,
                        PackageManager.GET_PERMISSIONS, 0, 0, null,
                        new PackageUserState());
                as = PackageUtil.getAppSnippet(this, mPkgInfo.applicationInfo, sourceFile);
            } break;

            case SCHEME_CONTENT: {
                mStagingAsynTask = new StagingAsyncTask();
                mStagingAsynTask.execute(packageUri);
                return false;
            }

            default: {
                Log.w(TAG, "Unsupported scheme " + scheme);
                setPmResult(PackageManager.INSTALL_FAILED_INVALID_URI);
                clearCachedApkIfNeededAndFinish();
                return false;
            }
        }

        PackageUtil.initSnippetForNewApp(this, as, R.id.app_snippet);

        return true;
    }

    /** Get the ApplicationInfo for the calling package, if available */
    private ApplicationInfo getSourceInfo() {
        String callingPackage = getCallingPackage();
        if (callingPackage != null) {
            try {
                return mPm.getApplicationInfo(callingPackage, 0);
            } catch (NameNotFoundException ex) {
                // ignore
            }
        }
        return null;
    }


    /** Get the originating uid if possible, or VerificationParams.NO_UID if not available */
    private int getOriginatingUid(Intent intent) {
        // The originating uid from the intent. We only trust/use this if it comes from a
        // system application
        int uidFromIntent = intent.getIntExtra(Intent.EXTRA_ORIGINATING_UID,
                VerificationParams.NO_UID);

        // Get the source info from the calling package, if available. This will be the
        // definitive calling package, but it only works if the intent was started using
        // startActivityForResult,
        ApplicationInfo sourceInfo = getSourceInfo();
        if (sourceInfo != null) {
            if (uidFromIntent != VerificationParams.NO_UID &&
                    (mSourceInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_PRIVILEGED) != 0) {
                return uidFromIntent;

            }
            // We either didn't get a uid in the intent, or we don't trust it. Use the
            // uid of the calling package instead.
            return sourceInfo.uid;
        }

        // We couldn't get the specific calling package. Let's get the uid instead
        int callingUid;
        try {
            callingUid = ActivityManagerNative.getDefault()
                    .getLaunchedFromUid(getActivityToken());
        } catch (android.os.RemoteException ex) {
            Log.w(TAG, "Could not determine the launching uid.");
            // nothing else we can do
            return VerificationParams.NO_UID;
        }

        // If we got a uid from the intent, we need to verify that the caller is a
        // privileged system package before we use it
        if (uidFromIntent != VerificationParams.NO_UID) {
            String[] callingPackages = mPm.getPackagesForUid(callingUid);
            if (callingPackages != null) {
                for (String packageName: callingPackages) {
                    try {
                        ApplicationInfo applicationInfo =
                                mPm.getApplicationInfo(packageName, 0);

                        if ((applicationInfo.privateFlags & ApplicationInfo.PRIVATE_FLAG_PRIVILEGED)
                                != 0) {
                            return uidFromIntent;
                        }
                    } catch (NameNotFoundException ex) {
                        // ignore it, and try the next package
                    }
                }
            }
        }
        // We either didn't get a uid from the intent, or we don't trust it. Use the
        // calling uid instead.
        return callingUid;
    }

    @Override
    public void onBackPressed() {
        if (mSessionId != -1) {
            mInstaller.setPermissionsResult(mSessionId, false);
        }
        super.onBackPressed();
    }

    // Generic handling when pressing back key
    public void onCancel(DialogInterface dialog) {
        clearCachedApkIfNeededAndFinish();
    }

    public void onClick(View v) {
        if (v == mOk) {
            if (mOkCanInstall || mScrollView == null) {
                if (mSessionId != -1) {
                    mInstaller.setPermissionsResult(mSessionId, true);
                    clearCachedApkIfNeededAndFinish();
                } else {
                    startInstall();
                }
            } else {
                mScrollView.pageScroll(View.FOCUS_DOWN);
            }
        } else if (v == mCancel) {
            // Cancel and finish
            setResult(RESULT_CANCELED);
            if (mSessionId != -1) {
                mInstaller.setPermissionsResult(mSessionId, false);
            }
            clearCachedApkIfNeededAndFinish();
        }
    }

    private void startInstall() {
        // Start subactivity to actually install the application
        Intent newIntent = new Intent();
        newIntent.putExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO,
                mPkgInfo.applicationInfo);
        newIntent.setData(mPackageURI);
        newIntent.setClass(this, InstallAppProgress.class);
        String installerPackageName = getIntent().getStringExtra(
                Intent.EXTRA_INSTALLER_PACKAGE_NAME);
        if (mOriginatingURI != null) {
            newIntent.putExtra(Intent.EXTRA_ORIGINATING_URI, mOriginatingURI);
        }
        if (mReferrerURI != null) {
            newIntent.putExtra(Intent.EXTRA_REFERRER, mReferrerURI);
        }
        if (mOriginatingUid != VerificationParams.NO_UID) {
            newIntent.putExtra(Intent.EXTRA_ORIGINATING_UID, mOriginatingUid);
        }
        if (installerPackageName != null) {
            newIntent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME,
                    installerPackageName);
        }
        if (getIntent().getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false)) {
            newIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            newIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        }
        if(localLOGV) Log.i(TAG, "downloaded app uri="+mPackageURI);
        startActivity(newIntent);
        finish();
    }

    private void clearCachedApkIfNeededAndFinish() {
        if (mContentUriApkStagingFile != null) {
            mContentUriApkStagingFile.delete();
            mContentUriApkStagingFile = null;
        }
        finish();
    }

    private final class StagingAsyncTask extends AsyncTask<Uri, Void, File> {
        private static final long SHOW_EMPTY_STATE_DELAY_MILLIS = 300;

        private final Runnable mEmptyStateRunnable = new Runnable() {
            @Override
            public void run() {
                ((TextView) findViewById(R.id.app_name)).setText(R.string.app_name_unknown);
                ((TextView) findViewById(R.id.install_confirm_question))
                        .setText(R.string.message_staging);
                mInstallConfirm.setVisibility(View.VISIBLE);
                findViewById(android.R.id.tabhost).setVisibility(View.INVISIBLE);
                findViewById(R.id.spacer).setVisibility(View.VISIBLE);
                findViewById(R.id.ok_button).setEnabled(false);
                Drawable icon = getDrawable(R.drawable.ic_file_download);
                Utils.applyTint(PackageInstallerActivity.this,
                        icon, android.R.attr.colorControlNormal);
                ((ImageView) findViewById(R.id.app_icon)).setImageDrawable(icon);
            }
        };

        @Override
        protected void onPreExecute() {
            getWindow().getDecorView().postDelayed(mEmptyStateRunnable,
                    SHOW_EMPTY_STATE_DELAY_MILLIS);
        }

        @Override
        protected File doInBackground(Uri... params) {
            if (params == null || params.length <= 0) {
                return null;
            }
            Uri packageUri = params[0];
            File sourceFile = null;
            try {
                sourceFile = File.createTempFile("package", ".apk", getCacheDir());
                try (
                    InputStream in = getContentResolver().openInputStream(packageUri);
                    OutputStream out = (in != null) ? new FileOutputStream(
                            sourceFile) : null;
                ) {
                    // Despite the comments in ContentResolver#openInputStream
                    // the returned stream can be null.
                    if (in == null) {
                        return null;
                    }
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) >= 0) {
                        // Be nice and respond to a cancellation
                        if (isCancelled()) {
                            return null;
                        }
                        out.write(buffer, 0, bytesRead);
                    }
                }
            } catch (IOException ioe) {
                Log.w(TAG, "Error staging apk from content URI", ioe);
                if (sourceFile != null) {
                    sourceFile.delete();
                }
            }
            return sourceFile;
        }

        @Override
        protected void onPostExecute(File file) {
            getWindow().getDecorView().removeCallbacks(mEmptyStateRunnable);
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (file == null) {
                showDialogInner(DLG_PACKAGE_ERROR);
                setPmResult(PackageManager.INSTALL_FAILED_INVALID_APK);
                return;
            }
            mContentUriApkStagingFile = file;
            Uri fileUri = Uri.fromFile(file);

            boolean wasSetUp = processPackageUri(fileUri);
            if (wasSetUp) {
                checkIfAllowedAndInitiateInstall(false);
            }
        }

        @Override
        protected void onCancelled(File file) {
            getWindow().getDecorView().removeCallbacks(mEmptyStateRunnable);
        }
    };
}
