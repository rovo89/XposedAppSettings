package de.robv.android.xposed.mods.appsettings.hooks;

import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setIntField;

import java.lang.reflect.Method;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.mods.appsettings.Common;
import de.robv.android.xposed.mods.appsettings.XposedMod;


public class Activities {

	private static final String PROP_FULLSCREEN = "AppSettings-Fullscreen";
	private static final String PROP_KEEP_SCREEN_ON = "AppSettings-KeepScreenOn";
	private static final String PROP_ORIENTATION = "AppSettings-Orientation";

	public static void hookActivitySettings() {
		try {
			findAndHookMethod("com.android.internal.policy.impl.PhoneWindow", null, "generateLayout",
					"com.android.internal.policy.impl.PhoneWindow.DecorView", new XC_MethodHook() {

				@SuppressLint("InlinedApi")
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					Window window = (Window) param.thisObject;
					Context context = window.getContext();
					String packageName = context.getPackageName();

					if (!XposedMod.isActive(packageName))
						return;

					int fullscreen;
					try {
						fullscreen = XposedMod.prefs.getInt(packageName + Common.PREF_FULLSCREEN,
								Common.FULLSCREEN_DEFAULT);
					} catch (ClassCastException ex) {
						// Legacy boolean setting
						fullscreen = XposedMod.prefs.getBoolean(packageName + Common.PREF_FULLSCREEN, false)
								? Common.FULLSCREEN_FORCE : Common.FULLSCREEN_DEFAULT;
					}
					if (fullscreen == Common.FULLSCREEN_FORCE) {
						window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
						setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.TRUE);
					} else if (fullscreen == Common.FULLSCREEN_PREVENT) {
						window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
						setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.FALSE);
					} else if (fullscreen == Common.FULLSCREEN_IMMERSIVE) {
						if (Build.VERSION.SDK_INT >= 19) {
							window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
							setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.TRUE);

							View decorView = window.getDecorView();
							decorView.setSystemUiVisibility(
									View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
									| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
									| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
						}
					}

					if (XposedMod.prefs.getBoolean(packageName + Common.PREF_NO_TITLE, false))
						window.requestFeature(Window.FEATURE_NO_TITLE);
					
					if (XposedMod.prefs.getBoolean(packageName + Common.PREF_ALLOW_ON_LOCKSCREEN, false))
		    				window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
		    				    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
		    				    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

					if (XposedMod.prefs.getBoolean(packageName + Common.PREF_SCREEN_ON, false)) {
						window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
						setAdditionalInstanceField(window, PROP_KEEP_SCREEN_ON, Boolean.TRUE);
					}

					int orientation = XposedMod.prefs.getInt(packageName + Common.PREF_ORIENTATION, XposedMod.prefs.getInt(Common.PREF_DEFAULT + Common.PREF_ORIENTATION, 0));
					if (orientation > 0 && orientation < Common.orientationCodes.length && context instanceof Activity) {
						((Activity) context).setRequestedOrientation(Common.orientationCodes[orientation]);
						setAdditionalInstanceField(context, PROP_ORIENTATION, orientation);
					}
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}
		
		try {
			findAndHookMethod(Window.class, "setFlags", int.class, int.class,
					new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

					int flags = (Integer) param.args[0];
					int mask = (Integer) param.args[1];
					if ((mask & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
						Boolean fullscreen = (Boolean) getAdditionalInstanceField(param.thisObject, PROP_FULLSCREEN);
						if (fullscreen != null) {
							if (fullscreen.booleanValue()) {
								flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
							} else {
								flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
							}
							param.args[0] = flags;
						}
					}
					if ((mask & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0) {
						Boolean keepScreenOn = (Boolean) getAdditionalInstanceField(param.thisObject, PROP_KEEP_SCREEN_ON);
						if (keepScreenOn != null) {
							if (keepScreenOn.booleanValue()) {
								flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
							}
							param.args[0] = flags;
						}
					}
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}

		try {
			findAndHookMethod(Activity.class, "setRequestedOrientation", int.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					Integer orientation = (Integer) getAdditionalInstanceField(param.thisObject, PROP_ORIENTATION);
					if (orientation != null)
						param.args[0] = Common.orientationCodes[orientation];
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}

		try {
			// Hook one of the several variations of ActivityStack.realStartActivityLocked from different ROMs
			Method mthRealStartActivityLocked;
			if (Build.VERSION.SDK_INT <= 18) {
				try {
					mthRealStartActivityLocked = findMethodExact("com.android.server.am.ActivityStack", null, "realStartActivityLocked",
							"com.android.server.am.ActivityRecord", "com.android.server.am.ProcessRecord",
							boolean.class, boolean.class, boolean.class);
				} catch (NoSuchMethodError t) {
					mthRealStartActivityLocked = findMethodExact("com.android.server.am.ActivityStack", null, "realStartActivityLocked",
							"com.android.server.am.ActivityRecord", "com.android.server.am.ProcessRecord",
							boolean.class, boolean.class);
				}
			} else {
				mthRealStartActivityLocked = findMethodExact("com.android.server.am.ActivityStackSupervisor", null, "realStartActivityLocked",
						"com.android.server.am.ActivityRecord", "com.android.server.am.ProcessRecord",
						boolean.class, boolean.class);
			}
			hookMethod(mthRealStartActivityLocked, new XC_MethodHook() {

	    		@Override
	    		protected void afterHookedMethod(MethodHookParam param) throws Throwable {
	    			String pkgName = (String) getObjectField(param.args[0], "packageName");
	    			if (!XposedMod.isActive(pkgName, Common.PREF_RESIDENT))
	    				return;
	    			
					int adj = -12;
					Object proc = getObjectField(param.args[0], "app");
					
					// Override the *Adj values if meant to be resident in memory
					if (proc != null) {
						setIntField(proc, "maxAdj", adj);
						if (Build.VERSION.SDK_INT <= 18)
							setIntField(proc, "hiddenAdj", adj);
						setIntField(proc, "curRawAdj", adj);
						setIntField(proc, "setRawAdj", adj);
						setIntField(proc, "curAdj", adj);
						setIntField(proc, "setAdj", adj);
					}
	    		}
	    	});
	    } catch (Throwable e) {
	        XposedBridge.log(e);
	    }
    }
}
