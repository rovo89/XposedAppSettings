package de.robv.android.xposed.mods.appsettings;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

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
	public static final String PREF_RESIDENT = "/resident";
	public static final String PREF_NO_FULLSCREEN_IME = "/no-fullscreen-ime";
	public static final String PREF_NO_BIG_NOTIFICATIONS = "/no-big-notifications";
	public static final String PREF_INSISTENT_NOTIF = "/insistent-notif";
	public static final String PREF_REVOKEPERMS = "/revoke-perms";
	public static final String PREF_REVOKELIST = "/revoke-list";
	public static final String PREF_FULLSCREEN = "/fullscreen";
	public static final String PREF_NO_TITLE = "/no-title";
	public static final String PREF_ALLOW_ON_LOCKSCREEN = "/allow-on-lockscreen";
	public static final String PREF_SCREEN_ON = "/screen-on";
	public static final String PREF_ORIENTATION = "/orientation";

	public static final int[] swdp = { 0, 320, 480, 600, 800, 1000 };
	public static final int[] wdp = { 0, 320, 480, 600, 800, 1000 };
	public static final int[] hdp = { 0, 480, 854, 1024, 1280, 1600 };
	
	public static final int[] orientationCodes = { Integer.MIN_VALUE, SCREEN_ORIENTATION_UNSPECIFIED, SCREEN_ORIENTATION_PORTRAIT, SCREEN_ORIENTATION_LANDSCAPE, SCREEN_ORIENTATION_SENSOR };
	public static final int[] orientationLabels = { R.string.settings_default, R.string.settings_ori_normal,
		R.string.settings_ori_portrait, R.string.settings_ori_landscape, R.string.settings_ori_forceauto };

	public static final int FULLSCREEN_DEFAULT = 0;
	public static final int FULLSCREEN_FORCE = 1;
	public static final int FULLSCREEN_PREVENT = 2;
	public static final int FULLSCREEN_IMMERSIVE = 3;

}
