package de.robv.android.xposed.mods.appsettings;


import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.removeAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setFloatField;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

import java.util.Locale;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AndroidAppHelper;
import android.app.Notification;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.content.res.XResources.DimensionReplacement;
import android.media.AudioTrack;
import android.media.JetPlayer;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.ViewConfiguration;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.mods.appsettings.hooks.Activities;
import de.robv.android.xposed.mods.appsettings.hooks.PackagePermissions;

public class XposedMod implements IXposedHookZygoteInit, IXposedHookLoadPackage {

	public static final String this_package = XposedMod.class.getPackage().getName();

	private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
	private static final String[] SYSTEMUI_ADJUSTED_DIMENSIONS = {
		"status_bar_height",
		"navigation_bar_height", "navigation_bar_height_landscape",
		"navigation_bar_width",
		"system_bar_height"
	};

	public static XSharedPreferences prefs;

	@Override
	public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
		loadPrefs();

		adjustSystemDimensions();

		// Hook to override DPI (globally, including resource load + rendering)
		try {
			if (Build.VERSION.SDK_INT < 17) {
				findAndHookMethod(Display.class, "init", int.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						String packageName = AndroidAppHelper.currentPackageName();

						if (!isActive(packageName)) {
							// No overrides for this package
							return;
						}

						int packageDPI = prefs.getInt(packageName + Common.PREF_DPI,
							prefs.getInt(Common.PREF_DEFAULT + Common.PREF_DPI, 0));
						if (packageDPI > 0) {
							// Density for this package is overridden, change density
							setFloatField(param.thisObject, "mDensity", packageDPI / 160.0f);
						}
					};
				});
			} else {
				findAndHookMethod(Display.class, "updateDisplayInfoLocked", new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						String packageName = AndroidAppHelper.currentPackageName();

						if (!isActive(packageName)) {
							// No overrides for this package
							return;
						}

						int packageDPI = prefs.getInt(packageName + Common.PREF_DPI,
							prefs.getInt(Common.PREF_DEFAULT + Common.PREF_DPI, 0));
						if (packageDPI > 0) {
							// Density for this package is overridden, change density
							Object mDisplayInfo = getObjectField(param.thisObject, "mDisplayInfo");
							setIntField(mDisplayInfo, "logicalDensityDpi", packageDPI);
						}
					};
				});
			}
		} catch (Throwable t) {
			XposedBridge.log(t);
		}

		// Override settings used when loading resources
		try {
			findAndHookMethod(Resources.class, "updateConfiguration",
				Configuration.class, DisplayMetrics.class, "android.content.res.CompatibilityInfo",
				new XC_MethodHook() {

				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					if (param.args[0] == null)
						return;

					/* Starting with XposedBridge v52, by the time updateConfiguration is called this object
					 * might not yet be an XResources instance where the package name can be fetched fom.
					 * If that's the case, use the newly introduced getPackageNameDuringConstruction method
					 * which was introduced for this purpose and fetches the package name for this Resource
					 * in the middle of initialization.
					 */
					String packageName;
					Resources res = ((Resources) param.thisObject);
					if (res instanceof XResources) {
						packageName = ((XResources) res).getPackageName();
					} else if (res instanceof XModuleResources) {
						return;
					} else {
						try {
							packageName = XResources.getPackageNameDuringConstruction();
						} catch (IllegalStateException e) {
							// That's ok, we might have been called for
							// non-standard resources
							return;
						}
					}

					String hostPackageName = AndroidAppHelper.currentPackageName();
					boolean isActiveApp = hostPackageName.equals(packageName);

					// Workaround for KitKat. The keyguard is a different package now but runs in the
					// same process as SystemUI and displays as main package
					if (Build.VERSION.SDK_INT >= 19 && hostPackageName.equals("com.android.keyguard"))
						hostPackageName = SYSTEMUI_PACKAGE;

					// If setting enabled to also modify resources on other host packages (typically
					// for widgets), simulate that this package is not hosted by another one
					// For everything except the process-wide default Locale - see below
					if (isActive(packageName, Common.PREF_RES_ON_WIDGETS))
						hostPackageName = packageName;

					// settings related to the density etc. are calculated for the running app...
					Configuration newConfig = null;
					if (hostPackageName != null && isActive(hostPackageName)) {
						int screen = prefs.getInt(hostPackageName + Common.PREF_SCREEN,
							prefs.getInt(Common.PREF_DEFAULT + Common.PREF_SCREEN, 0));
						if (screen < 0 || screen >= Common.swdp.length)
							screen = 0;

						int dpi = prefs.getInt(hostPackageName + Common.PREF_DPI,
								prefs.getInt(Common.PREF_DEFAULT + Common.PREF_DPI, 0));
						int fontScale = prefs.getInt(hostPackageName + Common.PREF_FONT_SCALE,
								prefs.getInt(Common.PREF_DEFAULT + Common.PREF_FONT_SCALE, 0));
						int swdp = Common.swdp[screen];
						int wdp = Common.wdp[screen];
						int hdp = Common.hdp[screen];

						boolean xlarge = prefs.getBoolean(hostPackageName + Common.PREF_XLARGE, false);

						if (swdp > 0 || xlarge || dpi > 0 || fontScale > 0) {
							newConfig = new Configuration((Configuration) param.args[0]);

							DisplayMetrics newMetrics;
							if (param.args[1] != null) {
								newMetrics = new DisplayMetrics();
								newMetrics.setTo((DisplayMetrics) param.args[1]);
								param.args[1] = newMetrics;
							} else {
								newMetrics = res.getDisplayMetrics();
							}

							if (swdp > 0) {
								newConfig.smallestScreenWidthDp = swdp;
								newConfig.screenWidthDp = wdp;
								newConfig.screenHeightDp = hdp;
							}
							if (xlarge)
								newConfig.screenLayout |= Configuration.SCREENLAYOUT_SIZE_XLARGE;
							if (dpi > 0) {
								newMetrics.density = dpi / 160f;
								newMetrics.densityDpi = dpi;

								if (Build.VERSION.SDK_INT >= 17)
									setIntField(newConfig, "densityDpi", dpi);
							}
							if (fontScale > 0)
								newConfig.fontScale = fontScale / 100.0f;
						}
					}

					// ... whereas the locale is taken from the app for which resources are loaded
					if (packageName != null && isActive(packageName)) {
						Locale loc = getPackageSpecificLocale(packageName);
						if (loc != null) {
							if (newConfig == null)
								newConfig = new Configuration((Configuration) param.args[0]);

							setConfigurationLocale(newConfig, loc);
							// Also set the locale as the app-wide default,
							// for purposes other than resource loading
							// Only do this when the package's res are not being loaded by a different
							// host, regardless of the Widgets setting
							if (isActiveApp)
								Locale.setDefault(loc);
						}
					}

					if (newConfig != null)
						param.args[0] = newConfig;
				}
			});
		} catch (Throwable t) {
			XposedBridge.log(t);
		}

		Activities.hookActivitySettings();
	}


	@SuppressLint("NewApi")
	private void setConfigurationLocale(Configuration config, Locale loc) {
		config.locale = loc;
		if (Build.VERSION.SDK_INT >= 17) {
			// Don't use setLocale() in order not to trigger userSetLocale
			config.setLayoutDirection(loc);
		}
	}


	/** Adjust all framework dimensions that should reflect
	 *  changes related with SystemUI, namely statusbar and navbar sizes.
	 *  The values are adjusted and replaced system-wide by fixed px values.
	 */
	private void adjustSystemDimensions() {
		if (!isActive(SYSTEMUI_PACKAGE))
			return;

		int systemUiDpi = prefs.getInt(SYSTEMUI_PACKAGE + Common.PREF_DPI,
				prefs.getInt(Common.PREF_DEFAULT + Common.PREF_DPI, 0));
		if (systemUiDpi <= 0)
			return;

		// SystemUI had its DPI overridden.
		// Adjust the relevant framework dimen resources.
		Resources sysRes = Resources.getSystem();
		float sysDensity = sysRes.getDisplayMetrics().density;
		float scaleFactor = (systemUiDpi / 160f) / sysDensity;

		for (String resName : SYSTEMUI_ADJUSTED_DIMENSIONS) {
			int id = sysRes.getIdentifier(resName, "dimen", "android");
			if (id != 0) {
				float original = sysRes.getDimension(id);
				XResources.setSystemWideReplacement(id,
						new DimensionReplacement(original * scaleFactor, TypedValue.COMPLEX_UNIT_PX));
			}
		}
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		prefs.reload();
		if (lpparam.packageName.equals("android")) {
			try {
				final int sdk = Build.VERSION.SDK_INT;
				XC_MethodHook notifyHook = new XC_MethodHook() {
					@SuppressWarnings("deprecation")
					@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
						String packageName = (String) param.args[0];

						Notification n;
						if (sdk <= 15 || sdk >= 18)
							n = (Notification) param.args[6];
						else
							n = (Notification) param.args[5];

						prefs.reload();
						if (!isActive(packageName))
							return;

						if (isActive(packageName, Common.PREF_INSISTENT_NOTIF)) {
							n.flags |= Notification.FLAG_INSISTENT;
						}
						if (isActive(packageName, Common.PREF_NO_BIG_NOTIFICATIONS)) {
							try {
								setObjectField(n, "bigContentView", null);
							} catch (Exception e) { }
						}
						int ongoingNotif = XposedMod.prefs.getInt(packageName + Common.PREF_ONGOING_NOTIF,
								Common.ONGOING_NOTIF_DEFAULT);
						if (ongoingNotif == Common.ONGOING_NOTIF_FORCE) {
							n.flags |= Notification.FLAG_ONGOING_EVENT;
						} else if (ongoingNotif == Common.ONGOING_NOTIF_PREVENT) {
							n.flags &= ~Notification.FLAG_ONGOING_EVENT & ~Notification.FLAG_FOREGROUND_SERVICE;
						}

						if (isActive(packageName, Common.PREF_MUTE)) {
							n.sound = null;
							n.flags &= ~Notification.DEFAULT_SOUND;
						}
						if (sdk >= 16 && isActive(packageName) && prefs.contains(packageName + Common.PREF_NOTIF_PRIORITY)) {
							int priority = XposedMod.prefs.getInt(packageName + Common.PREF_NOTIF_PRIORITY, 0);
							if (priority > 0 && priority < Common.notifPriCodes.length) {
								n.flags &= ~Notification.FLAG_HIGH_PRIORITY;
								n.priority = Common.notifPriCodes[priority];
							}
						}
					}
				};
				if (sdk <= 15) {
					findAndHookMethod("com.android.server.NotificationManagerService", lpparam.classLoader, "enqueueNotificationInternal",
							String.class, int.class, int.class, String.class, int.class, int.class, Notification.class, int[].class,
							notifyHook);
				} else if (sdk == 16) {
					findAndHookMethod("com.android.server.NotificationManagerService", lpparam.classLoader, "enqueueNotificationInternal",
							String.class, int.class, int.class, String.class, int.class, Notification.class, int[].class,
							notifyHook);
				} else if (sdk == 17) {
					findAndHookMethod("com.android.server.NotificationManagerService", lpparam.classLoader, "enqueueNotificationInternal",
							String.class, int.class, int.class, String.class, int.class, Notification.class, int[].class, int.class,
							notifyHook);
				} else if (sdk < 21) {
					findAndHookMethod("com.android.server.NotificationManagerService", lpparam.classLoader, "enqueueNotificationInternal",
							String.class, String.class, int.class, int.class, String.class, int.class, Notification.class, int[].class, int.class,
							notifyHook);
				} else {
					findAndHookMethod("com.android.server.notification.NotificationManagerService", lpparam.classLoader, "enqueueNotificationInternal",
							String.class, String.class, int.class, int.class, String.class, int.class, Notification.class, int[].class, int.class,
							notifyHook);
				}
			} catch (Throwable t) {
				XposedBridge.log(t);
			}

			PackagePermissions.initHooks(lpparam.classLoader);
			Activities.hookActivitySettingsInSystemServer(lpparam.classLoader);
		}

		// Override the default Locale if one is defined (not res-related, here)
		if (isActive(lpparam.packageName)) {
			Locale packageLocale = getPackageSpecificLocale(lpparam.packageName);
			if (packageLocale != null)
				Locale.setDefault(packageLocale);
		}

		if (this_package.equals(lpparam.packageName)) {
			findAndHookMethod("de.robv.android.xposed.mods.appsettings.XposedModActivity",
					lpparam.classLoader, "isModActive", XC_MethodReplacement.returnConstant(true));
		}

		try {
			if (isActive(lpparam.packageName, Common.PREF_LEGACY_MENU))
				findAndHookMethod(ViewConfiguration.class, "hasPermanentMenuKey",
						XC_MethodReplacement.returnConstant(true));
		} catch (Throwable t) {
			XposedBridge.log(t);
		}

		try {
			if (isActive(lpparam.packageName, Common.PREF_MUTE)) {

				// Hook the AudioTrack API
				findAndHookMethod(AudioTrack.class, "play", XC_MethodReplacement.returnConstant(null));

				// Hook the JetPlayer API
				findAndHookMethod(JetPlayer.class, "play", XC_MethodReplacement.returnConstant(null));

				// Hook the MediaPlayer API
				XC_MethodHook displayHook = new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
						// Detect if video will be used for this media
						if (param.args[0] != null)
							setAdditionalInstanceField(param.thisObject, "HasVideo", true);
						else
							removeAdditionalInstanceField(param.thisObject, "HasVideo");
					}
				};
				findAndHookMethod(MediaPlayer.class, "setSurface", Surface.class, displayHook);
				findAndHookMethod(MediaPlayer.class, "setDisplay", SurfaceHolder.class, displayHook);
				findAndHookMethod(MediaPlayer.class, "start", new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
						if (getAdditionalInstanceField(param.thisObject, "HasVideo") != null)
							// Video will be used - still start the media but with muted volume
							((MediaPlayer) param.thisObject).setVolume(0, 0);
						else
							// No video - skip starting to play the media altogether
							param.setResult(null);
					}
				});

				// Hook the SoundPool API
				findAndHookMethod(SoundPool.class, "play", int.class, float.class, float.class,
						int.class, int.class, float.class,
						XC_MethodReplacement.returnConstant(0));
				findAndHookMethod(SoundPool.class, "resume", int.class, XC_MethodReplacement.returnConstant(null));
			}
		} catch (Throwable t) {
			XposedBridge.log(t);
		}
	}

	private static Locale getPackageSpecificLocale(String packageName) {
		String locale = prefs.getString(packageName + Common.PREF_LOCALE, null);
		if (locale == null || locale.isEmpty())
			return null;

		String[] localeParts = locale.split("_", 3);
		String language = localeParts[0];
		String region = (localeParts.length >= 2) ? localeParts[1] : "";
		String variant = (localeParts.length >= 3) ? localeParts[2] : "";
		return new Locale(language, region, variant);
	}


	public static void loadPrefs() {
		prefs = new XSharedPreferences(Common.MY_PACKAGE_NAME, Common.PREFS);
		prefs.makeWorldReadable();
	}

	public static boolean isActive(String packageName) {
		return prefs.getBoolean(packageName + Common.PREF_ACTIVE, false);
	}

	public static boolean isActive(String packageName, String sub) {
		return prefs.getBoolean(packageName + Common.PREF_ACTIVE, false) && prefs.getBoolean(packageName + sub, false);
	}
}
