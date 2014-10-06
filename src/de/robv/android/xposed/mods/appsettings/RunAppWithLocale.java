package de.robv.android.xposed.mods.appsettings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

public class RunAppWithLocale extends BroadcastReceiver {
	public static final String ACTION_RUN_APP_WITH_LOCALE = "appsettings.intent.action.RUN_APP_WITH_LOCALE";

	@Override
	public void onReceive(final Context context, Intent intent) {
		String action = intent.getAction();
		final SharedPreferences prefs = context.getSharedPreferences(Common.PREFS, Context.MODE_WORLD_READABLE);
		if (ACTION_RUN_APP_WITH_LOCALE.equals(action)) {
			Bundle extras = intent.getExtras();
			if (extras == null) {
				return;
			}
			final String packageName = extras.getString("package");
			final String newLocale = extras.getString("locale");
			final boolean active = prefs.getBoolean(packageName + Common.PREF_ACTIVE, false);
			final String currentLocale = prefs.getString(packageName + Common.PREF_LOCALE, null);
			final SharedPreferences.Editor prefsEditor = prefs.edit();
			prefsEditor.putBoolean(packageName + Common.PREF_ACTIVE, true);
			prefsEditor.putString(packageName + Common.PREF_LOCALE, newLocale);
			prefsEditor.apply();
			Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
			if (launchIntent != null) {
				launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(launchIntent);
			}
			// TODO: check for current settings and restore them
			Handler revertBack = new Handler();
			revertBack.postDelayed(new Runnable() {

				@Override
				public void run() {
					prefsEditor.putBoolean(packageName + Common.PREF_ACTIVE, false);
					prefsEditor.putString(packageName + Common.PREF_LOCALE, null);
					prefsEditor.apply();
				}
			}, 5000);
		}
	}

}