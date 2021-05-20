package red.lilu.app;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class ActivityRemoteControlAllow extends AppCompatActivity {

    private static final String T = "调试";
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 2;
    private static MyApplication application;
    private LocalBroadcastManager broadcastManager;
    private MediaProjectionManager mediaProjectionManager;
    private RTCScreenEncoder rtcScreenEncoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_control_task_do);

        Log.d(T, "允许远程控制");

        application = (MyApplication) getApplication();
        broadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            MediaProjection mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection == null) {
                Toast.makeText(getApplicationContext(), "拒绝分享屏幕!", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            record(mediaProjection);

//            //广播获取录屏权限
//            Intent broadcastIntent = new Intent("MediaProjection");
//            broadcastManager.sendBroadcast(broadcastIntent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (rtcScreenEncoder != null) {
            rtcScreenEncoder.stop();
        }
    }

    private void record(MediaProjection mediaProjection) {
        rtcScreenEncoder = new RTCScreenEncoder(
                application,
                mediaProjection,
                error -> {
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                    });
                },
                data -> {
                    KcAPI.sendRemoteControlVideoData(
                            data.presentationTimeUs,
                            data.bytes,
                            application,
                            error -> {
                                Log.w(T, error);
                            },
                            done -> {
                                //
                            }
                    );
                }
        );
        rtcScreenEncoder.start();
    }

}
