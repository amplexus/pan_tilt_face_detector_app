package org.amplexus.opencv.app;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

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
public class DetectFaceDemo {
	
	//static final String CASCADE_CLASSIFIER_FILENAME = "/haarcascade_frontalface_alt.xml";
	static final String CASCADE_CLASSIFIER_FILENAME = "/lbpcascade_frontalface.xml";
	
	public static void main(String[] args) {

		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		System.out.println("Running DetectFaceDemo");
		CascadeClassifier faceDetector = new CascadeClassifier(DetectFaceDemo.class.getResource(CASCADE_CLASSIFIER_FILENAME).getPath());
		JFrame frame = new JFrame();
		Mat webcamImage = new Mat();
		VideoCapture capture = new VideoCapture(0);
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
//						System.out.println("Detected face at: " + faceRect.x + "x" + faceRect.y + "y" + faceRect.width + "w" + faceRect.height + "h");
						Core.rectangle(webcamImage, new Point(faceRect.x, faceRect.y), new Point(faceRect.x + faceRect.width, faceRect.y + faceRect.height), new Scalar(0, 255, 0));
					}
					if(biggestFaceRect != null) {
						Point p = findDeltaFromCentre(webcamWidth, webcamHeight, biggestFaceRect);
					}
					showResult(webcamImage);
				} else {
					System.out.println(" --(!) No captured frame -- Break!");
					break;
				}
			}
		}
	}

	private static Point findDeltaFromCentre(int webcamWidth, int webcamHeight, Rect faceRect) {
//		System.out.println("webcam dimensions=" + webcamWidth + ":" + webcamHeight);
		Point webcamCentre = new Point(webcamWidth / 2.0, webcamHeight / 2.0);
//		System.out.println("webcamCentre=" + webcamCentre.x + ":" + webcamCentre.y);
		Point faceCentre = new Point(faceRect.x + faceRect.width / 2.0, faceRect.y + faceRect.height / 2.0);
//		System.out.println("faceCentre=" + faceCentre.x + ":" + faceCentre.y);
		Point delta = new Point(webcamCentre.x - faceCentre.x, webcamCentre.y - faceCentre.y);
//		System.out.println("delta=" + delta.x + ":" + delta.y);
		return delta;
	}
	
	static JFrame frame = null;
	static JLabel messageLabel = null;
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
			
			messageLabel = new JLabel("Face Detector");
			//frame.getContentPane().add(messageLabel);
			frame.pack();
			frame.setVisible(true);
		} else {
			frame.repaint();
		}
	}
}
