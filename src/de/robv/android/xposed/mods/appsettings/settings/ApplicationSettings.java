package de.robv.android.xposed.mods.appsettings.settings;

import java.util.ArrayList;
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
import android.support.v7.app.ActionBarActivity;
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
public class ApplicationSettings extends ActionBarActivity {

	private boolean dirty = false;


	CompoundButton swtActive;

    private String pkgName;
    SharedPreferences prefs;
    private Set<String> disabledPermissions;
    private boolean allowRevoking;
    private Intent parentIntent;

    LocaleList localeList;
	int selectedLocalePos;
    
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	
        super.onCreate(savedInstanceState);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            swtActive = new Switch(this);
        } else {
            swtActive = new CheckBox(this);
        }
        getSupportActionBar().setCustomView(swtActive);
        getSupportActionBar().setDisplayShowCustomEnabled(true);

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
            	dirty = true;
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
        // Track changes to the DPI field to know if the settings were changed
        ((EditText) findViewById(R.id.txtDPI)).addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
                dirty = true;
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		// Update Font Scaling field
		if (prefs.getBoolean(pkgName + Common.PREF_ACTIVE, false)) {
			((EditText) findViewById(R.id.txtFontScale)).setText(String.valueOf(prefs.getInt(pkgName + Common.PREF_FONT_SCALE, 100)));
		} else {
			((EditText) findViewById(R.id.txtFontScale)).setText("100");
		}
		// Track changes to the Font Scale field to know if the settings were changed
		((EditText) findViewById(R.id.txtFontScale)).addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				dirty = true;
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			@Override
			public void afterTextChanged(Editable s) {
			}
		});

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
		// Track changes to the screen to know if the settings were changed
		spnScreen.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
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
        ((CheckBox) findViewById(R.id.chkXlarge)).setChecked(prefs.getBoolean(pkgName + Common.PREF_XLARGE, false));
		// Track changes to the Tablet checkbox to know if the settings were changed
        ((CheckBox) findViewById(R.id.chkXlarge)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                dirty = true;
            }
        });
		
        
        // Update Language and list of possibilities
		localeList = new LocaleList(getString(R.string.settings_default));

		Spinner spnLanguage = (Spinner) findViewById(R.id.spnLocale);
		ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
			android.R.layout.simple_spinner_item, localeList.getDescriptionList());
		dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spnLanguage.setAdapter(dataAdapter);
		selectedLocalePos = localeList.getLocalePos(prefs.getString(pkgName + Common.PREF_LOCALE, null));
		spnLanguage.setSelection(selectedLocalePos);
		// Track changes to the language to know if the settings were changed
		spnLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
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
			List<String> lstFullscreen = Arrays.asList(
					new String[] { getString(R.string.settings_default),
							getString(R.string.settings_force),
							getString(R.string.settings_prevent) });
			ArrayAdapter<String> fullscreenAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, lstFullscreen);
			fullscreenAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			spnFullscreen.setAdapter(fullscreenAdapter);
			spnFullscreen.setSelection(fullscreenSelection);
			// Track changes to the fullscreen option to know if the settings were changed
			spnFullscreen.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
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
		}

		// Update No Title field
		((CheckBox) findViewById(R.id.chkNoTitle)).setChecked(prefs.getBoolean(pkgName + Common.PREF_NO_TITLE, false));
		// Track changes to the No Title checkbox to know if the settings were changed
		((CheckBox) findViewById(R.id.chkNoTitle)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				dirty = true;
			}
		});

		// Update Allow On Lockscreen field
		((CheckBox) findViewById(R.id.chkAllowOnLockscreen)).setChecked(prefs.getBoolean(pkgName + Common.PREF_ALLOW_ON_LOCKSCREEN, false));
		// Track changes to the Allow On Lockscreen checkbox to know if the settings were changed
		((CheckBox) findViewById(R.id.chkAllowOnLockscreen)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				dirty = true;
			}
		});

		// Update Screen On field
		((CheckBox) findViewById(R.id.chkScreenOn)).setChecked(prefs.getBoolean(pkgName + Common.PREF_SCREEN_ON, false));
		// Track changes to the Screen On checkbox to know if the settings were changed
		((CheckBox) findViewById(R.id.chkScreenOn)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				dirty = true;
			}
		});

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
		// Track changes to the orientation to know if the settings were changed
		spnOrientation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
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
        ((CheckBox) findViewById(R.id.chkResident)).setChecked(prefs.getBoolean(pkgName + Common.PREF_RESIDENT, false));
		// Track changes to know if the settings were changed
        ((CheckBox) findViewById(R.id.chkResident)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                dirty = true;
            }
        });
        
		// Update No Big Notifications field
		((CheckBox) findViewById(R.id.chkNoBigNotifications)).setChecked(prefs.getBoolean(pkgName + Common.PREF_NO_BIG_NOTIFICATIONS, false));
		// Track changes to the No Big Notifications checkbox to know if the settings were changed
		((CheckBox) findViewById(R.id.chkNoBigNotifications)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				dirty = true;
			}
		});

        // Update Insistent Notifications field
        ((CheckBox) findViewById(R.id.chkInsistentNotifications)).setChecked(prefs.getBoolean(pkgName + Common.PREF_INSISTENT_NOTIF, false));
		// Track changes to the Insistent Notifications checkbox to know if the settings were changed
        ((CheckBox) findViewById(R.id.chkInsistentNotifications)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                dirty = true;
            }
        });

		// Setting for permissions revoking
		allowRevoking = prefs.getBoolean(pkgName + Common.PREF_REVOKEPERMS, false);
		disabledPermissions = getStringSet(prefs, pkgName + Common.PREF_REVOKELIST, new HashSet<String>());

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
    	
    	// Require confirmation to exit the screen and lose configuration changes
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.settings_unsaved_title);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
        }
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
            if (swtActive.isChecked()) {
                prefsEditor.putBoolean(pkgName + Common.PREF_ACTIVE, true);
                int dpi;
                try {
                	dpi = Integer.parseInt(((EditText) findViewById(R.id.txtDPI)).getText().toString());
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
					fontScale = Integer.parseInt(((EditText) findViewById(R.id.txtFontScale)).getText().toString());
				} catch (Exception ex) {
					fontScale = 0;
				}
				if (fontScale != 0 && fontScale != 100) {
					prefsEditor.putInt(pkgName + Common.PREF_FONT_SCALE, fontScale);
				} else {
					prefsEditor.remove(pkgName + Common.PREF_FONT_SCALE);
				}
                int screen = ((Spinner) findViewById(R.id.spnScreen)).getSelectedItemPosition();
                if (screen > 0) {
                    prefsEditor.putInt(pkgName + Common.PREF_SCREEN, screen);
                } else {
                	prefsEditor.remove(pkgName + Common.PREF_SCREEN);
                }
                if (((CheckBox) findViewById(R.id.chkXlarge)).isChecked()) {
                    prefsEditor.putBoolean(pkgName + Common.PREF_XLARGE, true);
                }  else {
                    prefsEditor.remove(pkgName + Common.PREF_XLARGE);
                }
				selectedLocalePos = ((Spinner) findViewById(R.id.spnLocale)).getSelectedItemPosition();
				if (selectedLocalePos > 0) {
					prefsEditor.putString(pkgName + Common.PREF_LOCALE, localeList.getLocale(selectedLocalePos));
				} else {
					prefsEditor.remove(pkgName + Common.PREF_LOCALE);
				}
				int fullscreen = ((Spinner) findViewById(R.id.spnFullscreen)).getSelectedItemPosition();
				if (fullscreen > 0) {
					prefsEditor.putInt(pkgName + Common.PREF_FULLSCREEN, fullscreen);
				} else {
					prefsEditor.remove(pkgName + Common.PREF_FULLSCREEN);
				}
				if (((CheckBox) findViewById(R.id.chkNoTitle)).isChecked()) {
					prefsEditor.putBoolean(pkgName + Common.PREF_NO_TITLE, true);
				} else {
					prefsEditor.remove(pkgName + Common.PREF_NO_TITLE);
				}
				if (((CheckBox) findViewById(R.id.chkAllowOnLockscreen)).isChecked()) {
					prefsEditor.putBoolean(pkgName + Common.PREF_ALLOW_ON_LOCKSCREEN, true);
				} else {
					prefsEditor.remove(pkgName + Common.PREF_ALLOW_ON_LOCKSCREEN);
				}
				if (((CheckBox) findViewById(R.id.chkScreenOn)).isChecked()) {
					prefsEditor.putBoolean(pkgName + Common.PREF_SCREEN_ON, true);
				} else {
					prefsEditor.remove(pkgName + Common.PREF_SCREEN_ON);
				}
				int orientation = ((Spinner) findViewById(R.id.spnOrientation)).getSelectedItemPosition();
				if (orientation > 0) {
					prefsEditor.putInt(pkgName + Common.PREF_ORIENTATION, orientation);
				} else {
					prefsEditor.remove(pkgName + Common.PREF_ORIENTATION);
				}
				if (((CheckBox) findViewById(R.id.chkResident)).isChecked()) {
					prefsEditor.putBoolean(pkgName + Common.PREF_RESIDENT, true);
				} else {
					prefsEditor.remove(pkgName + Common.PREF_RESIDENT);
				}
				if (((CheckBox) findViewById(R.id.chkNoBigNotifications)).isChecked()) {
					prefsEditor.putBoolean(pkgName + Common.PREF_NO_BIG_NOTIFICATIONS, true);
				} else {
					prefsEditor.remove(pkgName + Common.PREF_NO_BIG_NOTIFICATIONS);
				}
				if (((CheckBox) findViewById(R.id.chkInsistentNotifications)).isChecked()) {
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
					// Commit and reopen the editor, as it seems to be bugged when updating a StringSet
                    prefsEditor.commit();
                    prefsEditor = prefs.edit();
                    putStringSet(prefsEditor, pkgName + Common.PREF_REVOKELIST, disabledPermissions);
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
                prefsEditor.remove(pkgName + Common.PREF_NO_BIG_NOTIFICATIONS);
                prefsEditor.remove(pkgName + Common.PREF_INSISTENT_NOTIF);
                prefsEditor.remove(pkgName + Common.PREF_REVOKEPERMS);
                prefsEditor.remove(pkgName + Common.PREF_REVOKELIST);
            }
            prefsEditor.commit();
            
            dirty = false;
            
            // Check if in addition so saving the settings, the app should also be killed
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

	// prefs.getStringSet(pkgName + Common.PREF_REVOKELIST, new HashSet<String>());
	public static Set<String> getStringSet(SharedPreferences prefs, String key, Set<String> defValues) {
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
			return prefs.getStringSet(key, defValues);
		}
		String s = prefs.getString(key, null);
		if (s == null || s.length() == 0) {
			return defValues;
		}
		return StringToSet(s);
	}

	// prefsEditor.putStringSet(pkgName + Common.PREF_REVOKELIST, disabledPermissions);
	public static void putStringSet(Editor prefsEditor, String key, Set<String> values) {
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
			prefsEditor.putStringSet(key, values);
		} else {
			prefsEditor.putString(key, SetToString(values));
		}
	}

	// just assume there is no @@ in the values
	private static final String SPLITER = "@@";

	private static String SetToString(Set<String> values) {
		StringBuilder sb = new StringBuilder();
		for (String s : values) {
			sb.append(s);
			sb.append(SPLITER);
		}
		if (sb.length() > 0) {
			return sb.substring(0, sb.length() - SPLITER.length());
		} else {
			return sb.toString();
		}
	}

	private static Set<String> StringToSet(String ss) {
		Set<String> set = new HashSet<String>();
		for (String s : ss.split(SPLITER)) {
			set.add(s);
		}
		return set;
	}

}
