package de.neo.remote.mobile.services;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;
import de.neo.android.persistence.DaoBuilder;
import de.neo.android.persistence.DaoFactory;
import de.neo.remote.api.IControl;
import de.neo.remote.api.IControlCenter;
import de.neo.remote.api.IControlUnit;
import de.neo.remote.api.IImageViewer;
import de.neo.remote.api.IInternetSwitch;
import de.neo.remote.api.IInternetSwitch.State;
import de.neo.remote.api.IInternetSwitchListener;
import de.neo.remote.api.IPlayList;
import de.neo.remote.api.IPlayer;
import de.neo.remote.api.IPlayerListener;
import de.neo.remote.api.PlayerException;
import de.neo.remote.api.PlayingBean;
import de.neo.remote.mobile.persistence.RemoteDaoFilling;
import de.neo.remote.mobile.persistence.RemoteDataBase;
import de.neo.remote.mobile.persistence.RemoteServer;
import de.neo.remote.mobile.util.BufferBrowser;
import de.neo.remote.mobile.util.ControlCenterBuffer;
import de.neo.remote.mobile.util.NotificationHandler;
import de.neo.rmi.api.RMILogger;
import de.neo.rmi.api.RMILogger.LogPriority;
import de.neo.rmi.api.RMILogger.RMILogListener;
import de.neo.rmi.api.Server;
import de.neo.rmi.protokol.RemoteException;
import de.neo.rmi.transceiver.ReceiverProgress;
import de.neo.rmi.transceiver.SenderProgress;

public class RemoteService extends Service {

	protected RemoteServer mCurrentServer;
	protected ControlCenterBuffer mCurrentControlCenter;
	public StationStuff mCurrentMediaServer;
	protected PlayingBean mCurrentPlayingFile;

	protected PlayerBinder mBinder;
	protected Server mLocalServer;
	protected Map<String, BufferdUnit> mUnitMap;
	protected NotificationHandler mNotificationHandler;

	public ProgressListener downloadListener;
	SenderProgress uploadListener;
	private PlayerListener mPlayerListener;

	protected IInternetSwitchListener internetSwitchListener;
	protected Handler mHandler;

	protected List<IRemoteActionListener> mActionListener;

