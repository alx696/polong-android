package red.lilu.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import kc.Kc;
import red.lilu.app.databinding.ActivityAddBinding;

public class ActivityAdd extends AppCompatActivity {

    private static final String T = "调试";
    private static final int REQUEST_CODE_QRCODE = 1;
    private ActivityAddBinding b;
    private MyApplication application;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAddBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("添加破友");

        application = (MyApplication) getApplication();

        // 绑定功能
        b.buttonAdd.setOnClickListener(v -> {
            String id = b.textLayoutId.getEditText().getText().toString();
            if (id.isEmpty()) {
                b.textLayoutId.setError("没有填写!");
                return;
            }

            KcAPI.newContact(
                    id,
                    application,
                    error -> {
                        runOnUiThread(() -> {
                            b.textLayoutId.setError(error);
                        });
                    },
                    result -> {
                        runOnUiThread(() -> {
                            setResult(RESULT_OK);
                            finish();
                        });
                    }
            );
        });
        b.buttonScanQrcode.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), ActivityQrCode.class);
            startActivityForResult(intent, REQUEST_CODE_QRCODE);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_QRCODE && resultCode == RESULT_OK) {
            String id = data.getStringExtra("text");

            // 检查ID是否正确
            try {
                Kc.idValid(id);
                b.textLayoutId.getEditText().setText(id);
            } catch (Exception e) {
                Log.d(T, e.getMessage());
                Toast.makeText(getApplicationContext(), "二维码无效", Toast.LENGTH_LONG).show();
            }
        }
    }
}
