package de.newsystem.rmi.dynamics;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import de.newsystem.rmi.api.Oneway;
import de.newsystem.rmi.api.Server;
import de.newsystem.rmi.handler.ServerConnection;
import de.newsystem.rmi.handler.ServerConnection.ConnectionSocket;
import de.newsystem.rmi.protokol.RemoteAble;
import de.newsystem.rmi.protokol.RemoteException;
import de.newsystem.rmi.protokol.Reply;
import de.newsystem.rmi.protokol.Request;
import de.newsystem.rmi.protokol.ServerPort;

/**
 * proxy
 * 
 * @author sebastian
 * 
 */
public class DynamicProxy implements InvocationHandler {

	/**
	 * id of the object
	 */
	private String id;

	/**
	 * server connection of this global object id
	 */
	private ServerConnection serverConnection;

	/**
	 * server on witch provides the proxy
	 */
	private Server server;

	/**
	 * the static counter counts new global objects to number them.
	 */
	private static int counter = 0;

	/**
	 * Allocates new proxy with given id, server connection and server.
	 * 
	 * @param id
	 * @param sc
	 */
	public DynamicProxy(String id, ServerConnection sc, Server server) {
		this.id = id;
		this.serverConnection = sc;
		this.server = server;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] vals)
			throws Throwable {
		if (method.getName().equals("equals") && vals != null
				&& vals.length == 1)
			return equals(vals[0]);
		if (method.getName().equals("hashCode") && vals == null)
			return hashCode();
		if (method.getName().equals("toString") && vals == null)
			return toString();
		Request request = new Request(id, method.getName());
		checkParameter(vals);
		request.setParams(vals);
		request.setOneway(method.getAnnotation(Oneway.class) != null);
		return performeRequest(request);
	}

	/**
	 * perform the given request.
	 * 
	 * @param request
	 * @return result of the request
	 * @throws Throwable
	 */
	private Object performeRequest(Request request) throws Throwable {
		Reply reply = null;
		ConnectionSocket socket = null;
		RemoteException remoteException = null;
		for (int i = 0; i < server.getConnectionSocketCount(); i++) {
			try {
				socket = serverConnection.getFreeConnectionSocket();
			} catch (IOException e) {
				throw new RemoteException(id, e.getMessage());
			}
			try {
				try {
					socket.getOutput().writeObject(request);
					if (request.isOneway())
						return null;
					reply = (Reply) socket.getInput().readObject();
					if (reply == null)
						throw new RemoteException(id, "null returned");
					if (reply.getError() != null)
						throw reply.getError();
					if (reply.getReturnType() != null
							&& reply.getNewId() != null)
						return server.createProxy(reply.getNewId(),
								serverConnection, reply.getReturnType());
					else
						return reply.getResult();
				} catch (IOException e) {
					socket.disconnect();
					remoteException = new RemoteException(id, e.getMessage());
				}
			} catch (Exception e) {
				throw new RemoteException(id, e.getMessage());
			} finally {
				socket.free();
			}
		}
		throw remoteException;
	}

	/**
	 * check parameters for remoteable object -> create adapter
	 * 
	 * @param paramters
	 */
	private void checkParameter(Object[] paramters) {
		if (paramters != null)
			for (int i = 0; i < paramters.length; i++) {
				if (paramters[i] instanceof RemoteAble) {
					String objId = server.getAdapterObjectIdMap().get(
							paramters[i]);
					if (objId == null) {
						DynamicAdapter adapter = new DynamicAdapter(
								paramters[i], server);
						objId = getNextId();
						server.getAdapterMap().put(objId, adapter);
						server.getAdapterObjectIdMap().put(paramters[i], objId);
					}
					Reply r = new Reply();
					r.addNewId(objId);
					r.setServerPort(new ServerPort(server.getServerPort()));
					r.setReturnType(paramters[i].getClass().getInterfaces()[0]);
					paramters[i] = r;

				}
			}
	}

	private String getNextId() {
		String id = "newsystem.parameter(" + server.getServerPort() + ":"
				+ (counter++) + ")";
		return id;
	}

	public String getId() {
		return id;
	}

	@Override
	public boolean equals(Object obj) {
		return toString().equals(obj.toString());
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public String toString() {
		return id;
	}

}
