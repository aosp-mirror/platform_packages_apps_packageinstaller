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
    private Button mDoneButton;
    private Button mLaunchButton;
    private boolean installFlag;
    private Intent mLaunchIntent;
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        mAppInfo = intent.getParcelableExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO);
        installFlag = intent.getBooleanExtra(PackageUtil.INTENT_ATTR_INSTALL_STATUS, true);
        initView();
    }
    
    public void initView() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        String unknown =  getString(R.string.unknown);
        setContentView(R.layout.install_done);
        //initialize views
        PackageUtil.initAppSnippet(this, mAppInfo, R.id.app_snippet);
        TextView centerText = (TextView)findViewById(R.id.center_text);
        if(installFlag) {
            centerText.setText(getString(R.string.install_done));
        } else {
            centerText.setText(R.string.install_failed);
        }
        mDoneButton = (Button)findViewById(R.id.done_button);
        mDoneButton.setOnClickListener(this);
        mLaunchButton = (Button)findViewById(R.id.launch_button);
        //enable or disable launch buton
        mLaunchIntent = PackageUtil.getLaunchIntentForPackage(this, 
                mAppInfo.packageName);
        if(mLaunchIntent != null) {
            mLaunchButton.setOnClickListener(this);
        } else {
            mLaunchButton.setEnabled(false);
        }
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
