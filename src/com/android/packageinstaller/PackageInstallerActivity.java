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

import com.android.packageinstaller.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnCancelListener;
import android.content.pm.IPackageDataObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;

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
public class PackageInstallerActivity extends Activity implements OnCancelListener {
    private static final int INSTALL_INITIAL=0;
    private static final int INSTALL_CONFIRM=1;
    private static final int INSTALL_PROGRESS=2;
    private static final int INSTALL_DONE=3;
    private static final String TAG = "PackageInstaller";
    private Uri mPackageURI;    
    private boolean localLOGV = false;
    private int mCurrentState = INSTALL_INITIAL;
    PackageManager mPm;
    private PackageParser.Package mPkgInfo;
    private File mTmpFile;
    private Uri mPackageUri;
    private static final int SUCCEEDED=1;
    private static final int FAILED=0;
    // Broadcast receiver for clearing cache
    ClearCacheReceiver mClearCacheReceiver;
    private static final int HANDLER_BASE_MSG_IDX = 0;
    private static final int FREE_SPACE = HANDLER_BASE_MSG_IDX+1;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FREE_SPACE:
                    unregisterReceiver(mClearCacheReceiver);
                    if(msg.arg1 == SUCCEEDED) {
                        makeTempCopyAndInstall();
                    } else {
                        displayOutOfSpaceDialog();
                    }
                    break;
                default:
                    break;
            }
        }
    };
   
    private void startInstallActivityClass(int requestCode, Class<?> cls) {
        Intent newIntent = new Intent();
        startInstallActivityClass(newIntent, requestCode, cls);
    }
    private void startInstallActivityClass(Intent newIntent, int requestCode, Class<?> cls) {
        newIntent.putExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO, 
                                                  mPkgInfo.applicationInfo);
        newIntent.setData(mPackageURI);
        newIntent.setClass(this, cls);
        if(localLOGV) Log.i(TAG, "downloaded app uri="+mPackageURI);
        startActivityForResult(newIntent, requestCode);
    }
    
 
    private void startInstallConfirm() {
        Intent newIntent = new Intent();
        newIntent.putExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO, 
                mPkgInfo.applicationInfo);
        newIntent.setData(mPackageURI);
        newIntent.setClass(this, InstallAppConfirmation.class);
        startActivityForResult(newIntent, INSTALL_CONFIRM);
    }
    
    private void startInstallProgress() {
        startInstallActivityClass(INSTALL_PROGRESS, InstallAppProgress.class);
    }
    
    private void startInstallDone(boolean result) {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.putExtra(PackageUtil.INTENT_ATTR_INSTALL_STATUS, result);
        startInstallActivityClass(newIntent, INSTALL_DONE, InstallAppDone.class);
    }
    
    private void displayReplaceAppDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.dlg_app_replacement_title)
            .setMessage(R.string.dlg_app_replacement_statement)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    startInstallConfirm();
                }})
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Log.i(TAG, "Canceling installation");
                    finish();
                }})
            .setOnCancelListener(this)
            .show();
    }
    
    
    /*
     * Utility method to display a dialog prompting the user to turn on settings property
     * before installing application
     */
    private void displayUnknowAppsDialog() {        
        new AlertDialog.Builder(this)
            .setTitle(R.string.unknown_apps_dlg_title)
            .setMessage(R.string.unknown_apps_dlg_text)
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Log.i(TAG, "Finishing off activity so that user can navigate to settings manually");
                    finish();
            }})
            .setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Log.i(TAG, "Launching settings");
                    launchSettingsAppAndFinish();
                }
            })
            .setOnCancelListener(this)
            .show();
    }
    
    /*
     * Utility method to display a dialog indicating a parse error when parsing the package
     */
    private void displayPackageErrorDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.Parse_error_dlg_title)
            .setMessage(R.string.Parse_error_dlg_text)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            })
            .setOnCancelListener(this)
            .show();
    }    
    
    /*
     * Utility method to display dialog indicating out of disk space
     */
    private void displayOutOfSpaceDialog() {      
        //guaranteed not to be null. will default to package name if not set by app
        CharSequence appTitle = mPm.getApplicationLabel(mPkgInfo.applicationInfo);
        String dlgText = getString(R.string.out_of_space_dlg_text, 
                                                          appTitle.toString());
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.out_of_space_dlg_title)
            .setMessage(dlgText)
            .setPositiveButton(R.string.manage_applications, new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int which) {
                   //launch manage applications
                   Intent intent = new Intent("android.intent.action.MANAGE_PACKAGE_STORAGE");
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
            .show();
    }

    private class PkgDataObserver extends  IPackageDataObserver.Stub {
        public void onRemoveCompleted(String packageName, boolean succeeded) {
            Message msg = mHandler.obtainMessage(FREE_SPACE);
            msg.arg1 = succeeded?SUCCEEDED:FAILED;
            mHandler.sendMessage(msg);
        }
    }
    
    private class ClearCacheReceiver extends BroadcastReceiver {
        public static final String INTENT_CLEAR_CACHE = 
                "com.android.packageinstaller.CLEAR_CACHE";
        @Override
        public void onReceive(Context context, Intent intent) {
            Message msg = mHandler.obtainMessage(FREE_SPACE);
            msg.arg1 = (getResultCode() ==1) ? SUCCEEDED : FAILED;
            mHandler.sendMessage(msg);
        }
    }
    
    private void checkOutOfSpace(long size) {
        if(localLOGV) Log.i(TAG, "Checking for "+size+" number of bytes");
        if (mClearCacheReceiver == null) {
            mClearCacheReceiver = new ClearCacheReceiver();
        }
        registerReceiver(mClearCacheReceiver,
                new IntentFilter(ClearCacheReceiver.INTENT_CLEAR_CACHE));
        PendingIntent pi = PendingIntent.getBroadcast(this,
                0,  new Intent(ClearCacheReceiver.INTENT_CLEAR_CACHE), 0);
        mPm.freeStorage(size, pi);
    }

    private void launchSettingsAppAndFinish() {
        //Create an intent to launch SettingsTwo activity
        Intent launchSettingsIntent = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
        startActivity(launchSettingsIntent);
        finish();
    }
    
    private boolean isInstallingUnknownAppsAllowed() {
        return Settings.Secure.getInt(getContentResolver(), 
            Settings.Secure.INSTALL_NON_MARKET_APPS, 0) > 0;
    }
    
    private File createTempPackageFile(String filePath) {
        File tmpPackageFile;
        int i = filePath.lastIndexOf("/");
        String tmpFileName;
        if(i != -1) {
            tmpFileName = filePath.substring(i+1);
        } else {
            tmpFileName = filePath;
        }
        FileOutputStream fos;
        try {
            fos=openFileOutput(tmpFileName, MODE_WORLD_READABLE);
        } catch (FileNotFoundException e1) {
            Log.e(TAG, "Error opening file "+tmpFileName);
            return null;
        }
        try {
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "Error opening file "+tmpFileName);
            return null;
        }
        tmpPackageFile=getFileStreamPath(tmpFileName);
        File srcPackageFile = new File(filePath);
        if (!FileUtils.copyFile(srcPackageFile, tmpPackageFile)) {
            return null;
        }
        return tmpPackageFile;
    }
    
    private void makeTempCopyAndInstall() {
        //copy file to tmp dir
        mTmpFile = createTempPackageFile(mPackageURI.getPath());
        if(mTmpFile == null) {
            //display a dialog
            Log.e(TAG, "Error copying file locally. Failed Installation");
            displayOutOfSpaceDialog();
            return;
        }
        mPackageURI = Uri.parse("file://"+mTmpFile.getPath());
        // Check if package is already installed. display confirmation dialog if replacing pkg
        boolean alreadyInstalled = PackageUtil.isPackageAlreadyInstalled(this,
                mPkgInfo.applicationInfo.packageName);
        if(alreadyInstalled) {
            if(localLOGV) Log.i(TAG, "Replacing existing package:"+
                    mPkgInfo.applicationInfo.packageName);
            displayReplaceAppDialog();
        } else {
            startInstallConfirm();
        }
    }
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        //get intent information
        final Intent intent = getIntent();
        mPackageURI = intent.getData();
        mPkgInfo = PackageUtil.getPackageInfo(mPackageURI);
        // Check for parse errors
        if(mPkgInfo == null) {
            Log.w(TAG, "Parse error when parsing manifest. Discontinuing installation");
            displayPackageErrorDialog();
            return;
        }
        mPm = getPackageManager();
        //set view
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.install_start);
        PackageUtil.initAppSnippet(this, mPkgInfo.applicationInfo, R.id.app_snippet);
       //check setting
        if(!isInstallingUnknownAppsAllowed()) {
            //ask user to enable setting first
            displayUnknowAppsDialog();
            return;
        }
        //compute the size of the application. just an estimate
        long size;
        String apkPath = mPackageURI.getPath();
        File apkFile = new File(apkPath);
        //TODO? DEVISE BETTER HEAURISTIC
        size = 4*apkFile.length();
        checkOutOfSpace(size);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Delete the temporary file if it still exists
        if (mTmpFile != null) {
            deleteFile(mTmpFile.getName());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        boolean finish = true;
        boolean removeTmpFile = false;
        switch(requestCode) {
        case INSTALL_CONFIRM:
            if (resultCode == RESULT_OK) {
                finish = false;
                mCurrentState = INSTALL_PROGRESS;
                startInstallProgress();
            } else {
                removeTmpFile = true;
            }
            break;
        case INSTALL_PROGRESS:
            boolean ok = false;
            finish = false;
            mCurrentState = INSTALL_DONE;
            if (resultCode == PackageManager.INSTALL_SUCCEEDED) {
                ok = true;
            }
            // Now that the package is installed just delete the temp file
            removeTmpFile = true;
            //start the next screen to show final status of installation
            startInstallDone(ok);
            break;
        case INSTALL_DONE:
            //neednt check for result code here
            break;
        default:
            break;
        }
        if ((removeTmpFile) && (mTmpFile != null)) {
            deleteFile(mTmpFile.getName());
        }
        if (finish) {
            //finish off this activity to return to the previous activity that launched it
            if (localLOGV) Log.i(TAG, "Finishing off activity");
            finish();
        }
    }
    
    // Generic handling when pressing back key
    public void onCancel(DialogInterface dialog) {
        finish();
    }
}
