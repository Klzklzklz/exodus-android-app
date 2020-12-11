package org.eu.exodus_privacy.exodusprivacy.fragments;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;

import org.eu.exodus_privacy.exodusprivacy.Utils;
import org.eu.exodus_privacy.exodusprivacy.adapters.ApplicationViewModel;
import org.eu.exodus_privacy.exodusprivacy.manager.DatabaseManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ComputeAppListTask extends AsyncTask<Void, Void, List<ApplicationViewModel>> {

    private static final String gStore = "com.android.vending";
    private static final String fdroid = "ord.fdroid.fdroid";
    private final WeakReference<PackageManager> packageManagerRef;
    private final WeakReference<DatabaseManager> databaseManagerRef;
    private final WeakReference<Listener> listenerRef;

    order userOrderChoice;

    ComputeAppListTask(WeakReference<PackageManager> packageManagerRef,
                       WeakReference<DatabaseManager> databaseManagerRef,
                       WeakReference<Listener> listenerRef, order orderChoice) {
        this.packageManagerRef = packageManagerRef;
        this.databaseManagerRef = databaseManagerRef;
        this.listenerRef = listenerRef;
        userOrderChoice = orderChoice;
    }

    protected List<ApplicationViewModel> doInBackground(Void... params) {
        PackageManager packageManager = packageManagerRef.get();
        DatabaseManager databaseManager = databaseManagerRef.get();

        List<ApplicationViewModel> vms = new ArrayList<>();
        if (packageManager != null && databaseManager != null) {
            List<PackageInfo> installedPackages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS);
            vms = applyStoreFilter(installedPackages, databaseManager, packageManager);
            convertPackagesToViewModels(vms, databaseManager, packageManager);
        }
        //Reordering should done here
        if (userOrderChoice == null) {
            userOrderChoice = order.DEFAULT;
        }
        vms = order(vms, userOrderChoice);
        return vms;
    }

    private List<ApplicationViewModel> order(List<ApplicationViewModel> vms, order orderChoice) {
        if (orderChoice == order.LESS_TRACKERS) {
            Collections.sort(vms, (obj1, obj2) -> Integer.compare(obj1.requestedPermissions != null ? obj1.requestedPermissions.length : 0, obj2.requestedPermissions != null ? obj2.requestedPermissions.length : 0));
            Collections.sort(vms, (obj1, obj2) -> Integer.compare(obj1.trackers != null ? obj1.trackers.size() : 0, obj2.trackers != null ? obj2.trackers.size() : 0));
        } else if (orderChoice == order.MOST_TRACKERS) {
            Collections.sort(vms, (obj1, obj2) -> Integer.compare(obj2.requestedPermissions != null ? obj2.requestedPermissions.length : 0, obj1.requestedPermissions != null ? obj1.requestedPermissions.length : 0));
            Collections.sort(vms, (obj1, obj2) -> Integer.compare(obj2.trackers != null ? obj2.trackers.size() : 0, obj1.trackers != null ? obj1.trackers.size() : 0));
        } else if (orderChoice == order.LESS_PERMISSIONS) {
            Collections.sort(vms, (obj1, obj2) -> Integer.compare(obj1.trackers != null ? obj1.trackers.size() : 0, obj2.trackers != null ? obj2.trackers.size() : 0));
            Collections.sort(vms, (obj1, obj2) -> Integer.compare(obj1.requestedPermissions != null ? obj1.requestedPermissions.length : 0, obj2.requestedPermissions != null ? obj2.requestedPermissions.length : 0));
        } else if (orderChoice == order.MOST_PERMISSIONS) {
            Collections.sort(vms, (obj1, obj2) -> Integer.compare(obj2.trackers != null ? obj2.trackers.size() : 0, obj1.trackers != null ? obj1.trackers.size() : 0));
            Collections.sort(vms, (obj1, obj2) -> Integer.compare(obj2.requestedPermissions != null ? obj2.requestedPermissions.length : 0, obj1.requestedPermissions != null ? obj1.requestedPermissions.length : 0));
        } else {
            Collections.sort(vms, (obj1, obj2) -> String.valueOf(obj1.label).compareToIgnoreCase(String.valueOf(obj2.label)));
        }
        return vms;
    }

    @Override
    protected void onPostExecute(List<ApplicationViewModel> vms) {
        Listener listener = listenerRef.get();

        if (listener != null) {
            listener.onAppsComputed(vms);
        }
    }

    private void convertPackagesToViewModels(List<ApplicationViewModel> appsToBuild,
                                             DatabaseManager databaseManager,
                                             PackageManager packageManager) {
        for (ApplicationViewModel vm : appsToBuild) {
            try {
                PackageInfo pi = packageManager.getPackageInfo(vm.packageName, PackageManager.GET_PERMISSIONS);
                buildViewModelFromPackageInfo(vm, pi, databaseManager, packageManager);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void buildViewModelFromPackageInfo(ApplicationViewModel vm, PackageInfo pi,
                                               DatabaseManager databaseManager,
                                               PackageManager packageManager) {

        vm.versionName = pi.versionName;
        vm.packageName = pi.packageName;
        vm.versionCode = pi.versionCode;
        vm.requestedPermissions = pi.requestedPermissions;

        if (vm.versionName != null)
            vm.report = databaseManager.getReportFor(vm.packageName, vm.versionName, vm.source);
        else {
            vm.report = databaseManager.getReportFor(vm.packageName, vm.versionCode, vm.source);
        }

        if (vm.report != null) {
            vm.trackers = databaseManager.getTrackers(vm.report.trackers);
        }

        try {
            vm.icon = packageManager.getApplicationIcon(vm.packageName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        vm.label = packageManager.getApplicationLabel(pi.applicationInfo);
        vm.installerPackageName = packageManager.getInstallerPackageName(vm.packageName);
        vm.isVisible = true;
    }

    private List<ApplicationViewModel> applyStoreFilter(List<PackageInfo> packageInfos,
                                                        DatabaseManager databaseManager,
                                                        PackageManager packageManager) {
        List<ApplicationViewModel> result = new ArrayList<>();
        for (PackageInfo packageInfo : packageInfos) {
            String packageName = packageInfo.packageName;
            String installerPackageName = packageManager.getInstallerPackageName(packageName);
            ApplicationViewModel vm = new ApplicationViewModel();
            vm.packageName = packageName;
            if (!gStore.equals(installerPackageName) && !fdroid.equals(installerPackageName)) {
                String auid = Utils.getCertificateSHA1Fingerprint(packageManager, packageName);
                Map<String, String> sources = databaseManager.getSources(packageName);
                for (Map.Entry<String, String> entry : sources.entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(auid)) {
                        vm.source = entry.getKey();
                        break;
                    }
                }
            } else if (gStore.equals(installerPackageName)) {
                vm.source = "google";
            } else {
                vm.source = "fdroid";
            }
            ApplicationInfo appInfo = null;
            try {
                appInfo = packageManager.getApplicationInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            if (vm.source != null && appInfo != null && appInfo.enabled)
                result.add(vm);
        }
        return result;
    }

    public enum order {
        DEFAULT,
        MOST_TRACKERS,
        LESS_TRACKERS,
        MOST_PERMISSIONS,
        LESS_PERMISSIONS,
    }

    interface Listener {
        void onAppsComputed(List<ApplicationViewModel> apps);
    }

}
