package red.lilu.app;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

public class ActivityRemoteControlTaskDo extends AppCompatActivity {

    private static final String T = "调试";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.float_window_remote_control_task_do);

        Log.d(T, "触发无障碍服务检测");

//        finish();
    }
}
