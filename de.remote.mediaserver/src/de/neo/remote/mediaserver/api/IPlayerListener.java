package de.neo.remote.mediaserver.api;

import de.newsystem.rmi.api.Oneway;
import de.newsystem.rmi.protokol.RemoteAble;
import de.newsystem.rmi.protokol.RemoteException;

/**
 * listener for current media file
 * @author sebastian
 */
public interface IPlayerListener extends RemoteAble{

	/**
	 * 
	 * @param playing
	 * @throws RemoteException
	 */
	@Oneway
	public void playerMessage(PlayingBean playing) throws RemoteException;
	
}