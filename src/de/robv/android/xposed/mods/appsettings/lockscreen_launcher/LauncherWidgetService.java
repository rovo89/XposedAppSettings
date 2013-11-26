package de.robv.android.xposed.mods.appsettings.lockscreen_launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.robv.android.xposed.mods.appsettings.Common;
import de.robv.android.xposed.mods.appsettings.R;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

public class LauncherWidgetService extends RemoteViewsService {
	@Override
	public RemoteViewsFactory onGetViewFactory(Intent intent) {
		Log.d("WidgetService", "Creating a view factory");
		return new LauncherRemoteViewsFactory(this.getApplicationContext(), intent);
	}
}

class LauncherRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
	
	private SharedPreferences prefs;
	public Context context;
	

	private ArrayList<ApplicationInfo> appList = new ArrayList<ApplicationInfo>();

	//private int appWidgetId;
	

	public LauncherRemoteViewsFactory(Context givenContext, Intent intent) {
		context = givenContext;
		prefs = context.getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);
		//appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
		//		AppWidgetManager.INVALID_APPWIDGET_ID);
	}

	public void onCreate() {
		// In onCreate() you setup any connections / cursors to your data source. Heavy lifting,
		// for example downloading or creating content etc, should be deferred to onDataSetChanged()
		// or getViewAt(). Taking more than 20 seconds in this call will result in an ANR.
	}

	public void onDestroy() {
		// In onDestroy() you should tear down anything that was setup for your data source.
		appList.clear();
	}

	public int getCount() {
		return appList.size();
	}

	public RemoteViews getViewAt(int position) {
		// We construct a remote views item based on our widget item xml file, and set the
		// text based on the position.
		RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.launcher_grid_item);
		
		ApplicationInfo app = appList.get(position);
		
		rv.setTextViewText(R.id.app_name, app.name == null ? "" : app.name);
		
		// Display app icon
		try {
			Context otherAppCtx = context.createPackageContext(app.packageName, Context.CONTEXT_IGNORE_SECURITY);
	
			Bitmap b = BitmapFactory.decodeResource(otherAppCtx.getResources(), app.icon);
			if (b != null) rv.setImageViewBitmap(R.id.app_icon, b);
				
		} catch (Exception e) {
			// no icon TODO is this a problem?
		}

		// Next, we set a fill-intent which will be used to fill-in the pending intent template
		// which is set on the collection view in StackWidgetProvider.
		final Intent fillInIntent = new Intent();
		fillInIntent.putExtra(LockscreenLauncherProvider.PKG_NAME, appList.get(position).packageName);
		rv.setOnClickFillInIntent(R.id.app_icon, fillInIntent);

		// You can do heaving lifting in here, synchronously. For example, if you need to
		// process an image, fetch something from the network, etc., it is ok to do it here,
		// synchronously. A loading view will show up in lieu of the actual contents in the
		// interim.

		// Return the remote views object.
		return rv;
	}

	public RemoteViews getLoadingView() {
		// You can create a custom loading view (for instance when getViewAt() is slow.) If you
		// return null here, you will get the default loading view.
		return null;
	}

	public int getViewTypeCount() {
		return 1;
	}

	public long getItemId(int position) {
		return position;
	}

	public boolean hasStableIds() {
		return false;
	}

	public void onDataSetChanged() {
		// This is triggered when you call AppWidgetManager notifyAppWidgetViewDataChanged
		// on the collection view corresponding to this factory. You can do heaving lifting in
		// here, synchronously. For example, if you need to process an image, fetch something
		// from the network, etc., it is ok to do it here, synchronously. The widget will remain
		// in its current state while work is being done here, so you don't need to worry about
		// locking up the widget.

		appList.clear();
		
		PackageManager pm = context.getPackageManager();
		List<PackageInfo> pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
		
		for (PackageInfo pkgInfo : pkgs) {

			ApplicationInfo appInfo = pkgInfo.applicationInfo;
			if (appInfo == null)
				continue;
			
			if (prefs.getBoolean(appInfo.packageName + Common.PREF_ACTIVE, false) &&
					prefs.getBoolean(appInfo.packageName + Common.PREF_ALLOW_ON_LOCKSCREEN, false)) {
				appInfo.name = appInfo.loadLabel(pm).toString();
				Log.d("Widget", "Will show " + appInfo.name + " " + appInfo.packageName);
				appList.add(appInfo);
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
}