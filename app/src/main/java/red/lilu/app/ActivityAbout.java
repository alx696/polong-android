package red.lilu.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import red.lilu.app.data_store.Pref;
import red.lilu.app.databinding.ActivityAboutBinding;

@kotlinx.coroutines.ExperimentalCoroutinesApi
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

        Pref pref = application.getDataStore().data().blockingFirst();
        website = pref.getWebsite();
        b.textWebsite.setText(website);
        b.textVersion.setText(String.format("版本：%s", application.getVersionName()));
        if (pref.getVersionCode() > application.getVersionCode()) {
            b.textVersionUpdate.setVisibility(View.VISIBLE);
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