package de.robv.android.xposed.mods.appsettings.settings;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import de.robv.android.xposed.mods.appsettings.R;


/**
 * Manages a popup dialog for editing the Permission Revoking settings for a package
 */
public class PermissionSettings {
	private Dialog dialog;

	private OnDismissListener onOkListener;
	private OnDismissListener onCancelListener;

	boolean revokeActive;
	private Set<String> disabledPerms;

	private List<PermissionInfo> permsList = new LinkedList<PermissionInfo>();

	/**
	 * Prepare a dialog for editing the permissions for the supplied package,
	 * with the provided owner activity and initial settings
	 */
	public PermissionSettings(Activity owner, String pkgName, boolean revoking, Set<String> disabledPermissions) {
		dialog = new Dialog(owner);
		dialog.setContentView(R.layout.permissions_dialog);
		dialog.setTitle("Permissions");
		dialog.setCancelable(true);
		dialog.setOwnerActivity(owner);

		revokeActive = revoking;
		if (disabledPermissions != null)
			disabledPerms = new HashSet<String>(disabledPermissions);
		else
			disabledPerms = new HashSet<String>();

		Switch swtRevoke = (Switch) dialog.findViewById(R.id.swtRevokePerms);
		swtRevoke.setChecked(revokeActive);

		// Track changes to the Revoke checkbox to lock or unlock the list of
		// permissions
		swtRevoke.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				revokeActive = isChecked;
				dialog.findViewById(R.id.lstPermissions).setBackgroundColor(revokeActive ? Color.BLACK : Color.DKGRAY);
			}
		});
		dialog.findViewById(R.id.lstPermissions).setBackgroundColor(revokeActive ? Color.BLACK : Color.DKGRAY);

		// Load the list of permissions for the package and present them
		try {
			loadPermissionsList(pkgName);
		} catch (NameNotFoundException e) {
			throw new RuntimeException("Invalid package permissions: " + pkgName, e);
		}

		final PermsListAdaptor appListAdaptor = new PermsListAdaptor(owner, permsList);
		((ListView) dialog.findViewById(R.id.lstPermissions)).setAdapter(appListAdaptor);

		((Button) dialog.findViewById(R.id.btnPermsCancel)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (onCancelListener != null)
					onCancelListener.onDismiss(PermissionSettings.this);
				dialog.dismiss();
			}
		});
		((Button) dialog.findViewById(R.id.btnPermsOk)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (onOkListener != null)
					onOkListener.onDismiss(PermissionSettings.this);
				dialog.dismiss();
			}
		});
	}

	/**
	 * Display the editor dialog
	 */
	public void display() {
		dialog.show();
	}

	/**
	 * Register a listener to be invoked when the editor is dismissed with the
	 * Ok button
	 */
	public void setOnOkListener(OnDismissListener listener) {
		onOkListener = listener;
	}

	/**
	 * Register a listener to be invoked when the editor is dismissed with the
	 * Cancel button
	 */
	public void setOnCancelListener(OnDismissListener listener) {
		onCancelListener = listener;
	}

	/**
	 * Get the state of the Active switch
	 */
	public boolean getRevokeActive() {
		return revokeActive;
	}

	/**
	 * Get the list of permissions in the disabled state
	 */
	public Set<String> getDisabledPermissions() {
		return new HashSet<String>(disabledPerms);
	}

	/*
	 * Adapter to feed the list of permission entries
	 */
	private class PermsListAdaptor extends ArrayAdapter<PermissionInfo> {

		public PermsListAdaptor(Context context, List<PermissionInfo> items) {

			super(context, R.layout.app_list_item, items);
		}

		private class ViewHolder {
			TextView tvName;
			TextView tvDescription;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			ViewHolder vHolder;
			if (row == null) {
				row = dialog.getLayoutInflater().inflate(R.layout.app_permission_item, parent, false);
				vHolder = new ViewHolder();
				vHolder.tvName = (TextView) row.findViewById(R.id.perm_name);
				vHolder.tvDescription = (TextView) row.findViewById(R.id.perm_description);
				row.setTag(vHolder);
			} else {
				vHolder = (ViewHolder) row.getTag();
			}

			PermissionInfo perm = permsList.get(position);
			PackageManager pm = dialog.getContext().getPackageManager();

			CharSequence label = perm.loadLabel(pm);
			if (!label.equals(perm.name)) {
				label = perm.name + " (" + label + ")";
			}

			vHolder.tvName.setText(label);
			CharSequence description = perm.loadDescription(pm);
			description = (description == null) ? "" : description.toString().trim();
			if (description.length() == 0)
				description = "( no description provided )";
			vHolder.tvDescription.setText(description);
			switch (perm.protectionLevel) {
			case PermissionInfo.PROTECTION_DANGEROUS:
				vHolder.tvDescription.setTextColor(Color.RED);
				break;
			case PermissionInfo.PROTECTION_SIGNATURE:
				vHolder.tvDescription.setTextColor(Color.GREEN);
				break;
			case PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM:
				vHolder.tvDescription.setTextColor(Color.YELLOW);
				break;
			default:
				vHolder.tvDescription.setTextColor(Color.parseColor("#0099CC"));
				break;
			}

			vHolder.tvName.setTag(perm.name);
			if (disabledPerms.contains(perm.name)) {
				vHolder.tvName.setPaintFlags(vHolder.tvName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
				vHolder.tvName.setTextColor(Color.MAGENTA);
			} else {
				vHolder.tvName.setPaintFlags(vHolder.tvName.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
				vHolder.tvName.setTextColor(Color.WHITE);
			}
			row.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (!revokeActive)
						return;

					TextView tv = (TextView) v.findViewById(R.id.perm_name);
					if ((tv.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) != 0) {
						disabledPerms.remove(tv.getTag());
						tv.setPaintFlags(tv.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
						tv.setTextColor(Color.WHITE);
					} else {
						disabledPerms.add((String) tv.getTag());
						tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
						tv.setTextColor(Color.MAGENTA);
					}
				}
			});

			return row;
		}

	}

	/*
	 * Populate the list of permissions requested by this package
	 */
	@SuppressLint("DefaultLocale")
	private void loadPermissionsList(String pkgName) throws NameNotFoundException {
		permsList.clear();

		PackageManager pm = dialog.getContext().getPackageManager();
		PackageInfo pkgInfo = pm.getPackageInfo(pkgName, PackageManager.GET_PERMISSIONS);
		if (pkgInfo.sharedUserId != null) {
			Switch swtRevoke = (Switch) dialog.findViewById(R.id.swtRevokePerms);
			swtRevoke.setEnabled(false);
			swtRevoke.setChecked(false);
			swtRevoke.setText("Shared packages not yet supported");
			swtRevoke.setTextColor(Color.RED);
		}
		String[] permissions = pkgInfo.requestedPermissions;
		if (permissions == null) {
			permissions = new String[0];
		}
		for (String perm : permissions) {
			try {
				permsList.add(pm.getPermissionInfo(perm, 0));
			} catch (NameNotFoundException e) {
				PermissionInfo unknownPerm = new PermissionInfo();
				unknownPerm.name = perm;
				permsList.add(unknownPerm);
			}
		}

		Collections.sort(permsList, new Comparator<PermissionInfo>() {
			@Override
			public int compare(PermissionInfo lhs, PermissionInfo rhs) {
				if (lhs.name == null) {
					return -1;
				} else if (rhs.name == null) {
					return 1;
				} else {
					return lhs.name.toUpperCase().compareTo(rhs.name.toUpperCase());
				}
			}
		});
	}

	/**
	 * Interface for the listeners of Ok/Cancel dismiss actions
	 */
	public static interface OnDismissListener {

		public abstract void onDismiss(PermissionSettings obj);
	}

}
