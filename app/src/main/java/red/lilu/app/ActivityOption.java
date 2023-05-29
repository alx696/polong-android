package red.lilu.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

import io.reactivex.rxjava3.core.Single;
import red.lilu.app.data_store.Pref;
import red.lilu.app.databinding.ActivityOptionBinding;

@kotlinx.coroutines.ExperimentalCoroutinesApi
public class ActivityOption extends AppCompatActivity {

    private static final String T = "调试";
    private static final int REQUEST_CODE_EDIT_INFO = 1;
    private ActivityOptionBinding b;
    private MyApplication application;
    private ClipboardManager clipboardManager;
    private String id;
    private String qrcodePath;
    private boolean optionChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityOptionBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("设置");

        application = (MyApplication) getApplication();
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        id = getIntent().getStringExtra("id");

        b.textId.setText(id);

        qrcodePath = getExternalCacheDir() + "/qrcode.jpg";
        QcAPI.encode(
                qrcodePath, id,
                application.getExecutorService(),
                error -> {
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                    });
                },
                done -> {
                    runOnUiThread(() -> {
                        GlideApp.with(ActivityOption.this)
                                .load(new File(qrcodePath))
                                .into(b.imageQrcode);
                    });
                }
        );

        b.switchRemoteControl.setChecked(
                application.getDataStore().data().blockingFirst().getRemoteControlEnable()
        );

        showOption();

        b.buttonIdCopy.setOnClickListener(v -> {
            clipboardManager.setPrimaryClip(
                    ClipData.newPlainText(
                            "破号",
                            id
                    )
            );
            Toast.makeText(getApplicationContext(), "已经复制", Toast.LENGTH_SHORT).show();
        });

        b.buttonInfoEdit.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), ActivityInfoForm.class);
            startActivityForResult(intent, REQUEST_CODE_EDIT_INFO);
        });

        b.switchRemoteControl.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Single<Pref> prefSingle = application.getDataStore().updateDataAsync(
                        updatePref -> Single.just(
                                updatePref.toBuilder()
                                        .setRemoteControlEnable(isChecked)
                                        .build()
                        )
                );
                prefSingle.blockingSubscribe();
            }
        });

        b.buttonShareQrcode.setOnClickListener(v -> {
            MyApplication.fileShare(
                    this,
                    qrcodePath,
                    error -> {
                        runOnUiThread(() -> {
                            Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                        });
                    }
            );
        });

        b.textLayoutBootstrap.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                if (!text.isEmpty()) {
                    text = text.trim().replaceAll("\n", ",");
                }

                KcAPI.setOptionBootstrap(
                        text,
                        application,
                        error -> {
                            runOnUiThread(() -> {
                                b.textLayoutBootstrap.setError(error);
                            });
                        },
                        result -> {
                            runOnUiThread(() -> {
                                b.textLayoutBootstrap.setError(null);
                                optionChanged = true;
                            });
                        }
                );
            }
        });

        b.textLayoutBlacklist.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                if (!text.isEmpty()) {
                    text = text.trim().replaceAll("\n", ",");
                }

                KcAPI.setOptionBlacklistID(
                        text,
                        application,
                        error -> {
                            runOnUiThread(() -> {
                                b.textLayoutBlacklist.setError(error);
                            });
                        },
                        result -> {
                            runOnUiThread(() -> {
                                b.textLayoutBootstrap.setError(null);
                                optionChanged = true;
                            });
                        }
                );
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        //监听按导航(返回)图标
        if (menuItem.getItemId() == android.R.id.home) {
            result();

            //拦截
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //监听按返回键
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            result();

            //拦截
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_EDIT_INFO && resultCode == RESULT_OK) {
            optionChanged = true;
            showOption();
        }
    }

    void showOption() {
        KcAPI.getOption(
                application,
                error -> {
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                        finish();
                    });
                },
                option -> {
                    runOnUiThread(() -> {
                        b.textName.setText(option.name);
                        GlideApp.with(this)
                                .load(
                                        Base64.decode(option.photo, Base64.NO_WRAP)
                                )
                                .circleCrop()
                                .into(b.imagePhoto);

                        b.textLayoutBootstrap.getEditText().setText(
                                TextUtils.join("\n", option.bootstrap_array)
                        );

                        b.textLayoutBlacklist.getEditText().setText(
                                TextUtils.join("\n", option.blacklist_id_array)
                        );
                    });
                }
        );
    }

    void result() {
        if (optionChanged) {
            setResult(RESULT_OK);
        }
        finish();
    }

}
