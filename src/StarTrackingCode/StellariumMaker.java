package StarTrackingCode;


import java.io.IOException;
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
public class StellariumMaker {
    private int scaleDownFactor = 8;
    private int SI_height = 1080;
    private int SI_width = 1920;
    private int zero = 0;

    private int scaledDownSI_height = SI_height/scaleDownFactor;
    private int scaledDownSI_width = SI_width/scaleDownFactor;
    
    public Mat getStellarium(Mat input) throws IOException
    {   
        
        return input;
    }
    
    public StellariumMaker()
    {
        
    }
}
