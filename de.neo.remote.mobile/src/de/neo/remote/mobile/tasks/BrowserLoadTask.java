package de.neo.remote.mobile.tasks;

import java.util.Arrays;

import android.content.Intent;
import android.os.AsyncTask;
import android.widget.Toast;
import de.neo.remote.mediaserver.api.IPlayer;
import de.neo.remote.mediaserver.api.PlayerException;
import de.neo.remote.mobile.activities.BrowserActivity;
import de.neo.remote.mobile.activities.ControlSceneActivity;
import de.neo.remote.mobile.services.RemoteService.StationStuff;
import de.neo.remote.mobile.util.BrowserAdapter;
import de.neo.rmi.protokol.RemoteException;
import de.remote.mobile.R;

public class BrowserLoadTask extends AsyncTask<String, Integer, String> {

	Exception exeption = null;
	private BrowserActivity activity;
	private String goTo;
	private boolean goBack;
	private String[] itemArray;

	public BrowserLoadTask(BrowserActivity activity, String goTo, boolean goBack) {
		this.activity = activity;
		this.goTo = goTo;
		this.goBack = goBack;
	}

	@Override
	protected void onPreExecute() {
		super.onPreExecute();

		if (activity.binder.getLatestMediaServer() == null) {
			activity.disableScreen();
		} else {
			activity.setProgressBarVisibility(true);
			activity.setTitle("loading...");
		}
	}

	public void execute() {
		execute(new String[] {});
	}

	@Override
	protected String doInBackground(String... params) {

		try {
			exeption = null;
			itemArray = loadItems(goTo);
		} catch (Exception e) {
			exeption = e;
			itemArray = new String[] {};
		}
		return "";
	}

	private String[] loadItems(String gotoPath)
			throws RemoteException, PlayerException {
		StationStuff mediaServer = activity.binder.getLatestMediaServer();
		if (mediaServer == null) {
			activity.disableScreen();
			return new String[] {};
		}
		switch (activity.viewerState) {
		case DIRECTORIES:
			if (gotoPath != null)
				mediaServer.browser.goTo(gotoPath);
			if (goBack)
				goBack = !mediaServer.browser.goBack();
			mediaServer.browser.getLocation();
			mediaServer.browser.getFullLocation();
			String[] directories = mediaServer.browser.getDirectories();
			String[] files = mediaServer.browser.getFiles();
			String[] all = new String[directories.length + files.length];
			System.arraycopy(directories, 0, all, 0, directories.length);
			System.arraycopy(files, 0, all, directories.length, files.length);
			return all;
		case PLAYLISTS:
			String[] playLists = mediaServer.pls.getPlayLists();
			Arrays.sort(playLists);
			return playLists;
		case PLS_ITEMS:
			activity.plsFileMap.clear();
			for (String item : mediaServer.pls
					.listContent(activity.currentPlayList))
				if (item.indexOf("/") >= 0)
					activity.plsFileMap.put(
							item.substring(item.lastIndexOf("/") + 1), item);
				else
					activity.plsFileMap.put(item, item);
			return activity.plsFileMap.keySet().toArray(new String[] {});
		default:
			return new String[] {};
		}
	}

	@Override
	protected void onPostExecute(String result) {
		super.onPostExecute(result);
		if (goBack) {
			Intent intent = new Intent(activity, ControlSceneActivity.class);
			activity.startActivity(intent);
		}
		StationStuff mediaServer = activity.binder.getLatestMediaServer();
		activity.setProgressBarVisibility(false);
		if (mediaServer == null) {
			activity.setTitle("No media server@"
					+ activity.binder.getLatestMediaServer().name);
			activity.listView
					.setAdapter(new BrowserAdapter(activity, null,
							new String[] {}, activity.viewerState,
							activity.playingBean));
			return;
		}
		activity.listView.setAdapter(new BrowserAdapter(activity,
				mediaServer.browser, itemArray, activity.viewerState,
				activity.playingBean));
		activity.listView.setSelection(activity.selectedPosition);
		switch (activity.viewerState) {
		case DIRECTORIES:
			try {
				activity.setTitle(mediaServer.browser.getLocation() + "@"
						+ mediaServer.name);
			} catch (RemoteException e) {
				activity.setTitle("no connection");
			}
			activity.filesystemButton
					.setBackgroundResource(R.drawable.image_border);
			activity.playlistButton.setBackgroundDrawable(null);
			break;
		case PLAYLISTS:
			activity.setTitle("Playlists@" + mediaServer.name);
			activity.playlistButton
					.setBackgroundResource(R.drawable.image_border);
			activity.filesystemButton.setBackgroundDrawable(null);
			break;
		case PLS_ITEMS:
			activity.playlistButton
					.setBackgroundResource(R.drawable.image_border);
			activity.filesystemButton.setBackgroundDrawable(null);
			activity.setTitle("Playlist: " + activity.currentPlayList + "@"
					+ mediaServer.name);
		}
		if (exeption != null) {
			if (exeption instanceof NullPointerException)
				Toast.makeText(
						activity,
						"NullPointerException: Mediaserver might incorrectly configured",
						Toast.LENGTH_SHORT).show();
			else if (exeption.getMessage() != null
					&& exeption.getMessage().length() > 0)
				Toast.makeText(activity, exeption.getMessage(),
						Toast.LENGTH_SHORT).show();
			else
				Toast.makeText(activity, exeption.getClass().getSimpleName(),
						Toast.LENGTH_SHORT).show();
		}
		IPlayer player = mediaServer.player;
		activity.totemButton.setBackgroundDrawable(null);
		activity.mplayerButton.setBackgroundDrawable(null);
		if (activity.omxButton != null)
			activity.omxButton.setBackgroundDrawable(null);
		if (player == activity.binder.getLatestMediaServer().mplayer)
			activity.mplayerButton
					.setBackgroundResource(R.drawable.image_border);
		if (player == activity.binder.getLatestMediaServer().totem)
			activity.totemButton.setBackgroundResource(R.drawable.image_border);
		if (player == activity.binder.getLatestMediaServer().omxplayer
				&& activity.omxButton != null)
			activity.omxButton.setBackgroundResource(R.drawable.image_border);
	}

}