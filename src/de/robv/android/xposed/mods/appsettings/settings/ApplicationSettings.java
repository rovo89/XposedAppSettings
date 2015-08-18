package de.robv.android.xposed.mods.appsettings.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.mods.appsettings.Common;
import de.robv.android.xposed.mods.appsettings.R;

@SuppressLint("WorldReadableFiles")
public class ApplicationSettings extends Activity {

	private Switch swtActive;

	private String pkgName;
	private SharedPreferences prefs;
	private Set<String> settingKeys;
	private Map<String, Object> initialSettings;
	private Set<String> disabledPermissions;
	private boolean allowRevoking;
	private Intent parentIntent;

	private LocaleList localeList;


	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		swtActive = new Switch(this);
		getActionBar().setCustomView(swtActive);
		getActionBar().setDisplayShowCustomEnabled(true);

		setContentView(R.layout.app_settings);

		Intent i = getIntent();
		parentIntent = i;

		prefs = getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);

		ApplicationInfo app;
		try {
			app = getPackageManager().getApplicationInfo(i.getStringExtra("package"), 0);
			pkgName = app.packageName;
		} catch (NameNotFoundException e) {
			// Close the dialog gracefully, package might have been uninstalled
			finish();
			return;
		}

		// Display app info
		((TextView) findViewById(R.id.app_label)).setText(app.loadLabel(getPackageManager()));
		((TextView) findViewById(R.id.package_name)).setText(app.packageName);
		((ImageView) findViewById(R.id.app_icon)).setImageDrawable(app.loadIcon(getPackageManager()));

		// Update switch of active/inactive tweaks
		if (prefs.getBoolean(pkgName + Common.PREF_ACTIVE, false)) {
			swtActive.setChecked(true);
			findViewById(R.id.viewTweaks).setVisibility(View.VISIBLE);
		} else {
			swtActive.setChecked(false);
			findViewById(R.id.viewTweaks).setVisibility(View.GONE);
		}
		// Toggle the visibility of the lower panel when changed
		swtActive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				findViewById(R.id.viewTweaks).setVisibility(isChecked ? View.VISIBLE : View.GONE);
			}
		});

		// Update DPI field
		if (prefs.getBoolean(pkgName + Common.PREF_ACTIVE, false)) {
			((EditText) findViewById(R.id.txtDPI)).setText(String.valueOf(
				prefs.getInt(pkgName + Common.PREF_DPI, 0)));
		} else {
			((EditText) findViewById(R.id.txtDPI)).setText("0");
		}

		// Update Font Scaling field
		if (prefs.getBoolean(pkgName + Common.PREF_ACTIVE, false)) {
			((EditText) findViewById(R.id.txtFontScale)).setText(String.valueOf(prefs.getInt(pkgName + Common.PREF_FONT_SCALE, 100)));
		} else {
			((EditText) findViewById(R.id.txtFontScale)).setText("100");
		}

		// Load and render current screen setting + possible options
		int screen = prefs.getInt(pkgName + Common.PREF_SCREEN, 0);
		if (screen < 0 || screen >= Common.swdp.length)
			screen = 0;
		final int selectedScreen = screen;

		Spinner spnScreen = (Spinner) findViewById(R.id.spnScreen);
		List<String> lstScreens = new ArrayList<String>(Common.swdp.length);
		lstScreens.add(getString(R.string.settings_default));
		for (int j = 1; j < Common.swdp.length; j++)
			lstScreens.add(String.format("%dx%d", Common.wdp[j], Common.hdp[j]));
		ArrayAdapter<String> screenAdapter = new ArrayAdapter<String>(this,
			android.R.layout.simple_spinner_item, lstScreens);
		screenAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spnScreen.setAdapter(screenAdapter);
		spnScreen.setSelection(selectedScreen);

		// Update Tablet field
		((CheckBox) findViewById(R.id.chkXlarge)).setChecked(prefs.getBoolean(pkgName + Common.PREF_XLARGE, false));

		// Update Res On Widgets field
		((CheckBox) findViewById(R.id.chkResOnWidgets)).setChecked(prefs.getBoolean(pkgName + Common.PREF_RES_ON_WIDGETS, false));

		// Update Language and list of possibilities
		localeList = new LocaleList(getString(R.string.settings_default));

		final Spinner spnLanguage = (Spinner) findViewById(R.id.spnLocale);
		ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
			android.R.layout.simple_spinner_item, localeList.getDescriptionList());
		dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spnLanguage.setAdapter(dataAdapter);
		int selectedLocalePos = localeList.getLocalePos(prefs.getString(pkgName + Common.PREF_LOCALE, null));
		spnLanguage.setSelection(selectedLocalePos);
		spnLanguage.setLongClickable(true);
		spnLanguage.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View arg0) {
				int selPos = spnLanguage.getSelectedItemPosition();
				if (selPos > 0)
					Toast.makeText(getApplicationContext(), localeList.getLocale(selPos), Toast.LENGTH_SHORT).show();
				return true;
			}
		});


		// Helper to list all apk folders under /res
		((Button) findViewById(R.id.btnListRes)).setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				AlertDialog.Builder builder = new AlertDialog.Builder(ApplicationSettings.this);

				ScrollView scrollPane = new ScrollView(ApplicationSettings.this);
				TextView txtPane = new TextView(ApplicationSettings.this);
				StringBuilder contents = new StringBuilder();
				JarFile jar = null;
				TreeSet<String> resEntries = new TreeSet<String>();
				Matcher m = Pattern.compile("res/(.+)/[^/]+").matcher("");
				try {
					ApplicationInfo app = getPackageManager().getApplicationInfo(pkgName, 0);
					jar = new JarFile(app.publicSourceDir);
					Enumeration<JarEntry> entries = jar.entries();
					while (entries.hasMoreElements()) {
						JarEntry entry = entries.nextElement();
						m.reset(entry.getName());
						if (m.matches())
							resEntries.add(m.group(1));
					}
					if (resEntries.size() == 0)
						resEntries.add(getString(R.string.res_noentries));
					jar.close();
					for (String dir : resEntries) {
						contents.append('\n');
						contents.append(dir);
					}
					contents.deleteCharAt(0);
				} catch (Exception e) {
					contents.append(getString(R.string.res_failedtoload));
					if (jar != null) {
						try {
							jar.close();
						} catch (Exception ex) { }
					}
				}
				txtPane.setText(contents);
				scrollPane.addView(txtPane);
				builder.setView(scrollPane);
				builder.setTitle(R.string.res_title);
				builder.show();
			}
		});


		// Setup fullscreen settings
		{
			int fullscreen;
			try {
				fullscreen = prefs.getInt(pkgName + Common.PREF_FULLSCREEN, Common.FULLSCREEN_DEFAULT);
			} catch (ClassCastException ex) {
				// Legacy boolean setting
				fullscreen = prefs.getBoolean(pkgName + Common.PREF_FULLSCREEN, false)
						? Common.FULLSCREEN_FORCE : Common.FULLSCREEN_DEFAULT;
			}
			final int fullscreenSelection = fullscreen;
			Spinner spnFullscreen = (Spinner) findViewById(R.id.spnFullscreen);
			// Note: the order of these items must match the Common.FULLSCREEN_... constants
			String[] fullscreenArray;
			if (Build.VERSION.SDK_INT >= 19) {
				fullscreenArray = new String[] {
						getString(R.string.settings_default),
						getString(R.string.settings_force),
						getString(R.string.settings_prevent),
						getString(R.string.settings_immersive)
				};
			} else {
				fullscreenArray = new String[] {
						getString(R.string.settings_default),
						getString(R.string.settings_force),
						getString(R.string.settings_prevent)
				};
			}

			List<String> lstFullscreen = Arrays.asList(fullscreenArray);
			ArrayAdapter<String> fullscreenAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, lstFullscreen);
			fullscreenAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			spnFullscreen.setAdapter(fullscreenAdapter);
			spnFullscreen.setSelection(fullscreenSelection);
		}

		// Update No Title field
		((CheckBox) findViewById(R.id.chkNoTitle)).setChecked(prefs.getBoolean(pkgName + Common.PREF_NO_TITLE, false));

		// Update Allow On Lockscreen field
		((CheckBox) findViewById(R.id.chkAllowOnLockscreen)).setChecked(prefs.getBoolean(pkgName + Common.PREF_ALLOW_ON_LOCKSCREEN, false));

		// Update Screen On field
		((CheckBox) findViewById(R.id.chkScreenOn)).setChecked(prefs.getBoolean(pkgName + Common.PREF_SCREEN_ON, false));

		// Load and render current screen setting + possible options
		int orientation = prefs.getInt(pkgName + Common.PREF_ORIENTATION, 0);
		if (orientation < 0 || orientation >= Common.orientationCodes.length)
			orientation = 0;
		final int selectedOrientation = orientation;

		Spinner spnOrientation = (Spinner) findViewById(R.id.spnOrientation);
		List<String> lstOrientations = new ArrayList<String>(Common.orientationLabels.length);
		for (int j = 0; j < Common.orientationLabels.length; j++)
			lstOrientations.add(getString(Common.orientationLabels[j]));
		ArrayAdapter<String> orientationAdapter = new ArrayAdapter<String>(this,
			android.R.layout.simple_spinner_item, lstOrientations);
		orientationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spnOrientation.setAdapter(orientationAdapter);
		spnOrientation.setSelection(selectedOrientation);

		// Setting for making the app resident in memory
		((CheckBox) findViewById(R.id.chkResident)).setChecked(prefs.getBoolean(pkgName + Common.PREF_RESIDENT, false));

		// Setting for disabling fullscreen IME
		((CheckBox) findViewById(R.id.chkNoFullscreenIME)).setChecked(prefs.getBoolean(pkgName + Common.PREF_NO_FULLSCREEN_IME, false));

		// Update No Big Notifications field
		if (Build.VERSION.SDK_INT >= 16) {
			((CheckBox) findViewById(R.id.chkNoBigNotifications)).setChecked(prefs.getBoolean(pkgName + Common.PREF_NO_BIG_NOTIFICATIONS, false));
		} else {
			findViewById(R.id.chkNoBigNotifications).setVisibility(View.GONE);
		}

		// Setup Ongoing Notifications settings
		{
			int ongoingNotifs = prefs.getInt(pkgName + Common.PREF_ONGOING_NOTIF, Common.ONGOING_NOTIF_DEFAULT);
			Spinner spnOngoingNotif = (Spinner) findViewById(R.id.spnOngoingNotifications);
			// Note: the order of these items must match the Common.ONGOING_NOTIF_... constants
			String[] ongoingNotifArray = new String[] {
						getString(R.string.settings_default),
						getString(R.string.settings_force),
						getString(R.string.settings_prevent) };

			List<String> lstOngoingNotif = Arrays.asList(ongoingNotifArray);
			ArrayAdapter<String> ongoingNotifAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, lstOngoingNotif);
			ongoingNotifAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			spnOngoingNotif.setAdapter(ongoingNotifAdapter);
			spnOngoingNotif.setSelection(ongoingNotifs);
		}

		// Update Insistent Notifications field
		((CheckBox) findViewById(R.id.chkInsistentNotifications)).setChecked(prefs.getBoolean(pkgName + Common.PREF_INSISTENT_NOTIF, false));

		// Load and render notifications priority
		if (Build.VERSION.SDK_INT >= 16) {
			int notifPriority = prefs.getInt(pkgName + Common.PREF_NOTIF_PRIORITY, 0);
			if (notifPriority < 0 || notifPriority >= Common.notifPriCodes.length)
				notifPriority = 0;
			final int selectedNotifPriority = notifPriority;

			Spinner spnNotifPri = (Spinner) findViewById(R.id.spnNotifPriority);
			List<String> lstNotifPriorities = new ArrayList<String>(Common.notifPriLabels.length);
			for (int j = 0; j < Common.notifPriLabels.length; j++)
				lstNotifPriorities.add(getString(Common.notifPriLabels[j]));
			ArrayAdapter<String> notifPriAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, lstNotifPriorities);
			notifPriAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			spnNotifPri.setAdapter(notifPriAdapter);
			spnNotifPri.setSelection(selectedNotifPriority);
		} else {
			findViewById(R.id.viewNotifPriority).setVisibility(View.GONE);
		}

		// Update Mute field
		((CheckBox) findViewById(R.id.chkMute)).setChecked(prefs.getBoolean(pkgName + Common.PREF_MUTE, false));

		// Update Legacy Menu field
		CheckBox showLegacyMenu = (CheckBox) findViewById(R.id.chkLegacyMenu);
		if (Build.VERSION.SDK_INT >= 22) {
			showLegacyMenu.setVisibility(View.GONE);
		} else {
			showLegacyMenu.setChecked(prefs.getBoolean(pkgName + Common.PREF_LEGACY_MENU, false));
		}

		// Setting for permissions revoking
		allowRevoking = prefs.getBoolean(pkgName + Common.PREF_REVOKEPERMS, false);
		disabledPermissions = prefs.getStringSet(pkgName + Common.PREF_REVOKELIST, new HashSet<String>());

		// Setup recents mode options
		final int selectedRecentsMode = prefs.getInt(pkgName + Common.PREF_RECENTS_MODE, Common.PREF_RECENTS_DEFAULT);
		// Note: the order of these items must match the Common.RECENTS_... constants
		String[] recentsModeArray = new String[] { getString(R.string.settings_default),
				getString(R.string.settings_force), getString(R.string.settings_prevent) };

		Spinner spnRecentsMode = (Spinner) findViewById(R.id.spnRecentsMode);
		List<String> lstRecentsMode = Arrays.asList(recentsModeArray);
		ArrayAdapter<String> recentsModeAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, lstRecentsMode);
		recentsModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spnRecentsMode.setAdapter(recentsModeAdapter);
		spnRecentsMode.setSelection(selectedRecentsMode);

		Button btnPermissions = (Button) findViewById(R.id.btnPermissions);
		btnPermissions.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// set up permissions editor
				try {
					final PermissionSettings permsDlg = new PermissionSettings(ApplicationSettings.this, pkgName, allowRevoking, disabledPermissions);
					permsDlg.setOnOkListener(new PermissionSettings.OnDismissListener() {
						@Override
						public void onDismiss(PermissionSettings obj) {
							allowRevoking = permsDlg.getRevokeActive();
							disabledPermissions.clear();
							disabledPermissions.addAll(permsDlg.getDisabledPermissions());
						}
					});
					permsDlg.display();
				} catch (NameNotFoundException e) {
				}
			}
		});

		settingKeys = getSettingKeys();
		initialSettings = getSettings();
	}

	private Set<String> getSettingKeys() {
		HashSet<String> settingKeys = new HashSet<String>();
		settingKeys.add(pkgName + Common.PREF_ACTIVE);
		settingKeys.add(pkgName + Common.PREF_DPI);
		settingKeys.add(pkgName + Common.PREF_FONT_SCALE);
		settingKeys.add(pkgName + Common.PREF_SCREEN);
		settingKeys.add(pkgName + Common.PREF_XLARGE);
		settingKeys.add(pkgName + Common.PREF_RES_ON_WIDGETS);
		settingKeys.add(pkgName + Common.PREF_LOCALE);
		settingKeys.add(pkgName + Common.PREF_FULLSCREEN);
		settingKeys.add(pkgName + Common.PREF_NO_TITLE);
		settingKeys.add(pkgName + Common.PREF_ALLOW_ON_LOCKSCREEN);
		settingKeys.add(pkgName + Common.PREF_SCREEN_ON);
		settingKeys.add(pkgName + Common.PREF_ORIENTATION);
		settingKeys.add(pkgName + Common.PREF_RESIDENT);
		settingKeys.add(pkgName + Common.PREF_NO_FULLSCREEN_IME);
		settingKeys.add(pkgName + Common.PREF_NO_BIG_NOTIFICATIONS);
		settingKeys.add(pkgName + Common.PREF_INSISTENT_NOTIF);
		settingKeys.add(pkgName + Common.PREF_ONGOING_NOTIF);
		if (Build.VERSION.SDK_INT >= 16)
			settingKeys.add(pkgName + Common.PREF_NOTIF_PRIORITY);
		settingKeys.add(pkgName + Common.PREF_RECENTS_MODE);
		settingKeys.add(pkgName + Common.PREF_MUTE);
		settingKeys.add(pkgName + Common.PREF_LEGACY_MENU);
		settingKeys.add(pkgName + Common.PREF_REVOKEPERMS);
		settingKeys.add(pkgName + Common.PREF_REVOKELIST);
		return settingKeys;
	}

	private Map<String, Object> getSettings() {

		Map<String, Object> settings = new HashMap<String, Object>();
		if (swtActive.isChecked()) {
			settings.put(pkgName + Common.PREF_ACTIVE, true);

			int dpi;
			try {
				dpi = Integer.parseInt(((EditText) findViewById(R.id.txtDPI)).getText().toString());
			} catch (Exception ex) {
				dpi = 0;
			}
			if (dpi != 0)
				settings.put(pkgName + Common.PREF_DPI, dpi);

			int fontScale;
			try {
				fontScale = Integer.parseInt(((EditText) findViewById(R.id.txtFontScale)).getText().toString());
			} catch (Exception ex) {
				fontScale = 0;
			}
			if (fontScale != 0 && fontScale != 100)
				settings.put(pkgName + Common.PREF_FONT_SCALE, fontScale);

			int screen = ((Spinner) findViewById(R.id.spnScreen)).getSelectedItemPosition();
			if (screen > 0)
				settings.put(pkgName + Common.PREF_SCREEN, screen);

			if (((CheckBox) findViewById(R.id.chkXlarge)).isChecked())
				settings.put(pkgName + Common.PREF_XLARGE, true);

			if (((CheckBox) findViewById(R.id.chkResOnWidgets)).isChecked())
				settings.put(pkgName + Common.PREF_RES_ON_WIDGETS, true);

			int selectedLocalePos = ((Spinner) findViewById(R.id.spnLocale)).getSelectedItemPosition();
			if (selectedLocalePos > 0)
				settings.put(pkgName + Common.PREF_LOCALE, localeList.getLocale(selectedLocalePos));

			int fullscreen = ((Spinner) findViewById(R.id.spnFullscreen)).getSelectedItemPosition();
			if (fullscreen > 0)
				settings.put(pkgName + Common.PREF_FULLSCREEN, fullscreen);

			if (((CheckBox) findViewById(R.id.chkNoTitle)).isChecked())
				settings.put(pkgName + Common.PREF_NO_TITLE, true);

			if (((CheckBox) findViewById(R.id.chkAllowOnLockscreen)).isChecked())
				settings.put(pkgName + Common.PREF_ALLOW_ON_LOCKSCREEN, true);

			if (((CheckBox) findViewById(R.id.chkScreenOn)).isChecked())
				settings.put(pkgName + Common.PREF_SCREEN_ON, true);

			int orientation = ((Spinner) findViewById(R.id.spnOrientation)).getSelectedItemPosition();
			if (orientation > 0)
				settings.put(pkgName + Common.PREF_ORIENTATION, orientation);

			if (((CheckBox) findViewById(R.id.chkResident)).isChecked())
				settings.put(pkgName + Common.PREF_RESIDENT, true);

			if (((CheckBox) findViewById(R.id.chkNoFullscreenIME)).isChecked())
				settings.put(pkgName + Common.PREF_NO_FULLSCREEN_IME, true);

			if (((CheckBox) findViewById(R.id.chkNoBigNotifications)).isChecked())
				settings.put(pkgName + Common.PREF_NO_BIG_NOTIFICATIONS, true);

			if (((CheckBox) findViewById(R.id.chkInsistentNotifications)).isChecked())
				settings.put(pkgName + Common.PREF_INSISTENT_NOTIF, true);

			int ongoingNotif = ((Spinner) findViewById(R.id.spnOngoingNotifications)).getSelectedItemPosition();
			if (ongoingNotif > 0)
				settings.put(pkgName + Common.PREF_ONGOING_NOTIF, ongoingNotif);

			if (Build.VERSION.SDK_INT >= 16) {
				int notifPriority = ((Spinner) findViewById(R.id.spnNotifPriority)).getSelectedItemPosition();
				if (notifPriority > 0)
					settings.put(pkgName + Common.PREF_NOTIF_PRIORITY, notifPriority);
			}

			int recentsMode = ((Spinner) findViewById(R.id.spnRecentsMode)).getSelectedItemPosition();
			if (recentsMode > 0)
				settings.put(pkgName + Common.PREF_RECENTS_MODE, recentsMode);

			if (((CheckBox) findViewById(R.id.chkMute)).isChecked())
				settings.put(pkgName + Common.PREF_MUTE, true);

			if (((CheckBox) findViewById(R.id.chkLegacyMenu)).isChecked())
				settings.put(pkgName + Common.PREF_LEGACY_MENU, true);

			if (allowRevoking)
				settings.put(pkgName + Common.PREF_REVOKEPERMS, true);

			if (disabledPermissions.size() > 0)
				settings.put(pkgName + Common.PREF_REVOKELIST, new HashSet<String>(disabledPermissions));
		}
		return settings;
	}

	@Override
	public void onBackPressed() {
		// If form wasn't changed, exit without prompting
		if (getSettings().equals(initialSettings)) {
			finish();
			return;
		}

		// Require confirmation to exit the screen and lose configuration changes
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.settings_unsaved_title);
		builder.setIconAttribute(android.R.attr.alertDialogIcon);
		builder.setMessage(R.string.settings_unsaved_detail);
		builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				ApplicationSettings.this.finish();
			}
		});
		builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
			}
		});
		builder.show();
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
		setResult(RESULT_OK, parentIntent);
	}




	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_app, menu);
		updateMenuEntries(getApplicationContext(), menu, pkgName);
		return true;
	}

	public static void updateMenuEntries(Context context, Menu menu, String pkgName) {
		if (context.getPackageManager().getLaunchIntentForPackage(pkgName) == null) {
			menu.findItem(R.id.menu_app_launch).setEnabled(false);
			Drawable icon = menu.findItem(R.id.menu_app_launch).getIcon().mutate();
			icon.setColorFilter(Color.GRAY, Mode.SRC_IN);
			menu.findItem(R.id.menu_app_launch).setIcon(icon);
		}

		boolean hasMarketLink = false;
		try {
			PackageManager pm = context.getPackageManager();
			String installer = pm.getInstallerPackageName(pkgName);
			if (installer != null)
				hasMarketLink = installer.equals("com.android.vending") || installer.contains("google");
		} catch (Exception e) {
		}

		menu.findItem(R.id.menu_app_store).setEnabled(hasMarketLink);
		try {
			Resources res = context.createPackageContext("com.android.vending", 0).getResources();
			int id = res.getIdentifier("ic_launcher_play_store", "mipmap", "com.android.vending");
			Drawable icon = res.getDrawable(id);
			if (!hasMarketLink) {
				icon = icon.mutate();
				icon.setColorFilter(Color.GRAY, Mode.SRC_IN);
			}
			menu.findItem(R.id.menu_app_store).setIcon(icon);
		} catch (Exception e) {
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		if (item.getItemId() == R.id.menu_save) {
			Editor prefsEditor = prefs.edit();
			Map<String, Object> newSettings = getSettings();
			for (String key : settingKeys) {
				Object value = newSettings.get(key);
				if (value == null) {
					prefsEditor.remove(key);
				} else {
					if (value instanceof Boolean) {
						prefsEditor.putBoolean(key, ((Boolean) value).booleanValue());
					} else if (value instanceof Integer) {
						prefsEditor.putInt(key, ((Integer) value).intValue());
					} else if (value instanceof String) {
						prefsEditor.putString(key, (String) value);
					} else if (value instanceof Set) {
						prefsEditor.remove(key);
						// Commit and reopen the editor, as it seems to be bugged when updating a StringSet
						prefsEditor.commit();
						prefsEditor = prefs.edit();
						prefsEditor.putStringSet(key, (Set<String>) value);
					} else {
						// Should never happen
						throw new IllegalStateException("Invalid setting type: " + key + "=" + value);
					}
				}
			}
			prefsEditor.commit();

			// Update saved settings to detect modifications later
			initialSettings = newSettings;

			// Check if in addition to saving the settings, the app should also be killed
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.settings_apply_title);
			builder.setMessage(R.string.settings_apply_detail);
			builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// Send the broadcast requesting to kill the app
					Intent applyIntent = new Intent(Common.MY_PACKAGE_NAME + ".UPDATE_PERMISSIONS");
					applyIntent.putExtra("action", Common.ACTION_PERMISSIONS);
					applyIntent.putExtra("Package", pkgName);
					applyIntent.putExtra("Kill", true);
					sendBroadcast(applyIntent, Common.MY_PACKAGE_NAME + ".BROADCAST_PERMISSION");

					dialog.dismiss();
				}
			});
			builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// Send the broadcast but not requesting kill
					Intent applyIntent = new Intent(Common.MY_PACKAGE_NAME + ".UPDATE_PERMISSIONS");
					applyIntent.putExtra("action", Common.ACTION_PERMISSIONS);
					applyIntent.putExtra("Package", pkgName);
					applyIntent.putExtra("Kill", false);
					sendBroadcast(applyIntent, Common.MY_PACKAGE_NAME + ".BROADCAST_PERMISSION");

					dialog.dismiss();
				}
			});
			builder.create().show();

		} else if (item.getItemId() == R.id.menu_app_launch) {
			Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage(pkgName);
			startActivity(LaunchIntent);
		} else if (item.getItemId() == R.id.menu_app_settings) {
			startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
									Uri.parse("package:" + pkgName)));
		} else if (item.getItemId() == R.id.menu_app_store) {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkgName)));
		}
		return super.onOptionsItemSelected(item);
	}


}
