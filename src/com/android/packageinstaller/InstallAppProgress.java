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
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * This activity corresponds to a download progress screen that is displayed 
 * when the user tries
 * to install an application bundled as an apk file. The result of the application install
 * is indicated in the result code that gets set to the corresponding installation status
 * codes defined in PackageManager. If the package being installed already exists,
 * the existing package is replaced with the new one.
 */
public class InstallAppProgress extends Activity {
    private final String TAG="InstallAppProgress";
    private boolean localLOGV = false;
    private ApplicationInfo mAppInfo;
    private Uri mPackageURI;
    private ProgressBar mProgressBar;
    private final int INSTALL_COMPLETE = 1;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case INSTALL_COMPLETE:
                    //finish the activity posting result
                    setResultAndFinish(msg.arg1);
                    break;
                default:
                    break;
            }
        }
    };
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        mAppInfo = intent.getParcelableExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO);
        mPackageURI = intent.getData();
        initView();
    }
    
    class PackageInstallObserver extends IPackageInstallObserver.Stub {
        public void packageInstalled(String packageName, int returnCode) {
            Message msg = mHandler.obtainMessage(INSTALL_COMPLETE);
            msg.arg1 = returnCode;
            mHandler.sendMessage(msg);
        }
    }
    
    void setResultAndFinish(int retCode) {
        Intent data = new Intent();
        setResult(retCode);
        finish();
    }
    
    public void initView() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        String unknown =  getString(R.string.unknown);
        setContentView(R.layout.op_progress);
        //initialize views
        PackageUtil.initAppSnippet(this, mAppInfo, R.id.app_snippet);
        TextView installTextView = (TextView)findViewById(R.id.center_text);
        installTextView.setText(R.string.installing);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mProgressBar.setIndeterminate(true);
        // Set flag to replace package if already existing
        int installFlags = 0;
        PackageManager pm = getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(mAppInfo.packageName, 
                    PackageManager.GET_UNINSTALLED_PACKAGES);
            if(pi != null) {
                installFlags |= PackageManager.REPLACE_EXISTING_PACKAGE;
            }
        } catch (NameNotFoundException e) {
        }
        if((installFlags & PackageManager.REPLACE_EXISTING_PACKAGE )!= 0) {
            Log.w(TAG, "Replacing package:"+mAppInfo.packageName);
        }
        PackageInstallObserver observer = new PackageInstallObserver();
        pm.installPackage(mPackageURI, observer, installFlags);
    }
}
