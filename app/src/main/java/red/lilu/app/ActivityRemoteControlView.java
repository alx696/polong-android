package red.lilu.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.nio.ByteBuffer;

import red.lilu.app.databinding.ActivityRemoteControlViewBinding;

public class ActivityRemoteControlView extends AppCompatActivity {

    private static final String T = "调试";
    private ActivityRemoteControlViewBinding b;
    private MyApplication application;
    private LocalBroadcastManager broadcastManager;
    private LocalBroadcastReceiver localBroadcastReceiver;
    private SurfaceTexture surfaceTexture;
    private RTCVideoDecoder rtcVideoDecoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRemoteControlViewBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        application = (MyApplication) getApplication();
        broadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
        localBroadcastReceiver = new LocalBroadcastReceiver();

        b.textureVideo.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                surfaceTexture = surface;

                // 监听广播
                IntentFilter broadcastIntentFilter = new IntentFilter();
                broadcastIntentFilter.addAction("push");
                broadcastManager.registerReceiver(
                        localBroadcastReceiver,
                        broadcastIntentFilter
                );

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
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
                //
            }
        });

        b.buttonClose.setOnClickListener(v -> {
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        broadcastManager.unregisterReceiver(localBroadcastReceiver);

        if (rtcVideoDecoder != null) {
            rtcVideoDecoder.stop();
        }
    }

    private class LocalBroadcastReceiver extends BroadcastReceiver {

        private KcAPI.RemoteControlInfo remoteControlInfo;

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(T, "远程控制界面收到广播:" + intent.getAction());
            switch (intent.getAction()) {
                case "push":
                    String type = intent.getStringExtra("type");

                    if (type.equals("RemoteControlReceiveVideoInfo")) {
                        String json = intent.getStringExtra("json");
                        Log.i(T, "收到视频信息" + json);

                        remoteControlInfo = application.getGson().fromJson(json, KcAPI.RemoteControlInfo.class);
                    } else if (type.equals("RemoteControlReceiveVideoData")) {
                        long presentationTimeUs = intent.getLongExtra("presentationTimeUs", 0);
                        byte[] data = intent.getByteArrayExtra("data");
                        Log.d(T, String.format("收到视频数据: %d, %d", presentationTimeUs, data.length));

                        if (rtcVideoDecoder == null) {
                            rtcVideoDecoder = new RTCVideoDecoder(
                                    remoteControlInfo.width, remoteControlInfo.height,
                                    ByteBuffer.wrap(data),
                                    surfaceTexture,
                                    error -> {
                                        Log.w(T, error);
                                    }
                            );
                        }

                        rtcVideoDecoder.decode(data, presentationTimeUs);
                    }

                    break;
            }
        }
    }

}
