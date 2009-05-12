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
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

/**
 * This activity corresponds to a install status  screen that is displayed 
 * when the user tries
 * to install an application bundled as an apk file. The screen
 * has two buttons to either launch the newly installed application
 * or close the screen. The installation result and the package uri are passed through the
 * intent that launches the activity.
 */
public class InstallAppDone extends Activity  implements View.OnClickListener {
    private final String TAG="InstallAppDone";
    private boolean localLOGV = false;
    private ApplicationInfo mAppInfo;
    private Uri mPkgURI;
    private Button mDoneButton;
    private Button mLaunchButton;
    private boolean installFlag;
    private Intent mLaunchIntent;
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        mAppInfo = intent.getParcelableExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO);
        mPkgURI = intent.getData();
        installFlag = intent.getBooleanExtra(PackageUtil.INTENT_ATTR_INSTALL_STATUS, true);
        if(localLOGV) Log.i(TAG, "installFlag="+installFlag);
        initView();
    }
    
    public void initView() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        String unknown =  getString(R.string.unknown);
        setContentView(R.layout.install_done);
        // Initialize views
        PackageUtil.initSnippetForInstalledApp(this, mAppInfo, R.id.app_snippet);
        TextView centerText = (TextView)findViewById(R.id.center_text);
        mDoneButton = (Button)findViewById(R.id.done_button);
        mLaunchButton = (Button)findViewById(R.id.launch_button);
        int centerTextDrawableId;
        int centerTextLabel;
        if(installFlag) {
            mLaunchButton.setVisibility(View.VISIBLE);
            centerTextDrawableId = R.drawable.button_indicator_finish;
            centerTextLabel = R.string.install_done;
            // Enable or disable launch button
            mLaunchIntent = getPackageManager().getLaunchIntentForPackage( 
                    mAppInfo.packageName);
            if(mLaunchIntent != null) {
                mLaunchButton.setOnClickListener(this);
            } else {
                mLaunchButton.setEnabled(false);
            }
        } else {
            centerTextDrawableId = com.android.internal.R.drawable.ic_bullet_key_permission;
            centerTextLabel = R.string.install_failed;
            mLaunchButton.setVisibility(View.INVISIBLE);
        }
        Drawable centerTextDrawable = getResources().getDrawable(centerTextDrawableId);
        centerTextDrawable.setBounds(0, 0, 
                centerTextDrawable.getIntrinsicWidth(),
                centerTextDrawable.getIntrinsicHeight());
        centerText.setCompoundDrawables(centerTextDrawable, null, null, null);
        centerText.setText(getString(centerTextLabel));
        mDoneButton.setOnClickListener(this);
    }

    public void onClick(View v) {
        if(v == mDoneButton) {
            Log.i(TAG, "Finished installing "+mAppInfo);
            finish();
        } else if(v == mLaunchButton) {
                startActivity(mLaunchIntent);
                finish();
        }
    }
}
