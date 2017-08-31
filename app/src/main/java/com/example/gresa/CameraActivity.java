/*
 * Copyright 2016-present Tzutalin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.gresa;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.tzutalin.dlib.Constants;

import java.io.File;


public class CameraActivity extends Activity {

    private static int OVERLAY_PERMISSION_REQ_CODE = 1;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera);
        new Thread(new Runnable() {
            @Override
            public void run() {

                if (!new File(Constants.getFaceShapeModelPath()).exists()) {
                    // mTransparentTitleView.setText("Copying landmark model to " + Constants.getFaceShapeModelPath());
                    FileUtils.copyFileFromRawToOthers(getBaseContext(), R.raw.shape_predictor_68_face_landmarks, Constants.getFaceShapeModelPath());
                }
                OpenCVFaceRecognizer.aktivizoDetektimin();
            }
        }).start();
        if (null == savedInstanceState) {
            CameraConnectionFragment fragment=new CameraConnectionFragment();
            fragment.isRecognizing=getIntent().getBooleanExtra("isRecognizing",false);
            if(getIntent().getStringExtra("name")!=null){
                fragment.name=getIntent().getStringExtra("name");
            };
            Log.d("Recognition", "IsRecognizing:"+fragment.isRecognizing);
            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container,fragment )
                    .commit();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this.getApplicationContext())) {
                    Toast.makeText(CameraActivity.this, "CameraActivity\", \"SYSTEM_ALERT_WINDOW, permission not granted...", Toast.LENGTH_SHORT).show();
                } else {
                    Intent intent = getIntent();
                    finish();
                    startActivity(intent);
                }
            }
        }
    }
}