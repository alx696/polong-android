package red.lilu.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.io.Files;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.function.Consumer;

import red.lilu.app.databinding.ActivityChatBinding;

public class ActivityChat extends AppCompatActivity implements RecyclerViewAdapterChatMessage.Callback {

    private static final String T = "调试";
    private static final int REQUEST_CODE_INFO = 1;
    private static final int REQUEST_CODE_FILE = 2;
    private static final int PERMISSION_REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 3;
    private ActivityChatBinding b;
    private MyApplication application;
    private LocalBroadcastManager broadcastManager;
    private LocalBroadcastReceiver localBroadcastReceiver;
    private LinearLayoutManager layoutManager;
    private RecyclerViewAdapterChatMessage adapter;
    private Consumer<String> onError;
    private String myID, targetID;
    private KcAPI.Contact targetContact;
    private Bitmap myPhotoBitmap, targetPhotoBitmap;
    private String copyToPublicDownloadFilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("会话");

        application = (MyApplication) getApplication();
        broadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
        localBroadcastReceiver = new LocalBroadcastReceiver();

        // 获取参数
        myID = getIntent().getStringExtra("myID");
        targetID = getIntent().getStringExtra("targetID");

        //准备界面
        b.recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        b.recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration itemDecoration = new DividerItemDecoration(
                b.recyclerView.getContext(),
                layoutManager.getOrientation()
        );
        itemDecoration.setDrawable(
                new ColorDrawable(ContextCompat.getColor(getApplicationContext(), R.color.grey))
        );
        b.recyclerView.addItemDecoration(itemDecoration);
        adapter = new RecyclerViewAdapterChatMessage(
                application,
                filepath -> {
                    runOnUiThread(() -> {
                        MyApplication.fileShare(
                                this,
                                filepath,
                                error -> {
                                }
                        );
                    });
                },
                filepath -> {
                    runOnUiThread(() -> {
                        MyApplication.fileView(
                                this,
                                filepath,
                                null,
                                error -> {
                                }
                        );
                    });
                },
                this
        );
        b.recyclerView.setAdapter(adapter);

        onError = error -> {
            runOnUiThread(() -> {
                Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
            });
        };

