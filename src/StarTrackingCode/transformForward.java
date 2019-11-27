package StarTrackingCode;


import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import static org.opencv.core.CvType.CV_32FC1;
import org.opencv.core.Mat;
import org.opencv.core.Point;

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
public class transformForward {
    Mat K;
    transformForward()
    {   
        K = new Mat(3,3,CV_32FC1);
        K.put(0, 0, 246.43059488719612);
        K.put(0, 1, 0);
        K.put(0, 2, 103.08424033768227);
        K.put(1, 0, 0);
        K.put(1, 1, 245.6414223852361);
        K.put(1, 2, 77.55187058195787);
        K.put(2, 0, 0);
        K.put(2, 1, 0);
        K.put(2, 2, 1);
       /* 
        K.put(0, 0, 1);
        K.put(0, 1, 0);
        K.put(0, 2, 0);
        K.put(1, 0, 0);
        K.put(1, 1, 1);
        K.put(1, 2, 0);
        K.put(2, 0, 0);
        K.put(2, 1, 0);
        K.put(2, 2, 1);
        */
    }
    List<Point> transform(Mat tform, List<Point> original, boolean cleanUp, boolean returnOriginalList)
    {/*
        for(int i = 0; i < 3; i++)
       {
           System.out.println("TFORM IS");
           System.out.println(tform.get(i, 0)[0]);
           System.out.println(tform.get(i, 1)[0]);
           System.out.println(tform.get(i, 2)[0]);
       }*/
        
        
        
        Mat ogMat = new Mat(original.size(),3,CvType.CV_32FC1);
        for(int i = 0; i < original.size(); i++)
        {
            ogMat.put(i, 0, original.get(i).x);
            ogMat.put(i, 1, original.get(i).y);
            ogMat.put(i, 2, 1);
            //ogMat.put(i, 0, original.get(i).x);
            //ogMat.put(i, 1, original.get(i).y);
            //ogMat.put(i, 2, 1);
        }
         
        Mat A = new Mat();
        
        Mat transposedTemp = new Mat();
        Core.transpose(ogMat, transposedTemp);
        Core.gemm(K.inv(),transposedTemp,1,A,0,ogMat); 
        Core.gemm(tform,ogMat,1,A,0,A);
        Core.gemm(K,A,1,transposedTemp,0,transposedTemp);
        Core.transpose(transposedTemp,A);
        List<Point> transformedPoints = new ArrayList<Point>();
        Point tempPoint;
        for(int j = 0; j< original.size(); j++)
        {
            tempPoint = new Point((A.get(j, 0)[0]/A.get(j, 2)[0]*1),(A.get(j, 1)[0]/A.get(j, 2)[0]*1));
            //tempPoint = new Point((A.get(j, 0)[0]/A.get(j, 2)[0]),(A.get(j, 1)[0]/A.get(j, 2)[0]));
            transformedPoints.add(tempPoint);
        }
        if(cleanUp)
        {
            int removed = 0;
             for(int k = 0; k< transformedPoints.size(); k++)
            {
                long temp;
                long temp2;
                temp = Math.round(transformedPoints.get(k).x);
                temp2 = Math.round(transformedPoints.get(k).y);
                tempPoint = new Point(temp,temp2);
                transformedPoints.set(k, tempPoint);
                if(temp < 0 || temp2 < 0 || temp >= 240 || temp2 >= 180)
                {
                    transformedPoints.remove(k);
                    original.remove(k);
                    k--;
                    removed++;
                }
            }
        }
        if(returnOriginalList)
        {
            List<Point> tempPoints = transformedPoints;
            transformedPoints = new ArrayList<Point>();
            for(int i = 0; i < tempPoints.size(); i++)
            {
                transformedPoints.add(tempPoints.get(i));
                transformedPoints.add(original.get(i));
            }
        }
        
        return transformedPoints;
    }
    
}
