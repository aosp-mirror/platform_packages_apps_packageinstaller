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
import android.app.Dialog;
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
import android.widget.TextView;
import android.content.pm.PackageManager.NameNotFoundException;

/*
 * This activity presents UI to uninstall an application. Usually launched with intent
 * Intent.ACTION_UNINSTALL_PKG_COMMAND and attribute 
 * com.android.packageinstaller.PackageName set to the application package name
 */
public class UninstallerActivity extends Activity implements OnClickListener {
    private static final String TAG = "UninstallerActivity";
    private boolean localLOGV = false;
    // States indicating status of ui display when uninstalling application
    private static final int UNINSTALL_CONFIRM = 1;
    private static final int UNINSTALL_PROGRESS = 2;
    private static final int UNINSTALL_DONE = 3;
    private int mCurrentState = UNINSTALL_CONFIRM;
    PackageManager mPm;
    private ApplicationInfo mAppInfo;
    private Button mOk;
    private Button mCancel;

    // Dialog identifiers used in showDialog
    private static final int DLG_BASE = 0;
    private static final int DLG_APP_NOT_FOUND = DLG_BASE + 1;
    private static final int DLG_UNINSTALL_FAILED = DLG_BASE + 2;
    
    private void showDialogInner(int id) {
            showDialog(id);
    }
    
    @Override
    public Dialog onCreateDialog(int id) {
        switch (id) {
        case DLG_APP_NOT_FOUND :
            return new AlertDialog.Builder(this)
                    .setTitle(R.string.app_not_found_dlg_title)
                    .setIcon(com.android.internal.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.app_not_found_dlg_text)
                    .setNeutralButton(getString(R.string.dlg_ok), 
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }})
                    .create();
        case DLG_UNINSTALL_FAILED :
            // Guaranteed not to be null. will default to package name if not set by app
           CharSequence appTitle = mPm.getApplicationLabel(mAppInfo);
           String dlgText = getString(R.string.uninstall_failed_msg,
                    appTitle.toString());
            // Display uninstall failed dialog
            return new AlertDialog.Builder(this)
                    .setTitle(R.string.uninstall_failed)
                    .setIcon(com.android.internal.R.drawable.ic_dialog_alert)
                    .setMessage(dlgText)
                    .setNeutralButton(getString(R.string.dlg_ok), 
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }})
                    .create();
        }
        return null;
    }

    private void startUninstallProgress() {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.putExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO, 
                                                  mAppInfo);
        newIntent.setClass(this, UninstallAppProgress.class);
        startActivityForResult(newIntent, UNINSTALL_PROGRESS);
    }
    
    private void startUninstallDone() {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.putExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO, 
                                                  mAppInfo);
        newIntent.putExtra(PackageUtil.INTENT_ATTR_INSTALL_STATUS, true);
        newIntent.setClass(this, UninstallAppDone.class);
        startActivityForResult(newIntent, UNINSTALL_DONE);
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
            showDialog(DLG_APP_NOT_FOUND);
            return;
        }
        //initialize package manager
        mPm = getPackageManager();
        boolean errFlag = false;
        try {
            mAppInfo = mPm.getApplicationInfo(packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (NameNotFoundException e) {
            errFlag = true;
        }
        if(mAppInfo == null || errFlag) {
            Log.e(TAG, "Invalid application:"+packageName);
            showDialog(DLG_APP_NOT_FOUND);
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            //set view
            setContentView(R.layout.uninstall_confirm);
            TextView question = (TextView) findViewById(R.id.uninstall_question);
            TextView confirm = (TextView) findViewById(R.id.uninstall_confirm_text);
            if ((mAppInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                question.setText(R.string.uninstall_update_question);
                confirm.setText(R.string.uninstall_update_text);
            } else {
                question.setText(R.string.uninstall_application_question);
                confirm.setText(R.string.uninstall_application_text);
            }
            PackageUtil.initSnippetForInstalledApp(this, mAppInfo, R.id.app_snippet);
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
            if (resultCode==UninstallAppProgress.SUCCEEDED) {
                startUninstallDone();
            } else {
                showDialogInner(DLG_UNINSTALL_FAILED);
            }
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
