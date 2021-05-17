package red.lilu.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import red.lilu.app.databinding.ActivityRemoteControlViewBinding;

public class ActivityRemoteControlView extends AppCompatActivity {

    private static final String T = "调试";
    private ActivityRemoteControlViewBinding b;
    private MyApplication application;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRemoteControlViewBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        application = (MyApplication) getApplication();
    }

}
