package StarTrackingCode;

import javax.swing.JPopupMenu;
import javax.swing.ToolTipManager;
import stdDraw3D.StdDraw3D;
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
public class Gui3D {
    double storedx;
    double storedy;
    double storedz;
    public Gui3D()
    {
        storedx = 0;
        storedy = 0;
        storedz = 0;
      System.setProperty("sun.awt.ncerasebackground","true");
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        ToolTipManager ttm = ToolTipManager.sharedInstance();
        ttm.setLightWeightPopupEnabled(false);
        
            
        StdDraw3D.setScale(-1,3);
        //StdDraw3D.box(0,1,0,0.5,0.5,1,0,0,0);
        StdDraw3D.box(1,1,0,0.5,0.5,1,0,0,0);
        StdDraw3D.show(1);
    }
    public void updateRotation(double x,double y,double z)
    {
        if(x < Math.PI/4 && y < Math.PI/4 && z < Math.PI/8 )
        {
            storedx+=x*180/Math.PI;
            storedy+=y*-180/Math.PI;
            storedz+=z*-180/Math.PI;
            StdDraw3D.clear(StdDraw3D.BLACK);
            StdDraw3D.box(0,0,0,0.5,0.5,1,storedx,storedy,storedz);
            StdDraw3D.show(1);
        }
    }
    public void updateRotationAbs(double x,double y,double z)
    {
        x *= 180/Math.PI;
        y*=-180/Math.PI;
        z*=-180/Math.PI;

        //StdDraw3D.clear(StdDraw3D.BLACK);
        StdDraw3D.box(0,1,0,0.5,0.5,1,x,y,z);
        StdDraw3D.show(1);
    }
    public void updateRotationAbs2(double x,double y,double z)
    {
        x *= 180/Math.PI;
        y*=-180/Math.PI;
        z*=-180/Math.PI;

        StdDraw3D.clear(StdDraw3D.BLACK);
        StdDraw3D.box(1,1,0,0.5,0.5,1,x,y,z);
        StdDraw3D.show(1);
    }
    
    public void updateRotationAbs3(double x,double y,double z)
    {
        x *= -180/Math.PI;
        y*=180/Math.PI;
        z*=-180/Math.PI;

        StdDraw3D.clear(StdDraw3D.BLACK);
        StdDraw3D.cone(0,0,0,0.5,1,x-90,y,z);
        StdDraw3D.show(1);
    }
    
    
}
