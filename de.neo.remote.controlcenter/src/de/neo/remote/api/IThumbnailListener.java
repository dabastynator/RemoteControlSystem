package de.neo.remote.api;

import de.neo.rmi.api.Oneway;
import de.neo.rmi.protokol.RemoteAble;
import de.neo.rmi.protokol.RemoteException;

/**
 * The thumbnail listener gets thumbnail for entities.
 * 
 * @author sebastian
 */
public interface IThumbnailListener extends RemoteAble {

	/**
	 * Set the thumbnail for specified entity, with width and height.
	 * 
	 * @param file
	 * @param width
	 * @param height
	 * @param thumbnail
	 * @throws RemoteException
	 */
	@Oneway
	public void setThumbnail(String file, int width, int height, int[] thumbnail)
			throws RemoteException;

}
