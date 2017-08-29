package com.tzutalin.dlib;

import android.os.Environment;

import java.io.File;

/**
 * Created by darrenl on 2016/4/22.
 */
public final class Constants {
    private Constants() {
        // Constants should be prive
    }
    public static final String APP_DIR=Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "FIEK-FaceRecognizer";

    /**
     * getFaceShapeModelPath
     * @return default face shape model path
     */
    public static String getFaceShapeModelPath() {
        File sdcard = Environment.getExternalStorageDirectory();
        String targetPath = APP_DIR+File.separator+ "shape_predictor_68_face_landmarks.dat";
        return targetPath;
    }
    public static String getTrainingImagesZipPath(){
        File sdcard = Environment.getExternalStorageDirectory();
        String targetPath = APP_DIR+File.separator+"training.zip";
        return targetPath;

    }
}
