package de.neo.remote.mediaserver.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import de.neo.remote.mediaserver.api.PlayerException;
import de.neo.remote.mediaserver.api.PlayingBean;
import de.neo.remote.mediaserver.api.PlayingBean.STATE;
import de.neo.rmi.protokol.RemoteException;

public class MPlayer extends AbstractPlayer {

	protected Process mplayerProcess;
	protected PrintStream mplayerIn;
	protected int positionLeft = 0;
	protected int volume = 50;
	private int seekValue;
	private Object playListfolder;

	public MPlayer(String playListfolder) {
		super();
		this.playListfolder = playListfolder;
	}

	protected void writeCommand(String cmd) throws PlayerException {
		if (mplayerIn == null)
			throw new PlayerException("mplayer is down");
		mplayerIn.print(cmd);
		mplayerIn.print("\n");
		mplayerIn.flush();
	}

	@Override
	public void play(String file) {
		if (mplayerProcess == null)
			startPlayer();

		if (new File(file).isDirectory()) {
			createPlayList(file);
			mplayerIn.print("loadlist " + playListfolder + "/playlist.pls\n");
			mplayerIn.flush();
		} else {
			mplayerIn.print("loadfile \"" + file + "\" 0\n");
			mplayerIn.flush();
		}
		try {
			writeVolume();
		} catch (PlayerException e) {
		}
		super.play(file);
	}

