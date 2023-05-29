package red.lilu.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import red.lilu.app.databinding.ActivityInfoFromBinding;

public class ActivityInfoForm extends AppCompatActivity {

    private static final String T = "调试";
    private static final int REQUEST_CODE_SELECT_PHOTO = 2;
    private ActivityInfoFromBinding b;
    private MyApplication application;
    private String photoBase64 = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityInfoFromBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        //禁止区域外触摸关闭
        setFinishOnTouchOutside(false);

        application = (MyApplication) getApplication();

        b.textLayoutName.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().isEmpty()) {
                    b.textLayoutName.setError(null);
                }
            }
        });

        // 选择头像
        b.buttonPhoto.setOnClickListener(v -> {
            // https://developer.android.com/training/data-storage/shared/documents-files#open-file
            Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
            fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
            fileIntent.setType("image/*");
            startActivityForResult(fileIntent, REQUEST_CODE_SELECT_PHOTO);
        });

        // 确定
        b.buttonOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = b.textLayoutName.getEditText().getText().toString();
                if (name.isEmpty()) {
                    b.textLayoutName.setError("没有填写名字");
                    return;
                }

                if (photoBase64.isEmpty()) {
                    b.layoutImage.setBackgroundColor(Color.RED);
                    return;
                }

                KcAPI.setInfo(
                        name,
                        photoBase64,
                        application,
                        error -> {
                            runOnUiThread(() -> {
                                Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                                finish();
                            });
                        },
                        myInfo -> {
                            runOnUiThread(() -> {
                                setResult(RESULT_OK);
                                finish();
                            });
                        }
                );
            }
        });

        // 显示信息
        KcAPI.getOption(
                application,
                error -> {
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                        finish();
                    });
                },
                option -> {
                    if (!option.name.isEmpty()) {
                        runOnUiThread(() -> {
                            b.textLayoutName.getEditText().setText(option.name);

                            photoBase64 = option.photo;
                            GlideApp.with(getApplicationContext())
                                    .load(
                                            Base64.decode(photoBase64, Base64.NO_WRAP)
                                    )
                                    .circleCrop()
                                    .into(b.imagePhoto);
                        });
                    }
                }
        );
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //监听按返回键
        if (keyCode == KeyEvent.KEYCODE_BACK) {

            //拦截
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SELECT_PHOTO && resultCode == RESULT_OK) {
            Uri fileUri = data.getData();
            Log.d(T, "选择文件:" + fileUri.toString());

            b.progressPhoto.setVisibility(View.VISIBLE);
            CompletableFuture.runAsync(() -> {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(fileUri);
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(
                            bitmap,
                            256,
                            256,
                            false
                    );
                    bitmap.recycle();
                    inputStream.close();

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
                    photoBase64 = new String(
                            Base64.encode(bos.toByteArray(), Base64.NO_WRAP)
                    );
                    scaledBitmap.recycle();
                    bos.close();

                    // 更新
                    runOnUiThread(() -> {
                        b.progressPhoto.setVisibility(View.INVISIBLE);
                        GlideApp.with(this)
                                .load(
                                        Base64.decode(photoBase64, Base64.NO_WRAP)
                                )
                                .circleCrop()
                                .into(b.imagePhoto);
                        b.layoutImage.setBackgroundResource(0);
                    });
                } catch (Exception e) {
                    Log.w(T, e);
                    runOnUiThread(() -> {
                        b.progressPhoto.setVisibility(View.INVISIBLE);
                        Toast.makeText(getApplicationContext(), "头像无法使用", Toast.LENGTH_LONG).show();
                    });
                }
            });
        }

    }
}
