

package com.example.gresa;

import android.app.Activity;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Trace;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;
import com.tzutalin.dlibtest.ImageUtils;

import junit.framework.Assert;


import org.androidannotations.annotations.App;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

import static org.bytedeco.javacpp.opencv_core.IPL_DEPTH_32S;
import static org.bytedeco.javacpp.opencv_core.cvGraphAddEdge;
import static org.bytedeco.javacpp.opencv_imgcodecs.CV_LOAD_IMAGE_GRAYSCALE;
import static org.bytedeco.javacpp.opencv_imgcodecs.imdecode;
import org.bytedeco.javacpp.BytePointer;

import org.bytedeco.javacpp.opencv_core.Mat;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */
public class OnGetImageListener implements OnImageAvailableListener {
    private static final boolean SAVE_PREVIEW_BITMAP = false;

    //324, 648, 972, 1296, 224, 448, 672, 976, 1344
    private static final int INPUT_SIZE = 1344;
    private static final int SCALE_RATIO=2;
    private static final String TAG = "OnGetImageListener";
    private static String IMAGES_PATH;
    private static String TEST_IMAGES_PATH;
    private int mScreenRotation = 90;

    private List<VisionDetRet> results;
    private int leftBound;
    private int rightBound;
    private int topBound;
    private int bottomBound;
    private boolean isFrontFacing;
    public boolean isRecognizing;
    public String name;
    private boolean mRecognized;
    private int mPreviewWdith = 0;
    private int mPreviewHeight = 0;
    private byte[][] mYUVBytes;
    private int[] mRGBBytes = null;
    private Bitmap mRGBframeBitmap = null;
    private Bitmap mCroppedBitmap = null;
    private Bitmap mResizedBitmap = null;
    private Bitmap mInversedBipmap = null;

    private boolean mIsComputing = false;
    private Handler mInferenceHandler;
    private int numPics;
    private int label;
    private Context mContext;
    private  FaceDet mFaceDet;
    private TextView txtInfo;
    private ProgressBar prgBar;
    private Paint mFaceLandmardkPaint;
    private CameraConnectionFragment fragment;
    private Activity mActivity;
    private OpenCVFaceRecognizer objTrain,objRecognize;
    private AppSharedPreferences sharedPreferences;
    private ArrayList<Bitmap> trainingData;
    private CountDownTimer timer;
    private boolean isTesting;
    private float resizeRatio = 2f;

    private int mframeNum = 0;

    public void initialize(
            final CameraConnectionFragment fragment,
            final Handler handler) {
        this.fragment=fragment;
        this.mActivity=fragment.getActivity();
        this.mContext = mActivity.getApplicationContext();
        sharedPreferences=new AppSharedPreferences(mContext);
        this.mInferenceHandler = handler;
        this.txtInfo=fragment.txtInfo;
        this.prgBar=fragment.prgBar;
        trainingData=new ArrayList<Bitmap>();
        isFrontFacing=false;
        mRecognized=false;
        isTesting=false;
        numPics=0;

    }

    public void deInitialize() {
        synchronized (OnGetImageListener.this) {
            if (mFaceDet != null) {
                mFaceDet.release();
            }

        }
    }

