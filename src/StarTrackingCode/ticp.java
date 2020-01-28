package StarTrackingCode;


import de.biomedical_imaging.edu.wlu.cs.levy.CG.KDTree;
import de.biomedical_imaging.edu.wlu.cs.levy.CG.KeyDuplicateException;
import de.biomedical_imaging.edu.wlu.cs.levy.CG.KeySizeException;
import java.util.ArrayList;
import java.util.List;
import org.bytedeco.javacpp.opencv_ml.KNearest;
import org.opencv.core.Core;
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
 *
 *
 *
 *          This code runs Trimmed Iterative Closest Point Algorithm 
 *          in order to determine the movement from one frame to the next
 *
 *
 */
public class ticp {
    int iterations = 200;
    double tol = 1e-6;
    KNearest knn;
    estimateRot estimator;
    transformForward transformer;
    ticp(){
        knn = KNearest.create();
        estimator = new estimateRot();
        transformer = new transformForward();
    }
    Mat tform(List<Point> locationsOld, List<Point> locationsNew) throws KeySizeException, KeyDuplicateException
    {
        Mat tForm = Mat.eye(3, 3, 5); //5 means CV_32F
        Mat tempTForm;
        Mat tempTForm2;
        double npo= Math.ceil(0.8*locationsOld.size());
        double prev_mean_distance =100000; //a very high number
        KDTree kdTree = new KDTree(2);
        double[] temp;
        ArrayList<double[]> real = new ArrayList();
        ArrayList<double[]> predicted = new ArrayList();
        double[] distance = new double[locationsOld.size()];
        for(int j = 0; j < locationsNew.size(); j++)
        {
            temp = new double[]{locationsNew.get(j).x, locationsNew.get(j).y};
            kdTree.insert(temp, temp);
        }
        for(int l = 0; l < locationsOld.size(); l++)
        {
            predicted.add(new double[]{locationsOld.get(l).x, locationsOld.get(l).y});
        }        int i = 0;
        for(i = 0; i < iterations; i++)
        {
            predicted = new ArrayList();
            for(int l = 0; l < locationsOld.size(); l++)
            {
                predicted.add(new double[]{locationsOld.get(l).x, locationsOld.get(l).y});
            }
        
            real = new ArrayList();
           for(int k = 0; k < locationsOld.size();k++)
            {
                
                real.add((double[]) kdTree.nearest(predicted.get(k)));
                double left = (real.get(k)[0]-predicted.get(k)[0]) * (real.get(k)[0]-predicted.get(k)[0]);
                double right = (real.get(k)[1]-predicted.get(k)[1]) * (real.get(k)[1]-predicted.get(k)[1]);
                distance[k] = Math.sqrt(left + right);
               
            }
            
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
                double tempInt = distance[index];
                distance[index] = distance[counter];
                distance[counter] = tempInt;
                
            }
            double currentMean = 0;
            for(int m = 0; m < npo; m++)
            {
                //System.out.printf("predicted is %d %d real is %d %d distance is %f\n",
                //       (int)predicted.get(m)[0], (int)predicted.get(m)[1], (int)real.get(m)[0], (int)real.get(m)[1],distance[m]);
                currentMean += distance[m];
            }
            //code works up to here
            currentMean = currentMean / npo;
            //System.out.printf("current mean is %f\n", currentMean);
            if (currentMean<=tol || Math.abs(prev_mean_distance-currentMean)<tol)
            {
                //System.out.println(Math.abs(currentMean-prev_mean_distance));
                //System.out.println(prev_mean_distance);
                //System.out.println(currentMean);
                break;
            }
            //Estimator works same as matlab (other than camera matrix) so must be transform foward or I'm as goodas it gets rip
            tempTForm = estimator.predictRotation(predicted.subList(0, (int)npo),real.subList(0, (int)npo));
            List <Point> tempList = locationsOld;
            //So it must be the transformer then
            locationsOld = transformer.transform(tempTForm,tempList,false,false);
            /*System.out.println("NOW SHOWING Old Locations VALUES OK MATE");
            for(int x = 0; x < locationsOld.size(); x++)
            {
                System.out.println(locationsOld.get(x));
            }*/
            tempTForm2 = tForm;
            Core.gemm(tempTForm2, tempTForm, 1, tForm, 0, tForm);
            prev_mean_distance = currentMean;
        }
        return tForm;
    } 
   
}
