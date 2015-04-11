package org.amplexus.opencv.app;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.objdetect.CascadeClassifier;

public class WebcamTask extends SwingWorker<Void, Mat> {

	static final String CASCADE_CLASSIFIER_FILENAME = "/lbpcascade_frontalface.xml"; // Least expensive / least accurate
	// static final String CASCADE_CLASSIFIER_FILENAME = "/haarcascade_frontalface_alt.xml"; // More expensive / more accurate
	// static final String CASCADE_CLASSIFIER_FILENAME = "/haarcascade_frontalface_default.xml";  // Most expensive / most accurate

	CascadeClassifier faceDetector = null;
	VideoCapture capture = null;
	JLabel webcamImageLabel = null;
	long pauseMillis = 0;
	
	public WebcamTask(VideoCapture vc, long pauseMillis, JLabel webcamImageLabel) {
		this.capture = vc;
//		this.capture = new VideoCapture(-1);
		this.pauseMillis = pauseMillis;
		this.webcamImageLabel = webcamImageLabel;
		this.faceDetector = new CascadeClassifier(FaceTracker.class.getResource(CASCADE_CLASSIFIER_FILENAME).getPath());
	}

	@Override
	protected Void doInBackground() throws Exception {
		if (capture.isOpened()) {
			while(!isCancelled()) {
				Mat webcamImage = new Mat();
				capture.read(webcamImage);
				if(!webcamImage.empty()) {
		        	int webcamWidth = webcamImage.cols();
		    		int webcamHeight = webcamImage.rows();
		    		MatOfRect detectedFaces = new MatOfRect();
		    		// The following line causes crash on exit for some reason
		    		faceDetector.detectMultiScale(webcamImage, detectedFaces); // As per http://en.wikipedia.org/wiki/Viola-Jones_object_detection_framework
		    		double biggestRect = 0;
		    		Rect biggestFaceRect = null;
		    		Rect[] detectedFacesRectArray = detectedFaces.toArray();
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
//		    			panTiltTowards(webcamWidth, webcamHeight, centreOfFaceOnWebcam);
		    		}
		
//		        	MatOfByte matOfByte = new MatOfByte();
//		    		System.out.println("imencoding...");
//		    		Highgui.imencode(".jpg", webcamImage, matOfByte); // Mapping #1
//		    		byte[] byteArray = matOfByte.toArray(); // Mapping #2
//		    		System.out.println("converted to array...");
//		    		BufferedImage bufImage = null;
//		    		InputStream in = new ByteArrayInputStream(byteArray);
//		    		try {
//		    			bufImage = ImageIO.read(in); // Mapping #3
//		    			publish(bufImage);
//		    		} catch(IOException e) {
//		    			e.printStackTrace();		    			
//		    		}
		    		
	    			System.out.println("Publishing image...");
	            	publish(webcamImage);
				}
				if(pauseMillis > 0) {
					try {
						Thread.sleep(pauseMillis);
					} catch (InterruptedException e) {
					}
				}
			}
		}
		return null;
	}
	
	/**
	 * Invoked on the event dispatcher thread	
	 */
    @Override
    protected void process(List<Mat> chunks) {
    	Iterator<Mat> i = chunks.iterator();
//    	while(i.hasNext()) {
    	if(i.hasNext()) { // don't want to fall behind, so just process the first one!
        	Mat webcamImage = i.next();
        	renderImage(webcamImage);
        	System.out.println("Published image: " + webcamImage.toString());

    	}
    }
    
	private void renderImage(Mat webcamImage) {
    	MatOfByte matOfByte = new MatOfByte();
		Highgui.imencode(".jpg", webcamImage, matOfByte); // Mapping #1
		System.out.println("imencoded...");
		byte[] byteArray = matOfByte.toArray(); // Mapping #2
		System.out.println("converted to array...");
		BufferedImage bufImage = null;
		InputStream in = new ByteArrayInputStream(byteArray);
		try {
			bufImage = ImageIO.read(in); // Mapping #3
			ImageIcon icon = (ImageIcon) webcamImageLabel.getIcon();
			icon.setImage(bufImage);
			webcamImageLabel.repaint();
			System.out.println("Repainted...");
		} catch (IOException e) {
			e.printStackTrace();
		}    		
	}

	@Override
	protected void done() {
		System.out.println("done");
	}
}
