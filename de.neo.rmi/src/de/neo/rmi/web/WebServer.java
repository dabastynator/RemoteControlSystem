package de.neo.rmi.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpServer;

import de.neo.rmi.api.RMILogger;
import de.neo.rmi.api.RMILogger.LogPriority;
import de.neo.rmi.protokol.RemoteAble;

public class WebServer {

	private static WebServer mInstance;

	public static WebServer getInstance() {
		if (mInstance == null)
			mInstance = new WebServer();
		return mInstance;
	}

	private int mPort = 5060;
	private HttpServer mServer = null;
	private List<WebServerHandler> mHandlerList;

	private WebServer() {
		mHandlerList = new ArrayList<>();
	}

	public void setPort(int port) {
		mPort = port;
	}

	/**
	 * Handle remote able object at specified path.
	 * 
	 * @param path
	 * @param remoteAble
	 * @throws IOException
	 */
	public void handle(String path, RemoteAble remoteAble) throws IOException {
		handle(path, remoteAble, null);
	}

	/**
	 * Handle the remoteable object. Every request must have token parameter.
	 * 
	 * @param remoteAble
	 * @param token
	 * @throws IOException
	 */
	public void handle(String path, RemoteAble remoteAble, String token) throws IOException {

		if (!path.startsWith("/"))
			path = "/" + path;
		RMILogger.performLog(LogPriority.INFORMATION, "Add web-handler", mPort + path);

		WebServerHandler handler = new WebServerHandler(remoteAble, path);
		handler.setSecurityToken(token);
		mHandlerList.add(handler);
	}

	public void start() throws IOException {
		if (mServer != null)
			throw new IllegalStateException("Webserver is already running.");
		RMILogger.performLog(LogPriority.INFORMATION, "Start webserver with " + mHandlerList.size() + " handler",
				"localhost:" + mPort);
		mServer = HttpServer.create(new InetSocketAddress(mPort), 0);
		for (WebServerHandler handler : mHandlerList)
			mServer.createContext(handler.getPath(), handler);
		mServer.setExecutor(null); // creates a default executor
		mServer.start();
	}

	public void shutdown() {
		RMILogger.performLog(LogPriority.INFORMATION, "shutdown webserver", null);
		mServer.stop(10);
		mServer = null;
	}

}