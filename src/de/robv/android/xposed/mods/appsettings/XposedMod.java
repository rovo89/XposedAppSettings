package de.robv.android.xposed.mods.appsettings;


import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setFloatField;
import static de.robv.android.xposed.XposedHelpers.setIntField;

import java.io.File;
import java.util.Locale;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XResources;
import android.os.Build;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.view.Display;
import de.robv.android.xposed.IXposedHookCmdInit;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.mods.appsettings.hooks.Activities;
import de.robv.android.xposed.mods.appsettings.hooks.PackagePermissions;

public class XposedMod implements IXposedHookZygoteInit, IXposedHookLoadPackage, IXposedHookCmdInit {

	public static SharedPreferences prefs;
	
	
	@Override
	public void initZygote(de.robv.android.xposed.IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
		
		/*
		 * Do not load the preferences in the Zygote process (else they will be
		 * inherited) but rather on startup of each forked process.
		 */
		try {
			findAndHookMethod("com.android.internal.os.ZygoteInit", XposedMod.class.getClassLoader(), "handleSystemServerProcess", "com.android.internal.os.ZygoteConnection.Arguments", new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					// Load the preferences object that can be used throughout
					// the entire systemserver process
					loadPrefs();

					// Other actions done at the very beginning of systemserver
					// may go here
				}
			});
		} catch (Throwable t) {
			XposedBridge.log(t);
		}
		
		try {
			findAndHookMethod("com.android.internal.os.ZygoteConnection", XposedMod.class.getClassLoader(), "handleChildProc",
					"com.android.internal.os.ZygoteConnection.Arguments", "[Ljava.io.FileDescriptor;",
					"java.io.FileDescriptor", "java.io.PrintStream", new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					// Load the preferences object that can be used throughout
					// the entire process
					loadPrefs();

					// Other actions done at the very beginning of other
					// processes forked by Zygote may go here
				}
			});
		} catch (Throwable t) {
			XposedBridge.log(t);
		}
		
		

		// Hook to override DPI (globally, including resource load + rendering)
		try {
			if (Build.VERSION.SDK_INT < 17) {
				findAndHookMethod(Display.class, "init", int.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						String packageName = AndroidAppHelper.currentPackageName();

						if (prefs == null || !prefs.getBoolean(packageName + Common.PREF_ACTIVE, false)) {
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

						if (prefs == null || !prefs.getBoolean(packageName + Common.PREF_ACTIVE, false)) {
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
					if (prefs == null)
						return;
					
					if (param.args[0] != null && param.thisObject instanceof XResources) {
						String packageName = ((XResources) param.thisObject).getPackageName();
						if (packageName != null) {
							boolean isActiveApp = AndroidAppHelper.currentPackageName().equals(packageName);
							
							int screen = prefs.getInt(packageName + Common.PREF_SCREEN,
								prefs.getInt(Common.PREF_DEFAULT + Common.PREF_SCREEN, 0));
							if (screen < 0 || screen >= Common.swdp.length)
								screen = 0;
							
							int dpi = (isActiveApp && Build.VERSION.SDK_INT >= 17) ?
								prefs.getInt(packageName + Common.PREF_DPI, prefs.getInt(Common.PREF_DEFAULT + Common.PREF_DPI, 0)) : 0;
							int swdp = Common.swdp[screen];
							int wdp = Common.wdp[screen];
							int hdp = Common.hdp[screen];
							int w = Common.w[screen];
							int h = Common.h[screen];
							
							boolean tablet = prefs.getBoolean(packageName + Common.PREF_TABLET, false);
							
							Locale loc = getPackageSpecificLocale(packageName);
							
							if (swdp > 0 || loc != null || tablet || dpi > 0) {
								Configuration newConfig = new Configuration((Configuration) param.args[0]);
								if (swdp > 0) {
									newConfig.smallestScreenWidthDp = swdp;
									newConfig.screenWidthDp = wdp;
									newConfig.screenHeightDp = hdp;
								}
								if (loc != null) {
									newConfig.locale = loc;
									// Also set the locale as the app-wide default,
									// for purposes other than resource loading
									if (isActiveApp)
										Locale.setDefault(loc);
								}
								if (tablet)
									newConfig.screenLayout |= Configuration.SCREENLAYOUT_SIZE_XLARGE;
								if (dpi > 0)
									setIntField(newConfig, "densityDpi", dpi);
								param.args[0] = newConfig;
								
								if (w > 0 && param.args[1] != null) {
									DisplayMetrics newMetrics = new DisplayMetrics();
									newMetrics.setTo((DisplayMetrics) param.args[1]);
									newMetrics.widthPixels = w;
									newMetrics.heightPixels = h;
									param.args[1] = newMetrics;
								}
							}
						}
					}
				}
			});
		} catch (Throwable t) {
			XposedBridge.log(t);
		}
        
        PackagePermissions.initHooks();
        Activities.hookActivitySettings();
	}

	
    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {

        // Override the default Locale if one is defined (not res-related, here)
        if (prefs != null && prefs.getBoolean(lpparam.packageName + Common.PREF_ACTIVE, false)) {
    		Locale packageLocale = getPackageSpecificLocale(lpparam.packageName);
    		if (packageLocale != null)
    			Locale.setDefault(packageLocale);
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


	@Override
    public void initCmdApp(de.robv.android.xposed.IXposedHookCmdInit.StartupParam startupParam) throws Throwable {
		loadPrefs();
    }
	
	
	public static void loadPrefs() {
		File prefFile = new File(Environment.getDataDirectory(), "data/" + Common.MY_PACKAGE_NAME + "/shared_prefs/" + Common.PREFS + ".xml");
		if (prefFile.exists())
			prefs = AndroidAppHelper.getSharedPreferencesForPackage(Common.MY_PACKAGE_NAME, Common.PREFS, Context.MODE_PRIVATE);
		else
			prefs = null;
	}
	
	public static boolean isActive(String packageName) {
		return prefs.getBoolean(packageName + Common.PREF_ACTIVE, false);
	}
	
	public static boolean isActive(String packageName, String sub) {
		return prefs.getBoolean(packageName + Common.PREF_ACTIVE, false) && prefs.getBoolean(packageName + sub, false);
	}
}
