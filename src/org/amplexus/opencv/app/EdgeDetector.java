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
import org.opencv.core.CvType;
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
 * Detects edges in a video stream. See http://docs.opencv.org/java/
 * 
 * Needs opencv-244.jar and libopencv_java246.so
 * Runs with -Djava.library.path=/path/to/lib (containing above .so file)
 */
public class EdgeDetector {
	
	private static final Logger log = Logger.getLogger(EdgeDetector.class);

	public static void main(String[] args) {

		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		System.out.println("Running DetectFaceDemo");
		JFrame frame = new JFrame();
		Mat webcamImage = new Mat();
		Mat edgesImage = new Mat();
		
		VideoCapture capture = new VideoCapture(-1);
		if (capture.isOpened()) {
			while (true) {
				capture.read(webcamImage);
				if (!webcamImage.empty()) {
					Imgproc.Canny(webcamImage, edgesImage, 200.0, 300.0);
					showResult(edgesImage);
				} else {
					System.out.println(" --(!) No captured frame -- Break!");
					break;
				}
			}
		}
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
	
	private Mat greyscaleToBinary(Mat greyscaleMat){

	    int size = (int) greyscaleMat.total() * greyscaleMat.channels();
	    double[] buf = new double[size];
	    greyscaleMat.get(0, 0, buf);

	    for(int i = 0; i < size; i++)
	    {
	        buf[i] = (buf[i] >= 0) ? 1 : 0;
	    }

	    Mat binaryMat = new Mat(greyscaleMat.size(), CvType.CV_8U);
	    binaryMat.put(0, 0, buf);
	    return binaryMat;
	}  
}
