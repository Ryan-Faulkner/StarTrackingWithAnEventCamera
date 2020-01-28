package StarTrackingCode;


import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Vector;
import static org.bytedeco.javacpp.opencv_core.CV_64F;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/*
 * Copyright (C) 2019 ryanj.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

/**
 *
 * @author ryanj
 *
 *
 *
 *    This code is a bit of a mess, it is not part of the main program, and instead a separate function I wrote to determine the calibration matrix of the camera I was using.
 *
 *
 *
 *
 *
 */
public class Calibrator {
    Calibrator()
    {
    }
    public static void main(String[] args) throws FileNotFoundException
    {
        int imCounter = 1;
        int width = 240;
        int height = 180;
        double [][] undistortMapX;
        double [][] undistortMapY;
        undistortMapX = new double[height][width];
        undistortMapY = new double[height][width];
        Scanner scannerx = new Scanner(new File("C:\\Users\\ryanj\\Documents\\Honours Stuff\\undistort_map_x.txt"));
        Scanner scannery = new Scanner(new File("C:\\Users\\ryanj\\Documents\\Honours Stuff\\undistort_map_y.txt"));
        for(int i = 0; i < height; i++)
        {
            for(int j = 0; j < width; j++)
            {
                undistortMapX[i][j] = scannerx.nextDouble();
                undistortMapY[i][j] = scannery.nextDouble();
            }
        }
        Size patternSize = new Size();
        patternSize.height = 11;
        patternSize.width = 4;
        System.load("C:\\Users\\ryanj\\Downloads\\opencv\\build\\java\\x64\\opencv_java320.dll");
        
        File[] files = new File("C:\\Users\\ryanj\\Documents\\jaer-master\\starTrackingBuiltOnJAER\\eventImagesFinal").listFiles();
        
        List<Point3> corners = new ArrayList();
        for( int i = 0; i < patternSize.height; i++ )
        {
            for( int j = 0; j < patternSize.width; j++ )
            {
                int squareSize = 128;
                corners.add(new Point3((2*j + i % 2)*squareSize, i*squareSize,0));
            }
        }
        MatOfPoint3f cornerMat = new  MatOfPoint3f();
        cornerMat.fromList(corners);
        
        List<Mat>objectPoints = new ArrayList<Mat>();
        List<Mat>imagePoints = new ArrayList<Mat>();
        for (File tfile : files) 
        {
          File[] tempfiles = tfile.listFiles();
          for(File file : tempfiles)
          {
            MatOfPoint2f pointBuf = new MatOfPoint2f();
               Point p = new Point(-1,-1);
              Mat currentImage = Imgcodecs.imread(file.getAbsolutePath(), Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);

              /*Mat processedEventMat = currentImage;
              Mat eventMat = Mat.zeros(180,240,CV_8UC1);
              for(int a = 0; a < width; a++)
              {
                  for(int b = 0; b < height; b++)
                  {
                      int newCol = (int) undistortMapX[b][a];
                      int newRow = (int) undistortMapY[b][a];
                      if(newCol>0 && newCol<= width && newRow>0 && newRow<= height)
                      {
                          eventMat.put(newRow, newCol, processedEventMat.get(b,a));
                      }
                  }
              }
              currentImage = eventMat;*/
              Mat eventMat = currentImage;
              //Imgproc.filter2D(currentImage,currentImage,-1,kernel,p,0,BORDER_REPLICATE);
              //Imgproc.threshold(currentImage, currentImage, 3.0, 0, Imgproc.THRESH_TOZERO);
              Mat kernel = Mat.ones(2,2, CvType.CV_32F);
              Imgproc.dilate(currentImage, currentImage, kernel);
              
              Imgproc.erode(currentImage, currentImage, kernel);
              Imgproc.erode(currentImage, currentImage, kernel);
              Imgproc.dilate(currentImage, currentImage, kernel);
              Imgproc.dilate(currentImage, currentImage, kernel);
              Imgproc.dilate(currentImage, currentImage, kernel); 
              Imgproc.dilate(currentImage, currentImage, kernel);
              // Imgproc.filter2D(eventMat,eventMat,-1,kernel,p,0,BORDER_REPLICATE);
              // Imgproc.threshold(eventMat, eventMat, 3.0, 0, Imgproc.THRESH_TOZERO);
              currentImage = eventMat;
              //dilate, erode, erode, dilate, dilate, dilate is 4
              //with everything 6
              //now without initial dilate/erode : ALSO 4 

              Mat resizeimage = new Mat();
              Size sz = currentImage.size();
              sz.height = sz.height*1.5;
              sz.width = sz.width*1.5;
              Imgproc.resize( currentImage, resizeimage, sz );
              Mat invertcolormatrix= new Mat(currentImage.rows(),currentImage.cols(), currentImage.type(), new Scalar(255,255,255));

              Core.subtract(invertcolormatrix, currentImage, currentImage);
              boolean found = Calib3d.findCirclesGrid(currentImage,patternSize , pointBuf, Calib3d.CALIB_CB_ASYMMETRIC_GRID);

              if ( found)                // If done with success,
              {
                  System.out.println("SUCCESS");
                  // improve the found corners' coordinate accuracy for chessboard

                    // Draw the corners.
                  Imgproc.cvtColor(currentImage, currentImage, Imgproc.COLOR_GRAY2BGR);
                    Calib3d.drawChessboardCorners(currentImage, patternSize, pointBuf, found );
                  //Imgcodecs.imwrite("eventImagesSuccess/image" + imCounter + ".jpg", currentImage);
                  imagePoints.add(pointBuf);
                  objectPoints.add(cornerMat);
              }
              else
              {
                  //Imgcodecs.imwrite("eventImagesFail/image" + imCounter + ".jpg", currentImage);
              }
              imCounter++;
          }
        }
        Mat cameraMatrix = Mat.eye(3, 3, CV_64F);
        Mat distCoeffs = Mat.zeros(8, 1, CV_64F);
        
        Vector<Mat> rvecs = new Vector(), tvecs = new Vector();
        double error = Calib3d.calibrateCamera(objectPoints, imagePoints, patternSize,cameraMatrix, distCoeffs, rvecs, tvecs);
        
        System.out.print(cameraMatrix.get(0, 0)[0]);
        System.out.print(" ");
        System.out.print(cameraMatrix.get(0, 1)[0]);
        System.out.print(" ");
        System.out.println(cameraMatrix.get(0, 2)[0]);
        System.out.print(cameraMatrix.get(1, 0)[0]);
        System.out.print(" ");
        System.out.print(cameraMatrix.get(1, 1)[0]);
        System.out.print(" ");
        System.out.println(cameraMatrix.get(1, 2)[0]);
        System.out.print(cameraMatrix.get(2, 0)[0]);
        System.out.print(" ");
        System.out.print(cameraMatrix.get(2, 1)[0]);
        System.out.print(" ");
        System.out.println(cameraMatrix.get(2, 2)[0]);
        
        System.out.println("and now stats");
        System.out.println(error);
        
        //still need to add flags "The final argument is the flag. You need to specify here options 
        //like fix the aspect ratio for the focal length, assume zero tangential distortion or to fix the principal point."
        //I'm too tired to work out if I need any or all of these so that's a tomorrow job
        /*
        System.load("C:\\Users\\ryanj\\Downloads\\opencv\\build\\java\\x64\\opencv_java320.dll");
        File image = new File("C:/Users/ryanj/Pictures/normal.jpg");
        Mat currentImage = Imgcodecs.imread(image.getAbsolutePath(), Imgcodecs.CV_LOAD_IMAGE_ANYCOLOR);
        
    
        int width = 240;
        int height = 180;
        double [][] undistortMapX;
        double [][] undistortMapY;
        undistortMapX = new double[height][width];
        undistortMapY = new double[height][width];
        Scanner scannerx = new Scanner(new File("C:\\Users\\ryanj\\Documents\\Honours Stuff\\undistort_map_x.txt"));
        Scanner scannery = new Scanner(new File("C:\\Users\\ryanj\\Documents\\Honours Stuff\\undistort_map_y.txt"));
        for(int i = 0; i < height; i++)
        {
            for(int j = 0; j < width; j++)
            {
                undistortMapX[i][j] = scannerx.nextDouble();
                undistortMapY[i][j] = scannery.nextDouble();
            }
        }
        Mat processedEventMat = currentImage;
        Mat eventMat = Mat.zeros(180,240,CV_8UC1);
        for(int a = 0; a < width; a++)
        {
            for(int b = 0; b < height; b++)
            {
                int newCol = (int) undistortMapX[b][a];
                int newRow = (int) undistortMapY[b][a];
                if(newCol>0 && newCol<= width && newRow>0 && newRow<= height)
                {
                    eventMat.put(newRow, newCol, processedEventMat.get(b,a));
                }
            }
        }
        Imgcodecs.imwrite("C:\\Users\\ryanj\\Pictures\\undistorted.jpg", eventMat);*/
        
    }
}
