package red.lilu.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.nio.ByteBuffer;

import red.lilu.app.databinding.ActivityRemoteControlViewBinding;

public class ActivityRemoteControlView extends AppCompatActivity {

    private static final String T = "调试";
    private ActivityRemoteControlViewBinding b;
    private MyApplication application;
    private LocalBroadcastManager localBroadcastManager;
    private LocalBroadcastReceiver localBroadcastReceiver;
    private SurfaceTexture surfaceTexture;
    private RTCVideoDecoder rtcVideoDecoder;
    private String targetID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRemoteControlViewBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        application = (MyApplication) getApplication();
        localBroadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
        localBroadcastReceiver = new LocalBroadcastReceiver();

        targetID = getIntent().getStringExtra("targetID");

        b.texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                surfaceTexture = surface;

                // 监听广播
                IntentFilter broadcastIntentFilter = new IntentFilter();
                broadcastIntentFilter.addAction("push");
                broadcastIntentFilter.addAction("remote_control_close");
                localBroadcastManager.registerReceiver(
                        localBroadcastReceiver,
                        broadcastIntentFilter
                );

                KcAPI.remoteControlSendRequest(
                        targetID,
                        application,
                        error -> {
                            Log.w(T, error);
                            runOnUiThread(() -> {
                                Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                                finish();
                            });
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

        localBroadcastManager.unregisterReceiver(localBroadcastReceiver);

        // 发出停止远程控制命令
        KcAPI.remoteControlClose(
                targetID,
                application,
                error -> {
                    Log.w(T, error);
                },
                done -> {
                    //
                }
        );

        if (rtcVideoDecoder != null) {
            rtcVideoDecoder.stop();
        }
    }

    private class LocalBroadcastReceiver extends BroadcastReceiver {

        private KcAPI.RemoteControlInfo remoteControlInfo;

        @Override
        public void onReceive(Context context, Intent intent) {
//            Log.d(T, "远程控制界面收到广播:" + intent.getAction());
            switch (intent.getAction()) {
                case "push":
                    String type = intent.getStringExtra("type");

                    if (type.equals("RemoteControlReceiveVideoInfo")) {
                        String json = intent.getStringExtra("json");
                        Log.i(T, "收到视频信息" + json);

                        b.textTips.setText("准备接收画面");

                        remoteControlInfo = application.getGson().fromJson(json, KcAPI.RemoteControlInfo.class);
                        resizeTextureView(
                                b.layoutWrap,
                                b.texture,
                                remoteControlInfo.width,
                                remoteControlInfo.height
                        );
                    } else if (type.equals("RemoteControlReceiveVideoData")) {
                        long presentationTimeUs = intent.getLongExtra("presentationTimeUs", 0);
                        byte[] data = intent.getByteArrayExtra("data");
//                        Log.d(T, String.format("收到视频数据: %d, %d", presentationTimeUs, data.length));

                        if (rtcVideoDecoder == null) {
                            rtcVideoDecoder = new RTCVideoDecoder(
                                    remoteControlInfo.width, remoteControlInfo.height,
                                    ByteBuffer.wrap(data),
                                    surfaceTexture,
                                    error -> {
                                        Log.w(T, error);
                                    }
                            );

                            b.textTips.setVisibility(View.INVISIBLE);
                        }

                        rtcVideoDecoder.decode(data, presentationTimeUs);
                    }

                    break;
                case "remote_control_close":
                    finish();

                    break;
            }
        }
    }

    private void resizeTextureView(View wrap, TextureView textureView, int videoWidth, int videoHeight) {
        // 获取外壳尺寸
        int wrapWidth = wrap.getWidth();
        int wrapHeight = wrap.getHeight();
        Log.d(T, String.format("外壳尺寸: %d,%d", wrapWidth, wrapHeight));

        // 按照视频比例计算SurfaceTexture在外壳内的最大尺寸
        int matchWidth = videoWidth;
        int matchHeight = videoHeight;
        if (videoWidth > wrapWidth) {
            // 按比例缩小宽度
            matchWidth = wrapWidth;
            matchHeight = (int) (matchWidth * (float) videoHeight / (float) videoWidth);
        }
        Log.d(T, String.format("缩小宽度尺寸: %d,%d", matchWidth, matchHeight));
        if (videoHeight > wrapHeight) {
            // 按比例缩小高度
            matchHeight = wrapHeight;
            matchWidth = (int) (matchHeight * (float) videoWidth / (float) videoHeight);
        }
        Log.d(T, String.format("缩小宽度尺寸: %d,%d", matchWidth, matchHeight));

        // 调整SurfaceTexture尺寸
        ViewGroup.LayoutParams layoutParams = textureView.getLayoutParams();
        layoutParams.width = matchWidth;
        layoutParams.height = matchHeight;
        textureView.setLayoutParams(layoutParams);
    }

}
