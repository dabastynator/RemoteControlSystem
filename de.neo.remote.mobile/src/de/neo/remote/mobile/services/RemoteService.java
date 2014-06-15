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
import de.neo.remote.controlcenter.api.IControlCenter;
import de.neo.remote.controlcenter.api.IControlUnit;
import de.neo.remote.gpiopower.api.IInternetSwitch;
import de.neo.remote.gpiopower.api.IInternetSwitch.State;
import de.neo.remote.gpiopower.api.IInternetSwitchListener;
import de.neo.remote.mediaserver.api.IBrowser;
import de.neo.remote.mediaserver.api.IChatServer;
import de.neo.remote.mediaserver.api.IControl;
import de.neo.remote.mediaserver.api.IImageViewer;
import de.neo.remote.mediaserver.api.IPlayList;
import de.neo.remote.mediaserver.api.IPlayer;
import de.neo.remote.mediaserver.api.IPlayerListener;
import de.neo.remote.mediaserver.api.PlayerException;
import de.neo.remote.mediaserver.api.PlayingBean;
import de.neo.remote.mobile.database.RemoteDatabase;
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

	/**
	 * name of current server
	 */
	protected int serverID;

	/**
	 * ip of current server
	 */
	protected String serverIP;

	/**
	 * name of current server
	 */
	protected String serverName;

	/**
	 * the binder to execute all functions
	 */
	protected PlayerBinder binder;

	/**
	 * local server, to provide
	 */
	protected Server localServer;

	/**
	 * remote station list object
	 */
	protected ControlCenterBuffer controlCenter;

	protected Map<String, StationStuff> stationStuff;

	protected Map<String, Object> unitMap;
	protected Map<String, float[]> unitMapPostion;
	protected Map<String, String> unitMapDescription;

	/**
	 * listener for player
	 */
	protected PlayerListener playerListener;

	/**
	 * listener for download progress and playing files to update notifications
	 */
	protected NotificationHandler notificationHandler;

	public ProgressListener downloadListener;

	protected IInternetSwitchListener internetSwitchListener;

	/**
	 * current playing file
	 */
	protected PlayingBean playingFile;

	/**
	 * handler to post actions in the ui thread
	 */
	protected Handler handler;

	/**
	 * database object to the local database
	 */
	protected RemoteDatabase serverDB;

	/**
	 * list of all listeners for any action on this service
	 */
	protected List<IRemoteActionListener> actionListener;

	private RMILogListener rmiLogListener;

	/**
	 * remote chat server object
	 */
	public IChatServer chatServer;

	/**
	 * current media server
	 */
	public StationStuff currentMediaServer;

	protected SenderProgress uploadListener;

	/**
	 * create connection, execute runnable if connection has started in the ui
	 * thread.
	 * 
	 * @param successRunnable
	 */
	protected void connect() {
		localServer = Server.getServer();
		try {
			try {
				localServer.connectToRegistry(serverIP);
			} catch (SocketException e) {
				localServer.connectToRegistry(serverIP);
			}
			localServer.startServer();

			IControlCenter center = (IControlCenter) localServer.find(
					IControlCenter.ID, IControlCenter.class);
			if (center == null)
				throw new RemoteException(IControlCenter.ID,
						"control center not found in registry");
			controlCenter = new ControlCenterBuffer(center);

			refreshControlCenter();

			chatServer = (IChatServer) localServer.find(IChatServer.ID,
					IChatServer.class);
			// power.registerPowerSwitchListener(powerListener);
			handler.post(new Runnable() {
				@Override
				public void run() {
					for (IRemoteActionListener listener : actionListener)
						listener.onServerConnectionChanged(serverName, serverID);
				}
			});
		} catch (final Exception e) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(RemoteService.this, e.getMessage(),
							Toast.LENGTH_LONG).show();
					for (IRemoteActionListener listener : actionListener)
						listener.onServerConnectionChanged(null, -1);
				}
			});
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		handler = new Handler();
		rmiLogListener = new RMILogListener() {
			@Override
			public void rmiLog(LogPriority priority, String message, String id,
					long date) {
				Log.e("RMI Logs", message);
			}
		};
		RMILogger.addLogListener(rmiLogListener);
		binder = new PlayerBinder(this);
		stationStuff = new HashMap<String, StationStuff>();
		unitMap = new HashMap<String, Object>();
		unitMapPostion = new HashMap<String, float[]>();
		unitMapDescription = new HashMap<String, String>();
		actionListener = new ArrayList<IRemoteActionListener>();
		notificationHandler = new NotificationHandler(this);
		playerListener = new PlayerListener();
		internetSwitchListener = new GPIOListener();
		downloadListener = new ProgressListener();
		uploadListener = new UploadProgressListenr();
		actionListener.add(notificationHandler);
		serverDB = new RemoteDatabase(this);
	}

	@Override
	public void onDestroy() {
		disconnect();
		for (IRemoteActionListener listener : actionListener) {
			listener.onServerConnectionChanged(null, -1);
			listener.onStopService();
		}
		serverDB.close();
		RMILogger.removeLogListener(rmiLogListener);
		super.onDestroy();
	}

	/**
	 * disconnect from current connection
	 */
	private void disconnect() {
		if (localServer != null)
			localServer.close();
		unitMap.clear();
		controlCenter = null;
		stationStuff.clear();
		serverID = -1;
		chatServer = null;
		serverName = null;
		notificationHandler.removeNotification();
	}

	@Override
	public PlayerBinder onBind(Intent intent) {
		return binder;
	}

	/**
	 * @return local server
	 */
	public Server getServer() {
		return localServer;
	}

	public void setCurrentMediaServer(final StationStuff mediaObjects)
			throws RemoteException {
		if (mediaObjects != null) {
			new Thread() {
				@Override
				public void run() {
					StationStuff oldServer = currentMediaServer;
					currentMediaServer = mediaObjects;
					try {
						if (oldServer != null) {
							oldServer.totem
									.removePlayerMessageListener(playerListener);
							oldServer.mplayer
									.removePlayerMessageListener(playerListener);
						}
					} catch (RemoteException e) {
					}
					try {
						currentMediaServer.mplayer
								.addPlayerMessageListener(playerListener);
						currentMediaServer.totem
								.addPlayerMessageListener(playerListener);
						currentMediaServer.omxplayer
								.addPlayerMessageListener(playerListener);
						playerListener.playerMessage(currentMediaServer.player
								.getPlayingBean());
					} catch (PlayerException e) {
					} catch (RemoteException e) {
					}
				}
			}.start();
		}
	}

	public static class StationStuff {
		public IBrowser browser;
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

	public void connectToServer(final int id) {
		new Thread() {
			public void run() {
				if (id != serverID) {
					disconnect();
					serverID = id;
					serverIP = serverDB.getServerDao().getIpOfServer(id);
					serverName = serverDB.getServerDao().getNameOfServer(id);
					connect();
				}
			}
		}.start();
	}

	public void disconnectFromServer() {
		new Thread() {
			public void run() {
				disconnect();
				handler.post(new Runnable() {
					@Override
					public void run() {
						for (IRemoteActionListener listener : actionListener)
							listener.onServerConnectionChanged(serverName,
									serverID);
					}
				});
			}
		}.start();
	}

	public void refreshControlCenter() {
		int stationSize = 0;
		controlCenter.clear();
		try {
			stationSize = controlCenter.getControlUnitNumber();
			controlCenter.getGroundPlot();
		} catch (RemoteException e1) {
		}
		currentMediaServer = null;
		stationStuff.clear();
		unitMap.clear();
		unitMapPostion.clear();
		unitMapDescription.clear();
		for (int i = 0; i < stationSize; i++) {
			try {
				IControlUnit unit = controlCenter.getControlUnit(i);
				Object object = unit.getRemoteableControlObject();
				String name = unit.getName();
				Log.e("control unit", name);
				String description = unit.getDescription();
				float[] position = unit.getPosition();
				unitMap.put(name, object);
				unitMapPostion.put(name, position);
				unitMapDescription.put(name, description);
				if (object instanceof IInternetSwitch)
					((IInternetSwitch) object)
							.registerPowerSwitchListener(internetSwitchListener);
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
			playingFile = playing;
			handler.post(new Runnable() {
				@Override
				public void run() {
					String media = "unknown";
					if (currentMediaServer != null)
						media = currentMediaServer.name;
					for (IRemoteActionListener listener : actionListener)
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
			handler.post(new Runnable() {
				@Override
				public void run() {
					for (IRemoteActionListener listener : actionListener)
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
			handler.post(new Runnable() {
				public void run() {
					for (IRemoteActionListener l : actionListener)
						l.startReceive(size, file);
				}
			});
		}

		@Override
		public void progressReceive(final long size, final String file) {
			handler.post(new Runnable() {
				public void run() {
					for (IRemoteActionListener l : actionListener)
						l.progressReceive(size, file);
				}
			});
		}

		@Override
		public void endReceive(final long size) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(RemoteService.this, "download finished",
							Toast.LENGTH_SHORT).show();
					for (IRemoteActionListener l : actionListener)
						l.endReceive(size);
				}
			});
		}

		@Override
		public void exceptionOccurred(final Exception e) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(RemoteService.this,
							"error occurred while loading: " + e.getMessage(),
							Toast.LENGTH_SHORT).show();
					for (IRemoteActionListener l : actionListener)
						l.exceptionOccurred(e);
				}
			});
		}

		@Override
		public void downloadCanceled() {
			handler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(RemoteService.this, "download cancled",
							Toast.LENGTH_SHORT).show();
					for (IRemoteActionListener l : actionListener)
						l.downloadCanceled();
				}
			});
		}

	}

	public class UploadProgressListenr implements SenderProgress {

		@Override
		public void startSending(final long size) {
			handler.post(new Runnable() {
				public void run() {
					for (IRemoteActionListener l : actionListener)
						l.startSending(size);
				}
			});
		}

		@Override
		public void progressSending(final long size) {
			handler.post(new Runnable() {
				public void run() {
					for (IRemoteActionListener l : actionListener)
						l.progressSending(size);
				}
			});
		}

		@Override
		public void endSending(final long size) {
			handler.post(new Runnable() {
				public void run() {
					for (IRemoteActionListener l : actionListener)
						l.endSending(size);
				}
			});
		}

		@Override
		public void exceptionOccurred(final Exception e) {
			handler.post(new Runnable() {
				public void run() {
					for (IRemoteActionListener l : actionListener)
						l.exceptionOccurred(e);
				}
			});
		}

		@Override
		public void sendingCanceled() {
			handler.post(new Runnable() {
				public void run() {
					for (IRemoteActionListener l : actionListener)
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
		void onServerConnectionChanged(String serverName, int serverID);

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
