package red.lilu.app;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ActivityRemoteControlTaskDo extends AppCompatActivity {

    private static final String T = "调试";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_control_task_do);

        Log.d(T, "触发无障碍服务对远程任务的检测");

        TextView taskTextView = findViewById(R.id.text_task);
        taskTextView.setText(getIntent().getStringExtra("json"));
    }

//    @Override
//    protected void onResume() {
//        super.onResume();
//
//        finish();
//    }
}
