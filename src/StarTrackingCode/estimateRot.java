package StarTrackingCode;


import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import static org.opencv.core.CvType.CV_32FC1;
import org.opencv.core.Mat;

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
public class estimateRot {
    Mat K;
    estimateRot()
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
    }
    Mat predictRotation( List<double[]>predicted, List<double[]> real)
    {
        Mat tform;
        double [][] p2 = new double[predicted.size()][2];
        predicted.toArray(p2);
        double [][] p1 = new double[real.size()][2];
        real.toArray(p1);
        double [][] p1dbl = new double[p1.length][3];
        double [][] p2dbl = new double[p2.length][3];
        //System.out.println("Nowowo");
        for(int i = 0; i < p1.length; i++)
        {
            //System.out.printf("new x %f y %f old x %f y %f\n", p1[i][0] , p1[i][1] , p2[i][0] , p2[i][1]);
            //System.out.println(p1[i][0] + " " + p1[i][1]);
            p1dbl[i][0] = p1[i][0];
            p1dbl[i][1] = p1[i][1];
            p1dbl[i][2] = 1;
            //p1dbl[i][0] = p1[i][0];
            //p1dbl[i][1] = p1[i][1];
            //p1dbl[i][2] = 1;
        }
        //System.out.println("Prevowo");
        for(int i = 0; i < p2.length; i++)
        {
           // System.out.println(p2[i][0] + " " + p2[i][1]);
            p2dbl[i][0] = p2[i][0];
            p2dbl[i][1] = p2[i][1];
            p2dbl[i][2] = 1;
            //p2dbl[i][0] = p2[i][0];
            //p2dbl[i][1] = p2[i][1];
            //p2dbl[i][2] = 1;
            
        }
        //System.out.println("Doneowo");
        Mat prediction = new Mat(real.size(),3,CvType.CV_32F);
        Mat reality = new Mat(real.size(),3,CvType.CV_32F);
        for(int row = 0; row < real.size(); row++)
        {
            for(int column = 0; column < 3; column++)
            {
                prediction.put(row, column, p2dbl[row][column]);
                reality.put(row, column, p1dbl[row][column]);
                //System.out.println(p2int[row][column]);
                //System.out.println(p1int[row][column]);
            }   
        }
        Mat A = new Mat();
        Mat transposedTemp = new Mat();
        Core.transpose(reality, transposedTemp);
        tform = Mat.eye(3, 3, 5);
        Core.gemm(K.inv(),transposedTemp,1,tform,0,reality);
        Core.transpose(prediction, transposedTemp);
        Core.gemm(K.inv(),transposedTemp,1,tform,0,prediction);
        Core.transpose(prediction, transposedTemp);
        //System.out.println(prediction.size().toString());
        //System.out.println(reality.size().toString());
        Core.gemm(reality,transposedTemp, 1, tform, 0, A);
        //System.out.println(A.size());
       // System.out.println("A is " + A);
      // Core.transpose(A, transposedTemp);
        
        Mat w = new Mat();
        Mat u = new Mat();
        Mat v = new Mat();
        
        Core.SVDecomp(A, w, u, v);
        
        //transposedTemp = v;
        //Core.transpose(v, transposedTemp);
        transposedTemp = v;
        v = u;
        u = transposedTemp;
        /*   System.out.println("W");
           System.out.println(w.size());
           System.out.println(w.get(0, 0)[0]);
           System.out.println(w.get(1, 0)[0]);
           System.out.println(w.get(2,0)[0]);
           System.out.println("U");
      for(int i = 0; i < 3; i++)
       {
           
           System.out.println(u.get(i, 0)[0]);
           System.out.println(u.get(i, 1)[0]);
           System.out.println(u.get(i, 2)[0]);
       }
      
           System.out.println("V");
        for(int i = 0; i < 3; i++)
         {
             System.out.println(v.get(i, 0)[0]);
             System.out.println(v.get(i, 1)[0]);
             System.out.println(v.get(i, 2)[0]);
         }*/
        //System.out.printf("tform is %d%d, u is %d%d, v is %d%d",(int)tform.size().height, (int)tform.size().width,(int)u.size().height, (int)u.size().width,(int)v.size().height, (int)v.size().width);
        //transposedTemp = u;
        //Core.transpose(u, transposedTemp);
        Core.gemm(v, u, 1, tform, 0, A);
        
        double det = Core.determinant(A);
        /*System.out.println("Det is");
        System.out.println(det);
        System.out.println("A");
        for(int i = 0; i < 3; i++)
         {

             System.out.println(A.get(i, 0)[0]);
             System.out.println(A.get(i, 1)[0]);
             System.out.println(A.get(i, 2)[0]);
         }*/
        if(det < 0)
        {
            /*System.out.println("MAYDAY");
            System.out.println("MAYDAY");
            System.out.println("MAYDAY");
            System.out.println("MAYDAY");
            System.out.println("MAYDAY");
            System.out.println("MAYDAY");*/
            for(int y = 0; y < 3; y++)
            {
                v.put(y, 2, v.get(y, 2)[0]*-1);
            }
            Core.gemm(v, u, 1, tform, 0, A);
            det = Core.determinant(A);
        }
        tform.put(2, 2, det);
        //System.out.println(transposedTemp.size());
        //System.out.println(v.size());
        //System.out.println(tform.size());
        Mat temp2 = A;
        //System.out.println(tform);
        //System.out.println(v);
        Core.gemm(v, tform, 1, tform, 0, temp2);
        Core.gemm(temp2, u, 1, tform, 0, tform);
        //System.out.println("TFORM");
      /*for(int a = 0; a < 3; a++)
       {
           System.out.println(tform.get(a, 0)[0]);
           System.out.println(tform.get(a, 1)[0]);
           System.out.println(tform.get(a, 2)[0]);       
       }*/
        return tform;
    }
}
