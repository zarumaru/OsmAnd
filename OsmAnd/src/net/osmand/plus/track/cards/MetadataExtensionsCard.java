package net.osmand.plus.track.cards;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import net.osmand.gpx.GPXUtilities.Metadata;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.util.Algorithms;

import java.util.Map;

public class MetadataExtensionsCard extends BaseMetadataCard {

	public MetadataExtensionsCard(@NonNull MapActivity mapActivity, @NonNull Metadata metadata) {
		super(mapActivity, metadata);
	}

	@Override
	@StringRes
	protected int getTitleId() {
		return R.string.shared_string_extensions;
	}

	@Override
	public void updateContent() {
		super.updateContent();

		Map<String, String> extensions = metadata.extensions;
		updateVisibility(!Algorithms.isEmpty(extensions));

		if (extensions != null) {
			for (String key : extensions.keySet()) {
				String title = extensions.get(key);
				if (title != null) {
					createItemRow(title, key, null);
				}
			}
		}
	}
}