    private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {

        Display getOrient = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int orientation = Configuration.ORIENTATION_UNDEFINED;
        Point point = new Point();
        getOrient.getSize(point);
        int screen_width = point.x;
        int screen_height = point.y;
        Log.d(TAG, String.format("screen size (%d,%d)", screen_width, screen_height));
        if (screen_width < screen_height) {
            orientation = Configuration.ORIENTATION_PORTRAIT;
            mScreenRotation = -90;
        } else {
            orientation = Configuration.ORIENTATION_LANDSCAPE;
            mScreenRotation = 0;
        }

        Assert.assertEquals(dst.getWidth(), dst.getHeight());
        final float minDim = Math.min(src.getWidth(), src.getHeight());

        final Matrix matrix = new Matrix();

        // We only want the center square out of the original rectangle.
        final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
        final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
        matrix.preTranslate(translateX, translateY);

        final float scaleFactor = dst.getHeight() / minDim;
        matrix.postScale(scaleFactor, scaleFactor);

        // Rotate around the center if necessary.
        if (mScreenRotation != 0) {
            matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
            matrix.postRotate(mScreenRotation);
            matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
        }

        final Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, matrix, null);
    }

    public Bitmap imageSideInversion(Bitmap src){
        Matrix sideInversion = new Matrix();
        sideInversion.setScale(-1, 1);
        Bitmap inversedImage = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), sideInversion, false);
        return inversedImage;
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Log.d("FaceDetector","faceDetector: "+(OpenCVFaceRecognizer.faceDetector!=null)) ;
        if(mInferenceHandler!=null) {
            if(mFaceDet==null && OpenCVFaceRecognizer.faceDetector!=null){
                mFaceDet = OpenCVFaceRecognizer.faceDetector;
            }
            Image image = null;
            try {
                image = reader.acquireLatestImage();

                if (image == null) {
                    return;
                }

                // No mutex needed as this method is not reentrant.
                if (mIsComputing) {
                    image.close();
                    return;
                }
                mIsComputing = true;

                Trace.beginSection("imageAvailable");

                final Plane[] planes = image.getPlanes();

                // Initialize the storage bitmaps once when the resolution is known.
                if (mPreviewWdith != image.getWidth() || mPreviewHeight != image.getHeight()) {
                    mPreviewWdith = image.getWidth();
                    mPreviewHeight = image.getHeight();

                    //Log.d(TAG, String.format("Initializing at size %dx%d", mPreviewWdith, mPreviewHeight));
                    mRGBBytes = new int[mPreviewWdith * mPreviewHeight];
                    mRGBframeBitmap = Bitmap.createBitmap(mPreviewWdith, mPreviewHeight, Config.ARGB_8888);
                    mCroppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

                    mYUVBytes = new byte[planes.length][];
                    for (int i = 0; i < planes.length; ++i) {
                        mYUVBytes[i] = new byte[planes[i].getBuffer().capacity()];
                    }
                }

                for (int i = 0; i < planes.length; ++i) {
                    planes[i].getBuffer().get(mYUVBytes[i]);
                }

                final int yRowStride = planes[0].getRowStride();
                final int uvRowStride = planes[1].getRowStride();
                final int uvPixelStride = planes[1].getPixelStride();
                ImageUtils.convertYUV420ToARGB8888(
                        mYUVBytes[0],
                        mYUVBytes[1],
                        mYUVBytes[2],
                        mRGBBytes,
                        mPreviewWdith,
                        mPreviewHeight,
                        yRowStride,
                        uvRowStride,
                        uvPixelStride,
                        false);

                image.close();
            } catch (final Exception e) {
                if (image != null) {
                    image.close();
                }
                //Log.e(TAG, "Exception!", e);
                Trace.endSection();
                return;
            }

            mRGBframeBitmap.setPixels(mRGBBytes, 0, mPreviewWdith, 0, 0, mPreviewWdith, mPreviewHeight);
            drawResizedBitmap(mRGBframeBitmap, mCroppedBitmap);

            mInversedBipmap = imageSideInversion(mCroppedBitmap);
            mResizedBitmap = Bitmap.createScaledBitmap(mInversedBipmap, (int) (INPUT_SIZE / SCALE_RATIO), (int) (INPUT_SIZE / SCALE_RATIO), true);

            mInferenceHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {


                            if (mframeNum % 3 == 0 && OpenCVFaceRecognizer.faceDetector!=null) {
                                long startTime = System.currentTimeMillis();
                                synchronized (OnGetImageListener.this) {
                                    results = OpenCVFaceRecognizer.faceDetector.detect(mResizedBitmap);
                                }
                                long endTime = System.currentTimeMillis();
                                // mTransparentTitleView.setText("Time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");
                            }

                            // Draw on bitmap
                            if (results!=null && results.size() == 1) {
                                VisionDetRet ret = results.get(0);


                                ArrayList<Point> landmarks = ret.getFaceLandmarks();
                                processLandmarks(landmarks);
                                Log.d("Recognition", "IsRecognizing:" + isRecognizing);

                                Log.d("Recognizer", "Front facing "+isFrontFacing );

                                int top = (int) (topBound / resizeRatio);
                                int left = (int) (leftBound / resizeRatio);
                                int width = (int) ((rightBound - leftBound + 10) / resizeRatio);
                                int height = (int) ((bottomBound - topBound + 10) / resizeRatio);
                                if (isFrontFacing && numPics < 10) {
                                    Log.d("BitmapDimensions:", "height:" + mResizedBitmap.getHeight() + "width: " + mResizedBitmap.getWidth() + "left:" + left + " top:" + top + " width: " + width + " height:" + height);
                                    if ((mResizedBitmap.getHeight() >= top + height) && (mResizedBitmap.getWidth() >= left + width) && (top >= 0) && (left >= 0)) {

                                        Bitmap faceImage = Bitmap.createBitmap(mResizedBitmap, left, top, width, height);
                                        Bitmap scaledFaceImage = faceImage.createScaledBitmap(faceImage, 180, 200, true);

                                        if (!isRecognizing) {
                                            numPics++;
                                            jepInformate("Training...");
                                            vendosProgresin(numPics);
                                            shtoImazheNeListe(scaledFaceImage);
                                            if (numPics == 5) {
                                                sharedPreferences.putListMat("trainingData", trainingData);
                                                ArrayList<Mat>dataTest=sharedPreferences.getListMat("trainingData");
                                                objTrain = new OpenCVFaceRecognizer(false, dataTest, null,isTesting);

                                                Face.putSharedPreferences(sharedPreferences);


                                            }

                                        } else {
                                            if (objRecognize == null) {
                                                jepInformate("Recoginzing...");
                                                Log.d("Recognizer", "Testing recognizer");

                                                opencv_core.Mat testImage = convertBitmapToMat(scaledFaceImage);
                                                if (testImage != null) {
                                                    objRecognize = new OpenCVFaceRecognizer(true, null, testImage,isTesting);
                                                    jepInformate("Ju lutem levizni koken anash per verifikim!");

                                                }
                                            }

                                        }
                                    }
                                }
                                if (objRecognize != null) {
                                    if (objRecognize.isFinished) {
                                        if (objRecognize.mLabel != -1) {
                                            if(isTesting) {
                                                if (Face.krahasoKarakteristikatGjeometrike(sharedPreferences)) {
                                                    jepInformate("Training finished!");
                                                    isRecognizing = false;
                                                    isTesting = false;
                                                    System.exit(1);
                                                }
                                                else{
                                                    isRecognizing=false;
                                                    objRecognize=null;
                                                    numPics=0;
                                                }
                                            }

                                            if (!isFrontFacing) {
                                                if (Face.krahasoKarakteristikatGjeometrike(sharedPreferences)) {
                                                    jepInformate("Autentikim i suksesshem!");
                                                    System.exit(1);
                                                }
                                                else{
                                                    objRecognize=null;
                                                    jepInformate("");
                                                }
                                                    if(timer!=null){
                                                        timer.cancel();
                                                        timer=null;
                                                    }
                                            } else {
                                                if(timer==null) {
                                                    timer = new CountDownTimer(3000, 1000) {
                                                        /**
                                                         * Callback fired on regular interval.
                                                         *
                                                         * @param millisUntilFinished The amount of time until finished.
                                                         */
                                                        @Override
                                                        public void onTick(long millisUntilFinished) {

                                                        }

                                                        /**
                                                         * Callback fired when the time is up.
                                                         */
                                                        @Override
                                                        public void onFinish() {
                                                            jepInformate("Unknown face");
                                                            objRecognize = null;
                                                            timer=null;
                                                        }


                                                    }.start();

                                                }
                                            }

                                        } else {
                                            if(isTesting){
                                                isRecognizing=false;
                                                objRecognize=null;
                                                numPics=0;
                                            }
                                            else
                                            jepInformate("Unknown face.");
                                            objRecognize=null;
                                        }


                                    }

                                }


                            } else {
                                mRecognized = false;
                                isFrontFacing = false;
                                jepInformate("Recognizing...");
                            }


                            if (objTrain != null) {
                                if (objTrain.isFinished) {

                                    jepInformate("Testing");
                                    objTrain = null;
                                    isRecognizing=true;
                                    isTesting=true;
                                    numPics=0;
                                }
                            }

                            mframeNum++;
                            mIsComputing = false;
                        }

                    });

            Trace.endSection();
        }
    }

    private void jepInformate(final String informata){
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtInfo.setText(informata);
            }
        });
    }
    private void vendosProgresin(final int progresi){
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                prgBar.setProgress(progresi,true);
            }
        });
    }
    private void processLandmarks(ArrayList<Point> landmarks){

        double leftSideDimensions=eulerDistance(30,31,landmarks);
        double rightSideDimensions=eulerDistance(30,35,landmarks);
        // Draw landmark

        leftBound=(int)(landmarks.get(0).x*resizeRatio);
        topBound=(int)(landmarks.get(0).y*resizeRatio);
        rightBound=leftBound;
        bottomBound=topBound;

        for(Point point:landmarks){
            int pointX=(int)(point.x*resizeRatio);
            int pointY=(int)(point.y*resizeRatio);
            if(leftBound>pointX){
                leftBound=pointX;
            }
            if(topBound>pointY){
                topBound=pointY;
            }
            if(rightBound<pointX){
                rightBound=pointX;
            }
            if(bottomBound<pointY){
                bottomBound=pointY;
            }
        }

        leftBound=(int)(leftBound-10*resizeRatio);
        topBound=(int)(topBound-10*resizeRatio);
        rightBound=(int)( rightBound+10*resizeRatio);
        bottomBound=(int)(bottomBound+10*resizeRatio);
        isFrontFacing=detectFrontFacing(leftSideDimensions,rightSideDimensions);
        if(isFrontFacing) {
            Face.NOSE_WIDTH = eulerDistance(31, 35, landmarks);
            Face.NOSE_LENGTH = eulerDistance(27, 30, landmarks)/Face.NOSE_WIDTH;
            Face.LEFT_ANGLE = findAngle(30, 31, 33, landmarks)/Face.NOSE_WIDTH;
            Face.RIGHT_ANGLE = findAngle(30, 35, 34, landmarks)/Face.NOSE_WIDTH;
            Face.NOSE_LEFT = eulerDistance(30, 31, landmarks)/Face.NOSE_WIDTH;
            Face.NOSE_RIGHT = eulerDistance(30, 35, landmarks)/Face.NOSE_WIDTH;
        }

        }
    private double eulerDistance(int index1, int index2,ArrayList<Point> landmarks){
        double distance=Math.sqrt(Math.pow(landmarks.get(index1).x-landmarks.get(index2).x,2)+Math.pow(landmarks.get(index1).y-landmarks.get(index2).y,2));
        return distance;
    }
    private boolean detectFrontFacing(double leftDimensions,double rightDimensions){
        boolean isFrontFacing=true;
            isFrontFacing&=((rightDimensions/leftDimensions>0.9)&&(rightDimensions/leftDimensions<1.1));
            Log.d("FrontFacing","Ratio: "+rightDimensions/leftDimensions);

        return isFrontFacing;
    }
    private double findAngle(int index1, int index2, int index3, ArrayList<Point> landmarks){
        double length12=eulerDistance(index1,index2,landmarks);
        double length23=eulerDistance(index2,index3,landmarks);
        double vector1x=landmarks.get(index1).x-landmarks.get(index2).x;
        double vector2x=landmarks.get(index3).x-landmarks.get(index2).x;
        double vector1y=landmarks.get(index1).y-landmarks.get(index2).y;
        double vector2y=landmarks.get(index3).y-landmarks.get(index2).y;
        double scalarProduct=(vector1x*vector2x)+(vector1y*vector2y);
        double angle= Math.acos(scalarProduct/(length12*length23))*180/Math.PI;
        return angle;


    }


    private class FileManagerAsync extends AsyncTask<Void, Void, Void> {
        private String dir;
        private String fname;
        private Bitmap mBitmap;

        public FileManagerAsync(Bitmap bitmap, String fname,String dir) {

            this.mBitmap = bitmap;
            this.fname = fname;
            this.dir=dir;

        }


        @Override
        protected Void doInBackground(Void... params) {
            saveBitmap(mBitmap, fname,dir);
            return null;
        }


        /**
         * Saves a Bitmap object to disk for analysis.
         *
         * @param bitmap The bitmap to save.
         */
        public void saveBitmap(final Bitmap bitmap, String fname,String dir) {
            String root =
                    Constants.APP_DIR;
            if(dir!=null){
                root+=File.separator+dir;
            }
            Log.d("FileSave",root);
            //Timber.tag(TAG).d(String.format("Saving %dx%d bitmap to %s.", bitmap.getWidth(), bitmap.getHeight(), root));
            final File myDir = new File(root);

            if (!myDir.mkdirs()) {
                Log.d("FileSave", "Make dir failed");
            }
            fname += ".png";
            final File file = new File(myDir, fname);
            if (file.exists()) {
                file.delete();
            }
            try {
                final FileOutputStream out = new FileOutputStream(file);

                bitmap.compress(Bitmap.CompressFormat.PNG, 99, out);
                out.flush();
                out.close();
            } catch (final Exception e) {
                //Timber.tag(TAG).e("Exception!", e);
            }
        }
    }
        private int getLabel(){
            int maxLabel=1;
            File f = new File(Constants.APP_DIR+File.separator+"training");
            File file[] = f.listFiles();
            for(int i=0; i<file.length;i++){
                String[] fLabel=file[i].getName().split("-");
                int currentLabel=Integer.parseInt(fLabel[0]);
                if(currentLabel>maxLabel){
                    maxLabel=currentLabel;
                }
            }
            maxLabel++;
            return maxLabel;
        }
        private String findNameFromLabel(int label){
            String name="";
            File f = new File(Constants.APP_DIR+File.separator+"training");
            File file[] = f.listFiles();
            for(int i=0; i<file.length;i++){
                String[] fLabel=file[i].getName().split("-");
                if(Integer.parseInt(fLabel[0])==label){
                    name=fLabel[1].substring(0,fLabel[1].length()-5);
                }
            }
            return name;
        }

        private void shtoImazheNeListe(Bitmap objBitmap){
           if(objBitmap!=null)
                trainingData.add(objBitmap);
            }
        private Mat convertBitmapToMat(Bitmap bmp){
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] data = stream.toByteArray();
            Mat mat;
            mat=imdecode(new Mat(data),CV_LOAD_IMAGE_GRAYSCALE);

                return mat;
        }
        private static class Face{
            public static final String sNOSE_LENGTH="NOSE_LENGTH";
            public static final String sNOSE_WIDTH="NOSE_WIDTH";
            public static final String sNOSE_LEFT="NOSE_LEFT";
            public static final String sNOSE_RIGHT="NOSE_RIGHT";
            public static final String sLEFT_ANGLE="LEFT_ANGLE";
            public static final String sRIGHT_ANGLE="RIGHT_ANGLE";
            public static double NOSE_LENGTH;
            public static double NOSE_WIDTH;
            public static double NOSE_LEFT;
            public static double NOSE_RIGHT;
            public static double LEFT_ANGLE;
            public static double RIGHT_ANGLE;
            public static void putSharedPreferences(AppSharedPreferences sharedPreferences){
                sharedPreferences.putDouble(sNOSE_LEFT,NOSE_LEFT);
                sharedPreferences.putDouble(sNOSE_WIDTH,NOSE_WIDTH);
                sharedPreferences.putDouble(sNOSE_LENGTH,NOSE_LENGTH);
                sharedPreferences.putDouble(sNOSE_RIGHT,NOSE_RIGHT);
                sharedPreferences.putDouble(sLEFT_ANGLE,LEFT_ANGLE);
                sharedPreferences.putDouble(sRIGHT_ANGLE,RIGHT_ANGLE);

            }
            public static boolean krahasoKarakteristikatGjeometrike(AppSharedPreferences sharedPreferences){
                double noseLeft=sharedPreferences.getDouble(sNOSE_LEFT,0);
                double noseWidth=sharedPreferences.getDouble(sNOSE_WIDTH,0);
                double noseLength=sharedPreferences.getDouble(sNOSE_LENGTH,0);
                double noseRight=sharedPreferences.getDouble(sNOSE_RIGHT,0);
                double leftAngle=sharedPreferences.getDouble(sLEFT_ANGLE,0);
                double rightAngle=sharedPreferences.getDouble(sRIGHT_ANGLE,0);
                Log.d("recognizeri","nleft: "+noseLeft/NOSE_LEFT+" nright: "+noseRight/NOSE_RIGHT+" nWidth: "+noseWidth/NOSE_WIDTH+" nLen: "+noseLength/NOSE_LENGTH+" lAng: "+leftAngle/LEFT_ANGLE+" rAng: "+rightAngle/RIGHT_ANGLE);
                return (((noseLeft/NOSE_LEFT)<1.2)&&((noseLeft/NOSE_LEFT)>0.7) &&
                        ((noseLength/NOSE_LENGTH)<1.2)&&((noseLength/NOSE_LENGTH)>0.9) &&
                        ((noseRight/NOSE_RIGHT)<1.2)&&((noseRight/NOSE_RIGHT)>0.9) &&
                        ((leftAngle/LEFT_ANGLE)<1.2)&&((leftAngle/LEFT_ANGLE)>0.9) &&
                        ((rightAngle/RIGHT_ANGLE)<1.2)&&((rightAngle/RIGHT_ANGLE)>0.8));
            }
    }

}

