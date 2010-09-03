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
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * This activity corresponds to a download progress screen that is displayed 
 * when an application is uninstalled. The result of the application uninstall
 * is indicated in the result code that gets set to 0 or 1. The application gets launched
 * by an intent with the intent's class name explicitly set to UninstallAppProgress and expects
 * the application object of the application to uninstall.
 */
public class UninstallAppProgress extends Activity implements OnClickListener {
    private final String TAG="UninstallAppProgress";
    private boolean localLOGV = false;
    private ApplicationInfo mAppInfo;
    private TextView mStatusTextView;
    private Button mOkButton;
    private ProgressBar mProgressBar;
    private View mOkPanel;
    private volatile int mResultCode = -1;
    private final int UNINSTALL_COMPLETE = 1;
    public final static int SUCCEEDED=1;
    public final static int FAILED=0;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UNINSTALL_COMPLETE:
                    mResultCode = msg.arg1;
                    // Update the status text
                    if (msg.arg1 == SUCCEEDED) {
                        mStatusTextView.setText(R.string.uninstall_done);
                    } else {
                        mStatusTextView.setText(R.string.uninstall_failed);
                    }
                    mProgressBar.setVisibility(View.INVISIBLE);
                    // Show the ok button
                    mOkPanel.setVisibility(View.VISIBLE);
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
        boolean isUpdate = ((mAppInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0);
        setTitle(isUpdate ? R.string.uninstall_update_title : R.string.uninstall_application_title);

        setContentView(R.layout.uninstall_progress);
        // Initialize views
        View snippetView = findViewById(R.id.app_snippet);
        PackageUtil.initSnippetForInstalledApp(this, mAppInfo, snippetView);
        mStatusTextView = (TextView)findViewById(R.id.center_text);
        mStatusTextView.setText(R.string.uninstalling);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mProgressBar.setIndeterminate(true);
        // Hide button till progress is being displayed
        mOkPanel = (View)findViewById(R.id.ok_panel);
        mOkButton = (Button)findViewById(R.id.ok_button);
        mOkButton.setOnClickListener(this);
        mOkPanel.setVisibility(View.INVISIBLE);
        PackageDeleteObserver observer = new PackageDeleteObserver();
        getPackageManager().deletePackage(mAppInfo.packageName, observer, 0);
    }

    public void onClick(View v) {
        if(v == mOkButton) {
            Log.i(TAG, "Finished uninstalling pkg: " + mAppInfo.packageName);
            setResultAndFinish(mResultCode);
        }
    }
    
    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (ev.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (mResultCode == -1) {
                // Ignore back key when installation is in progress
                return true;
            } else {
                // If installation is done, just set the result code
                setResult(mResultCode);
            }
        }
        return super.dispatchKeyEvent(ev);
    }
}
