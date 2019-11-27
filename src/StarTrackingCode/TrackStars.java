package StarTrackingCode;


//import edu.wlu.cs.levy.CG.KeyDuplicateException;
//import edu.wlu.cs.levy.CG.KeySizeException;
import de.biomedical_imaging.edu.wlu.cs.levy.CG.KeyDuplicateException;
import de.biomedical_imaging.edu.wlu.cs.levy.CG.KeySizeException;
import java.awt.Color;
import static java.lang.Math.asin;
import static java.lang.Math.atan2;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import static org.bytedeco.javacpp.opencv_core.CV_64F;
import static org.bytedeco.javacpp.opencv_core.CV_8UC1;
import static org.bytedeco.javacpp.opencv_core.CV_8UC3;
import static org.bytedeco.javacpp.opencv_core.CV_PI;
import static org.bytedeco.javacpp.opencv_imgproc.CV_THRESH_TOZERO;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.utils.Converters;

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
 */
public class TrackStars {
        //firstly there's offset stuff in OG code which seem to be just to prevent wasting CPU time when there are no stars visible
        //I'm just gonna assume there's always stars visible for now because that's easier/probably true if camera is facing sky at night
    Mat[] past12;
    Mat[] past12Cent;
    int imageCount;
    int nearest = 0;
    int farthest = 5;
    boolean lastUsed[][][];
    int pointsVar = 50; //variance of the spread of the points in the event Image
    final private int width = 240;
    final private int height = 180;
    ticp ticper;
    double roll;
    double pitch;
    double yaw;
    int imCount;
    generateCorrespondences generator;
    RotAvg averager;
    public Rotation currentPose;
    public Rotation currentPose2;
    public TrackStars()
    { 
        averager = new RotAvg();
        counterer = 0;
       past12 = new Mat[12];
       past12Cent = new Mat[12];
       lastUsed = new boolean [256][256][256];
       ticper = new ticp();
       generator = new generateCorrespondences();
       roll = 0;
       pitch = 0;
       yaw = 0; 
       imCount = 0;
        imageCount = 0;
        currentPose = new Rotation(1,0,0,0,false);
        currentPose2 = new Rotation(1,0,0,0,false);
       
    }
    //copied form opencv C++ library as can't find it in Java version
    

// Converts a given Rotation Matrix to Euler angles
// Convention used is Y-Z-X Tait-Bryan angles
    //except convention I see is X-Z-Y if Matlab is to be believed - work out which is which later
// Reference code implementation:
// https://www.euclideanspace.com/maths/geometry/rotations/conversions/matrixToEuler/index.htm
    Mat rot2euler( Mat rotationMatrix)
    {
        Mat euler = new Mat(3,1,CV_64F);
        double m00 = rotationMatrix.get(0,0)[0];
        double m02 = rotationMatrix.get(0,2)[0];
        double m10 = rotationMatrix.get(1,0)[0];
        double m11 = rotationMatrix.get(1,1)[0];
        double m12 = rotationMatrix.get(1,2)[0];
        double m20 = rotationMatrix.get(2,0)[0];
        double m22 = rotationMatrix.get(2,2)[0];

        double bank, attitude, heading;

        // Assuming the angles are in radians.
        if (m10 > 0.998) { // singularity at north pole
            bank = 0;
            attitude = CV_PI/2;
            heading = atan2(m02,m22);
            //System.out.println("1");
        }
        else if (m10 < -0.998) { // singularity at south pole
            bank = 0;
            attitude = -CV_PI/2;
            heading = atan2(m02,m22);
            //System.out.println("2");
        }
        else
        {
            bank = atan2(-m12,m11);
            attitude = asin(m10);
            heading = atan2(-m20,m00);
            //System.out.println(m10);
            //System.out.println(attitude);
            //System.out.println("3");
        }
        euler.put(0,0,bank);
        euler.put(1,0,attitude);
        euler.put(2,0,heading);
        return euler;
        
    }
    
