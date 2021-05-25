package red.lilu.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import red.lilu.app.databinding.ActivityRemoteControlAskBinding;

public class ActivityRemoteControlAsk extends AppCompatActivity {

    private static final String T = "调试";
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 2;
    private ActivityRemoteControlAskBinding b;
    private static MyApplication application;
    private Messenger messenger;
    private boolean serviceBound;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            messenger = new Messenger(service);
            serviceBound = true;

            init();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            messenger = null;
            serviceBound = false;
        }
    };
    private MediaProjectionManager mediaProjectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRemoteControlAskBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        application = (MyApplication) getApplication();
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        String id = getIntent().getStringExtra("id");
        String name = getIntent().getStringExtra("name");
        b.textId.setText(id);
        b.textName.setText(name);
    }

    @Override
    protected void onStart() {
        super.onStart();

        bindService(new Intent(this, ServiceForeground.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK) {
                MediaProjection mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                feedback(mediaProjection);
            } else {
                feedback(null);
            }
        }
    }

    private void init() {
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);
    }

    /**
     * 反馈
     *
     * @param mediaProjection 设为null表示拒绝
     */
    private void feedback(@Nullable MediaProjection mediaProjection) {
        Message message = new Message();
        message.what = ServiceForeground.MESSENGER_WHAT_REMOTE_CONTROL_ASK;
        message.obj = mediaProjection;
        try {
            Log.i(T, "发送媒体投影");
            messenger.send(message);

            finish();
        } catch (RemoteException e) {
            Log.w(T, e);
        }
    }

}
