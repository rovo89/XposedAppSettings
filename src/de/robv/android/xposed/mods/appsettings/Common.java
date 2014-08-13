package de.robv.android.xposed.mods.appsettings;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
import android.annotation.TargetApi;
import android.app.Notification;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class Common {

	public static final String TAG = "AppSettings";
	public static final String MY_PACKAGE_NAME = Common.class.getPackage().getName();

	public static final String ACTION_PERMISSIONS = "update_permissions";


	public static final String PREFS = "ModSettings";

	public static final String PREF_DEFAULT = "default";

	public static final String PREF_ACTIVE = "/active";
	public static final String PREF_DPI = "/dpi";
	public static final String PREF_FONT_SCALE = "/font-scale";
	public static final String PREF_LOCALE = "/locale";
	public static final String PREF_SCREEN = "/screen";
	public static final String PREF_XLARGE = "/tablet";
	public static final String PREF_RES_ON_WIDGETS = "/res-on-widgets";
	public static final String PREF_RESIDENT = "/resident";
	public static final String PREF_NO_FULLSCREEN_IME = "/no-fullscreen-ime";
	public static final String PREF_NO_BIG_NOTIFICATIONS = "/no-big-notifications";
	public static final String PREF_INSISTENT_NOTIF = "/insistent-notif";
	public static final String PREF_ONGOING_NOTIF = "/ongoing-notif";
	public static final String PREF_NOTIF_PRIORITY = "/notif-priority";
	public static final String PREF_REVOKEPERMS = "/revoke-perms";
	public static final String PREF_REVOKELIST = "/revoke-list";
	public static final String PREF_FULLSCREEN = "/fullscreen";
	public static final String PREF_NO_TITLE = "/no-title";
	public static final String PREF_ALLOW_ON_LOCKSCREEN = "/allow-on-lockscreen";
	public static final String PREF_SCREEN_ON = "/screen-on";
	public static final String PREF_ORIENTATION = "/orientation";
	public static final String PREF_RECENTS_MODE = "/recents-mode";
	public static final String PREF_MUTE = "/mute";
	public static final String PREF_LEGACY_MENU = "/legacy-menu";

	public static final int[] swdp = { 0, 320, 480, 600, 800, 1000 };
	public static final int[] wdp = { 0, 320, 480, 600, 800, 1000 };
	public static final int[] hdp = { 0, 480, 854, 1024, 1280, 1600 };

	public static int[] orientationCodes = { Integer.MIN_VALUE,
		SCREEN_ORIENTATION_UNSPECIFIED,
		SCREEN_ORIENTATION_PORTRAIT, SCREEN_ORIENTATION_LANDSCAPE,
		SCREEN_ORIENTATION_SENSOR,
		SCREEN_ORIENTATION_SENSOR_PORTRAIT, SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
		SCREEN_ORIENTATION_REVERSE_PORTRAIT, SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
		SCREEN_ORIENTATION_FULL_SENSOR,
		// These require API 18
		SCREEN_ORIENTATION_USER_PORTRAIT, SCREEN_ORIENTATION_USER_LANDSCAPE,
		SCREEN_ORIENTATION_FULL_USER };
	{
		if (Build.VERSION.SDK_INT < 18) {
			// Strip out the last 3 entries
			int[] newCodes = new int[orientationCodes.length - 3];
			System.arraycopy(orientationCodes, 0, newCodes, 0, orientationCodes.length - 3);
			orientationCodes = newCodes;
		}
	}
	public static int[] orientationLabels = { R.string.settings_default,
		R.string.settings_ori_normal,
		R.string.settings_ori_portrait, R.string.settings_ori_landscape,
		R.string.settings_ori_forceauto,
		R.string.settings_ori_portrait_sensor, R.string.settings_ori_landscape_sensor,
		R.string.settings_ori_portrait_reverse, R.string.settings_ori_landscape_reverse,
		R.string.settings_ori_forceauto_4way,
		// These require API 18
		R.string.settings_ori_portrait_user, R.string.settings_ori_landscape_user,
		R.string.settings_ori_user_4way };
	{
		if (Build.VERSION.SDK_INT < 18) {
			// Strip out the last 3 entries
			int[] newLabels = new int[orientationLabels.length - 3];
			System.arraycopy(orientationLabels, 0, newLabels, 0, orientationLabels.length - 3);
			orientationLabels = newLabels;
		}
	}

	public static final int[] notifPriCodes = { Integer.MIN_VALUE,
		Notification.PRIORITY_MAX, Notification.PRIORITY_HIGH,
		Notification.PRIORITY_DEFAULT,
		Notification.PRIORITY_LOW, Notification.PRIORITY_MIN };
	public static final int[] notifPriLabels = { R.string.settings_default,
		R.string.settings_npri_max, R.string.settings_npri_high,
		R.string.settings_npri_normal,
		R.string.settings_npri_low,
		R.string.settings_npri_min };

	public static final int FULLSCREEN_DEFAULT = 0;
	public static final int FULLSCREEN_FORCE = 1;
	public static final int FULLSCREEN_PREVENT = 2;
	public static final int FULLSCREEN_IMMERSIVE = 3;

	public static final int ONGOING_NOTIF_DEFAULT = 0;
	public static final int ONGOING_NOTIF_FORCE = 1;
	public static final int ONGOING_NOTIF_PREVENT = 2;

	public static final int PREF_RECENTS_DEFAULT = 0;
	public static final int PREF_RECENTS_FORCE = 1;
	public static final int PREF_RECENTS_PREVENT = 2;

}
