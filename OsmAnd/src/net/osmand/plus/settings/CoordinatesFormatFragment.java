package net.osmand.plus.settings;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.PreferenceViewHolder;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import net.osmand.Location;
import net.osmand.data.PointDescription;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.R;
import net.osmand.plus.settings.bottomsheets.ChangeGeneralProfilesPrefBottomSheet;
import net.osmand.plus.wikipedia.WikipediaDialogFragment;


public class CoordinatesFormatFragment extends BaseSettingsFragment {

	public static final String TAG = CoordinatesFormatFragment.class.getSimpleName();

	private static final String UTM_FORMAT_WIKI_LINK = "https://en.wikipedia.org/wiki/Universal_Transverse_Mercator_coordinate_system";

	private static final String FORMAT_DEGREES = "format_degrees";
	private static final String FORMAT_MINUTES = "format_minutes";
	private static final String FORMAT_SECONDS = "format_seconds";
	private static final String UTM_FORMAT = "utm_format";
	private static final String OLC_FORMAT = "olc_format";

	@Override
	protected String getFragmentTag() {
		return TAG;
	}

	@Override
	protected int getPreferencesResId() {
		return R.xml.coordinates_format;
	}

	@Override
	protected int getToolbarResId() {
		return R.layout.profile_preference_toolbar;
	}

	@Override
	protected int getToolbarTitle() {
		return R.string.coordinates_format;
	}

