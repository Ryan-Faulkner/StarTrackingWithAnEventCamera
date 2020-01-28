

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

//import edu.wlu.cs.levy.CG.KeyDuplicateException;
//import edu.wlu.cs.levy.CG.KeySizeException;
import StarTrackingCode.Gui3D;
import StarTrackingCode.TrackStars;
import StarTrackingCode.MakeCentroidImage;
import StarTrackingCode.MakeEventImage;
import de.biomedical_imaging.edu.wlu.cs.levy.CG.KeyDuplicateException;
import de.biomedical_imaging.edu.wlu.cs.levy.CG.KeySizeException;
import eu.seebetter.ini.chips.davis.DAVIS240C;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException; 
import java.io.InputStream;
import java.math.BigInteger;
import java.net.DatagramPacket; 
import java.net.DatagramSocket; 
import java.util.Arrays;
import javax.imageio.ImageIO;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.EventPacket;
import org.apache.commons.math3.geometry.euclidean.threed.CardanEulerSingularityException;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention;
import org.apache.commons.math3.geometry.euclidean.threed.RotationOrder;
import static org.bytedeco.javacpp.opencv_imgproc.CV_INTER_AREA;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/**
 *
 * @author ryanj
 *
 *
 *
 *
 *              This is the main function which runs everything. 
 *              It gets packets sent through UDP and then calls other functions to process them and to display the result.
 *
 */


public class StarTracking {
    static TrackStars tracker;
    static Gui3D gui3d; 
    /**
     * @param args the command line arguments
     */
    
    //looks good now check how often RoTAvg is called in Irot, I suspect they increment start of window by window size not by 1 like I am
    
