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
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDeleteObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Window;
import android.view.ViewDebug;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * This activity corresponds to a download progress screen that is displayed 
 * when an application is uninstalled. The result of the application uninstall
 * is indicated in the result code that gets set to 0 or 1. The application gets launched
 * by an intent with the intent's class name explicitly set to UninstallAppProgress and expects
 * the application object of the application to uninstall.
 */
public class UninstallAppProgress extends Activity {
    private final String TAG="UninstallAppProgress";
    private boolean localLOGV = false;
    private ApplicationInfo mAppInfo;
    private final int UNINSTALL_COMPLETE = 1;
    public final static int SUCCEEDED=1;
    public final static int FAILED=0;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UNINSTALL_COMPLETE:
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
        initView();
    }
    
    class PackageDeleteObserver extends IPackageDeleteObserver.Stub {
        public void packageDeleted(boolean succeeded) {
            Message msg = mHandler.obtainMessage(UNINSTALL_COMPLETE);
            msg.arg1 = succeeded?SUCCEEDED:FAILED;
            mHandler.sendMessage(msg);
        }
    }
    
    void setResultAndFinish(int retCode) {
        setResult(retCode);
        finish();
    }
    
    public void initView() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.op_progress);
        // Initialize views
        PackageUtil.initSnippetForInstalledApp(this, mAppInfo, R.id.app_snippet);
        TextView installTextView = (TextView)findViewById(R.id.center_text);
        installTextView.setText(R.string.uninstalling);
        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        progressBar.setIndeterminate(true);
        PackageDeleteObserver observer = new PackageDeleteObserver();
        getPackageManager().deletePackage(mAppInfo.packageName, observer, 0);
    }
}
