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


import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;

import com.jogamp.opengl.GL2;
import ch.unizh.ini.jaer.projects.npp.DvsFramer.DvsFrame;
import eu.visualize.ini.convnet.EasyXMLReader;

import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ImageDisplay;
import org.tensorflow.Tensor;

/**
 * Simple convolutional neural network (CNN) data structure to hold CNN from
 * Matlab DeepLearnToolbox or Caffe. Replicates the computation in matlab
 * function cnnff.m, which is as follows:
 * <pre>
 *
 * function net = cnnff(net, x)
 * n = numel(net.layers);
 * net.layers{1}.activations{1} = x;
 * inputmaps = 1;
 * * for l = 2 : n   %  for each layer
 * if strcmp(net.layers{l}.type, 'c')
 * %  !!below can probably be handled by insane matrix operations
 * for j = 1 : net.layers{l}.outputmaps   %  for each output map
 * %  create temp output map
 * z = zeros(size(net.layers{l - 1}.activations{1}) - [net.layers{l}.kernelsize - 1 net.layers{l}.kernelsize - 1 0]);
 * for i = 1 : inputmaps   %  for each input map
 * %  convolve with corresponding kernel and add to temp output map
 * z = z + convn(net.layers{l - 1}.activations{i}, net.layers{l}.k{i}{j}, 'valid');
 * end
 * %  add bias, pass through nonlinearity
 * net.layers{l}.activations{j} = sigm(z + net.layers{l}.b{j});
 * end
 * %  set number of input maps to this layers number of outputmaps
 * inputmaps = net.layers{l}.outputmaps;
 * elseif strcmp(net.layers{l}.type, 's')
 * %  downsample
 * for j = 1 : inputmaps
 * z = convn(net.layers{l - 1}.activations{j}, ones(net.layers{l}.scale) / (net.layers{l}.scale ^ 2), 'valid');   %  !! replace with variable
 * net.layers{l}.activations{j} = z(1 : net.layers{l}.scale : end, 1 : net.layers{l}.scale : end, :);
 * end
 * end
 * end
 *
 * %  concatenate all end layer feature maps into vector
 * net.fv = [];
 * for j = 1 : numel(net.layers{n}.activations)
 * sa = size(net.layers{n}.activations{j});
 * net.fv = [net.fv; reshape(net.layers{n}.activations{j}, sa(1) * sa(2), sa(3))];
 * end
 * %  feedforward into output perceptrons
 * net.o = sigm(net.ffW * net.fv + repmat(net.ffb, 1, size(net.fv, 2)));
 *
 * end
 *
 *
 * </pre>
 *
 * @author tobi
 */
public class DavisCNNPureJava extends AbstractDavisCNN {

    protected int nLayers;
    protected Layer[] layers; // all the layers in the middle
    public InputLayer inputLayer; // part of layers
    public OutputOrInnerProductFullyConnectedLayer outputLayer; // the final layer, not part of layers
    protected boolean normalizeKernelDisplayWeightsGlobally = true;
    protected boolean normalizeActivationDisplayGlobally = true;

    public DavisCNNPureJava(AbstractDavisCNNProcessor processor) {
        super(processor);
    }
  
    /**
     * Process a DVS frame
     *
     * @param subsampler
     * @return the output activations vector
     */
    public float[] processDvsFrame(DvsFrame subsampler) {
        inputLayer.processDvsTimeslice(subsampler);
        setLastInputTypeProcessedWasApsFrame(false);
        return processLayers();
    }

    /**
     * Computes the output of the network from an input activationsFrame
     *
     * @param frame the renderer that rendered the APS output
     * @return the vector of output values
     * @see #getActivations
     */
    public float[] processAPSFrame(ApsFrameExtractor frame) {
        if (inputLayer == null) {
            return null;
        }
        inputLayer.processDownsampledFrame(frame);
        setLastInputTypeProcessedWasApsFrame(true);
        return processLayers();
    }

    /**
     * Computes the output of the network from an input activationsFrame
     *
     * @param frame the renderer that rendered the APS output
     * @param offX x offset of the patch
     * @param offY y offset of the patch
     * @return the vector of output values
     * @see #getActivations
     */
    public float[] processInputPatchFrame(AEFrameChipRenderer frame, int offX, int offY) {
        inputLayer.processInputFramePatch(frame, offX, offY);
        setLastInputTypeProcessedWasApsFrame(true);
        return processLayers();
    }

    /**
     * Process network given an input layer.
     *
     * @param inputLayerinput
     * @return the network output
     */
    private float[] processNetwork(InputLayer inputLayerInput) {
        this.inputLayer = inputLayerInput;
        return processLayers();
    }

    // single point of entry for processing
    private float[] processLayers() {
        operationCounter = 0;
        startProcessingTimeNs = System.nanoTime();
        for (int i = 1; i < nLayers; i++) { // skip input layer, whose activations are computed by reading in frame and downsampling it
            layers[i].compute(layers[i - 1]);
        }
        outputLayer.compute(layers[nLayers - 1]);
        if (isSoftMaxOutput()) {
            outputLayer.computeSoftMax();
        }

        if (isPrintActivations()) {
            printActivations();
        }
        if (isPrintWeights()) {
            printWeights();
        }
        processingTimeNs = System.nanoTime() - startProcessingTimeNs;
        networkRanOnce = true;
        getSupport().firePropertyChange(EVENT_MADE_DECISION, null, this);
        return outputLayer.activations;
    }
    
  
    public void drawActivations() {
        checkActivationsFrame();
        if (layers == null) {
            return;
        }
        for (Layer l : layers) {
            if ((l instanceof ConvLayer) && hideConvLayers) {
                continue;
            }
            if ((l instanceof SubsamplingLayer) && hideSubsamplingLayers) {
                continue;
            }
            l.drawActivations();
        }
        if (outputLayer != null) {
            outputLayer.drawActivations();
        }
        if (!activationsFrame.isVisible()) {
            activationsFrame.setVisible(true);
        }

    }

    @Override
    public void printActivations() {
        System.out.println("\n\n\n****************************************************\nActivations");
        for (Layer l : layers) {
            l.printActivations();
        }
        if (outputLayer != null) {
            outputLayer.printActivations();
        }
    }

    @Override
    public void printWeights() {
        System.out.println("\n\n\n****************************************************\nWeights");
        for (Layer l : layers) {
            l.printWeights();
        }
        if (outputLayer != null) {
            outputLayer.printWeights();
        }
    }

    public JFrame drawKernels() {
        checkKernelsFrame();
        for (Layer l : layers) {
            if (l instanceof ConvLayer) {
                ((ConvLayer) l).drawKernels();
//                break; // DEBUG only first layer
            }
        }
        if (!kernelsFrame.isVisible()) {
            kernelsFrame.setVisible(true);
            return kernelsFrame;
        }
        kernelsFrame.repaint();
        return kernelsFrame;
    }

    private float[] readFloatArray(EasyXMLReader layerReader, String name) {
        String dt = layerReader.getAttrValue(name, "dt");
        float[] f = null;
        switch (dt) {
            case "base64-single":
                f = layerReader.getBase64FloatArr(name);
                break;
            case "ASCII-float32":
                f = layerReader.getAsciiFloatArr(name);
                break;
            default:
                throw new RuntimeException("bad datatype dt=" + dt + "; should be base64-single or ASCII-float32");
        }
        return f;
    }

    /**
     * The logistic function
     *
     * @param v
     * @return
     */
    private float sigm(float v) {
        return (float) (1.0 / (1.0 + Math.exp(-v)));
    }

    private float relu(float v) {
        if (v <= 0) {
            return 0;
        } else {
            return v;
        }
    }

    @Override
    public InputLayer getInputLayer() {
        return inputLayer;
    }

    @Override
    public OutputLayer getOutputLayer() {
        return outputLayer;
    }

    @Override
    public Tensor processAPSDVSFrame(APSDVSFrame frame) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

 

    abstract public class Layer extends AbstractDavisCNN.Layer {

        public float[] activations;

        public Layer(int index) {
            super(index);
        }

        /**
         * Return the activation (output) of this layer
         *
         * @param map the output map
         * @param x
         * @param y
         * @return activation from 0-1
         */
        abstract public float a(int map, int x, int y);

