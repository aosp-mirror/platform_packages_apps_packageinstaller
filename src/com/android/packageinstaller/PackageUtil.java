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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

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

    /**
     * Utility method to get application information for a given {@link File}
     */
    public static ApplicationInfo getApplicationInfo(File sourcePath) {
        final String archiveFilePath = sourcePath.getAbsolutePath();
        PackageParser packageParser = new PackageParser(archiveFilePath);
        File sourceFile = new File(archiveFilePath);
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setToDefaults();
        PackageParser.Package pkg = packageParser.parsePackage(
                sourceFile, archiveFilePath, metrics, 0);
        if (pkg == null) {
            return null;
        }
        return pkg.applicationInfo;
    }

    /**
     * Utility method to get package information for a given {@link File}
     */
    public static PackageParser.Package getPackageInfo(File sourceFile) {
        final String archiveFilePath = sourceFile.getAbsolutePath();
        PackageParser packageParser = new PackageParser(archiveFilePath);
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setToDefaults();
        PackageParser.Package pkg =  packageParser.parsePackage(sourceFile,
                archiveFilePath, metrics, 0);
        // Nuke the parser reference.
        packageParser = null;
        return pkg;
    }

    public static View initSnippet(View snippetView, CharSequence label, Drawable icon) {
        ((ImageView)snippetView.findViewById(R.id.app_icon)).setImageDrawable(icon);
        ((TextView)snippetView.findViewById(R.id.app_name)).setText(label);
        return snippetView;
    }

    /**
     * Utility method to display a snippet of an installed application.
     * The content view should have been set on context before invoking this method.
     * appSnippet view should include R.id.app_icon and R.id.app_name
     * defined on it.
     *
     * @param pContext context of package that can load the resources
     * @param componentInfo ComponentInfo object whose resources are to be loaded
     * @param snippetView the snippet view
     */
    public static View initSnippetForInstalledApp(Activity pContext,
            ApplicationInfo appInfo, View snippetView) {
        final PackageManager pm = pContext.getPackageManager();
        return initSnippet(
                snippetView,
                appInfo.loadLabel(pm),
                appInfo.loadIcon(pm));
    }

    /**
     * Utility method to display application snippet of a new package.
     * The content view should have been set on context before invoking this method.
     * appSnippet view should include R.id.app_icon and R.id.app_name
     * defined on it.
     *
     * @param pContext context of package that can load the resources
     * @param appInfo ApplicationInfo object of package whose resources are to be loaded
     * @param snippetId view id of app snippet view
     */
    public static View initSnippetForNewApp(Activity pContext, AppSnippet as,
            int snippetId) {
        View appSnippet = pContext.findViewById(snippetId);
        ((ImageView)appSnippet.findViewById(R.id.app_icon)).setImageDrawable(as.icon);
        ((TextView)appSnippet.findViewById(R.id.app_name)).setText(as.label);
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

    static public class AppSnippet {
        CharSequence label;
        Drawable icon;
        public AppSnippet(CharSequence label, Drawable icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    /**
     * Utility method to load application label
     *
     * @param pContext context of package that can load the resources
     * @param appInfo ApplicationInfo object of package whose resources are to be loaded
     * @param snippetId view id of app snippet view
     */
    public static AppSnippet getAppSnippet(
            Activity pContext, ApplicationInfo appInfo, File sourceFile) {
        final String archiveFilePath = sourceFile.getAbsolutePath();
        Resources pRes = pContext.getResources();
        AssetManager assmgr = new AssetManager();
        assmgr.addAssetPath(archiveFilePath);
        Resources res = new Resources(assmgr, pRes.getDisplayMetrics(), pRes.getConfiguration());
        CharSequence label = null;
        // Try to load the label from the package's resources. If an app has not explicitly
        // specified any label, just use the package name.
        if (appInfo.labelRes != 0) {
            try {
                label = res.getText(appInfo.labelRes);
            } catch (Resources.NotFoundException e) {
            }
        }
        if (label == null) {
            label = (appInfo.nonLocalizedLabel != null) ?
                    appInfo.nonLocalizedLabel : appInfo.packageName;
        }
        Drawable icon = null;
        // Try to load the icon from the package's resources. If an app has not explicitly
        // specified any resource, just use the default icon for now.
        if (appInfo.icon != 0) {
            try {
                icon = res.getDrawable(appInfo.icon);
            } catch (Resources.NotFoundException e) {
            }
        }
        if (icon == null) {
            icon = pContext.getPackageManager().getDefaultActivityIcon();
        }
        return new PackageUtil.AppSnippet(label, icon);
    }
}
