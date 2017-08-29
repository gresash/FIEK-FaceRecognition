package com.example.gresa;

/**
 * Created by Ahmet on 21-Aug-17.
 */

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.tzutalin.dlib.Constants;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.IntBuffer;

import static org.bytedeco.javacpp.opencv_core.CV_32SC1;
import static org.bytedeco.javacpp.opencv_core.CV_8UC1;
import static org.bytedeco.javacpp.opencv_face.createFisherFaceRecognizer;
import static org.bytedeco.javacpp.opencv_face.createEigenFaceRecognizer;
import static org.bytedeco.javacpp.opencv_face.createLBPHFaceRecognizer;
import static org.bytedeco.javacpp.opencv_imgcodecs.imread;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.CV_LOAD_IMAGE_GRAYSCALE;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.opencv_face.FaceRecognizer;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;

public class OpenCVFaceRecognizer {
    public static final String TRAINING_DIR= Constants.APP_DIR+File.separator+"training";
    public static final String TEST_IMAGE=Constants.APP_DIR+File.separator+"test"+File.separator+"FIEK-test.png";
    private static FaceRecognizer faceRecognizerEigenfaces;
    private static FaceRecognizer faceRecognizerFisherfaces;
    private static FaceRecognizer faceRecognizerLBP;
    public RecognitionAsync recognitionTask;
    public boolean isFinished;
    public int mLabel;

    public OpenCVFaceRecognizer(boolean isRecognizing){
        isFinished=false;
        recognitionTask=new RecognitionAsync(isRecognizing);
        recognitionTask.execute();

    }

    public static void Train(){
    Log.d("Recognizer","Starting image training.");
        File root = new File(TRAINING_DIR);

        FilenameFilter imgFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                name = name.toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".pgm") || name.endsWith(".png");
            }
        };

        File[] imageFiles = root.listFiles(imgFilter);

        MatVector images = new MatVector(imageFiles.length);

        Mat labels = new Mat(imageFiles.length, 1, CV_32SC1);
        IntBuffer labelsBuf = labels.createBuffer();

        int counter = 0;

        for (File image : imageFiles) {
            Mat img = imread(image.getAbsolutePath(), CV_LOAD_IMAGE_GRAYSCALE);

            Mat videoMatGray = new Mat();
            // Convert the current frame to grayscale:
            //cvtColor(img, videoMatGray, COLOR_BGRA2GRAY);
            equalizeHist(img, img);


            int label = Integer.parseInt(image.getName().split("\\-")[0]);

            images.put(counter, img);

            labelsBuf.put(counter, label);

            counter++;
        }

        //faceRecognizer = createEigenFaceRecognizer();
        faceRecognizerEigenfaces = createEigenFaceRecognizer();
        faceRecognizerEigenfaces.setThreshold(2);
        faceRecognizerEigenfaces.train(images, labels);

       // faceRecognizerFisherfaces = createFisherFaceRecognizer();
        //faceRecognizerFisherfaces.setThreshold(5000);
        //faceRecognizerFisherfaces.train(images, labels);

        faceRecognizerLBP = createLBPHFaceRecognizer();
        faceRecognizerLBP.setThreshold(80);
        faceRecognizerLBP.train(images, labels);

        Log.d("Recognizer","Image training done.");
    }

    public static int Recognize(int algorithmInt) {
        Log.d("Recognizer","Starting recognition");
        Mat testImage = imread(TEST_IMAGE, CV_LOAD_IMAGE_GRAYSCALE);

        Mat videoMatGray = new Mat();
        // Convert the current frame to grayscale:
        //cvtColor(testImage, videoMatGray, COLOR_BGRA2GRAY);
        equalizeHist(testImage,testImage);


        String algorithm="";

        IntPointer label = new IntPointer(1);
        DoublePointer confidence = new DoublePointer(1);
        switch(algorithmInt) {
            case 1: faceRecognizerEigenfaces.predict(testImage, label, confidence); algorithm="Eigenfaces";break;
            case 2: //faceRecognizerFisherfaces.predict(testImage,label,confidence); algorithm="Fisherfaces";
                 break;
            case 3: faceRecognizerLBP.predict(testImage,label,confidence); algorithm="LBPH";break;
            default: break;
        }

        int predictedLabel = label.get(0);

        double confidencePrediction=confidence.get(0);

        Log.d("Recognizeri", algorithm+" Predicted label: " + predictedLabel+" Confidence: "+confidencePrediction);
        if(confidencePrediction>70) {
        return -1;
        }

        label.deallocate();
        confidence.deallocate();


        return predictedLabel;

    }
    private class RecognitionAsync extends AsyncTask<Void, Void, Void> {
        private boolean isRecognizing;
        public int label;

        public RecognitionAsync(boolean isRecognizing ) {

            this.isRecognizing = isRecognizing;

        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected Void doInBackground(Void... params) {
            if(isRecognizing){
                OpenCVFaceRecognizer.Recognize(1);
                OpenCVFaceRecognizer.Recognize(2);
                mLabel=OpenCVFaceRecognizer.Recognize(3);
                isFinished=true;

            }
            else{
                OpenCVFaceRecognizer.Train();
                isFinished=true;
                label=-1;
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {


        }
    }
}

