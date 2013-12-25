package de.robv.android.xposed.mods.appsettings.hooks;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.mods.appsettings.Common;
import de.robv.android.xposed.mods.appsettings.XposedMod;
import de.robv.android.xposed.mods.appsettings.settings.ApplicationSettings;

public class PackagePermissions extends BroadcastReceiver {
	private final Object pmSvc;
	private final Map<String, Object> mPackages;
	private final Object mSettings;

	@SuppressWarnings("unchecked")
	public PackagePermissions(Object pmSvc) {
		this.pmSvc = pmSvc;
		this.mPackages = (Map<String, Object>) getObjectField(pmSvc, "mPackages");
		this.mSettings = getObjectField(pmSvc, "mSettings");
	}

	public static void initHooks() {
		/* Hook to the PackageManager service in order to
		 * - Listen for broadcasts to apply new settings and restart the app
		 * - Intercept the permission granting function to remove disabled permissions
		 */
		try {
			final Class<?> clsPMS;
			if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1)
				clsPMS = findClass("com.android.server.pm.PackageManagerService", XposedMod.class.getClassLoader());
			else
				clsPMS = findClass("com.android.server.PackageManagerService", XposedMod.class.getClassLoader());

			// Listen for broadcasts from the Settings part of the mod, so it's applied immediately
			findAndHookMethod(clsPMS, "systemReady", new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param)
						throws Throwable {
					Context mContext = (Context) getObjectField(param.thisObject, "mContext");
					mContext.registerReceiver(new PackagePermissions(param.thisObject),
							new IntentFilter(Common.MY_PACKAGE_NAME + ".UPDATE_PERMISSIONS"),
							Common.MY_PACKAGE_NAME + ".BROADCAST_PERMISSION",
							null);
				}
			});

			// if the user has disabled certain permissions for an app, do as if the hadn't requested them 
			String methodName;
			if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
				methodName = "grantPermissionsLPw";
			} else {
				methodName = "grantPermissionsLP";
			}
			findAndHookMethod(clsPMS, methodName, "android.content.pm.PackageParser$Package", boolean.class,
					new XC_MethodHook() {
				@SuppressWarnings("unchecked")
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					String pkgName = (String) getObjectField(param.args[0], "packageName");
					if (!XposedMod.isActive(pkgName) || !XposedMod.prefs.getBoolean(pkgName + Common.PREF_REVOKEPERMS, false))
						return;

					Set<String> disabledPermissions = ApplicationSettings.getStringSet(XposedMod.prefs, pkgName + Common.PREF_REVOKELIST, null);
					if (disabledPermissions == null || disabledPermissions.isEmpty())
						return;

					ArrayList<String> origRequestedPermissions = (ArrayList<String>) getObjectField(param.args[0], "requestedPermissions");
					param.setObjectExtra("orig_requested_permissions", origRequestedPermissions);

					ArrayList<String> newRequestedPermissions = new ArrayList<String>(origRequestedPermissions.size());
					for (String perm: origRequestedPermissions) {
						if (!disabledPermissions.contains(perm))
							newRequestedPermissions.add(perm);
						else
							// you requested those internet permissions? I didn't read that, sorry
							Log.w(Common.TAG, "Not granting permission " + perm
	                                + " to package " + pkgName
	                                + " because you think it should not have it");
					}

					setObjectField(param.args[0], "requestedPermissions", newRequestedPermissions);
				}

				@SuppressWarnings("unchecked")
				@Override
				protected void afterHookedMethod(MethodHookParam param) throws Throwable {
					// restore requested permissions if they were modified
					ArrayList<String> origRequestedPermissions = (ArrayList<String>) param.getObjectExtra("orig_requested_permissions");
					if (origRequestedPermissions != null)
						setObjectField(param.args[0], "requestedPermissions", origRequestedPermissions);
				}
			});
		} catch (Throwable e) {
			XposedBridge.log(e);
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		try {
			// The app broadcasted a request to update settings for a running app

			// Validate the action being requested
			if (!Common.ACTION_PERMISSIONS.equals(intent.getExtras().getString("action")))
				return;

			String pkgName = intent.getExtras().getString("Package");
			boolean killApp = intent.getExtras().getBoolean("Kill", false);

			XposedMod.prefs.reload();

			Object pkgInfo;
			synchronized (mPackages) {
				pkgInfo = mPackages.get(pkgName);
				if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
					callMethod(pmSvc, "grantPermissionsLPw", pkgInfo, true);
					callMethod(mSettings, "writeLPr");
				} else {
					callMethod(pmSvc, "grantPermissionsLP", pkgInfo, true);
					callMethod(mSettings, "writeLP");
				}
			}

			// Apply new permissions if needed
			if (killApp) {
				try {
					ApplicationInfo appInfo = (ApplicationInfo) getObjectField(pkgInfo, "applicationInfo");
					if (Build.VERSION.SDK_INT <= 18)
						callMethod(pmSvc, "killApplication", pkgName, appInfo.uid);
					else
						callMethod(pmSvc, "killApplication", pkgName, appInfo.uid, "apply App Settings");
				} catch (Throwable t) {
					XposedBridge.log(t);
				}
			}
		} catch (Throwable t) {
			XposedBridge.log(t);
		}
	}
}