        // 获取我的和对方信息
        KcAPI.getOption(
                application,
                onError,
                option -> {
                    if (!option.photo.isEmpty()) {
                        byte[] myPhotoData = Base64.decode(option.photo, Base64.NO_WRAP);
                        myPhotoBitmap = BitmapFactory.decodeByteArray(myPhotoData, 0, myPhotoData.length);
                    }
                    //
                    KcAPI.getContact(
                            application,
                            onError,
                            map -> {
                                runOnUiThread(() -> {
                                    targetContact = map.get(targetID);

                                    if (!targetContact.photo.isEmpty()) {
                                        byte[] targetPhotoData = Base64.decode(targetContact.photo, Base64.NO_WRAP);
                                        targetPhotoBitmap = BitmapFactory.decodeByteArray(targetPhotoData, 0, targetPhotoData.length);
                                    }

                                    init();
                                });
                            }
                    );
                    //
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        //广播界面状态
        Intent broadcastIntent = new Intent("ui");
        broadcastIntent.putExtra("chatTargetID", targetID);
        broadcastManager.sendBroadcast(broadcastIntent);
    }

    @Override
    protected void onPause() {
        super.onPause();

        //广播界面状态
        Intent broadcastIntent = new Intent("ui");
        broadcastIntent.putExtra("chatTargetID", "");
        broadcastManager.sendBroadcast(broadcastIntent);

        //标记消息已读
        KcAPI.setChatMessageReadByPeerID(
                targetContact.id,
                application,
                error -> {
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                    });
                },
                result -> {
                    //
                }
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        broadcastManager.unregisterReceiver(localBroadcastReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.clear) {
            Snackbar.make(b.recyclerView, "确定要这样?", BaseTransientBottomBar.LENGTH_SHORT)
                    .setAction("清空消息", v -> {
                        KcAPI.deleteChatMessageByPeerID(
                                targetContact.id,
                                application,
                                onError,
                                result -> {
                                    runOnUiThread(() -> {
                                        adapter.clear();
                                    });
                                }
                        );
                    })
                    .show();
        } else if (id == R.id.info) {
            Intent intent = new Intent(getApplicationContext(), ActivityTargetInfo.class);
            intent.putExtra("id", targetContact.id);
            startActivityForResult(intent, REQUEST_CODE_INFO);
        } else if (id == R.id.remote_control) {
            Intent intent = new Intent(getApplicationContext(), ActivityRemoteControlView.class);
            intent.putExtra("targetID", targetContact.id);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_FILE && resultCode == RESULT_OK) {
            String[] paths = data.getStringArrayExtra("paths");
            for (String path : paths) {
                sendMessage("", path);
            }
        }

        if (requestCode == REQUEST_CODE_INFO && resultCode == RESULT_OK) {
            //更新对方信息
            KcAPI.getContact(
                    application,
                    onError,
                    map -> {
                        runOnUiThread(() -> {
                            Log.d(T, "更新对方信息");

                            targetContact = map.get(targetID);

                            if (!targetContact.photo.isEmpty()) {
                                byte[] targetPhotoData = Base64.decode(targetContact.photo, Base64.NO_WRAP);
                                targetPhotoBitmap = BitmapFactory.decodeByteArray(targetPhotoData, 0, targetPhotoData.length);

                                adapter.updateTargetBitmap(targetPhotoBitmap);
                            }
                        });
                    }
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE_WRITE_EXTERNAL_STORAGE) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                copyFileToPublicDownload();
            } else {
                Toast.makeText(getApplicationContext(), "没有存储权限!", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void onRecyclerViewAdapterChatMessageCopyFileToPublicDownload(String filePath) {
        copyToPublicDownloadFilePath = filePath;

        if (ContextCompat.checkSelfPermission(
                application.getApplicationContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED) {
            copyFileToPublicDownload();
        } else {
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE_WRITE_EXTERNAL_STORAGE
            );
        }
    }

    @Override
    public void onRecyclerViewAdapterChatMessageSend(String text, String path) {
        sendMessage(text, path);
    }

    @Override
    public void onRecyclerViewAdapterChatMessageDelete(long id) {
        KcAPI.deleteChatMessageByID(
                id,
                application,
                onError,
                result -> {
                    runOnUiThread(() -> {
                        adapter.delete(id);
                    });
                }
        );
    }

    class LocalBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
//            Log.d(T, "会话界面收到广播:" + intent.getAction());
            switch (intent.getAction()) {
                case "push":
                    String type = intent.getStringExtra("type");

                    if (type.equals("ContactDelete") && intent.getStringExtra("id").equals(targetID)) {
                        finish();
                    }

                    if (type.equals("ChatMessage")) {
                        String peerID = intent.getStringExtra("peerID");
                        if (!peerID.equals(myID) && !peerID.equals(targetContact.id)) {
                            return;
                        }

                        KcAPI.ChatMessage data = application.getGson().fromJson(
                                intent.getStringExtra("json"),
                                new TypeToken<KcAPI.ChatMessage>() {
                                }.getType()
                        );
                        adapter.add(data);
                        layoutManager.scrollToPosition(
                                adapter.getItemCount() - 1
                        );
                    }

                    if (type.equals("ChatMessageState")) {
                        String peerID = intent.getStringExtra("peerID");
                        if (!peerID.equals(myID) && !peerID.equals(targetContact.id)) {
                            return;
                        }

                        long messageID = intent.getLongExtra("messageID", 0);
                        String state = intent.getStringExtra("state");
                        adapter.updateState(messageID, state);
                    }

                    break;
            }
        }
    }

    private AlertDialog alertWait(String text) {
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(ActivityChat.this)
                .setCancelable(false)
                .setView(R.layout.dialog_wait) // 嵌入视图
                .show();

        TextView text_tips = alertDialog.findViewById(R.id.text_tips);
        text_tips.setText(text);

        return alertDialog;
    }

    private void sendMessage(String text, String path) {
        java.util.function.Consumer<String> onDone = result -> {
            runOnUiThread(() -> {
                b.textLayoutText.getEditText().setText("");
            });
        };

        if (!text.isEmpty()) {
            KcAPI.sendChatMessageText(
                    targetContact.id,
                    text,
                    application,
                    onError,
                    onDone
            );
        } else if (!path.isEmpty()) {
            File file = new File(path);

            KcAPI.sendChatMessageFile(
                    targetContact.id,
                    file.getAbsolutePath(),
                    file.getName(),
                    Files.getFileExtension(file.getName()),
                    file.length(),
                    application,
                    onError,
                    onDone
            );
        }
    }

    private void copyFileToPublicDownload() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), "没有存储权限！", Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog waitDialog = alertWait("正在复制");
        MyApplication.fileCopyToDownload(
                this,
                copyToPublicDownloadFilePath,
                application,
                error -> {
                    runOnUiThread(() -> {
                        waitDialog.cancel();
                        Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                    });
                },
                done -> {
                    runOnUiThread(() -> {
                        waitDialog.cancel();
                        Toast.makeText(getApplicationContext(), "已经复制", Toast.LENGTH_SHORT).show();
                    });
                }
        );
    }

    private void ready() {
        // 绑定功能
        b.buttonSend.setOnClickListener(v -> {
            String text = b.textLayoutText.getEditText().getText().toString();
            if (text.isEmpty()) {
                return;
            }

            sendMessage(text, null);
        });
        b.buttonFile.setOnClickListener(v -> {
            Intent fileIntent = new Intent(getApplicationContext(), ActivityFileChoose.class);
            startActivityForResult(fileIntent, REQUEST_CODE_FILE);
        });

        // 监听广播
        IntentFilter broadcastIntentFilter = new IntentFilter();
        broadcastIntentFilter.addAction("push");
        broadcastManager.registerReceiver(
                localBroadcastReceiver,
                broadcastIntentFilter
        );

        // 处理分享
        if (getIntent().getParcelableExtra("uri") != null) {
            Uri uri = getIntent().getParcelableExtra("uri");
            Log.i(T, "分享文件:" + uri);

            AlertDialog waitDialog = alertWait("正在准备");
            application.uriToDir(
                    uri,
                    getExternalCacheDir(),
                    file -> {
                        waitDialog.cancel();
                        runOnUiThread(() -> {
                            sendMessage("", file.getAbsolutePath());
                        });
                    },
                    error -> {
                        runOnUiThread(() -> {
                            waitDialog.cancel();
                            Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                        });
                    }
            );
        }
    }

    private void init() {
        // 加载数据
        KcAPI.findChatMessage(
                targetContact.id,
                application,
                onError,
                result -> {
                    runOnUiThread(() -> {
                        adapter.set(myID, myPhotoBitmap, targetPhotoBitmap, result);
                        layoutManager.scrollToPosition(
                                adapter.getItemCount() - 1
                        );

                        ready();
                    });
                }
        );
    }

}
