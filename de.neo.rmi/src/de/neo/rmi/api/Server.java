package de.neo.rmi.api;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.neo.rmi.api.RMILogger.LogPriority;
import de.neo.rmi.dynamics.DynamicAdapter;
import de.neo.rmi.handler.ConnectionHandler;
import de.neo.rmi.handler.ServerConnection;
import de.neo.rmi.handler.ShutdownHandler;
import de.neo.rmi.protokol.GlobalObject;
import de.neo.rmi.protokol.RegistryReply;
import de.neo.rmi.protokol.RegistryRequest;
import de.neo.rmi.protokol.RemoteException;
import de.neo.rmi.protokol.ServerPort;
import de.neo.rmi.protokol.RegistryRequest.Type;

/**
 * server api for all clients. to provide a remote object first initialize the
 * server then register the object.<br>
 * <br>
 * <code>
 * Server s = Server.getServer();<br>
 * s.connectToRegistry(REGISTRY_LOCATION, REGISTRY_URL);<br>
 * s.startServer(SERVER_PORT);<br>
 * s.register(OBJECT_ID, remoteObject);<br>
 * <code>
 * 
 * @author sebastian
 */
public class Server {

	/**
	 * default server port
	 */
	public static int PORT = 5003;

	/**
	 * default count of sockets per connection
	 */
	public static int DEFAULT_CONNECTION_SOCKETCOUNT = 5;

	/**
	 * singleton server object
	 */
	private static Server server;

	/**
	 * maximum sockets per connection
	 */
	private int connectionSocketCount = DEFAULT_CONNECTION_SOCKETCOUNT;

	/**
	 * registry socket
	 */
	private Socket registrySocket;

	/**
	 * registry inputstream
	 */
	private ObjectInputStream registryIn;

	/**
	 * registry outputstream
	 */
	private ObjectOutputStream registryOut;

	/**
	 * server socket
	 */
	private ServerSocket serverSocket;

	/**
	 * shutdown handler of the server
	 */
	private ShutdownHandler shutdownHandler;

	/**
	 * List of all registered ids in the registry.
	 */
	private List<String> registeredIDList = new ArrayList<String>();

	/**
	 * list of all adapters
	 */
	private Map<String, DynamicAdapter> adapterMap = new HashMap<String, DynamicAdapter>();

	/**
	 * map to get id of adapter object
	 */
	private Map<Object, String> adapterObjectId = new HashMap<Object, String>();

	/**
	 * list of all connections to other servers
	 */
	private Map<ServerPort, ServerConnection> serverConnections = Collections
			.synchronizedMap(new HashMap<ServerPort, ServerConnection>());

	/**
	 * list of all connections of the server
	 */
	private List<ConnectionHandler> handlers = new ArrayList<ConnectionHandler>();

	/**
	 * server port
	 */
	private int port = PORT;

	/**
	 * server ip
	 */
	private String ip;

	/**
	 * is connected to registry
	 */
	private boolean isConnectedRegistry = false;

	/**
	 * create connection to the registry. enables to register, find and
	 * unregister global objects.
	 * 
	 * @param registry
	 * @param port
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public void connectToRegistry(String registry, int port)
			throws UnknownHostException, IOException {
		registrySocket = new Socket(registry, port);
		registryOut = new ObjectOutputStream(registrySocket.getOutputStream());
		registryIn = new ObjectInputStream(registrySocket.getInputStream());
		ip = registrySocket.getLocalAddress().getHostAddress();
		isConnectedRegistry = true;
		RMILogger.performLog(LogPriority.INFORMATION, "connect to registry: "
				+ registry + ":" + port, null);
		shutdownHandler = new ShutdownHandler(this);
		Runtime.getRuntime().addShutdownHook(shutdownHandler);
	}

	/**
	 * create connection to the registry. enables to register, find and
	 * unregister global objects.
	 * 
	 * @param registry
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public void connectToRegistry(String registry) throws UnknownHostException,
			IOException {
		connectToRegistry(registry, Registry.PORT);
	}

	/**
	 * force connection to given registry. Retry after 500 ms, if network is not
	 * available.
	 * 
	 * @param ip
	 *            of registry
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public void forceConnectToRegistry(String registry)
			throws UnknownHostException, IOException {
		boolean connected = false;
		long waitTime = 100;
		long maxTime = 1000 * 60 * 10;
		while (!connected) {
			try {
				connectToRegistry(registry);
				connected = true;
			} catch (SocketException e) {
				connected = false;
				RMILogger.performLog(LogPriority.WARNING,
						"connection to registry refused. retry after "
								+ waitTime + "ms", null);
				try {
					Thread.sleep(waitTime);
					waitTime = Math.min(waitTime * 2, maxTime);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		}
	}

	/**
	 * start the server. the port must be initialized, otherwise the default
	 * server port will be used. the server starts a new thread, so this method
	 * is not blocking.
	 * 
	 * @param port
	 */
	public void startServer(int port) {
		this.port = port;
		new ServerThread().start();
	}

