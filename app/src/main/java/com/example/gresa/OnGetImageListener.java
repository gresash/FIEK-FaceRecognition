

package com.example.gresa;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Trace;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;
import com.tzutalin.dlibtest.ImageUtils;

import junit.framework.Assert;


import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;


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
    private FaceDet mFaceDet;
    private TrasparentTitleView mTransparentTitleView;
    private FloatingCameraWindow mWindow;
    private Paint mFaceLandmardkPaint;
    private ProgressDialog mDialog;

    private OpenCVFaceRecognizer objTrain,objRecognize;


    private int mframeNum = 0;

    public void initialize(
            final Context context,
            final AssetManager assetManager,
            final TrasparentTitleView scoreView,
            final Handler handler) {
        this.mContext = context;
        this.mTransparentTitleView = scoreView;
        this.mInferenceHandler = handler;
        mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        mWindow = new FloatingCameraWindow(mContext);
        isFrontFacing=false;
        mRecognized=false;
        mFaceLandmardkPaint = new Paint();
        mFaceLandmardkPaint.setColor(Color.GREEN);
        mFaceLandmardkPaint.setStrokeWidth(2);
        mFaceLandmardkPaint.setStyle(Paint.Style.STROKE);
        numPics=0;
        IMAGES_PATH=Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "dlib";
        TEST_IMAGES_PATH=IMAGES_PATH+File.separator+"test";
        if(!isRecognizing){
            label=getLabel();
        }

    }

    public void deInitialize() {
        synchronized (OnGetImageListener.this) {
            if (mFaceDet != null) {
                mFaceDet.release();
            }

            if (mWindow != null) {
                mWindow.release();
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
        mResizedBitmap = Bitmap.createScaledBitmap(mInversedBipmap, (int)(INPUT_SIZE/SCALE_RATIO), (int)(INPUT_SIZE/SCALE_RATIO), true);

        mInferenceHandler.post(
                new Runnable() {
                    @Override
                    public void run() {

                        if (!new File(Constants.getFaceShapeModelPath()).exists()) {
                           // mTransparentTitleView.setText("Copying landmark model to " + Constants.getFaceShapeModelPath());
                            FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks, Constants.getFaceShapeModelPath());
                        }

                        if(mframeNum % 3 == 0){
                            long startTime = System.currentTimeMillis();
                            synchronized (OnGetImageListener.this) {
                                results = mFaceDet.detect(mResizedBitmap);
                            }
                            long endTime = System.currentTimeMillis();
                           // mTransparentTitleView.setText("Time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");
                        }

                        // Draw on bitmap
                        if (results.size() == 1) {
                            VisionDetRet ret=results.get(0);


                            ArrayList<Point> landmarks = ret.getFaceLandmarks();
                            Canvas canvas = new Canvas(mInversedBipmap);
                            drawFaceMesh(landmarks,mFaceLandmardkPaint,canvas);
                            Log.d("Recognition", "IsRecognizing:"+isRecognizing);
                            float resizeRatio = 2f;
                            int top=(int) (topBound / resizeRatio);
                            int left=(int) (leftBound / resizeRatio);
                            int width= (int) ((rightBound - leftBound+10) / resizeRatio);
                            int height=(int) ((bottomBound - topBound+10) / resizeRatio);
                            if (isFrontFacing && numPics <5) {
                                Log.d("Recognizer", "Front facing");
                                Log.d("BitmapDimensions:","height:"+mResizedBitmap.getHeight()+"width: "+mResizedBitmap.getWidth()+ "left:" + left + " top:" + top + " width: " + width + " height:" + height);
                                if ((mResizedBitmap.getHeight() >= top + height) && (mResizedBitmap.getWidth() >= left + width) && (top>=0) && (left>=0)) {

                                    Bitmap faceImage = Bitmap.createBitmap(mResizedBitmap, left, top, width, height);
                                    Bitmap scaledFaceImage = faceImage.createScaledBitmap(faceImage,180,200,true);

                                    if (!isRecognizing) {
                                        if(name!=null) {
                                            mWindow.setMoreInformation("Training..." + numPics + "/5");
                                            numPics++;
                                            Log.d("FileSave", numPics + "trying to save");
                                            new FileManagerAsync(scaledFaceImage, label+"-" + name + numPics, "training").execute();
                                            if (numPics == 5) {
                                                objTrain = new OpenCVFaceRecognizer(false);

                                            }
                                        }
                                    } else {
                                            if(objRecognize==null) {
                                                mWindow.setMoreInformation("Recoginzing...");
                                                Log.d("Recognizer", "Testing recognizer");
                                                FileManagerAsync SaveImage =
                                                        new FileManagerAsync(scaledFaceImage, "FIEK-test", "test");
                                                try {
                                                    SaveImage.execute().get();
                                                } catch (Exception ex) {
                                                    throw new RuntimeException("Couldn't save image");
                                                }
                                                if (SaveImage.getStatus() == AsyncTask.Status.FINISHED) {
                                                    Log.d("Recognizer", "Test image saved in " + TEST_IMAGES_PATH + File.separator + "Gresa.png");

                                                    objRecognize = new OpenCVFaceRecognizer(true);

                                                }
                                            }

                                    }
                                }
                            }else {
                                    if (!isFrontFacing) {
                                        if(objRecognize!=null){
                                            if(objRecognize.isFinished) {
                                                if (objRecognize.mLabel != -1) {
                                                    String name = findNameFromLabel(objRecognize.mLabel);
                                                    mWindow.setMoreInformation("Hello, " + name);
                                                }
                                                else{
                                                    mWindow.setMoreInformation("Unknown face.");
                                                }
                                            }
                                        }

                                        Log.d("Recognizer", "Face successfully recognized!");
                                    }
                                }
                            }
                            else{
                            mRecognized=false;
                            isFrontFacing=false;
                            }


                        if(objTrain!=null){
                        if(objTrain.isFinished){
                            mWindow.setMoreInformation("Training finished!");

                        }
                        }

                        mframeNum++;
                        mWindow.setRGBBitmap(mInversedBipmap);
                        mIsComputing = false;
                    }

                });

        Trace.endSection();
    }
    private void drawFaceMesh(ArrayList<Point> landmarks,Paint mPaint,Canvas canvas){
        float resizeRatio = 2f;
        float[] points=new float[460];
        int[] indexes={0,1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,9,10,
                10,11,11,12,12,13,13,14,14,15,15,16,16,26,26,25,
                25,24,24,23,23,22,22,27,27,21,21,20,20,19,19,
                18,18,17,17,0,27,28,28,29,29,30,30,31,31,32,
                32,33,33,34,34,35,35,30,36,37,37,38,38,39,39,
                40,40,41,41,36,42,43,43,44,44,45,45,46,46,47,
                47,42,48,49,49,50,50,51,51,52,52,53,53,54,54,
                55,55,56,56,57,57,58,58,59,59,60,0,36,36,17,18,
                37,19,37,20,38,21,39,27,39,27,42,22,42,23,43,24,
                44,25,44,26,45,16,45,15,46,1,41,14,46,2,41,13,35,
                3,31,4,48,12,54,11,54,5,48,10,55,6,59,9,56,7,58,8,57,
                39,28,41,30,30,46,47,29,40,29,2,41,3,40,14,46,
                47,13,42,28,
                60,48,31,48,33,51,35,54,33,50,33,52,
                21,22,20,23,19,24};

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
            canvas.drawCircle(pointX,pointY,2,mPaint);
        }
        Rect bounds = new Rect();
        leftBound=(int)(leftBound-10*resizeRatio);
        topBound=(int)(topBound-10*resizeRatio);
        rightBound=(int)( rightBound+10*resizeRatio);
        bottomBound=(int)(bottomBound+10*resizeRatio);
        bounds.left = (int)(leftBound-10*resizeRatio);
        bounds.top = (int)(topBound-10*resizeRatio);
        bounds.right =(int)( rightBound+10*resizeRatio);
        bounds.bottom = (int)(bottomBound+10*resizeRatio);
        canvas.drawRect(bounds, mFaceLandmardkPaint);

        double rightDist=Math.sqrt(Math.pow(landmarks.get(30).x-landmarks.get(35).x,2)+Math.pow(landmarks.get(30).y-landmarks.get(35).y,2));
        double leftDist=Math.sqrt(Math.pow(landmarks.get(30).x-landmarks.get(31).x,2)+Math.pow(landmarks.get(30).y-landmarks.get(31).y,2));

        isFrontFacing=(rightDist/leftDist>0.8)&&(rightDist/leftDist<1.3)?true:false;

        int index=0;
        for (int i=0;i<indexes.length;i++) {
            int pointX = (int) (landmarks.get(indexes[i]).x * resizeRatio);
            int pointY = (int) (landmarks.get(indexes[i]).y * resizeRatio);
            points[index] = pointX;
            index++;
            points[index] = pointY;
            index++;

        }
        mPaint.setAlpha(50);
        canvas.drawLines(points,mPaint);
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

}

