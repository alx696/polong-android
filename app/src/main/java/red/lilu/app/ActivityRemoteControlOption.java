package red.lilu.app;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import red.lilu.app.databinding.ActivityRemoteControlOptionBinding;

public class ActivityRemoteControlOption extends AppCompatActivity {

    private static final String T = "调试";
    private ActivityRemoteControlOptionBinding b;
    private MyApplication application;
    private KeyguardManager keyguardManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRemoteControlOptionBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        application = (MyApplication) getApplication();
        keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 检测辅助服务是否正在运行
        boolean accessibilitySettingsOn = application.isAccessibilitySettingsOn(ServiceAccessibilityRemoteControl.class.getName());
        Log.d(T, "是否启用功能:" + accessibilitySettingsOn);
        b.textEnable.setText(accessibilitySettingsOn ? "是" : "否");

        // 检测是否开启解锁密码
        boolean isDeviceSecure = (keyguardManager != null && keyguardManager.isDeviceSecure());
        Log.d(T, "能否锁屏使用:" + isDeviceSecure);
        b.textLockUse.setText(!isDeviceSecure ? "是" : "否");

        // 检测是否能够显示悬浮窗口
        boolean drawOverlaysOn = Settings.canDrawOverlays(this);
        Log.d(T, "是否悬浮提示:" + drawOverlaysOn);
        b.textOverlay.setText(drawOverlaysOn ? "是" : "否");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        //监听按导航(返回)图标
        if (menuItem.getItemId() == android.R.id.home) {
            finish();

            //拦截
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //监听按返回键
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();

            //拦截
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void showAccessibilityServiceWindow(View v) {
        new MaterialAlertDialogBuilder(ActivityRemoteControlOption.this)
                .setTitle("需要你来操作")
                .setMessage("接下来会为你打开设置界面，在“已经下载的服务”中点“远程控制”，选择开启或关闭。")
                .setNegativeButton("取消", (dialog, which) -> {
                    dialog.cancel();
                })
                .setPositiveButton("继续", (dialog, which) -> {

                    dialog.cancel();

                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);

                })
                .show();
    }

    public void showkeyguardWindow(View v) {
        new MaterialAlertDialogBuilder(ActivityRemoteControlOption.this)
                .setTitle("需要你来操作")
                .setMessage("屏幕解锁如果需要密码，锁屏时不能使用远程控制！接下来会为你打开设置界面，你可以选择关闭屏幕解锁（锁屏）密码。")
                .setNegativeButton("取消", (dialog, which) -> {
                    dialog.cancel();
                })
                .setPositiveButton("继续", (dialog, which) -> {
                    dialog.cancel();

                    Intent intent = new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
                    startActivity(intent);
                })
                .show();
    }

    public void showOverlayPermissionWindow(View v) {
        new MaterialAlertDialogBuilder(ActivityRemoteControlOption.this)
                .setTitle("需要你来操作")
                .setMessage("接下来会为你打开设置界面，你可以选择开启或关闭。")
                .setNegativeButton("取消", (dialog, which) -> {
                    dialog.cancel();
                })
                .setPositiveButton("继续", (dialog, which) -> {

                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    startActivity(intent);

                })
                .show();
    }

}
