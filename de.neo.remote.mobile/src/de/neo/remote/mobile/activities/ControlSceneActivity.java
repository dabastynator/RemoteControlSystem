package de.neo.remote.mobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.ViewPager.LayoutParams;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import de.neo.android.opengl.AbstractSceneSurfaceView;
import de.neo.remote.api.IInternetSwitch.State;
import de.neo.remote.api.PlayingBean;
import de.neo.remote.mobile.persistence.RemoteServer;
import de.neo.remote.mobile.services.RemoteBinder;
import de.neo.remote.mobile.tasks.AbstractTask;
import de.neo.remote.mobile.util.ControlSceneRenderer;
import de.remote.mobile.R;

public class ControlSceneActivity extends AbstractConnectionActivity {

	private AbstractSceneSurfaceView mGLView;
	private ControlSceneRenderer mRenderer;
	private Handler mHandler;
	private FrameLayout mLayout;
	private ProgressBar mProgress;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mHandler = new Handler();

		SelectMediaServer selecter = new SelectMediaServer();

		setContentView(R.layout.controlscene);
		findComponents();

		mRenderer = new ControlSceneRenderer(this, selecter);
		mGLView = new AbstractSceneSurfaceView(this, savedInstanceState,
				mRenderer);

		LayoutParams params = new LayoutParams();
		params.height = LayoutParams.MATCH_PARENT;
		params.width = LayoutParams.MATCH_PARENT;
		mLayout.addView(mGLView, 0, params);

		setTitle(getResources().getString(R.string.connecting));
	}

	private void findComponents() {
		mLayout = (FrameLayout) findViewById(R.id.controlscene_layout);
		mProgress = (ProgressBar) findViewById(R.id.controlscene_progress);
	}

	@Override
	protected void onPause() {
		super.onPause();
		mGLView.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mGLView.onResume();
	}

	@Override
	public void onServerConnectionChanged(RemoteServer server) {
		if (mBinder.getControlCenter() == null) {
			setTitle(getResources().getString(R.string.no_controlcenter));
			mProgress.setVisibility(View.GONE);
		} else {
			setTitle(getResources().getString(R.string.load_controlcenter));
			mProgress.setVisibility(View.VISIBLE);
			new Thread(new UpdateGLViewRunner()).start();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.opt_control_refresh:
			setTitle(getResources().getString(R.string.refresh));
			mProgress.setVisibility(View.VISIBLE);
			new Thread() {
				public void run() {
					mBinder.refreshControlCenter();
					new UpdateGLViewRunner().run();
				};
			}.start();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onPlayingBeanChanged(String mediaserver, PlayingBean bean) {
		mRenderer.setPlayingBean(mediaserver, bean);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater mi = new MenuInflater(getApplication());
		mi.inflate(R.menu.power_pref, menu);
		return true;
	}

	@Override
	public void onPowerSwitchChange(String _switch, State state) {
		mRenderer.powerSwitchChanged(_switch, state);

	}

	@Override
	void onRemoteBinder(RemoteBinder mBinder) {
		// TODO Auto-generated method stub

	}

	@Override
	void onStartConnecting() {
		setTitle(getResources().getString(R.string.connecting));
		mProgress.setVisibility(View.VISIBLE);
	}

	public class UpdateGLViewRunner implements Runnable {

		@Override
		public void run() {
			try {
				mRenderer.reloadControlCenter(mBinder);
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						if (mBinder.getControlCenter() != null)
							Toast.makeText(
									ControlSceneActivity.this,
									getResources().getString(
											R.string.loaded_controlcenter),
									Toast.LENGTH_SHORT).show();
						setTitle("Controlcenter@"
								+ mBinder.getServer().getName());
						mProgress.setVisibility(View.GONE);
					}
				});
			} catch (final Exception e) {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						new AbstractTask.ErrorDialog(ControlSceneActivity.this,
								e).show();
						setTitle(getResources().getString(
								R.string.no_conneciton));
						mProgress.setVisibility(View.GONE);
					}
				});
			}
		}

	}

	public class SelectMediaServer {
		public void selectMediaServer(final String id) {
			mHandler.post(new Runnable() {

				@Override
				public void run() {
					Intent intent = new Intent(ControlSceneActivity.this,
							MediaServerActivity.class);
					intent.putExtra(MediaServerActivity.EXTRA_MEDIA_ID, id);
					ControlSceneActivity.this.startActivity(intent);
				}
			});
		}
	}

}
