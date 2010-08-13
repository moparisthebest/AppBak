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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import org.moparisthebest.openintents.intents.FileManagerIntents;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.Toast;

/**
 * This Activity allows you to either back up your applications to a text file,
 * which is handled in this Activity. Or to restore your applications from that
 * text file, which is implemented in RestoreList.
 */
public class AppBak extends Activity implements OnClickListener {
	private static final int PICK_RESTORE_FILE = 1;
	private static final int RESTORE_LIST = 2;

	private static final int MENU_WEBSITE = Menu.FIRST + 1;
	private static final int MENU_LICENSE = Menu.FIRST + 2;

	@Override
	protected void onCreate(Bundle savedState) {
		super.onCreate(savedState);

		setContentView(R.layout.main);

		// Setup the new shortcut button
		View view = findViewById(R.id.back_apps);
		if (view != null)
			view.setOnClickListener(this);
		view = findViewById(R.id.rest_apps);
		if (view != null)
			view.setOnClickListener(this);
	}

	public void askUserQuestion(int title, int question, String text) {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(title);
		alert.setMessage(question);

		// Set an EditText view to get user input
		final EditText input = new EditText(this);
		input.setText(text);
		alert.setView(input);

		alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				doBackupToFile(input.getText().toString());
			}
		});

		alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {

			}
		});

		alert.show();
	}

	public static void showMsg(int title, String message, Context context) {
		AlertDialog msg = new AlertDialog.Builder(context).create();
		msg.setTitle(title);
		msg.setMessage(message);
		msg.setButton(context.getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				return;
			}
		});
		msg.show();
	}

	public void showMsg(int title, String message) {
		showMsg(title, message, this);
	}

	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.back_apps: {
			// String fileToSaveTo = askUserQuestion(R.string.b, 0,
			// fileToSaveTo, null)

			askUserQuestion(R.string.back_apps, R.string.back_question, new SimpleDateFormat(
					"yyyy-MM-dd-HH.mm.ss").format(new Date())
					+ ".txt");

			break;
		}
		case R.id.rest_apps: {
			// Start the activity
			// startActivity(new Intent(this, RestoreList.class));
			// Intent intentBrowseFiles = new Intent(Intent.ACTION_GET_CONTENT);
			// intentBrowseFiles.setType("text/plain");
			// intentBrowseFiles.setType("*/*");
			// intentBrowseFiles.addCategory(Intent.CATEGORY_OPENABLE);
			// startActivity(intentBrowseFiles);
			Intent intentBrowseFiles = new Intent(FileManagerIntents.ACTION_PICK_FILE);
			intentBrowseFiles.setData(Uri.fromFile(new File(android.os.Environment
					.getExternalStorageDirectory(), "AppBak/")));
			intentBrowseFiles.putExtra(FileManagerIntents.EXTRA_TITLE,
					getString(R.string.rest_picker));
			intentBrowseFiles.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT,
					getString(R.string.rest_picker_button));
			// intentBrowseFiles.setType("text/plain");
			startActivityForResult(intentBrowseFiles, PICK_RESTORE_FILE);
			break;
		}
		}
	}

	private String backupThreadResult = null;

	private void doBackupToFile(String file) {
		File bakFolder = new File(android.os.Environment.getExternalStorageDirectory(), "AppBak/");
		if (!bakFolder.isDirectory()) {
			if (!bakFolder.mkdirs()) {
				showMsg(R.string.back_apps, getString(R.string.back_fail)
						+ bakFolder.getAbsolutePath());
				return;
			}
		}
		final File bakFile = new File(bakFolder, file);
		if (bakFile.exists())
			bakFile.delete();
		// final ProgressDialog hpd =
		// ProgressDialog.show(this,getString(R.string.back_apps),"Backing up apps now, this may take awhile",
		// false);
		final ProgressDialog pd = new ProgressDialog(this);
		pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pd.setTitle(R.string.back_apps);
		pd.setMessage(getString(R.string.back_warning));
		pd.setIndeterminate(false);
		pd.setOnDismissListener(new DialogInterface.OnDismissListener() {
			public void onDismiss(DialogInterface dialog) {
				showMsg(R.string.back_apps, backupThreadResult);
			}
		});
		pd.show();
		// final Handler pdHandler = new Handler();
		new Thread(new Runnable() {
			public void run() {

				try {
					final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
					mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
					PackageManager pm = getPackageManager();
					final List<ResolveInfo> pkgAppsList = pm.queryIntentActivities(mainIntent, 0);

					// ainIntent.set

					pd.setMax(pkgAppsList.size());

					PrintStream ps = new PrintStream(new FileOutputStream(bakFile));
					ps.println(getString(R.string.back_file_comment) + new Date().toLocaleString());
					for (ResolveInfo ri : pkgAppsList) {
						// ignore built in apps
						if (ri.activityInfo.applicationInfo.packageName.startsWith("com.android.")) {
							pd.incrementProgressBy(1);
							continue;
						}

						ps.printf("%s,%s,%s", ri.loadLabel(pm), pm.getPackageInfo(
								ri.activityInfo.applicationInfo.packageName, 0).versionName,
								ri.activityInfo.applicationInfo.packageName);
						ps.println();
						// Update the progress bar
						// pdHandler.post(new Runnable() {
						// public void run() {
						pd.incrementProgressBy(1);
						// }
						// });

					}
					ps.close();
					backupThreadResult = getText(R.string.back_success) + bakFile.getAbsolutePath();
				} catch (Exception e) {
					backupThreadResult = getString(R.string.fatal_error) + e.getMessage();
				}

				pd.dismiss();
			}
		}).start();

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode != RESULT_OK || data == null)
			return;

		switch (requestCode) {
		case PICK_RESTORE_FILE:
			Uri filename = data.getData();
			if (filename != null) {
				Intent restore = new Intent(this, RestoreList.class);
				restore.setData(filename);
				startActivityForResult(restore, RESTORE_LIST);
			}
			break;
		case RESTORE_LIST:
			int title = data.getIntExtra("title", R.string.rest_apps);
			String message = data.getStringExtra("message");
			showMsg(title, message, this);
			break;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		menu.add(0, MENU_WEBSITE, 0, R.string.menu_website).setIcon(R.drawable.ic_menu_help)
				.setShortcut('0', 'w');

		menu.add(0, MENU_LICENSE, 0, R.string.menu_license)
				.setIcon(R.drawable.ic_menu_info_details).setShortcut('0', 'l');

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case MENU_LICENSE:
			Scanner scan = new Scanner(getResources().openRawResource(R.raw.license_short));
			StringBuilder sb = new StringBuilder();
			while (scan.hasNextLine())
				sb.append(scan.nextLine()).append("\n");

			this.showMsg(R.string.app_name, sb.toString());
			return true;

		case MENU_WEBSITE:
			// Build the intent to go to the website
			Intent result = new Intent(Intent.ACTION_VIEW, Uri
					.parse("http://android.moparisthebest.org/"));
			try {
				startActivity(result);
			} catch (android.content.ActivityNotFoundException e) {
				this.showMsg(R.string.app_name, getString(R.string.no_browser));
			}
			return true;

		}
		return super.onOptionsItemSelected(item);

	}

}
