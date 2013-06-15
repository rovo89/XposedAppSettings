package de.robv.android.xposed.mods.appsettings.settings;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
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
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
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
import de.robv.android.xposed.mods.appsettings.Common;
import de.robv.android.xposed.mods.appsettings.R;

@SuppressLint("WorldReadableFiles")
public class ApplicationSettings extends Activity {

	private boolean dirty = false;

	private String pkgName;
	SharedPreferences prefs;
	private Set<String> disabledPermissions;
	private boolean allowRevoking;
	private Intent parentIntent;

	LocaleList localeList;
	int selectedLocalePos;
	private PackageManager pm;

	private Switch mSwitch;
	private TextView mPackageName;
	private TextView mAppLabel;
	private ImageView mAppIcon;
	private View mViewTweaks;
	private EditText mTxtDPI;
	private EditText mTxtFontScale;
	private CheckBox mChkXlarge;
	private CheckBox mChkFullscreen;
	private CheckBox mChkNoTitle;
	private CheckBox mChkAllowOnLockscreen;
	private CheckBox mChkScreenOn;
	private CheckBox mChkResident;
	private CheckBox mChkInsistentNotifications;
	private Button mBtnListRes;
	private Button mBtnPermissions;
	private Spinner mSpnScreen;
	private Spinner mSpnOrientation;
	private Spinner mSpnLocale;

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		pm = getPackageManager();

		mSwitch = new Switch(this);
		getActionBar().setCustomView(mSwitch);
		getActionBar().setDisplayShowCustomEnabled(true);

		setContentView(R.layout.app_settings);
		FindViews();

		parentIntent = getIntent();

