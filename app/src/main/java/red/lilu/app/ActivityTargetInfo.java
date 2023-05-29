package red.lilu.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;

import red.lilu.app.databinding.ActivityTargetInfoBinding;

public class ActivityTargetInfo extends AppCompatActivity {

    private static final String T = "调试";
    private static final int REQUEST_CODE_EDIT_NAME = 1;
    private ActivityTargetInfoBinding b;
    private MyApplication application;
    private ClipboardManager clipboardManager;
    private String qrcodePath;
    private String id;
    private KcAPI.Contact contact;
    private boolean infoUpdate = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityTargetInfoBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("对方信息");

        application = (MyApplication) getApplication();
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        id = getIntent().getStringExtra("id");
        qrcodePath = getExternalCacheDir() + "/qrcode.jpg";
        KcAPI.getContact(
                application,
                error -> {
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                        finish();
                    });
                },
                map -> {
                    runOnUiThread(() -> {
                        contact = map.get(id);
                        showInfo();
                    });
                }
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_EDIT_NAME && resultCode == RESULT_OK) {
            contact.nameRemark = data.getStringExtra("text");
            b.textNameRemark.setText(contact.nameRemark);
            saveNameRemark();
        }
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

    private void deleteContact() {
        KcAPI.delContact(
                id,
                application,
                error -> {
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                    });
                },
                result -> {
                    runOnUiThread(this::finish);
                }
        );
    }

    /**
     * 删除联系人
     */
    public void contactDelete(View view) {
        Snackbar.make(b.getRoot(), "确定要这样?", BaseTransientBottomBar.LENGTH_SHORT)
                .setAction("删除", v -> {
                    deleteContact();
                })
                .show();
    }

    /**
     * 拉黑联系人
     */
    public void contactBlacklist(View view) {
        Snackbar.make(b.getRoot(), "确定要这样?", BaseTransientBottomBar.LENGTH_SHORT)
                .setAction("加黑名单", v -> {
                    KcAPI.getOption(
                            application,
                            error -> {
                                runOnUiThread(() -> {
                                    Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                                });
                            },
                            option -> {
                                option.blacklist_id_array.add(id);

                                //
                                KcAPI.setOptionBlacklistID(
                                        TextUtils.join(",", option.blacklist_id_array),
                                        application,
                                        error -> {
                                            runOnUiThread(() -> {
                                                Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                                            });
                                        },
                                        result -> {
                                            deleteContact();
                                        });
                                //
                            });
                })
                .show();
    }

    void saveNameRemark() {
        KcAPI.setContactNameRemark(
                id,
                contact.nameRemark,
                application,
                error -> {
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                        finish();
                    });
                },
                info -> {
                    runOnUiThread(() -> {
                        Log.d(T, "已经保存节点信息名字备注");
                    });
                }
        );
    }

    void showInfo() {
        // 更新界面
        if (!contact.photo.isEmpty()) {
            GlideApp.with(this)
                    .load(
                            Base64.decode(contact.photo, Base64.NO_WRAP)
                    )
                    .circleCrop()
                    .into(b.imagePhoto);
        }
        b.textName.setText(contact.name);
        b.textNameRemark.setText(contact.nameRemark);
        b.textId.setText(id);
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
                        GlideApp.with(ActivityTargetInfo.this)
                                .load(new File(qrcodePath))
                                .into(b.imageQrcode);
                    });
                }
        );

        // 绑定功能
        b.buttonInfoRefresh.setOnClickListener(v -> {
            KcAPI.newContact(
                    contact.id,
                    application,
                    error -> {
                        runOnUiThread(() -> {
                            Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                            finish();
                        });
                    },
                    info -> {
                        runOnUiThread(() -> {
                            contact = info;
                            infoUpdate = true;
                            // 更新头像和名字
                            if (!contact.photo.isEmpty()) {
                                GlideApp.with(this)
                                        .load(
                                                Base64.decode(contact.photo, Base64.NO_WRAP)
                                        )
                                        .circleCrop()
                                        .into(b.imagePhoto);
                            }
                            b.textName.setText(contact.name);
                        });
                    }
            );
        });
        b.buttonNameRemarkEdit.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), ActivityEditText.class);
            intent.putExtra("title", "修改备注");
            intent.putExtra("text", b.textNameRemark.getText().toString());
            startActivityForResult(intent, REQUEST_CODE_EDIT_NAME);
        });
        b.buttonIdCopy.setOnClickListener(v -> {
            clipboardManager.setPrimaryClip(
                    ClipData.newPlainText(
                            "复制文字",
                            id
                    )
            );
            Toast.makeText(getApplicationContext(), "已经复制", Toast.LENGTH_SHORT).show();
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
    }

    void result() {
        if (infoUpdate) {
            setResult(RESULT_OK);
        }
        finish();
    }

}
