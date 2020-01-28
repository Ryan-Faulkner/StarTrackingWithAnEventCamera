package StarTrackingCode;


import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import net.sf.jaer.event.EventPacket;
import static org.bytedeco.javacpp.opencv_core.CV_8UC1;
import org.opencv.core.*;
import static org.opencv.core.Core.flip;

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
 *          This code produces an event image from a list of points where an event has been logged in the data packet
 *
 *
 */
public class MakeEventImage {
    
    final private int width = 240;
    final private int height = 180;
    private double [][] undistortMapX;
    private double [][] undistortMapY;

    public MakeEventImage() throws IOException
    {
        undistortMapX = new double[height][width];
        undistortMapY = new double[height][width];
        Scanner scannerx = new Scanner(new File("C:\\Users\\ryanj_000\\Downloads\\undistort_map_x.txt"));
        Scanner scannery = new Scanner(new File("C:\\Users\\ryanj_000\\Downloads\\undistort_map_y.txt"));
        for(int i = 0; i < height; i++)
        {
            for(int j = 0; j < width; j++)
            {
                undistortMapX[i][j] = scannerx.nextDouble();
                undistortMapY[i][j] = scannery.nextDouble();
            }
        }
        
    }
    
    public Mat returnMat(EventPacket events)
    {
        Mat kernel = Mat.ones(3,3, CvType.CV_32F);
        Mat kernel2 = Mat.ones(6,6, CvType.CV_32F);
        Mat eventMat = Mat.zeros(180,240,CV_8UC1);
        double temp[];
        for(int i = 0; i < events.getSize();i++)
        {
            temp = eventMat.get(events.elementData[i].getY(),events.elementData[i].getX());
            temp[0]++;
            eventMat.put(events.elementData[i].getY(),events.elementData[i].getX(),temp);
        }
        //Next I need to undistort the image
        Mat processedEventMat = eventMat;
        flip(eventMat,processedEventMat,0);
        
        eventMat = Mat.zeros(180,240,CV_8UC1);
        for(int a = 0; a < width; a++)
        {
            for(int b = 0; b < height; b++)
            {
                int newCol = (int) undistortMapX[b][a];
                int newRow = (int) undistortMapY[b][a];
                if(newCol>0 && newCol<= width && newRow>0 && newRow<= height)
                {
                    eventMat.put(newRow, newCol, processedEventMat.get(b,a));
                }
            }
        }
        Point p = new Point(-1,-1);
        
        return eventMat;
    }
}
