/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.util;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;

/**
 * Static utility methods for drawing stuff.
 * 
 * @author Bjoern
 */
public final class DrawGL {
    
    /**
     * Don't let anyone instantiate this class.
     */
    private DrawGL() {}
    
   
    /** Draws an arrow vector using current open gl color, starting from 0,0, using head length 1 and scaling 1
      * 
      * @param gl the opengl context
      * @param headX The x length of arrow
      * @param headY the y length of arrow
      */
    public static void drawVector(GL2 gl, float headX, float headY) { drawVector(gl,0,0,headX,headY,1,1); }
    /** Draws an arrow vector using current open gl color, using headlength 1 and scaling 1
      * 
      * @param gl the opengl context
      * @param origX the arrow origin location x
      * @param origY the arrow origin location x
      * @param headX The x length of arrow
      * @param headY the y length of arrow
      */
    public static void drawVector(GL2 gl, float origX, float origY, float headX, float headY) { drawVector(gl,origX,origY,headX,headY,1,1); }
     /** Draws an arrow vector using current open gl color
      * 
      * @param gl the opengl context
      * @param origX the arrow origin location x
      * @param origY the arrow origin location x
      * @param headX The x length of arrow
      * @param headY the y length of arrow
      * @param headlength the length of the arrow tip segments as fraction of entire arrow length, after scaling
      * @param scale the scaling used for drawing the arrow
      */
    public static void drawVector(GL2 gl, float origX, float origY, float headX, float headY, float headlength, float scale) {
        float endx = headX*scale, endy = headY*scale;
        float arx  = -endx+endy,  ary  = -endx-endy;   // halfway between pointing back to origin
        float l = (float)Math.sqrt((arx*arx)+(ary*ary)); // length
        arx = (arx/l)*headlength;   ary  = (ary/l)*headlength; // normalize to headlength
        
        gl.glTranslatef(origX, origY, 0);
        
        gl.glBegin(GL2.GL_LINES);
        {
            gl.glVertex2f(0,0);
            gl.glVertex2f(endx,endy);
            // draw arrow (half)
            gl.glVertex2f(endx,endy);
            gl.glVertex2f(endx+arx, endy+ary);
            // other half, 90 degrees
            gl.glVertex2f(endx,endy);
            gl.glVertex2f(endx+ary, endy-arx);
        }
        gl.glEnd();
    }
    
    public static void drawBox(GL2 gl, float centerX, float centerY, float width, float height, float angle) {
        final float r2d = (float) (180 / Math.PI);
        final float w = width/2, h = height/2;
        
        gl.glTranslatef(centerX, centerY, 0);
        if(angle!=0) gl.glRotatef(angle * r2d, 0, 0, 1);

        gl.glBegin(GL.GL_LINE_LOOP);
        {
            gl.glVertex2f(-w, -h);
            gl.glVertex2f(+w, -h);
            gl.glVertex2f(+w, +h);
            gl.glVertex2f(-w, +h);
        }
        gl.glEnd();
    }
    
    /**
     * Draws ellipse. Set the line width before drawing, and push and pop matrix
     * @param gl
     * @param centerX
     * @param centerY
     * @param radiusX
     * @param radiusY
     * @param angle
     * @param N number of segments used to draw ellipse
     */
    public static void drawEllipse(GL2 gl, float centerX, float centerY, float radiusX, float radiusY, float angle, int N) {
        final float r2d = (float) (180 / Math.PI);
        
        gl.glTranslatef(centerX, centerY, 0);
        if(angle!=0) gl.glRotatef(angle * r2d, 0, 0, 1);
        
        gl.glBegin(GL.GL_LINE_LOOP);
        {
            for (int i = 0; i < N; i++) {
                double a = ((float) i / N) * 2 * Math.PI;
                double cosA = Math.cos(a);
                double sinA = Math.sin(a);

                gl.glVertex2d(radiusX * cosA, radiusY * sinA);
            }
        }
        gl.glEnd();
    }
    
    /** Draws a circle. Set the line width before drawing, and push and pop matrix
     * 
     * @param gl
     * @param centerX
     * @param centerY
     * @param radius
     * @param N number of segments used to draw ellipse
     */
    public static void drawCircle(GL2 gl, float centerX, float centerY, float radius, int N) {
        drawEllipse(gl,centerX,centerY,radius,radius,0,N);
    }
    
    /** Draws a line. Set the line width before drawing, and push and pop matrix.
     * 
     * @param gl
     * @param startX
     * @param startY
     * @param lengthX
     * @param lengthY
     * @param scale  scales the line length by this factor
     */
    public static void drawLine(GL2 gl, float startX, float startY, float lengthX, float lengthY, float scale) {
        gl.glTranslatef(startX, startY, 0);
        
        gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0,0);
            gl.glVertex2f(lengthX*scale,lengthY*scale);
        gl.glEnd();
    }
}
