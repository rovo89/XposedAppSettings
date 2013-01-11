package de.robv.android.xposed.mods.appsettings.settings;

import java.text.Collator;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import de.robv.android.xposed.mods.appsettings.R;
import de.robv.android.xposed.mods.appsettings.XposedMod;
import de.robv.android.xposed.mods.appsettings.hooks.PackagePermissions;

@SuppressLint("WorldReadableFiles")
public class ApplicationSettings extends Activity {

	private boolean dirty = false;

	
    private String pkgName;
    SharedPreferences prefs;
    private Set<String> disabledPermissions = new HashSet<String>();
    private boolean allowRevoking;
    private Intent parentIntent;
    
    private List<PermissionInfo> permsList = new LinkedList<PermissionInfo>();
    
	String[] localeCodes;
	String[] localeDescriptions;
	int selectedLocale;
    
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	
        super.onCreate(savedInstanceState);

        setContentView(R.layout.app_settings);
        
        Intent i = getIntent();
        parentIntent = i;

        prefs = getSharedPreferences(XposedMod.PREFS, Context.MODE_WORLD_READABLE);        
        
        ApplicationInfo app;
        try {
            app = getPackageManager().getApplicationInfo(i.getStringExtra("package"), 0);
            pkgName = app.packageName;
        } catch (NameNotFoundException e) {
            throw new RuntimeException("Invalid package: " + i.getStringExtra("package"));
        }
        
        // Display app info
        ((TextView) findViewById(R.id.app_label)).setText(app.loadLabel(getPackageManager()));
        ((TextView) findViewById(R.id.package_name)).setText(app.packageName);
        ((ImageView) findViewById(R.id.app_icon)).setImageDrawable(app.loadIcon(getPackageManager()));
        
