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
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.PermissionInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.Window;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageParser.Package;

public class PackageInstallerActivity extends Activity {
    private static final int INSTALL_INITIAL=0;
    private static final int INSTALL_CONFIRM=1;
    private static final int INSTALL_PROGRESS=2;
    private static final int INSTALL_DONE=3;
    private static final String TAG = "PackageInstaller";
    private Uri mPackageURI;    
    private boolean localLOGV = true;
    private int mCurrentState = INSTALL_INITIAL;
    PackageManager mPm;
    private Package mPkgInfo;
    private File mTmpFile;
    private Uri mPackageUri;
    private static final int DELETE_COMPLETE=1;
    private static final int SUCCEEDED=1;
    private static final int FAILED=0;
    private static final int FREE_SPACE = 2;
    private Handler mHandler = new Handler() {
        public static final String TAG = "PackageInstallerActivity.Handler";
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DELETE_COMPLETE:
                    //finish the activity posting result
                    startInstallConfirm();
                    break;
                case FREE_SPACE:
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
                   replacePackage();
                }})
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Log.i(TAG, "Canceling installation");
                    finish();
                }})
                .setCancelable(false)
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
            }})
            .setCancelable(false)
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
            }})
        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Log.i(TAG, "Canceling installation");
                finish();
            }})
            .setCancelable(false)
        .show();
    }

    private class PkgDataObserver extends  IPackageDataObserver.Stub {
        public void onRemoveCompleted(String packageName, boolean succeeded)
                throws RemoteException {
            Message msg = mHandler.obtainMessage(FREE_SPACE);
            msg.arg1 = succeeded?SUCCEEDED:FAILED;
            mHandler.sendMessage(msg);
        }
        
    }
    
    private void checkOutOfSpace(long size) {
        if(localLOGV) Log.i(TAG, "Checking for "+size+" number of bytes");
        PkgDataObserver observer = new PkgDataObserver();
        mPm.freeApplicationCache(size, observer);
    }

    private void launchSettingsAppAndFinish() {
        //Create an intent to launch SettingsTwo activity
        Intent launchSettingsIntent = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
        startActivity(launchSettingsIntent);
        finish();
    }
    
    private boolean isInstallingUnknownAppsAllowed() {
        return Settings.System.getInt(getContentResolver(), 
                Settings.System.INSTALL_NON_MARKET_APPS, 0) > 0;
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
        //check out of space condition. display dialog if necessary
        if(PackageUtil.isPackageAlreadyInstalled(this, mPkgInfo.applicationInfo.packageName)) {
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
        if(mPkgInfo == null) {
            Log.w(TAG, "Parse error when parsing manifest. Discontinuing installation");
            finish();
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
    
    class PackageDeleteObserver extends IPackageDeleteObserver.Stub {
        public void packageDeleted(boolean succeeded) throws RemoteException {
            Message msg = mHandler.obtainMessage(DELETE_COMPLETE);
            msg.arg1 = succeeded?SUCCEEDED:FAILED;
            mHandler.sendMessage(msg);
        }
    }
    
    
    void replacePackage() {
        PackageDeleteObserver observer = new PackageDeleteObserver();
        mPm.deletePackage(mPkgInfo.applicationInfo.packageName, observer, 
                PackageManager.DONT_DELETE_DATA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        boolean finish = true;
        switch(requestCode) {
        case INSTALL_CONFIRM:
            if(resultCode == RESULT_OK) {
                finish = false;
                mCurrentState = INSTALL_CONFIRM;
                startInstallProgress();
            }
            break;
        case INSTALL_PROGRESS:
            boolean ok = false;
            finish = false;
            mCurrentState = INSTALL_DONE;
            if(resultCode == PackageManager.INSTALL_SUCCEEDED) {
                ok = true;
            }
            //start the next screen to show final status of installation
            startInstallDone(ok);
            break;
        case INSTALL_DONE:
            //neednt check for result code here
            break;
        default:
            break;
        }
        if(finish) {
            if(mTmpFile != null) {
                deleteFile(mTmpFile.getName());
            }
            //finish off this activity to return to the previous activity that launched it
            Log.i(TAG, "Finishing off activity");
            finish();
        }
    }
}
