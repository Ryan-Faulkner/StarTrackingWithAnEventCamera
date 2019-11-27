


import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.*;

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
public class GUI 
{
    myMouseListener mml;
    private JFrame frame;
    private iconPanel panel;
     long millis;
    public GUI()
    { 
        mml = new myMouseListener();
        long millis=System.currentTimeMillis();
       frame = new JFrame("Events Received");
       frame.setSize(600, 500); 
       panel = new iconPanel();
       panel.addMouseListener(mml);
       frame.add(panel);
       //frame.pack();
       frame.setVisible(true);
       frame.paintAll(frame.getGraphics());
       
    }
    public void displayImage(BufferedImage bufImage)
    {
        //System.out.println(System.currentTimeMillis()-millis);
        millis=System.currentTimeMillis();
       panel.setPic(bufImage);
       frame.repaint();
    }
    
}
class iconPanel extends JPanel 
{
    private BufferedImage bufImage;
    public iconPanel() 
    {
          this.setBackground(new Color(0, true));
          bufImage = new BufferedImage(180,240,1);
    }

    @Override
    protected void paintComponent(Graphics g) 
    {
    super.paintComponent(g);
    g.drawImage(bufImage, 0, 0, bufImage.getWidth(), bufImage.getHeight(), this);
    } 
    public void setPic(BufferedImage image) 
    {
    
    this.bufImage = image;
    repaint();
    }
}