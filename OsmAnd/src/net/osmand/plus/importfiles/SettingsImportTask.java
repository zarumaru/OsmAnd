package net.osmand.plus.importfiles;

import static net.osmand.plus.AppInitializer.loadRoutingFiles;
import static net.osmand.plus.settings.backend.backup.SettingsHelper.getSettingsToOperate;

import android.app.ProgressDialog;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import net.osmand.plus.utils.AndroidUtils;
import net.osmand.CallbackWithObject;
import net.osmand.plus.utils.FileUtils;
import net.osmand.IndexConstants;
import net.osmand.plus.AppInitializer;
import net.osmand.plus.plugins.CustomOsmandPlugin;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.plugins.audionotes.AudioVideoNotesPlugin;
import net.osmand.plus.base.BaseLoadAsyncTask;
import net.osmand.plus.settings.backend.ExportSettingsType;
import net.osmand.plus.settings.backend.backup.FileSettingsHelper;
import net.osmand.plus.settings.backend.backup.SettingsHelper.CheckDuplicatesListener;
import net.osmand.plus.settings.backend.backup.SettingsHelper.CollectListener;
import net.osmand.plus.settings.backend.backup.SettingsHelper.ImportListener;
import net.osmand.plus.settings.backend.backup.items.PluginSettingsItem;
import net.osmand.plus.settings.backend.backup.items.SettingsItem;
import net.osmand.plus.settings.fragments.FileImportSettingsFragment;
import net.osmand.plus.settings.fragments.ImportCompleteFragment;
import net.osmand.util.Algorithms;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class SettingsImportTask extends BaseLoadAsyncTask<Void, Void, String> {

	private final Uri uri;
	private final String name;
	private final List<ExportSettingsType> settingsTypes;
	private final boolean replace;
	private final boolean silentImport;
	private final String latestChanges;
	private final int version;
	private final CallbackWithObject<List<SettingsItem>> callback;

	public SettingsImportTask(@NonNull FragmentActivity activity, @NonNull Uri uri,
							  @NonNull String name, List<ExportSettingsType> settingsTypes,
							  boolean replace, boolean silentImport, String latestChanges, int version,
							  CallbackWithObject<List<SettingsItem>> callback) {
		super(activity);
		this.uri = uri;
		this.name = name;
		this.settingsTypes = settingsTypes;
		this.replace = replace;
		this.silentImport = silentImport;
		this.latestChanges = latestChanges;
		this.version = version;
		this.callback = callback;
	}

	@Override
	protected String doInBackground(Void... voids) {
		File tempDir = FileUtils.getTempDir(app);
		File dest = new File(tempDir, name);
		return ImportHelper.copyFile(app, dest, uri, true, false);
	}

	@Override
	protected void onPostExecute(String error) {
		File tempDir = FileUtils.getTempDir(app);
		File file = new File(tempDir, name);
		if (error == null && file.exists()) {
			FileSettingsHelper settingsHelper = app.getFileSettingsHelper();
			settingsHelper.collectSettings(file, latestChanges, version, new CollectListener() {
				@Override
				public void onCollectFinished(boolean succeed, boolean empty, @NonNull List<SettingsItem> items) {
					hideProgress();
					if (succeed) {
						List<SettingsItem> pluginIndependentItems = new ArrayList<>();
						List<PluginSettingsItem> pluginSettingsItems = new ArrayList<>();
						for (SettingsItem item : items) {
							if (item instanceof PluginSettingsItem) {
								pluginSettingsItems.add((PluginSettingsItem) item);
							} else if (Algorithms.isEmpty(item.getPluginId())) {
								pluginIndependentItems.add(item);
							}
						}
						for (PluginSettingsItem pluginItem : pluginSettingsItems) {
							handlePluginImport(pluginItem, file);
						}
						if (!pluginIndependentItems.isEmpty()) {
							if (settingsTypes == null) {
								FragmentActivity activity = activityRef.get();
								if (!silentImport && activity != null) {
									FragmentManager fragmentManager = activity.getSupportFragmentManager();
									FileImportSettingsFragment.showInstance(fragmentManager, pluginIndependentItems, file);
								}
							} else {
								Map<ExportSettingsType, List<?>> allSettingsMap = getSettingsToOperate(pluginIndependentItems, false, false);
								List<SettingsItem> settingsList = settingsHelper.getFilteredSettingsItems(allSettingsMap, settingsTypes, pluginIndependentItems, false);
								settingsHelper.checkDuplicates(file, settingsList, settingsList, getDuplicatesListener(file, replace));
							}
						}
					} else if (empty) {
						app.showShortToastMessage(app.getString(R.string.file_import_error, name, app.getString(R.string.shared_string_unexpected_error)));
					}
				}
			});
		} else {
			hideProgress();
			app.showShortToastMessage(app.getString(R.string.file_import_error, name, error));
		}
	}

	private CheckDuplicatesListener getDuplicatesListener(File file, boolean replace) {
		return new CheckDuplicatesListener() {
			@Override
			public void onDuplicatesChecked(@NonNull List<Object> duplicates, List<SettingsItem> items) {
				if (replace) {
					for (SettingsItem item : items) {
						item.setShouldReplace(true);
					}
				}
				app.getFileSettingsHelper().importSettings(file, items, "", 1, getImportListener(file));
			}
		};
	}

	private ImportListener getImportListener(File file) {
		return new ImportListener() {
			@Override
			public void onImportProgressUpdate(int value, int uploadedKb) {

			}

			@Override
			public void onImportItemStarted(@NonNull String type, @NonNull String fileName, int work) {

			}

			@Override
			public void onImportItemProgress(@NonNull String type, @NonNull String fileName, int value) {

			}

			@Override
			public void onImportItemFinished(@NonNull String type, @NonNull String fileName) {

			}

			@Override
			public void onImportFinished(boolean succeed, boolean needRestart, @NonNull List<SettingsItem> items) {
				if (succeed) {
					app.getRendererRegistry().updateExternalRenderers();
					app.getPoiFilters().loadSelectedPoiFilters();
					AppInitializer.loadRoutingFiles(app, null);
					FragmentActivity activity = activityRef.get();
					AudioVideoNotesPlugin plugin = PluginsHelper.getPlugin(AudioVideoNotesPlugin.class);
					if (plugin != null) {
						plugin.indexingFiles(true, true);
					}
					if (activity instanceof MapActivity) {
						((MapActivity) activity).updateApplicationModeSettings();
					}
					if (!silentImport && file != null && activity != null) {
						FragmentManager fm = activity.getSupportFragmentManager();
						ImportCompleteFragment.showInstance(fm, items, file.getName(), needRestart);
					}
				}
			}
		};
	}


	private void handlePluginImport(PluginSettingsItem pluginItem, File file) {
		FragmentActivity activity = activityRef.get();
		ProgressDialog progress;
		if (AndroidUtils.isActivityNotDestroyed(activity)) {
			progress = new ProgressDialog(activity);
			progress.setTitle(app.getString(R.string.loading_smth, ""));
			progress.setMessage(app.getString(R.string.importing_from, pluginItem.getPublicName(app)));
			progress.setIndeterminate(true);
			progress.setCancelable(false);
			progress.show();
		} else {
			progress = null;
		}

		ImportListener importListener = new ImportListener() {
			@Override
			public void onImportProgressUpdate(int value, int uploadedKb) {

			}

			@Override
			public void onImportItemStarted(@NonNull String type, @NonNull String fileName, int work) {

			}

			@Override
			public void onImportItemProgress(@NonNull String type, @NonNull String fileName, int value) {

			}

			@Override
			public void onImportItemFinished(@NonNull String type, @NonNull String fileName) {

			}

			@Override
			public void onImportFinished(boolean succeed, boolean needRestart, @NonNull List<SettingsItem> items) {
				FragmentActivity activity = activityRef.get();
				if (progress != null && AndroidUtils.isActivityNotDestroyed(activity)) {
					progress.dismiss();
				}
				AudioVideoNotesPlugin pluginAudioVideo = PluginsHelper.getPlugin(AudioVideoNotesPlugin.class);
				if (pluginAudioVideo != null) {
					pluginAudioVideo.indexingFiles(true, true);
				}
				CustomOsmandPlugin plugin = pluginItem.getPlugin();
				plugin.loadResources();

				if (!Algorithms.isEmpty(plugin.getDownloadMaps())) {
					app.getDownloadThread().runReloadIndexFilesSilent();
				}
				if (!Algorithms.isEmpty(plugin.getRendererNames())) {
					app.getRendererRegistry().updateExternalRenderers();
				}
				if (!Algorithms.isEmpty(plugin.getRouterNames())) {
					loadRoutingFiles(app, null);
				}
				if (!silentImport && activity != null) {
					plugin.onInstall(app, activity);
				}
				String pluginId = pluginItem.getPluginId();
				File pluginDir = app.getAppPath(IndexConstants.PLUGINS_DIR + pluginId);
				if (!pluginDir.exists()) {
					pluginDir.mkdirs();
				}
				app.getFileSettingsHelper().exportSettings(pluginDir, "items", null, items, false);
			}
		};
		List<SettingsItem> pluginItems = new ArrayList<>(pluginItem.getPluginDependentItems());
		pluginItems.add(0, pluginItem);
		app.getFileSettingsHelper().checkDuplicates(file, pluginItems, pluginItems, new CheckDuplicatesListener() {
			@Override
			public void onDuplicatesChecked(@NonNull List<Object> duplicates, List<SettingsItem> items) {
				for (SettingsItem item : items) {
					item.setShouldReplace(true);
				}
				app.getFileSettingsHelper().importSettings(file, items, "", 1, importListener);
			}
		});
	}
}