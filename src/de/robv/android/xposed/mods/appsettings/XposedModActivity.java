package de.robv.android.xposed.mods.appsettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SectionIndexer;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import de.robv.android.xposed.mods.appsettings.FilterItemComponent.FilterState;
import de.robv.android.xposed.mods.appsettings.settings.ApplicationSettings;


public class XposedModActivity extends Activity {

	private ArrayList<ApplicationInfo> appList = new ArrayList<ApplicationInfo>();
	private ArrayList<ApplicationInfo> filteredAppList = new ArrayList<ApplicationInfo>();
	private String nameFilter;
	private FilterState filterAppType;
	private FilterState filterActive;
	private FilterState filterDPI;
	private FilterState filterTextScale;
	private FilterState filterResLoad;
	private FilterState filterLocale;
	private FilterState filterFullscreen;
	private FilterState filterNoTitle;
	private FilterState filterScreenOn;
	private FilterState filterOrientation;
	private FilterState filterResident;
	private FilterState filterInsNotif;
	private FilterState filterPermissions;

    private SharedPreferences prefs;
	
	@SuppressLint("WorldReadableFiles")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTitle(R.string.app_name);
		super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);
		
        setContentView(R.layout.main);
        
        TabHost tabHost=(TabHost)findViewById(R.id.tabHost);
        tabHost.setup();
        
        TabSpec spec1=tabHost.newTabSpec("App Settings");
        spec1.setIndicator("App Settings");
        spec1.setContent(R.id.tab1);

        TabSpec spec2=tabHost.newTabSpec("About");
        spec2.setIndicator("About");
        spec2.setContent(R.id.tab2);
        
        tabHost.addTab(spec1);
        tabHost.addTab(spec2);
        tabHost.setCurrentTab(0);
        
        ((TextView) findViewById(R.id.about_title)).setMovementMethod(LinkMovementMethod.getInstance());
        
        try {
	        ((TextView) findViewById(R.id.version)).setText("Version: " +
	        		getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (NameNotFoundException e) {
        }
        
        
        ListView list = (ListView) findViewById(R.id.lstApps);

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
    
    
    
    @SuppressLint("DefaultLocale")
    private void loadApps() {

        appList.clear();
        
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = getPackageManager().getInstalledApplications(0);
        for (ApplicationInfo appInfo : apps) {
            appInfo.name = appInfo.loadLabel(pm).toString();
            appList.add(appInfo);
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
        final AppListAdaptor appListAdaptor = new AppListAdaptor(XposedModActivity.this,appList);

        ((ListView) findViewById(R.id.lstApps)).setAdapter(appListAdaptor);
        ((SearchView) findViewById(R.id.searchApp)).setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            
            @Override
            public boolean onQueryTextSubmit(String query) {
                nameFilter = query;
                appListAdaptor.getFilter().filter(nameFilter);
                ((SearchView) findViewById(R.id.searchApp)).clearFocus();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                nameFilter = newText;
                appListAdaptor.getFilter().filter(nameFilter);
                return false;
            }
            
            
        });
        
		((ImageButton) findViewById(R.id.btnFilter)).setOnClickListener(new View.OnClickListener() {
			Dialog filterDialog;

			@Override
			public void onClick(View v) {
				// set up dialog
				filterDialog = new Dialog(XposedModActivity.this);
				filterDialog.setContentView(R.layout.filter_dialog);
				filterDialog.setTitle("Filter");
				filterDialog.setCancelable(true);
				filterDialog.setOwnerActivity(XposedModActivity.this);

				// Load previously used filters
				((FilterItemComponent) filterDialog.findViewById(R.id.fltAppType)).setFilterState(filterAppType);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltActive)).setFilterState(filterActive);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltDPI)).setFilterState(filterDPI);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltTextScale)).setFilterState(filterTextScale);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltResLoad)).setFilterState(filterResLoad);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltLocale)).setFilterState(filterLocale);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltFullscreen)).setFilterState(filterFullscreen);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltNoTitle)).setFilterState(filterNoTitle);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltScreenOn)).setFilterState(filterScreenOn);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltOrientation)).setFilterState(filterOrientation);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltResident)).setFilterState(filterResident);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltInsNotif)).setFilterState(filterInsNotif);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltPermissions)).setFilterState(filterPermissions);

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
						filterDPI = FilterState.ALL;
						filterTextScale = FilterState.ALL;
						filterResLoad = FilterState.ALL;
						filterLocale = FilterState.ALL;
						filterFullscreen = FilterState.ALL;
						filterNoTitle = FilterState.ALL;
						filterScreenOn = FilterState.ALL;
						filterOrientation = FilterState.ALL;
						filterResident = FilterState.ALL;
						filterInsNotif = FilterState.ALL;
						filterPermissions = FilterState.ALL;

						filterDialog.dismiss();
						appListAdaptor.getFilter().filter(nameFilter);
					}
				});
				((Button) filterDialog.findViewById(R.id.btnFilterApply)).setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						filterAppType = ((FilterItemComponent) filterDialog.findViewById(R.id.fltAppType)).getFilterState();
						filterActive = ((FilterItemComponent) filterDialog.findViewById(R.id.fltActive)).getFilterState();
						filterDPI = ((FilterItemComponent) filterDialog.findViewById(R.id.fltDPI)).getFilterState();
						filterTextScale = ((FilterItemComponent) filterDialog.findViewById(R.id.fltTextScale)).getFilterState();
						filterResLoad = ((FilterItemComponent) filterDialog.findViewById(R.id.fltResLoad)).getFilterState();
						filterLocale = ((FilterItemComponent) filterDialog.findViewById(R.id.fltLocale)).getFilterState();
						filterFullscreen = ((FilterItemComponent) filterDialog.findViewById(R.id.fltFullscreen)).getFilterState();
						filterNoTitle = ((FilterItemComponent) filterDialog.findViewById(R.id.fltNoTitle)).getFilterState();
						filterScreenOn = ((FilterItemComponent) filterDialog.findViewById(R.id.fltScreenOn)).getFilterState();
						filterOrientation = ((FilterItemComponent) filterDialog.findViewById(R.id.fltOrientation)).getFilterState();
						filterResident = ((FilterItemComponent) filterDialog.findViewById(R.id.fltResident)).getFilterState();
						filterInsNotif = ((FilterItemComponent) filterDialog.findViewById(R.id.fltInsNotif)).getFilterState();
						filterPermissions = ((FilterItemComponent) filterDialog.findViewById(R.id.fltPermissions)).getFilterState();

						filterDialog.dismiss();
						appListAdaptor.getFilter().filter(nameFilter);
					}
				});

				filterDialog.show();
			}

			private void enableFilterDetails(boolean enable) {
				((FilterItemComponent) filterDialog.findViewById(R.id.fltAppType)).setEnabled(true);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltDPI)).setEnabled(enable);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltTextScale)).setEnabled(enable);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltResLoad)).setEnabled(enable);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltLocale)).setEnabled(enable);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltFullscreen)).setEnabled(enable);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltNoTitle)).setEnabled(enable);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltScreenOn)).setEnabled(enable);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltOrientation)).setEnabled(enable);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltResident)).setEnabled(enable);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltInsNotif)).setEnabled(enable);
				((FilterItemComponent) filterDialog.findViewById(R.id.fltPermissions)).setEnabled(enable);
			}
        });
    }
    
    
    // Handle background loading of apps
    private class PrepareAppsAdapter extends AsyncTask<Void,Void,AppListAdaptor> {
        ProgressDialog dialog;
        
        @Override
        protected void onPreExecute() {
            dialog = new ProgressDialog(((ListView) findViewById(R.id.lstApps)).getContext());
            dialog.setMessage("Loading apps, please wait");
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.show();
        }
        
        @Override
        protected AppListAdaptor doInBackground(Void... params) {
            if (appList.size() == 0) {
                loadApps();
            }
            return null;
        }

        @Override
        protected void onPostExecute(final AppListAdaptor result) {
            prepareAppList();
            
            try {
                dialog.dismiss();
            } catch (Exception e) {
                
            }
        }
    }    
    
	
    private class AppListFilter extends Filter {

        private AppListAdaptor adaptor;
        
        AppListFilter(AppListAdaptor adaptor) {
            super();
            this.adaptor = adaptor;
        }
        
    	@SuppressLint("WorldReadableFiles")
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

			if (filteredOut(prefs.getInt(packageName + Common.PREF_DPI, 0) > 0, filterDPI))
				return true;
			if (filteredOut(prefs.getInt(packageName + Common.PREF_FONT_SCALE, 100) != 100, filterTextScale))
				return true;
			if (filteredOut(prefs.getInt(packageName + Common.PREF_SCREEN, 0) > 0
					|| prefs.getBoolean(packageName + Common.PREF_XLARGE, false), filterResLoad))
				return true;
			if (filteredOut(!prefs.getString(packageName + Common.PREF_LOCALE, "").isEmpty(), filterLocale))
				return true;
			if (filteredOut(prefs.getBoolean(packageName + Common.PREF_FULLSCREEN, false), filterFullscreen))
				return true;
			if (filteredOut(prefs.getBoolean(packageName + Common.PREF_NO_TITLE, false), filterNoTitle))
				return true;
			if (filteredOut(prefs.getBoolean(packageName + Common.PREF_SCREEN_ON, false), filterScreenOn))
				return true;
			if (filteredOut(prefs.getInt(packageName + Common.PREF_ORIENTATION, 0) > 0, filterOrientation))
				return true;
			if (filteredOut(prefs.getBoolean(packageName + Common.PREF_RESIDENT, false), filterResident))
				return true;
			if (filteredOut(prefs.getBoolean(packageName + Common.PREF_INSISTENT_NOTIF, false), filterInsNotif))
				return true;
			if (filteredOut(prefs.getBoolean(packageName + Common.PREF_REVOKEPERMS, false), filterPermissions))
				return true;

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
            adaptor.notifyDataSetChanged();
            adaptor.clear();
            for(int i = 0, l = filteredAppList.size(); i < l; i++) {
                adaptor.add(filteredAppList.get(i));
            }
            adaptor.notifyDataSetInvalidated();            
        }
    }
    
    
    
    class AppListAdaptor extends ArrayAdapter<ApplicationInfo> implements SectionIndexer {
        
        private Map<String, Integer> alphaIndexer;
        private String[] sections;
        private Filter filter;
 
        
        @SuppressLint("DefaultLocale")
        public AppListAdaptor(Context context, List<ApplicationInfo> items) {
            super(context, R.layout.app_list_item, new ArrayList<ApplicationInfo>(items));
            
            filteredAppList.addAll(items);
            
            filter = new AppListFilter(this);
 
            alphaIndexer = new HashMap<String, Integer>();
            for(int i = filteredAppList.size() - 1; i >= 0; i--)
            {
                ApplicationInfo app = filteredAppList.get(i);
                String appName = app.name;
                String firstChar;
                if (appName == null || appName.length() < 1) {
                	firstChar = "@";
                } else {
	                firstChar = appName.substring(0, 1).toUpperCase();
	                if(firstChar.charAt(0) > 'Z' || firstChar.charAt(0) < 'A')
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
			if (row == null) {
				row = getLayoutInflater().inflate(R.layout.app_list_item, parent, false);
			}

			ApplicationInfo app = filteredAppList.get(position);

			((TextView) row.findViewById(R.id.app_name)).setText(app.name == null ? "" : app.name);
			((TextView) row.findViewById(R.id.app_package)).setTextColor(prefs.getBoolean(app.packageName + Common.PREF_ACTIVE,
			    false) ? Color.RED : Color.parseColor("#0099CC"));
			((TextView) row.findViewById(R.id.app_package)).setText(app.packageName);
			((ImageView) row.findViewById(R.id.app_icon)).setImageDrawable(app.loadIcon(getPackageManager()));

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
	                if(firstChar.charAt(0) > 'Z' || firstChar.charAt(0) < 'A')
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