	/**
	 * start the server at default port
	 */
	public void startServer() {
		startServer(PORT);
	}

	/**
	 * get singleton server
	 * 
	 * @return server
	 */
	public static Server getServer() {
		if (server == null)
			server = new Server();
		return server;
	}

	/**
	 * register a object in the registry. the registry must be initialized and
	 * connected before.
	 * 
	 * @param id
	 * @param object
	 */
	public void register(String id, Object object) {
		// add adapter
		ServerPort serverPort = new ServerPort(ip, port);
		adapterMap.put(id, new DynamicAdapter(id, object, this));
		adapterObjectId.put(object, id);
		// tell registry
		GlobalObject globalObject = new GlobalObject(id, serverPort);
		RegistryRequest request = new RegistryRequest(Type.REGISTER);
		request.setObject(globalObject);
		request.setId(id);
		try {
			registryOut.writeObject(request);
			@SuppressWarnings("unused")
			RegistryReply reply = (RegistryReply) registryIn.readObject();
			registeredIDList.add(id);
			RMILogger
					.performLog(LogPriority.INFORMATION, "register object", id);
		} catch (IOException | ClassNotFoundException e) {
			RMILogger.performLog(LogPriority.ERROR, "Error register object: "
					+ e.getClass().getSimpleName() + ": " + e.getMessage(), id);
		}
	}

	/**
	 * remove a object from the registry. the registry must be initialized and
	 * connected before.
	 * 
	 * @param id
	 */
	public void unRegister(String id) {
		adapterObjectId.remove(adapterMap.get(id));
		adapterMap.remove(id);
		RegistryRequest request = new RegistryRequest(Type.UNREGISTER);
		try {
			registryOut.writeObject(request);
			@SuppressWarnings("unused")
			RegistryReply reply = (RegistryReply) registryIn.readObject();
			registeredIDList.remove(id);
			RMILogger.performLog(LogPriority.INFORMATION, "unregister object ",
					id);
		} catch (IOException e) {
			RMILogger.performLog(LogPriority.ERROR,
					"unregister object " + e.getMessage(), id);
		} catch (ClassNotFoundException e) {
			RMILogger.performLog(LogPriority.ERROR,
					"unregister object " + e.getMessage(), id);
		}
	}

	/**
	 * search a remote object in the registry. the registry must be initialized
	 * and connected before.
	 * 
	 * @param id
	 * @param template
	 * @return object
	 * @throws RemoteException
	 */
	@SuppressWarnings("rawtypes")
	public Object find(String id, Class template) throws RemoteException {
		try {
			RegistryRequest request = new RegistryRequest(Type.FIND);
			request.setId(id);
			registryOut.writeObject(request);
			RegistryReply reply = (RegistryReply) registryIn.readObject();
			if (reply.getObject() == null)
				return null;
			// connect to server
			ServerConnection sc = connectToServer(reply.getObject()
					.getServerPort());
			// create proxy
			return sc.createProxy(id, template);
		} catch (UnknownHostException e) {
			throw new RemoteException(id, e.getMessage());
		} catch (IOException e) {
			throw new RemoteException(id, e.getMessage());
		} catch (ClassNotFoundException e) {
			throw new RemoteException(id, e.getMessage());
		}
	}

	/**
	 * force search of a remote object in the registry. the search will be
	 * retried, if the object is not in the registry. the registry must be
	 * initialized and connected before.
	 * 
	 * @param id
	 * @param template
	 * @return object
	 * @throws RemoteException
	 */
	@SuppressWarnings("rawtypes")
	public Object forceFind(String id, Class template) throws RemoteException {
		try {
			RegistryRequest request = new RegistryRequest(Type.FIND);
			request.setId(id);
			int sleepTime = 100;
			Object result = null;
			RegistryReply reply = null;
			while (result == null) {
				registryOut.writeObject(request);
				reply = (RegistryReply) registryIn.readObject();
				if (reply.getObject() == null) {
					RMILogger.performLog(LogPriority.WARNING,
							"object not found in registry. retry after "
									+ sleepTime + "ms", id);
					Thread.sleep(sleepTime);
					sleepTime = Math.min(sleepTime * 2, 60 * 1000);
				} else
					result = reply.getObject();
			}
			// connect to server
			ServerConnection sc = connectToServer(reply.getObject()
					.getServerPort());
			// create proxy
			return sc.createProxy(id, template);
		} catch (UnknownHostException e) {
			throw new RemoteException(id, e.getMessage());
		} catch (IOException e) {
			throw new RemoteException(id, e.getMessage());
		} catch (ClassNotFoundException e) {
			throw new RemoteException(id, e.getMessage());
		} catch (InterruptedException e) {
			throw new RemoteException(id, e.getMessage());
		}
	}

