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

import java.io.File;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * This is a utility class for defining some utility methods and constants
 * used in the package installer application.
 */
public class PackageUtil {
    public static final String PREFIX="com.android.packageinstaller.";
    public static final String INTENT_ATTR_INSTALL_STATUS = PREFIX+"installStatus";
    public static final String INTENT_ATTR_APPLICATION_INFO=PREFIX+"applicationInfo";
    public static final String INTENT_ATTR_PERMISSIONS_LIST=PREFIX+"PermissionsList";
    //intent attribute strings related to uninstall
    public static final String INTENT_ATTR_PACKAGE_NAME=PREFIX+"PackageName";
    
    /*
     * Utility method to get application information for a given packageURI
     */
    public static  ApplicationInfo getApplicationInfo(Uri packageURI) {
        final String archiveFilePath = packageURI.getPath();
        PackageParser packageParser = new PackageParser(archiveFilePath);
        File sourceFile = new File(archiveFilePath);
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setToDefaults();
        PackageParser.Package pkg = packageParser.parsePackage(sourceFile, archiveFilePath, metrics, 0);
        if (pkg == null) {
            return null;
        }
        return pkg.applicationInfo;
    }
    
    /*
     * Utility method to get package information for a given packageURI
     */
    public static  PackageParser.Package getPackageInfo(Uri packageURI) {
        final String archiveFilePath = packageURI.getPath();
        PackageParser packageParser = new PackageParser(archiveFilePath);
        File sourceFile = new File(archiveFilePath);
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setToDefaults();
        return packageParser.parsePackage(sourceFile, archiveFilePath, metrics, 0);
    }
    
    /*
     * Utility method to get application label from package manager for a given context
     */
    public static CharSequence getApplicationLabel(Context context, ApplicationInfo appInfo) {
        CharSequence appName = appInfo.loadLabel(context.getPackageManager());
        if(appName == null) {
            appName = context.getString(R.string.unknown);
        }
        return appName;
    }
    
    /*
     * Utility method to getApplicationIcon from package manager for a given context
     */
    public static Drawable getApplicationIcon(Context context, ApplicationInfo appInfo) {
        return appInfo.loadIcon(context.getPackageManager());
    }
    
    /*
     * Utility method to display application snippet. make sure to setContentView on context
     * before invoking this method
     */
    public static View initAppSnippet(Activity context, ApplicationInfo appInfo, int snippetId) {        
        View appSnippet = context.findViewById(snippetId);        
        ((ImageView)appSnippet.findViewById(R.id.app_icon)).setImageDrawable(
                getApplicationIcon(context, appInfo));
        ((TextView)appSnippet.findViewById(R.id.app_name)).setText(
                getApplicationLabel(context, appInfo));
        return appSnippet;
    }
    
    public static boolean isPackageAlreadyInstalled(Activity context, String pkgName) {
        List<PackageInfo> installedList = context.getPackageManager().getInstalledPackages(
                PackageManager.GET_UNINSTALLED_PACKAGES);
        int installedListSize = installedList.size();
        for(int i = 0; i < installedListSize; i++) {
            PackageInfo tmp = installedList.get(i);
            if(pkgName.equalsIgnoreCase(tmp.packageName)) {
                return true;
            }
            
        }
        return false;
    }
}
