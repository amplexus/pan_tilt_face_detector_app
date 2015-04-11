package org.amplexus.opencv.app;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.apache.log4j.Logger;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.video.*;

/*
 * Detects faces in an image, draws boxes around them, and writes the results
 * to "faceDetection.png".
 * 
 * Needs opencv-244.jar and libopencv_java246.so
 * Runs with -Djava.library.path=/path/to/lib (containing above .so file)
 */
public class FaceTracker {
	
	public static final String	DEFAULT_USBPORT		= "/dev/ttyUSB0" ;
	public static final int		DEFAULT_BAUD_RATE	= 9600 ;
	public static final int		DEFAULT_DELTA		= 5;

	// static final String CASCADE_CLASSIFIER_FILENAME = "/lbpcascade_frontalface.xml"; // Least expensive / least accurate
	static final String CASCADE_CLASSIFIER_FILENAME = "/haarcascade_frontalface_alt.xml"; // More expensive / more accurate
	// static final String CASCADE_CLASSIFIER_FILENAME = "/haarcascade_frontalface_default.xml";  // Most expensive / most accurate
	
    private static final Logger log = Logger.getLogger(FaceTracker.class);
	private static XBeeCommunicatorTask task = null;

	private static String usbPort = DEFAULT_USBPORT ;				// The current USB port we talk to the robot through
	private static int baudRate = DEFAULT_BAUD_RATE ;				// The current baud rate that we talk to the robot at

	private static JLabel messageLabel = new JLabel("Hello");
	private static JButton cancelButton = new JButton("Cancel");
	
	public static void main(String[] args) {

		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		System.out.println("Running DetectFaceDemo");
		CascadeClassifier faceDetector = new CascadeClassifier(FaceTracker.class.getResource(CASCADE_CLASSIFIER_FILENAME).getPath());
		JFrame frame = new JFrame();
		Mat webcamImage = new Mat();
//		for(int i = 0; i < 32; i++) {
//			VideoCapture vc = new VideoCapture(i);
//			if(vc.isOpened()) {
//				System.out.println("Got device: " + i);
//				vc.release();
//			}
//		}
//		if(true)
//			return;
		
		VideoCapture capture = new VideoCapture(-1);
		if (capture.isOpened()) {
			while (true) {
				capture.read(webcamImage);
				if (!webcamImage.empty()) {
					int webcamWidth = webcamImage.cols();
					int webcamHeight = webcamImage.rows();
					MatOfRect detectedFaces = new MatOfRect();
					faceDetector.detectMultiScale(webcamImage, detectedFaces); // As per http://en.wikipedia.org/wiki/Viola-Jones_object_detection_framework
//					System.out.println(String.format("Detected %s faces", faceDetections.toArray().length));
					Rect[] detectedFacesRectArray = detectedFaces.toArray();
					double biggestRect = 0;
					Rect biggestFaceRect = null;
					for (Rect faceRect : detectedFacesRectArray) {
						// determine biggest face - that will be the one we centre the camera on using the pan/tilt servos
						if(faceRect.area() > biggestRect) {
							biggestRect = faceRect.area();
							biggestFaceRect = faceRect;
						}
						Core.rectangle(webcamImage, new Point(faceRect.x, faceRect.y), new Point(faceRect.x + faceRect.width, faceRect.y + faceRect.height), new Scalar(0, 255, 0));
					}
					if(biggestFaceRect != null) {
						System.out.println("Detected face at: " + biggestFaceRect.x + "x" + biggestFaceRect.y + "y" + biggestFaceRect.width + "w" + biggestFaceRect.height + "h");
						Point centreOfFaceOnWebcam = new Point(biggestFaceRect.x + biggestFaceRect.width / 2, biggestFaceRect.y + biggestFaceRect.height / 2);
						panTiltTowards(webcamWidth, webcamHeight, centreOfFaceOnWebcam);
					}
					showResult(webcamImage);
				} else {
					System.out.println(" --(!) No captured frame -- Break!");
					break;
				}
			}
		}
	}

	private static void panTiltTowards(int webcamWidth, int webcamHeight, Point p) {
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

	static JFrame frame = null;
	static ImageIcon icon = null;
	public static void showResult(Mat imgMat) {
		
//		Imgproc.resize(imgMat, imgMat, new Size(640, 480)); // Mapping #0
		MatOfByte matOfByte = new MatOfByte();
		Highgui.imencode(".jpg", imgMat, matOfByte); // Mapping #1
		byte[] byteArray = matOfByte.toArray(); // Mapping #2
		BufferedImage bufImage = null;
		InputStream in = new ByteArrayInputStream(byteArray);
		try {
			bufImage = ImageIO.read(in); // Mapping #3
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(icon == null) {
			icon = new ImageIcon(bufImage);
		} else {
			icon.setImage(bufImage);
		}
		if(frame == null) {
			frame = new JFrame();
			frame.getContentPane().add(new JLabel(icon));
			frame.pack();
			frame.setVisible(true);
		} else {
			frame.repaint();
		}
	}
	
	/**
	 * Make the robot move to the left.
	 */
	private static void panLeft(int delta) {
		if(isXbeeTaskRunning())
			return;
		task = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_PAN_LEFT, delta, usbPort, baudRate, messageLabel, cancelButton);
		task.execute() ;
		log.info("Panning left") ;
	}
	
	/**
	 * Make the robot move to the right.
	 */
	private static void panRight(int delta) {
		if(isXbeeTaskRunning())
			return;
		task = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_PAN_RIGHT, delta, usbPort, baudRate, messageLabel, cancelButton);
		task.execute() ;
		log.info("Panning right") ;
	}
	
	/**
	 * Make the robot move forward.
	 */
	private static void tiltUp(int delta) {
		if(isXbeeTaskRunning())
			return;
		log.info("Tilting up") ;
		task = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_TILT_UP, delta, usbPort, baudRate, messageLabel, cancelButton);
		task.execute() ;
	}
	
	/**
	 * Make the robot move backwards.
	 */
	private static void tiltDown(int delta) {
		if(isXbeeTaskRunning())
			return;
		log.info("Tilting down") ;
		task = new XBeeCommunicatorTask(XBeeCommunicatorTask.CMD_TILT_DOWN, delta, usbPort, baudRate, messageLabel, cancelButton);
		task.execute() ;
	}

	/**
	 * Kill the previous command if it is still running.
	 */
	private static boolean isXbeeTaskRunning() {
		if(task != null && task.isDone() == false) {
			return true;
		} else {
			task = null;
			return false;
		}
	}
}
