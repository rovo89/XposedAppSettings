package de.robv.android.xposed.mods.appsettings.hooks;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticIntField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setIntField;

import java.lang.reflect.Method;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.mods.appsettings.Common;
import de.robv.android.xposed.mods.appsettings.XposedMod;


public class Activities {

	private static final String PROP_FULLSCREEN = "AppSettings-Fullscreen";
	private static final String PROP_IMMERSIVE = "AppSettings-Immersive";
	private static final String PROP_KEEP_SCREEN_ON = "AppSettings-KeepScreenOn";
	private static final String PROP_LEGACY_MENU = "AppSettings-LegacyMenu";
	private static final String PROP_ORIENTATION = "AppSettings-Orientation";

	private static int FLAG_NEEDS_MENU_KEY = getStaticIntField(WindowManager.LayoutParams.class, "FLAG_NEEDS_MENU_KEY");

	public static void hookActivitySettings() {
		try {
			findAndHookMethod("com.android.internal.policy.impl.PhoneWindow", null, "generateLayout",
					"com.android.internal.policy.impl.PhoneWindow.DecorView", new XC_MethodHook() {

				@SuppressLint("InlinedApi")
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					Window window = (Window) param.thisObject;
					View decorView = (View) param.args[0];
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
					} else if (fullscreen == Common.FULLSCREEN_IMMERSIVE && Build.VERSION.SDK_INT >= 19) {
						window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
						setAdditionalInstanceField(window, PROP_FULLSCREEN, Boolean.TRUE);
						setAdditionalInstanceField(decorView, PROP_IMMERSIVE, Boolean.TRUE);
						decorView.setSystemUiVisibility(
								View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
								| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
								| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
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

					if (XposedMod.prefs.getBoolean(packageName + Common.PREF_LEGACY_MENU, false)) {
						window.setFlags(FLAG_NEEDS_MENU_KEY, FLAG_NEEDS_MENU_KEY);
						setAdditionalInstanceField(window, PROP_LEGACY_MENU, Boolean.TRUE);
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
					if ((mask & FLAG_NEEDS_MENU_KEY) != 0) {
						Boolean menu = (Boolean) getAdditionalInstanceField(param.thisObject, PROP_LEGACY_MENU);
						if (menu != null) {
							if (menu.booleanValue()) {
								flags |= FLAG_NEEDS_MENU_KEY;
							}
							param.args[0] = flags;
						}
					}
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}

		if (Build.VERSION.SDK_INT >= 19) {
			try {
				findAndHookMethod("android.view.ViewRootImpl", null, "dispatchSystemUiVisibilityChanged",
						int.class, int.class, int.class, int.class, new XC_MethodHook() {
					@TargetApi(19)
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
						// Has the navigation bar been shown?
						int localChanges = (Integer) param.args[3];
						if ((localChanges & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0)
							return;

						// Should it be hidden?
						View decorView = (View) getObjectField(param.thisObject, "mView");
						Boolean immersive = (decorView == null)
								? null
								: (Boolean) getAdditionalInstanceField(decorView, PROP_IMMERSIVE);
						if (immersive == null || !immersive.booleanValue())
							return;

						// Enforce SYSTEM_UI_FLAG_HIDE_NAVIGATION and hide changes to this flag
						int globalVisibility = (Integer) param.args[1];
						int localValue = (Integer) param.args[2];
						param.args[1] = globalVisibility | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
						param.args[2] = localValue | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
						param.args[3] = localChanges & ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
					}
				});
			} catch (Throwable e) {
				XposedBridge.log(e);
			}
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
					if (XposedMod.isActive(pkgName, Common.PREF_RESIDENT)) {
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
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}

		try {
			hookAllConstructors(findClass("com.android.server.am.ActivityRecord", null), new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) throws Throwable {
					ActivityInfo aInfo = (ActivityInfo) getObjectField(param.thisObject, "info");
					if (aInfo == null)
						return;
					String pkgName = aInfo.packageName;
					if (XposedMod.prefs.getInt(pkgName + Common.PREF_RECENTS_MODE, Common.PREF_RECENTS_DEFAULT) > 0) {
						int recentsMode = XposedMod.prefs.getInt(pkgName + Common.PREF_RECENTS_MODE, Common.PREF_RECENTS_DEFAULT);
						if (recentsMode == Common.PREF_RECENTS_DEFAULT)
							return;
						Intent intent = (Intent) getObjectField(param.thisObject, "intent");
						if (recentsMode == Common.PREF_RECENTS_FORCE) {
							int flags = (intent.getFlags() & ~Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
							intent.setFlags(flags);
						}
						else if (recentsMode == Common.PREF_RECENTS_PREVENT)
							intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
					}
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}

		try {
			findAndHookMethod(InputMethodService.class, "doStartInput",
					InputConnection.class, EditorInfo.class, boolean.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					EditorInfo info = (EditorInfo) param.args[1];
					if (info != null && info.packageName != null) {
						XposedMod.prefs.reload();
						if (XposedMod.isActive(info.packageName, Common.PREF_NO_FULLSCREEN_IME))
							info.imeOptions |= EditorInfo.IME_FLAG_NO_FULLSCREEN;
					}
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}
	}
}
