package red.lilu.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import red.lilu.app.databinding.ActivityAlertBinding;

public class ActivityAlert extends AppCompatActivity {

    private static final String T = "调试";
    private ActivityAlertBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAlertBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        //禁止区域外触摸关闭
        setFinishOnTouchOutside(false);

        Intent intent = getIntent();
        String title = intent.getStringExtra("title");
        String content = intent.getStringExtra("content");

        b.textTitle.setText(title);
        b.textContent.setText(content);

        b.buttonClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_OK);
                finish();
            }
        });
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
}
