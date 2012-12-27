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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SectionIndexer;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import de.robv.android.xposed.mods.appsettings.settings.ApplicationSettings;


public class XposedModActivity extends Activity {

    private ArrayList<ApplicationInfo> appList = new ArrayList<ApplicationInfo>();    
    private ArrayList<ApplicationInfo> filteredAppList = new ArrayList<ApplicationInfo>();    
    private String activeFilter;
    private SharedPreferences prefs;
	

	@SuppressLint("WorldReadableFiles")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTitle(R.string.app_name);
		super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(XposedMod.PREFS, Context.MODE_WORLD_READABLE);
		
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
                activeFilter = query;
                appListAdaptor.getFilter().filter(activeFilter);
                ((SearchView) findViewById(R.id.searchApp)).clearFocus();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                activeFilter = newText;
                appListAdaptor.getFilter().filter(activeFilter);
                return false;
            }
            
            
        });
        
        ((CheckBox) findViewById(R.id.chkOnlyTweaked)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                appListAdaptor.getFilter().filter(activeFilter);
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
            
            boolean onlyTweaked = ((CheckBox) findViewById(R.id.chkOnlyTweaked)).isChecked();
            SharedPreferences prefs = getSharedPreferences(XposedMod.PREFS, Context.MODE_WORLD_READABLE);
            
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
                if (onlyTweaked && !prefs.getBoolean(app.packageName + XposedMod.PREF_ACTIVE, false)) {
                    i.remove();
                }
            }

            result.values = items;
            result.count = items.size();
            
            return result;
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
			((TextView) row.findViewById(R.id.app_package)).setTextColor(prefs.getBoolean(app.packageName + XposedMod.PREF_ACTIVE,
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
