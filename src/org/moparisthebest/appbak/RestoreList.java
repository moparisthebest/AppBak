/**
 *  AppBak is an Android app that assists in backing up and restoring apps.
 *  Copyright (C) 2010 Travis Burtrum (moparisthebest)
 *
 *  This file is part of AppBak.
 *
 *  AppBak is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AppBak is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with AppBak.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.moparisthebest.appbak;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class RestoreList extends ListActivity {
	PackageManager mPackageManager;

	/**
	 * This class is used to store the info retrieved from the backup file and
	 * keep it sorted by the app name and allow us to retrieve the package name
	 * when it is chosen.
	 */
	private final class AppInfoWrapper {
		public String appName, version, packageName;

		public AppInfoWrapper(String appName, String version, String packageName) {
			this.appName = appName;
			this.version = version;
			this.packageName = packageName;
		}

		@Override
		public String toString() {
			return appName;
		}

	}

	@SuppressWarnings("unchecked")
	private class AppNameAdapter extends ArrayAdapter {
		LayoutInflater mInflater;

		public AppNameAdapter(Activity activity, List activities) {
			super(activity, 0, activities);
			mInflater = activity.getLayoutInflater();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final String info = ((AppInfoWrapper) getItem(position)).appName;

			View view = convertView;
			if (view == null) {
				// Inflate the view and cache the pointer to the text view
				view = mInflater.inflate(android.R.layout.simple_list_item_1, parent, false);
				view.setTag(view.findViewById(android.R.id.text1));
			}

			final TextView textView = (TextView) view.getTag();
			textView.setText(info);

			return view;
		}
		
	}

	private ArrayList<AppInfoWrapper> buildList(ArrayList<AppInfoWrapper> packageToApp) {
		// Load the activities
		List<PackageInfo> installedApps = this.getPackageManager().getInstalledPackages(
				PackageManager.GET_ACTIVITIES);

		HashSet<String> installed = new HashSet<String>(installedApps.size());
		for (PackageInfo pi : installedApps)
			installed.add(pi.packageName);// pi.applicationInfo.packageName
		// Make the wrappers
		ArrayList<AppInfoWrapper> activities = new ArrayList<AppInfoWrapper>(packageToApp.size());
		for (AppInfoWrapper aiw : packageToApp)
			if (!installed.contains(aiw.packageName))
				activities.add(aiw);
		return activities;
	}

	private ArrayList<AppInfoWrapper> parseFile(File f) {
		ArrayList<AppInfoWrapper> ret = new ArrayList<AppInfoWrapper>();
		boolean readAtLeastOneLine = false;
		try {
			Scanner scan = new Scanner(f);
			while (scan.hasNextLine()) {
				String[] parts = scan.nextLine().split(",");
				// parts should have 3 parts, if not ignore the line
				if (parts.length != 3)
					continue;
				// 0 : app name
				// 1 : app version
				// 2 : app package name
				ret.add(new AppInfoWrapper(parts[0], parts[1], parts[2]));
				readAtLeastOneLine = true;
			}
		} catch (Exception e) {
			// this just means that the file couldn't be found, or some I/O
			// error.
		}
		if (!readAtLeastOneLine)
			returnToCaller(R.string.rest_apps, getText(R.string.rest_fail) + f.getAbsolutePath());
		return ret;
	}

	private void returnToCaller(int title, String message) {
		Intent intent = getIntent();
		intent.putExtra("title", title);
		intent.putExtra("message", message);
		setResult(RESULT_OK, intent);
		finish();
	}

	@Override
	protected void onCreate(Bundle savedState) {
		super.onCreate(savedState);

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.list);

		getListView().setTextFilterEnabled(true);

		mPackageManager = getPackageManager();

		// load the file
		/*
		 * String state = Environment.getExternalStorageState(); if
		 * (Environment.MEDIA_MOUNTED.equals(state)) { mExternalStorageAvailable
		 * = mExternalStorageWriteable = true; } else if
		 * (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
		 * mExternalStorageAvailable = true; mExternalStorageWriteable = false;
		 * } else { mExternalStorageAvailable = mExternalStorageWriteable =
		 * false; }
		 */
		File f = null;
		// f = new File(android.os.Environment.getExternalStorageDirectory(),
		// "appbak.txt");
		try {
			f = new File(new java.net.URI(this.getIntent().getData().toString()));
		} catch (URISyntaxException e) {
			returnToCaller(R.string.rest_apps, getString(R.string.rest_bad_file)); 
		}

		ArrayList<AppInfoWrapper> packageToApp = parseFile(f);

		// Start loading the data
		setProgressBarIndeterminateVisibility(true);

		ArrayList<AppInfoWrapper> activities = buildList(packageToApp);

		if (activities.size() == 0)
			returnToCaller(R.string.rest_apps, getString(R.string.rest_success));

		setProgressBarIndeterminateVisibility(false);
		setListAdapter(new AppNameAdapter(this, activities));
	}

	@Override
	protected void onListItemClick(ListView list, View view, int position, long id) {
		AppInfoWrapper ob = (AppInfoWrapper) getListAdapter().getItem(position);
		String name = ob.packageName;
		((AppNameAdapter) getListAdapter()).remove(ob);

		// Build the intent to view the app in the market
		Uri uri = Uri.parse("market://details?id=" + name);
		// Uri uri = Uri.parse("market://search?q=pname:"+name);
		// Uri uri =
		// Uri.parse("http://market.android.com/search?q=pname:"+name);
		Intent result = new Intent(Intent.ACTION_VIEW, uri);

		try {
			startActivity(result);
		} catch (android.content.ActivityNotFoundException e) {
			returnToCaller(R.string.rest_apps, getString(R.string.rest_no_market));
		}
		
		if(getListAdapter().isEmpty())
			returnToCaller(R.string.rest_apps, getString(R.string.rest_success_finish));

	}
}