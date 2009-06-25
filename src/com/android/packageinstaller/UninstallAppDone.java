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
 * This activity corresponds to a uninstall status  screen that is displayed 
 * when an application gets uninstalled. The screen contains a single ok button at the
 * bottom.
 */
public class UninstallAppDone extends Activity  implements View.OnClickListener {
    private final String TAG="UninstallAppDone";
    private boolean localLOGV = false;
    private ApplicationInfo mAppInfo;
    private Button mOkButton;
    private boolean uninstallFlag;
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        mAppInfo = intent.getParcelableExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO);
        //TODO set installFlag
        uninstallFlag = intent.getBooleanExtra(PackageUtil.INTENT_ATTR_INSTALL_STATUS, true);
        initView();
    }
    
    public void initView() {
        String unknown =  getString(R.string.unknown);
        setContentView(R.layout.uninstall_done);
        TextView centerText = (TextView)findViewById(R.id.center_text);
        if(uninstallFlag) {
            centerText.setText(getString(R.string.uninstall_done));
        } else {
            centerText.setText(R.string.uninstall_failed);
        }
        mOkButton = (Button)findViewById(R.id.ok_button);
        mOkButton.setOnClickListener(this);
    }

    public void onClick(View v) {
        if(v == mOkButton) {
            Log.i(TAG, "Finished installing "+mAppInfo);
            finish();
        }
    }
}
