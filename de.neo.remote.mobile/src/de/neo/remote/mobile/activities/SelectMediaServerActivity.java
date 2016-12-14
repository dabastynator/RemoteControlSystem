package de.neo.remote.mobile.activities;

import java.util.ArrayList;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ListView;
import de.neo.remote.api.IWebMediaServer.BeanMediaServer;
import de.neo.remote.mobile.services.WidgetService;
import de.neo.remote.mobile.tasks.AbstractTask;
import de.neo.remote.mobile.util.MediaServerAdapter;
import de.neo.rmi.protokol.RemoteException;
import de.remote.mobile.R;

public class SelectMediaServerActivity extends AbstractConnectionActivity {

	public static final String MS_NUMBER = "ms_number";

	private int appWidgetId;

	private SelectMSListener listener;
	protected ListView msList;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.mediaserver_list);

		findComponents();

		Bundle extras = getIntent().getExtras();
		appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		listener = new SelectMSListener();

		new AsyncTask<Integer, Integer, Exception>() {

			ArrayList<BeanMediaServer> mediaserver;
			String[] ids;

			@Override
			protected Exception doInBackground(Integer... params) {
				try {
					mediaserver = mWebMediaServer.getMediaServer("");
					ids = new String[mediaserver.size()];
					for (int i = 0; i < mediaserver.size(); i++)
						ids[i] = mediaserver.get(i).getID();
				} catch (RemoteException e) {
					return e;
				}
				return null;

			}

			@Override
			protected void onPostExecute(Exception result) {
				if (result != null)
					new AbstractTask.ErrorDialog(getApplicationContext(), result).show();
				msList.setAdapter(new MediaServerAdapter(getApplicationContext(), mediaserver, ids, listener));
			}
		}.execute();

	}

	private void findComponents() {
		msList = (ListView) findViewById(R.id.list_mediaserver);
	}

	public class SelectMSListener {

		public boolean onSelectSwitch(BeanMediaServer mediaserver) {
			// Store selection
			SharedPreferences prefs = SelectMediaServerActivity.this.getSharedPreferences(WidgetService.PREFERENCES, 0);
			SharedPreferences.Editor edit = prefs.edit();
			edit.putString("" + appWidgetId, mediaserver.getID());
			edit.commit();

			WidgetService.updateMusicWidget(getApplicationContext(), appWidgetId, mediaserver);

			Intent resultValue = new Intent();
			resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
			setResult(RESULT_OK, resultValue);
			finish();
			return false;
		}

	}

}