	private void createPlayList(String file) {
		try {
			Process exec = Runtime.getRuntime().exec(
					new String[] { "/usr/bin/find", file + "/" });
			PrintStream output = new PrintStream(new FileOutputStream(
					playListfolder + "/playlist.pls"));
			BufferedReader input = new BufferedReader(new InputStreamReader(
					exec.getInputStream()));
			BufferedReader error = new BufferedReader(new InputStreamReader(
					exec.getErrorStream()));
			String line = "";
			while ((line = input.readLine()) != null)
				output.println(line);
			while ((line = error.readLine()) != null)
				System.out.println(line);
			output.close();
			input.close();
			error.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected void startPlayer() {
		try {
			String[] args = new String[] { "/usr/bin/mplayer", "-slave",
					"-quiet", "-idle", "-geometry", positionLeft + ":0" };
			mplayerProcess = Runtime.getRuntime().exec(args);
			// the standard input of MPlayer
			mplayerIn = new PrintStream(mplayerProcess.getOutputStream());
			// start player observer
			new PlayerObserver(mplayerProcess.getInputStream()).start();
			// set default volume
			mplayerIn.print("volume " + volume + " 1\n");
			mplayerIn.flush();
		} catch (IOException e) {
			// throw new PlayerException(e.getMessage());
		}
	}

	@Override
	public void playPause() throws PlayerException {
		writeCommand("pause");
		super.playPause();
	}

	@Override
	public void quit() throws PlayerException {
		writeCommand("quit");
		mplayerIn = null;
		mplayerProcess = null;
		super.quit();
	}

	@Override
	public void next() throws PlayerException {
		writeCommand("pt_step 1");
		super.next();
	}

	@Override
	public void previous() throws PlayerException {
		writeCommand("pt_step -1");
		super.previous();
	}

	@Override
	public void seekForwards() throws RemoteException, PlayerException {
		if (seekValue <= 0)
			seekValue = 5;
		else if (seekValue < -600)
			seekValue *= 5;
		writeCommand("seek " + seekValue + " 0");
		playingBean.incrementCurrentTime(seekValue);
	}

	@Override
	public void seekBackwards() throws RemoteException, PlayerException {
		if (seekValue >= 0)
			seekValue = -5;
		else if (seekValue > -600)
			seekValue *= 5;
		writeCommand("seek " + seekValue + " 0");
		playingBean.incrementCurrentTime(seekValue);
	}

	@Override
	public void volUp() throws PlayerException {
		volume += 3;
		if (volume > 100)
			volume = 100;
		writeVolume();
	}

	@Override
	public void volDown() throws PlayerException {
		volume -= 3;
		if (volume < 0)
			volume = 0;
		writeVolume();
	}

	private void writeVolume() throws PlayerException {
		writeCommand("volume " + volume + " 1");
	}

	@Override
	public void fullScreen(boolean full) throws PlayerException {
		if (full)
			writeCommand("vo_fullscreen 1");
		else
			writeCommand("vo_fullscreen 0");
	}

	@Override
	public void nextAudio() throws PlayerException {
		writeCommand("switch_audio -1");
	}

	@Override
	public void moveLeft() throws PlayerException {
		int time = 0;
		if (playingBean != null)
			time = Math.max(playingBean.getCurrentTime() - 3, 0);
		quit();
		positionLeft -= 1680;
		startPlayer();
		if (playingBean != null && playingBean.getPath() != null) {
			play(playingBean.getPath());
			try {
				setPlayingPosition(time);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void moveRight() throws PlayerException {
		int time = 0;
		if (playingBean != null)
			time = Math.max(playingBean.getCurrentTime() - 3, 0);
		quit();
		positionLeft += 1680;
		startPlayer();
		if (playingBean != null && playingBean.getPath() != null) {
			play(playingBean.getPath());
			try {
				setPlayingPosition(time);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void playPlayList(String pls) throws RemoteException,
			PlayerException {
		if (mplayerIn == null) {
			startPlayer();
		}
		if (!new File(pls).exists())
			throw new PlayerException("playlist " + pls + " does not exist");

		if (firstLineOf(pls).equals("[playlist]")) {
			String url = lineOfFile(pls, 3);
			url = url.substring(6);
			mplayerIn.print("loadfile " + url + "\n");
		} else
			mplayerIn.print("loadlist " + pls + "\n");

		writeVolume();
	}

	private String firstLineOf(String pls) {
		return lineOfFile(pls, 1);
	}

	private String lineOfFile(String file, int line) {
		String firstLine = null;
		try {
			BufferedReader reader = new BufferedReader(new FileReader(new File(
					file)));
			for (int i = 0; i < line; i++)
				firstLine = reader.readLine();
			reader.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return firstLine;
	}

	class PlayerObserver extends Thread {
		private BufferedReader input;

		public PlayerObserver(InputStream stream) {
			input = new BufferedReader(new InputStreamReader(stream));
		}

		@Override
		public void run() {
			String line = null;
			PlayingBean bean = new PlayingBean();
			try {
				while ((line = input.readLine()) != null) {
					if (line.startsWith("Playing")) {
						try {
							String file = line.substring(8);
							file = file.substring(0, file.length() - 1);
							bean = readFileInformations(new File(file));
						} catch (IOException e) {
							bean = new PlayingBean();
						}
						String file = line.substring(line
								.lastIndexOf(File.separator) + 1);
						file = file.substring(0, file.length() - 1);
						bean.setFile(file.trim());
						if (playingBean != null
								&& playingBean.getPath() != null
								&& playingBean.getPath().equals(bean.getPath()))
							bean.setCurrentTime(playingBean.getCurrentTime());
					}
					if (line.startsWith(" Title: "))
						bean.setTitle(line.substring(8).trim());
					if (line.startsWith(" Artist: "))
						bean.setArtist(line.substring(9).trim());
					if (line.startsWith(" Album: "))
						bean.setAlbum(line.substring(8).trim());
					if (line.equals("Starting playback...")) {
						bean.setState(PlayingBean.STATE.PLAY);
						informPlayingBean(bean);
					}
					if (line.startsWith("ICY Info")) {
						bean.setCurrentTime(0);
						bean.parseICYInfo(line);
						bean.setState(STATE.PLAY);
						informPlayingBean(bean);
					}
				}
				bean.setState(PlayingBean.STATE.DOWN);
				informPlayingBean(bean);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void setPlayingPosition(int second) throws RemoteException,
			PlayerException {
		writeCommand("seek " + second + " 2");
		playingBean.setCurrentTime(second);
	}

	@Override
	public void useShuffle(boolean shuffle) throws RemoteException,
			PlayerException {
		throw new PlayerException("shuffle is not supported jet.");
	}
}