	/**
	 * create connection, execute runnable if connection has started in the ui
	 * thread.
	 * 
	 * @param successRunnable
	 */
	protected void connect() {
		mLocalServer = Server.getServer();
		try {
			try {
				mLocalServer.connectToRegistry(mCurrentServer.getIP());
			} catch (SocketException e) {
				mLocalServer.connectToRegistry(mCurrentServer.getIP());
			}
			mLocalServer.startServer();

			IControlCenter center = mLocalServer.find(IControlCenter.ID,
					IControlCenter.class);
			if (center == null)
				throw new RemoteException(IControlCenter.ID,
						"control center not found in registry");
			mCurrentControlCenter = new ControlCenterBuffer(center);

			refreshControlCenter();

			// power.registerPowerSwitchListener(powerListener);
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					for (IRemoteActionListener listener : mActionListener)
						listener.onServerConnectionChanged(mCurrentServer);
				}
			});
		} catch (final Exception e) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					mCurrentServer = null;
					Toast.makeText(RemoteService.this, e.getMessage(),
							Toast.LENGTH_LONG).show();
					for (IRemoteActionListener listener : mActionListener)
						listener.onServerConnectionChanged(mCurrentServer);
				}
			});
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mHandler = new Handler();
		RMILogListener rmiLogListener = new RMILogListener() {
			@Override
			public void rmiLog(LogPriority priority, String message, String id,
					long date) {
				Log.e("RMI Logs", message);
			}
		};
		RMILogger.addLogListener(rmiLogListener);
		mBinder = new PlayerBinder(this);
		mUnitMap = new HashMap<String, BufferdUnit>();
		mActionListener = new ArrayList<IRemoteActionListener>();
		mNotificationHandler = new NotificationHandler(this);
		mPlayerListener = new PlayerListener();
		internetSwitchListener = new GPIOListener();
		downloadListener = new ProgressListener();
		uploadListener = new UploadProgressListenr();
		mActionListener.add(mNotificationHandler);
		DaoBuilder builder = new DaoBuilder().setDatabase(
				new RemoteDataBase(this)).setDaoMapFilling(
				new RemoteDaoFilling());
		DaoFactory.initiate(builder);
	}

	@Override
	public void onDestroy() {
		disconnectFromServer();
		for (IRemoteActionListener listener : mActionListener) {
			listener.onServerConnectionChanged(null);
			listener.onStopService();
		}
		DaoFactory.finilize();
		super.onDestroy();
	}

	@Override
	public PlayerBinder onBind(Intent intent) {
		return mBinder;
	}

	/**
	 * @return local server
	 */
	public Server getServer() {
		return mLocalServer;
	}

	public void setCurrentMediaServer(final StationStuff mediaObjects)
			throws RemoteException {
		if (mediaObjects != null) {
			new Thread() {
				@Override
				public void run() {
					StationStuff oldServer = mCurrentMediaServer;
					mCurrentMediaServer = mediaObjects;
					try {
						if (oldServer != null) {
							oldServer.totem
									.removePlayerMessageListener(mPlayerListener);
							oldServer.mplayer
									.removePlayerMessageListener(mPlayerListener);
						}
					} catch (RemoteException e) {
					}
					try {
						mCurrentMediaServer.mplayer
								.addPlayerMessageListener(mPlayerListener);
						mCurrentMediaServer.totem
								.addPlayerMessageListener(mPlayerListener);
						mCurrentMediaServer.omxplayer
								.addPlayerMessageListener(mPlayerListener);
						mPlayerListener
								.playerMessage(mCurrentMediaServer.player
										.getPlayingBean());
					} catch (PlayerException e) {
					} catch (RemoteException e) {
					}
				}
			}.start();
		}
	}

	public static class BufferdUnit {

		public BufferdUnit(IControlUnit controlUnit) throws RemoteException {
			mUnit = controlUnit;
			mID = mUnit.getID();
			mName = mUnit.getName();
			mDescription = mUnit.getDescription();
			mObject = mUnit.getRemoteableControlObject();
			mPosition = mUnit.getPosition();
		}

		public String mID;
		public String mName;
		public String mDescription;
		public StationStuff mStation;
		public float[] mPosition;
		public Object mObject;
		public IControlUnit mUnit;
		public String mSwitchType;
	}

	public static class StationStuff {
		public BufferBrowser browser;
		public IPlayer player;
		public IPlayList pls;
		public IControl control;
		public IPlayer mplayer;
		public IPlayer totem;
		public IPlayer omxplayer;
		public IImageViewer imageViewer;
		public String name;
		public int directoryCount;
	}

	public void connectToServer(final RemoteServer server) {
		disconnectFromServer();
		new Thread() {
			public void run() {
				if (mCurrentServer != server) {
					mCurrentServer = server;
					connect();
				}
			}
		}.start();
	}

	public void disconnectFromServer() {
		new Thread() {
			public void run() {
				if (mLocalServer != null)
					mLocalServer.close();
				mUnitMap.clear();
				mCurrentControlCenter = null;
				mCurrentServer = null;
				mNotificationHandler.removeNotification();
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						for (IRemoteActionListener listener : mActionListener)
							listener.onServerConnectionChanged(null);
					}
				});
			}
		}.start();
	}

	public void refreshControlCenter() {
		String[] ids = null;
		mCurrentControlCenter.clear();
		try {
			ids = mCurrentControlCenter.getControlUnitIDs();
			mCurrentControlCenter.getGroundPlot();
		} catch (RemoteException e1) {
		}
		mCurrentMediaServer = null;
		mUnitMap.clear();
		for (String id : ids) {
			try {
				BufferdUnit bufferdUnit = new BufferdUnit(
						mCurrentControlCenter.getControlUnit(id));
				Log.e("control unit", bufferdUnit.mName);
				mUnitMap.put(bufferdUnit.mID, bufferdUnit);
				if (bufferdUnit.mObject instanceof IInternetSwitch) {
					IInternetSwitch iswitch = (IInternetSwitch) bufferdUnit.mObject;
					iswitch.registerPowerSwitchListener(internetSwitchListener);
					bufferdUnit.mSwitchType = iswitch.getType();
				}
			} catch (Exception e) {
				Log.e("error",
						e.getClass().getSimpleName() + ": " + e.getMessage());
			}
		}
	}

	/**
	 * listener for player activity. make notification if any message comes.
	 * 
	 * @author sebastian
	 */
	public class PlayerListener implements IPlayerListener {

		@Override
		public void playerMessage(final PlayingBean playing) {
			mCurrentPlayingFile = playing;
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					String media = "unknown";
					if (mCurrentMediaServer != null)
						media = mCurrentMediaServer.name;
					for (IRemoteActionListener listener : mActionListener)
						listener.onPlayingBeanChanged(media, playing);
				}
			});
		}
	}

	/**
	 * the gpio listener listenes for remote power switch cange.
	 * 
	 * @author sebastian
	 * 
	 */
	public class GPIOListener implements IInternetSwitchListener {

		@Override
		public void onPowerSwitchChange(final String switchName,
				final State state) throws RemoteException {
			Log.e("gpio power", "Switch: " + switchName + " " + state);
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					for (IRemoteActionListener listener : mActionListener)
						listener.onPowerSwitchChange(switchName, state);
				}
			});
		}

	}

	/**
	 * the progresslistener listens for remote download progess.
	 * 
	 * @author sebastian
	 * 
	 */
	public class ProgressListener implements ReceiverProgress {

		@Override
		public void startReceive(final long size, final String file) {
			mHandler.post(new Runnable() {
				public void run() {
					for (IRemoteActionListener l : mActionListener)
						l.startReceive(size, file);
				}
			});
		}

		@Override
		public void progressReceive(final long size, final String file) {
			mHandler.post(new Runnable() {
				public void run() {
					for (IRemoteActionListener l : mActionListener)
						l.progressReceive(size, file);
				}
			});
		}

		@Override
		public void endReceive(final long size) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(RemoteService.this, "download finished",
							Toast.LENGTH_SHORT).show();
					for (IRemoteActionListener l : mActionListener)
						l.endReceive(size);
				}
			});
		}

		@Override
		public void exceptionOccurred(final Exception e) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(RemoteService.this,
							"error occurred while loading: " + e.getMessage(),
							Toast.LENGTH_SHORT).show();
					for (IRemoteActionListener l : mActionListener)
						l.exceptionOccurred(e);
				}
			});
		}

		@Override
		public void downloadCanceled() {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(RemoteService.this, "download cancled",
							Toast.LENGTH_SHORT).show();
					for (IRemoteActionListener l : mActionListener)
						l.downloadCanceled();
				}
			});
		}

	}

	public class UploadProgressListenr implements SenderProgress {

		@Override
		public void startSending(final long size) {
			mHandler.post(new Runnable() {
				public void run() {
					for (IRemoteActionListener l : mActionListener)
						l.startSending(size);
				}
			});
		}

		@Override
		public void progressSending(final long size) {
			mHandler.post(new Runnable() {
				public void run() {
					for (IRemoteActionListener l : mActionListener)
						l.progressSending(size);
				}
			});
		}

		@Override
		public void endSending(final long size) {
			mHandler.post(new Runnable() {
				public void run() {
					for (IRemoteActionListener l : mActionListener)
						l.endSending(size);
				}
			});
		}

		@Override
		public void exceptionOccurred(final Exception e) {
			mHandler.post(new Runnable() {
				public void run() {
					for (IRemoteActionListener l : mActionListener)
						l.exceptionOccurred(e);
				}
			});
		}

		@Override
		public void sendingCanceled() {
			mHandler.post(new Runnable() {
				public void run() {
					for (IRemoteActionListener l : mActionListener)
						l.sendingCanceled();
				}
			});
		}

	}

	/**
	 * this interface informs listener about any action on the remote service,
	 * such as new connection, new power switch state or new playing file.
	 * 
	 * @author sebastian
	 */
	public interface IRemoteActionListener extends ReceiverProgress,
			SenderProgress {

		/**
		 * server player plays new file
		 * 
		 * @param bean
		 */
		void onPlayingBeanChanged(String mediasesrver, PlayingBean bean);

		/**
		 * connection with server changed
		 * 
		 * @param serverName
		 */
		void onServerConnectionChanged(RemoteServer server);

		/**
		 * call on stopping remote service.
		 */
		void onStopService();

		/**
		 * call on power switch change.
		 * 
		 * @param _switch
		 * @param state
		 */
		void onPowerSwitchChange(String _switch, State state);

	}

}
