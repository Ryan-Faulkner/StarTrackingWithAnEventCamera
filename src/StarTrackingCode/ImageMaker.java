package StarTrackingCode;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import eu.seebetter.ini.chips.davis.DAVIS240C;
import java.io.IOException; 
import java.math.BigInteger;
import java.net.DatagramPacket; 
import java.net.DatagramSocket; 
import java.util.Arrays;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.EventPacket;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;

/**
 *
 * @author ryanj
 *
 *
 *
 *                              This code is not part of the main function, but separate, and to be used when saving images to the computer system.
 *
 *
 */
public class ImageMaker {

    /**
     * @param args the command line arguments
     */
    
    
    
    public static void main(String[] args) throws IOException
    {
        System.load("C:\\Users\\ryanj\\Downloads\\opencv\\build\\java\\x64\\opencv_java320.dll");
        Mat testMat = new Mat();
        // declare constant values e.g. port
        final int port = 8991;
        final int frameDuration = 60000;
        final DAVIS240C chip = new DAVIS240C();
        final MakeEventImage imageMaker = new MakeEventImage();
        final MakeCentroidImage centroidMaker = new MakeCentroidImage();
        TrackStars tracker = new TrackStars();
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
        int imageCounter = 0;
        
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
                    if(eventsInImage > 0)
                    {
                        processedPacket = extractor.extractPacket(packet);
                        packet = new AEPacketRaw();
                        eventMat = imageMaker.returnMat(processedPacket);
                        Mat threshed = eventMat;
                        Core.multiply(eventMat, new Scalar(50), threshed);
                       
                       Imgcodecs.imwrite("PosterImages/NoFilter/im" + imageCounter + ".jpg", threshed);
                      
                        centroidMat = centroidMaker.returnCentroid(eventMat);
                       Imgcodecs.imwrite("PosterImages/Undilated/im" + imageCounter + ".jpg", centroidMat);
                       imageCounter++;
                        
                    }
                    frameStart += frameDuration;
                }
                else if(timestamps[j] < frameStart)
                {
                    frameStart = timestamps[j];
                    //System.out.println("This should only print when timer resets");
                }
                currentEvent.address = addresses[j];
                currentEvent.timestamp = timestamps[j];
                packet.addEvent(currentEvent);
                eventsInImage++;
                
            }
        } 
        
    } 
    
}