        // Update switch of active/inactive tweaks
        if (prefs.getBoolean(pkgName + XposedMod.PREF_ACTIVE, false)) {
            ((Switch) findViewById(R.id.switchAppTweaked)).setChecked(true);
            findViewById(R.id.viewTweaks).setVisibility(View.VISIBLE);
        } else {
            ((Switch) findViewById(R.id.switchAppTweaked)).setChecked(false);
            findViewById(R.id.viewTweaks).setVisibility(View.GONE);
        }
        // Toggle the visibility of the lower panel when changed
        ((Switch) findViewById(R.id.switchAppTweaked)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            	dirty = true;
                findViewById(R.id.viewTweaks).setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });
        
        // Update DPI field
        if (prefs.getBoolean(pkgName + XposedMod.PREF_ACTIVE, false)) {
        	((EditText) findViewById(R.id.txtDPI)).setText(String.valueOf(
        		prefs.getInt(pkgName + XposedMod.PREF_DPI, 0)));
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

        // Load and render current screen setting + possible options
        int screen = prefs.getInt(pkgName + XposedMod.PREF_SCREEN, 0);
        if (screen < 0 || screen >= XposedMod.screens.length)
        	screen = 0;
        final int selectedScreen = screen;
        
		Spinner spnScreen = (Spinner) findViewById(R.id.spnScreen);
		List<String> lstScreens = Arrays.asList(XposedMod.screens);
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
        ((CheckBox) findViewById(R.id.chkTablet)).setChecked(prefs.getBoolean(pkgName + XposedMod.PREF_TABLET, false));
		// Track changes to the Tablet checkbox to know if the settings were changed
        ((CheckBox) findViewById(R.id.chkTablet)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                dirty = true;
            }
        });
		
        
        // Update Language and list of possibilities
        prepareLocalesList();
		
		Spinner spnLanguage = (Spinner) findViewById(R.id.spnLanguage);
		List<String> lstLanguages = Arrays.asList(localeDescriptions);
		ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
			android.R.layout.simple_spinner_item, lstLanguages);
		dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spnLanguage.setAdapter(dataAdapter);
		spnLanguage.setSelection(selectedLocale);
		// Track changes to the language to know if the settings were changed
		spnLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1, int pos, long arg3) {
				if (pos != selectedLocale) {
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
			            } catch (Exception ex) { }
		            }
		        }
		        txtPane.setText(contents);
		        scrollPane.addView(txtPane);
		        builder.setView(scrollPane);
		        builder.setTitle("Resources");
		        builder.show();
			}
		});
		

		// Setting for permissions revoking
        allowRevoking = prefs.getBoolean(pkgName + XposedMod.PREF_REVOKEPERMS, false);
        ((CheckBox) findViewById(R.id.chkRevokePerms)).setChecked(allowRevoking);
		// Track changes to the Revoke checkbox to know if the settings were changed
        // and to lock or unlock the list of permissions 
        ((CheckBox) findViewById(R.id.chkRevokePerms)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            	dirty = true;
                allowRevoking = isChecked;
                findViewById(R.id.lstPermissions).setBackgroundColor(allowRevoking ? Color.BLACK : Color.DKGRAY);
            }
        });
        findViewById(R.id.lstPermissions).setBackgroundColor(allowRevoking ? Color.BLACK : Color.DKGRAY);
        

		// Setting for making the app resident in memory
        ((CheckBox) findViewById(R.id.chkResident)).setChecked(prefs.getBoolean(pkgName + XposedMod.PREF_RESIDENT, false));
		// Track changes to know if the settings were changed
        ((CheckBox) findViewById(R.id.chkResident)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                dirty = true;
            }
        });
        
        
        // Load the list of permissions for the package and present them
        try {
            loadPermissionsList();
        } catch (NameNotFoundException e) {
            throw new RuntimeException("Invalid package permissions: " + pkgName, e);
        }
        
        final PermsListAdaptor appListAdaptor = new PermsListAdaptor(this, permsList);
        ((ListView) findViewById(R.id.lstPermissions)).setAdapter(appListAdaptor);
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
        builder.setTitle("Warning");
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage("You didn't save the configuration. " +
        		"Really go back and discard changes?");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ApplicationSettings.this.finish();
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
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
    
    
    
    @SuppressLint("DefaultLocale")
    private void loadPermissionsList() throws NameNotFoundException {

        permsList.clear();
        disabledPermissions.clear();
        
        PackageManager pm = getPackageManager();
        PackageInfo pkgInfo = pm.getPackageInfo(pkgName, PackageManager.GET_PERMISSIONS);
        if (pkgInfo.sharedUserId != null) {
            ((CheckBox) findViewById(R.id.chkRevokePerms)).setEnabled(false);
            ((CheckBox) findViewById(R.id.chkRevokePerms)).setChecked(false);
            ((CheckBox) findViewById(R.id.chkRevokePerms)).setText("Shared packages not yet supported");
            ((CheckBox) findViewById(R.id.chkRevokePerms)).setTextColor(Color.RED);
        }
        String[] permissions = pkgInfo.requestedPermissions;
        if (permissions == null) {
            permissions = new String[0];
        }
        for (String perm : permissions) {
            if (perm.startsWith(PackagePermissions.REVOKED_PREFIX)) {
                perm = perm.substring(PackagePermissions.REVOKED_PREFIX.length());
            }
            try {
                permsList.add(pm.getPermissionInfo(perm, 0));
            } catch (NameNotFoundException e) {
                PermissionInfo unknownPerm = new PermissionInfo();
                unknownPerm.name = perm;
                permsList.add(unknownPerm);
            }
        }
        disabledPermissions = prefs.getStringSet(pkgName + XposedMod.PREF_REVOKELIST, new HashSet<String>());
        
        Collections.sort(permsList, new Comparator<PermissionInfo>() {
            @Override
            public int compare(PermissionInfo lhs, PermissionInfo rhs) {
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

    
    class PermsListAdaptor extends ArrayAdapter<PermissionInfo> {
        
        public PermsListAdaptor(Context context, List<PermissionInfo> items) {

            super(context, R.layout.app_list_item, items);
        }
        
        private class ViewHolder {
            TextView tvName;
            TextView tvDescription;
            CheckBox chkPermDisabled;
        }
 
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			ViewHolder vHolder;
			if (row == null) {
				row = getLayoutInflater().inflate(R.layout.app_permission_item, parent, false);
				vHolder = new ViewHolder();
				vHolder.tvName = (TextView) row.findViewById(R.id.perm_name);
				vHolder.tvDescription = (TextView) row.findViewById(R.id.perm_description);
				vHolder.chkPermDisabled = (CheckBox) row.findViewById(R.id.chkPermDisabled);
				row.setTag(vHolder);
			} else {
				vHolder = (ViewHolder) row.getTag();
			}

			PermissionInfo perm = permsList.get(position);

			CharSequence label = perm.loadLabel(getPackageManager());
			if (!label.equals(perm.name)) {
				label = perm.name + " (" + label + ")";
			}

			vHolder.tvName.setText(label);
			vHolder.tvDescription.setText(perm.loadDescription(getPackageManager()));

			vHolder.chkPermDisabled.setVisibility(View.GONE);

			vHolder.tvName.setTag(perm.name);
			if (disabledPermissions.contains(perm.name)) {
				vHolder.tvName.setPaintFlags(vHolder.tvName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
				vHolder.tvName.setTextColor(Color.MAGENTA);
			} else {
				vHolder.tvName.setPaintFlags(vHolder.tvName.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
				vHolder.tvName.setTextColor(Color.WHITE);
			}
			row.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					if (!allowRevoking)
						return;

					dirty = true;

					TextView tv = (TextView) v.findViewById(R.id.perm_name);
					if ((tv.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) != 0) {
						disabledPermissions.remove(tv.getTag());
						tv.setPaintFlags(tv.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
						tv.setTextColor(Color.WHITE);
					} else {
						disabledPermissions.add((String) tv.getTag());
						tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
						tv.setTextColor(Color.MAGENTA);
					}
				}
			});

			return row;
		}

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
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        
        if (item.getItemId() == R.id.menu_save) {
            Editor prefsEditor = prefs.edit();
            if (((Switch) findViewById(R.id.switchAppTweaked)).isChecked()) {
                prefsEditor.putBoolean(pkgName + XposedMod.PREF_ACTIVE, true);
                int dpi;
                try {
                	dpi = Integer.parseInt(((EditText) findViewById(R.id.txtDPI)).getText().toString());
                } catch (Exception ex) {
                	dpi = 0;
                }
                if (dpi != 0) {
                    prefsEditor.putInt(pkgName + XposedMod.PREF_DPI, dpi);
                } else {
                    prefsEditor.remove(pkgName + XposedMod.PREF_DPI);
                }
                int screen = ((Spinner) findViewById(R.id.spnScreen)).getSelectedItemPosition();
                if (screen > 0) {
                    prefsEditor.putInt(pkgName + XposedMod.PREF_SCREEN, screen);
                } else {
                	prefsEditor.remove(pkgName + XposedMod.PREF_SCREEN);
                }
                if (((CheckBox) findViewById(R.id.chkTablet)).isChecked()) {
                    prefsEditor.putBoolean(pkgName + XposedMod.PREF_TABLET, true);
                }  else {
                    prefsEditor.remove(pkgName + XposedMod.PREF_TABLET);
                }
                if (((CheckBox) findViewById(R.id.chkResident)).isChecked()) {
                    prefsEditor.putBoolean(pkgName + XposedMod.PREF_RESIDENT, true);
                }  else {
                    prefsEditor.remove(pkgName + XposedMod.PREF_RESIDENT);
                }
                prefsEditor.remove(pkgName + XposedMod.PREF_REVOKELIST);
                if (disabledPermissions.size() > 0) {
                    prefsEditor.putStringSet(pkgName + XposedMod.PREF_REVOKELIST, disabledPermissions);
                }
                if (((CheckBox) findViewById(R.id.chkRevokePerms)).isChecked()) {
                    prefsEditor.putBoolean(pkgName + XposedMod.PREF_REVOKEPERMS, true);
                }  else {
                    prefsEditor.remove(pkgName + XposedMod.PREF_REVOKEPERMS);
                }
                selectedLocale = ((Spinner) findViewById(R.id.spnLanguage)).getSelectedItemPosition();
                if (selectedLocale > 0) {
                    prefsEditor.putString(pkgName + XposedMod.PREF_LOCALE, localeCodes[selectedLocale]);
                } else {
                	prefsEditor.remove(pkgName + XposedMod.PREF_LOCALE);
                }
                
            } else {
                prefsEditor.remove(pkgName + XposedMod.PREF_ACTIVE);
                prefsEditor.remove(pkgName + XposedMod.PREF_RESIDENT);
                prefsEditor.remove(pkgName + XposedMod.PREF_REVOKEPERMS);
                prefsEditor.remove(pkgName + XposedMod.PREF_REVOKELIST);
                prefsEditor.remove(pkgName + XposedMod.PREF_DPI);
                prefsEditor.remove(pkgName + XposedMod.PREF_SCREEN);
                prefsEditor.remove(pkgName + XposedMod.PREF_TABLET);
                prefsEditor.remove(pkgName + XposedMod.PREF_LOCALE);
            }
            prefsEditor.commit();
            
            dirty = false;
            
            // Check if in addition so saving the settings, the app should also be killed
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Apply settings");
            builder.setMessage("Also kill the application so when it's relaunched it uses the new settings?");
            builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                	// Send the broadcast requesting to kill the app
                    Intent applyIntent = new Intent(XposedMod.MY_PACKAGE_NAME + ".UPDATE_PERMISSIONS");
                    applyIntent.putExtra("action", XposedMod.ACTION_PERMISSIONS);
                    applyIntent.putExtra("Package", pkgName);
                    applyIntent.putExtra("Kill", true);
                    sendBroadcast(applyIntent, XposedMod.MY_PACKAGE_NAME + ".BROADCAST_PERMISSION");
                    
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                	// Send the broadcast but not requesting kill
                    Intent applyIntent = new Intent(XposedMod.MY_PACKAGE_NAME + ".UPDATE_PERMISSIONS");
                    applyIntent.putExtra("action", XposedMod.ACTION_PERMISSIONS);
                    applyIntent.putExtra("Package", pkgName);
                    applyIntent.putExtra("Kill", false);
                    sendBroadcast(applyIntent, XposedMod.MY_PACKAGE_NAME + ".BROADCAST_PERMISSION");

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
        }
        return super.onOptionsItemSelected(item);
    }
    
    
    
    /*
     * From AOSP code - listing available languages to present to the user
     */
	private static class LocaleInfo implements Comparable<LocaleInfo> {
        static final Collator sCollator = Collator.getInstance();

        String label;
        Locale locale;

        public LocaleInfo(String label, Locale locale) {
            this.label = label;
            this.locale = locale;
        }

        @Override
        public String toString() {
            return this.label;
        }

        @Override
        public int compareTo(LocaleInfo another) {
            return sCollator.compare(this.label, another.label);
        }
    }
	
	
	private void prepareLocalesList() {
		final String[] locales = Resources.getSystem().getAssets().getLocales();
		Arrays.sort(locales);
		final int origSize = locales.length;
		final LocaleInfo[] preprocess = new LocaleInfo[origSize];
	    int finalSize = 0;
	    for (int i = 0 ; i < origSize; i++ ) {
	        final String s = locales[i];
	        final int len = s.length();
	        if (len == 5) {
	            String language = s.substring(0, 2);
	            String country = s.substring(3, 5);
	            final Locale l = new Locale(language, country);
	
	            if (finalSize == 0) {
	                preprocess[finalSize++] =
	                        new LocaleInfo(toTitleCase(l.getDisplayLanguage(l)), l);
	            } else {
	                // check previous entry:
	                //  same lang and a country -> upgrade to full name and
	                //    insert ours with full name
	                //  diff lang -> insert ours with lang-only name
	                if (preprocess[finalSize-1].locale.getLanguage().equals(
	                        language)) {
	                    preprocess[finalSize-1].label = toTitleCase(
	                            getDisplayName(preprocess[finalSize-1].locale));
	                    preprocess[finalSize++] =
	                            new LocaleInfo(toTitleCase(
	                                    getDisplayName(l)), l);
	                } else {
	                    String displayName;
	                    if (s.equals("zz_ZZ")) {
	                        displayName = "Pseudo...";
	                    } else {
	                        displayName = toTitleCase(l.getDisplayLanguage(l));
	                    }
	                    preprocess[finalSize++] = new LocaleInfo(displayName, l);
	                }
	            }
	        }
	    }
	
	    final LocaleInfo[] localeInfos = new LocaleInfo[finalSize];
	    for (int i = 0; i < finalSize; i++) {
	        localeInfos[i] = preprocess[i];
	    }
	    Arrays.sort(localeInfos);
	    
	    String configuredLang = prefs.getString(pkgName + XposedMod.PREF_LOCALE, null);
	    selectedLocale = 0;
	    
	    localeCodes = new String[localeInfos.length + 1];
	    localeDescriptions = new String[localeInfos.length + 1];
	    localeCodes[0] = "";
	    localeDescriptions[0] = "(Default)";
	    for (int i = 1; i < finalSize + 1; i++) {
	    	localeCodes[i] = getLocaleCode(localeInfos[i-1].locale);
	    	localeDescriptions[i] = localeInfos[i-1].label;
	    	if (localeCodes[i].equals(configuredLang))
	    		selectedLocale = i;
	    }
	}
	
	private static String toTitleCase(String s) {
	    if (s.length() == 0) {
	        return s;
	    }
	
	    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
	
    private static String getDisplayName(Locale loc) {
		return loc.getDisplayName(loc);
	}
    
	private static String getLocaleCode(Locale loc) {
		String result = loc.getLanguage();
		if (loc.getCountry().length() > 0)
			result += "_" + loc.getCountry();
		if (loc.getVariant().length() > 0)
			result += "_" + loc.getVariant();
		return result;
	}
}
