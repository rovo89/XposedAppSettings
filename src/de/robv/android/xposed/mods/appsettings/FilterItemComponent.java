package de.robv.android.xposed.mods.appsettings;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

/**
 * Composite component that displays a header and a triplet of radio buttons for
 * selection of All / Overridden / Unchanged settings for each parameter
 */
public class FilterItemComponent extends LinearLayout {

	private OnFilterChangeListener listener;

	/** Constructor for designer instantiation */
	public FilterItemComponent(Context context, AttributeSet attrs) {
		super(context, attrs);

		LayoutInflater.from(context).inflate(R.layout.filter_item, this);

		TypedArray atts = context.obtainStyledAttributes(attrs, R.styleable.FilterItem);

		// Load label values, if any
		setLabel(R.id.txtFilterName, atts.getString(R.styleable.FilterItem_label));
		setLabel(R.id.radAll, atts.getString(R.styleable.FilterItem_all_label));
		setLabel(R.id.radOverridden, atts.getString(R.styleable.FilterItem_overridden_label));
		setLabel(R.id.radUnchanged, atts.getString(R.styleable.FilterItem_unchanged_label));
		atts.recycle();

		setupListener();
	}

	/** Constructor for programmatic instantiation */
	public FilterItemComponent(Context context, String filterName, String labelAll, String labelOverriden, String labelUnchanged) {
		super(context);

		LayoutInflater.from(context).inflate(R.layout.filter_item, this);

		// Load label values, if any
		setLabel(R.id.txtFilterName, filterName);
		setLabel(R.id.radAll, labelAll);
		setLabel(R.id.radOverridden, labelOverriden);
		setLabel(R.id.radUnchanged, labelUnchanged);

		setupListener();
	}

	private void setupListener() {
	    // Notify any listener of changes in the selected option
		((RadioGroup) findViewById(R.id.radOptions)).setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if (listener != null) {
					switch (checkedId) {
					case R.id.radOverridden:
						listener.onFilterChanged(FilterItemComponent.this, FilterState.OVERRIDDEN);
						break;
					case R.id.radUnchanged:
						listener.onFilterChanged(FilterItemComponent.this, FilterState.UNCHANGED);
						break;
					default:
						listener.onFilterChanged(FilterItemComponent.this, FilterState.ALL);
						break;
					}
				}
			}
		});
    }

	/*
	 * Update the label of a view id, if non-null
	 */
	private void setLabel(int id, CharSequence value) {
		TextView label = (TextView) findViewById(id);
		if (label != null && value != null) {
			label.setText(value);
		}
	}

	/**
	 * Enable or disable all the items within this compound component
	 */
	@Override
	public void setEnabled(boolean enabled) {
		findViewById(R.id.radOptions).setEnabled(enabled);
		findViewById(R.id.radAll).setEnabled(enabled);
		findViewById(R.id.radOverridden).setEnabled(enabled);
		findViewById(R.id.radUnchanged).setEnabled(enabled);
	}

	/**
	 * Check if this compound component is enabled
	 */
	@Override
	public boolean isEnabled() {
		return findViewById(R.id.radOptions).isEnabled();
	}

	/**
	 * Get currently selected filter option
	 */
	public FilterState getFilterState() {
		switch (((RadioGroup) findViewById(R.id.radOptions)).getCheckedRadioButtonId()) {
		case R.id.radOverridden:
			return FilterState.OVERRIDDEN;
		case R.id.radUnchanged:
			return FilterState.UNCHANGED;
		default:
			return FilterState.ALL;
		}
	}

	/**
	 * Activate one of the 3 options as the selected one
	 */
	public void setFilterState(FilterState state) {
		// Handle null values and use the default "All"
		if (state == null)
			state = FilterState.ALL;

		switch (state) {
		case OVERRIDDEN:
			((RadioGroup) findViewById(R.id.radOptions)).check(R.id.radOverridden);
			break;
		case UNCHANGED:
			((RadioGroup) findViewById(R.id.radOptions)).check(R.id.radUnchanged);
			break;
		default:
			((RadioGroup) findViewById(R.id.radOptions)).check(R.id.radAll);
			break;
		}
	}

	/**
	 * Register a listener to be notified when the selection changes
	 */
	public void setOnFilterChangeListener(OnFilterChangeListener listener) {
		this.listener = listener;
	}

	/**
	 * Interface for listeners that will be notified of selection changes
	 */
	public static interface OnFilterChangeListener {
		/**
		 * Notification that this filter item has changed to a new selected state
		 */
		public void onFilterChanged(FilterItemComponent item, FilterState state);
	}

	/**
	 * Possible values for the filter state: All / Overridden / Unchanged
	 */
	public static enum FilterState {
		ALL, OVERRIDDEN, UNCHANGED;
	}
}
