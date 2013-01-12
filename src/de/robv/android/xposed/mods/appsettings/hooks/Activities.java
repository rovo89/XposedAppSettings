package de.robv.android.xposed.mods.appsettings.hooks;

import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.removeAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setIntField;

import java.lang.reflect.Method;

import android.content.Context;
import android.os.Bundle;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.mods.appsettings.Common;
import de.robv.android.xposed.mods.appsettings.XposedMod;


public class Activities {

	public static void hookActivitySettings(LoadPackageParam lpparam) {
	    try {
	    	// Hook one of the several variations of ActivityStack.startActivityLocked from different ROMs
	    	Method mthStartActivityLocked;
	    	try {
	        	mthStartActivityLocked = findMethodExact("com.android.server.am.ActivityStack", lpparam.classLoader, "startActivityLocked",
	    				"com.android.server.am.ActivityRecord", boolean.class, boolean.class, boolean.class);
	    	} catch (Throwable t) {
	    		try {
	        		mthStartActivityLocked = findMethodExact("com.android.server.am.ActivityStack", lpparam.classLoader, "startActivityLocked",
	        				"com.android.server.am.ActivityRecord", boolean.class, boolean.class, boolean.class, Bundle.class);
	    		} catch (Throwable t1) {
	        		mthStartActivityLocked = findMethodExact("com.android.server.am.ActivityStack", lpparam.classLoader, "startActivityLocked",
	        				"com.android.server.am.ActivityRecord", boolean.class, boolean.class);
	    		}
	    	}
	    	hookMethod(mthStartActivityLocked, new XC_MethodHook() {

	    		@Override
	    		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
	    			if (XposedMod.prefs == null)
	    				return;

	    			String pkgName = (String) getObjectField(param.args[0], "packageName");
	    			
	    			// Restore any stored original values if no longer active, for fullscreen theme
	    			if (!XposedMod.prefs.getBoolean(pkgName + Common.PREF_ACTIVE, false)
	    					|| !XposedMod.prefs.getBoolean(pkgName + Common.PREF_FULLSCREEN, false)) {
	    				Integer oriTheme = (Integer) getAdditionalInstanceField(param.args[0], "OriginalTheme");
	    				if (oriTheme != null) {
	    					setIntField(param.args[0], "theme", oriTheme);
	    					removeAdditionalInstanceField(param.args[0], "OriginalTheme");
	    				}
	    				
	    				Object aInfo = getObjectField(param.args[0], "info");
	    				oriTheme = (Integer) getAdditionalInstanceField(aInfo, "OriginalTheme");
	    				if (oriTheme != null) {
	    					setIntField(aInfo, "theme", oriTheme);
	    					removeAdditionalInstanceField(aInfo, "OriginalTheme");
	    				}
	    				
	    				Object appInfo = getObjectField(aInfo, "applicationInfo");
	    				oriTheme = (Integer) getAdditionalInstanceField(appInfo, "OriginalTheme");
	    				if (oriTheme != null) {
	    					setIntField(appInfo, "theme", oriTheme);
	    					removeAdditionalInstanceField(appInfo, "OriginalTheme");
	    				}
	    			}
	    			
	    			// The same for the orientation
	    			if (!XposedMod.prefs.getBoolean(pkgName + Common.PREF_ACTIVE, false)
	    					|| XposedMod.prefs.getInt(pkgName + Common.PREF_ORIENTATION, XposedMod.prefs.getInt(Common.PREF_DEFAULT + Common.PREF_ORIENTATION, 0)) <= 0) {
	    				Object aInfo = getObjectField(param.args[0], "info");
	    				Integer oriOrientation = (Integer) getAdditionalInstanceField(aInfo, "OriginalOrientation");
	    				if (oriOrientation != null) {
	    					setIntField(aInfo, "screenOrientation", oriOrientation);
	    					removeAdditionalInstanceField(aInfo, "OriginalOrientation");
	    				}
	    			}
	    			
	    			
	    			if (XposedMod.prefs.getBoolean(pkgName + Common.PREF_ACTIVE, false)) {
	    				// Change the theme is meant to be shown fullscreen
	    				if (XposedMod.prefs.getBoolean(pkgName + Common.PREF_FULLSCREEN, false)) {
	    					Context context = (Context) getObjectField(param.thisObject, "mContext");
	    					int resId = context.getResources().getIdentifier("@android:style/Theme.NoTitleBar.Fullscreen", "style", "android");

	    					// Store the original values before replacing, on the first replacement
	    					if (getAdditionalInstanceField(param.args[0], "OriginalTheme") == null)
	    						setAdditionalInstanceField(param.args[0], "OriginalTheme", getIntField(param.args[0], "theme"));
	    					setIntField(param.args[0], "theme", resId);
	    					
	    					Object aInfo = getObjectField(param.args[0], "info");
	    					if (getAdditionalInstanceField(aInfo, "OriginalTheme") == null)
	    						setAdditionalInstanceField(aInfo, "OriginalTheme", getIntField(aInfo, "theme"));
	    					setIntField(aInfo, "theme", resId);
	    					
	    					Object appInfo = getObjectField(aInfo, "applicationInfo");
	    					if (getAdditionalInstanceField(appInfo, "OriginalTheme") == null)
	    						setAdditionalInstanceField(appInfo, "OriginalTheme", getIntField(appInfo, "theme"));
	    					setIntField(appInfo, "theme", resId);
	    				}

	    				int orientation = XposedMod.prefs.getInt(pkgName + Common.PREF_ORIENTATION, XposedMod.prefs.getInt(Common.PREF_DEFAULT + Common.PREF_ORIENTATION, 0));
	    				if (orientation < 0 || orientation >= Common.orientationCodes.length)
	    					orientation = 0;
	    				// Change the orientation behavior if it's being overridden
	    				if (orientation > 0) {
	    					// Store the original values before replacing, on the first replacement
	    					Object aInfo = getObjectField(param.args[0], "info");
	    					if (getAdditionalInstanceField(aInfo, "OriginalOrientation") == null)
	    						setAdditionalInstanceField(aInfo, "OriginalOrientation", getIntField(aInfo, "screenOrientation"));
	    					setIntField(aInfo, "screenOrientation", Common.orientationCodes[orientation]);
	    				}
	    			}
	    		};
	    	});
	    } catch (Throwable e) {
	        XposedBridge.log(e);
	    }
	    
	    try {
	    	// Hook one of the several variations of ActivityStack.realStartActivityLocked from different ROMs
	    	Method mthRealStartActivityLocked;
	    	try {
	        	mthRealStartActivityLocked = findMethodExact("com.android.server.am.ActivityStack", lpparam.classLoader, "realStartActivityLocked",
	    				"com.android.server.am.ActivityRecord", "com.android.server.am.ProcessRecord",
	    				boolean.class, boolean.class, boolean.class);
	    	} catch (Throwable t) {
	    		mthRealStartActivityLocked = findMethodExact("com.android.server.am.ActivityStack", lpparam.classLoader, "realStartActivityLocked",
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
