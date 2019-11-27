/* 
 * Copyright (C) 2017 Tobi.
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
package ch.unizh.ini.jaer.projects.npp;

import java.beans.PropertyChangeEvent;
import java.util.Arrays;

import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEFrameChipRenderer;

/**
 * Runs CNN on patches generated by accumulated DVS subframes generated by
 * DvsFramerROIGenerator to dynamically update a heat map in a data-driven way.
 *
 * @author Tobi Delbruck
 *
 */
@Description("using data-driven ROIs from DVS events to update heat map by running CNN on each filled ROI")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DavisWhatWhereCNNProcessor extends AbstractDavisCNNProcessor {

    public static final String OUTPUT_AVAILBLE = "outputUpdated";

    private float alpha = getFloat("alpha", 0.2f);

    private int filterx = 0, filtery = 0;  // Output location

    public DavisWhatWhereCNNProcessor(AEChip chip) {
        super(chip);
        String deb = "3. Debug", disp = "1. Display", anal = "2. Analysis";

        dvsFramer = new DvsFramerROIGenerator(chip);
        getEnclosedFilterChain().add(dvsFramer); // only for control, we iterate with it here using the events we recieve
        setEnclosedFilterChain(getEnclosedFilterChain());
        setPropertyTooltip(disp, "alpha", "how opaque the overlay of ROI processing results is drawn");
        initFilter();

    }

//    @Override
//    public synchronized EventPacket<?> filterPacket(EventPacket<?> in) {
//        if (!addedPropertyChangeListener) {
//            if (dvsFramer != null) {
//                dvsFramer.getSupport().addPropertyChangeListener(DvsFramerROIGenerator.EVENT_NEW_ROI_AVAILABLE, this);
//            }
//            if (apsDvsNet != null) {
//                apsDvsNet.getSupport().addPropertyChangeListener(DavisCNNPureJava.EVENT_MADE_DECISION, this);
//            }
//            addedPropertyChangeListener = true;
//        }
//        if (apsDvsNet == null) {
//            log.warning("null network; load one using the LoadApsDvsNetworkFromXML button");
//            return in;
//        }
//
//        resetTimeLimiter();
//        if (!dvsFramer.allocateMemory()) {
//            return in;
//        }
//        for (BasicEvent e : in) {
//
//            if (timeLimiter.isTimedOut()) {
//                break; // discard rest of this packet
//            }
//            lastProcessedEventTimestamp = e.getTimestamp();
//            PolarityEvent p = (PolarityEvent) e;
//
//            dvsFramer.addEvent(p); // generates ROI complete EVENT_NEW_FRAME_AVAILBLE, no need to process frames here
//
//        }
//        return in;
//    }

    @Override
    public void resetFilter() {
        super.resetFilter();
        dvsFramer.resetFilter();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
    }

    @Override
    public synchronized void propertyChange(PropertyChangeEvent evt) {
        if(!isFilterEnabled()) return;
        switch (evt.getPropertyName()) {
            case AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE:
                break;
            case DvsFramer.EVENT_NEW_FRAME_AVAILABLE:
                long startTime = 0;
                if (measurePerformance) {
                    startTime = System.nanoTime();
                }
                DvsFramerROIGenerator.ROI roi = (DvsFramerROIGenerator.ROI) evt.getNewValue();
                apsDvsNet.processDvsFrame(roi); // generates PropertyChange EVENT_MADE_DECISION
                float[] activations = Arrays.copyOf(apsDvsNet.getOutputLayer().getActivations(), apsDvsNet.getOutputLayer().getNumUnits());
                roi.setActivations(activations);
                float[] rgba = Arrays.copyOf(activations, 4);
                // alpha starts at 0, so fully transparent
                if (apsDvsNet.getOutputLayer().getMaxActivatedUnit()!= 3) { // background
                    rgba[3] = alpha; // set very tranparent and show decision as rgb
                } else {
                    Arrays.fill(rgba, 0); // don't show background at all
                }

                roi.setRgba(rgba); // for now just render 4-tuple as RGBA

                if (measurePerformance) {
                    long dt = System.nanoTime() - startTime;
                    float ms = 1e-6f * dt;
                    float fps = 1e3f / ms;
                    performanceString = String.format("Frame processing time: %.1fms (%.1f FPS); %s", ms, fps, apsDvsNet.getPerformanceString());
                }

                break;
            default:

        }

    }

    /**
     * @return the alpha
     */
    public float getAlpha() {
        return alpha;
    }

    /**
     * @param alpha the alpha to set
     */
    public void setAlpha(float alpha) {
        if (alpha < 0) {
            alpha = 0;
        } else if (alpha > 1) {
            alpha = 1;
        }
        this.alpha = alpha;
        putFloat("alpha", alpha);
    }

}
