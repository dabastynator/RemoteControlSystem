package de.neo.remote.api;

import de.neo.rmi.protokol.RemoteAble;
import de.neo.rmi.protokol.RemoteException;

/**
 * The ImageViewer provides functionality to show images and dia-shows.
 * 
 * @author sebastian
 */
public interface IImageViewer extends RemoteAble {

	/**
	 * all possible image extensions.
	 */
	public static final String[] IMAGE_EXTENSIONS = { "jpg", "jpeg", "png",
			"gif", "tiff", "bmp" };

	public enum Direction {
		LEFT, RIGHT, DOWN, UP
	};

	/**
	 * show given image-file
	 * 
	 * @param file
	 * @throws RemoteException
	 */
	void show(String file) throws RemoteException, ImageException;

	/**
	 * exit the image show.
	 * 
	 * @throws RemoteException
	 */
	void quit() throws RemoteException, ImageException;

	/**
	 * @param imageTime
	 *            The time one image is on the screen in milliseconds.
	 * @throws RemoteException
	 */
	void toggleDiashow(int imageTime) throws RemoteException, ImageException;

	/**
	 * show next image in the folder.
	 * 
	 * @throws RemoteException
	 */
	void next() throws RemoteException, ImageException;

	/**
	 * show previous image in the folder.
	 * 
	 * @throws RemoteException
	 */
	void previous() throws RemoteException, ImageException;

	/**
	 * zoom into the current picture.
	 * 
	 * @throws RemoteException
	 * @throws ImageException
	 */
	void zoomIn() throws RemoteException, ImageException;

	/**
	 * zoom out of current picture.
	 * 
	 * @throws RemoteException
	 * @throws ImageException
	 */
	void zoomOut() throws RemoteException, ImageException;

	/**
	 * move section of the picture.
	 * 
	 * @param direction
	 * @throws RemoteException
	 * @throws ImageException
	 */
	void move(Direction direction) throws RemoteException, ImageException;

}
