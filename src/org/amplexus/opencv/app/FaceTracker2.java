package org.amplexus.opencv.app;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.objdetect.CascadeClassifier;

/*
 * Detects faces in an image, draws boxes around them, and writes the results
 * to "faceDetection.png".
 * 
 * To avoid errors when recycling VideoCapture connections:
 * 	export LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libv4l/v4l1compat.so
 * 
 * Needs opencv-246.jar and libopencv_java246.so
 * Runs with -Djava.library.path=/path/to/lib (containing above .so file)
 */
public class FaceTracker2 {
	
	public static final String	DEFAULT_USBPORT		= "/dev/ttyUSB0" ;
	public static final int		DEFAULT_BAUD_RATE	= 9600 ;
	public static final int		DEFAULT_DELTA		= 5;

	// static final String CASCADE_CLASSIFIER_FILENAME = "/lbpcascade_frontalface.xml"; // Least expensive / least accurate
	static final String CASCADE_CLASSIFIER_FILENAME = "/haarcascade_frontalface_alt.xml"; // More expensive / more accurate
	// static final String CASCADE_CLASSIFIER_FILENAME = "/haarcascade_frontalface_default.xml";  // Most expensive / most accurate
	
    private static final Logger log = Logger.getLogger(FaceTracker2.class);
	private XBeeCommunicatorTask xbeeTask = null;
	private WebcamTask webcamTask = null;

	private String usbPort = DEFAULT_USBPORT ;	// The current USB port we talk to the robot through
	private int baudRate = DEFAULT_BAUD_RATE ;	// The current baud rate that we talk to the robot at
	private int delta = DEFAULT_DELTA ;

	private JLabel messageLabel ;				// Message bar - displays status messages at the bottom of the window
	private JButton cancelButton ;				// Cancel button - cancels the currently executing XbeeCommunicatorTask
	private JButton resetButton ;				// Reset button - reset the pan-tilt to default position
	private JButton pingButton ;				// Ping button - ping the pan-tilt arduino
	private JButton selfTestButton ;			// Self Test button - asks the pan-tilt arduino to perform a self test
	private JComboBox baudRateComboBox ;		// Baud rate - choose the speed at which we talk to the robot
	private JComboBox webCamComboBox ;			// Web cams - choose the webcam to talk to
	private JComboBox usbPortComboBox ;			// USB port - choose the USB port through which we talk to the robot
	
	private JToggleButton activateWebcamButton;
	private JLabel deltaLabel ;					// Label for the speed slider
	private JSlider deltaSlider;				// Speed - choose the speed that the robot will move at
	private JLabel webcamImageLabel;
	private ImageIcon webcamImageIcon;
	private Dimension minimumSize;
	private Boolean validWebcam[] = new Boolean[4];
	VideoCapture videoCapture;
	
	public static void main(String[] args) {

        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        PropertyConfigurator.configure("log4j.properties");
        FaceTracker2 tracker = new FaceTracker2();
        tracker.displayGUI();
	}

	private FaceTracker2() {
		initWebcam();
	}
	
	private void initWebcam() {
		
		videoCapture = new VideoCapture();

		minimumSize = new Dimension(640, 480);
		
		for(int i = 0; i < validWebcam.length; i++) {
			videoCapture.open(i);
			if(videoCapture.isOpened()) {
				
				Mat image = new Mat();
				if(videoCapture.read(image)) {
		            double frameWidth = videoCapture.get(Highgui.CV_CAP_PROP_FRAME_WIDTH);
		            double frameHeight = videoCapture.get(Highgui.CV_CAP_PROP_FRAME_HEIGHT);
					System.out.println("Discovered device #" + i + " with dims " + frameWidth + "x" + frameHeight);
//					minimumSize = new Dimension((int)frameWidth, (int)frameHeight);
					validWebcam[i] = true;
				} else
					validWebcam[i] = false;
				videoCapture.release();
			} else {
				validWebcam[i] = false;
			}			
		}
//		videoCapture.open(0);		
//		if(vc.isOpened()) {
//			System.out.println("Opened 0 ok"); 
//			Mat image = new Mat();
//			if(vc.read(image)) {
//				System.out.println("Read image with 0"); 
//			} else {				
//				System.out.println("Failed to read image with 0");
//			}
//		} else {			
//			System.out.println("Failed to open 0");
//		}
//		vc.release();
	}
	
