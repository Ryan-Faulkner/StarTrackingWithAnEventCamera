package StarTrackingCode;


import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Arrays;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention;
import org.apache.commons.math3.geometry.euclidean.threed.RotationOrder;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolver;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;

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
public class RotAvg {
    Mat[][] relativeRotations;
    Rotation [] absRotations;
    Rotation noAvgPose;
    int counter;
    RotAvg()
    {
        relativeRotations = new Mat[5][5];
        absRotations = new Rotation [10];
        absRotations[0] = null;
        absRotations[1] = null;
        absRotations[2] = null;
        absRotations[3] = null;
        absRotations[4] = null;
        absRotations[5] = null;
        absRotations[6] = null;
        absRotations[7] = null;
        absRotations[8] = null;
        absRotations[9] = new Rotation(1,0,0,0,false);
        noAvgPose = new Rotation(1,0,0,0,false);
        counter = 5; //set to 1 so will never save lol
    }
    void addRot(Mat[] relRs)
    {
        relativeRotations[0] = relativeRotations[1];
        relativeRotations[1] = relativeRotations[2];
        relativeRotations[2] = relativeRotations[3];
        relativeRotations[3] = relativeRotations[4];
        relativeRotations[4] = new Mat[]{relRs[0],relRs[1],relRs[2],relRs[3],relRs[4]};
        double[][]tempM = new double[3][3];
        tempM[0][0] = relativeRotations[4][0].get(0, 0)[0];
        tempM[0][1] = relativeRotations[4][0].get(0, 1)[0];
        tempM[0][2] = relativeRotations[4][0].get(0, 2)[0];
        tempM[1][0] = relativeRotations[4][0].get(1, 0)[0];
        tempM[1][1] = relativeRotations[4][0].get(1, 1)[0];
        tempM[1][2] = relativeRotations[4][0].get(1, 2)[0];
        tempM[2][0] = relativeRotations[4][0].get(2, 0)[0];
        tempM[2][1] = relativeRotations[4][0].get(2, 1)[0];
        tempM[2][2] = relativeRotations[4][0].get(2, 2)[0];
        Rotation relativeR = new Rotation(tempM,1);
        
        absRotations[0] = absRotations[1];
        absRotations[1] = absRotations[2];
        absRotations[2] = absRotations[3];
        absRotations[3] = absRotations[4];
        absRotations[4] = absRotations[5];
        absRotations[5] = absRotations[6];
        absRotations[6] = absRotations[7];
        absRotations[7] = absRotations[8];
        absRotations[8] = absRotations[9];
        Rotation tempR = absRotations[9];
        //do a sanity check
        double[] angles = relativeR.getAngles(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR);
        if(angles[0] < Math.PI/2 && angles[1] < Math.PI/2 && angles[2] < Math.PI/2)
           //     true)
        {
          //absRotations[9] = relativeR.applyTo(tempR);
          absRotations[9] = relativeR.compose(tempR, RotationConvention.VECTOR_OPERATOR);
          tempR = noAvgPose;
          //noAvgPose = relativeR.applyTo(tempR);
          noAvgPose = relativeR.compose(tempR, RotationConvention.VECTOR_OPERATOR);
        }
        
    }
    Rotation getAverage()
    {
       // if(true)
       // {
       //      return absRotations[9];
       // }
        
        int m = 10;
        if(absRotations[0] == null)
        {
            return absRotations[9];
        }
        Vector<Point> idxs = new Vector<Point>();
        Vector<Rotation> rotations = new Vector<Rotation>(); //qq
        double[][]tempM = new double[3][3];
        HashSet<Integer> vertices = new HashSet<Integer>();
        for(int newFrame = 4; newFrame >= 0; newFrame--)
        {
            for (int oldFrame = 0; oldFrame < 4; oldFrame++)
            {
                if(relativeRotations[newFrame][oldFrame] != null)
                {
                    tempM[0][0] = relativeRotations[newFrame][oldFrame].get(0, 0)[0];
                    tempM[0][1] = relativeRotations[newFrame][oldFrame].get(0, 1)[0];
                    tempM[0][2] = relativeRotations[newFrame][oldFrame].get(0, 2)[0];
                    tempM[1][0] = relativeRotations[newFrame][oldFrame].get(1, 0)[0];
                    tempM[1][1] = relativeRotations[newFrame][oldFrame].get(1, 1)[0];
                    tempM[1][2] = relativeRotations[newFrame][oldFrame].get(1, 2)[0];
                    tempM[2][0] = relativeRotations[newFrame][oldFrame].get(2, 0)[0];
                    tempM[2][1] = relativeRotations[newFrame][oldFrame].get(2, 1)[0];
                    tempM[2][2] = relativeRotations[newFrame][oldFrame].get(2, 2)[0];
                    Rotation tempRot = new Rotation(tempM,1);
                    //do a sanity check
                    double[] angles = tempRot.getAngles(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR);
                    if(Math.abs(angles[0]) < Math.PI*3/4 && Math.abs(angles[1]) < Math.PI*3/4 && Math.abs(angles[2]) < Math.PI*3/4)
                           // true)
                    {
                        rotations.add(new Rotation(tempM,1));
                        idxs.add(new Point(9-newFrame,9-oldFrame-newFrame-1));
                        vertices.add(9-newFrame);
                        vertices.add(9-oldFrame-newFrame-1);
                        //System.out.println(9-newFrame);
                        //System.out.println(9-oldFrame-newFrame-1);
                    }  
                }
            }
        }
        if(idxs.size() < 5 || vertices.size() < 5)
        {
            return absRotations[4];
        }
        HashMap<Integer,Integer> vertex_to_idx = new HashMap<Integer,Integer>();
        HashMap<Integer,Integer> idx_to_vertex = new HashMap<Integer,Integer>();
        int f = vertices.size() - 5;
        //no need to add frames inside the window which are fixed as no loop closure
        int t = 0;
        int k = f;
        for (int x : vertices) {
            if(x >= m-5)
            {
                idx_to_vertex.put(k, x);
                vertex_to_idx.put(x, k);
                //System.out.println(" FROM " + x + " TO " + k);
                k++;
            }
            else
            {
                idx_to_vertex.put(t, x);
                vertex_to_idx.put(x, t);
                //System.out.println(" FROM " + x + " TO " + t);
                t++;
            }
        }
        //now get my indexes of new and old frame for each RR to fit the new order
        for(int i = 0; i < idxs.size(); i++)
        {
            idxs.get(i).x = vertex_to_idx.get((int)idxs.get(i).x);
            idxs.get(i).y = vertex_to_idx.get((int)idxs.get(i).y);
        }
        //now get absolute rotation estimate for each frame
        Rotation[] absR = new Rotation[vertices.size()];
        for(int x : vertices)
        {
            absR[vertex_to_idx.get(x)] = absRotations[x];
            if(absRotations[x] == null)
            {
                System.out.println("AKJLGSAHOGISAG");
            }
            //rotation does quaternion in order v1,v2,v3,scale however c++ code is scale first so playing it safe like this
        }
        
        //make QQ
        Mat relR = new Mat(new Size(idxs.size(),4), CvType.CV_32F);
        for(int i = 0; i < idxs.size(); i++)
        {
            relR.put(i, 0, rotations.get(i).getQ1());
            relR.put(i, 1, rotations.get(i).getQ2());
            relR.put(i, 2, rotations.get(i).getQ3());
            relR.put(i, 3, rotations.get(i).getQ0());
        }
        
        Mat A = Mat.zeros(idxs.size(), vertices.size()-f, CvType.CV_32F);
        for(int l = 0; l < idxs.size(); l++)
        {
            Point e = idxs.get(l);
            int e1 =(int) e.x;
            int e2 = (int)e.y;
            int j = e2 -f;
            if(j < 0)
            {
                continue;
            }
            A.put(l, j, 1);
            int i = e1-f;
            if(i < 0)
            {
                continue;
            }
            A.put(l, i, -1);
        }
        double sigma = 5*Math.PI/180;
        int irls_iters = 100;
        double change_th = .001;
        //cost removed as input as I'll just always do German Mclure or whatever looks easiest I think lol
        //runtime and iterations and weights are all unecessary outputs so not passing them in either (they were the two outputs)
        //I do need the new absR though.....so guess I'll have it return that lol
        Rotation[] newAbs = irls(rotations, idxs, A, sigma, absR, f, irls_iters, change_th);
        
        for(int count = f; count < vertices.size(); count++)
        {
            //System.out.println(absRotations[idx_to_vertex.get(counter)].getAngles(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR)[0]);
            //System.out.println(newAbs[counter].getAngles(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR)[0]);
            absRotations[idx_to_vertex.get(count)] = newAbs[count];
        }
        
        return absRotations[4];
        
    }
    Rotation[] irls(Vector<Rotation> relR, Vector<Point> idxs, Mat A, double sigma, Rotation [] absR, int f, int max_iters, double change_th)
    {
        /*System.out.println("Starting Abs");
        for(int i = 0; i < absR.length; i++)
        {
            System.out.println(absR[i].getQ0()+" " + absR[i].getQ1() +" " +  absR[i].getQ2() +" " +  absR[i].getQ3());
        }*/
        if(counter == 0)
        {
            try (PrintWriter out = new PrintWriter("relR.txt"))
            {
                for(int i = 0; i < relR.size(); i++)
                {
                    out.println(relR.get(i).getQ3()+" " + relR.get(i).getQ0() +" " +  relR.get(i).getQ1() +" " +  relR.get(i).getQ2());
                }
            }
            catch (FileNotFoundException e)
            {

            }
             try (PrintWriter out = new PrintWriter("idxs.txt"))
            {
                for(int i = 0; i < idxs.size(); i++)
                {
                   out.println(idxs.get(i).x +" " + idxs.get(i).y);
                }
            }
            catch (FileNotFoundException e)
            {

            }
            try (PrintWriter out = new PrintWriter("absR.txt"))
            {
                for(int i = 0; i < absR.length; i++)
                {
                    out.println(absR[i].getQ3()+" " + absR[i].getQ0() +" " +  absR[i].getQ1() +" " +  absR[i].getQ2());
                }
            }
            catch (FileNotFoundException e)
            {

            }
             try (PrintWriter out = new PrintWriter("A.txt"))
            {
                for(int i = 0; i < A.height(); i++)
                {
                    out.print(A.get(i, 0)[0]);
                    for(int j = 1; j < A.width(); j++)
                    {
                        out.print(" " + A.get(i, j)[0]);
                    }
                    out.println();
                }
            }
            catch (FileNotFoundException e)
            {

            }
             try (PrintWriter out = new PrintWriter("other.txt"))
            {
                out.println("Sigma");
                 out.println(sigma);
                out.println("f");
                 out.println(f);
                out.println("maxIter");
                 out.println(max_iters);
                 out.println("Change Threshold");
                 out.println(change_th);
            }
            catch (FileNotFoundException e)
            {

            }
        }
        
        //don't need clocka s not timing
        int m = relR.size();
        int n = absR.length-f;
        //change in rel rotation
        Vector<Rotation> w;
        //change in abs rotation
        Rotation [] W = new Rotation[n]; 
        Mat W3 = Mat.ones(n,3, CvType.CV_32F);
        double score = Double.MAX_VALUE;
        int iters = 0;
        double [] weights = new double[m];
        Arrays.fill(weights, 1);
        
        //Mat DB = new Mat(m,3,CvType.CV_32F);
        DMatrixRMaj CB = new DMatrixRMaj(m,3);
        Mat D = Mat.zeros(m,m,CvType.CV_32F);
        //triplet list which I'm ignoring for now
        
        while((score > change_th) && (iters < max_iters))
        {
            //System.out.println("ITERATIONS IS " + iters);
            w = delta_rel(idxs,relR,absR);
            Mat wMat = new Mat(m,4,CvType.CV_32F);
            wMat = logMap(w);
            for(int i = 0; i < m; i++)
            {
                //D is just an identity matrix but instead of 1 it's the weight for each rotation
                D.put(i, i, weights[i]);
                //System.out.println(weights[i]);
            }
            Mat DA = new Mat();
            
            Core.gemm(D, A, 1, DA, 0, DA);
            DMatrixRMaj CA = new DMatrixRMaj(DA.height(),DA.width());
            for(int i = 0; i < DA.rows(); i++)
            {
                for(int j = 0; j < DA.cols(); j++)
                {
                    CA.set(i, j, DA.get(i, j)[0]);
                }
            }//DA then merges D and A so I can get weighting of each frame (and from that absolute rotation I think)
            /*System.out.println("CA");
            for(int i = 0; i < CA.numRows; i++)
            {
                System.out.println(CA.get(i,0) + " " + CA.get(i,1) + " " + CA.get(i,2) + " " + CA.get(i,3) + " " + CA.get(i,4));
            }
            System.out.println("CB");*/
            for(int i = 0; i < m; i++)
            {
                //DB.put(i, 0, weights[i]*wMat.get(i,0)[0]);
                //DB.put(i, 1, weights[i]*wMat.get(i,1)[0]);
                //DB.put(i, 2, weights[i]*wMat.get(i,2)[0]);
                
                CB.set(i, 0, weights[i]*wMat.get(i,0)[0]);
                CB.set(i, 1, weights[i]*wMat.get(i,1)[0]);
                CB.set(i, 2, weights[i]*wMat.get(i,2)[0]);
                //System.out.println(CB.get(i,0) + " " + CB.get(i,1) + " " + CB.get(i,2));
                
                //Vector3D vec = w.get(i).getAxis(RotationConvention.FRAME_TRANSFORM);
                //DB.put(i, 0, weights[i]*vec.getX()*w.get(i).getAngle());
                //DB.put(i, 1, weights[i]*vec.getY()*w.get(i).getAngle());
                //DB.put(i, 2, weights[i]*vec.getZ()*w.get(i).getAngle());
                //DB.put(i, 1, weights[i]*wMat.get(i,1)[0]);
                //DB.put(i, 2, weights[i]*wMat.get(i,2)[0]);
            }
            DMatrixRMaj X = new DMatrixRMaj(DA.width(),3);
            LinearSolver<DMatrixRMaj,DMatrixRMaj> qrp = LinearSolverFactory_DDRM.pseudoInverse(true);
            qrp.setA(CA);
            qrp.solve(CB, X);
            /*System.out.println("X IS");
            for(int i = 0; i < X.numRows; i++)
            {
                for(int j = 0; j < X.numCols; j++)
                {
                    System.out.print(X.get(i, j) + " ");                
                }
                System.out.println();
            }*/
            //Core.solve(DA, DB, W3, Core.DECOMP_SVD | Core.DECOMP_NORMAL);
             score = 0;
             //System.out.println("W3");
             for(int i = 0; i < X.numRows; i++)
             {
                 for(int j = 0; j < X.numCols; j++)
                 {
                    W3.put(i, j, X.get(i, j));
                    //System.out.print(W3.get(i,j)[0] + " ");
                 }
                 //System.out.println();
             }
             if(counter == 1)
             {
                System.out.println("YIKES");
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
                 counter++;
             }
             else
             {
                 if(counter == 2)
                 {
                      System.out.println("YEETEDY DU COWBOY");
                    W3.put(0, 0, -0.150138);
                    W3.put(0, 1, -0.37768);
                    W3.put(0, 2, 0.16554);
                    W3.put(1, 0, -0.234419);
                    W3.put(1, 1, -0.0518866);
                    W3.put(1, 2, 0.206017);
                    W3.put(2, 0,-0.319952);
                    W3.put(2, 1, 0.367421);
                    W3.put(2, 2, 0.138533);
                    W3.put(3, 0, -0.104796);
                    W3.put(3, 1, 0.200602);
                    W3.put(3, 2, 0.268215);
                    W3.put(4, 0, 0);
                    W3.put(4, 1, 0);
                    W3.put(4, 2, 0);
                     counter++;
                 }
             }
             for(int i = 0; i < W3.rows(); i++)
             {
                  // System.out.println(W3.get(i, 0)[0] + " " + W3.get(i, 1)[0] + " " + W3.get(i, 2)[0]);
                   score += Math.sqrt(Math.pow(W3.get(i, 0)[0],2) + Math.pow(W3.get(i, 1)[0],2) + Math.pow(W3.get(i, 2)[0],2));
             }
             //System.out.println("DONE");
            if(Double.isNaN(W3.get(0,0)[0]))
            {
                System.out.println("AF");
            }
             //upgrade weights and repeat
             //cost I will use: Geman_McClure
             Mat E = new Mat();
             Core.gemm(A, W3, 1, A, 0, E);
             Mat temp = E;
             /*System.out.println("EMAT WAS");
             for(int i = 0; i < m; i++)
             {
                 //System.out.println(wMat.get(i, 0)[0] + " " + wMat.get(i, 1)[0] + " " + wMat.get(i, 2)[0]);
                 System.out.println(E.get(i, 0)[0] + " " + E.get(i, 1)[0] + " " + E.get(i, 2)[0]);
             }*/
             for(int i = 0; i < E.rows(); i++)
             {
                 for(int j = 0; j < E.cols(); j++)
                 {
                     E.put(i, j, E.get(i,j)[0]-wMat.get(i, j)[0]);
                 }
             }
             /*System.out.println("EMAT IS");
             for(int i = 0; i < m; i++)
             {
                 //System.out.println(wMat.get(i, 0)[0] + " " + wMat.get(i, 1)[0] + " " + wMat.get(i, 2)[0]);
                 System.out.println(E.get(i, 0)[0] + " " + E.get(i, 1)[0] + " " + E.get(i, 2)[0]);
             }*/
             //System.out.println("WEIGHTS ARE");
             for (int i = 0; i < m; i++)
             {
                 weights[i] = sigma*sigma;
                 weights[i]+=Math.pow(E.get(i, 0)[0],2);
                 weights[i]+=Math.pow(E.get(i, 1)[0],2);
                 weights[i]+=Math.pow(E.get(i, 2)[0],2);
                 weights[i] = 1/weights[i];
                 //System.out.println(weights[i]);
             }
             score /= W3.height();
             //System.out.println("WAUUGHWUGHAWGf");
             //System.out.println(score);
             
             W = expMap(W3);
             //upgrade abs_rot
             //dont need score as not outputting it lol
             for(int i = 0; i < n; i++ )
             {
                 //System.out.println("Before " + absR[i+f].getAngles(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR)[0] + " "  + absR[i+f].getAngles(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR)[1] + " "  + absR[i+f].getAngles(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR)[2]);
                  absR[i+f] = absR[i+f].compose(W[i], RotationConvention.FRAME_TRANSFORM);  //for same result as Alvarro use FRAME
                 //System.out.println("After " + absR[i+f].getAngles(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR)[0] + " "  + absR[i+f].getAngles(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR)[1] + " "  + absR[i+f].getAngles(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR)[2]);
             }
             /*System.out.println("ABS ROT ARE ");
             for(int i = 0; i < absR.length; i++)
             {
                
                System.out.println(absR[i].getQ1()+" " +absR[i].getQ2()+" " +absR[i].getQ3()+" " +absR[i].getQ0());
             }*/
             iters++;
        }
         if(counter == 0)
        {
            try (PrintWriter out = new PrintWriter("absROutput.txt"))
            {
                for(int i = 0; i < absR.length; i++)
                {
                    out.println(absR[i].getQ0()+" " + absR[i].getQ1() +" " +  absR[i].getQ2() +" " +  absR[i].getQ3());
                }
            }
            catch (FileNotFoundException e)
            {

            }
        }
         //    System.out.println(score);
        counter++;
        /*System.out.println("Final Abs");
        for(int i = 0; i < absR.length; i++)
        {
            System.out.println(absR[i].getQ0()+" " + absR[i].getQ1() +" " +  absR[i].getQ2() +" " +  absR[i].getQ3());
        }*/
        //System.out.println("IT TOOK " + iters);
        return absR;
    }
    Vector<Rotation> delta_rel(Vector<Point> idxs,Vector<Rotation> relR, Rotation[] absR)
    {
        //Rotation[] absRInv = new Rotation[absR.length];
       /* System.out.println("AbsR");
        for(int j = 0; j < absR.length; j++)
        {
            System.out.println(absR[j].getQ1() + " " + absR[j].getQ2() + " " + absR[j].getQ3() + " " + absR[j].getQ0());
        }
        System.out.println("RelR");
        for(int j = 0; j < relR.size(); j++)
        {
            System.out.println(relR.get(j).getQ1() + " " + relR.get(j).getQ2() + " " + relR.get(j).getQ3() + " " + relR.get(j).getQ0());
        }*/
        //for(int i = 0; i < absR.length; i++)
        //{
        //    absRInv[i] = new Rotation(-1*absR[i].getQ0(),absR[i].getQ1(),absR[i].getQ2(),absR[i].getQ3(),false);
       // }
        int m = idxs.size();
        Vector<Rotation> resp = new Vector<Rotation>();
        for (int k = 0; k < m; k++)
        {
            Point e = idxs.get(k);
            int i =(int) e.x;
            int j =(int) e.y;
            Rotation tempR = absR[j].composeInverse(relR.get(k).compose(absR[i],RotationConvention.FRAME_TRANSFORM),RotationConvention.FRAME_TRANSFORM);
            //Rotation tempR = absR[j].composeInverse(relR.get(k).compose(absR[i],RotationConvention.VECTOR_OPERATOR),RotationConvention.VECTOR_OPERATOR);
            //System.out.println(tempR.getQ1() + " " + tempR.getQ2() + " " + tempR.getQ3() + " " + tempR.getQ0());
            resp.add(tempR);
        }
        return resp;
    }
    Mat logMap (Vector<Rotation> w)
    {
        Mat wMat = new Mat(w.size(),4,CvType.CV_32F);
        double aux =0;
        
        // w(:,1)=2*atan2(s2,w(:,1));
        int m= w.size();
        for(int i=0; i<m; ++i)
        {
            
            double theta = w.get(i).getQ0();
            theta = 2* Math.atan2(Math.sqrt( Math.pow(w.get(i).getQ1(),2) + Math.pow(w.get(i).getQ2(),2) + Math.pow(w.get(i).getQ3(),2)), theta);
            
            if (theta < -Math.PI)
            {
                theta += 2*Math.PI;
            }
            else if (theta >= Math.PI)
            {
                theta -= 2*Math.PI;
            }
            
           /* System.out.println("Q0");
            System.out.println(w.get(i).getQ0());
            System.out.println("Q1");
            System.out.println(w.get(i).getQ1());
            System.out.println("Q2");
            System.out.println(w.get(i).getQ2());
            System.out.println("Q3");
            System.out.println(w.get(i).getQ3());*/
            aux = theta/Math.sqrt( Math.pow(w.get(i).getQ1(),2) + Math.pow(w.get(i).getQ2(),2) + Math.pow(w.get(i).getQ3(),2));
            wMat.put(i, 0, w.get(i).getQ1() * aux);
            wMat.put(i, 1, w.get(i).getQ2() * aux);
            wMat.put(i, 2, w.get(i).getQ3() * aux);
            wMat.put(i, 3, theta);
            if(Math.sqrt( Math.pow(w.get(i).getQ1(),2) + Math.pow(w.get(i).getQ2(),2) + Math.pow(w.get(i).getQ3(),2)) < Double.MIN_VALUE)
            {
                wMat.put(i,3 ,0);
            }
        }
        return wMat;
        
    }
    Rotation [] expMap(Mat W3)
    {
        Rotation [] W = new Rotation[W3.rows()];
        for (int i = 0; i < W3.rows(); i++)
        {
            
            double norm =Math.sqrt(Math.pow(W3.get(i, 0)[0],2) + Math.pow(W3.get(i, 1)[0],2) + Math.pow(W3.get(i, 2)[0],2));
            double angleCoef = Math.sin(norm/2)/norm;
            norm = Math.cos(norm/2);
            if(Double.isNaN(norm))
            {
                norm = 0;
            }
            if(Double.isNaN(angleCoef))
            {
                angleCoef = 0;
            }
            W[i] = new Rotation(norm,W3.get(i,0)[0]*angleCoef,W3.get(i,1)[0]*angleCoef,W3.get(i,2)[0]*angleCoef,false);
            
        }
        
        return W;
    }
}
