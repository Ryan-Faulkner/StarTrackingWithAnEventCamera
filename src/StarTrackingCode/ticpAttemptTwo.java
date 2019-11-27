package StarTrackingCode;


//import edu.wlu.cs.levy.CG.KDTree;
//import edu.wlu.cs.levy.CG.KeyDuplicateException;
//import edu.wlu.cs.levy.CG.KeySizeException;
import java.util.List;
import org.bytedeco.javacpp.opencv_ml.KNearest;
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
public class ticpAttemptTwo {
    int iterations = 15;
    double tol = 1e-6;
    KNearest knn;
    ticpAttemptTwo(){
        knn = KNearest.create();
    }
    Mat tform(List<Point> locationsOld, List<Point> locationsNew)// throws KeySizeException, KeyDuplicateException
    {
        Mat tForm = Mat.eye(3, 3, 5); //5 means CV_32F{
        return tForm;
        
    } 
   
}