        /**
         * Computes activations from input layer
         *
         * @param input the input layer to processAPSFrame from
         */
        abstract public void compute(Layer input);
    }

    /**
     * Represents input to network; computes the sub/down sampled input from
     * image activationsFrame. Order of entries in activations is the same as in
     * matlab, first column on left from row=0 to height, 2nd column, etc.
     */
    public class InputLayer extends Layer implements AbstractDavisCNN.InputLayer {

        protected int width;
        protected int height;
        protected int numChannels = 1; // TODO generalize to more channels

        public InputLayer(int index) {
            super(index);
        }

        private boolean inputClampedTo1 = false; // for debug
        private boolean inputClampedToIncreasingIntegers = false; // debug
        public int nUnits;
        private ImageDisplay imageDisplay = null;

        /**
         * Computes the output from input frame.
         *
         * @param frameExtractor the image comes from this frame extractor
         * @return the vector of input layer activations
         */
        public float[] processDownsampledFrame(ApsFrameExtractor frameExtractor) {
//            if (frame == null || frameWidth == 0 || (frame.length / type.samplesPerPixel()) % frameWidth != 0) {
//                throw new IllegalArgumentException("input frame is null or frame array length is not a multiple of width=" + frameWidth);
//            }
            if (activations == null) {
                activations = new float[nUnits];
            }
            int frameHeight = frameExtractor.getChip().getSizeY();
            int frameWidth = frameExtractor.getChip().getSizeX();
            // subsample input activationsFrame to width height
            // activationsFrame has width*height pixels
            // for first pass we just downsample every width/dimx pixel in x and every height/dimy pixel in y
            // TODO change to subsample (averaging)
            Random r = null;
            if (inputClampedToIncreasingIntegers) {
                r = new Random();
                r.setSeed(0);
            }
            float xstride = (float) frameWidth / width, ystride = (float) frameHeight / height;
            int xo = 0, yo = 0;
            loop:
            for (float y = 0; Math.ceil(y) < frameHeight; y += ystride) {
                xo = 0;
                for (float x = 0; Math.ceil(x) < frameWidth; x += xstride) {  // take every xstride, ystride pixels as output
                    float v = 0;
                    final int xfloor = (int) Math.floor(x);
                    final int yfloor = (int) Math.floor(y);
                    v = frameExtractor.getNewFrame()[frameExtractor.getIndex(xfloor, yfloor)];
                    v = debugNet(v, xo, yo);  // TODO remove only for debug
                    final int o = o(xo, height - yo - 1);
//                    System.out.println(String.format("x=%9.3f y=%9.3f xfloor=%9d yfloor=%9d xo=%6d yo=%6d",x,y,xfloor,yfloor,xo,yo));
//                    if(o>=activations.length){
//                        log.warning("out of bounds exception indexing to activations array. Place a breakpoint here to debug");
//                    }
                    activations[o] = v;
                    xo++;
                    operationCounter += 4;
                }
                yo++;
            }
            normalizeInputFrame(activations, true);
            return activations;
        }

        /**
         * Computes the output from input frame.
         *
         * @param renderer the image comes from this image displayed in AEViewer
         * @param xOffset x offset of the patch
         * @param yOffset y offset of tye patch
         * @return the vector of input layer activations
         */
        public float[] processInputFramePatch(AEFrameChipRenderer renderer, int xOffset, int yOffset) {
//            if (frame == null || frameWidth == 0 || (frame.length / type.samplesPerPixel()) % frameWidth != 0) {
//                throw new IllegalArgumentException("input frame is null or frame array length is not a multiple of width=" + frameWidth);
//            }
            if (activations == null) {
                activations = new float[nUnits];
            }
            int frameHeight = renderer.getChip().getSizeY();
            int frameWidth = renderer.getChip().getSizeX();
            int dimx2 = width / 2;
            int dimy2 = height / 2;
            if ((xOffset < dimx2) || (xOffset > (frameWidth - dimx2)) || (yOffset < dimy2) || (xOffset > (frameWidth - dimy2))) {
                log.warning("Cannot process input frame patch with x offset: " + xOffset + " and y Offset: " + yOffset + " because frame measures only " + frameWidth + " x " + frameHeight);
                return null;
            }
            for (int y = yOffset - dimy2; y < (yOffset + dimy2); y++) {
                for (int x = xOffset - dimx2; x < (xOffset + dimx2); x++) {  // take every xstride, ystride pixels as output
                    float v = 0;
                    renderer.getPixMapIndex((int) Math.floor(x), (int) Math.floor(y));

                    v = renderer.getApsGrayValueAtPixel((int) Math.floor(x), (int) Math.floor(y));
                    // v = debugNet(v, x, y);  // TODO remove only for debug

                    activations[o(x - (xOffset - dimx2), height - (y - (yOffset - dimy2)) - 1)] = v * 1024;

                    // activations[o(height - y % height- 1, x % width )] = v; // NOTE transpose and flip of image here which is actually the case in matlab code (image must be drawn in matlab as transpose to be correct orientation)
                }
            }
            // normalizeInputFrame(activations, true);
            return activations;
        }

