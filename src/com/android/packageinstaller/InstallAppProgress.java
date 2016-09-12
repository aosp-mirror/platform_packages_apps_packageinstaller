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

import static android.content.pm.PackageInstaller.SessionParams.UID_UNKNOWN;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageParser;
import android.content.pm.PackageParser.PackageLite;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.android.packageinstaller.permission.utils.IoUtils;

import com.android.internal.content.PackageHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * This activity corresponds to a download progress screen that is displayed 
 * when the user tries
 * to install an application bundled as an apk file. The result of the application install
 * is indicated in the result code that gets set to the corresponding installation status
 * codes defined in PackageManager. If the package being installed already exists,
 * the existing package is replaced with the new one.
 */
public class InstallAppProgress extends Activity implements View.OnClickListener, OnCancelListener {
    private final String TAG="InstallAppProgress";
    private static final String BROADCAST_ACTION =
            "com.android.packageinstaller.ACTION_INSTALL_COMMIT";
    private static final String BROADCAST_SENDER_PERMISSION =
            "android.permission.INSTALL_PACKAGES";
    private ApplicationInfo mAppInfo;
    private Uri mPackageURI;
    private ProgressBar mProgressBar;
    private View mOkPanel;
    private TextView mStatusTextView;
    private TextView mExplanationTextView;
    private Button mDoneButton;
    private Button mLaunchButton;
    private final int INSTALL_COMPLETE = 1;
    private Intent mLaunchIntent;
    private static final int DLG_OUT_OF_SPACE = 1;
    private CharSequence mLabel;
    private HandlerThread mInstallThread;
    private Handler mInstallHandler;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case INSTALL_COMPLETE:
                    if (getIntent().getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false)) {
                        Intent result = new Intent();
                        result.putExtra(Intent.EXTRA_INSTALL_RESULT, msg.arg1);
                        setResult(msg.arg1 == PackageInstaller.STATUS_SUCCESS
                                ? Activity.RESULT_OK : Activity.RESULT_FIRST_USER,
                                        result);
                        clearCachedApkIfNeededAndFinish();
                        return;
                    }
                    // Update the status text
                    mProgressBar.setVisibility(View.GONE);
                    // Show the ok button
                    int centerTextLabel;
                    int centerExplanationLabel = -1;
                    if (msg.arg1 == PackageInstaller.STATUS_SUCCESS) {
                        mLaunchButton.setVisibility(View.VISIBLE);
                        ((ImageView)findViewById(R.id.center_icon))
                                .setImageDrawable(getDrawable(R.drawable.ic_done_92));
                        centerTextLabel = R.string.install_done;
                        // Enable or disable launch button
                        mLaunchIntent = getPackageManager().getLaunchIntentForPackage(
                                mAppInfo.packageName);
                        boolean enabled = false;
                        if(mLaunchIntent != null) {
                            List<ResolveInfo> list = getPackageManager().
                                    queryIntentActivities(mLaunchIntent, 0);
                            if (list != null && list.size() > 0) {
                                enabled = true;
                            }
                        }
                        if (enabled) {
                            mLaunchButton.setOnClickListener(InstallAppProgress.this);
                        } else {
                            mLaunchButton.setEnabled(false);
                        }
                    } else if (msg.arg1 == PackageInstaller.STATUS_FAILURE_STORAGE){
                        showDialogInner(DLG_OUT_OF_SPACE);
                        return;
                    } else {
                        // Generic error handling for all other error codes.
                        ((ImageView)findViewById(R.id.center_icon))
                                .setImageDrawable(getDrawable(R.drawable.ic_report_problem_92));
                        centerExplanationLabel = getExplanationFromErrorCode(msg.arg1);
                        centerTextLabel = R.string.install_failed;
                        mLaunchButton.setVisibility(View.GONE);
                    }
                    if (centerExplanationLabel != -1) {
                        mExplanationTextView.setText(centerExplanationLabel);
                        findViewById(R.id.center_view).setVisibility(View.GONE);
                        ((TextView)findViewById(R.id.explanation_status)).setText(centerTextLabel);
                        findViewById(R.id.explanation_view).setVisibility(View.VISIBLE);
                    } else {
                        ((TextView)findViewById(R.id.center_text)).setText(centerTextLabel);
                        findViewById(R.id.center_view).setVisibility(View.VISIBLE);
                        findViewById(R.id.explanation_view).setVisibility(View.GONE);
                    }
                    mDoneButton.setOnClickListener(InstallAppProgress.this);
                    mOkPanel.setVisibility(View.VISIBLE);
                    break;
                default:
                    break;
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final int statusCode = intent.getIntExtra(
                    PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (statusCode == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                context.startActivity((Intent)intent.getParcelableExtra(Intent.EXTRA_INTENT));
            } else {
                onPackageInstalled(statusCode);
            }
        }
    };

    private int getExplanationFromErrorCode(int errCode) {
        Log.d(TAG, "Installation error code: " + errCode);
        switch (errCode) {
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                return R.string.install_failed_blocked;
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return R.string.install_failed_conflict;
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return R.string.install_failed_incompatible;
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return R.string.install_failed_invalid_apk;
            default:
                return -1;
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        mAppInfo = intent.getParcelableExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO);
        mPackageURI = intent.getData();

        final String scheme = mPackageURI.getScheme();
        if (scheme != null && !"file".equals(scheme) && !"package".equals(scheme)) {
            throw new IllegalArgumentException("unexpected scheme " + scheme);
        }

        mInstallThread = new HandlerThread("InstallThread");
        mInstallThread.start();
        mInstallHandler = new Handler(mInstallThread.getLooper());

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BROADCAST_ACTION);
        registerReceiver(
                mBroadcastReceiver, intentFilter, BROADCAST_SENDER_PERMISSION, null /*scheduler*/);

        initView();
    }

    @Override
    public void onBackPressed() {
        clearCachedApkIfNeededAndFinish();
    }

    @SuppressWarnings("deprecation")
    @Override
    public Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
        case DLG_OUT_OF_SPACE:
            String dlgText = getString(R.string.out_of_space_dlg_text, mLabel);
            return new AlertDialog.Builder(this)
                    .setTitle(R.string.out_of_space_dlg_title)
                    .setMessage(dlgText)
                    .setPositiveButton(R.string.manage_applications, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            //launch manage applications
                            Intent intent = new Intent("android.intent.action.MANAGE_PACKAGE_STORAGE");
                            startActivity(intent);
                            clearCachedApkIfNeededAndFinish();
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Log.i(TAG, "Canceling installation");
                            clearCachedApkIfNeededAndFinish();
                        }
                    })
                    .setOnCancelListener(this)
                    .create();
        }
       return null;
   }

    @SuppressWarnings("deprecation")
    private void showDialogInner(int id) {
        removeDialog(id);
        showDialog(id);
    }

    void onPackageInstalled(int statusCode) {
        Message msg = mHandler.obtainMessage(INSTALL_COMPLETE);
        msg.arg1 = statusCode;
        mHandler.sendMessage(msg);
    }

    int getInstallFlags(String packageName) {
        PackageManager pm = getPackageManager();
        try {
            PackageInfo pi =
                    pm.getPackageInfo(packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
            if (pi != null) {
                return PackageManager.INSTALL_REPLACE_EXISTING;
            }
        } catch (NameNotFoundException e) {
        }
        return 0;
    }

    private void doPackageStage(PackageManager pm, PackageInstaller.SessionParams params) {
        final PackageInstaller packageInstaller = pm.getPackageInstaller();
        PackageInstaller.Session session = null;
        try {
            final String packageLocation = mPackageURI.getPath();
            final File file = new File(packageLocation);
            final int sessionId = packageInstaller.createSession(params);
            final byte[] buffer = new byte[65536];

            session = packageInstaller.openSession(sessionId);

            final InputStream in = new FileInputStream(file);
            final long sizeBytes = file.length();
            final OutputStream out = session.openWrite("PackageInstaller", 0, sizeBytes);
            try {
                int c;
                while ((c = in.read(buffer)) != -1) {
                    out.write(buffer, 0, c);
                    if (sizeBytes > 0) {
                        final float fraction = ((float) c / (float) sizeBytes);
                        session.addProgress(fraction);
                    }
                }
                session.fsync(out);
            } finally {
                IoUtils.closeQuietly(in);
                IoUtils.closeQuietly(out);
            }

            // Create a PendingIntent and use it to generate the IntentSender
            Intent broadcastIntent = new Intent(BROADCAST_ACTION);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    InstallAppProgress.this /*context*/,
                    sessionId,
                    broadcastIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(pendingIntent.getIntentSender());
        } catch (IOException e) {
            onPackageInstalled(PackageInstaller.STATUS_FAILURE);
        } finally {
            IoUtils.closeQuietly(session);
        }
    }

    void initView() {
        setContentView(R.layout.op_progress);

        final PackageUtil.AppSnippet as;
        final PackageManager pm = getPackageManager();
        final int installFlags = getInstallFlags(mAppInfo.packageName);

        if((installFlags & PackageManager.INSTALL_REPLACE_EXISTING )!= 0) {
            Log.w(TAG, "Replacing package:" + mAppInfo.packageName);
        }
        if ("package".equals(mPackageURI.getScheme())) {
            as = new PackageUtil.AppSnippet(pm.getApplicationLabel(mAppInfo),
                    pm.getApplicationIcon(mAppInfo));
        } else {
            final File sourceFile = new File(mPackageURI.getPath());
            as = PackageUtil.getAppSnippet(this, mAppInfo, sourceFile);
        }
        mLabel = as.label;
        PackageUtil.initSnippetForNewApp(this, as, R.id.app_snippet);
        mStatusTextView = (TextView)findViewById(R.id.center_text);
        mExplanationTextView = (TextView) findViewById(R.id.explanation);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mProgressBar.setIndeterminate(true);
        // Hide button till progress is being displayed
        mOkPanel = findViewById(R.id.buttons_panel);
        mDoneButton = (Button)findViewById(R.id.done_button);
        mLaunchButton = (Button)findViewById(R.id.launch_button);
        mOkPanel.setVisibility(View.INVISIBLE);

        if ("package".equals(mPackageURI.getScheme())) {
            try {
                pm.installExistingPackage(mAppInfo.packageName);
                onPackageInstalled(PackageInstaller.STATUS_SUCCESS);
            } catch (PackageManager.NameNotFoundException e) {
                onPackageInstalled(PackageInstaller.STATUS_FAILURE_INVALID);
            }
        } else {
            final PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.referrerUri = getIntent().getParcelableExtra(Intent.EXTRA_REFERRER);
            params.originatingUri = getIntent().getParcelableExtra(Intent.EXTRA_ORIGINATING_URI);
            params.originatingUid = getIntent().getIntExtra(Intent.EXTRA_ORIGINATING_UID,
                    UID_UNKNOWN);

            File file = new File(mPackageURI.getPath());
            try {
                PackageLite pkg = PackageParser.parsePackageLite(file, 0);
                params.setAppPackageName(pkg.packageName);
                params.setInstallLocation(pkg.installLocation);
                params.setSize(
                    PackageHelper.calculateInstalledSize(pkg, false, params.abiOverride));
            } catch (PackageParser.PackageParserException e) {
                Log.e(TAG, "Cannot parse package " + file + ". Assuming defaults.");
                Log.e(TAG, "Cannot calculate installed size " + file + ". Try only apk size.");
                params.setSize(file.length());
            } catch (IOException e) {
                Log.e(TAG, "Cannot calculate installed size " + file + ". Try only apk size.");
                params.setSize(file.length());
            }

            mInstallHandler.post(new Runnable() {
                @Override
                public void run() {
                    doPackageStage(pm, params);
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
        mInstallThread.getLooper().quitSafely();
    }

    public void onClick(View v) {
        if(v == mDoneButton) {
            if (mAppInfo.packageName != null) {
                Log.i(TAG, "Finished installing "+mAppInfo.packageName);
            }
            clearCachedApkIfNeededAndFinish();
        } else if(v == mLaunchButton) {
            try {
                startActivity(mLaunchIntent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Could not start activity", e);
            }
            clearCachedApkIfNeededAndFinish();
        }
    }

    public void onCancel(DialogInterface dialog) {
        clearCachedApkIfNeededAndFinish();
    }

    private void clearCachedApkIfNeededAndFinish() {
        // If we are installing from a content:// the apk is copied in the cache
        // dir and passed in here. As we aren't started for a result because our
        // caller needs to be able to forward the result, here we make sure the
        // staging file in the cache dir is removed.
        if ("file".equals(mPackageURI.getScheme()) && mPackageURI.getPath() != null
                && mPackageURI.getPath().startsWith(getCacheDir().toString())) {
            File file = new File(mPackageURI.getPath());
            file.delete();
        }
        finish();
    }
}