    int counterer;
    public Mat track(Mat centroid) throws KeySizeException, KeyDuplicateException// throws KeySizeException, KeyDuplicateException
    {
        
        Imgproc.threshold(centroid, centroid, 200, CV_THRESH_TOZERO, 0);
        //Imgcodecs.imwrite("testData/centroidImage" + counterer +".png", centroid);
        
        counterer++;
        Mat heirarchy = new Mat();
        Mat eventMat = centroid.clone();
        List<MatOfPoint> contours = new ArrayList<>();
        Mat tempMat = eventMat;
        int dilationSize = 15;
        Mat element = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2*dilationSize + 1, 2*dilationSize+1));
        Imgproc.dilate(tempMat, eventMat, element);
        Imgproc.findContours(eventMat, contours, heirarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        
        Mat centroidMat2 =  Mat.zeros(180,240,CV_8UC1);
        for (MatOfPoint contour : contours) {
            Moments moments = Imgproc.moments(contour);
            Point centroidPoint = new Point();
            centroidPoint.x = moments.get_m10() / moments.get_m00();
            centroidPoint.y = moments.get_m01() / moments.get_m00();
            if (centroidPoint.x<=width && centroidPoint.y<=height)
            {
                centroidMat2.put(Math.toIntExact(Math.round(centroidPoint.y)), Math.toIntExact(Math.round(centroidPoint.x)), 255);
            }
        }
        
        boolean [][][] used= new boolean[256][256][256];
        Mat coloured = new Mat(centroid.height(), centroid.width(), CV_8UC3, new Scalar(0, 0, 0));
        Mat realColoured = new Mat(centroid.height(), centroid.width(), CV_8UC3, new Scalar(0, 0, 0));
        Mat predictions = new Mat(centroid.height(), centroid.width(), CV_8UC3, new Scalar(0, 0, 0));
        //
        //if (number of events in eventImage is above APC threshold):
        //
        tempMat = new Mat();
        //System.out.println(tempMat);
        Core.findNonZero(centroid, tempMat);
        //Imgcodecs.imwrite("testData\\artificial data\\wtf" + ".jpg", centroid);
        /*System.out.println(tempMat.get(0,0)[0] + "  " + tempMat.get(0,0)[1]);
        System.out.println("first is " + centroid.get((int)tempMat.get(0, 0)[1],(int)tempMat.get(0, 0)[0])[0]);
        System.out.println("first is " + centroid.get((int)tempMat.get(0, 0)[1],(int)tempMat.get(0, 0)[0])[0]);
        System.out.println(tempMat);*/
        if(tempMat.dims() == 0 || tempMat.empty())
        {
            //no matches can be found so uhh no matches
            if(past12[0] != null)
            {
                return past12[0];            
            }
            else
            {
                return coloured;
            }
        }
        MatOfPoint tempMapPoint = new MatOfPoint(tempMat);
        List<Point> locationsNew = new ArrayList<Point>();
        Converters.Mat_to_vector_Point(tempMapPoint,locationsNew);
            //System.out.println("LNew is : " + locationsNew);
        
        Core.findNonZero(centroidMat2, tempMat);
        if(tempMat.dims() == 0 || tempMat.empty())
        {
            //no matches can be found so uhh no matches
            if(past12[0] != null)
            {
                return past12[0];            
            }
            else
            {
                return coloured;
            }
        }
        tempMapPoint = new MatOfPoint(tempMat);
        List<Point> locationsNewDilated = new ArrayList<Point>();
        Converters.Mat_to_vector_Point(tempMapPoint,locationsNewDilated);
        
        boolean [][] matched = new boolean[centroid.height()][centroid.width()];
        Mat [] relRs = new Mat[farthest-nearest];
        for(int i = nearest; i < farthest; i++)
        {
            //vset code goes here I'm also skipping cause not gonna be easy to turn into java
           
            if(past12[i] == null)
            {
                continue;
            }
            else
            {
                //System.out.println("ALL G");
            }
            //System.out.println(past12Cent[i]);
            tempMat = new Mat();
            Core.findNonZero(past12Cent[i], tempMat);
            if(tempMat.dims() == 0 || tempMat.empty())
            {
                continue;
            }
            tempMapPoint = new MatOfPoint(tempMat);
            List<Point> locationsOld = new ArrayList<Point>();
            Converters.Mat_to_vector_Point(tempMapPoint,locationsOld);
            
            
            eventMat = past12Cent[i].clone();
            tempMat = eventMat;
            Imgproc.dilate(tempMat, eventMat, element);
            //Imgcodecs.imwrite("dilatedImage" + ".jpg", eventMat);
            
            contours = new ArrayList<>();
            Imgproc.findContours(eventMat, contours, heirarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            Mat centroidMat3 =  Mat.zeros(180,240,CV_8UC1);
            for (MatOfPoint contour : contours) {
                Moments moments = Imgproc.moments(contour);
                Point centroidPoint = new Point();
                centroidPoint.x = moments.get_m10() / moments.get_m00();
                centroidPoint.y = moments.get_m01() / moments.get_m00();
                if (centroidPoint.x<=width && centroidPoint.y<=height)
                {
                    centroidMat3.put(Math.toIntExact(Math.round(centroidPoint.y)), Math.toIntExact(Math.round(centroidPoint.x)), 255);
                }
            }
            
            Core.findNonZero(centroidMat3, tempMat);
            if(tempMat.dims() == 0 || tempMat.empty())
            {
                continue;
            }
            tempMapPoint = new MatOfPoint(tempMat);
            List<Point> locationsOldDilated = new ArrayList<Point>();
            Converters.Mat_to_vector_Point(tempMapPoint,locationsOldDilated);
            //get variance
            double meanOldX = 0.0;            
            double meanOldY = 0.0;
            double meanNewX = 0.0;            
            double meanNewY = 0.0;
            for(int j = 0; j < locationsOld.size(); j++)
            {
                meanOldX += locationsOld.get(j).x;
                meanOldY += locationsOld.get(j).y;
            }
            meanOldX = meanOldX / locationsOld.size(); 
            meanOldY = meanOldY / locationsOld.size(); 
            for(int j = 0; j < locationsNew.size(); j++)
            {
                meanNewX += locationsNew.get(j).x;
                meanNewY += locationsNew.get(j).y;
            }
            meanNewX = meanNewX / locationsNew.size();
            meanNewY = meanNewY / locationsNew.size();
            
            double varOldX = 0.0;            
            double varOldY = 0.0;
            double varNewX = 0.0;            
            double varNewY = 0.0;
            for(int j = 0; j < locationsOld.size(); j++)
            {
                varOldX += (locationsOld.get(j).x - meanOldX)*(locationsOld.get(j).x - meanOldX);
                varOldY += (locationsOld.get(j).y - meanOldY)*(locationsOld.get(j).y - meanOldY);
            }
            varOldX = varOldX / (locationsOld.size()-1); 
            varOldY = varOldY / (locationsOld.size()-1); 
            for(int j = 0; j < locationsNew.size(); j++)
            {
                varNewX += (locationsNew.get(j).x - meanNewX)*(locationsNew.get(j).x - meanNewX);
                varNewY += (locationsNew.get(j).y - meanNewY)*(locationsNew.get(j).y - meanNewY);
            }
            if(past12[i] == null)
            {
                System.out.println("WATGA333333F");
            }
            varNewX = varNewX / (locationsNew.size()-1); 
            varNewY = varNewY / (locationsNew.size()-1);
            if(true) //varOldX > pointsVar && varNewX > pointsVar && varOldY > pointsVar && varNewY > pointsVar
            {
                
                int connectedCount = 0;
                //System.out.println(locationsOld);
                Mat tform = ticper.tform(locationsOld, locationsNew);
                //Imgcodecs.imwrite("testData/results/centroidImage" + counterer +".jpg", centroid);
                /*System.out.println("It is:" + tform.get(0,0).length);
                System.out.print(tform.get(0, 0)[0]);
                System.out.print(" ");
                System.out.print(tform.get(0, 1)[0]);
                System.out.print(" ");
                System.out.println(tform.get(0, 2)[0]);
                System.out.print(tform.get(1, 0)[0]);
                System.out.print(" ");
                System.out.print(tform.get(1, 1)[0]);
                System.out.print(" ");
                System.out.println(tform.get(1, 2)[0]);
                System.out.print(tform.get(2, 0)[0]);
                System.out.print(" ");
                System.out.print(tform.get(2, 1)[0]);
                System.out.print(" ");
                System.out.println(tform.get(2, 2)[0]);*//*
                
                if(i == nearest)
                {
                    Mat eulerr;
                    //System.out.println("Angles");
                    eulerr = rot2euler(tform);
                    //System.out.println(eulerr.get(0,0)[0]);
                    //System.out.println(eulerr.get(1,0)[0]);
                    //System.out.println(eulerr.get(2,0)[0]);
                    if(eulerr.get(0, 0)[0] < Math.PI/2 && eulerr.get(1, 0)[0] < Math.PI/2 && eulerr.get(2, 0)[0] < Math.PI/2)
                    {
                        pitch += eulerr.get(0, 0)[0];
                        roll += eulerr.get(1, 0)[0];
                        yaw += eulerr.get(2, 0)[0];
                    }
                        /*
                    System.out.println("roll " + roll);
                    System.out.println("pitch " + pitch);
                    System.out.println("yaw " + yaw);
                    System.out.println();*//*
                }
                */
                relRs[i] = tform;
                /*
                
                
                Mat angles = new Mat();
                Calib3d.Rodrigues(tform, angles);
                double theta = Math.sqrt(Math.pow(angles.get(0,0)[0],2) + Math.pow(angles.get(1,0)[0],2) + Math.pow(angles.get(2,0)[0],2));
                Mat actualAngles = new Mat();
                Core.multiply(angles, new Scalar(1/theta), actualAngles);
                
                System.out.println("Angle is: " + theta);
                
                for(int counter = 0; counter < actualAngles.size().height; counter++)
                {
                    System.out.println(actualAngles.get(counter,0)[0]);
                }*/
               /* Mat K = new Mat(3,3,CV_32FC1);
                K.put(0, 0, 246.43059488719612);
                K.put(0, 1, 0);
                K.put(0, 2, 103.08424033768227);
                K.put(1, 0, 0);
                K.put(1, 1, 245.6414223852361);
                K.put(1, 2, 77.55187058195787);
                K.put(2, 0, 0);
                K.put(2, 1, 0);
                K.put(2, 2, 1);*/
                //System.out.println("rot" + rotations.isContinuous());
                //System.out.println("tra" + translations.isContinuous());
                //System.out.println("n" + normals.isContinuous());
                //transformed prev = transformForward(prev);
                
                /*
                Imgcodecs.imwrite("debugImages/" + 
                generator.imageCount + "past.jpg", past12Cent[i]);
                Imgcodecs.imwrite("debugImages/" + 
                generator.imageCount + "current.jpg", centroid);
                //*/
                
                //don't need connections if just showing on screen the thing lol
                
                List<Point> transformedOld = generator.generate(locationsOldDilated,locationsNewDilated,tform);
                boolean [] connected= new boolean[transformedOld.size()];
                //I need my colouring to be better.
                //lets make it simple and just compare to last frame, should work now I only have 1 dot for each star
                //better idea, lets get mean distance between matches and say if it's more than double the mean don't count it.
                for(int counter = 0; counter < transformedOld.size(); counter+=2)
                {
                    if(!matched[(int) transformedOld.get(counter+1).y][(int)  transformedOld.get(counter+1).x])
                    {
                        double[] tempPixel = past12[i].get((int) transformedOld.get(counter).y,(int)  transformedOld.get(counter).x);
                        if(tempPixel != null)
                        {
                            /*if(lastUsed[(int)tempPixel[0]][(int)tempPixel[1]][(int)tempPixel[2]] == false)
                            {
                                used[(int)tempPixel[0]][(int)tempPixel[1]][(int)tempPixel[2]] = true;
                                counter++;
                                continue;
                            }*/
                            if(used[(int)tempPixel[0]][(int)tempPixel[1]][(int)tempPixel[2]] == true)
                            {
                                //counter++;
                                continue;
                            }
                            if(past12[i].get((int) transformedOld.get(counter).y,(int)  transformedOld.get(counter).x)[0] != 0 || past12[i].get((int) transformedOld.get(counter).y,(int)  transformedOld.get(counter).x)[1] != 0 || past12[i].get((int) transformedOld.get(counter).y,(int)  transformedOld.get(counter).x)[2] != 0)
                            {
                                connectedCount++;
                                connected[counter] = true;
                                used[(int)tempPixel[0]][(int)tempPixel[1]][(int)tempPixel[2]] = true;
                                matched[(int) transformedOld.get(counter+1).y][(int)  transformedOld.get(counter+1).x] = true;
                                double[] pixel = new double[3];
                                pixel[0] = tempPixel[0];
                                pixel[1] = tempPixel[1];
                                pixel[2] = tempPixel[2];
                               // System.out.printf("Y and X are %d %d %n",(int) transformedOld.get(counter).y,(int)  transformedOld.get(counter).x);
                                coloured.put((int) transformedOld.get(counter+1).y,(int)  transformedOld.get(counter+1).x, pixel);
                                realColoured.put((int) transformedOld.get(counter+1).y,(int)  transformedOld.get(counter+1).x, pixel);
                                /*if((int) transformedOld.get(counter+2).y < centroid.height() && (int) transformedOld.get(counter+2).y > 0 &&
                                        (int) transformedOld.get(counter+2).x < centroid.width() && (int) transformedOld.get(counter+2).x> 0)
                                {
                                    predictions.put((int) transformedOld.get(counter+2).y,(int)  transformedOld.get(counter+2).x, pixel);                                
                                }*/
                                
                            }
                            else
                            {
                                //System.out.printf("X and Y are %d %d %n",(int) transformedOld.get(counter).x,(int)  transformedOld.get(counter).y);
                                if(!connected[counter])
                                {
                                    connected[counter] = true;
                                    double[] pixel = new double[3];
                                    Random random = new Random();
                                    float hue = random.nextFloat();
                                    float saturation = 0.9f;//1.0 for brilliant, 0.0 for dull
                                    float luminance = 1.0f; //1.0 for brighter, 0.0 for black
                                    Color color = Color.getHSBColor(hue, saturation, luminance);
                                    pixel[0] = color.getRed();
                                    pixel[1] = color.getBlue();
                                    pixel[2] = color.getGreen();
                                   //System.out.println((int) transformedOld.get(counter).x);
                                    coloured.put((int) transformedOld.get(counter+1).y,(int)  transformedOld.get(counter+1).x, pixel);
                                }

                            }
                        }
                        else
                        {
                            if(!connected[counter])
                            {
                                //tem.out.printf("X andASF Y are %d %d %n",(int) transformedOld.get(counter).x,(int)  transformedOld.get(counter).y);

                                connected[counter] = true;
                                double[] pixel = new double[3];
                                Random random = new Random();
                                float hue = random.nextFloat();
                                float saturation = 0.9f;//1.0 for brilliant, 0.0 for dull
                                float luminance = 1.0f; //1.0 for brighter, 0.0 for black
                                Color color = Color.getHSBColor(hue, saturation, luminance);
                                pixel[0] = color.getRed();
                                pixel[1] = color.getBlue();
                                pixel[2] = color.getGreen();
                                coloured.put((int) transformedOld.get(counter+1).y,(int)  transformedOld.get(counter+1).x, pixel);
                            }

                        }
                    }
                }
                //System.out.println(connectedCount-transformedOld.size()/2);
            }
            
            
             
       }
        if(relRs[0] != null)
        {
            averager.addRot(relRs);
            currentPose2 = averager.noAvgPose;
           currentPose = averager.getAverage();
        }
       lastUsed = used;
       addImage(coloured,centroid);
       imageCount++;
       //Imgcodecs.imwrite("debugImages/predictions" + imageCount + ".jpg", predictions);
       return realColoured;
    }
    
    
    
    void addImage(Mat newImage, Mat newCentroid)
    {
        Mat temp = newImage.clone();
        Mat temp2 = newImage;
        Mat temp3 = newCentroid.clone();
        Mat temp4 = newCentroid;
        
        for (int i = 0; i < 11; i++)
        {
            temp2 = past12[i];
            past12[i] = temp;
            
            temp = temp2;
            temp4 = past12Cent[i];
            past12Cent[i] = temp3;
            temp3 = temp4;
        }
        past12[11] = temp;
        past12Cent[11] = temp3;
    }
}