	private void displayGUI() {

		messageLabel = new JLabel("Use arrow keys to move, the period (.) key to stop") ;
		
		JButton aboutButton = new JButton("About") ;
		aboutButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JOptionPane.showMessageDialog((Component) e.getSource(), "Pan-Tilt OpenCV Face Tracker App v1.0") ;
			}
		});

		cancelButton = new JButton("Cancel") ;
		cancelButton.setEnabled(false) ;
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(!xbeeTask.isDone())
					xbeeTask.cancel(true) ;
			}
		});

		pingButton = new JButton("Ping") ;
		pingButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ping();
			}
		});

		resetButton = new JButton("Reset") ;
		resetButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				reset();
			}
		});

		selfTestButton = new JButton("Test") ;
		selfTestButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				selfTest();
			}
		});

		Integer[] baudRates = {2400, 4800, 9600, 19200, 38400, 76800, 153600 } ;
		baudRateComboBox = new JComboBox(baudRates) ;
		baudRateComboBox.setSelectedIndex(2) ;
		baudRateComboBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				baudRate = (Integer) baudRateComboBox.getSelectedItem() ;
			}
		});

		String[] webCams;
		int numWebcams = 0;
		for(int i = 0; i < validWebcam.length; i++) {
			if(validWebcam[i]) {
				numWebcams++;
			}
		}
		
		webCams = new String[numWebcams];
		int webCamId = 0;
		for(int i = 0; i < validWebcam.length; i++) {
			if(validWebcam[i]) {
				webCams[webCamId++] = new String("Webcam #" + i);
			}
		}
		
		webCamComboBox = new JComboBox(webCams) ;
		webCamComboBox.setSelectedIndex(-1);
		webCamComboBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int webCamId = webCamComboBox.getSelectedIndex();
				if(webCamId < 0)
					return;

				activateWebcamButton.setEnabled(true);
				activateWebcamButton.setSelected(false);

				if(videoCapture.isOpened())
					videoCapture.release();
				videoCapture.open(webCamId);
			}
		});

		
		activateWebcamButton = new JToggleButton("Activate Webcam", false) ;
		activateWebcamButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(activateWebcamButton.isSelected()) {
					webCamComboBox.setEnabled(false);
					webcamTask = new WebcamTask(videoCapture, 1000, webcamImageLabel);
					webcamTask.execute();
				} else {
					if(webcamTask != null && ! webcamTask.isCancelled() && ! webcamTask.isDone())
						webcamTask.cancel(false);
					webCamComboBox.setEnabled(true);
				}
			}
		});
		activateWebcamButton.setEnabled(false); // Won't be enabled unless a webcam is selected

		String[] usbPorts = enumerateUsbPorts();
		usbPortComboBox = new JComboBox(usbPorts) ;
		usbPortComboBox.setSelectedIndex(0) ;
		usbPortComboBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				usbPort = (String) usbPortComboBox.getSelectedItem() ;
			}
		});
		
		deltaLabel = new JLabel("Speed: ") ;
		deltaSlider = new JSlider(JSlider.HORIZONTAL, 0, 45, 10) ;
		deltaSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				delta = deltaSlider.getValue() ;
			}
		});
		deltaSlider.setMajorTickSpacing(50) ;
		deltaSlider.setMinorTickSpacing(10) ;
		deltaSlider.setPaintTicks(true) ;
		deltaSlider.setPaintLabels(true) ;

		/*
		 * Layout our GUI
		 */
		
		JPanel headerSubPanel1 = new JPanel();
		LayoutManager headerSubPanel1BoxLayout = new BoxLayout(headerSubPanel1, BoxLayout.LINE_AXIS) ;
		headerSubPanel1.setLayout(headerSubPanel1BoxLayout);
		headerSubPanel1.add(activateWebcamButton);
		headerSubPanel1.add(aboutButton);
		headerSubPanel1.add(webCamComboBox);
		headerSubPanel1.add(baudRateComboBox);
		headerSubPanel1.add(usbPortComboBox);
		
		JPanel headerSubPanel2 = new JPanel();
		LayoutManager headerSubPanel2BoxLayout = new BoxLayout(headerSubPanel2, BoxLayout.LINE_AXIS);
		headerSubPanel2.setLayout(headerSubPanel2BoxLayout);
		headerSubPanel2.add(deltaSlider);
		headerSubPanel2.add(cancelButton);
		headerSubPanel2.add(resetButton);
		headerSubPanel2.add(pingButton);
		headerSubPanel2.add(selfTestButton);
		
		JPanel headerPanel = new JPanel();
		LayoutManager headerPanelBoxLayout = new BoxLayout(headerPanel, BoxLayout.PAGE_AXIS) ;
		headerPanel.setLayout(headerPanelBoxLayout) ;
		headerPanel.add(headerSubPanel1) ;
		headerPanel.add(headerSubPanel2) ;

		JPanel centerPanel = new JPanel();
		LayoutManager centerPanelBoxLayout = new BoxLayout(centerPanel, BoxLayout.PAGE_AXIS);
		centerPanel.setLayout(centerPanelBoxLayout);
		webcamImageLabel = new JLabel("");

		BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
		webcamImageIcon = new ImageIcon();
		webcamImageIcon.setImage(image);
		webcamImageLabel = new JLabel(webcamImageIcon);
		webcamImageLabel.setIcon(webcamImageIcon);
		centerPanel.add(webcamImageLabel);

		JPanel contentPanel = new JPanel();
		LayoutManager borderLayout = new BorderLayout();
		
		contentPanel.setLayout(borderLayout);
		contentPanel.add(headerPanel, BorderLayout.PAGE_START) ;
		contentPanel.add(messageLabel, BorderLayout.PAGE_END) ;
		contentPanel.add(centerPanel, BorderLayout.CENTER) ;

		JFrame mainFrame = new JFrame();
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainFrame.setContentPane(contentPanel);
		mainFrame.pack();
		mainFrame.setResizable(false) ;
		mainFrame.setLocationRelativeTo(null) ;
		mainFrame.setTitle("Pan Tilt Face Tracker v1.0 (c) Craig Jackson 2013") ;
		mainFrame.setVisible(true);
	}

	
	private void panTiltTowards(int webcamWidth, int webcamHeight, Point p) {
		Point webcamCentre = new Point(webcamWidth / 2.0, webcamHeight / 2.0);
		Point delta = new Point(p.x - webcamCentre.x, p.y - webcamCentre.y);
		if(delta.x > webcamWidth / 10) // Only pan if we're more than 10% away from centre
			panLeft(DEFAULT_DELTA);
		else if(delta.x < -webcamWidth / 10) // Only pan if we're more than 10% away from centre
			panRight(DEFAULT_DELTA);
		if(delta.y > webcamHeight / 10) // Only tilt if we're more than 10% away from centre
			tiltDown(DEFAULT_DELTA);
		else if(delta.y < -webcamHeight / 10) // Only tilt if we're more than 10% away from centre
			tiltUp(DEFAULT_DELTA);
	}

	/**
	 * Make the robot move to the left.
	 */
	private void panLeft(int delta) {
		if(isXbeeTaskRunning())
			return;
		xbeeTask = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_PAN_LEFT, delta, usbPort, baudRate, messageLabel, cancelButton);
		xbeeTask.execute() ;
		log.info("Panning left") ;
	}
	
	/**
	 * Make the robot move to the right.
	 */
	private void panRight(int delta) {
		if(isXbeeTaskRunning())
			return;
		xbeeTask = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_PAN_RIGHT, delta, usbPort, baudRate, messageLabel, cancelButton);
		xbeeTask.execute() ;
		log.info("Panning right") ;
	}
	
	/**
	 * Make the robot move forward.
	 */
	private void tiltUp(int delta) {
		if(isXbeeTaskRunning())
			return;
		log.info("Tilting up") ;
		xbeeTask = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_TILT_UP, delta, usbPort, baudRate, messageLabel, cancelButton);
		xbeeTask.execute() ;
	}
	
	/**
	 * Make the robot move backwards.
	 */
	private void tiltDown(int delta) {
		if(isXbeeTaskRunning())
			return;
		log.info("Tilting down") ;
		xbeeTask = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_TILT_DOWN, delta, usbPort, baudRate, messageLabel, cancelButton);
		xbeeTask.execute() ;
	}

	/**
	 * Kill the previous command if it is still running.
	 */
	private boolean isXbeeTaskRunning() {
		if(xbeeTask != null && xbeeTask.isDone() == false) {
			return true;
		} else {
			xbeeTask = null;
			return false;
		}
	}
	private void selfTest() {
		killPreviousCommand() ;
		log.info("Testing...") ;
		xbeeTask = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_SELFTEST, 0, usbPort, baudRate, messageLabel, cancelButton);
		cancelButton.setEnabled(true) ;
		xbeeTask.execute() ;		
	}
	
	private void ping() {
		killPreviousCommand() ;
		log.info("Ping") ;
		xbeeTask = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_PING, 0, usbPort, baudRate, messageLabel, cancelButton);
		cancelButton.setEnabled(true) ;
		xbeeTask.execute() ;				
	}
	
	private void reset() {
		killPreviousCommand() ;
		log.info("Resetting controller...") ;
		xbeeTask = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_RESET, 0, usbPort, baudRate, messageLabel, cancelButton);
		cancelButton.setEnabled(true) ;
		xbeeTask.execute() ;		
	}
	
	private void killPreviousCommand() {
		if(xbeeTask != null && xbeeTask.isDone() == false) {
			xbeeTask.cancel(true) ;
		}
	}
	
	/**
	 * Detects the candidate USB ports for communicating with the robot via an attached XBee explorer.
	 * 
	 * @return a list of candidate USB ports.
	 */
	private String[] enumerateUsbPorts() {
		String[] usbPorts = {"/dev/ttyUSB0", "/dev/ttyUSB1", "/dev/ttyUSB2", "/dev/ttyUSB3", "/dev/ttyUSB4", "/dev/ttyUSB5", } ; 
		
		return usbPorts ;
	}
}
