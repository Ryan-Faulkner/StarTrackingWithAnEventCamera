This Code uses a DAVIS Event Camera to track stars in the night sky, and through this track movement of the camera between frames (and whatever it is attached to).

I'll comment the code and make a better Readme in early December when I have more free time, for now:

To Run:
Should work as is, other than two DLL files which I'm currently manually importing at the start of StarTracking.Java, 
you should see "System.load("C:\\Users\\ryanj_000\\Documents\\HonoursCode\\starTrackingBuiltOnJAER2\\starTrackingBuiltOnJAER\\lib\\opencv_java320.dll");" and another below it, at the start of the maint function
replace these absolute paths with your computers version. If it doesn't work try properly downloading the libraries and building them on your computer as opposed to using the files included in this code.

Libraries used:
JAER (https://github.com/SensorsINI/jaer), this whole code was built on top of this library so that it could use the inbuilt classes for dealing with the event camera and events
OpenCV(https://opencv.org/)
Java3D(http://www.java3d.org/)
KDTree for Java(https://mvnrepository.com/artifact/de.biomedical-imaging.edu.wlu.cs.levy.cg/kdtree/1.0.3)
Efficient Java Matrix Library (http://ejml.org/)


The Star Tracking Code is based off the MATLAB implementation by Tat-Jun Chin and Samya Bagchi and Anders Eriksson and Andre van Schaik:
Star Tracking using an Event Camera
(https://arxiv.org/abs/1812.02895)

The Rotation Averaging Code is based off the C++ code by Álvaro Parra:
iRotAvg (https://github.com/ajparra/iRotAvg)
As part of the paper by Parra, Alvaro and Chin, Tat-Jun and Eriksson, Anders and Reid, Ian:
Visual SLAM: Why Bundle Adjust? (https://cs.adelaide.edu.au/~aparra/publication/parra19_icra/)
