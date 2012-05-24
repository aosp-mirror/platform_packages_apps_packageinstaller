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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageParser;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AppSecurityPermissions;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

/*
 * This activity is launched when a new application is installed via side loading
 * The package is first parsed and the user is notified of parse errors via a dialog.
 * If the package is successfully parsed, the user is notified to turn on the install unknown
 * applications setting. A memory check is made at this point and the user is notified of out
 * of memory conditions if any. If the package is already existing on the device, 
 * a confirmation dialog (to replace the existing package) is presented to the user.
 * Based on the user response the package is then installed by launching InstallAppConfirm
 * sub activity. All state transitions are handled in this activity
 */
public class PackageInstallerActivity extends Activity implements OnCancelListener, OnClickListener {
    private static final String TAG = "PackageInstaller";
    private Uri mPackageURI;    
    private boolean localLOGV = false;
    PackageManager mPm;
    PackageParser.Package mPkgInfo;
    ApplicationInfo mSourceInfo;

    // ApplicationInfo object primarily used for already existing applications
    private ApplicationInfo mAppInfo = null;

    // View for install progress
    View mInstallConfirm;
    // Buttons to indicate user acceptance
    private Button mOk;
    private Button mCancel;

    static final String PREFS_ALLOWED_SOURCES = "allowed_sources";

    // Dialog identifiers used in showDialog
    private static final int DLG_BASE = 0;
    private static final int DLG_UNKNOWN_APPS = DLG_BASE + 1;
    private static final int DLG_PACKAGE_ERROR = DLG_BASE + 2;
    private static final int DLG_OUT_OF_SPACE = DLG_BASE + 3;
    private static final int DLG_INSTALL_ERROR = DLG_BASE + 4;
    private static final int DLG_ALLOW_SOURCE = DLG_BASE + 5;

    /**
     * This is a helper class that implements the management of tabs and all
     * details of connecting a ViewPager with associated TabHost.  It relies on a
     * trick.  Normally a tab host has a simple API for supplying a View or
     * Intent that each tab will show.  This is not sufficient for switching
     * between pages.  So instead we make the content part of the tab host
     * 0dp high (it is not shown) and the TabsAdapter supplies its own dummy
     * view to show as the tab content.  It listens to changes in tabs, and takes
     * care of switch to the correct paged in the ViewPager whenever the selected
     * tab changes.
     */
    public static class TabsAdapter extends PagerAdapter
            implements TabHost.OnTabChangeListener, ViewPager.OnPageChangeListener {
        private final Context mContext;
        private final TabHost mTabHost;
        private final ViewPager mViewPager;
        private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();
        private final Rect mTempRect = new Rect();

        static final class TabInfo {
            private final String tag;
            private final View view;

            TabInfo(String _tag, View _view) {
                tag = _tag;
                view = _view;
            }
        }

        static class DummyTabFactory implements TabHost.TabContentFactory {
            private final Context mContext;

            public DummyTabFactory(Context context) {
                mContext = context;
            }

            @Override
            public View createTabContent(String tag) {
                View v = new View(mContext);
                v.setMinimumWidth(0);
                v.setMinimumHeight(0);
                return v;
            }
        }

        public TabsAdapter(Activity activity, TabHost tabHost, ViewPager pager) {
            mContext = activity;
            mTabHost = tabHost;
            mViewPager = pager;
            mTabHost.setOnTabChangedListener(this);
            mViewPager.setAdapter(this);
            mViewPager.setOnPageChangeListener(this);
        }

        public void addTab(TabHost.TabSpec tabSpec, View view) {
            tabSpec.setContent(new DummyTabFactory(mContext));
            String tag = tabSpec.getTag();

            TabInfo info = new TabInfo(tag, view);
            mTabs.add(info);
            mTabHost.addTab(tabSpec);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mTabs.size();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View view = mTabs.get(position).view;
            container.addView(view);
            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View)object);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public void onTabChanged(String tabId) {
            int position = mTabHost.getCurrentTab();
            mViewPager.setCurrentItem(position);
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            // Unfortunately when TabHost changes the current tab, it kindly
            // also takes care of putting focus on it when not in touch mode.
            // The jerk.
            // This hack tries to prevent this from pulling focus out of our
            // ViewPager.
            TabWidget widget = mTabHost.getTabWidget();
            int oldFocusability = widget.getDescendantFocusability();
            widget.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            mTabHost.setCurrentTab(position);
            widget.setDescendantFocusability(oldFocusability);

            // Scroll the current tab into visibility if needed.
            View tab = widget.getChildTabViewAt(position);
            mTempRect.set(tab.getLeft(), tab.getTop(), tab.getRight(), tab.getBottom());
            widget.requestRectangleOnScreen(mTempRect, false);

            // Make sure the scrollbars are visible for a moment after selection
            final View contentView = mTabs.get(position).view;
            if (contentView instanceof CaffeinatedScrollView) {
                ((CaffeinatedScrollView) contentView).awakenScrollBars();
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }
    }

