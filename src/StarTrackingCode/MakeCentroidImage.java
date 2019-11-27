package StarTrackingCode;


import java.util.ArrayList;
import java.util.List;
import static org.bytedeco.javacpp.opencv_core.CV_8UC1;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.*;
import org.opencv.imgproc.Moments;

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
public class MakeCentroidImage {
    
    final private int width = 240;
    final private int height = 180;

    public MakeCentroidImage() 
    {
        
    }
    
    public Mat returnCentroid(Mat eventMat)
    {
        Mat heirarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        
        Imgproc.findContours(eventMat, contours, heirarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        Mat centroidMat =  Mat.zeros(180,240,CV_8UC1);
        for(int j = 0; j < contours.size(); j++)
        {
            Moments moments = Imgproc.moments(contours.get(j));
            Point centroid = new Point();
            centroid.x = moments.get_m10() / moments.get_m00();
            centroid.y = moments.get_m01() / moments.get_m00();
            if (centroid.x<=width && centroid.y<=height)
            {
                centroidMat.put(Math.toIntExact(Math.round(centroid.y)), Math.toIntExact(Math.round(centroid.x)), 255);
            }
        }
        /*
        contours = new ArrayList<>();
        Mat tempMat = centroidMat;
        int dilationSize = 15;
        Mat element = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2*dilationSize + 1, 2*dilationSize+1));
        Imgproc.dilate(tempMat, eventMat, element);
        Imgcodecs.imwrite("dilatedImage" + ".jpg", eventMat);
        Imgproc.findContours(eventMat, contours, heirarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        
        Mat centroidMat2 =  Mat.zeros(180,240,CV_8UC1);
        for (MatOfPoint contour : contours) {
            Moments moments = Imgproc.moments(contour);
            Point centroid = new Point();
            centroid.x = moments.get_m10() / moments.get_m00();
            centroid.y = moments.get_m01() / moments.get_m00();
            if (centroid.x<=width && centroid.y<=height)
            {
                centroidMat2.put(Math.toIntExact(Math.round(centroid.y)), Math.toIntExact(Math.round(centroid.x)), 255);
            }
        }
        
        
        tempMat = centroidMat2;
        Imgproc.dilate(tempMat, eventMat, element);
        Imgcodecs.imwrite("dilatedImage2" + ".jpg", eventMat);
        return centroidMat2;
        */return centroidMat;
    }
}
