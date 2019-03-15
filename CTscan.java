import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.imageio.*;
import javax.swing.*;
import java.awt.event.*;
import javax.swing.event.*;

// OK this is not best practice - maybe you'd like to create
// a volume data class?
// I won't give extra marks for that though.
// 689613 - Kwok Chun Man
// This is my own work.

public class CTscan extends JFrame {

    JButton mip_button; //an example button to switch to MIP mode
    JButton his_button;
    JLabel image_icon1; //using JLabel to display an image (check online documentation)
    JLabel image_icon2; //using JLabel to display an image (check online documentation)
    JLabel image_icon3;
    JLabel image_icon4;

    JSlider zslice_slider, yslice_slider, sideslice_slider, resize_slider; //sliders to step through the slices (z and y directions) (remember 113 slices in z direction 0-112)
    BufferedImage imageTop, imageFront, imageSide, imageMIPTop, imageMIPFront, imageMIPSide , imageResize; //storing the image in memory
    short cthead[][][]; //store the 3D volume data set
    short min, max; //min/max value in the 3D volume data set

    boolean histogramOn;

    /*
        This function sets up the GUI and reads the data set
    */
    public void Example() throws IOException {
        //File name is hardcoded here - much nicer to have a dialog to select it and capture the size from the user
        File file = new File("CThead");

        //Create a BufferedImage to store the image data
        imageTop = new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR);
        imageFront = new BufferedImage(256, 112, BufferedImage.TYPE_3BYTE_BGR);
        imageSide = new BufferedImage(256, 112, BufferedImage.TYPE_3BYTE_BGR);