    private void startInstallConfirm() {
        TabHost tabHost = (TabHost)findViewById(android.R.id.tabhost);
        tabHost.setup();
        ViewPager viewPager = (ViewPager)findViewById(R.id.pager);
        TabsAdapter adapter = new TabsAdapter(this, tabHost, viewPager);

        boolean permVisible = false;
        int msg = 0;
        if (mPkgInfo != null) {
            AppSecurityPermissions perms = new AppSecurityPermissions(this, mPkgInfo);
            if (mAppInfo != null) {
                permVisible = true;
                msg = (mAppInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        ? R.string.install_confirm_question_update_system
                        : R.string.install_confirm_question_update;
                ScrollView scrollView = new CaffeinatedScrollView(this);
                scrollView.setFillViewport(true);
                if (perms.getPermissionCount(AppSecurityPermissions.WHICH_NEW) > 0) {
                    scrollView.addView(perms.getPermissionsView(AppSecurityPermissions.WHICH_NEW));
                } else {
                    LayoutInflater inflater = (LayoutInflater)getSystemService(
                            Context.LAYOUT_INFLATER_SERVICE);
                    TextView label = (TextView)inflater.inflate(R.layout.label, null);
                    label.setText(R.string.no_new_perms);
                    scrollView.addView(label);
                }
                adapter.addTab(tabHost.newTabSpec("new").setIndicator(
                        getText(R.string.newPerms)), scrollView);
            }
            if (perms.getPermissionCount(AppSecurityPermissions.WHICH_PERSONAL) > 0) {
                permVisible = true;
                ScrollView scrollView = new CaffeinatedScrollView(this);
                scrollView.setFillViewport(true);
                scrollView.addView(perms.getPermissionsView(AppSecurityPermissions.WHICH_PERSONAL));
                adapter.addTab(tabHost.newTabSpec("personal").setIndicator(
                        getText(R.string.privacyPerms)), scrollView);
            }
            if (perms.getPermissionCount(AppSecurityPermissions.WHICH_DEVICE) > 0) {
                permVisible = true;
                ScrollView scrollView = new CaffeinatedScrollView(this);
                scrollView.setFillViewport(true);
                scrollView.addView(perms.getPermissionsView(AppSecurityPermissions.WHICH_DEVICE));
                adapter.addTab(tabHost.newTabSpec("device").setIndicator(
                        getText(R.string.devicePerms)), scrollView);
            }
        }
        if (!permVisible) {
            if (msg == 0) {
                msg = R.string.install_confirm_question_no_perms;
            }
            tabHost.setVisibility(View.INVISIBLE);
        }
        if (msg != 0) {
            ((TextView)findViewById(R.id.install_confirm_question)).setText(msg);
        }
        mInstallConfirm.setVisibility(View.VISIBLE);
        mOk = (Button)findViewById(R.id.ok_button);
        mCancel = (Button)findViewById(R.id.cancel_button);
        mOk.setOnClickListener(this);
        mCancel.setOnClickListener(this);
    }

    private void showDialogInner(int id) {
        // TODO better fix for this? Remove dialog so that it gets created again
        removeDialog(id);
        showDialog(id);
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
        case DLG_UNKNOWN_APPS:
            return new AlertDialog.Builder(this)
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
                        }
                    })
                    .setOnCancelListener(this)
                    .create(); 
        case DLG_PACKAGE_ERROR :
            return new AlertDialog.Builder(this)
                    .setTitle(R.string.Parse_error_dlg_title)
                    .setMessage(R.string.Parse_error_dlg_text)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setOnCancelListener(this)
                    .create();
        case DLG_OUT_OF_SPACE:
            // Guaranteed not to be null. will default to package name if not set by app
            CharSequence appTitle = mPm.getApplicationLabel(mPkgInfo.applicationInfo);
            String dlgText = getString(R.string.out_of_space_dlg_text, 
                    appTitle.toString());
            return new AlertDialog.Builder(this)
                    .setTitle(R.string.out_of_space_dlg_title)
                    .setMessage(dlgText)
                    .setPositiveButton(R.string.manage_applications, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            //launch manage applications
                            Intent intent = new Intent("android.intent.action.MANAGE_PACKAGE_STORAGE");
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);   
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Log.i(TAG, "Canceling installation");
                            finish();
                        }
                  })
                  .setOnCancelListener(this)
                  .create();
        case DLG_INSTALL_ERROR :
            // Guaranteed not to be null. will default to package name if not set by app
            CharSequence appTitle1 = mPm.getApplicationLabel(mPkgInfo.applicationInfo);
            String dlgText1 = getString(R.string.install_failed_msg,
                    appTitle1.toString());
            return new AlertDialog.Builder(this)
                    .setTitle(R.string.install_failed)
                    .setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setMessage(dlgText1)
                    .setOnCancelListener(this)
                    .create();
        case DLG_ALLOW_SOURCE:
            CharSequence appTitle2 = mPm.getApplicationLabel(mSourceInfo);
            String dlgText2 = getString(R.string.allow_source_dlg_text,
                    appTitle2.toString());
            return new AlertDialog.Builder(this)
                    .setTitle(R.string.allow_source_dlg_title)
                    .setMessage(dlgText2)
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setResult(RESULT_CANCELED);
                            finish();
                        }})
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            SharedPreferences prefs = getSharedPreferences(PREFS_ALLOWED_SOURCES,
                                    Context.MODE_PRIVATE);
                            prefs.edit().putBoolean(mSourceInfo.packageName, true).apply();
                            startInstallConfirm();
                        }
                    })
                    .setOnCancelListener(this)
                    .create();
       }
       return null;
   }

    private void launchSettingsAppAndFinish() {
        // Create an intent to launch SettingsTwo activity
        Intent launchSettingsIntent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
        launchSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launchSettingsIntent);
        finish();
    }
    
    private boolean isInstallingUnknownAppsAllowed() {
        return Settings.Secure.getInt(getContentResolver(), 
            Settings.Secure.INSTALL_NON_MARKET_APPS, 0) > 0;
    }
    
    private void initiateInstall() {
        String pkgName = mPkgInfo.packageName;
        // Check if there is already a package on the device with this name
        // but it has been renamed to something else.
        String[] oldName = mPm.canonicalToCurrentPackageNames(new String[] { pkgName });
        if (oldName != null && oldName.length > 0 && oldName[0] != null) {
            pkgName = oldName[0];
            mPkgInfo.setPackageName(pkgName);
        }
        // Check if package is already installed. display confirmation dialog if replacing pkg
        try {
            mAppInfo = mPm.getApplicationInfo(pkgName,
                    PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (NameNotFoundException e) {
            mAppInfo = null;
        }
        startInstallConfirm();
    }

    void setPmResult(int pmResult) {
        Intent result = new Intent();
        result.putExtra(Intent.EXTRA_INSTALL_RESULT, pmResult);
        setResult(pmResult == PackageManager.INSTALL_SUCCEEDED
                ? RESULT_OK : RESULT_FIRST_USER, result);
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // get intent information
        final Intent intent = getIntent();
        mPackageURI = intent.getData();
        mPm = getPackageManager();

        final String scheme = mPackageURI.getScheme();
        if (scheme != null && !"file".equals(scheme)) {
            throw new IllegalArgumentException("unexpected scheme " + scheme);
        }

        final File sourceFile = new File(mPackageURI.getPath());
        mPkgInfo = PackageUtil.getPackageInfo(sourceFile);

        // Check for parse errors
        if (mPkgInfo == null) {
            Log.w(TAG, "Parse error when parsing manifest. Discontinuing installation");
            showDialogInner(DLG_PACKAGE_ERROR);
            setPmResult(PackageManager.INSTALL_FAILED_INVALID_APK);
            return;
        }
        
        //set view
        setContentView(R.layout.install_start);
        mInstallConfirm = findViewById(R.id.install_confirm_panel);
        mInstallConfirm.setVisibility(View.INVISIBLE);
        final PackageUtil.AppSnippet as = PackageUtil.getAppSnippet(
                this, mPkgInfo.applicationInfo, sourceFile);
        PackageUtil.initSnippetForNewApp(this, as, R.id.app_snippet);

        // Deal with install source.
        String callerPackage = getCallingPackage();
        if (callerPackage != null && intent.getBooleanExtra(
                Intent.EXTRA_NOT_UNKNOWN_SOURCE, false)) {
            try {
                mSourceInfo = mPm.getApplicationInfo(callerPackage, 0);
                if (mSourceInfo != null) {
                    if ((mSourceInfo.flags&ApplicationInfo.FLAG_SYSTEM) != 0) {
                        // System apps don't need to be approved.
                        initiateInstall();
                        return;
                    }
                    /* for now this is disabled, since the user would need to
                     * have enabled the global "unknown sources" setting in the
                     * first place in order to get here.
                    SharedPreferences prefs = getSharedPreferences(PREFS_ALLOWED_SOURCES,
                            Context.MODE_PRIVATE);
                    if (prefs.getBoolean(mSourceInfo.packageName, false)) {
                        // User has already allowed this one.
                        initiateInstall();
                        return;
                    }
                    //ask user to enable setting first
                    showDialogInner(DLG_ALLOW_SOURCE);
                    return;
                     */
                }
            } catch (NameNotFoundException e) {
            }
        }

        // Check unknown sources.
        if (!isInstallingUnknownAppsAllowed()) {
            //ask user to enable setting first
            showDialogInner(DLG_UNKNOWN_APPS);
            return;
        }
        initiateInstall();
    }
    
    // Generic handling when pressing back key
    public void onCancel(DialogInterface dialog) {
        finish();
    }

    public void onClick(View v) {
        if(v == mOk) {
            // Start subactivity to actually install the application
            Intent newIntent = new Intent();
            newIntent.putExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO,
                    mPkgInfo.applicationInfo);
            newIntent.setData(mPackageURI);
            newIntent.setClass(this, InstallAppProgress.class);
            String installerPackageName = getIntent().getStringExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME);
            if (installerPackageName != null) {
                newIntent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, installerPackageName);
            }
            if (getIntent().getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false)) {
                newIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                newIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            }
            if(localLOGV) Log.i(TAG, "downloaded app uri="+mPackageURI);
            startActivity(newIntent);
            finish();
        } else if(v == mCancel) {
            // Cancel and finish
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    /**
     * It's a ScrollView that knows how to stay awake.
     */
    static class CaffeinatedScrollView extends ScrollView {
        public CaffeinatedScrollView(Context context) {
            super(context);
        }

        /**
         * Make this visible so we can call it
         */
        @Override
        public boolean awakenScrollBars() {
            return super.awakenScrollBars();
        }
    }
}
