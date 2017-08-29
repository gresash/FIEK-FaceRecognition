/*
*  Copyright (C) 2015-present TzuTaLin
*/

package com.example.gresa;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.dexafree.materialList.card.Card;
import com.dexafree.materialList.card.provider.BigImageCardProvider;
import com.dexafree.materialList.view.MaterialListView;
import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.PedestrianDet;
import com.tzutalin.dlib.VisionDetRet;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import hugo.weaving.DebugLog;
import timber.log.Timber;

@EActivity(R.layout.activity_main)
public class MainActivity extends AppCompatActivity {
    private static final int RESULT_LOAD_IMG = 1;
    private static final int REQUEST_CODE_PERMISSION = 2;
    private String name;
    private static final String TAG = "MainActivity";

    // Storage Permissions
    private static String[] PERMISSIONS_REQ = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,

    };

    protected String mTestImgPath;
    private AnimationDrawable faceAnimation;


    FaceDet mFaceDet;
    PedestrianDet mPersonDet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Just use hugo to print log
        isExternalStorageWritable();
        isExternalStorageReadable();

        // For API 23+ you need to request the read/write permissions even if they are already in your manifest.
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;

        if (currentapiVersion >= Build.VERSION_CODES.M) {
            verifyPermissions(this);
        }
        runDetectAsync();
        //startActivity(new Intent(this, CameraActivity.class));

    }

    @AfterViews
    protected void setupUI() {
        ImageView faceImage = (ImageView) findViewById(R.id.imvLogo);
        faceImage.setBackgroundResource(R.drawable.face_animation);
        faceAnimation= (AnimationDrawable) faceImage.getBackground();
        faceAnimation.start();
        //Toast.makeText(MainActivity.this, getString(R.string.description_info), Toast.LENGTH_LONG).show();
    }

    @Click({R.id.btnRecognize})
    protected void Recognize() {
        Intent i=new Intent(this, CameraActivity.class);
        i.putExtra("isRecognizing",true);
        startActivity(i);
    }

    @Click({R.id.btnTrain})
    protected void Train() {

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        final EditText edittext = new EditText(MainActivity.this);
        //alert.setMessage("Enter New Name");
        alert.setTitle("Enter New Name");

        alert.setView(edittext);

        alert.setPositiveButton("Train", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

               name = edittext.getText().toString();
                if(!name.equals("")) {
                    Intent i = new Intent(MainActivity.this, CameraActivity.class);
                    i.putExtra("isRecognizing", false);
                    i.putExtra("name", name);
                    startActivity(i);
                }
            }
        });
        alert.show();

    }

    /**
     * Checks if the app has permission to write to device storage or open camera
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    @DebugLog
    private static boolean verifyPermissions(Activity activity) {
        // Check if we have write permission
        int write_permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int read_persmission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE);
        int camera_permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);

        if (write_permission != PackageManager.PERMISSION_GRANTED ||
                read_persmission != PackageManager.PERMISSION_GRANTED ||
                camera_permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_REQ,
                    REQUEST_CODE_PERMISSION
            );
            return false;
        } else {
            return true;
        }
    }

    /* Checks if external storage is available for read and write */
    @DebugLog
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    @DebugLog
    private boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION) {
                //runDetectAsync();
        }
    }



    // ==========================================================
    // Tasks inner class
    // ==========================================================
    private ProgressDialog mDialog;

    @Background
    @NonNull
    protected void runDetectAsync() {
        showDiaglog();

        File appDir=new File(Constants.APP_DIR);
        if(!appDir.exists()){
            appDir.mkdirs();
            Log.d("ExtFiles",Constants.APP_DIR+" created");
        }

        final String faceShapeModelPath = Constants.getFaceShapeModelPath();
        if (!new File(faceShapeModelPath).exists()) {
            FileUtils.copyFileFromRawToOthers(getApplicationContext(), R.raw.shape_predictor_68_face_landmarks, faceShapeModelPath);
        }

        final String trainingDirPath=Constants.APP_DIR+File.separator+"training";
        File trainingDir=new File(trainingDirPath);

        if(!trainingDir.exists()||trainingDir.listFiles().length==0){

            Log.d("ExtFiles","training.zip created to "+Constants.getTrainingImagesZipPath());
            FileUtils.copyFileFromRawToOthers(getApplicationContext(),R.raw.training,Constants.getTrainingImagesZipPath());
            Log.d("ExtFiles","Starting extraction");
            if(FileUtils.unpackTrainingZip(Constants.APP_DIR,"training.zip")){
                new File(Constants.getTrainingImagesZipPath()).delete();
            }
         }if(trainingDir.exists()) {

            OpenCVFaceRecognizer objRecognizer = new OpenCVFaceRecognizer(false);
            while (!objRecognizer.isFinished) {

            }
        }

       dismissDialog();
    }


    @UiThread
    protected void showDiaglog() {
        mDialog = ProgressDialog.show(MainActivity.this, "Wait", "FIEK-FaceRecognition", true);
    }

    @UiThread
    protected void dismissDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }




}
