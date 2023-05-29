package red.lilu.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AppCompatActivity;

import red.lilu.app.databinding.ActivityEditTextBinding;

public class ActivityEditText extends AppCompatActivity {

    private static final String T = "调试";
    private ActivityEditTextBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityEditTextBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        // 获取参数
        String title = getIntent().getStringExtra("title");
        String text = getIntent().getStringExtra("text");

        // 更新界面
        getSupportActionBar().setTitle(title);
        b.textLayoutText.getEditText().setText(text);
        // 默认打开软键盘没有效果?
        if (b.textLayoutText.requestFocus()) {
            imm.showSoftInput(b.textLayoutText, InputMethodManager.SHOW_IMPLICIT);
        }

        // 绑定功能
        b.buttonSave.setOnClickListener(v -> {
            String editText = b.textLayoutText.getEditText().getText().toString();
            if (editText.isEmpty()) {
                b.textLayoutText.setError("没有填写!");
                return;
            }

            Intent resultIntent = new Intent();
            resultIntent.putExtra("text", editText);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(menuItem);
    }

}
