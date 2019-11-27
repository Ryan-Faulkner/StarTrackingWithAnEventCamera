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
        Mat tForm = Mat.eye(3, 3, 5); //5 means CV_32F{
        Mat tempTForm;
        Mat tempTForm2;
        double npo= Math.ceil(0.8*locationsOld.size());
        double prev_mean_distance =100000; //a very high number
        KDTree kdTree = new KDTree(2);
        double[] temp;
        ArrayList<double[]> real = new ArrayList();
        ArrayList<double[]> predicted = new ArrayList();
        double[] distance = new double[locationsOld.size()];
        //System.out.println("Now");
        for(int j = 0; j < locationsNew.size(); j++)
        {
           // System.out.println(locationsNew.get(j).x + " " + locationsNew.get(j).y);
            temp = new double[]{locationsNew.get(j).x, locationsNew.get(j).y};
            kdTree.insert(temp, temp);
        }
        //System.out.println("Prev");
        for(int l = 0; l < locationsOld.size(); l++)
        {
          //   System.out.println(locationsOld.get(l).x + " " + locationsOld.get(l).y);
            predicted.add(new double[]{locationsOld.get(l).x, locationsOld.get(l).y});
        }
        //System.out.println("Done");
        int i = 0;
        for(i = 0; i < iterations; i++)
        {
            predicted = new ArrayList();
            for(int l = 0; l < locationsOld.size(); l++)
            {
                predicted.add(new double[]{locationsOld.get(l).x, locationsOld.get(l).y});
            }
            //Mat element1 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new  Size(2*3+ 1, 2*3+1));
            //Mat newImage = new Mat(180, 240, CV_8UC3, new Scalar(0, 0, 0));
            //Mat newImage2 = new Mat(180, 240, CV_8UC3, new Scalar(0, 0, 0));
        
            real = new ArrayList();
           for(int k = 0; k < locationsOld.size();k++)
            {
                
                real.add((double[]) kdTree.nearest(predicted.get(k)));
                double left = (real.get(k)[0]-predicted.get(k)[0]) * (real.get(k)[0]-predicted.get(k)[0]);
                double right = (real.get(k)[1]-predicted.get(k)[1]) * (real.get(k)[1]-predicted.get(k)[1]);
                distance[k] = Math.sqrt(left + right);
                /*double[] pixel = new double[3];
                pixel[0] = 255;
                pixel[1] = 255;
                pixel[2] = 255;
                newImage2.put((int) predicted.get(k)[1],(int)  predicted.get(k)[0], pixel); 
                */
                
               // System.out.printf("real is %f, predicted is %f, distance is %f\n", real.get(k)[0],predicted.get(k)[0],distance[k]);
               // System.out.printf("real is %f, predicted is %f, distance is %f\n", real.get(k)[1],predicted.get(k)[1],distance[k]);
            }
            /*Imgproc.dilate(newImage, newImage, element1);   
            Imgproc.dilate(newImage2, newImage2, element1);   */  
            /*double[] pixel = new double[3];
            pixel[0] = 255;
            pixel[1] = 255;
            pixel[2] = 255;
            for(int j = 0; j < locationsNew.size(); j++)
           {
               newImage.put((int)locationsNew.get(j).y,(int)locationsNew.get(j).x, pixel);
           }
            //System.out.println("done");
            //Imgcodecs.imwrite("testData/steps/" + i + ".jpg", newImage); 
            //Imgcodecs.imwrite("testData/steps/prediccted" + i + ".png", newImage2);*/
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
        //System.out.printf("Iterations is %d\n", i);
        //System.out.println("CURRENT IS " + currentMean);
        /*System.out.println("TFORM:");
        System.out.print(tForm.get(0, 0)[0]);
        System.out.print(" ");
        System.out.print(tForm.get(0, 1)[0]);
        System.out.print(" ");
        System.out.println(tForm.get(0, 2)[0]);
        System.out.print(tForm.get(1, 0)[0]);
        System.out.print(" ");
        System.out.print(tForm.get(1, 1)[0]);
        System.out.print(" ");
        System.out.println(tForm.get(1, 2)[0]);
        System.out.print(tForm.get(2, 0)[0]);
        System.out.print(" ");
        System.out.print(tForm.get(2, 1)[0]);
        System.out.print(" ");
        System.out.println(tForm.get(2, 2)[0]);*/
        return tForm;
    } 
   
}
