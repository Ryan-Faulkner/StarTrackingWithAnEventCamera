package StarTrackingCode;


import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.Vector;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.opencv.core.CvType;
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
public class RotAvgTester { 
    
    public static void main(String[] args) throws FileNotFoundException
    {
        System.load("C:\\Users\\ryanj\\Downloads\\opencv\\build\\java\\x64\\opencv_java320.dll");
        RotAvg rot = new RotAvg();
        Vector<Rotation> relR = new Vector<Rotation>();
        Rotation[] absR = new Rotation[9];
        Vector<Point> idxs = new Vector<Point>();
        Mat A = new Mat();
        Mat W3 = new Mat(5, 3, CvType.CV_32F);
        A = Mat.zeros(20, 5, CvType.CV_32F);
        Scanner scannerx = new Scanner(new File("C:\\Users\\ryanj\\Documents\\Honours Stuff\\testing data\\Re__input_output_of_my_code\\relR.txt"));
        Scanner scannery = new Scanner(new File("C:\\Users\\ryanj\\Documents\\Honours Stuff\\testing data\\Re__input_output_of_my_code\\A.txt"));
        Scanner scannerz = new Scanner(new File("C:\\Users\\ryanj\\Documents\\Honours Stuff\\testing data\\Re__input_output_of_my_code\\absR.txt"));
        Scanner scannerid = new Scanner(new File("C:\\Users\\ryanj\\Documents\\Honours Stuff\\testing data\\Re__input_output_of_my_code\\idxs.txt"));
        for(int i = 0; i < 20; i++)
        {
            double q1 = scannerx.nextDouble();
            double q2 = scannerx.nextDouble();
            double q3 = scannerx.nextDouble();
            double q0 = scannerx.nextDouble();
            relR.add( new Rotation(q0,q1,q2,q3,false));
            idxs.add(new Point((int)scannerid.nextDouble(),(int)scannerid.nextDouble()));
        }
        for(int i = 0; i < 9; i++)
        {
            
            double q1 = scannerz.nextDouble();
            double q2 = scannerz.nextDouble();
            double q3 = scannerz.nextDouble();
            double q0 = scannerz.nextDouble();
            absR[i] = ( new Rotation(q0,q1,q2,q3,false));
        }
        for(int i = 0; i < 20; i++)
        {
            for(int j = 0; j < 5; j++)
            {
                A.put(i,j,scannery.nextDouble());
            }
        }
        W3.put(0, 0, 0.226462);
        W3.put(0, 1, -1.03899);
        W3.put(0, 2, 0.192124);
        W3.put(1, 0, 0.104504);
        W3.put(1, 1, 0.15208);
        W3.put(1, 2, 0.0286773);
        W3.put(2, 0, -0.409905);
        W3.put(2, 1, 1.27854);
        W3.put(2, 2, -0.0630153);
        W3.put(3, 0, -0.0276237);
        W3.put(3, 1, 0.116734);
        W3.put(3, 2, 0.0986201);
        W3.put(4, 0, 0);
        W3.put(4, 1, 0);
        W3.put(4, 2, 0);
        rot.irls(relR, idxs, A, 0.08726646259971647, absR, 4, 100, 0.001);
        System.out.println(W3.rows());
        Rotation[] W4 = rot.expMap(W3);
        for(int i = 0; i < W4.length; i++)
        {
            System.out.println(W4[i].getQ1() + " " + W4[i].getQ2() + " " + W4[i].getQ3() + " " + W4[i].getQ0());
        }
    }
}
