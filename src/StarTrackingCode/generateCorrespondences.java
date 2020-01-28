package StarTrackingCode;


import de.biomedical_imaging.edu.wlu.cs.levy.CG.KDTree;
import de.biomedical_imaging.edu.wlu.cs.levy.CG.KeyDuplicateException;
import de.biomedical_imaging.edu.wlu.cs.levy.CG.KeySizeException;
import java.util.ArrayList;
import java.util.List;
import static org.bytedeco.javacpp.opencv_core.CV_8UC3;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;

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
 *                      This code determines which point in a new Frame corresponds to which point in the old frame, if any.
 *                      It then returns the corresponding point pairs.
 *
 */
public class generateCorrespondences {
    transformForward transformer;
    int imageCount;
    generateCorrespondences()
    {
        imageCount = 0;
        transformer = new transformForward();
    }
     List<Point> generate( List<Point> locationsOld,  List<Point> locationsNew, Mat tform) throws KeySizeException, KeyDuplicateException
    {
        boolean returnFuckingEverything = false;
        
        KDTree newTree = new KDTree(2);
        ArrayList<double[]> real = new ArrayList();
        ArrayList<double[]> predicted = new ArrayList();
        ArrayList<double[]> original = new ArrayList();
        double[] distance = new double[locationsOld.size()];
        double[] temp;
         List<Point> transformedLocationsOld = transformer.transform(tform,locationsOld,false,true);
         
        Mat oldImage = new Mat(180, 240, CV_8UC3, new Scalar(0, 0, 0));
        Mat predictImage = new Mat(180, 240, CV_8UC3, new Scalar(0, 0, 0));
        Mat newImage = new Mat(180, 240, CV_8UC3, new Scalar(0, 0, 0));
        for(int i = 0; i < transformedLocationsOld.size(); i+=2)
        {
            double[] pixel = new double[3];
            pixel[0] = 255;
            pixel[1] = 255;
            pixel[2] = 255;
            oldImage.put((int) transformedLocationsOld.get(i+1).y,(int)  transformedLocationsOld.get(i+1).x, pixel);  
            if((int) transformedLocationsOld.get(i).y < 180 && (int) transformedLocationsOld.get(i).y > 0 &&
            (int) transformedLocationsOld.get(i).x < 240 && (int) transformedLocationsOld.get(i).x> 0)
            {
                predictImage.put((int) transformedLocationsOld.get(i).y,(int)  transformedLocationsOld.get(i).x, pixel);                                
            } 
        }
        for(int i = 0; i < locationsNew.size(); i++)
        {
            double[] pixel = new double[3];
            pixel[0] = 255;
            pixel[1] = 255;
            pixel[2] = 255;
            newImage.put((int) locationsNew.get(i).y,(int)  locationsNew.get(i).x, pixel);  
        }
        
        /
        for(int j = 0; j < locationsNew.size(); j++)
        {
            temp = new double[]{locationsNew.get(j).x, locationsNew.get(j).y};
            newTree.insert(temp, temp);
        }
        int predCounter = 0;
         for(int k = 0; k < transformedLocationsOld.size();k+=2)
        {
            predicted.add(new double[]{transformedLocationsOld.get(k).x, transformedLocationsOld.get(k).y});
            real.add((double[]) newTree.nearest(predicted.get(predCounter)));
            original.add(new double[]{transformedLocationsOld.get(k+1).x, transformedLocationsOld.get(k+1).y});
           
            double left = (real.get(predCounter)[0]-predicted.get(predCounter)[0]) * (real.get(predCounter)[0]-predicted.get(predCounter)[0]);
            double right = (real.get(predCounter)[1]-predicted.get(predCounter)[1]) * (real.get(predCounter)[1]-predicted.get(predCounter)[1]);
            distance[predCounter] = Math.sqrt(left + right);
            predCounter++;
        }
        final List<double[]> realCopy = real;
        final List<double[]> predictedCopy = predicted;
        final List<double[]> originalCopy = original;
        real = new ArrayList(realCopy);
        predicted = new ArrayList(predictedCopy); 
        original = new ArrayList(originalCopy); 
        double meanDistance = 0;
      
        for(int counter = 0; counter < predicted.size(); counter++)
        {
            double min = Integer.MAX_VALUE;
            int index = -1;
            for(int counter2 = counter; counter2 < predicted.size(); counter2++)
            {
                if(distance[counter2] < min)
                {
                    min = distance[counter2];
                    index = counter2;
                }
            }
            double[] tempArray = real.get(index);
            real.set(index, real.get(counter));
            real.set(counter, tempArray);
            tempArray = predicted.get(index);
            predicted.set(index, predicted.get(counter));
            predicted.set(counter, tempArray);
            
            tempArray = original.get(index);
            original.set(index, original.get(counter));
            original.set(counter, tempArray);
            
            double tempInt = distance[index];
            distance[index] = distance[counter];
            
            distance[counter] = tempInt;
            meanDistance += tempInt;
        }
        meanDistance = meanDistance / predicted.size();
        
        int npo = (int) Math.ceil(predicted.size()*0.9);
        List<Point> idMatches = new ArrayList<>();
        for(int i = 0; i < npo; i++)
        {
            if(distance[i] > meanDistance * 1.3)
            {
                return idMatches;
            }
            Point tempPoint = new Point(original.get(i)[0],original.get(i)[1]);
            idMatches.add(tempPoint);
            tempPoint = new Point(real.get(i)[0],real.get(i)[1]);
            idMatches.add(tempPoint);
            if(returnFuckingEverything == true)
            {
                tempPoint = new Point(predicted.get(i)[0],predicted.get(i)[1]);
                idMatches.add(tempPoint);
            }
        }
        return idMatches;
        
    }
}