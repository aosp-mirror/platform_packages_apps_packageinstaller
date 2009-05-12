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
import java.util.ArrayList;
import android.widget.AppSecurityPermissions;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.PermissionInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * This activity corresponds to a confirmation screen that is displayed when the user tries
 * to install an application bundled as an apk file.
 * The intent that launches this activity should include the application information object
 * of the application(to be installed) and a list of permission strings associated
 * with the application. This information is displayed on the screen and installation is either
 * continued or canceled based on the user response(click ok or cancel).
 */
public class InstallAppConfirmation extends Activity implements View.OnClickListener {
    private final String TAG="InstallAppConfirmation";
    private boolean localLOGV = false;
    private Button mOk;
    private Button mCancel;
    private ApplicationInfo mAppInfo;
    private Uri mPkgURI;
    private View mContentView;
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        if(localLOGV) Log.i(TAG, "intent="+intent);
        mAppInfo = intent.getParcelableExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO);
        mPkgURI = intent.getData();        
        if(localLOGV) Log.i(TAG, "mAppInfo = "+mAppInfo);
        initView();
    }
    
    public void initView() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        String unknown =  getString(R.string.unknown);
        //set description
        String desc = getString(R.string.security_settings_desc);
        if(desc == null) {
            desc = unknown;
        }
        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContentView = inflater.inflate(R.layout.install_confirm, null);
        setContentView(mContentView);
        //initialize views
        PackageUtil.initSnippetForNewApp(this, mAppInfo, R.id.app_snippet, mPkgURI);
        if(desc != null) {
            ((TextView)findViewById(R.id.security_settings_desc)).setText(desc);
        }
        
        
        LinearLayout permsView = (LinearLayout) mContentView.findViewById(
                R.id.permissions_section);
        boolean permVisible = false;
        PackageParser.Package pkg = PackageUtil.getPackageInfo(mPkgURI);
        if(pkg != null) {
            AppSecurityPermissions asp = new AppSecurityPermissions(this, pkg);
            if(asp.getPermissionCount() > 0) {
                permVisible = true;
                permsView.setVisibility(View.VISIBLE);
                LinearLayout securityList = (LinearLayout) permsView.findViewById(
                        R.id.security_settings_list);
                securityList.addView(asp.getPermissionsView());
            } 
        }
        if(!permVisible){
            permsView.setVisibility(View.GONE);
        }
        mOk = (Button)findViewById(R.id.ok_button);
        mCancel = (Button)findViewById(R.id.cancel_button);
        mOk.setOnClickListener(this);
        mCancel.setOnClickListener(this);
    }
    
    public void setResultAndReturn(int result) {
        if(result == RESULT_CANCELED) Log.i(TAG, "Result has been canceled");
        if(result == RESULT_OK) Log.i(TAG, "result ok");
        setResult(result);
        finish();
    }
    
    public void onClick(View v) {
        int result = RESULT_CANCELED;
        if(v == mOk) {
            result = RESULT_OK;
            setResultAndReturn(result);
        } else if(v == mCancel) {
            result = RESULT_CANCELED;
            setResultAndReturn(result);
        }
    }
}