        /**
         * Computes the output from input frame. The frame can be either a
 gray-scale frame[] with a single entry per pixel, or it can be an RGB
 frame[] with 3 sample (RGB) per pixel. The appropriate extraction is
 done in processAPSFrame by the FrameType parameter.
         *
         * @param subsampler the DVS subsampled input
         * @return the vector of network output values
         */
        public float[] processDvsTimeslice(DvsFrame subsampler) {
//            if (frame == null || frameWidth == 0 || (frame.length / type.samplesPerPixel()) % frameWidth != 0) {
//                throw new IllegalArgumentException("input frame is null or frame array length is not a multiple of width=" + frameWidth);
//            }

            if (activations == null) {
                activations = new float[nUnits];
            }
            if (subsampler == null) {
                return activations;
            }

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < height; x++) {
                    // range is 0-1 in subsampler after normalization; just leave it there. Zero count pixels have value 127/255 in case subsampler does not rectify, and zero if it does.
                    float v = (subsampler.getValueAtPixel(x, y)); // already normalized before PropertyChangeEvent EVENT_NEW_FRAME_AVAILBLE
                    v = debugNet(v, x, y);
                    activations[o(x, height - y - 1)] = v;
                }
            }
            return activations;
        }

        private int o(int x, int y) {
            if (((height * x) + y) < 0) {
                System.out.print("a");
            }
            return (int) ((height * x) + y);  // activations of input layer are stored by column and then row, as in matlab array that is taken by (:)
        }

        @Override
        public void compute(Layer input) {
            throw new UnsupportedOperationException("Input layer only computes on input frame, not previous layer output; use compute(frame[] f, ...) method");
        }

        @Override
        public String toString() {
            return String.format("index=%d Input layer; dimx=%d dimy=%d nUnits=%d",
                    index, width, height, nUnits);
        }

        @Override
        public void printActivations() {
            super.printActivations();
            System.out.println("Activations:");
            for (int y = 0; y < height; y++) {
                System.out.print(String.format("y=%6d ", y));
                for (int x = 0; x < width; x++) {
                    System.out.print(String.format("%6.2g ", activations[o(x, y)]));
                }
                System.out.println("");
            }
        }

        @Override
        public void drawActivations() {
            if (!isVisible() || (activations == null)) {
                return;
            }
            checkActivationsFrame();
            if (imageDisplay == null) {
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                imageDisplay = ImageDisplay.createOpenGLCanvas();
                imageDisplay.setBorderSpacePixels(0);
                imageDisplay.setImageSize(width, height);
                imageDisplay.setSize(200, 200);
                panel.add(imageDisplay);

                activationsFrame.getContentPane().add(panel);
                activationsFrame.pack();
            }
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    imageDisplay.setPixmapGray(x, width - y - 1, (a(0, x, y))); // try to fit mean 0 std 1 into 0-1 range nicely by offset and gain of .5
                }
            }
            imageDisplay.repaint();
        }

        @Override
        public final float a(int map, int x, int y) {
            return activations[o(x, y)];
        }

        /**
         * @return the inputClampedTo1
         */
        public boolean isInputClampedTo1() {
            return inputClampedTo1;
        }

        /**
         * @param inputClampedTo1 the inputClampedTo1 to set
         */
        public void setInputClampedTo1(boolean inputClampedTo1) {
            this.inputClampedTo1 = inputClampedTo1; // TODO remove debug
        }

        /**
         * @return the inputClampedToIncreasingIntegers
         */
        public boolean isInputClampedToIncreasingIntegers() {  // only for debug
            return inputClampedToIncreasingIntegers; // TODO remove debug
        }

        /**
         * @param inputClampedToIncreasingIntegers the
         * inputClampedToIncreasingIntegers to set
         */
        public void setInputClampedToIncreasingIntegers(boolean inputClampedToIncreasingIntegers) {
            this.inputClampedToIncreasingIntegers = inputClampedToIncreasingIntegers;
        }

        /**
         * Normalizes frames to have zero mean and variance=1
         *
         * @param activations
         * @param aps set true to normalize image, false to normalize DVS (don't
         * subtract mean)
         */
        private void normalizeInputFrame(float[] activations, boolean aps) {
            if (inputClampedTo1 || inputClampedToIncreasingIntegers) {
                return;
            }
            // net trained gets 0-1 range inputs, so make our input so
            int n = activations.length;
            float sum = 0, sum2 = 0, var = 0, vari = 0;
            final float mid = .5f;
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                sum += activations[i];
            }
            float mean = sum / n;
            if (aps) {
                for (int i = 0; i < n; i++) {
                    vari = (float) Math.pow((activations[i] - mean), 2);
                    var += vari;
                }
                var = (var / n);
                float sig = (float) Math.sqrt(var);
                if (sig < 0.1f / 255.0f) {
                    sig = 0.1f / 255.0f;
                }
                for (int i = 0; i < n; i++) {
                    activations[i] = (activations[i] - mean) / sig;
                }
                for (int i = 0; i < n; i++) {
                    if (activations[i] < min) {
                        min = activations[i];
                    }
                    if (activations[i] > max) {
                        max = activations[i];
                    }
                    operationCounter += 4;
                }
                float range = (max - min);
                float rangenew = (1 - 0);
                for (int i = 0; i < n; i++) {
                    activations[i] = (((activations[i])) - min) * rangenew / range;
                    operationCounter += 4;
                }
            } else {// note that DVS histograme frame normalization may be done by DvsFramer if normalizeDVSForZsNullhop is set
                float mean_png_gray = 127.0f / 255.0f;
                for (int i = 0; i < n; i++) {
                    vari = (float) Math.pow((activations[i] - mean), 2);
                    var += vari;
                }
                var = (var / n);
                float sig = (float) Math.sqrt(var);
                if (sig < 0.1f / 255.0f) {
                    sig = 0.1f / 255.0f;
                }
                for (int i = 0; i < n; i++) {
                    activations[i] = (activations[i] - mean_png_gray); // note that pixels with zero count are NOT left at mean_png_gray!
                }
                float range = ((3.f * sig) - (-3.f * sig));
                float rangenew = (1 - 0);
                for (int i = 0; i < n; i++) {
                    activations[i] = (((activations[i])) - (-3.0f * sig)) * rangenew / range; // note that outliers are NOT clipped to 0-1 range!
                    operationCounter += 4;
                }
            }
        }

        private float debugNet(float v, int x, int y) {
            if (inputClampedToIncreasingIntegers) {
                v = (float) (x + y) / (width + height);
            } else if (inputClampedTo1) {
                v = 1.0f;
            }
            return v;
        }

        @Override
        synchronized public void cleanupGraphics() {
            imageDisplay = null;
        }

        /**
         * @return the width
         */
        public int getWidth() {
            return width;
        }

        /**
         * @return the height
         */
        public int getHeight() {
            return height;
        }

        /**
         * @return the numChannels
         */
        public int getNumChannels() {
            return numChannels;
        }
    }

    /**
     * The allowed activation functions for the CNN convolutional and final
     * layers
     */
    public enum ActivationFunction {

        Sigmoid, ReLu, None, Undefined;
    };

    /**
     * The allowed activation functions for the CNN convolutional and final
     * layers
     */
    public enum PoolingType {

        Average, Max, Undefined
    };

    /**
     * A convolutional layer. Does this operation:
     * <pre>
     * for j = 1 : net.layers{l}.outputmaps   %  for each output map
     * %  create temp output map
     *
     *
     * %the output map dimension is reduced from input map dimension by the (dim of kernel-1).
     * %That is because the kernel starts convolving inset by kernel dim/2, and it ends at kernel dim/2 from other side.
     *
     * %Another way to say it is that kernel.0 starts on left at input.0 and kernel.n starts at n.
     * %kernel.n ends at input.m. Therefore m-n+1 is the number of steps, or m-(n-1) is the number of steps.
     *
     * z = zeros(size(net.layers{l - 1}.activations{1}) - [net.layers{l}.kernelsize - 1 net.layers{l}.kernelsize - 1 0]);
     * for i = 1 : inputmaps   %  for each input map
     * %  convolve with corresponding kernel and add to temp output map
     * z = z + convn(net.layers{l - 1}.activations{i}, net.layers{l}.k{i}{j}, 'valid');
     * end
     * %  add bias, pass through nonlinearity
     * net.layers{l}.activations{j} = sigm(z + net.layers{l}.b{j});
     *
     * <pre>
     *
     * Note that for each of I input maps there are O kernels corresponding the the O output maps. Therefore there are I*O kernels.
     *
     * The activation function is selectable to either Sigmoid or ReLu
     *
     */
    public class ConvLayer extends Layer {

        private int nInputMaps;
        private int nOutputMaps; //
        private int kernelDim, singleKernelLength, halfKernelDim, kernelWeightsPerOutputMap, nKernels;
        private float[] biases;
        float minWeight = Float.POSITIVE_INFINITY, maxWeight = Float.NEGATIVE_INFINITY;
        /**
         * @see #k(int, int, int, int)
         */
        private float[] kernels;
        private int inputMapLength; // length of single input map out of input.activations
        private int inputMapDim; // size of single input map, sqrt of inputMapLength for square input (TODO assumes square input)
        private int outputMapLength; // length of single output map vector; biases.length/nOutputMaps, calculated during processAPSFrame()
        private int outputMapDim;  // dimension of single output map, calculated during processAPSFrame()
        private int activationsLength;
        private ImageDisplay[] activationDisplays = null;
        private ImageDisplay[][] kernelDisplays = null;
        private int warningCountMax = 10;

        private ActivationFunction activationFunction = ActivationFunction.Undefined; // default is the sigmoid, the only choice in DeepLearnToolbox

        public ConvLayer(int index) {
            super(index);
        }

        @Override
        public String toString() {
            return String.format("index=%d CNN   layer; nInputMaps=%d nOutputMaps=%d kernelSize=%d biases=float[%d] kernels=float[%d] activationFunction=%s",
                    index, nInputMaps, nOutputMaps, kernelDim, biases == null ? 0 : biases.length, kernels == null ? 0 : kernels.length, activationFunction.toString());
        }

        @Override
        public void printActivations() {
            super.printActivations();
            System.out.println("Activations:");
            for (int map = 0; map < nOutputMaps; map++) {
                System.out.print(String.format("map=%6d\n", map));
                for (int y = 0; y < outputMapDim; y++) {
                    System.out.print(String.format("y=%6d ", y));
                    for (int x = 0; x < outputMapDim; x++) {
                        System.out.print(String.format("%6.2g ", activations[o(map, x, y)]));
                    }
                    System.out.println("");
                }
            }
        }

        @Override
        public void printWeights() {
            super.printWeights();
            System.out.println("Biases:");
            for (float biase : biases) {
                System.out.print(String.format("%+6.3g ", biase));
            }
            System.out.println("");
            System.out.println("Weights:");
            for (int kernel = 0; kernel < nOutputMaps; kernel++) {
                System.out.println("Output map #" + kernel);
                for (int inputFeatureMapNumber = 0; inputFeatureMapNumber < nInputMaps; inputFeatureMapNumber++) {
                    System.out.println("Input map #" + inputFeatureMapNumber);
                    System.out.print("x= ");
                    for (int x = 0; x < kernelDim; x++) {
                        System.out.print(String.format("%7d ", x));
                    }
                    System.out.print("\n  ");

                    for (int y = 0; y < kernelDim; y++) {
                        for (int x = 0; x < kernelDim; x++) {
                            System.out.print(String.format("%+6.2g ", kernels[k(inputFeatureMapNumber, kernel, x, y)]));
                        }
                        System.out.print("\n  ");

                    }
                }
            }
        }

        @Override
        public void initializeConstants() {
            singleKernelLength = kernelDim * kernelDim;
            halfKernelDim = kernelDim / 2;
            // output size can only be computed once we know our input
            for (float kernel : kernels) {
                if (kernel < minWeight) {
                    minWeight = kernel;
                } else if (kernel > maxWeight) {
                    maxWeight = kernel;
                }
            }
        }

        private float normalizedWeight(float w) {
            if (normalizeKernelDisplayWeightsGlobally) {
                return ((w - minWeight) / (maxWeight - minWeight));
            } else {
                return w;
            }
        }

        /**
         * Computes convolutions of input kernels with input maps
         *
         * @param inputLayer the input to this layer, represented by nInputMaps
         * on the activations array
         */
        @Override
        public void compute(Layer inputLayer) {
            if (inputLayer.activations == null) {
                log.warning("input.activations==null");
                return;
            }
            if ((inputLayer.activations.length % nInputMaps) != 0 && warningCountMax-- > 0) {
                log.warning("input.activations.length=" + inputLayer.activations.length + " which is not divisible by nInputMaps=" + nInputMaps);
            }
            inputMapLength = inputLayer.activations.length / nInputMaps; // for computing indexing to input
            double sqrtInputMapLength = Math.sqrt(inputMapLength);
            if (Math.IEEEremainder(sqrtInputMapLength, 1) != 0 && warningCountMax-- > 0) {
                log.warning("input map is not square; Math.rint(sqrtInputMapLength)=" + Math.rint(sqrtInputMapLength));
            }
            nKernels = nInputMaps * nOutputMaps;
            inputMapDim = (int) sqrtInputMapLength;
            if (!zeroPadding) {
                outputMapDim = (inputMapDim - kernelDim) + 1;
            } else {
                outputMapDim = inputMapDim;
            }
            outputMapLength = outputMapDim * outputMapDim;
            activationsLength = outputMapLength * nOutputMaps;
            kernelWeightsPerOutputMap = singleKernelLength * nOutputMaps;

            if (nOutputMaps != biases.length && warningCountMax-- > 0) {
                log.warning("nOutputMaps!=biases.length: " + nOutputMaps + "!=" + biases.length);
            }

            if ((activations == null) || (activations.length != activationsLength)) {
                activations = new float[activationsLength];
            } else {
                Arrays.fill(activations, 0);  // clear the output, since results from inputMaps will be accumulated
            }

            for (int inputMap = 0; inputMap < nInputMaps; inputMap++) { // for each inputMap
                for (int outputMap = 0; outputMap < nOutputMaps; outputMap++) { // for each kernel/outputMap
                    conv(inputLayer, outputMap, inputMap);
                }
            }

            applyBiasAndNonlinearity();
        }

        // convolves a given kernel over the inputMap and accumulates output to activations
        private void conv(Layer inputLayer, int outputMap, int inputMap) {
            int startx = halfKernelDim, starty = halfKernelDim, endx = inputMapDim - halfKernelDim, endy = inputMapDim - halfKernelDim;
            float[][] newInputArray;
            newInputArray = new float[inputMapDim + 2 * halfKernelDim][inputMapDim + 2 * halfKernelDim];
            if (zeroPadding) {
                startx = halfKernelDim;
                starty = halfKernelDim;
                endx = inputMapDim + halfKernelDim;
                endy = inputMapDim + halfKernelDim;
                for (int xi = halfKernelDim; xi <= inputMapDim - halfKernelDim; xi++) { // index to outputMap
                    for (int yi = halfKernelDim; yi <= inputMapDim - halfKernelDim; yi++) {
                        newInputArray[xi][yi] = inputLayer.a(inputMap, xi - halfKernelDim, yi - halfKernelDim);
                    }
                }
            }

            int xo = 0, yo;
            for (int xi = startx; xi < endx; xi++) { // index to outputMap
                yo = 0;
                for (int yi = starty; yi < endy; yi++) {
                    int outidx = o(outputMap, xo, yo);
                    if (!zeroPadding) {
                        activations[outidx] += convsingle(inputLayer, outputMap, inputMap, xi, yi);
                    } else {
                        activations[outidx] += convsingle_zp(newInputArray, outputMap, inputMap, xi, yi);
                    }
                    yo++;
                }
                xo++;
            }
        }

        // computes single kernel inner product summed result centered on x,y in inputMap
        // DANGER DANGER - note that in matlab the conv2/convn function do convolutions by flipping in x and y (not transposing, but mirroring) the kernel matrix and then doing 2d sum-of-products.
        // So the sum-of-product results are not just the sum of products of corresponding x,y entries.
        private float convsingle(Layer input, int outputMap, int inputMap, int xincenter, int yincenter) {
            float sum = 0;
//            int nterms=0;
            // march over kernel y and x
            if (getNettype().equals("caffe_net")) {
                for (int xx = 0; xx < kernelDim; xx++) { // kernel coordinate
                    int inx = (xincenter + xx) - halfKernelDim; // input coordinate
                    for (int yy = 0; yy < kernelDim; yy++) { //yy is kernel coordinate
                        int iny = (yincenter + yy) - halfKernelDim; // iny is input coordinate
//                    sum += 1;
//                    sum += input.a(inputMap, inx, iny);
                        sum += kernels[k(inputMap, outputMap, xx, yy)] * input.a(inputMap, inx, iny); // NOTE flip of kernel to match matlab convention of reversing kernel as though doing time-based convolution
                        operationCounter += 2;
//                    iny++;
//                    nterms++;
                    }
//                inx++;
                }
            } else {
                for (int xx = 0; xx < kernelDim; xx++) { // kernel coordinate
                    int inx = (xincenter + xx) - halfKernelDim; // input coordinate
                    for (int yy = 0; yy < kernelDim; yy++) { //yy is kernel coordinate
                        int iny = (yincenter + yy) - halfKernelDim; // iny is input coordinate
//                    sum += 1;
//                    sum += input.a(inputMap, inx, iny);
                        sum += kernels[k(inputMap, outputMap, kernelDim - xx - 1, kernelDim - yy - 1)] * input.a(inputMap, inx, iny); // NOTE flip of kernel to match matlab convention of reversing kernel as though doing time-based convolution
                        operationCounter += 2;
//                    iny++;
//                    nterms++;
                    }
//                inx++;
                }
            }
//            return 1; //1; // debug
            return sum; //1; // debug
        }

        private float convsingle_zp(float[][] input, int outputMap, int inputMap, int xincenter, int yincenter) {
            float sum = 0;
            for (int xx = 0; xx < kernelDim; xx++) { // kernel coordinate
                int inx = (xincenter + xx) - halfKernelDim; // input coordinate
                for (int yy = 0; yy < kernelDim; yy++) { //yy is kernel coordinate
                    int iny = (yincenter + yy) - halfKernelDim; // iny is input coordinate
                    sum += kernels[k(inputMap, outputMap, xx, yy)] * input[inx][iny]; // NOTE flip of kernel to match matlab convention of reversing kernel as though doing time-based convolution
                    operationCounter += 2;
                }
            }
            return sum;
        }

        private void applyBiasAndNonlinearity() {
            if (activations == null) {
                return;
            }
            for (int b = 0; b < biases.length; b++) {
                for (int x = 0; x < outputMapDim; x++) {
                    for (int y = 0; y < outputMapDim; y++) {
                        int idx = o(b, x, y);
                        switch (activationFunction) {
                            case Sigmoid:
                                activations[idx] = sigm(activations[idx] + biases[b]);
                                break;
                            case ReLu:
                                activations[idx] = relu(activations[idx] + biases[b]);
                                break;
                            case None:
                                activations[idx] = (activations[idx] + biases[b]);
                                break;
                            default:
                                log.warning("activation type undefined; please set actvation type for all convolutional layers");
                        }
                        operationCounter += 2;
                    }
                }
            }

        }

        /**
         * Return kernel index corresponding to input map, kernel (output map),
         * x, and y.
         * <p>
         * kernels are stored in this order in the kernels array: y, x,
         * outputMap, inputMap, i.e. for 5x5 kernels, 12 output maps and 6 input
         * maps, the first 12 kernels (25*12=300 weights) are the 12 5x5 weights
         * for the first input map and each output map.
         *
         *
         * @param inputMap the features map to convolve
         * @param outputMap the feature (output map) to accumulate to
         * @param x
         * @param y
         * @return the index into kernels[]
         */
        public final int k(int inputMap, int outputMap, int x, int y) {
            return (inputMap * kernelWeightsPerOutputMap) + (singleKernelLength * outputMap) + (kernelDim * x) + y; //(kernelDim - y - 1);
        }

        // output index
        public final int o(int outputMap, int x, int y) {
            return (outputMap * outputMapLength) + (outputMapDim * x) + y; //(outputMapDim-y-1);
        }

        @Override
        synchronized public void cleanupGraphics() {
            activationDisplays = null;
            kernelDisplays = null;
        }

        @Override
        public void drawActivations() {
            if (!isVisible() || (activations == null)) {
                return;
            }
            checkActivationsFrame();
            if (activationDisplays == null) {
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                activationDisplays = new ImageDisplay[nOutputMaps];
                for (int i = 0; i < nOutputMaps; i++) {
                    activationDisplays[i] = ImageDisplay.createOpenGLCanvas();
                    activationDisplays[i].setBorderSpacePixels(0);
                    activationDisplays[i].setImageSize(outputMapDim, outputMapDim);
                    activationDisplays[i].setSize(100, 100);
                    panel.add(activationDisplays[i]);
                }

                activationsFrame.getContentPane().add(panel);
                activationsFrame.pack();
            }
            for (int map = 0; map < nOutputMaps; map++) {
                for (int x = 0; x < outputMapDim; x++) {
                    for (int y = 0; y < outputMapDim; y++) {
                        activationDisplays[map].setPixmapGray(y, outputMapDim - x - 1, activations[o(map, y, x)]);
                    }
                }
                activationDisplays[map].display();
            }
        }

        private void drawKernels() {
            if (!isVisible() || (kernels == null)) {
                return;
            }
            checkKernelsFrame();
            if (kernelDisplays == null) {
                JPanel panel = new JPanel();
                panel.setPreferredSize(new Dimension(900, 200));
                kernelsFrame.getContentPane().add(panel);
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                kernelDisplays = new ImageDisplay[nOutputMaps][nInputMaps];
                int s = (int) Math.ceil(Math.sqrt(nOutputMaps));
                for (int outputMapNumber = 0; outputMapNumber < nOutputMaps; outputMapNumber++) {
                    JPanel outputMapPanel = new JPanel(new GridLayout(s, s));
                    for (int inputFeatureMapNumber = 0; inputFeatureMapNumber < nInputMaps; inputFeatureMapNumber++) {
                        kernelDisplays[outputMapNumber][inputFeatureMapNumber] = ImageDisplay.createOpenGLCanvas();
                        kernelDisplays[outputMapNumber][inputFeatureMapNumber].setBorderSpacePixels(1);
                        kernelDisplays[outputMapNumber][inputFeatureMapNumber].setImageSize(kernelDim, kernelDim);
                        kernelDisplays[outputMapNumber][inputFeatureMapNumber].setSize(100, 100);
                        outputMapPanel.add(kernelDisplays[outputMapNumber][inputFeatureMapNumber]);
                    }
                    panel.add(outputMapPanel);
                }

                kernelsFrame.pack();
            }
            for (int kernel = 0; kernel < nOutputMaps; kernel++) {
                for (int inputFeatureMapNumber = 0; inputFeatureMapNumber < nInputMaps; inputFeatureMapNumber++) {
                    for (int x = 0; x < kernelDim; x++) {
                        for (int y = 0; y < kernelDim; y++) {
                            kernelDisplays[kernel][inputFeatureMapNumber].setPixmapGray(x, y, normalizedWeight(kernels[k(inputFeatureMapNumber, kernel, x, y)]));
                        }
                    }
                    kernelDisplays[kernel][inputFeatureMapNumber].setFontSize(12);
                    kernelDisplays[kernel][inputFeatureMapNumber].setTitleLabel(String.format("o%d i%d", kernel, inputFeatureMapNumber));
                    kernelDisplays[kernel][inputFeatureMapNumber].display();
                }
            }
        }

        @Override
        public final float a(int map, int x, int y) {
            return activations[o(map, x, y)];
        }

        /**
         * @return the activationFunction
         */
        public ActivationFunction getActivationFunction() {
            return activationFunction;
        }

        /**
         * @param activationFunction the activationFunction to set
         */
        public void setActivationFunction(ActivationFunction activationFunction) {
            this.activationFunction = activationFunction;
        }

    }

    public class SubsamplingLayer extends Layer {

        int averageOverDim, averageOverNum; // we average over averageOverDim*averageOverDim inputs
        float[] biases;
        int inputMapLength, inputMapDim, outputMapLength, outputMapDim;
        float averageOverMultiplier;
        int nOutputMaps;
        private int activationsLength;
        ImageDisplay[] activationDisplays = null;
        private PoolingType poolingType = PoolingType.Undefined;

        public SubsamplingLayer(int index) {
            super(index);
        }

        @Override
        public void compute(Layer input) {
            if (!(input instanceof ConvLayer)) {
                log.warning("Input to SubsamplingLayer is not ConvLayer; it is actaully " + input.toString());
                return;
            }
            ConvLayer convLayer = (ConvLayer) input;
            nOutputMaps = convLayer.nOutputMaps;
            inputMapDim = convLayer.outputMapDim;
            inputMapLength = convLayer.outputMapLength;
            outputMapDim = inputMapDim / averageOverDim;
            averageOverNum = (averageOverDim * averageOverDim);
            averageOverMultiplier = 1f / averageOverNum;
            outputMapLength = inputMapLength / averageOverNum;
            activationsLength = outputMapLength * nOutputMaps;

            if ((activations == null) || (activations.length != activationsLength)) {
                activations = new float[activationsLength];
            }

            for (int map = 0; map < nOutputMaps; map++) {
                for (int xo = 0; xo < outputMapDim; xo++) { // output map index
                    for (int yo = 0; yo < outputMapDim; yo++) { // output map
                        float sumOrMax = 0; // sum
                        // input indices
                        int startx = xo * averageOverDim, endx = startx + averageOverDim, starty = yo * averageOverDim, endy = starty + averageOverDim;
                        for (int xi = startx; xi < endx; xi++) { // iterate over input
                            for (int yi = starty; yi < endy; yi++) {
                                switch (poolingType) {
                                    case Average:
                                        sumOrMax += convLayer.a(map, xi, yi); // add to sum to processAPSFrame average
                                        break;
                                    case Max:
                                        float f = convLayer.a(map, xi, yi);
                                        sumOrMax = f > sumOrMax ? f : sumOrMax;
                                        break;
                                    case Undefined:
                                        log.warning("poolingType is undefined. Please define the pooling type (average or max) for each pooling layer");
                                }
                                operationCounter++;
                            }
                        }
                        // debug
                        int idx = o(map, xo, yo);
                        if (idx >= activations.length) {
                            log.warning("overran this pooling layer's output activations");
                        }
                        if (poolingType == PoolingType.Average) {
                            activations[o(map, xo, yo)] = sumOrMax * averageOverMultiplier;  //average
                        } else {
                            activations[o(map, xo, yo)] = sumOrMax;
                        }
                    }
                }
            }
        }

        // output index function
        final int o(int map, int x, int y) {
            return (map * outputMapLength) + (x * outputMapDim) + y;
        }

        @Override
        synchronized public void cleanupGraphics() {
            activationDisplays = null;
        }

        @Override
        public void drawActivations() {
            if (!isVisible() || (activations == null)) {
                return;
            }
            checkActivationsFrame();
            if (activationDisplays == null) {
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                activationDisplays = new ImageDisplay[nOutputMaps];
                for (int i = 0; i < nOutputMaps; i++) {
                    activationDisplays[i] = ImageDisplay.createOpenGLCanvas();
                    activationDisplays[i].setBorderSpacePixels(0);
                    activationDisplays[i].setImageSize(outputMapDim, outputMapDim);
                    activationDisplays[i].setSize(100, 100);
                    panel.add(activationDisplays[i]);
                }

                activationsFrame.getContentPane().add(panel);
                activationsFrame.pack();
            }
            for (int map = 0; map < nOutputMaps; map++) {
                for (int x = 0; x < outputMapDim; x++) {
                    for (int y = 0; y < outputMapDim; y++) {
                        activationDisplays[map].setPixmapGray(y, outputMapDim - x - 1, activations[o(map, x, y)]);
                    }
                }
                activationDisplays[map].display();
            }
        }

        @Override
        public String toString() {
            return String.format("index=%d Subsamp layer; averageOver=%d biases=float[%d] poolingType=%s",
                    index, averageOverDim, biases == null ? 0 : biases.length, poolingType.toString());
        }

        @Override
        public float a(int map, int x, int y) {
            return activations[o(map, x, y)];
        }

        /**
         * @return the poolingType
         */
        public PoolingType getPoolingType() {
            return poolingType;
        }

        /**
         * @param poolingType the poolingType to set
         */
        public void setPoolingType(PoolingType poolingType) {
            this.poolingType = poolingType;
        }
    }

    public class OutputOrInnerProductFullyConnectedLayer extends Layer implements OutputLayer {

        protected float maxActivation;
        protected int maxActivatedUnit;
        float[] biases;  //ffb in matlab DeepLearnToolbox
        /**
         * @see #weight(int, int)
         */
        float[] weights;
        ActivationFunction activationFunction = ActivationFunction.None;
        private ImageDisplay imageDisplay;

        public OutputOrInnerProductFullyConnectedLayer(int index) {
            super(index);
        }

        @Override
        public String toString() {
            return String.format("OutputOrInnerProductFullyConnectedLayer: bias=float[%d] outputWeights=float[%d]", biases.length, weights.length);
        }

        /**
         * Returns number of units, if they network has already run, otherwise
         * zero
         *
         * @return number of units
         */
        public int getNumUnits() {
            if (biases == null) {
                return 0; // not initialized or run yet
            } else {
                return biases.length;
            }
        }

        /**
         * Computes following perceptron output:
         * <pre>
         * concatenate all end layer feature maps into vector
         * net.fv = [];
         * for j = 1 : numel(net.layers{n}.activations)
         * sa = size(net.layers{n}.activations{j});
         * net.fv = [net.fv; reshape(net.layers{n}.activations{j}, sa(1) * sa(2), sa(3))];
         * end
         * %  feedforward into output perceptrons
         * net.o = sigm(net.ffW * net.fv + repmat(net.ffb, 1, size(net.fv, 2)));
         * </pre>
         */
        @Override
        public void compute(Layer input) {
            if ((activations == null) || (activations.length != biases.length)) {
                activations = new float[biases.length];
            } else {
                Arrays.fill(activations, 0);
            }
            try {
                int aidx = 0;
                for (int unit = 0; unit < biases.length; unit++) {  // for each output unit
                    for (int w = 0; w < input.activations.length; w++) { // simply MAC the weight times the input activation
                        activations[unit] += input.activations[aidx] * weight(unit, biases.length, w);
                        aidx++; // the input activations are stored in the feature maps of last layer, column, row, map order
                        operationCounter += 2;
                    }
                    aidx = 0;
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                log.warning("ArrayIndexOutOfBoundsException while computing fully connected or output layer. Could you have an incorrect zeroPadding setting? " + e.toString());
                throw new ArrayIndexOutOfBoundsException(e.toString());
            }

            maxActivation = Float.NEGATIVE_INFINITY;
            for (int unit = 0; unit < biases.length; unit++) {
                switch (activationFunction) {
                    case Sigmoid:
                    case Undefined:
                        activations[unit] = sigm(activations[unit] + biases[unit]);
                        break;
                    case ReLu:
                        activations[unit] = relu(activations[unit] + biases[unit]);
                        break;
                    case None:
                        activations[unit] = (activations[unit] + biases[unit]);
                }
                operationCounter += 2;
                if (activations[unit] > maxActivation) {
                    maxActivatedUnit = unit;
                    maxActivation = activations[unit];
                }
            }

        }

        private float weight(int unit, int nUnits, int weight) {
            // ffW in matlab DeepLearnToolbox, a many by few array in XML where there are a few rows each with many columsn to dot with previous layer
            // weight array here is stored by columns; first 4-column has first weight for each of 4 outputs, 2nd column (entries 4-7) has 2nd weights for 4 output units.
            return weights[unit + (nUnits * weight)]; // e.g 2nd weight for unit 0 is at position 4=0+4*1
        }

  

        @Override
        public void drawHistogram(GL2 gl, int width, int height, float lineWidth, Color color) {
            AbstractDavisCNN.drawHistogram(gl, activations, width, height, lineWidth, color);
        }

        @Override
        synchronized public void cleanupGraphics() {
            imageDisplay = null;
        }

        @Override
        public void drawActivations() {
            if (!isVisible() || (activations == null)) {
                return;
            }
            checkActivationsFrame();
            if (imageDisplay == null) {
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                imageDisplay = ImageDisplay.createOpenGLCanvas();
                imageDisplay.setBorderSpacePixels(0);
                imageDisplay.setImageSize(biases.length, 1);
                imageDisplay.setSize(200, 200);
                panel.add(imageDisplay);

                activationsFrame.getContentPane().add(panel);
                activationsFrame.pack();
            }
            for (int x = 0; x < biases.length; x++) {
                imageDisplay.setPixmapGray(x, 0, activations[x]);
            }
            imageDisplay.display();
        }

        @Override
        public float a(int map, int x, int y) { // map and y are ignored
            return activations[x];
        }

        @Override
        public void printActivations() {
            super.printActivations();
            System.out.println("Activations:");
            for (int i = 0; i < activations.length; i++) {
                System.out.print(String.format("%6.2g ", activations[i]));
            }
        }

        /**
         * computes the softmax on the existing activations. * Computes softmax
         * on its input activations, by o_i= exp(a_i)/sum_k(exp(a_k)) where o_i
         * is the i'th output and a_k is the k'th input.
         */
        private void computeSoftMax() {
            if (activations == null || activations.length == 0) {
                return;
            }
            float sum = 0;
            for (int k = 0; k < activations.length; k++) { // simply MAC the weight times the input activation
                float f = (float) Math.exp(activations[k]);
                if (Float.isInfinite(f)) {
                    f = Float.MAX_VALUE; // handle exponential overflow
                }
                sum += f;
                activations[k] = f;
                operationCounter += 2;
            }
            maxActivation = Float.NEGATIVE_INFINITY;
            float r = 1 / sum;
            for (int k = 0; k < activations.length; k++) { // simply MAC the weight times the input activation
                activations[k] *= r;
                if (activations[k] > maxActivation) {
                    setMaxActivatedUnit(k);
                    maxActivation = activations[k];
                }
                operationCounter += 2;
            }
        }

        /**
         * @return the maxActivation
         */
        @Override
        public float getMaxActivation() {
            return maxActivation;
        }

        /**
         * @return the maxActivatedUnit
         */
        @Override
        public int getMaxActivatedUnit() {
            return maxActivatedUnit;
        }

        @Override
        public float[] getActivations() {
            return activations;
        }

        @Override
        public void setMaxActivatedUnit(int unit) {
            maxActivatedUnit = unit;
        }

    }

//    /**
//     * This layer is sometimes added after the final layer's ReLu to normalize
//     * final outputs to max value of 1. If all units inputs are zero, then the
//     * softmax output will be 1/n, where n is the number of units. Otherwise if
//     * one unit is significantly larger than others then the softmax will make
//     * it one and all others almost zero, because of the exponential.
//     */
//    public class SoftMaxLayer extends Layer {
//
//        private ImageDisplay imageDisplay;
//
//        public SoftMaxLayer(int index) {
//            super(index);
//        }
//
//        ActivationFunction activationFunction = ActivationFunction.None;
//        public float maxActivation;
//        public int maxActivatedUnit;
//
//        @Override
//        public String toString() {
//            return String.format("SoftMaxLayer");
//        }
//
//        /**
//         * Computes softmax on it's input activations, by o_i=
//         * exp(a_i)/sum_k(exp(a_k)) where o_i is the i'th output and a_k is the
//         * k'th input.
//         */
//        @Override
//        public void compute(Layer input) {
//            if (input == null || input.activations == null || input.activations.length == 0) {
//                return;
//            }
//            if ((activations == null)) {
//                activations = new float[input.activations.length];
//            } else {
//                Arrays.fill(activations, 0);
//            }
//            float sum = 0;
//            for (int k = 0; k < input.activations.length; k++) { // simply MAC the weight times the input activation
//                float f = (float) Math.exp(input.activations[k]);
//                sum += f;
//                activations[k] = f;
//                operationCounter += 2;
//            }
//            maxActivation = Float.NEGATIVE_INFINITY;
//            float r = 1 / sum;
//            for (int k = 0; k < input.activations.length; k++) { // simply MAC the weight times the input activation
//                activations[k] *= r;
//                if (activations[k] > maxActivation) {
//                    maxActivatedUnit = k;
//                    maxActivation = activations[k];
//                }
//                operationCounter += 2;
//            }
//        }
//
//        private float weight(int unit, int nUnits, int weight) {
//            // ffW in matlab DeepLearnToolbox, a many by few array in XML where there are a few rows each with many columsn to dot with previous layer
//            // weight array here is stored by columns; first 4-column has first weight for each of 4 outputs, 2nd column (entries 4-7) has 2nd weights for 4 output units.
//            return weights[unit + (nUnits * weight)]; // e.g 2nd weight for unit 0 is at position 4=0+4*1
//        }
//
//        /**
//         * Draw with default width and color
//         *
//         * @param gl
//         * @param width width of drawHistogram (chip) area in gl pixels
//         * @param height of drawHistogram (chip) area in gl pixels
//         */
//        private void drawHistogram(GL2 gl, int width, int height) { // width and height are of AEchip drawHistogram size in pixels of chip (not screen pixels)
//
//            if (activations == null || activations.length == 0) {
//                return;
//            }
//            float dx = (float) (width) / (activations.length);
//            float sy = (float) (height) / 1;
//
////            gl.glBegin(GL.GL_LINES);
////            gl.glVertex2f(1, 1);
////            gl.glVertex2f(width - 1, 1);
////            gl.glEnd();
//            gl.glBegin(GL.GL_LINE_STRIP);
//            for (int i = 0; i < activations.length; i++) {
//                float y = 1 + (sy * activations[i] / 50f);  // TODO debug hack
//                float x1 = 1 + (dx * i), x2 = x1 + dx;
//                gl.glVertex2f(x1, 1);
//                gl.glVertex2f(x1, y);
//                gl.glVertex2f(x2, y);
//                gl.glVertex2f(x2, 1);
//            }
//            gl.glEnd();
//        }
//
//        public void drawHistogram(GL2 gl, int width, int height, float lineWidth, Color color) {
//            gl.glPushAttrib(GL2ES3.GL_COLOR | GL.GL_LINE_WIDTH);
//            gl.glLineWidth(lineWidth);
//            float[] ca = color.getColorComponents(null);
//            gl.glColor4fv(ca, 0);
//            SoftMaxLayer.this.drawHistogram(gl, width, height);
//            gl.glPopAttrib();
//        }
//
//        @Override
//        public void draw() {
//            if (!isVisible() || (activations == null) || activations.length == 0) {
//                return;
//            }
//            checkActivationsFrame();
//            if (imageDisplay == null) {
//                JPanel panel = new JPanel();
//                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
//                imageDisplay = ImageDisplay.createOpenGLCanvas();
//                imageDisplay.setBorderSpacePixels(0);
//                imageDisplay.setImageSize(activations.length, 1);
//                imageDisplay.setSize(200, 200);
//                panel.add(imageDisplay);
//
//                activationsFrame.getContentPane().add(panel);
//                activationsFrame.pack();
//            }
//            for (int x = 0; x < activations.length; x++) {
//                imageDisplay.setPixmapGray(x, 0, activations[x]);
//            }
//            imageDisplay.display();
//        }
//
//        @Override
//        public float a(int map, int x, int y) { // map and y are ignored
//            return activations[x];
//        }
//
//        @Override
//        public void printActivations() {
//            super.printActivations();
//            System.out.println("Activations:");
//            for (int i = 0; i < activations.length; i++) {
//                System.out.print(String.format("%6.2g ", activations[i]));
//            }
//        }
//
//    }
    public void setNetworkToUniformValues(float weight, float bias) {
        if (layers == null) {
            return;
        }
        for (Layer l : layers) {
            if (l == null) {
                continue;
            }
            if (l instanceof ConvLayer) {
                ConvLayer c = (ConvLayer) l;
                if (c.kernels != null) {
                    Arrays.fill(c.kernels, weight);
                }
                if (c.biases != null) {
                    Arrays.fill(c.biases, bias);
                }

            } else if (l instanceof OutputOrInnerProductFullyConnectedLayer) {
                OutputOrInnerProductFullyConnectedLayer o = (OutputOrInnerProductFullyConnectedLayer) l;
                if (o.biases != null) {
                    Arrays.fill(o.biases, bias);
                }
                if (o.weights != null) {
                    Arrays.fill(o.weights, weight);
                }

            }
        }
    }

    public void loadNetwork(File f) throws IOException {
        EasyXMLReader networkReader;
        networkReader = new EasyXMLReader(f);
        if (!networkReader.hasFile()) {
            log.warning("No file for reader; file=" + networkReader.getFile());
            throw new IOException("Exception thrown in EasyXMLReader for file " + f);
        }

        setNetname(networkReader.getRaw("name"));
        notes = networkReader.getRaw("notes");
        dob = networkReader.getRaw("dob");
        setNettype(networkReader.getRaw("type"));
        log.info(String.format("reading network with name=%s, notes=%s, dob=%s nettype=%s", getNetname(), notes, dob, getNettype()));
        if (!nettype.equals("cnn") && !nettype.equals("caffe_net")) {
            log.warning("network type is " + getNettype() + " which is not defined type \"cnn\" or \"caffe_net\"");
        }
        nLayers = networkReader.getNodeCount("Layer") - 1; // the output layer is a special layer not counted here
        log.info("network has " + nLayers + " layers");
        if (layers != null) {
            for (int i = 0; i < layers.length; i++) {
                layers[i] = null;
            }
        }
        outputLayer = null;
        layers = new Layer[nLayers];

        for (int i = 0; i <= nLayers; i++) { // we need one more layer here (<=) to get the output layer
            log.info("loading layer " + i);
            EasyXMLReader layerReader = networkReader.getNode("Layer", i);
            int index = layerReader.getInt("index");
            String type = layerReader.getRaw("type");
            switch (type) {
                case "i": {
                    log.info("loading input layer " + index);
                    inputLayer = new InputLayer(index);
                    layers[index] = inputLayer;
                    inputLayer.width = layerReader.getInt("dimx");
                    inputLayer.height = layerReader.getInt("dimy");
                    inputLayer.nUnits = layerReader.getInt("nUnits");
                }
                break;
                case "c": {
                    log.info("loading conv layer " + index);
                    ConvLayer l = new ConvLayer(index);
                    layers[index] = l;
                    l.nInputMaps = layerReader.getInt("inputMaps");
                    l.nOutputMaps = layerReader.getInt("outputMaps");
                    l.kernelDim = layerReader.getInt("kernelSize");
                    l.biases = readFloatArray(layerReader, "biases");
                    l.kernels = readFloatArray(layerReader, "kernels");
                    try {
                        String af = layerReader.getRaw("activationFunction");
                        if (af.equalsIgnoreCase("sigmoid")) {
                            l.activationFunction = ActivationFunction.Sigmoid;
                        } else if (af.equalsIgnoreCase("relu")) {
                            l.activationFunction = ActivationFunction.ReLu;
                        } else if (af.equalsIgnoreCase("none")) {
                            l.activationFunction = ActivationFunction.None;
                        } else {
                            log.warning("unknown conv layer activation function " + af + " in " + layerReader.toString());
                        }
                    } catch (NullPointerException e) {
                        throw new IOException("Caught " + e.toString() + " while parsing for activationFunction in convolutional layer; probably none defined; please define none, sigmoid, or relu.");
                    }
                    l.initializeConstants();
                }
                break;
                case "s":
                case "p": {
                    log.info("loading subsampling layer " + index);
                    SubsamplingLayer l = new SubsamplingLayer(index);
                    layers[index] = l;
                    l.averageOverDim = layerReader.getInt("averageOver");
                    try {
                        String af = layerReader.getRaw("poolingType");
                        if (af.equalsIgnoreCase("average")) {
                            l.setPoolingType(PoolingType.Average);
                        } else if (af.equalsIgnoreCase("max")) {
                            l.setPoolingType(PoolingType.Max);
                        } else {
                            throw new RuntimeException("unknown pooling layer pooling type (max or average) " + af + " in " + layerReader.toString());
                        }
                    } catch (NullPointerException e) {
                        throw new IOException("Caught " + e.toString() + " while parsing pooling layer for poolingType; please define poolingType as average or max.");
                    }
                }
                break;
                case "ip":
                case "o": // for output layer defined here, we simply assign it to outputLayer field at the end
                {
                    log.info("loading inner product or output fully connected layer " + index);
                    OutputOrInnerProductFullyConnectedLayer l = new OutputOrInnerProductFullyConnectedLayer(index);
                    l.weights = readFloatArray(layerReader, "weights"); // stored in many cols and few rows: one row per output unit
                    l.biases = readFloatArray(layerReader, "biases");
                    try {
                        String af = layerReader.getRaw("activationFunction");
                        if (af.equalsIgnoreCase("sigmoid")) {
                            l.activationFunction = ActivationFunction.Sigmoid;
                        } else if (af.equalsIgnoreCase("relu")) {
                            l.activationFunction = ActivationFunction.ReLu;
                        } else if (af.equalsIgnoreCase("none")) {
                            l.activationFunction = ActivationFunction.None;
                        } else {
                            log.warning("unknown inner product/output/fullyconnected layer activation function " + af + " in " + layerReader.toString());
                        }
                    } catch (NullPointerException e) {
                        throw new IOException("Caught " + e.toString() + " while parsing for activationFunction in inner product layer; probably none defined; please define none, sigmoid, or relu.");
                    }
                    switch (type) {
                        case "ip":
                            layers[index] = l;
                            break;
                        case "o":
                            log.info("assiging this layer to be network output layer");
                            outputLayer = l;
                            outputLayer.weights = readFloatArray(layerReader, "weights");
                            outputLayer.biases = readFloatArray(layerReader, "biases");
                            try {
                                String af = layerReader.getRaw("activationFunction");
                                if (af.equalsIgnoreCase("sigmoid")) {
                                    outputLayer.activationFunction = ActivationFunction.Sigmoid;
                                } else if (af.equalsIgnoreCase("relu")) {
                                    outputLayer.activationFunction = ActivationFunction.ReLu;
                                } else if (af.equalsIgnoreCase("none")) {
                                    outputLayer.activationFunction = ActivationFunction.None;
                                } else {
                                    log.warning("unknown conv layer activation function " + af + " in " + networkReader.toString());
                                }
                            } catch (NullPointerException e) {
                                throw new IOException("Caught " + e.toString() + " while parsing for outputActivationFunction in output layer; probably none defined and so using default sigmoid activation function");
                            }
                    }
                }
//                case "sm": // this is a softmax layer, which is automatically assigned as the output layer of the entire network
//                {
//                    log.info("loading softmax layer " + index);
//                    SoftMaxLayer l = new SoftMaxLayer(index);
//                    log.info("assiging this layer to be network output layer");
//                    outputLayer = l; // TODO must solve problem that output layer needs an interface for it to allow softmax to be one as well as FCN
//                }
                break;
                default:
                    throw new IOException("unknown layer type \"" + type + "\"");
            }
        }
//        try {
//            log.info("trying to load seperate output layer with special output tags that is written at top level of xml Network element");
//            OutputOrInnerProductFullyConnectedLayer ol = new OutputOrInnerProductFullyConnectedLayer(nLayers);
//            ol.weights = readFloatArray(networkReader, "outputWeights"); // stored in many cols and few rows: one row per output unit
//            ol.biases = readFloatArray(networkReader, "outputBias");
//
//            try {
//                String af = networkReader.getRaw("outputActivationFunction");
//                if (af.equalsIgnoreCase("sigmoid")) {
//                    outputLayer.activationFunction = ActivationFunction.Sigmoid;
//                } else if (af.equalsIgnoreCase("relu")) {
//                    outputLayer.activationFunction = ActivationFunction.ReLu;
//                } else if (af.equalsIgnoreCase("none")) {
//                    outputLayer.activationFunction = ActivationFunction.None;
//                } else {
//                    log.warning("unknown conv layer activation function " + af + " in " + networkReader.toString());
//                }
//            } catch (NullPointerException e) {
//                throw new NullPointerException("Caught " + e.toString() + " while parsing for outputActivationFunction in output layer; probably none defined and so using default sigmoid activation function");
//            }
//            if(outputLayer!=null){
//                throw new IOException("outputLayer "+outputLayer+" already exists; there should only be one defined");
//            }
//            outputLayer=ol;
//        } catch (NullPointerException e) {
//            log.warning("Caught " + e.toString() + " seaparate output layer not found");
//            if (outputLayer == null) {
//                throw new IOException(this.toString() + "\n     network has no output layer defined");
//            }
//        }
        setFilename(f.toString());

        cleanup();
        networkRanOnce = false;

        log.info(toString());
    }

    /**
     * Close extra graphics windows and dispose of them
     *
     */
    synchronized public void cleanup() {
        if (activationsFrame != null) {
            activationsFrame.dispose();
            activationsFrame = null;
        }
        if (kernelsFrame != null) {
            kernelsFrame.dispose();
            kernelsFrame = null;
        }
        for (Layer l : layers) {
            l.cleanupGraphics();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DeepLearnCnnNetwork: \n");
        sb.append(String.format("name=%s, dob=%s, type=%s\nnotes=%s\n", getNetname(), dob, getNettype(), notes));
        sb.append(String.format("nLayers=%d\n", nLayers));
        for (Layer l : layers) {
            sb.append((l == null ? "null layer" : l.toString()) + "\n");
        }
        sb.append(outputLayer == null ? "null outputLayer" : outputLayer.toString());
        return sb.toString();

    }

    /**
     * For debug, clamps input image to fixed value
     */
    public boolean isInputClampedTo1() {
        return inputLayer == null ? false : inputLayer.isInputClampedTo1();
    }

    /**
     * For debug, clamps input image to fixed value
     */
    public void setInputClampedTo1(boolean inputClampedTo1) {
        if (inputLayer != null) {
            inputLayer.setInputClampedTo1(inputClampedTo1);
        }
    }

    /**
     * For debug, clamps input image to fixed value
     */
    public boolean isInputClampedToIncreasingIntegers() {
        return inputLayer == null ? false : inputLayer.isInputClampedToIncreasingIntegers();
    }

    /**
     * For debug, clamps input image to fixed value
     */
    public void setInputClampedToIncreasingIntegers(boolean inputClampedToIncreasingIntegers) {
        if (inputLayer != null) {
            inputLayer.setInputClampedToIncreasingIntegers(inputClampedToIncreasingIntegers);
        }
    }

    /**
     * @return the normalizeKernelDisplayWeightsGlobally
     */
    public boolean isNormalizeKernelDisplayWeightsGlobally() {
        return normalizeKernelDisplayWeightsGlobally;
    }

    /**
     * @param normalizeKernelDisplayWeightsGlobally the
     * normalizeKernelDisplayWeightsGlobally to set
     */
    public void setNormalizeKernelDisplayWeightsGlobally(boolean normalizeKernelDisplayWeightsGlobally) {
        this.normalizeKernelDisplayWeightsGlobally = normalizeKernelDisplayWeightsGlobally;
    }

    /**
     * @return the normalizeActivationDisplayGlobally
     */
    public boolean isNormalizeActivationDisplayGlobally() {
        return normalizeActivationDisplayGlobally;
    }

    /**
     * @param normalizeActivationDisplayGlobally the
     * normalizeActivationDisplayGlobally to set
     */
    public void setNormalizeActivationDisplayGlobally(boolean normalizeActivationDisplayGlobally) {
        this.normalizeActivationDisplayGlobally = normalizeActivationDisplayGlobally;
    }

}
