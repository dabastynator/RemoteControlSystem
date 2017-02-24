package de.neo.smarthome;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.neo.remote.api.IRegistryConnection;
import de.neo.remote.api.RMILogger;
import de.neo.remote.api.RMILogger.LogPriority;
import de.neo.remote.api.RMILogger.RMILogListener;
import de.neo.remote.api.Server;
import de.neo.remote.protokol.RemoteException;
import de.neo.remote.web.WebServer;
import de.neo.smarthome.RemoteLogger.RemoteLogListener;
import de.neo.smarthome.action.ActionUnitFactory;
import de.neo.smarthome.api.IControlCenter;
import de.neo.smarthome.api.IControlUnit;
import de.neo.smarthome.api.Trigger;
import de.neo.smarthome.controlcenter.ControlCenterImpl;
import de.neo.smarthome.gpio.GPIOUnitFactory;
import de.neo.smarthome.mediaserver.MediaUnitFactory;
import de.neo.smarthome.rccolor.RCColorUnitFactory;

public class RemoteMain {

	public static String REGISTRY_IP = "RegistryIp";
	public static String SERVER_PORT = "ServerPort";
	public static String WEBSERVER = "WebServer";
	public static String WEBSERVER_PORT = "port";
	public static String WEBSERVER_TOKEN = "token";
	public static String WEBSERVER_PATH = "controlcenter";

	public static final SimpleDateFormat LogFormat = new SimpleDateFormat("dd.MM-HH:mm");

	public static Map<String, ControlUnitFactory> mControlUnitFactory;

	static {
		mControlUnitFactory = new HashMap<String, ControlUnitFactory>();
		mControlUnitFactory.put("InternetSwitch", new GPIOUnitFactory());
		mControlUnitFactory.put("CommandAction", new ActionUnitFactory());
		mControlUnitFactory.put("MediaServer", new MediaUnitFactory());
		mControlUnitFactory.put("ColorSetter", new RCColorUnitFactory());
	}

