package de.neo.remote.mobile.util;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.ViewGroup;
import de.neo.remote.api.PlayingBean;
import de.neo.remote.mobile.fragments.FileFragment;
import de.neo.remote.mobile.fragments.PlaylistFragment;
import de.neo.remote.mobile.services.RemoteService.StationStuff;

public class BrowserPageAdapter extends FragmentStatePagerAdapter {

	private FileFragment mFileFragment;
	private PlaylistFragment mPlaylistFragment;

	public BrowserPageAdapter(FragmentManager fm) {
		super(fm);
	}

	@Override
	public Fragment getItem(int position) {
		if (position == 0) {
			return new FileFragment();
		}
		if (position == 1) {
			return new PlaylistFragment();
		}
		return null;
	}

	@Override
	public int getCount() {
		return 2;
	}

	@Override
	public Object instantiateItem(ViewGroup container, int position) {
		Fragment fragment = (Fragment) super.instantiateItem(container,
				position);
		if (fragment instanceof FileFragment)
			mFileFragment = (FileFragment) fragment;
		if (fragment instanceof PlaylistFragment)
			mPlaylistFragment = (PlaylistFragment) fragment;
		return fragment;
	}

	public void setStation(StationStuff station) {
		if (mFileFragment != null)
			mFileFragment.setStation(station);
		if (mPlaylistFragment != null)
			mPlaylistFragment.setStation(station);
	}

	public boolean onContextItemSelected(MenuItem item, int position) {
		Fragment fragment = getItem(position);
		return fragment.onContextItemSelected(item);
	}

	public void onPlayingBeanChanged(String mediaserver, PlayingBean bean) {
		if (mFileFragment != null)
			mFileFragment.setPlayingFile(bean);
	}

	public void setThumbnail(String file, int width, int height, int[] thumbnail) {
		if (mFileFragment != null)
			mFileFragment.setThumbnail(file, width, height, thumbnail);
	}

	public interface BrowserFragment {
		public void refreshContent(Context context);

		public boolean onKeyDown(int keyCode, KeyEvent event);

		public void setStation(StationStuff station);
	}

}
