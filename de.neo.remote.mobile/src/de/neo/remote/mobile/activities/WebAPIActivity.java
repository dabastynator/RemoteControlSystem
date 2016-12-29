package de.neo.remote.mobile.activities;

import java.util.List;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import de.neo.android.persistence.Dao;
import de.neo.android.persistence.DaoException;
import de.neo.android.persistence.DaoFactory;
import de.neo.remote.api.IControlCenter;
import de.neo.remote.api.IWebAction;
import de.neo.remote.api.IWebLEDStrip;
import de.neo.remote.api.IWebMediaServer;
import de.neo.remote.api.IWebSwitch;
import de.neo.remote.mobile.persistence.RemoteDaoBuilder;
import de.neo.remote.mobile.persistence.RemoteServer;
import de.neo.remote.mobile.services.WidgetService;
import de.neo.rmi.api.WebProxyBuilder;
import de.remote.mobile.R;

/**
 * the activity handes connection with remote service.
 * 
 * @author sebastian
 * 
 */
public class WebAPIActivity extends ActionBarActivity {

	public static final String EXTRA_SERVER_ID = "server_id";

	protected ProgressDialog mProgress;
	protected RemoteServer mCurrentServer;
	protected boolean mIsActive;

	protected IWebSwitch mWebSwitch;
	protected IWebLEDStrip mWebLEDStrip;
	protected IWebAction mWebAction;
	protected IWebMediaServer mWebMediaServer;
	protected IControlCenter mWebControlCenter;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getSupportActionBar().setDisplayShowHomeEnabled(true);
		getSupportActionBar().setLogo(R.drawable.ic_launcher);

		// start widget service
		Intent intent = new Intent(this, WidgetService.class);
		startService(intent);

		DaoFactory.initiate(new RemoteDaoBuilder(this));
		mCurrentServer = getFavoriteServer();
		if (mCurrentServer == null) {
			intent = new Intent(this, SelectServerActivity.class);
			startActivityForResult(intent, SelectServerActivity.RESULT_CODE);
		}

		loadWebApi(mCurrentServer);
	}

	protected void loadWebApi(RemoteServer server) {
		if (server != null) {
			mWebSwitch = new WebProxyBuilder().setEndPoint(server.getEndPoint() + "/switch")
					.setSecurityToken(server.getApiToken()).setInterface(IWebSwitch.class).create();
			mWebLEDStrip = new WebProxyBuilder().setEndPoint(server.getEndPoint() + "/ledstrip")
					.setSecurityToken(server.getApiToken()).setInterface(IWebLEDStrip.class).create();
			mWebMediaServer = new WebProxyBuilder().setEndPoint(server.getEndPoint() + "/mediaserver")
					.setSecurityToken(server.getApiToken()).setInterface(IWebMediaServer.class).create();
			mWebAction = new WebProxyBuilder().setEndPoint(server.getEndPoint() + "/action")
					.setSecurityToken(server.getApiToken()).setInterface(IWebAction.class).create();
			mWebControlCenter = new WebProxyBuilder().setEndPoint(server.getEndPoint() + "/controlcenter")
					.setSecurityToken(server.getApiToken()).setInterface(IControlCenter.class).create();
		}
	}

	public static RemoteServer getFavoriteServer() {
		try {
			Dao<RemoteServer> dao = DaoFactory.getInstance().getDao(RemoteServer.class);
			List<RemoteServer> serverList = dao.loadAll();
			for (RemoteServer server : serverList) {
				if (server.isFavorite())
					return server;
			}
		} catch (DaoException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	protected void onResume() {
		super.onResume();
		mIsActive = true;
	}

	public boolean isActive() {
		return mIsActive;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == SelectServerActivity.RESULT_CODE) {
			if (data == null || data.getExtras() == null)
				return;
			Dao<RemoteServer> dao = DaoFactory.getInstance().getDao(RemoteServer.class);
			try {
				mCurrentServer = dao.loadById(data.getExtras().getInt(SelectServerActivity.SERVER_ID));
				loadWebApi(mCurrentServer);
			} catch (DaoException e) {
				e.printStackTrace();
				mCurrentServer = null;
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.opt_server_select:
			Intent intent = new Intent(this, SelectServerActivity.class);
			startActivityForResult(intent, SelectServerActivity.RESULT_CODE);
			break;
		case R.id.opt_exit:
			finish();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onPause() {
		dismissProgress();
		mIsActive = false;
		super.onPause();
	}

	public ProgressDialog createProgress() {
		dismissProgress();
		mProgress = new ProgressDialog(this);
		return mProgress;
	}

	public void dismissProgress() {
		if (mProgress != null)
			mProgress.dismiss();
		mProgress = null;
	}

	@Override
	protected void onDestroy() {
		DaoFactory.finilize();
		super.onDestroy();
	}

	public void progressFinished(Object result) {

	}

}