package red.lilu.app;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.rxjava3.RxDataStore;

import red.lilu.app.databinding.ActivityAboutBinding;

public class ActivityAbout extends AppCompatActivity {

    private static final String T = "调试";
    private ActivityAboutBinding b;
    private static MyApplication application;
    private String website;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        //禁止区域外触摸关闭
        setFinishOnTouchOutside(false);

        //获取系统服务
        application = (MyApplication) getApplication();

        Preferences preferences = RxDataStore.data(application.getPreferencesDataStore()).blockingFirst();
        website = preferences.get(MyApplication.SETTING_WEBSITE);
        Integer versionCode = preferences.get(MyApplication.SETTING_VERSION_CODE);

        b.textWebsite.setText(website);

        PackageInfo packageInfo = application.getPackageInfo();
        if (packageInfo != null) {
            b.textVersion.setText(String.format("版本：%s", packageInfo.versionName));
            if (versionCode > packageInfo.versionCode) {
                b.textVersionUpdate.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //拦截返回(按返回键, 点返回按钮)
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            //禁止返回

            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void close(View v) {
        finish();
    }

    public void visit(View v) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(
                Uri.parse(website)
        );
        startActivity(intent);
    }
}