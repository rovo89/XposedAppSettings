package de.robv.android.xposed.mods.appsettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.LinkMovementMethod;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.mods.appsettings.FilterItemComponent.FilterState;
import de.robv.android.xposed.mods.appsettings.settings.ApplicationSettings;
import de.robv.android.xposed.mods.appsettings.settings.PermissionsListAdapter;

@SuppressLint("WorldReadableFiles")
public class XposedModActivity extends Activity {

	private ArrayList<ApplicationInfo> appList = new ArrayList<ApplicationInfo>();
	private ArrayList<ApplicationInfo> filteredAppList = new ArrayList<ApplicationInfo>();

	private Map<String, Set<String>> permUsage = new HashMap<String, Set<String>>();
	private Map<String, Set<String>> sharedUsers = new HashMap<String, Set<String>>();
	private Map<String, String> pkgSharedUsers = new HashMap<String, String>();

	private String nameFilter;
	private FilterState filterAppType;
	private FilterState filterActive;
	private String filterPermissionUsage;

	private List<SettingInfo> settings;

	private static File prefsFile = new File(Environment.getDataDirectory(),
			"data/" + Common.MY_PACKAGE_NAME + "/shared_prefs/" + Common.PREFS + ".xml");
	private static File backupPrefsFile = new File(Environment.getExternalStorageDirectory(), "AppSettings-Backup.xml");
	private SharedPreferences prefs;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTitle(R.string.app_name);
		super.onCreate(savedInstanceState);