	/**
	 * Get the adapter map that maps the id to the adapter.
	 * 
	 * @return id adapter map
	 */
	public Map<String, DynamicAdapter> getAdapterMap() {
		return adapterMap;
	}

	/**
	 * Get the id map that maps the adapter to the id.
	 * 
	 * @return adapter id map
	 */
	public Map<Object, String> getAdapterObjectIdMap() {
		return adapterObjectId;
	}

	/**
	 * The server thread handles connections from other server. It creates new
	 * Threads to handle all connections parallel.
	 * 
	 * @author sebastian
	 */
	private class ServerThread extends Thread {

		@Override
		public void run() {
			try {
				serverSocket = new ServerSocket(port);
				RMILogger.performLog(LogPriority.INFORMATION,
						"server is listening on port: " + port, null);
				while (serverSocket != null) {
					final Socket socket = serverSocket.accept();
					new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								ConnectionHandler handler = new ConnectionHandler(
										ip, port, socket, Server.this);
								handlers.add(handler);
								handler.handle();
							} catch (IOException e) {
								RMILogger.performLog(LogPriority.ERROR,
										"Error creating new connection handler: "
												+ e.getMessage(), null);
							}
						}
					}).start();
				}
			} catch (IOException e1) {
				if (e1 instanceof SocketException)
					RMILogger.performLog(LogPriority.ERROR, "server closed "
							+ "(" + e1.getMessage() + ")", null);
				else
					e1.printStackTrace();
			}
		}

	}

	/**
	 * close all connections
	 * 
	 * @throws IOException
	 */
	public synchronized void close() {
		// close connections
		for (ServerConnection sc : serverConnections.values())
			sc.disconnect();
		for (ConnectionHandler handler : handlers)
			handler.close();
		// close sockets
		if (registrySocket != null)
			try {
				registrySocket.close();
			} catch (IOException e) {
			}
		if (serverSocket != null)
			try {
				serverSocket.close();
			} catch (IOException e) {
			}

		serverSocket = null;
		registrySocket = null;
		serverConnections.clear();
		handlers.clear();
		adapterMap.clear();
		adapterObjectId.clear();
		RMILogger.performLog(LogPriority.INFORMATION, "close server", null);
	}

	/**
	 * Create connection to given server ip and port. If there is already a
	 * connection, the existing one will be returned.
	 * 
	 * @param serverPort
	 * @return server connection
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public synchronized ServerConnection connectToServer(ServerPort serverPort)
			throws UnknownHostException, IOException {
		if (serverPort == null)
			throw new RuntimeException("serverport must not be null");
		ServerConnection serverConnection = serverConnections.get(serverPort);
		if (serverConnection != null)
			return serverConnection;
		serverConnection = new ServerConnection(serverPort, this);
		serverConnections.put(serverPort, serverConnection);
		return serverConnection;
	}

	/**
	 * returns the serverPort of this server
	 * 
	 * @return serverPort
	 */
	public ServerPort getServerPort() {
		return new ServerPort(ip, port);
	}

	/**
	 * @return true if server is connected to the registry
	 */
	public boolean isConnectedToRegistry() {
		return isConnectedRegistry;
	}

	/**
	 * @return maximum number of sockets per connection
	 */
	public int getConnectionSocketCount() {
		return connectionSocketCount;
	}

	/**
	 * set maximum number of sockets per connection. the sockets will be build
	 * dynamically, if they are needed.
	 * 
	 * @param connectionSocketCount
	 */
	public void setConnectionSocketCount(int connectionSocketCount) {
		this.connectionSocketCount = connectionSocketCount;
	}

	public List<String> getRegisteredIDs() {
		return registeredIDList;
	}

	public synchronized void closeConnectionTo(ServerPort serverPort) {
		ServerConnection connection = serverConnections.get(serverPort);
		if (connection != null) {
			connection.disconnect();
			serverConnections.remove(serverPort);
		}
	}

	public void manageConnector(IRegistryConnection connector, String registry) {
		ConnectorManager manager = new ConnectorManager(this, connector,
				registry);
		manager.start();
	}

}
