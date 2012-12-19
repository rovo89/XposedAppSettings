package de.robv.android.xposed.mods.appsettings.hooks;

import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodBestMatch;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.AndroidAppHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.mods.appsettings.XposedMod;



public class PackagePermissions extends BroadcastReceiver {

	public static final String REVOKED_PREFIX = "stericson.disabled.";

    private Object pmSvc;
    
    public PackagePermissions(Object pmSvc) {
        this.pmSvc = pmSvc;
    }

    
    @Override
    public void onReceive(Context context, Intent intent) {

        try {
            // The app broadcasted a request to update settings for a running app
            
            // Validate the action being requested
        	if (!XposedMod.ACTION_PERMISSIONS.equals(intent.getExtras().getString("action")))
        		return;
            
            String pkgName = intent.getExtras().getString("Package");
            boolean killApp = intent.getExtras().getBoolean("Kill", false);
            
            // Update the PM's configuration for this package
            revokePermissions(pmSvc, pkgName, killApp, true);
            
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
    
    
    @SuppressWarnings("unchecked")
    public static void revokePermissions(Object pmSvc, String pkgName, boolean killApp, boolean force) throws Throwable {
    	// Reload preferences if they were updated before the broadcast
    	if (force)
    		XposedMod.prefs = AndroidAppHelper.getSharedPreferencesForPackage(XposedMod.MY_PACKAGE_NAME, XposedMod.PREFS, Context.MODE_PRIVATE);

        Map<String, Object> mPackages = (Map<String, Object>) getObjectField(pmSvc, "mPackages");
        synchronized (mPackages) {
            Object pkgInfo = mPackages.get(pkgName);
            
            // Apply new permissions if needed
            if (XposedMod.prefs.getBoolean(pkgName + XposedMod.PREF_ACTIVE, false) || force)
            	doRevokePermissions(pmSvc, pkgName, mPackages, pkgInfo);
            
            if (killApp) {
                try {
                    ApplicationInfo appInfo = (ApplicationInfo) getObjectField(pkgInfo, "applicationInfo");
                    findMethodExact(pmSvc.getClass(), "killApplication", String.class, int.class).invoke(
                            pmSvc, pkgName, appInfo.uid);
                } catch (Throwable t) {
                	XposedBridge.log(t);
                }
            }
        }
    }


    @SuppressWarnings("unchecked")
	private static void doRevokePermissions(Object pmSvc, String pkgName, Map<String, Object> mPackages1, Object pkgInfo)
			throws IllegalAccessException, InvocationTargetException {
	    Object mSettings = getObjectField(pmSvc, "mSettings");
	    Map<String, Object> mPackages2 = (Map<String, Object>) getObjectField(mSettings, "mPackages");
	    Object pkgSettings = mPackages2.get(pkgName);

	    Set<String> grantedPermissions = (Set<String>) getObjectField(pkgSettings, "grantedPermissions");
	    
	    Set<String> disabledPermissions;
	    if (XposedMod.prefs.getBoolean(pkgName + XposedMod.PREF_REVOKEPERMS, false))
	    	disabledPermissions = XposedMod.prefs.getStringSet(pkgName + XposedMod.PREF_REVOKELIST, new HashSet<String>());
	    else
	    	disabledPermissions = new HashSet<String>();
	    
	    setDisabledPermissions(grantedPermissions, disabledPermissions);

	    // Save updated information to the packages.xml file
	    try {
	        findMethodExact(mSettings.getClass(), "writeLPr").invoke(mSettings);
	    } catch (Throwable t) {
	    	XposedBridge.log(t);
	    }
	    

	    Set<String> grantedPermissions1 = (Set<String>) getObjectField(
	    		getObjectField(mPackages1.get(pkgName), "mExtras"), "grantedPermissions");
	    setDisabledPermissions(grantedPermissions1, disabledPermissions);
	    
	    List<String> requestedPermissions;
	    requestedPermissions = (List<String>) getObjectField(mPackages1.get(pkgName), "requestedPermissions");
	    Set<String> requestedPermissionsSet = new HashSet<String>(requestedPermissions);
	    setDisabledPermissions(requestedPermissionsSet, disabledPermissions);
	    requestedPermissions.clear();
	    requestedPermissions.addAll(requestedPermissionsSet);

	    
	    // Also update the permissions associated with the user, in case they're not the same object as the package's
	    Integer userId = null;
	    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
	        // ICS
	        userId = (Integer) getObjectField(pkgSettings, "userId");
	    } else {
	    	// TODO Check if this works properly on JB
	    	userId = (Integer) getObjectField(pkgSettings, "appId");
	        Class<?> clsUserId = findClass("android.os.UserId", pmSvc.getClass().getClassLoader());
	        int userId2 = (Integer) findMethodExact(clsUserId, "getUserId", int.class).invoke(clsUserId, userId);
	        if (userId2 != 0) {
	        	userId = userId2;
	        }
	    }
	    
	    Object userPerms = findMethodExact("com.android.server.pm.Settings", pmSvc.getClass().getClassLoader(),
	    		"getUserIdLPr", int.class).invoke(mSettings, userId);
	    Set<String> uidPermissions = (Set<String>) getObjectField(userPerms, "grantedPermissions");
	    setDisabledPermissions(uidPermissions, disabledPermissions);
	    
	    try {
	        findMethodBestMatch(pmSvc.getClass(), "grantPermissionsLPw", pkgInfo.getClass(), boolean.class).invoke(
	             pmSvc, pkgInfo, true);
	    } catch (Throwable t) {
	    	XposedBridge.log(t);
	    }
    }

    
    private static void setDisabledPermissions(Set<String> permissions, Set<String> disabled) {
        Set<String> reEnablePerms = new HashSet<String>();
        for (String perm : permissions) {
            if (perm.startsWith(REVOKED_PREFIX)) {
                reEnablePerms.add(perm.substring(REVOKED_PREFIX.length()));
            }
        }
        for (String perm : reEnablePerms) {
            permissions.add(perm);
            permissions.remove(REVOKED_PREFIX + perm);
        }
        for (String perm : disabled) {
            if (permissions.contains(perm)) {
                permissions.remove(perm);
            } else {
            	// Adding disabled permission not previously on the list !
            }
            permissions.add(REVOKED_PREFIX + perm);
        }
    }

}
