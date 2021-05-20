package red.lilu.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import red.lilu.app.databinding.ActivityRemoteControlViewBinding;

public class ActivityRemoteControlView extends AppCompatActivity {

    private static final String T = "调试";
    private ActivityRemoteControlViewBinding b;
    private MyApplication application;
    private LocalBroadcastManager broadcastManager;
    private LocalBroadcastReceiver localBroadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRemoteControlViewBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        application = (MyApplication) getApplication();
        broadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
        localBroadcastReceiver = new LocalBroadcastReceiver();

        KcAPI.requestRemoteControl(
                getIntent().getStringExtra("targetID"),
                application,
                error -> {
                    Log.w(T, error);
                },
                done -> {
                    //
                }
        );

        b.buttonClose.setOnClickListener(v -> {
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        broadcastManager.unregisterReceiver(localBroadcastReceiver);
    }

    private class LocalBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(T, "远程控制界面收到广播:" + intent.getAction());
            switch (intent.getAction()) {
                case "push":
                    String type = intent.getStringExtra("type");

                    if (type.equals("RemoteControlReceiveVideoInfo")) {
                        String json = intent.getStringExtra("json");
                        Log.w(T, "收到视频信息" + json);
                    } else if (type.equals("RemoteControlReceiveVideoData")) {
                        String presentationTimeUs = intent.getStringExtra("presentationTimeUs");
//                        byte[] data = intent.getByteArrayExtra("data");
                        Log.w(T, "收到视频数据" + presentationTimeUs);
//                        Log.w(T, "收到视频数据" + data.length);
                    }

                    break;
            }
        }
    }

}
