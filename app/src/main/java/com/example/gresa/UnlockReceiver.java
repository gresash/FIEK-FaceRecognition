package com.example.gresa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.SyncStateContract;

import com.tzutalin.dlib.Constants;

import java.io.File;

public class UnlockReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)||intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)||intent.getAction().equals(Intent.ACTION_USER_PRESENT))
        {
            File file=new File(Constants.APP_DIR+ File.separator+"training.model");
            if(file.exists()) {
                Intent i = new Intent(context, CameraActivity.class);
                i.putExtra("isRecognizing", true);
                context.startActivity(i);
            }
        }
    }
}
