package de.neo.remote.gpio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import de.neo.remote.api.IInternetSwitch;
import de.neo.remote.api.IInternetSwitchListener;
import de.neo.rmi.protokol.RemoteException;

public class InternetSwitchImpl implements IInternetSwitch {

	public static final String ROOT = "InternetSwitch";

	public static final String FAMILY_REGEX = "[01]{5}";

	/**
	 * Listener for power switch change.
	 */
	private List<IInternetSwitchListener> mListeners = Collections
			.synchronizedList(new ArrayList<IInternetSwitchListener>());
	private String mName;
	private SwitchPower mPower;
	private String mType;
	private String mFamilyCode;
	private int mSwitchNumber;
	private State mState;
	private float[] mPosition;

	public InternetSwitchImpl(String name, SwitchPower power,
			String familyCode, int switchNumber, String type, float[] position) {
		if (!familyCode.matches(FAMILY_REGEX))
			throw new IllegalArgumentException("Invalid Familycode: "
					+ familyCode + ". must match " + FAMILY_REGEX);
		mName = name;
		mPower = power;
		mFamilyCode = familyCode;
		mSwitchNumber = switchNumber;
		mType = type;
		mPosition = position;
		mState = State.OFF;
	}

	public InternetSwitchImpl(Element item, SwitchPower power)
			throws SAXException {
		try {
			mFamilyCode = item.getAttribute("familyCode");
			mSwitchNumber = Integer.parseInt(item.getAttribute("switchNumber"));
			mName = item.getAttribute("name");
			mPosition = new float[] { Float.parseFloat(item.getAttribute("x")),
					Float.parseFloat(item.getAttribute("y")),
					Float.parseFloat(item.getAttribute("z")) };
			mType = item.getAttribute("type");
			if (!mFamilyCode.matches(FAMILY_REGEX))
				throw new IllegalArgumentException("Invalid Familycode: "
						+ mFamilyCode + ". must match " + FAMILY_REGEX);
			this.mPower = power;
			mState = State.OFF;
		} catch (Exception e) {
			throw new SAXException("Error reading switch " + e.getMessage());
		}
	}

	@Override
	public void setState(final State state) throws RemoteException {
		mPower.setSwitchState(mFamilyCode, mSwitchNumber, state);
		this.mState = state;
		new Thread() {
			public void run() {
				informListener(state);
			};
		}.start();
	}

	@Override
	public State getState() throws RemoteException {
		return mState;
	}

	@Override
	public void registerPowerSwitchListener(IInternetSwitchListener listener)
			throws RemoteException {
		if (!mListeners.contains(listener))
			mListeners.add(listener);
	}

	@Override
	public void unregisterPowerSwitchListener(IInternetSwitchListener listener)
			throws RemoteException {
		mListeners.remove(listener);
	}

	private void informListener(State state) {
		List<IInternetSwitchListener> exceptionList = new ArrayList<IInternetSwitchListener>();
		for (IInternetSwitchListener listener : mListeners) {
			try {
				listener.onPowerSwitchChange(mName, state);
			} catch (RemoteException e) {
				exceptionList.add(listener);
			}
		}
		mListeners.removeAll(exceptionList);
	}

	@Override
	public String getType() {
		return mType;
	}

	public String getName() {
		return mName;
	}

	public float[] getPosition() {
		return mPosition;
	}

}