	@Override
	public int getStatusBarColorId() {
		boolean nightMode = isNightMode();
		View view = getView();
		if (view != null && Build.VERSION.SDK_INT >= 23 && !nightMode) {
			view.setSystemUiVisibility(view.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
		}
		return nightMode ? R.color.list_background_color_dark : R.color.list_background_color_light;
	}

	@Override
	protected void setupPreferences() {
		Preference generalSettings = findPreference("coordinates_format_info");
		generalSettings.setIcon(getContentIcon(R.drawable.ic_action_info_dark));

		CheckBoxPreference degreesPref = (CheckBoxPreference) findPreference(FORMAT_DEGREES);
		CheckBoxPreference minutesPref = (CheckBoxPreference) findPreference(FORMAT_MINUTES);
		CheckBoxPreference secondsPref = (CheckBoxPreference) findPreference(FORMAT_SECONDS);
		CheckBoxPreference utmPref = (CheckBoxPreference) findPreference(UTM_FORMAT);
		CheckBoxPreference olcPref = (CheckBoxPreference) findPreference(OLC_FORMAT);

		Location loc = app.getLocationProvider().getLastKnownLocation();

		degreesPref.setSummary(getCoordinatesFormatSummary(loc, PointDescription.FORMAT_DEGREES));
		minutesPref.setSummary(getCoordinatesFormatSummary(loc, PointDescription.FORMAT_MINUTES));
		secondsPref.setSummary(getCoordinatesFormatSummary(loc, PointDescription.FORMAT_SECONDS));
		utmPref.setSummary(getCoordinatesFormatSummary(loc, PointDescription.UTM_FORMAT));
		olcPref.setSummary(getCoordinatesFormatSummary(loc, PointDescription.OLC_FORMAT));

		int currentFormat = settings.COORDINATES_FORMAT.get();
		String currentPrefKey = getCoordinatesKeyForFormat(currentFormat);
		updateSelectedFormatPrefs(currentPrefKey);
	}

	@Override
	protected void onBindPreferenceViewHolder(Preference preference, PreferenceViewHolder holder) {
		super.onBindPreferenceViewHolder(preference, holder);

		if (UTM_FORMAT.equals(preference.getKey())) {
			TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
			if (summaryView != null) {
				summaryView.setOnTouchListener(getSummaryTouchListener());
			}
		}
	}

	private View.OnTouchListener getSummaryTouchListener() {
		return new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				int action = event.getAction();

				if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
					TextView widget = (TextView) v;

					int x = (int) event.getX();
					int y = (int) event.getY();

					x -= widget.getTotalPaddingLeft();
					y -= widget.getTotalPaddingTop();

					x += widget.getScrollX();
					y += widget.getScrollY();

					Layout layout = widget.getLayout();
					int line = layout.getLineForVertical(y);
					int off = layout.getOffsetForHorizontal(line, x);

					Spannable spannable = new SpannableString(widget.getText());
					ClickableSpan[] links = spannable.getSpans(off, off, ClickableSpan.class);

					if (links.length != 0) {
						if (action == MotionEvent.ACTION_UP) {
							links[0].onClick(widget);
						} else {
							Selection.setSelection(spannable, spannable.getSpanStart(links[0]), spannable.getSpanEnd(links[0]));
						}
						return true;
					} else {
						Selection.removeSelection(spannable);
					}
				}

				return false;
			}
		};
	}

	private CharSequence getCoordinatesFormatSummary(Location loc, int format) {
		double lat = loc != null ? loc.getLatitude() : 49.41869;
		double lon = loc != null ? loc.getLongitude() : 8.67339;

		String formattedCoordinates = OsmAndFormatter.getFormattedCoordinates(lat, lon, format);
		if (format == PointDescription.UTM_FORMAT) {
			SpannableStringBuilder spannableBuilder = new SpannableStringBuilder();
			spannableBuilder.append(getString(R.string.shared_string_example));
			spannableBuilder.append(": ");
			spannableBuilder.append(formattedCoordinates);
			spannableBuilder.append("\n");
			spannableBuilder.append(getString(R.string.utm_format_descr));

			int start = spannableBuilder.length();
			spannableBuilder.append(" ");
			spannableBuilder.append(getString(R.string.shared_string_read_more));

			ClickableSpan clickableSpan = new ClickableSpan() {
				@Override
				public void onClick(@NonNull View widget) {
					Context ctx = getContext();
					if (ctx != null) {
						WikipediaDialogFragment.showFullArticle(ctx, Uri.parse(UTM_FORMAT_WIKI_LINK), isNightMode());
					}
				}

				@Override
				public void updateDrawState(@NonNull TextPaint ds) {
					super.updateDrawState(ds);
					ds.setUnderlineText(false);
				}
			};
			spannableBuilder.setSpan(clickableSpan, start, spannableBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

			return spannableBuilder;
		}
		return getString(R.string.shared_string_example) + ": " + formattedCoordinates;
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		String key = preference.getKey();

		int newFormat = getCoordinatesFormatForKey(key);
		if (newFormat != -1) {
			if (settings.COORDINATES_FORMAT.isSetForMode(getSelectedAppMode())) {
				settings.COORDINATES_FORMAT.set(newFormat);
				updateSelectedFormatPrefs(key);
				return (Boolean) newValue;
			} else {
				FragmentManager fragmentManager = getFragmentManager();
				if (fragmentManager != null) {
					ChangeGeneralProfilesPrefBottomSheet.showInstance(fragmentManager, settings.COORDINATES_FORMAT.getId(), newFormat, this, false);
				}
			}
		}

		return false;
	}

	private void updateSelectedFormatPrefs(String key) {
		PreferenceScreen screen = getPreferenceScreen();
		if (screen != null) {
			for (int i = 0; i < screen.getPreferenceCount(); i++) {
				Preference pref = screen.getPreference(i);
				if (pref instanceof CheckBoxPreference) {
					CheckBoxPreference checkBoxPreference = ((CheckBoxPreference) pref);
					boolean checked = checkBoxPreference.getKey().equals(key);
					checkBoxPreference.setChecked(checked);
				}
			}
		}
	}

	private int getCoordinatesFormatForKey(String key) {
		switch (key) {
			case FORMAT_DEGREES:
				return PointDescription.FORMAT_DEGREES;
			case FORMAT_MINUTES:
				return PointDescription.FORMAT_MINUTES;
			case FORMAT_SECONDS:
				return PointDescription.FORMAT_SECONDS;
			case UTM_FORMAT:
				return PointDescription.UTM_FORMAT;
			case OLC_FORMAT:
				return PointDescription.OLC_FORMAT;
			default:
				return -1;
		}
	}

	private String getCoordinatesKeyForFormat(int format) {
		switch (format) {
			case PointDescription.FORMAT_DEGREES:
				return FORMAT_DEGREES;
			case PointDescription.FORMAT_MINUTES:
				return FORMAT_MINUTES;
			case PointDescription.FORMAT_SECONDS:
				return FORMAT_SECONDS;
			case PointDescription.UTM_FORMAT:
				return UTM_FORMAT;
			case PointDescription.OLC_FORMAT:
				return OLC_FORMAT;
			default:
				return "Unknown format";
		}
	}
}