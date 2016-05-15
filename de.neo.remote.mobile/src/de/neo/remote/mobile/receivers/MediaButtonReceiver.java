package de.neo.remote.mobile.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.view.KeyEvent;
import de.neo.remote.api.PlayingBean;
import de.neo.remote.mobile.services.WidgetService;

public class MediaButtonReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
			KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			if (event.getAction() == KeyEvent.ACTION_UP)
				return;
			Intent playIntent = new Intent(context, WidgetService.class);
			if (KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE == event.getKeyCode()
					|| KeyEvent.KEYCODE_MEDIA_PLAY == event.getKeyCode()
					|| KeyEvent.KEYCODE_MEDIA_PAUSE == event.getKeyCode()) {
				playIntent.setAction(RemoteWidgetProvider.ACTION_PLAY);
			} else if (KeyEvent.KEYCODE_MEDIA_NEXT == event.getKeyCode()) {
				playIntent.setAction(RemoteWidgetProvider.ACTION_NEXT);
			} else if (KeyEvent.KEYCODE_MEDIA_PREVIOUS == event.getKeyCode()) {
				playIntent.setAction(RemoteWidgetProvider.ACTION_PREV);
			} else if (KeyEvent.KEYCODE_VOLUME_UP == event.getKeyCode()) {
				playIntent.setAction(RemoteWidgetProvider.ACTION_VOLUP);
			} else if (KeyEvent.KEYCODE_VOLUME_DOWN == event.getKeyCode()) {
				playIntent.setAction(RemoteWidgetProvider.ACTION_VOLDOWN);
			} else {
				return;
			}
			context.startService(playIntent);
		}
		if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
			AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			int volume = (Integer) intent.getExtras().get("android.media.EXTRA_VOLUME_STREAM_VALUE");
			Intent playIntent = new Intent(context, WidgetService.class);
			playIntent.setAction(RemoteWidgetProvider.ACTION_VOLUME);
			double remoteVolume = volumeLocalToRemote(am, volume);
			playIntent.putExtra(RemoteWidgetProvider.EXTRA_VOLUME, remoteVolume);
			context.startService(playIntent);
		}
	}

	public static double volumeLocalToRemote(AudioManager am, int localVolume) {
		int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		// Remote volumes goes from 0 (silent) to 1 (loud)
		// Because of the low steps of android (just 13) set volume from 50%
		// to 100%
		double remoteVolume = 0.5 + 0.5 * localVolume / maxVolume;
		return remoteVolume;
	}

	public static int volumeRemoteToLocal(AudioManager am, PlayingBean playing) {
		int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		int localVolume = (int) (maxVolume * 2 * (playing.getVolume() / 100.0 - 0.5));
		return localVolume;
	}

}