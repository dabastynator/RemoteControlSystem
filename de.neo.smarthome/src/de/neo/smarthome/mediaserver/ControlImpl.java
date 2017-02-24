package de.neo.smarthome.mediaserver;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import de.neo.remote.api.Server;
import de.neo.remote.protokol.RemoteException;
import de.neo.remote.protokol.ServerPort;
import de.neo.smarthome.api.IControl;

public class ControlImpl implements IControl {

	public static final int MOUSE_MOVE_PORT = 5066;

	public static final String MAKE_DARK = "xset dpms force off";
	public static final String MAKE_BRIDE = "xset dpms force on";
	public static final String EXIT_SCREENSAVER = "gnome-screensaver-command --exit";
	public static final String SHUTDOWN = "shutdown -h now";

	private Robot mRobot;
	private int mX = 0;
	private int mY = 0;

	@Override
	public void shutdown() {
		try {
			Runtime.getRuntime().exec(SHUTDOWN);
		} catch (IOException e) {
		}
	}

	@Override
	public void displayDark() {
		try {
			Runtime.getRuntime().exec(MAKE_DARK);
		} catch (IOException e) {
		}
	}

	@Override
	public void displayBride() {
		try {
			Runtime.getRuntime().exec(MAKE_BRIDE);
			Runtime.getRuntime().exec(EXIT_SCREENSAVER);
		} catch (IOException e) {
		}
	}

	private Robot getRobot() throws RemoteException {
		if (mRobot == null)
			try {
				mRobot = new Robot();
			} catch (AWTException e) {
				throw new RemoteException("", "robot not available: "
						+ e.getMessage());
			}
		return mRobot;
	}

	@Override
	public void mouseMove(int x, int y) throws RemoteException {
		getRobot().mouseMove(this.mX += x, this.mY += y);
	}

	@Override
	public void mousePress(int button) throws RemoteException {
		if (button == IControl.LEFT_CLICK) {
			getRobot().mousePress(InputEvent.BUTTON1_MASK);
			getRobot().mouseRelease(InputEvent.BUTTON1_MASK);
		}
		if (button == IControl.RIGHT_CLICK) {
			getRobot().mousePress(InputEvent.BUTTON3_MASK);
			getRobot().mouseRelease(InputEvent.BUTTON3_MASK);
		}
	}

	@Override
	public void keyPress(String string) throws RemoteException {
		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			if (Character.isUpperCase(c)) {
				getRobot().keyPress(KeyEvent.VK_SHIFT);
			}
			getRobot().keyPress(Character.toUpperCase(c));
			getRobot().keyRelease(Character.toUpperCase(c));

			if (Character.isUpperCase(c)) {
				getRobot().keyRelease(KeyEvent.VK_SHIFT);
			}
		}
	}

	@Override
	public ServerPort openMouseMoveStream() throws RemoteException, IOException {
		ServerSocket server = new ServerSocket(MOUSE_MOVE_PORT);
		MouseMoveStream stream = new MouseMoveStream(server);
		stream.start();
		ServerPort sp = new ServerPort(Server.getServer().getServerPort()
				.getIp(), MOUSE_MOVE_PORT);
		return sp;
	}

	private class MouseMoveStream extends Thread {

		private ServerSocket server;

		public MouseMoveStream(ServerSocket server) {
			this.server = server;
		}

		@Override
		public void run() {
			try {
				Socket socket = server.accept();
				DataInputStream input = new DataInputStream(
						socket.getInputStream());
				while (true) {
					int x = input.readInt();
					int y = input.readInt();
					mouseMove(x, y);
				}
			} catch (IOException e) {
			} catch (RemoteException e) {
			} finally {
				try {
					server.close();
				} catch (IOException e) {
				}
			}
		}

	}
}