		prefsFile.setReadable(true, false);
		prefs = getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);
		loadSettings();

		setContentView(R.layout.main);

		ListView list = (ListView) findViewById(R.id.lstApps);
		registerForContextMenu(list);
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// Open settings activity when clicking on an application
				String pkgName = ((TextView) view.findViewById(R.id.app_package)).getText().toString();
				Intent i = new Intent(getApplicationContext(), ApplicationSettings.class);
				i.putExtra("package", pkgName);
				startActivityForResult(i, position);
			}
		});

		// Load the list of apps in the background
		new PrepareAppsAdapter().execute();
	}

	private void loadSettings() {
		settings = new ArrayList<SettingInfo>();

		settings.add(new SettingInfo(Common.PREF_DPI, getString(R.string.settings_dpi)));
		settings.add(new SettingInfo(Common.PREF_FONT_SCALE, getString(R.string.settings_fontscale)));
		settings.add(new SettingInfo(Common.PREF_SCREEN, getString(R.string.settings_screen)));
		settings.add(new SettingInfo(Common.PREF_XLARGE, getString(R.string.settings_xlargeres)));
		settings.add(new SettingInfo(Common.PREF_LOCALE, getString(R.string.settings_locale)));
		settings.add(new SettingInfo(Common.PREF_FULLSCREEN, getString(R.string.settings_fullscreen)));
		settings.add(new SettingInfo(Common.PREF_NO_TITLE, getString(R.string.settings_notitle)));
		settings.add(new SettingInfo(Common.PREF_SCREEN_ON, getString(R.string.settings_screenon)));
		settings.add(new SettingInfo(Common.PREF_ALLOW_ON_LOCKSCREEN, getString(R.string.settings_showwhenlocked)));
		settings.add(new SettingInfo(Common.PREF_RESIDENT, getString(R.string.settings_resident)));
		settings.add(new SettingInfo(Common.PREF_NO_FULLSCREEN_IME, getString(R.string.settings_nofullscreenime)));
		settings.add(new SettingInfo(Common.PREF_ORIENTATION, getString(R.string.settings_orientation)));
		settings.add(new SettingInfo(Common.PREF_INSISTENT_NOTIF, getString(R.string.settings_insistentnotif)));
		settings.add(new SettingInfo(Common.PREF_NO_BIG_NOTIFICATIONS, getString(R.string.settings_nobignotif)));
		settings.add(new SettingInfo(Common.PREF_NOTIF_PRIORITY, getString(R.string.settings_notifpriority)));
		settings.add(new SettingInfo(Common.PREF_RECENTS_MODE, getString(R.string.settings_recents_mode)));
		settings.add(new SettingInfo(Common.PREF_MUTE, getString(R.string.settings_mute)));
		settings.add(new SettingInfo(Common.PREF_LEGACY_MENU, getString(R.string.settings_legacy_menu)));
		settings.add(new SettingInfo(Common.PREF_REVOKEPERMS, getString(R.string.settings_permissions)));
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		// Refresh the app that was just edited, if it's visible in the list
		ListView list = (ListView) findViewById(R.id.lstApps);
		if (requestCode >= list.getFirstVisiblePosition() &&
				requestCode <= list.getLastVisiblePosition()) {
			View v = list.getChildAt(requestCode - list.getFirstVisiblePosition());
			list.getAdapter().getView(requestCode, v, list);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_export:
			doExport();
			return true;
		case R.id.menu_import:
			doImport();
			return true;
		case R.id.menu_about:
			showAboutDialog();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void doExport() {
		new ExportTask().execute(backupPrefsFile);
	}

	private void doImport() {
		if (!backupPrefsFile.exists()) {
			Toast.makeText(this, getString(R.string.imp_exp_file_doesnt_exist, backupPrefsFile.getAbsolutePath()),
					Toast.LENGTH_LONG).show();
			return;
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.menu_import);
		builder.setMessage(R.string.imp_exp_confirm);
		builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				new ImportTask().execute(backupPrefsFile);
			}
		});
		builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// Do nothing
				dialog.dismiss();
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private class ExportTask extends AsyncTask<File, String, String> {
		@Override
		protected String doInBackground(File... params) {
			File outFile = params[0];
			try {
				copyFile(prefsFile, outFile);
				return getString(R.string.imp_exp_exported, outFile.getAbsolutePath());
			} catch (IOException ex) {
				return getString(R.string.imp_exp_export_error, ex.getMessage());
			}
		}

		@Override
		protected void onPostExecute(String result) {
			Toast.makeText(XposedModActivity.this, result, Toast.LENGTH_LONG).show();
		}
	}

	private class ImportTask extends AsyncTask<File, String, String> {
		private boolean importSuccessful;

		@Override
		protected String doInBackground(File... params) {
			importSuccessful = false;

			File inFile = params[0];
			String tempFilename = Common.PREFS + "-new";
			File newPrefsFile = new File(prefsFile.getParentFile(), tempFilename + ".xml");
			// Make sure the shared_prefs folder exists, with the proper permissions
			getSharedPreferences(tempFilename, Context.MODE_WORLD_READABLE).edit().commit();
			try {
				copyFile(inFile, newPrefsFile);
			} catch (IOException ex) {
				return getString(R.string.imp_exp_import_error, ex.getMessage());
			}

			newPrefsFile.setReadable(true, false);
			SharedPreferences newPrefs = getSharedPreferences(tempFilename, Context.MODE_WORLD_READABLE | Context.MODE_MULTI_PROCESS);
			if (newPrefs.getAll().size() == 0) {
				// No entries in imported file, discard it
				newPrefsFile.delete();
				return getString(R.string.imp_exp_invalid_import_file, inFile.getAbsoluteFile());
			} else {
				if (newPrefsFile.renameTo(prefsFile)) {
					importSuccessful = true;
				} else {
					prefsFile.delete();
					if (newPrefsFile.renameTo(prefsFile))
						importSuccessful = true;
				}

				return getString(R.string.imp_exp_imported);
			}
		}

		@Override
		protected void onPostExecute(String result) {
			if (importSuccessful) {
				// Refresh preferences
				prefs = getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE | Context.MODE_MULTI_PROCESS);
				// Refresh listed apps (account for filters)
				AppListAdapter appListAdapter = (AppListAdapter) ((ListView) findViewById(R.id.lstApps)).getAdapter();
				appListAdapter.getFilter().filter(nameFilter);
			}

			Toast.makeText(XposedModActivity.this, result, Toast.LENGTH_LONG).show();
		}
	}


	private static void copyFile(File source, File dest) throws IOException {
		InputStream in = null;
		OutputStream out = null;
		boolean success = false;
		try {
			in = new FileInputStream(source);
			out = new FileOutputStream(dest);
			byte[] buf = new byte[10 * 1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			out.flush();
			out.close();
			out = null;
			success = true;
		} catch (IOException ex) {
			throw ex;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (Exception ex) {
				}
			}
			if (out != null) {
				try {
					out.close();
				} catch (Exception ex) {
				}
			}
			if (!success) {
				dest.delete();
			}
		}
	}


	private void showAboutDialog() {
		View vAbout;
		vAbout = getLayoutInflater().inflate(R.layout.about, null);

		// Warn if the module is not active
		if (!isModActive())
			vAbout.findViewById(R.id.about_notactive).setVisibility(View.VISIBLE);

		// Display the resources translator, or hide it if none
		String translator = getResources().getString(R.string.translator);
		TextView txtTranslation = (TextView) vAbout.findViewById(R.id.about_translation);
		if (translator.isEmpty()) {
			txtTranslation.setVisibility(View.GONE);
		} else {
			txtTranslation.setText(getString(R.string.app_translation, translator));
			txtTranslation.setMovementMethod(LinkMovementMethod.getInstance());
		}

		// Clickable links
		((TextView) vAbout.findViewById(R.id.about_title)).setMovementMethod(LinkMovementMethod.getInstance());

		// Display the correct version
		try {
			((TextView) vAbout.findViewById(R.id.version)).setText(getString(R.string.app_version,
					getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
		} catch (NameNotFoundException e) {
		}

		// Prepare and show the dialog
		Builder dlgBuilder = new AlertDialog.Builder(this);
		dlgBuilder.setTitle(R.string.app_name);
		dlgBuilder.setCancelable(true);
		dlgBuilder.setIcon(R.drawable.ic_launcher);
		dlgBuilder.setPositiveButton(android.R.string.ok, null);
		dlgBuilder.setView(vAbout);
		dlgBuilder.show();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		if (v.getId() == R.id.lstApps) {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
			ApplicationInfo appInfo = filteredAppList.get(info.position);

			menu.setHeaderTitle(getPackageManager().getApplicationLabel(appInfo));
			getMenuInflater().inflate(R.menu.menu_app, menu);
			menu.findItem(R.id.menu_save).setVisible(false);

			ApplicationSettings.updateMenuEntries(getApplicationContext(), menu, appInfo.packageName);
		} else {
			super.onCreateContextMenu(menu, v, menuInfo);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		String pkgName = filteredAppList.get(info.position).packageName;
		if (item.getItemId() == R.id.menu_app_launch) {
			Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage(pkgName);
			startActivity(LaunchIntent);
			return true;
		} else if (item.getItemId() == R.id.menu_app_settings) {
			startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkgName)));
			return true;
		} else if (item.getItemId() == R.id.menu_app_store) {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkgName)));
			return true;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_SEARCH && (event.getFlags() & KeyEvent.FLAG_CANCELED) == 0) {
			SearchView searchV = (SearchView) findViewById(R.id.searchApp);
			if (searchV.isShown()) {
				searchV.setIconified(false);
				return true;
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	private static boolean isModActive() {
		return false;
	}


	@SuppressLint("DefaultLocale")
	private void loadApps(ProgressDialog dialog) {

		appList.clear();
		permUsage.clear();
		sharedUsers.clear();
		pkgSharedUsers.clear();

		PackageManager pm = getPackageManager();
		List<PackageInfo> pkgs = getPackageManager().getInstalledPackages(PackageManager.GET_PERMISSIONS);
		dialog.setMax(pkgs.size());
		int i = 1;
		for (PackageInfo pkgInfo : pkgs) {
			dialog.setProgress(i++);

			ApplicationInfo appInfo = pkgInfo.applicationInfo;
			if (appInfo == null)
				continue;

			appInfo.name = appInfo.loadLabel(pm).toString();
			appList.add(appInfo);

			String[] perms = pkgInfo.requestedPermissions;
			if (perms != null)
				for (String perm : perms) {
					Set<String> permUsers = permUsage.get(perm);
					if (permUsers == null) {
						permUsers = new TreeSet<String>();
						permUsage.put(perm, permUsers);
					}
					permUsers.add(pkgInfo.packageName);
				}

			if (pkgInfo.sharedUserId != null) {
				Set<String> sharedUserPackages = sharedUsers.get(pkgInfo.sharedUserId);
				if (sharedUserPackages == null) {
					sharedUserPackages = new TreeSet<String>();
					sharedUsers.put(pkgInfo.sharedUserId, sharedUserPackages);
				}
				sharedUserPackages.add(pkgInfo.packageName);

				pkgSharedUsers.put(pkgInfo.packageName, pkgInfo.sharedUserId);
			}
		}

		Collections.sort(appList, new Comparator<ApplicationInfo>() {
			@Override
			public int compare(ApplicationInfo lhs, ApplicationInfo rhs) {
				if (lhs.name == null) {
					return -1;
				} else if (rhs.name == null) {
					return 1;
				} else {
					return lhs.name.toUpperCase().compareTo(rhs.name.toUpperCase());
				}
			}
		});
	}

	private void prepareAppList() {
		final AppListAdapter appListAdapter = new AppListAdapter(XposedModActivity.this, appList);

		((ListView) findViewById(R.id.lstApps)).setAdapter(appListAdapter);
		((SearchView) findViewById(R.id.searchApp)).setOnQueryTextListener(new SearchView.OnQueryTextListener() {

			@Override
			public boolean onQueryTextSubmit(String query) {
				nameFilter = query;
				appListAdapter.getFilter().filter(nameFilter);
				((SearchView) findViewById(R.id.searchApp)).clearFocus();
				return false;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				nameFilter = newText;
				appListAdapter.getFilter().filter(nameFilter);
				return false;
			}

		});

		((ImageButton) findViewById(R.id.btnFilter)).setOnClickListener(new View.OnClickListener() {
			Dialog filterDialog;
			Map<String, FilterItemComponent> filterComponents;

			@Override
			public void onClick(View v) {
				// set up dialog
				filterDialog = new Dialog(XposedModActivity.this);
				filterDialog.setContentView(R.layout.filter_dialog);
				filterDialog.setTitle(R.string.filter_title);
				filterDialog.setCancelable(true);
				filterDialog.setOwnerActivity(XposedModActivity.this);

				LinearLayout entriesView = (LinearLayout) filterDialog.findViewById(R.id.filter_entries);
				filterComponents = new HashMap<String, FilterItemComponent>();
				for (SettingInfo setting : settings) {
					FilterItemComponent component = new FilterItemComponent(XposedModActivity.this, setting.label, null, null, null);
					component.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
					component.setFilterState(setting.filter);
					entriesView.addView(component);
					filterComponents.put(setting.settingKey, component);
				}

				// Block or unblock the details based on the Active setting
				enableFilterDetails(!FilterState.UNCHANGED.equals(filterActive));
				((FilterItemComponent) filterDialog.findViewById(R.id.fltActive)).setOnFilterChangeListener(new FilterItemComponent.OnFilterChangeListener() {
					@Override
					public void onFilterChanged(FilterItemComponent item, FilterState state) {
						enableFilterDetails(!FilterState.UNCHANGED.equals(state));
					}
				});

				// Close the dialog with the possible options
				((Button) filterDialog.findViewById(R.id.btnFilterCancel)).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						filterDialog.dismiss();
					}
				});
				((Button) filterDialog.findViewById(R.id.btnFilterClear)).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						filterAppType = FilterState.ALL;
						filterActive = FilterState.ALL;
						for (SettingInfo setting : settings)
							setting.filter = FilterState.ALL;

						filterDialog.dismiss();
						appListAdapter.getFilter().filter(nameFilter);
					}
				});
				((Button) filterDialog.findViewById(R.id.btnFilterApply)).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						filterAppType = ((FilterItemComponent) filterDialog.findViewById(R.id.fltAppType)).getFilterState();
						filterActive = ((FilterItemComponent) filterDialog.findViewById(R.id.fltActive)).getFilterState();
						for (SettingInfo setting : settings)
							setting.filter = filterComponents.get(setting.settingKey).getFilterState();

						filterDialog.dismiss();
						appListAdapter.getFilter().filter(nameFilter);
					}
				});

				filterDialog.show();
			}

			private void enableFilterDetails(boolean enable) {
				((FilterItemComponent) filterDialog.findViewById(R.id.fltAppType)).setEnabled(true);
				for (FilterItemComponent component : filterComponents.values())
					component.setEnabled(enable);
			}
		});

		((ImageButton) findViewById(R.id.btnPermsFilter)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				AlertDialog.Builder bld = new AlertDialog.Builder(XposedModActivity.this);
				bld.setCancelable(true);
				bld.setTitle(R.string.perms_filter_title);

				List<String> perms = new LinkedList<String>(permUsage.keySet());
				Collections.sort(perms);
				List<PermissionInfo> items = new ArrayList<PermissionInfo>();
				PackageManager pm = getPackageManager();
				for (String perm : perms) {
					try {
						items.add(pm.getPermissionInfo(perm, 0));
					} catch (NameNotFoundException e) {
						PermissionInfo unknownPerm = new PermissionInfo();
						unknownPerm.name = perm;
						items.add(unknownPerm);
					}
				}
				final PermissionsListAdapter adapter = new PermissionsListAdapter(XposedModActivity.this, items, new HashSet<String>(), false);
				bld.setAdapter(adapter, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						filterPermissionUsage = adapter.getItem(which).name;
						appListAdapter.getFilter().filter(nameFilter);
					}
				});

				final View permsView = getLayoutInflater().inflate(R.layout.permission_search, null);
				((SearchView) permsView.findViewById(R.id.searchPermission)).setOnQueryTextListener(new SearchView.OnQueryTextListener() {

					@Override
					public boolean onQueryTextSubmit(String query) {
						adapter.getFilter().filter(query);
						((SearchView) permsView.findViewById(R.id.searchPermission)).clearFocus();
						return false;
					}

					@Override
					public boolean onQueryTextChange(String newText) {
						adapter.getFilter().filter(newText);
						return false;
					}
				});
				bld.setView(permsView);

				bld.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						filterPermissionUsage = null;
						appListAdapter.getFilter().filter(nameFilter);
					}
				});

				AlertDialog dialog = bld.create();
				dialog.getListView().setFastScrollEnabled(true);

				dialog.show();
			}
		});

	}


	// Handle background loading of apps
	private class PrepareAppsAdapter extends AsyncTask<Void,Void,AppListAdapter> {
		ProgressDialog dialog;

		@Override
		protected void onPreExecute() {
			dialog = new ProgressDialog(((ListView) findViewById(R.id.lstApps)).getContext());
			dialog.setMessage(getString(R.string.app_loading));
			dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			dialog.setCancelable(false);
			dialog.show();
		}

		@Override
		protected AppListAdapter doInBackground(Void... params) {
			if (appList.size() == 0) {
				loadApps(dialog);
			}
			return null;
		}

		@Override
		protected void onPostExecute(final AppListAdapter result) {
			prepareAppList();

			try {
				dialog.dismiss();
			} catch (Exception e) {

			}
		}
	}


	/** Hold filter state and other info for each setting key */
	private static class SettingInfo {
		String settingKey;
		String label;
		FilterState filter;

		SettingInfo(String setting, String label) {
			this.settingKey = setting;
			this.label = label;
			filter = FilterState.ALL;
		}
	}


	private class AppListFilter extends Filter {

		private AppListAdapter adapter;

		AppListFilter(AppListAdapter adapter) {
			super();
			this.adapter = adapter;
		}

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			// NOTE: this function is *always* called from a background thread, and
			// not the UI thread.

			ArrayList<ApplicationInfo> items = new ArrayList<ApplicationInfo>();
			synchronized (this) {
				items.addAll(appList);
			}

			SharedPreferences prefs = getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);

			FilterResults result = new FilterResults();
			if (constraint != null && constraint.length() > 0) {
				Pattern regexp = Pattern.compile(constraint.toString(), Pattern.LITERAL | Pattern.CASE_INSENSITIVE);
				for (Iterator<ApplicationInfo> i = items.iterator(); i.hasNext(); ) {
					ApplicationInfo app = i.next();
					if (!regexp.matcher(app.name == null ? "" : app.name).find()
							&& !regexp.matcher(app.packageName).find()) {
						i.remove();
					}
				}
			}
			for (Iterator<ApplicationInfo> i = items.iterator(); i.hasNext(); ) {
				ApplicationInfo app = i.next();
				if (filteredOut(prefs, app))
					i.remove();
			}

			result.values = items;
			result.count = items.size();

			return result;
		}

		private boolean filteredOut(SharedPreferences prefs, ApplicationInfo app) {
			String packageName = app.packageName;
			boolean isUser = (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0;

			// AppType = Overridden is used for USER apps
			if (filteredOut(isUser, filterAppType))
				return true;

			if (filteredOut(prefs.getBoolean(packageName + Common.PREF_ACTIVE, false), filterActive))
				return true;

			if (FilterState.UNCHANGED.equals(filterActive))
				// Ignore additional filters
				return false;

			for (SettingInfo setting : settings)
				if (filteredOut(prefs.contains(packageName + setting.settingKey), setting.filter))
					return true;

			if (filterPermissionUsage != null) {
				Set<String> pkgsForPerm = permUsage.get(filterPermissionUsage);
				if (!pkgsForPerm.contains(packageName))
					return true;
			}

			return false;
		}

		private boolean filteredOut(boolean set, FilterState state) {
			if (state == null)
				return false;

			switch (state) {
			case UNCHANGED:
				return set;
			case OVERRIDDEN:
				return !set;
			default:
				return false;
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			// NOTE: this function is *always* called from the UI thread.
			filteredAppList = (ArrayList<ApplicationInfo>) results.values;
			adapter.notifyDataSetChanged();
			adapter.clear();
			for (int i = 0, l = filteredAppList.size(); i < l; i++) {
				adapter.add(filteredAppList.get(i));
			}
			adapter.notifyDataSetInvalidated();
		}
	}

	static class AppListViewHolder {
		TextView app_name;
		TextView app_package;
		ImageView app_icon;

		AsyncTask<AppListViewHolder, Void, Drawable> imageLoader;
	}

	class AppListAdapter extends ArrayAdapter<ApplicationInfo> implements SectionIndexer {

		private Map<String, Integer> alphaIndexer;
		private String[] sections;
		private Filter filter;
		private LayoutInflater inflater;
		private Drawable defaultIcon;

		@SuppressLint("DefaultLocale")
		public AppListAdapter(Context context, List<ApplicationInfo> items) {
			super(context, R.layout.app_list_item, new ArrayList<ApplicationInfo>(items));

			filteredAppList.addAll(items);

			filter = new AppListFilter(this);
			inflater = getLayoutInflater();
			defaultIcon = getResources().getDrawable(android.R.drawable.sym_def_app_icon);

			alphaIndexer = new HashMap<String, Integer>();
			for (int i = filteredAppList.size() - 1; i >= 0; i--) {
				ApplicationInfo app = filteredAppList.get(i);
				String appName = app.name;
				String firstChar;
				if (appName == null || appName.length() < 1) {
					firstChar = "@";
				} else {
					firstChar = appName.substring(0, 1).toUpperCase();
					if (firstChar.charAt(0) > 'Z' || firstChar.charAt(0) < 'A')
						firstChar = "@";
				}

				alphaIndexer.put(firstChar, i);
			}

			Set<String> sectionLetters = alphaIndexer.keySet();

			// create a list from the set to sort
			List<String> sectionList = new ArrayList<String>(sectionLetters);

			Collections.sort(sectionList);

			sections = new String[sectionList.size()];

			sectionList.toArray(sections);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			// Load or reuse the view for this row
			View row = convertView;
			AppListViewHolder holder;
			if (row == null) {
				row = inflater.inflate(R.layout.app_list_item, parent, false);
				holder = new AppListViewHolder();
				holder.app_name = (TextView) row.findViewById(R.id.app_name);
				holder.app_package = (TextView) row.findViewById(R.id.app_package);
				holder.app_icon = (ImageView) row.findViewById(R.id.app_icon);
				row.setTag(holder);
			} else {
				holder = (AppListViewHolder) row.getTag();
				holder.imageLoader.cancel(true);
			}

			final ApplicationInfo app = filteredAppList.get(position);

			holder.app_name.setText(app.name == null ? "" : app.name);
			holder.app_package.setTextColor(prefs.getBoolean(app.packageName + Common.PREF_ACTIVE, false)
					? Color.RED : Color.parseColor("#0099CC"));
			holder.app_package.setText(app.packageName);
			holder.app_icon.setImageDrawable(defaultIcon);

			holder.imageLoader = new AsyncTask<AppListViewHolder, Void, Drawable>() {
				private AppListViewHolder v;

				@Override
				protected Drawable doInBackground(AppListViewHolder... params) {
					v = params[0];
					return app.loadIcon(getPackageManager());
				}

				@Override
				protected void onPostExecute(Drawable result) {
					v.app_icon.setImageDrawable(result);
				}
			}.execute(holder);

			return row;
		}

		@SuppressLint("DefaultLocale")
		@Override
		public void notifyDataSetInvalidated() {
			alphaIndexer.clear();
			for (int i = filteredAppList.size() - 1; i >= 0; i--) {
				ApplicationInfo app = filteredAppList.get(i);
				String appName = app.name;
				String firstChar;
				if (appName == null || appName.length() < 1) {
					firstChar = "@";
				} else {
					firstChar = appName.substring(0, 1).toUpperCase();
					if (firstChar.charAt(0) > 'Z' || firstChar.charAt(0) < 'A')
						firstChar = "@";
				}
				alphaIndexer.put(firstChar, i);
			}

			Set<String> keys = alphaIndexer.keySet();
			Iterator<String> it = keys.iterator();
			ArrayList<String> keyList = new ArrayList<String>();
			while (it.hasNext()) {
				keyList.add(it.next());
			}

			Collections.sort(keyList);
			sections = new String[keyList.size()];
			keyList.toArray(sections);

			super.notifyDataSetInvalidated();
		}

		@Override
		public int getPositionForSection(int section) {
			if (section >= sections.length)
				return filteredAppList.size() - 1;

			return alphaIndexer.get(sections[section]);
		}

		@Override
		public int getSectionForPosition(int position) {

			// Iterate over the sections to find the closest index
			// that is not greater than the position
			int closestIndex = 0;
			int latestDelta = Integer.MAX_VALUE;

			for (int i = 0; i < sections.length; i++) {
				int current = alphaIndexer.get(sections[i]);
				if (current == position) {
					// If position matches an index, return it immediately
					return i;
				} else if (current < position) {
					// Check if this is closer than the last index we inspected
					int delta = position - current;
					if (delta < latestDelta) {
						closestIndex = i;
						latestDelta = delta;
					}
				}
			}

			return closestIndex;
		}

		@Override
		public Object[] getSections() {
			return sections;
		}

		@Override
		public Filter getFilter() {
			return filter;
		}
	}

}
