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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.content.pm.PackageManager.NameNotFoundException;

/*
 * This activity presents UI to uninstall an application. Usually launched with intent
 * Intent.ACTION_UNINSTALL_PKG_COMMAND and attribute 
 * com.android.packageinstaller.PackageName set to the application package name
 */
public class UninstallerActivity extends Activity implements OnClickListener {
    private static final String TAG = "UninstallerActivity";
    private boolean localLOGV = false;
    //states indicating status of ui display when uninstalling application
    private static final int UNINSTALL_CONFIRM=1;
    private static final int UNINSTALL_PROGRESS=2;
    private static final int UNINSTALL_DONE=3;
    private int mCurrentState = UNINSTALL_CONFIRM;
    PackageManager mPm;
    private ApplicationInfo mAppInfo;
    private Button mOk;
    private Button mCancel;
    
    private void startUninstallProgress() {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.putExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO, 
                                                  mAppInfo);
        newIntent.setClass(this, UninstallAppProgress.class);
        startActivityForResult(newIntent, UNINSTALL_PROGRESS);
    }
    
    private void startUninstallDone(boolean result) {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.putExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO, 
                                                  mAppInfo);
        newIntent.putExtra(PackageUtil.INTENT_ATTR_INSTALL_STATUS, result);
        newIntent.setClass(this, UninstallAppDone.class);
        startActivityForResult(newIntent, UNINSTALL_DONE);
    }
    
    private void displayErrorDialog(int msgId) {
        //display confirmation dialog
        new AlertDialog.Builder(this)
        .setTitle(getString(R.string.app_not_found_dlg_title))
        .setIcon(com.android.internal.R.drawable.ic_dialog_alert)
        .setMessage(getString(msgId))
        .setNeutralButton(getString(R.string.dlg_ok), 
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //force to recompute changed value
                        finish();
                    }
                }
        )
        .show();
    }
        
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        //get intent information
        final Intent intent = getIntent();
        Uri packageURI = intent.getData();
        String packageName = packageURI.getEncodedSchemeSpecificPart();
        if(packageName == null) {
            Log.e(TAG, "Invalid package name:"+packageName);
            displayErrorDialog(R.string.app_not_found_dlg_text);
            return;
        }
        //initialize package manager
        mPm = getPackageManager();
        boolean errFlag = false;
        try {
            mAppInfo = mPm.getApplicationInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            errFlag = true;
        }
        if(mAppInfo == null || errFlag) {
            Log.e(TAG, "Invalid application:"+packageName);
            displayErrorDialog(R.string.app_not_found_dlg_text);
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            //set view
            setContentView(R.layout.uninstall_confirm);
            PackageUtil.initAppSnippet(this, mAppInfo, R.id.app_snippet);
            //initialize ui elements
            mOk = (Button)findViewById(R.id.ok_button);
            mCancel = (Button)findViewById(R.id.cancel_button);
            mOk.setOnClickListener(this);
            mCancel.setOnClickListener(this);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        boolean finish = true;
        switch(requestCode) {
        case UNINSTALL_PROGRESS:
            finish = false;
            mCurrentState = UNINSTALL_DONE;
            //start the next screen to show final status of installation
            startUninstallDone(resultCode==UninstallAppProgress.SUCCEEDED);
            break;
        case UNINSTALL_DONE:
            //neednt check for result code here
            break;
        default:
            break;
        }
        if(finish) {
            //finish off this activity to return to the previous activity that launched it
            Log.i(TAG, "Finishing off activity");
            finish();
        }
    }
    
    private void finishAndReturn() {
        finish();
    }
    
    public void onClick(View v) {
        if(v == mOk) {
            //initiate next screen
            startUninstallProgress();
        } else if(v == mCancel) {
            finishAndReturn();
        }
    }
}