		prefs = getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);

		ApplicationInfo app;
		try {
			app = pm.getApplicationInfo(parentIntent.getStringExtra("package"), 0);
		} catch (NameNotFoundException e) {
			// Close the dialog gracefully, package might have been uninstalled
			finish();
			return;
		}
		pkgName = app.packageName;
		// Display app info
		mAppLabel.setText(app.loadLabel(pm));
		mPackageName.setText(app.packageName);
		mAppIcon.setImageDrawable(app.loadIcon(pm));

		// Update switch of active/inactive tweaks
		if (prefs.getBoolean(pkgName + Common.PREF_ACTIVE, false)) {
			mSwitch.setChecked(true);
			mViewTweaks.setVisibility(View.VISIBLE);
		} else {
			mSwitch.setChecked(false);
			mViewTweaks.setVisibility(View.GONE);
		}
		// Toggle the visibility of the lower panel when changed
		mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				dirty = true;
				findViewById(R.id.viewTweaks).setVisibility(isChecked ? View.VISIBLE : View.GONE);
			}
		});

		// Update DPI field
		if (prefs.getBoolean(pkgName + Common.PREF_ACTIVE, false)) {
			mTxtDPI.setText(String.valueOf(prefs.getInt(pkgName + Common.PREF_DPI, 0)));
		} else {
			mTxtDPI.setText("0");
		}
		mTxtDPI.addTextChangedListener(new DirtyListener());

		// Update Font Scaling field
		if (prefs.getBoolean(pkgName + Common.PREF_ACTIVE, false)) {
			mTxtFontScale.setText(String.valueOf(prefs.getInt(pkgName + Common.PREF_FONT_SCALE, 100)));
		} else {
			mTxtFontScale.setText("100");
		}
		mTxtFontScale.addTextChangedListener(new DirtyListener());

		// Load and render current screen setting + possible options
		int screen = prefs.getInt(pkgName + Common.PREF_SCREEN, 0);
		if (screen < 0 || screen >= Common.screens.length)
			screen = 0;
		final int selectedScreen = screen;

		List<String> lstScreens = Arrays.asList(Common.screens);
		ArrayAdapter<String> screenAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, lstScreens);
		screenAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpnScreen.setAdapter(screenAdapter);
		mSpnScreen.setSelection(selectedScreen);
		// Track changes to the screen to know if the settings were changed
		mSpnScreen.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1, int pos, long arg3) {
				if (pos != selectedScreen) {
					dirty = true;
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});

		// Update Tablet field
		mChkXlarge.setChecked(prefs.getBoolean(pkgName + Common.PREF_XLARGE, false));
		mChkXlarge.setOnCheckedChangeListener(new DirtyListener());

		// Update Language and list of possibilities
		localeList = new LocaleList();
		ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, localeList.getDescriptionList());
		dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpnLocale.setAdapter(dataAdapter);
		selectedLocalePos = localeList.getLocalePos(prefs.getString(pkgName + Common.PREF_LOCALE, null));
		mSpnLocale.setSelection(selectedLocalePos);
		mSpnLocale.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1, int pos, long arg3) {
				if (pos != selectedLocalePos) {
					dirty = true;
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});

		// Helper to list all apk folders under /res
		mBtnListRes.setOnClickListener(new View.OnClickListener() {

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
					ApplicationInfo app = pm.getApplicationInfo(pkgName, 0);
					jar = new JarFile(app.publicSourceDir);
					Enumeration<JarEntry> entries = jar.entries();
					while (entries.hasMoreElements()) {
						JarEntry entry = entries.nextElement();
						m.reset(entry.getName());
						if (m.matches())
							resEntries.add(m.group(1));
					}
					if (resEntries.size() == 0)
						resEntries.add("No resources found");
					jar.close();
					for (String dir : resEntries) {
						contents.append('\n');
						contents.append(dir);
					}
					contents.deleteCharAt(0);
				} catch (Exception e) {
					contents.append("Failed to load APK contents");
					if (jar != null) {
						try {
							jar.close();
						} catch (Exception ex) {
						}
					}
				}
				txtPane.setText(contents);
				scrollPane.addView(txtPane);
				builder.setView(scrollPane);
				builder.setTitle("Resources");
				builder.show();
			}
		});

		// Update Fullscreen field
		mChkFullscreen.setChecked(prefs.getBoolean(pkgName + Common.PREF_FULLSCREEN, false));
		mChkFullscreen.setOnCheckedChangeListener(new DirtyListener());

		// Update No Title field
		mChkNoTitle.setChecked(prefs.getBoolean(pkgName + Common.PREF_NO_TITLE, false));
		mChkNoTitle.setOnCheckedChangeListener(new DirtyListener());

		// Update Allow On Lockscreen field
		mChkAllowOnLockscreen.setChecked(prefs.getBoolean(pkgName + Common.PREF_ALLOW_ON_LOCKSCREEN, false));
		mChkAllowOnLockscreen.setOnCheckedChangeListener(new DirtyListener());

		// Update Screen On field
		mChkScreenOn.setChecked(prefs.getBoolean(pkgName + Common.PREF_SCREEN_ON, false));
		mChkScreenOn.setOnCheckedChangeListener(new DirtyListener());

		// Load and render current screen setting + possible options
		int orientation = prefs.getInt(pkgName + Common.PREF_ORIENTATION, 0);
		if (orientation < 0 || orientation >= Common.orientations.length)
			orientation = 0;
		final int selectedOrientation = orientation;

		List<String> lstOrientations = Arrays.asList(Common.orientations);
		ArrayAdapter<String> orientationAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, lstOrientations);
		orientationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpnOrientation.setAdapter(orientationAdapter);
		mSpnOrientation.setSelection(selectedOrientation);
		mSpnOrientation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1, int pos, long arg3) {
				if (pos != selectedOrientation) {
					dirty = true;
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});

		// Setting for making the app resident in memory
		mChkResident.setChecked(prefs.getBoolean(pkgName + Common.PREF_RESIDENT, false));
		mChkResident.setOnCheckedChangeListener(new DirtyListener());

		// Update Insistent Notifications field
		mChkInsistentNotifications.setChecked(prefs.getBoolean(pkgName + Common.PREF_INSISTENT_NOTIF, false));
		mChkInsistentNotifications.setOnCheckedChangeListener(new DirtyListener());

		// Setting for permissions revoking
		allowRevoking = prefs.getBoolean(pkgName + Common.PREF_REVOKEPERMS, false);
		disabledPermissions = prefs.getStringSet(pkgName + Common.PREF_REVOKELIST, new HashSet<String>());

		mBtnPermissions.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// set up permissions editor
				try {
					final PermissionSettings permsDlg = new PermissionSettings(ApplicationSettings.this, pkgName, allowRevoking, disabledPermissions);
					permsDlg.setOnOkListener(new PermissionSettings.OnDismissListener() {
						@Override
						public void onDismiss(PermissionSettings obj) {
							dirty = true;
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
	}

	@Override
	public void onBackPressed() {
		// If form wasn't changed, exit without prompting
		if (!dirty) {
			finish();
			return;
		}

		// Require confirmation to exit the screen and lose configuration
		// changes
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Discard changes?");
		builder.setIconAttribute(android.R.attr.alertDialogIcon);
		builder.setMessage("You didn't save the configuration. Really go back and discard changes?");
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

		if (getPackageManager().getLaunchIntentForPackage(pkgName) == null) {
			menu.findItem(R.id.menu_app_launch).setEnabled(false);
			Drawable icon = menu.findItem(R.id.menu_app_launch).getIcon().mutate();
			icon.setColorFilter(Color.GRAY, Mode.SRC_IN);
			menu.findItem(R.id.menu_app_launch).setIcon(icon);
		}

		boolean hasMarketLink = false;
		try {

			String installer = pm.getInstallerPackageName(pkgName);
			if (installer != null)
				hasMarketLink = installer.equals("com.android.vending") || installer.contains("google");
		} catch (Exception e) {
		}
		menu.findItem(R.id.menu_app_store).setEnabled(hasMarketLink);
		if (!hasMarketLink) {
			try {
				Resources res = createPackageContext("com.android.vending", 0).getResources();
				int id = res.getIdentifier("ic_launcher_play_store", "mipmap", "com.android.vending");
				Drawable icon = res.getDrawable(id);

				icon = icon.mutate();
				icon.setColorFilter(Color.GRAY, Mode.SRC_IN);

				menu.findItem(R.id.menu_app_store).setIcon(icon);
			} catch (Exception e) {
			}
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		if (item.getItemId() == R.id.menu_save) {

			SaveChanges();
			dirty = false;

			// Check if in addition so saving the settings, the app should also
			// be killed
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Apply settings");
			builder.setMessage("Also kill the application so when it's relaunched it uses the new settings?");
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
			Intent LaunchIntent = pm.getLaunchIntentForPackage(pkgName);
			startActivity(LaunchIntent);
		} else if (item.getItemId() == R.id.menu_app_settings) {
			startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkgName)));
		} else if (item.getItemId() == R.id.menu_app_store) {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkgName)));
		}
		return super.onOptionsItemSelected(item);
	}

	private void FindViews() {
		mAppLabel = (TextView) findViewById(R.id.app_label);
		mPackageName = (TextView) findViewById(R.id.package_name);
		mAppIcon = (ImageView) findViewById(R.id.app_icon);
		mViewTweaks = findViewById(R.id.viewTweaks);
		mTxtDPI = (EditText) findViewById(R.id.txtDPI);
		mTxtFontScale = (EditText) findViewById(R.id.txtFontScale);
		mSpnScreen = (Spinner) findViewById(R.id.spnScreen);
		mChkXlarge = (CheckBox) findViewById(R.id.chkXlarge);
		mSpnLocale = (Spinner) findViewById(R.id.spnLocale);
		mBtnListRes = (Button) findViewById(R.id.btnListRes);
		mChkFullscreen = (CheckBox) findViewById(R.id.chkFullscreen);
		mChkNoTitle = (CheckBox) findViewById(R.id.chkNoTitle);
		mChkAllowOnLockscreen = (CheckBox) findViewById(R.id.chkAllowOnLockscreen);
		mChkScreenOn = (CheckBox) findViewById(R.id.chkScreenOn);
		mSpnOrientation = (Spinner) findViewById(R.id.spnOrientation);
		mChkResident = (CheckBox) findViewById(R.id.chkResident);
		mChkInsistentNotifications = (CheckBox) findViewById(R.id.chkInsistentNotifications);
		mBtnPermissions = (Button) findViewById(R.id.btnPermissions);

	}

	private void SaveChanges() {
		Editor prefsEditor = prefs.edit();
		if (mSwitch.isChecked()) {
			prefsEditor.putBoolean(pkgName + Common.PREF_ACTIVE, true);
			int dpi;
			try {
				dpi = Integer.parseInt(mTxtDPI.getText().toString());
			} catch (Exception ex) {
				dpi = 0;
			}
			if (dpi != 0) {
				prefsEditor.putInt(pkgName + Common.PREF_DPI, dpi);
			} else {
				prefsEditor.remove(pkgName + Common.PREF_DPI);
			}
			int fontScale;
			try {
				fontScale = Integer.parseInt(mTxtFontScale.getText().toString());
			} catch (Exception ex) {
				fontScale = 0;
			}
			if (fontScale != 0 && fontScale != 100) {
				prefsEditor.putInt(pkgName + Common.PREF_FONT_SCALE, fontScale);
			} else {
				prefsEditor.remove(pkgName + Common.PREF_FONT_SCALE);
			}
			int screen = mSpnScreen.getSelectedItemPosition();
			if (screen > 0) {
				prefsEditor.putInt(pkgName + Common.PREF_SCREEN, screen);
			} else {
				prefsEditor.remove(pkgName + Common.PREF_SCREEN);
			}
			if (mChkXlarge.isChecked()) {
				prefsEditor.putBoolean(pkgName + Common.PREF_XLARGE, true);
			} else {
				prefsEditor.remove(pkgName + Common.PREF_XLARGE);
			}
			int selectedLocalePos = mSpnLocale.getSelectedItemPosition();
			if (selectedLocalePos > 0) {
				prefsEditor.putString(pkgName + Common.PREF_LOCALE, localeList.getLocale(selectedLocalePos));
			} else {
				prefsEditor.remove(pkgName + Common.PREF_LOCALE);
			}
			if (mChkFullscreen.isChecked()) {
				prefsEditor.putBoolean(pkgName + Common.PREF_FULLSCREEN, true);
			} else {
				prefsEditor.remove(pkgName + Common.PREF_FULLSCREEN);
			}
			if (mChkNoTitle.isChecked()) {
				prefsEditor.putBoolean(pkgName + Common.PREF_NO_TITLE, true);
			} else {
				prefsEditor.remove(pkgName + Common.PREF_NO_TITLE);
			}
			if (mChkAllowOnLockscreen.isChecked()) {
				prefsEditor.putBoolean(pkgName + Common.PREF_ALLOW_ON_LOCKSCREEN, true);
			} else {
				prefsEditor.remove(pkgName + Common.PREF_ALLOW_ON_LOCKSCREEN);
			}
			if (mChkScreenOn.isChecked()) {
				prefsEditor.putBoolean(pkgName + Common.PREF_SCREEN_ON, true);
			} else {
				prefsEditor.remove(pkgName + Common.PREF_SCREEN_ON);
			}
			int orientation = mSpnOrientation.getSelectedItemPosition();
			if (orientation > 0) {
				prefsEditor.putInt(pkgName + Common.PREF_ORIENTATION, orientation);
			} else {
				prefsEditor.remove(pkgName + Common.PREF_ORIENTATION);
			}
			if (mChkResident.isChecked()) {
				prefsEditor.putBoolean(pkgName + Common.PREF_RESIDENT, true);
			} else {
				prefsEditor.remove(pkgName + Common.PREF_RESIDENT);
			}
			if (mChkInsistentNotifications.isChecked()) {
				prefsEditor.putBoolean(pkgName + Common.PREF_INSISTENT_NOTIF, true);
			} else {
				prefsEditor.remove(pkgName + Common.PREF_INSISTENT_NOTIF);
			}
			if (allowRevoking) {
				prefsEditor.putBoolean(pkgName + Common.PREF_REVOKEPERMS, true);
			} else {
				prefsEditor.remove(pkgName + Common.PREF_REVOKEPERMS);
			}
			prefsEditor.remove(pkgName + Common.PREF_REVOKELIST);
			if (disabledPermissions.size() > 0) {
				// Commit and reopen the editor, as it seems to be bugged when
				// updating a StringSet
				prefsEditor.commit();
				prefsEditor = prefs.edit();
				prefsEditor.putStringSet(pkgName + Common.PREF_REVOKELIST, disabledPermissions);
			}

		} else {
			prefsEditor.remove(pkgName + Common.PREF_ACTIVE);
			prefsEditor.remove(pkgName + Common.PREF_DPI);
			prefsEditor.remove(pkgName + Common.PREF_FONT_SCALE);
			prefsEditor.remove(pkgName + Common.PREF_SCREEN);
			prefsEditor.remove(pkgName + Common.PREF_XLARGE);
			prefsEditor.remove(pkgName + Common.PREF_LOCALE);
			prefsEditor.remove(pkgName + Common.PREF_FULLSCREEN);
			prefsEditor.remove(pkgName + Common.PREF_NO_TITLE);
			prefsEditor.remove(pkgName + Common.PREF_ALLOW_ON_LOCKSCREEN);
			prefsEditor.remove(pkgName + Common.PREF_SCREEN_ON);
			prefsEditor.remove(pkgName + Common.PREF_ORIENTATION);
			prefsEditor.remove(pkgName + Common.PREF_RESIDENT);
			prefsEditor.remove(pkgName + Common.PREF_INSISTENT_NOTIF);
			prefsEditor.remove(pkgName + Common.PREF_REVOKEPERMS);
			prefsEditor.remove(pkgName + Common.PREF_REVOKELIST);
		}
		prefsEditor.commit();

	}

	// Track changes to know if the settings were changed
	private class DirtyListener implements CompoundButton.OnCheckedChangeListener, TextWatcher {

		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			dirty = true;
		}

		@Override
		public void afterTextChanged(Editable arg0) {
			dirty = true;
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}

	}

}
