package net.osmand.plus.track;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import net.osmand.CallbackWithObject;
import net.osmand.gpx.GPXFile;
import net.osmand.plus.R;
import net.osmand.plus.configmap.tracks.TrackItem;
import net.osmand.plus.configmap.tracks.TrackTab;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.settings.enums.TracksSortMode;
import net.osmand.plus.track.data.TrackFolder;
import net.osmand.plus.track.helpers.GpxSelectionHelper;
import net.osmand.plus.utils.AndroidUtils;

import java.util.List;
import java.util.Set;

public class SelectTrackTabsFragment extends BaseTracksTabsFragment {

	public static final String TAG = SelectTrackTabsFragment.class.getSimpleName();

	private Object fileSelectionListener;

	public boolean getContentStatusBarNightMode() {
		return nightMode;
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		Activity activity = requireActivity();
		int themeId = nightMode ? R.style.OsmandDarkTheme_DarkActionbar : R.style.OsmandLightTheme_DarkActionbar_LightStatusBar;
		Dialog dialog = new Dialog(activity, themeId);
		Window window = dialog.getWindow();
		if (window != null) {
			if (!settings.DO_NOT_USE_ANIMATIONS.get()) {
				window.getAttributes().windowAnimations = R.style.Animations_Alpha;
			}
			window.setStatusBarColor(ContextCompat.getColor(app, getStatusBarColorId()));
		}
		return dialog;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		updateNightMode();
		View view = themedInflater.inflate(R.layout.select_track_fragment, container, false);

		setupToolbar(view);
		setupTabLayout(view);

		return view;
	}

	private void setupToolbar(@NonNull View view) {
		progressBar = view.findViewById(R.id.progress_bar);
		View appbar = view.findViewById(R.id.appbar);
		ViewCompat.setElevation(appbar, 5.0f);
		appbar.setBackgroundColor(ContextCompat.getColor(app, nightMode ? R.color.app_bar_main_dark : R.color.card_and_list_background_light));

		Toolbar toolbar = view.findViewById(R.id.toolbar);
		toolbar.findViewById(R.id.back_button).setOnClickListener(v -> dismiss());
	}

	protected void setTabs(@NonNull List<TrackTab> tabs) {
		tabSize = tabs.size();
		setViewPagerAdapter(viewPager, tabs);
		tabLayout.setViewPager(viewPager);
		viewPager.setCurrentItem(0);
	}

	@Override
	public void tracksLoaded(@NonNull TrackFolder folder) {
		trackTabsHelper.updateItems(folder);
	}

	@Override
	public void loadTracksFinished(@NonNull TrackFolder folder) {
		AndroidUiHelper.updateVisibility(progressBar, false);
		updateTrackTabs();
		updateTabsContent();
	}

	@Override
	public void onTrackFolderSelected(@NonNull TrackFolder trackFolder) {
		FragmentActivity activity = getActivity();
		if (activity != null) {
			FragmentManager manager = activity.getSupportFragmentManager();
			SelectTrackFolderFragment.showInstance(manager, this, getTracksSortMode(), fileSelectionListener, trackFolder.getParentFolder(), trackFolder);
		}
	}

	protected void addTrackItem(@NonNull TrackItem item) {
		trackTabsHelper.addTrackItem(item);
		updateTrackTabs();
		setSelectedTab("import");
		updateTabsContent();
	}

	@Override
	public void setTracksSortMode(@NonNull TracksSortMode sortMode) {
		TrackTab trackTab = getSelectedTab();
		if (trackTab != null) {
			trackTab.setSortMode(sortMode);
			trackTabsHelper.sortTrackTab(trackTab);
			updateTabsContent();
		}
	}

	@Override
	public void onTrackItemsSelected(@NonNull Set<TrackItem> trackItems, boolean selected) {
		TrackItem firstTrackItem = trackItems.iterator().next();
		if (fileSelectionListener instanceof CallbackWithObject) {
			((CallbackWithObject<String>) fileSelectionListener).processResult(firstTrackItem.getPath());
		} else if (fileSelectionListener instanceof GpxFileSelectionListener) {
			GpxSelectionHelper.getGpxFile(requireActivity(), firstTrackItem.getFile(), true, result -> {
				((GpxFileSelectionListener) fileSelectionListener).onSelectGpxFile(result);
				return true;
			});
		}
		dismiss();
	}

	@Override
	public boolean selectionMode() {
		return false;
	}

	public static void showInstance(@NonNull FragmentManager manager, Object fileSelectionListener) {
		if (AndroidUtils.isFragmentCanBeAdded(manager, TAG)) {
			SelectTrackTabsFragment fragment = new SelectTrackTabsFragment();
			fragment.fileSelectionListener = fileSelectionListener;
			fragment.setRetainInstance(true);
			fragment.show(manager, TAG);
		}
	}

	public interface GpxFileSelectionListener {
		void onSelectGpxFile(@NonNull GPXFile gpxFile);
	}
}