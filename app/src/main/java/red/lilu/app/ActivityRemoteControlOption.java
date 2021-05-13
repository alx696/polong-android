package red.lilu.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import red.lilu.app.databinding.ActivityRemoteControlOptionBinding;

public class ActivityRemoteControlOption extends AppCompatActivity {

    private static final String T = "调试";
    private ActivityRemoteControlOptionBinding b;
    private MyApplication application;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRemoteControlOptionBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        application = (MyApplication) getApplication();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 检测辅助服务是否正在运行
        boolean accessibilitySettingsOn = application.isAccessibilitySettingsOn(ServiceAccessibilityRemoteControl.class.getName());
        Log.d(T, "无障碍服务是否已经开启:" + accessibilitySettingsOn);
        b.switchEnable.setChecked(accessibilitySettingsOn);
        b.switchEnable.setText(accessibilitySettingsOn ? "启用" : "停用");
        b.switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {

            // 提示操作
            new MaterialAlertDialogBuilder(ActivityRemoteControlOption.this)
                    .setTitle("需要你来操作")
                    .setMessage("接下来请到“已经下载的服务”中点“远程控制”，然后开启或关闭！")
                    .setPositiveButton("继续", (dialog, which) -> {

                        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                        startActivity(intent);

                    })
                    .show();

        });

        // 检测是否能够显示悬浮窗口
        boolean drawOverlaysOn = Settings.canDrawOverlays(this);
        Log.d(T, "显示悬浮窗口是否已经开启:" + drawOverlaysOn);
        b.switchDrawOverlays.setChecked(drawOverlaysOn);
        b.switchDrawOverlays.setText(drawOverlaysOn ? "启用" : "停用");
        b.switchDrawOverlays.setOnCheckedChangeListener((buttonView, isChecked) -> {

            // 提示操作
            new MaterialAlertDialogBuilder(ActivityRemoteControlOption.this)
                    .setTitle("需要你来操作")
                    .setMessage("接下来请找到“显示悬浮窗”，然后允许或拒绝！")
                    .setPositiveButton("继续", (dialog, which) -> {

                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                        startActivity(intent);

                    })
                    .show();

        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        b.switchEnable.setOnCheckedChangeListener(null);
        b.switchDrawOverlays.setOnCheckedChangeListener(null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
}
