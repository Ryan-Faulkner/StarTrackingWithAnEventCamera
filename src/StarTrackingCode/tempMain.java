package StarTrackingCode;


//import edu.wlu.cs.levy.CG.KeyDuplicateException;
//import edu.wlu.cs.levy.CG.KeySizeException;
import StarTrackingCode.TrackStars;
import StarTrackingCode.transformForward;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.bytedeco.javacpp.opencv_core.CV_32FC1;
import static org.bytedeco.javacpp.opencv_core.CV_8UC1;
import static org.bytedeco.javacpp.opencv_core.CV_8UC3;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
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
public class tempMain {
    
    public static void main(String[] args) throws IOException//, KeySizeException, KeyDuplicateException
    {
        System.load("C:\\Users\\ryanj\\Downloads\\opencv\\build\\java\\x64\\opencv_java320.dll");
        //Mat newImage = Imgcodecs.imread("C:\\Users\\ryanj\\Documents\\jaer-master\\starTrackingBuiltOnJAER\\testData\\artificial data\\centroidImage1191.png", CV_8UC1);
        //System.out.println(oldImage);
        Mat oldImage = Imgcodecs.imread("C:\\Users\\ryanj\\Documents\\jaer-master\\starTrackingBuiltOnJAER\\testData\\artificial data\\centroidImage1192.png", CV_8UC1);
        TrackStars tracker = new TrackStars();
        //Mat firstImage = tracker.track(oldImage);
        //Mat secondImage = tracker.track(newImage);
        Mat tform = Mat.zeros(new Size(3,3), CV_32FC1);
        /*tform.put(0, 0,1);
        tform.put(0, 1, 0);
        tform.put(0, 2,0);
        tform.put(1, 0, 0);
        tform.put(1, 1, 1 );
        tform.put(1, 2,0);
        tform.put(2, 0, 0);
        tform.put(2, 1, 0);
        tform.put(2, 2,1);/*
        tform.put(0, 0, 0.923879532511287);
        tform.put(0, 1, -0.382683432365090 );
        tform.put(0, 2,0);
        tform.put(1, 0, 0.382683432365090);
        tform.put(1, 1, 0.923879532511287 );
        tform.put(1, 2,0);
        tform.put(2, 0, 0);
        tform.put(2, 1, 0);
        tform.put(2, 2,1);
        */
        tform.put(0, 0, 0.923879532511287);
        tform.put(0, 1, 0);
        tform.put(0, 2,0.382683432365090);
        tform.put(1, 0, 0);
        tform.put(1, 1, 1);
        tform.put(1, 2, 0);
        tform.put(2, 0, -0.382683432365090);
        tform.put(2, 1, 0);
        tform.put(2, 2, 0.923879532511287);
        /*
        tform.put(0, 0,  -1);
        tform.put(0, 1, 0);
        tform.put(0, 2, 0);
        tform.put(1, 0, 0);
        tform.put(1, 1,  -1 );
        tform.put(1, 2, 0);
        tform.put(2, 0,  0);
        tform.put(2, 1, 0);
        tform.put(2, 2, 1);*/
        transformForward transformer = new transformForward();
        Mat tempMat = new Mat();
        //System.out.println(tempMat);
        Core.findNonZero(oldImage, tempMat);
        
        MatOfPoint tempMapPoint = new MatOfPoint(tempMat);
        List<Point> locationsNew = new ArrayList<Point>();
        Converters.Mat_to_vector_Point(tempMapPoint,locationsNew);
        List<Point> tformed = transformer.transform(tform, locationsNew, true, false);
        System.out.println("TFORMED ARE:");
        Mat finalImage = new Mat(180, 240, CV_8UC3, new Scalar(0, 0, 0));
        for(int i = 0; i < tformed.size(); i++)
        {
            System.out.println(tformed.get(i).x + "  " + tformed.get(i).y);
            double[] pixel = new double[3];
            pixel[0] = 255;
            pixel[1] = 255;
            pixel[2] = 255;
            finalImage.put((int) tformed.get(i).y,(int)  tformed.get(i).x, pixel);  
        }
        
        
        Imgcodecs.imwrite("C:\\Users\\ryanj\\Documents\\jaer-master\\starTrackingBuiltOnJAER\\testData\\artificial data\\tformed.png", finalImage);
        //Imgcodecs.imwrite("/testDataresults/secondImage.jpg", secondImage); */
    }
}
