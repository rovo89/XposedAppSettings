package de.robv.android.xposed.mods.appsettings.lockscreen_launcher;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;
import de.robv.android.xposed.mods.appsettings.R;

public class LockscreenLauncherProvider extends AppWidgetProvider {

	public static String CLICK_ACTION = "de.robv.android.xposed.mods.appsettings.lockscreen_launcher.CLICK_ACTION";
	public static String PKG_NAME = "de.robv.android.xposed.mods.appsettings.lockscreen_launcher.PKG_NAME";
	

	public void onUpdate (Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		// update each of the app widgets with the remote adapter
		for (int i = 0; i < appWidgetIds.length; ++i) {
			Log.d("Widget Provider", "Setting up widget " + i);
			// Set up the intent that starts the GridViewService, which will
			// provide the views for this collection.
			Intent intent = new Intent(context, LauncherWidgetService.class);
			// Add the app widget ID to the intent extras.
			intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
			intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
			// Instantiate the RemoteViews object for the app widget layout.
			RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.lockscreen_launcher);
			
			// Set up the RemoteViews object to use a RemoteViews adapter.
			rv.setRemoteAdapter(R.id.launcher_grid, intent);
			
			// The empty view is displayed when the collection has no items. 
			// It should be in the same layout used to instantiate the RemoteViews
			// object above.
			//rv.setEmptyView(R.id.launcher_grid, R.id.launcher_empty_view);

			// Here we setup the a pending intent template. Individuals items of a collection
			// cannot setup their own pending intents, instead, the collection as a whole can
			// setup a pending intent template, and the individual items can set a fillInIntent
			// to create unique before on an item to item basis.
			final Intent launchAppIntent = new Intent(context, LockscreenLauncherProvider.class);
			launchAppIntent.setAction(CLICK_ACTION);
			//launchAppIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, i);
			//launchAppIntent.setData(Uri.parse(launchAppIntent.toUri(Intent.URI_INTENT_SCHEME)));
			PendingIntent launchAppPendingIntent = PendingIntent.getBroadcast(context, 0, launchAppIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
			rv.setPendingIntentTemplate(R.id.launcher_grid, launchAppPendingIntent);
			Log.d("Widget Provider", "Set intent template " + launchAppIntent);

			//
			// Do additional processing specific to this app widget...
			//
				
			appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
		}
		super.onUpdate(context, appWidgetManager, appWidgetIds);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();
		
		Log.d("Widget Provider", "Received intent " + action);
		
		if (action.equals(CLICK_ACTION)) {
			
			final int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
					AppWidgetManager.INVALID_APPWIDGET_ID);
			final String pkgName = intent.getStringExtra(PKG_NAME);
			
			Log.d("Widget Provider", "Extra " + pkgName);
			
			Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(pkgName);
			context.startActivity(launchIntent);
			
		}

		super.onReceive(context, intent);
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	@Override
	public void onAppWidgetOptionsChanged (Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
		// TODO adjust spacing between rows to match spacing between columns
		super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
	}
	
}