        imageMIPTop = new BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR);
        imageMIPFront = new BufferedImage(256, 112, BufferedImage.TYPE_3BYTE_BGR);
        imageMIPSide = new BufferedImage(256, 112, BufferedImage.TYPE_3BYTE_BGR);

        imageResize = new BufferedImage(256, 256 , BufferedImage.TYPE_3BYTE_BGR);

        //Read the data quickly via a buffer (in C++ you can just do a single fread - I couldn't find the equivalent in Java)
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));

        int i, j, k; //loop through the 3D data set

        min = Short.MAX_VALUE;
        max = Short.MIN_VALUE; //set to extreme values
        short read; //value read in
        int b1, b2; //data is wrong Endian (check wikipedia) for Java so we need to swap the bytes around

        cthead = new short[113][256][256]; //allocate the memory - note this is fixed for this data set
        //loop through the data reading it in
        for (k = 0; k < 113; k++) {
            for (j = 0; j < 256; j++) {
                for (i = 0; i < 256; i++) {
                    //because the Endianess is wrong, it needs to be read byte at a time and swapped
                    b1 = ((int) in.readByte()) & 0xff; //the 0xff is because Java does not have unsigned types (C++ is so much easier!)
                    b2 = ((int) in.readByte()) & 0xff; //the 0xff is because Java does not have unsigned types (C++ is so much easier!)
                    read = (short) ((b2 << 8) | b1); //and swizzle the bytes around
                    if (read < min) min = read; //update the minimum
                    if (read > max) max = read; //update the maximum
                    cthead[k][j][i] = read; //put the short into memory (in C++ you can replace all this code with one fread)
                }
            }
        }
        System.out.println(min + " " + max); //diagnostic - for CThead this should be -1117, 2248
        //(i.e. there are 3366 levels of grey (we are trying to display on 256 levels of grey)
        //therefore histogram equalization would be a good thing

        // Set up the simple GUI
        // First the container:

        Container container = getContentPane();
        container.setLayout(new FlowLayout());

        // Then our image (as a label icon)
        image_icon1 = new JLabel(new ImageIcon(imageTop));
        container.add(image_icon1);
        image_icon2 = new JLabel(new ImageIcon(imageFront));
        container.add(image_icon2);
        image_icon3 = new JLabel(new ImageIcon(imageSide));
        container.add(image_icon3);

        // Then the invert button
        mip_button = new JButton("MIP");
        container.add(mip_button);
        his_button = new JButton("HISTOGRAM");
        container.add(his_button);

        //Add slider label

        //Sliders
        container.add(new JLabel("TOP"));
        zslice_slider = new JSlider(0, 112);
        container.add(zslice_slider);

        container.add(new JLabel("FRONT"));
        yslice_slider = new JSlider(0, 255);
        container.add(yslice_slider);

        container.add(new JLabel("SIDE"));
        sideslice_slider = new JSlider(0, 112);
        container.add(sideslice_slider);

        image_icon4 = new JLabel(new ImageIcon(imageResize));
        container.add(image_icon4);

        container.add(new JLabel("RESIZE"));
        resize_slider = new JSlider(1, 500);
        container.add(resize_slider);

        //Add labels (y slider as example)
        zslice_slider.setMajorTickSpacing(50);
        zslice_slider.setMinorTickSpacing(10);
        zslice_slider.setPaintTicks(true);
        zslice_slider.setPaintLabels(true);

        yslice_slider.setMajorTickSpacing(50);
        yslice_slider.setMinorTickSpacing(10);
        yslice_slider.setPaintTicks(true);
        yslice_slider.setPaintLabels(true);

        sideslice_slider.setMajorTickSpacing(50);
        sideslice_slider.setMinorTickSpacing(10);
        sideslice_slider.setPaintTicks(true);
        sideslice_slider.setPaintLabels(true);

        resize_slider.setMajorTickSpacing(99);
        resize_slider.setMinorTickSpacing(200);
        resize_slider.setPaintTicks(true);
        resize_slider.setPaintLabels(true);

        //see
        //https://docs.oracle.com/javase/7/docs/api/javax/swing/JSlider.html
        //for documentation (e.g. how to get the value, how to display vertically if you want)

        // Now all the handlers class
        GUIEventHandler handler = new GUIEventHandler();

        // associate appropriate handlers
        mip_button.addActionListener(handler);
        his_button.addActionListener(handler);
        yslice_slider.addChangeListener(handler);
        zslice_slider.addChangeListener(handler);
        sideslice_slider.addChangeListener(handler);
        resize_slider.addChangeListener(handler);

        // ... and display everything
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

    }

    /*
        This is the event handler for the application
    */
    private class GUIEventHandler implements ActionListener, ChangeListener {

        //Change handler (e.g. for sliders)
        public void stateChanged(ChangeEvent e) {
            //System.out.println(yslice_slider.getValue());
            //System.out.println(zslice_slider.getValue());
            //System.out.println(sideslice_slider.getValue());

            //e.g. do something to change the image here
            if (e.getSource() == zslice_slider) {
                if (histogramOn) {
                    // Update image
                    imageTop = HistogramTop(imageTop);
                    image_icon1.setIcon(new ImageIcon(imageTop));
                } else {
                    imageTop = top(imageTop);
                    image_icon1.setIcon(new ImageIcon(imageTop));
                }
            } else if (e.getSource() == yslice_slider) {
                if (histogramOn) {
                    imageFront = HistogramFront(imageFront);
                    image_icon2.setIcon(new ImageIcon(imageFront));
                } else {
                    imageFront = front(imageFront);
                    image_icon2.setIcon(new ImageIcon(imageFront));
                }
            } else if (e.getSource() == sideslice_slider) {
                if (histogramOn) {
                    imageSide = HistogramSide(imageSide);
                    image_icon3.setIcon(new ImageIcon(imageSide));
                } else {
                    imageSide = side(imageSide);
                    image_icon3.setIcon(new ImageIcon(imageSide));
                }
            } else if(e.getSource() == resize_slider){
                imageResize = resize() ;
                image_icon4.setIcon(new ImageIcon(imageResize));
            }
        }

        //action handlers (e.g. for buttons)
        public void actionPerformed(ActionEvent event) {
            if (event.getSource() == mip_button) {
                histogramOn = false;
                //e.g. do something to change the image here
                //e.g. call MIP function
                imageMIPTop = MIPTop(imageMIPTop);
                imageMIPFront = MIPFront(imageMIPFront);
                imageMIPSide = MIPSide(imageMIPSide);
                // Update image
                image_icon1.setIcon(new ImageIcon(imageMIPTop));
                image_icon2.setIcon(new ImageIcon(imageMIPFront));
                image_icon3.setIcon(new ImageIcon(imageMIPSide));
            }
            if (event.getSource() == his_button) {
                histogramOn = !histogramOn;
                if (!histogramOn) {
                    his_button.setText("HISTOGRAM - OFF");
                    imageTop = top(imageTop);
                    imageFront = front(imageFront);
                    imageSide = side(imageSide);
                    image_icon1.setIcon(new ImageIcon(imageTop));
                    image_icon2.setIcon(new ImageIcon(imageFront));
                    image_icon3.setIcon(new ImageIcon(imageSide));
                } else {
                    his_button.setText("HISTOGRAM - ON");
                    imageTop = HistogramTop(imageTop);
                    imageFront = HistogramFront(imageFront);
                    imageSide = HistogramSide(imageSide);
                    image_icon1.setIcon(new ImageIcon(imageTop));
                    image_icon2.setIcon(new ImageIcon(imageFront));
                    image_icon3.setIcon(new ImageIcon(imageSide));
                }
            }
        }
    }

    /*
        This function will return a pointer to an array
        of bytes which represent the image data in memory.
        Using such a pointer allows fast access to the image
        data for processing (rather than getting/setting
        individual pixels)
    */
    public static byte[] GetImageData(BufferedImage image) {
        WritableRaster WR = image.getRaster();
        DataBuffer DB = WR.getDataBuffer();
        if (DB.getDataType() != DataBuffer.TYPE_BYTE)
            throw new IllegalStateException("That's not of type byte");

        return ((DataBufferByte) DB).getData();
    }

    /*
        This function shows how to carry out an operation on an image.
        It obtains the dimensions of the image, and then loops through
        the image carrying out the copying of a slice of data into the
		image.
    */
    public BufferedImage top(BufferedImage image) {
        //Get image dimensions, and declare loop variables
        int w = image.getWidth(), h = image.getHeight(), i, j, c, k;
        //Obtain pointer to data for fast processing
        byte[] data = GetImageData(image);
        float col;
        short datum;
        //Shows how to loop through each pixel and colour
        //Try to always use j for loops in y, and i for loops in x
        //as this makes the code more readable
        for (j = 0; j < h; j++) {
            for (i = 0; i < w; i++) {
                //at this point (i,j) is a single pixel in the image
                //here you would need to do something to (i,j) if the image size
                //does not match the slice size (e.g. during an image resizing operation
                //If you don't do this, your j,i could be outside the array bounds
                //In the framework, the image is 256x256 and the data set slices are 256x256
                //so I don't do anything - this also leaves you something to do for the assignment
                datum = cthead[zslice_slider.getValue()][j][i]; //get values from slice 76 (change this in your assignment)
                //calculate the colour by performing a mapping from [min,max] -> [0,255]
                col = (255.0f * ((float) datum - (float) min) / ((float) (max - min)));
                for (c = 0; c < 3; c++) {
                    //and now we are looping through the bgr components of the pixel
                    //set the colour component c of pixel (i,j)
                    data[c + 3 * i + 3 * j * w] = (byte) col;
                } // colour loop
            } // column loop
        } // row loop

        return image;
    }

    /*
        This function show front image
     */
    public BufferedImage front(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight(), i, j, c, k;
        byte[] data = GetImageData(image);
        float col;
        short datum;
        for (j = 0; j < h; j++) {
            for (i = 0; i < w; i++) {
                datum = cthead[j][yslice_slider.getValue()][i];
                col = (255.0f * ((float) datum - (float) min) / ((float) (max - min)));
                for (c = 0; c < 3; c++) {
                    data[c + 3 * i + 3 * j * w] = (byte) col;
                }
            }
        }
        return image;
    }

    /*
        This function show side image
     */
    public BufferedImage side(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight(), i, j, c, k;
        byte[] data = GetImageData(image);
        float col;
        short datum;
        for (j = 0; j < h; j++) {
            for (i = 0; i < w; i++) {
                datum = cthead[j][i][sideslice_slider.getValue()];
                col = (255.0f * ((float) datum - (float) min) / ((float) (max - min)));
                for (c = 0; c < 3; c++) {
                    data[c + 3 * i + 3 * j * w] = (byte) col;
                }
            }
        }
        return image;
    }

    /*
        This function show the MIP of top image
     */
    public BufferedImage MIPTop(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight(), i, j, c, k;
        byte[] data = GetImageData(image);
        float col;
        short datum;

        for (j = 0; j < h; j++) {
            for (i = 0; i < w; i++) {
                short maxNum = min;

                for (k = 0; k < 113; k++) {
                    if (cthead[k][j][i] > maxNum) {
                        maxNum = cthead[k][j][i];
                    }
                    datum = maxNum;
                    col = (255.0f * ((float) datum - (float) min) / ((float) (max - min)));

                    for (c = 0; c < 3; c++) {
                        data[c + 3 * i + 3 * j * w] = (byte) col;
                    }
                }
            }
        }
        return image;
    }

    /*
    This function show the MIP of front image
 */
    public BufferedImage MIPFront(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight(), i, j, c, k;
        byte[] data = GetImageData(image);
        float col;
        short datum;

        for (j = 0; j < h; j++) {
            for (i = 0; i < w; i++) {
                short maxNum = min;

                for (k = 0; k < 256; k++) {
                    if (cthead[j][k][i] > maxNum) {
                        maxNum = cthead[j][k][i];
                    }
                    datum = maxNum;
                    col = (255.0f * ((float) datum - (float) min) / ((float) (max - min)));

                    for (c = 0; c < 3; c++) {
                        data[c + 3 * i + 3 * j * w] = (byte) col;
                    }
                }
            }
        }
        return image;
    }

    /*
    This function show the MIP of side image
 */
    public BufferedImage MIPSide(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight(), i, j, c, k;
        byte[] data = GetImageData(image);
        float col;
        short datum;
        for (j = 0; j < h; j++) {
            for (i = 0; i < w; i++) {
                short maxNum = min;

                for (k = 0; k < 256; k++) {
                    if (cthead[j][i][k] > maxNum) {
                        maxNum = cthead[j][i][k];
                    }
                    datum = maxNum;
                    col = (255.0f * ((float) datum - (float) min) / ((float) (max - min)));

                    for (c = 0; c < 3; c++) {
                        data[c + 3 * i + 3 * j * w] = (byte) col;
                    }
                }
            }
        }
        return image;
    }

    /*
    This function show the Histogram of top image
 */
    public BufferedImage HistogramTop(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight(), i, j, c, k, m;
        byte[] data = GetImageData(image);
        float col;
        short datum;
        int index;
        int histogram[] = new int[max - min + 1];
        int t[] = new int[max - min + 1];
        float mapping[] = new float[max - min + 1];
        float size = 256 * 256 * 113;
        for (m = 0; m < max - min + 1; m++) {
            histogram[m] = 0;
        }

        for (j = 0; j < 256; j++) {
            for (i = 0; i < 256; i++) {
                for (k = 0; k < 113; k++) {
                    index = cthead[k][j][i] - min;
                    histogram[index]++;
                }
            }
        }

        for (int n = 0; n < max - min + 1; n++) {
            if (n == 0) {
                t[0] = histogram[0];
            } else {
                t[n] = t[n - 1] + histogram[n];
            }
            mapping[n] = 255.0f * (t[n] / size);
        }

        for (j = 0; j < h; j++) {
            for (i = 0; i < w; i++) {
                datum = cthead[zslice_slider.getValue()][j][i];
                col = mapping[datum - min];
                for (c = 0; c < 3; c++) {
                    data[c + 3 * i + 3 * j * w] = (byte) col;
                }
            }
        }
        return image;
    }

    /*
This function show the Histogram of front image
*/
    public BufferedImage HistogramFront(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight(), i, j, c, k, m;
        byte[] data = GetImageData(image);
        float col;
        short datum;
        int index;
        int histogram[] = new int[max - min + 1];
        int t[] = new int[max - min + 1];
        float mapping[] = new float[max - min + 1];
        float size = 256 * 256 * 113;

        for (m = 0; m < max - min + 1; m++) {
            histogram[m] = 0;
        }

        for (j = 0; j < 256; j++) {
            for (i = 0; i < 256; i++) {
                for (k = 0; k < 113; k++) {
                    index = cthead[k][j][i] - min;
                    histogram[index]++;
                }
            }
        }

        for (int n = 0; n < max - min + 1; n++) {
            if (n == 0) {
                t[0] = histogram[0];
            } else {
                t[n] = t[n - 1] + histogram[n];
            }
            mapping[n] = 255.0f * (t[n] / size);
        }

        for (j = 0; j < h; j++) {
            for (i = 0; i < w; i++) {
                datum = cthead[j][yslice_slider.getValue()][i];
                col = mapping[datum - min];
                for (c = 0; c < 3; c++) {
                    data[c + 3 * i + 3 * j * w] = (byte) col;
                }
            }
        }
        return image;
    }

    /*
This function show the Histogram of side image
*/
    public BufferedImage HistogramSide(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight(), i, j, c, k, m;
        byte[] data = GetImageData(image);
        float col;
        short datum;
        int index;
        int histogram[] = new int[max - min + 1];
        int t[] = new int[max - min + 1];
        float mapping[] = new float[max - min + 1];
        float size = 256 * 256 * 113;

        for (m = 0; m < max - min + 1; m++) {
            histogram[m] = 0;
        }
        for (j = 0; j < 256; j++) {
            for (i = 0; i < 256; i++) {
                for (k = 0; k < 113; k++) {
                    index = cthead[k][j][i] - min;
                    histogram[index]++;
                }
            }
        }

        for (int n = 0; n < max - min + 1; n++) {
            if (n == 0) {
                t[0] = histogram[0];
            } else {
                t[n] = t[n - 1] + histogram[n];
            }
            mapping[n] = 255.0f * (t[n] / size);
        }

        for (j = 0; j < h; j++) {
            for (i = 0; i < w; i++) {
                datum = cthead[j][i][sideslice_slider.getValue()];
                col = mapping[datum - min];
                for (c = 0; c < 3; c++) {
                    data[c + 3 * i + 3 * j * w] = (byte) col;
                }
            }
        }
        return image;
    }

    /*
This function is for resizing the image
*/
    public BufferedImage resize() {
        int h = resize_slider.getValue();
        int w = resize_slider.getValue();
        BufferedImage image = new BufferedImage(h, w, BufferedImage.TYPE_3BYTE_BGR);
        int i, j, c;
        byte[] data = GetImageData(image);
        float col;
        short datum;
        for (j = 0; j < h; j++) {
            for (i = 0; i < w; i++) {
                float x = i * ((float) 256 / (float) h);
                float y = j * ((float) 256 / (float) w);
                datum = cthead[zslice_slider.getValue()][(int) y][(int) x];
                col = (255.0f * ((float) datum - (float) min) / ((float) (max - min)));
                for (c = 0; c < 3; c++) {
                    data[c + 3 * i + 3 * j * w] = (byte) col;
                }
            }
        }
        return image;
    }

    public static void main(String[] args) throws IOException {
        CTscan e = new CTscan();
        e.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        e.Example();
    }
}