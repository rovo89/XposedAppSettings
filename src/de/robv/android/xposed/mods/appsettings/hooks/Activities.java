package de.robv.android.xposed.mods.appsettings.hooks;

import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setIntField;

import java.lang.reflect.Method;

import android.app.Activity;
import android.content.Context;
import android.view.Window;
import android.view.WindowManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.mods.appsettings.Common;
import de.robv.android.xposed.mods.appsettings.XposedMod;


public class Activities {

	public static void hookActivitySettings() {
		try {
			findAndHookMethod("com.android.internal.policy.impl.PhoneWindow", null, "generateLayout",
					"com.android.internal.policy.impl.PhoneWindow.DecorView", new XC_MethodHook() {
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					Window window = (Window) param.thisObject;
					Context context = window.getContext();
					String packageName = context.getPackageName();

					if (!XposedMod.isActive(packageName))
						return;

					if (XposedMod.prefs.getBoolean(packageName + Common.PREF_FULLSCREEN, false))
						window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

					// TODO: This should be a separate setting
					if (XposedMod.prefs.getBoolean(packageName + Common.PREF_FULLSCREEN, false))
						window.requestFeature(Window.FEATURE_NO_TITLE);

					int orientation = XposedMod.prefs.getInt(packageName + Common.PREF_ORIENTATION, XposedMod.prefs.getInt(Common.PREF_DEFAULT + Common.PREF_ORIENTATION, 0));
					if (orientation > 0 && orientation < Common.orientationCodes.length && context instanceof Activity)
						((Activity) context).setRequestedOrientation(Common.orientationCodes[orientation]);
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}
		
	    try {
	    	// Hook one of the several variations of ActivityStack.realStartActivityLocked from different ROMs
	    	Method mthRealStartActivityLocked;
	    	try {
	        	mthRealStartActivityLocked = findMethodExact("com.android.server.am.ActivityStack", null, "realStartActivityLocked",
	    				"com.android.server.am.ActivityRecord", "com.android.server.am.ProcessRecord",
	    				boolean.class, boolean.class, boolean.class);
	    	} catch (Throwable t) {
	    		mthRealStartActivityLocked = findMethodExact("com.android.server.am.ActivityStack", null, "realStartActivityLocked",
	    				"com.android.server.am.ActivityRecord", "com.android.server.am.ProcessRecord",
	    				boolean.class, boolean.class);
	    	}
	    	hookMethod(mthRealStartActivityLocked, new XC_MethodHook() {

	    		@Override
	    		protected void afterHookedMethod(MethodHookParam param) throws Throwable {
	    			if (XposedMod.prefs == null)
	    				return;

	    			String pkgName = (String) getObjectField(param.args[0], "packageName");
	    			if (XposedMod.prefs.getBoolean(pkgName + Common.PREF_ACTIVE, false)) {
	    				if (XposedMod.prefs.getBoolean(pkgName + Common.PREF_RESIDENT, false)) {
	    					int adj = -12;
	    					Object proc = getObjectField(param.args[0], "app");
	    					
	    					// Override the *Adj values if meant to be resident in memory
	    					if (proc != null) {
	    						setIntField(proc, "maxAdj", adj);
	    						setIntField(proc, "hiddenAdj", adj);
	    						setIntField(proc, "curRawAdj", adj);
	    						setIntField(proc, "setRawAdj", adj);
	    						setIntField(proc, "curAdj", adj);
	    						setIntField(proc, "setAdj", adj);
	    					}
	    				}
	    			}
	    		};
	    	});
	    } catch (Throwable e) {
	        XposedBridge.log(e);
	    }
    }
}
