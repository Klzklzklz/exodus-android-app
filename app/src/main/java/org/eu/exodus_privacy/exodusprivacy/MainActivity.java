/*
 * Copyright (C) 2018  Anthony Chomienne, anthony@mob-dev.fr
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.eu.exodus_privacy.exodusprivacy;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.PopupMenu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.snackbar.Snackbar;

import org.eu.exodus_privacy.exodusprivacy.adapters.ApplicationListAdapter;
import org.eu.exodus_privacy.exodusprivacy.adapters.ApplicationViewModel;
import org.eu.exodus_privacy.exodusprivacy.adapters.TrackerListAdapter;
import org.eu.exodus_privacy.exodusprivacy.databinding.MainBinding;
import org.eu.exodus_privacy.exodusprivacy.fragments.ComputeAppList;
import org.eu.exodus_privacy.exodusprivacy.fragments.HomeFragment;
import org.eu.exodus_privacy.exodusprivacy.fragments.ReportFragment;
import org.eu.exodus_privacy.exodusprivacy.fragments.TrackerFragment;
import org.eu.exodus_privacy.exodusprivacy.fragments.Updatable;
import org.eu.exodus_privacy.exodusprivacy.listener.NetworkListener;
import org.eu.exodus_privacy.exodusprivacy.manager.DatabaseManager;
import org.eu.exodus_privacy.exodusprivacy.objects.Application;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static ComputeAppList.order order = ComputeAppList.order.DEFAULT;
    private List<Updatable> fragments;
    private SearchView searchView;
    private Menu toolbarMenu;
    private String packageName;
    private MainBinding binding;
    private ApplicationListAdapter.OnAppClickListener onAppClickListener;
    private String previousQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.main);
        final MainBinding mainBinding = binding;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_title);
        }
        fragments = new ArrayList<>();
        NetworkListener networkListener = new NetworkListener() {
            @Override
            public void onSuccess(Application application) {
                runOnUiThread(() -> {
                    for (Updatable updatable : fragments) {
                        if (updatable instanceof ReportFragment) {
                            ApplicationViewModel model = ((ReportFragment) updatable).getModel();
                            if (model.versionName == null)
                                model.report = DatabaseManager.getInstance(MainActivity.this).getReportFor(model.packageName, model.versionCode, model.source);
                            else
                                model.report = DatabaseManager.getInstance(MainActivity.this).getReportFor(model.packageName, model.versionName, model.source);
                            if (model.report != null)
                                model.trackers = DatabaseManager.getInstance(MainActivity.this).getTrackers(model.report.trackers);
                        }
                        updatable.onUpdateComplete();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    for (Updatable updatable : fragments) {
                        updatable.onUpdateComplete();
                    }
                    Snackbar bar = Snackbar.make(mainBinding.fragmentContainer, error, Snackbar.LENGTH_LONG);
                    bar.show();
                });
            }

            @Override
            public void onProgress(int resourceId, int progress, int maxProgress) {
                //do nothing here
            }
        };

        TrackerListAdapter.OnTrackerClickListener onTrackerClickListener = id -> {
            TrackerFragment tracker = TrackerFragment.newInstance(id);
            tracker.setOnAppClickListener(onAppClickListener);
            fragments.add(tracker);
            FragmentManager manager = getSupportFragmentManager();
            FragmentTransaction transaction = manager.beginTransaction();
            transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_right, R.anim.slide_in_left, R.anim.slide_out_left)
                    .replace(R.id.fragment_container, tracker)
                    .addToBackStack(null)
                    .commit();
        };

        onAppClickListener = (vm) -> {
            try {
                PackageManager pm = getPackageManager();
                PackageInfo packageInfo = pm.getPackageInfo(vm.packageName, PackageManager.GET_PERMISSIONS);
                ReportFragment report = ReportFragment.newInstance(pm, vm, packageInfo, onTrackerClickListener);
                fragments.add(report);
                FragmentManager manager = getSupportFragmentManager();
                FragmentTransaction transaction = manager.beginTransaction();
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_right, R.anim.slide_in_left, R.anim.slide_out_left)
                        .replace(R.id.fragment_container, report)
                        .addToBackStack(null)
                        .commit();

                packageName = packageInfo.packageName;

                searchView.clearFocus();
                if (toolbarMenu != null)
                    (toolbarMenu.findItem(R.id.action_filter)).collapseActionView();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                assert imm != null;
                imm.hideSoftInputFromWindow(mainBinding.fragmentContainer.getWindowToken(), 0);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        };

        HomeFragment home = new HomeFragment();
        fragments.add(home);
        home.setNetworkListener(networkListener);
        home.setOnAppClickListener(onAppClickListener);


        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        transaction.replace(R.id.fragment_container, home)
                .commit();
        home.startRefresh();
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() == 0)
            finish();
        else {
            getSupportFragmentManager().popBackStack();
            fragments.remove(fragments.size() - 1);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        toolbarMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        MenuItem actionFilterItem = menu.findItem(R.id.action_filter);

        searchView = (SearchView) actionFilterItem.getActionView();
        searchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                searchView.setQuery(previousQuery, false);
            }
        });
        ImageView searchClose = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        if (searchClose != null) {
            searchClose.setOnClickListener(v -> {
                previousQuery = "";
                searchView.setQuery("", true);
            });
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                previousQuery = query.trim();
                if (!searchView.isIconified()) {
                    searchView.setIconified(true);
                }
                menu.findItem(R.id.action_filter).collapseActionView();
                HomeFragment home = (HomeFragment) fragments.get(0);
                home.filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                HomeFragment home = (HomeFragment) fragments.get(0);
                home.filter(newText);
                return false;
            }
        });
        MenuItem settingsMenuItem = menu.findItem(R.id.action_settings);
        Updatable fragment = fragments.get(fragments.size() - 1);
        settingsMenuItem.setVisible(fragment instanceof ReportFragment);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", packageName, null));
            try {
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                Snackbar bar = Snackbar.make(binding.fragmentContainer, R.string.no_settings, Snackbar.LENGTH_LONG);
                bar.show();
            }
            return true;
        } else if (item.getItemId() == R.id.action_filter_options) {
            View menuItemView = findViewById(R.id.action_filter_options);
            PopupMenu popup = new PopupMenu(binding.fragmentContainer.getContext(), menuItemView);
            popup.getMenuInflater()
                    .inflate(R.menu.popup_menu_filter, popup.getMenu());
            MenuItem filterByNameMI = popup.getMenu().findItem(R.id.filter_by_name);
            MenuItem lessTrackersMI = popup.getMenu().findItem(R.id.having_less_trackers);
            MenuItem mostTrackersMI = popup.getMenu().findItem(R.id.having_most_trackers);
            MenuItem lessPermissionsMI = popup.getMenu().findItem(R.id.having_less_permissions);
            MenuItem mostPermissionsMI = popup.getMenu().findItem(R.id.having_most_permissions);
            switch (order) {
                case LESS_TRACKERS:
                    lessTrackersMI.setChecked(true);
                    break;
                case MOST_TRACKERS:
                    mostTrackersMI.setChecked(true);
                    break;
                case LESS_PERMISSIONS:
                    lessPermissionsMI.setChecked(true);
                    break;
                case MOST_PERMISSIONS:
                    mostPermissionsMI.setChecked(true);
                    break;
                default:
                    filterByNameMI.setChecked(true);
            }
            popup.setOnMenuItemClickListener(filter_item -> {
                if (filter_item.getItemId() == R.id.filter_by_name) {
                    order = ComputeAppList.order.DEFAULT;
                } else if (filter_item.getItemId() == R.id.having_less_trackers) {
                    order = ComputeAppList.order.LESS_TRACKERS;
                } else if (filter_item.getItemId() == R.id.having_most_trackers) {
                    order = ComputeAppList.order.MOST_TRACKERS;
                } else if (filter_item.getItemId() == R.id.having_most_permissions) {
                    order = ComputeAppList.order.MOST_PERMISSIONS;
                } else if (filter_item.getItemId() == R.id.having_less_permissions) {
                    order = ComputeAppList.order.LESS_PERMISSIONS;
                }
                if (fragments != null && fragments.size() > 0 && fragments.get(0) instanceof HomeFragment) {
                    HomeFragment home = (HomeFragment) fragments.get(0);
                    home.displayAppListAsync(order);
                }
                return false;
            });
            popup.show();
        }
        return false;
    }

}