	public static void main(String args[]) {
		checkArgs(args);
		File config = new File(getParameter("--config", args));
		if (!config.exists() || !config.isFile()) {
			System.err.println("Configuration file does not exist.");
			printUsage();
			System.exit(1);
		}
		setupLoging(System.out);
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(config);
			doc.getDocumentElement().normalize();
			NodeList registryList = doc.getElementsByTagName(REGISTRY_IP);
			if (registryList == null || registryList.getLength() == 0)
				throw new SAXException("No registry ip in xml: " + REGISTRY_IP);
			String registryIp = registryList.item(0).getTextContent();

			NodeList serverPortNode = doc.getElementsByTagName(SERVER_PORT);
			int serverPort = IControlCenter.PORT;
			if (serverPortNode != null && serverPortNode.getLength() > 0)
				serverPort = Integer.parseInt(serverPortNode.item(0).getTextContent());

			IControlCenter controlcenter = loadControlCenter(doc);
			List<IControlUnit> units = loadControlUnits(doc, controlcenter);

			Server server = Server.getServer();
			server.startServer(serverPort);

			ControlCenterRegistryConnection connector = new ControlCenterRegistryConnection(controlcenter, units);
			server.manageConnector(connector, registryIp);

			for (Trigger trigger : readStartupTrigger(doc.getElementsByTagName("StartTrigger"))) {
				try {
					controlcenter.trigger(trigger);
				} catch (RemoteException e) {
				}
			}
		} catch (ParserConfigurationException | SAXException | IOException | IllegalArgumentException e) {
			System.err.println("Error parsing configuration: " + e.getMessage());
			System.exit(1);
		}
	}

	private static void setupLoging(final PrintStream stream) {
		RMILogger.addLogListener(new RMILogListener() {
			@Override
			public void rmiLog(LogPriority priority, String message, String id, long date) {
				stream.println(LogFormat.format(new Date()) + " RMI " + priority + " by " + message + " (" + id + ")");
			}
		});
		RemoteLogger.mListeners.add(new RemoteLogListener() {
			@Override
			public void remoteLog(LogPriority priority, String message, String object, long date) {
				stream.println(LogFormat.format(new Date()) + " REMOTE " + priority + " by " + object + ": " + message);
			}
		});
	}

	private static List<IControlUnit> loadControlUnits(Document doc, IControlCenter center)
			throws SAXException, IOException {
		List<IControlUnit> units = new ArrayList<IControlUnit>();
		for (String unitName : mControlUnitFactory.keySet()) {
			NodeList nodeList = doc.getElementsByTagName(unitName);
			ControlUnitFactory factory = mControlUnitFactory.get(unitName);
			for (int i = 0; i < nodeList.getLength(); i++) {
				Element element = (Element) nodeList.item(i);
				AbstractControlUnit unit = factory.createControlUnit(center);
				unit.initialize(element);
				units.add(unit);
			}
		}
		return units;
	}

	private static IControlCenter loadControlCenter(Document doc) {
		NodeList controlCenterRoot = doc.getElementsByTagName(ControlCenterImpl.ROOT);
		NodeList webServerRoot = doc.getElementsByTagName(WEBSERVER);
		if (controlCenterRoot != null && controlCenterRoot.getLength() > 0) {
			ControlCenterImpl center = new ControlCenterImpl(controlCenterRoot.item(0));
			try {
				center.initializeRules(doc.getElementsByTagName("EventRule"));
				center.initializeTrigger(doc.getElementsByTagName("TimeTrigger"));
				WebServer webServer = WebServer.getInstance();
				if (webServerRoot != null && webServerRoot.getLength() > 0) {
					Element webRoot = (Element) webServerRoot.item(0);
					if (webRoot.hasAttribute(WEBSERVER_PORT) && webRoot.hasAttribute(WEBSERVER_TOKEN)) {
						webServer.setPort(Integer.valueOf(webRoot.getAttribute(WEBSERVER_PORT)));
						for (ControlUnitFactory factory : mControlUnitFactory.values()) {
							AbstractUnitHandler handler = factory.createUnitHandler(center);
							webServer.handle(handler.getWebPath(), handler, webRoot.getAttribute(WEBSERVER_TOKEN));
						}
						webServer.handle(WEBSERVER_PATH, center, webRoot.getAttribute(WEBSERVER_TOKEN));
						webServer.start();
					}
				}
			} catch (SAXException | IOException e) {
				RemoteLogger.performLog(LogPriority.ERROR, e.getMessage(), "Controlcenter");
			}

			return center;
		}
		return null;
	}

	private static void checkArgs(String[] args) {
		for (String str : args) {
			if (str.equals("--help") || str.equals("-h")) {
				printUsage();
				System.exit(1);
			}
			if (str.equals("--xmlhelp")) {
				System.exit(1);
			}
		}
	}

	private static String getParameter(String string, String[] args) {
		for (int i = 0; i < args.length - 1; i++) {
			String arg = args[i];
			if (arg.equals(string))
				return args[i + 1];
		}
		System.err.println("Error: Parameter " + string + " missing");
		printUsage();
		System.exit(0);
		return null;
	}

	private static void printUsage() {
		System.out.println("Usage:  ");
		System.out.println("  --help     -h : print usage.");
		System.out.println("  --registry    : ip of the registry.");
		System.out.println("  --config      : configuration xml file.");
		System.out.println("  --xmlhelp     : print xml schema.");
	}

	public static class ControlCenterRegistryConnection implements IRegistryConnection {

		private IControlCenter m_controlcenter;
		List<IControlUnit> m_units;

		public ControlCenterRegistryConnection(IControlCenter controlcenter, List<IControlUnit> units) {
			m_controlcenter = controlcenter;
			if (controlcenter != null && units != null) {
				try {
					for (IControlUnit unit : units)
						controlcenter.addControlUnit(unit);
				} catch (RemoteException e) {
				}
			} else {
				m_units = units;
			}
		}

		@Override
		public void onRegistryConnected(Server server) {
			try {
				IControlCenter controlcenter = m_controlcenter;
				if (controlcenter == null) {
					controlcenter = server.forceFind(IControlCenter.ID, IControlCenter.class);
				} else {
					server.register(IControlCenter.ID, m_controlcenter);
				}
				if (m_units != null)
					for (IControlUnit unit : m_units)
						controlcenter.addControlUnit(unit);
			} catch (RemoteException e) {
				RemoteLogger.performLog(LogPriority.ERROR, e.getMessage(), "RemoteMain");
			}
		}

		@Override
		public void onRegistryLost() {
			// TODO Auto-generated method stub

		}

		@Override
		public boolean isManaged() {
			return true;
		}

	}

	public static List<Trigger> readStartupTrigger(NodeList nodeList) throws SAXException {
		List<Trigger> triggers = new ArrayList<Trigger>();
		for (int i = 0; i < nodeList.getLength(); i++)
			if (nodeList.item(i) instanceof Element) {
				Element element = (Element) nodeList.item(i);
				for (int j = 0; j < element.getChildNodes().getLength(); j++)
					if (element.getChildNodes().item(j) instanceof Element) {
						Element trigger = (Element) element.getChildNodes().item(j);
						Trigger t = new Trigger();
						t.initialize(trigger);
						triggers.add(t);
					}
			}
		return triggers;
	}

}