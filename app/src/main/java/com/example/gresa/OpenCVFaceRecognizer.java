package com.example.gresa;

/**
 * Created by Ahmet on 21-Aug-17.
 */

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.IntBuffer;
import java.util.ArrayList;

import static org.bytedeco.javacpp.opencv_core.CV_32SC1;
import static org.bytedeco.javacpp.opencv_core.CV_8UC1;
import static org.bytedeco.javacpp.opencv_face.createFisherFaceRecognizer;
import static org.bytedeco.javacpp.opencv_face.createEigenFaceRecognizer;
import static org.bytedeco.javacpp.opencv_face.createLBPHFaceRecognizer;
import static org.bytedeco.javacpp.opencv_imgcodecs.imdecode;
import static org.bytedeco.javacpp.opencv_imgcodecs.imread;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.CV_LOAD_IMAGE_GRAYSCALE;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.opencv_face.FaceRecognizer;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacv.FrameGrabber;

public class OpenCVFaceRecognizer {
    private static FaceRecognizer faceRecognizerLBP;
    public static FaceDet faceDetector;
    public RecognitionAsync recognitionTask;
    public ArrayList<Mat> trainingData;
    public Mat testImage;
    public boolean isFinished;
    public int mLabel;

    private boolean testMode;

    public OpenCVFaceRecognizer(boolean isRecognizing,ArrayList<Mat>trainingData,Mat testImage,boolean testMode ){

        isFinished=false;
        this.trainingData=trainingData;
        this.testImage=testImage;
        this.testMode=testMode;

        recognitionTask=new RecognitionAsync(isRecognizing);
        recognitionTask.execute();

    }
    public static void aktivizoDetektimin(){

        faceDetector=new FaceDet(Constants.getFaceShapeModelPath());
        Log.d("FaceDetector","facedetector:" +(faceDetector!=null));
    }

    public static void Train(ArrayList<Mat> trainingData,boolean testMode){
    Log.d("Recognizer","Starting image training.");

        // Create matrix where each image is represented as a column vector
        Log.d("SharedPrefs",trainingData.size()+" size");
        MatVector images = new MatVector(trainingData.size());

        Mat labels = new Mat(trainingData.size(), 1, CV_32SC1);
        IntBuffer labelsBuf = labels.createBuffer();

        int counter = 0;

        for (Mat image : trainingData) {
            //Mat img=new Mat();
            if(image!=null) {

                Log.d("MatDecode","rows:"+image.rows()+" cols:"+image.cols());
                    equalizeHist(image, image);
                    //trainingData.get(counter).copyTo(imagesMatrix.col(counter));
                    images.put(counter, image);

                    labelsBuf.put(counter, 1);

                    counter++;

            }
            else{
                Log.d("SharedPrefs","Mat null");
            }
        }


        faceRecognizerLBP = createLBPHFaceRecognizer();
        faceRecognizerLBP.setThreshold(100);
        if(!testMode) {
            faceRecognizerLBP.train(images, labels);
        }
        else{
            faceRecognizerLBP.update(images,labels);
        }
        Log.d("Recognizer","Image training done.");
        BytePointer bp =new BytePointer(Constants.APP_DIR+File.separator+"training.model");
        faceRecognizerLBP.save(bp);
        bp.deallocate();
    }

    public static int Recognize(Mat image,boolean testMode) {
        Log.d("Recognizer","Starting recognition ");


        if(faceRecognizerLBP==null) {
            BytePointer bp = new BytePointer(Constants.APP_DIR + File.separator + "training.model");
            faceRecognizerLBP = createLBPHFaceRecognizer();
            faceRecognizerLBP.load(bp);
            bp.deallocate();
        }


        Mat testImage = new Mat(image.rows(),image.cols());
        image.convertTo(testImage, CV_LOAD_IMAGE_GRAYSCALE);

        equalizeHist(testImage,testImage);


        String algorithm="";

        IntPointer label = new IntPointer(1);
        DoublePointer confidence = new DoublePointer(1);
        faceRecognizerLBP.predict(testImage,label,confidence); algorithm="LBPH";


        int predictedLabel = label.get(0);

        double confidencePrediction=confidence.get(0);
        label.deallocate();
        confidence.deallocate();
        Log.d("Recognizeri", algorithm+" Predicted label: " + predictedLabel+" Confidence: "+confidencePrediction);
        if(testMode && (confidencePrediction>30)){
            return -1;
        }
        if(confidencePrediction>100) {
        return -1;
        }

        label.deallocate();
        confidence.deallocate();



        return predictedLabel;

    }
    private class RecognitionAsync extends AsyncTask<Void, Void, Void> {
        private boolean isRecognizing;
        public int label;

        public RecognitionAsync(boolean isRecognizing) {

            this.isRecognizing = isRecognizing;


        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected Void doInBackground(Void... params) {
            if(isRecognizing){
                if(testImage!=null)
                mLabel=OpenCVFaceRecognizer.Recognize(testImage,testMode);
                isFinished=true;

            }
            else{
                if(trainingData!=null)
                OpenCVFaceRecognizer.Train(trainingData,testMode);
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