    public static void main(String[] args) throws IOException, KeySizeException, KeyDuplicateException//, KeySizeException, KeyDuplicateException
    {
        gui3d = new Gui3D();
        //CHANGE THESE TWO LINES TO THE LOCATION OF THE LIBRARIES ON YOUR LOCAL COMPUTER
        //-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
        System.load("C:\\Users\\ryanj_000\\Documents\\HonoursCode\\starTrackingBuiltOnJAER2\\starTrackingBuiltOnJAER\\lib\\opencv_java320.dll");
        System.load("C:\\Users\\ryanj_000\\Documents\\HonoursCode\\starTrackingBuiltOnJAER2\\starTrackingBuiltOnJAER\\lib\\j3dcore-ogl.dll");
        //-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
        Mat testMat = new Mat();
        // declare constant values e.g. port
        final int port = 8991;
        final int frameDuration = 40000;
        final DAVIS240C chip = new DAVIS240C();
        final MakeEventImage imageMaker = new MakeEventImage();
        final MakeCentroidImage centroidMaker = new MakeCentroidImage();
        tracker = new TrackStars();
        final GUI gui = new GUI();
        final EventExtractor2D extractor = chip.getEventExtractor();
        
        //declare variables
        DatagramSocket ds = new DatagramSocket(port); 
        byte[] receive = new byte[63000]; 
        DatagramPacket DpReceive = null; 
        int prevSeqNum = 0;
        int[] addresses = new int[7874];
        int[] timestamps = new int[7874];
        int eventCounter;
        int frameStart = -1;
        int eventsInImage = 0;
        EventRaw currentEvent=new EventRaw();
        AEPacketRaw packet = new AEPacketRaw();
        EventPacket processedPacket;
        Mat eventMat;
        Mat centroidMat;
        int counter = 0;
        while (true) 
        {            
            eventCounter = 0;
            // Step 2 : create a DatgramPacket to receive the data. 
            DpReceive = new DatagramPacket(receive, receive.length); 
  
            // Step 3 : revieve the data in byte buffer. 
            ds.receive(DpReceive); 
            int seqNum = new BigInteger(Arrays.copyOfRange(receive, 0, 4)).intValue();
            if(prevSeqNum + 1 != seqNum)
            {
                //System.out.printf("lost %d packets%n", seqNum-prevSeqNum-1);
            }
            prevSeqNum = seqNum;
            for(int i = 4; i < DpReceive.getLength();i+=4)
            {
                addresses[eventCounter] = new BigInteger(Arrays.copyOfRange(receive, i, i+4)).intValue();
                i+=4;
                timestamps[eventCounter] = new BigInteger(Arrays.copyOfRange(receive, i, i+4)).intValue();
                eventCounter++;
            }
            // Clear the buffer after every message. 
            receive = new byte[65535]; 
            
            if(frameStart == -1)
            {
                frameStart = timestamps[0];
            }
            for(int j = 0; j < eventCounter; j++)
            {
                if(timestamps[j] > frameStart + frameDuration)
                {
                    if(eventsInImage > 5)
                    {
                        processedPacket = extractor.extractPacket(packet);
                        packet = new AEPacketRaw();
                        eventMat = imageMaker.returnMat(processedPacket);
                        centroidMat = centroidMaker.returnCentroid(eventMat);
                        if(Core.countNonZero(centroidMat) < 5)
                        {
                            eventsInImage = 0;
                            currentEvent.address = addresses[j];
                            currentEvent.timestamp = timestamps[j];
                            packet.addEvent(currentEvent);
                            eventsInImage++;
                           frameStart += frameDuration;
                            continue;
                        }
                        Mat colouredCentroid = tracker.track(centroidMat);
                        //turn Mat into image:
                        //Mat imageMat = eventMat;
                        Rotation currentPose = tracker.currentPose;
                        Rotation currentPose2 = tracker.currentPose2;
                        double[] angles;
                        double[] angles2;
                        try
                        {
                            angles = currentPose.getAngles(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR);
                            angles2 = currentPose2.getAngles(RotationOrder.XYZ, RotationConvention.VECTOR_OPERATOR);
                        }
                        catch(CardanEulerSingularityException e)
                        {
                            angles = new double[3];
                            angles[0] = 0; //pitch
                            angles[1] = 0; //yaw
                            angles[2] = 0; //roll
                            angles2 = new double[3];
                            angles2[0] = 0; //pitch
                            angles2[1] = 0; //yaw
                            angles2[2] = 0; //roll
                        }   
                       gui3d.updateRotationAbs2(angles[0],angles[1],angles[2]);
                       //the line below can be uncommented to show what it looks like without rotation averaging
                       //you will have to edit the gui3d code to that setting as well though
                       //gui3d.updateRotationAbs(angles2[0],angles2[1],angles2[2]);
                       
                       //gui3d.updateRotationAbs(tracker.pitch, tracker.yaw, tracker.roll); 
                            
                        Mat imageMat = colouredCentroid.clone();
                        int dilateSize = 5;
                        Mat element1 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new  Size(2*dilateSize+ 1, 2*dilateSize+1));
                        Mat tempImage = imageMat;
                        Imgproc.dilate(imageMat, tempImage, element1);
                        Mat resizeImage = new Mat();
                        Size scaleSize = new Size(tempImage.width()*2,tempImage.height()*2);
                        Imgproc.resize(tempImage, resizeImage, scaleSize , 0, 0, CV_INTER_AREA);
                        
                        MatOfByte matOfByte = new MatOfByte();
                        Imgcodecs.imencode(".jpg", resizeImage, matOfByte); 

                        byte[] byteArray = matOfByte.toArray();
                        BufferedImage bufImage = null;
                        try {

                            InputStream in = new ByteArrayInputStream(byteArray);
                            bufImage = ImageIO.read(in);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        //display image
                        gui.displayImage(bufImage);
                        eventsInImage = 0;
                        
                    }
                    frameStart += frameDuration;
                }
                else if(timestamps[j] < frameStart)
                {
                    frameStart = timestamps[j];
                }
                currentEvent.address = addresses[j];
                currentEvent.timestamp = timestamps[j];
                packet.addEvent(currentEvent);
                eventsInImage++;
                
            }
        } 
        
    } 
    
}
