package red.lilu.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.HashSet;

import red.lilu.app.databinding.ActivityMainBinding;

public class ActivityMain extends AppCompatActivity implements RecyclerViewAdapterContact.Callback {

    private static final String T = "调试";
    private static final int REQUEST_CODE_IGNORE_BATTERY_OPTIMIZATIONS = 1;
    private static final int REQUEST_CODE_ADD = 10;
    private static final int REQUEST_CODE_OPTION = 11;
    private ActivityMainBinding b;
    private static MyApplication application;
    private Intent serviceIntent;
    private LocalBroadcastManager broadcastManager;
    private LocalBroadcastReceiver localBroadcastReceiver;
    private RecyclerViewAdapterContact adapter;
    private String kcID = "";
    private KcAPI.Contact chatContact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);

        // 准备公共
        application = (MyApplication) getApplication();

        //准备界面
        b.recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        b.recyclerView.setLayoutManager(layoutManager);
        DividerItemDecoration itemDecoration = new DividerItemDecoration(
                b.recyclerView.getContext(),
                layoutManager.getOrientation()
        );
        b.recyclerView.addItemDecoration(itemDecoration);
        adapter = new RecyclerViewAdapterContact(application, this);
        b.recyclerView.setAdapter(adapter);

        //本地广播
        broadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
        localBroadcastReceiver = new LocalBroadcastReceiver();
        IntentFilter broadcastIntentFilter = new IntentFilter();
        broadcastIntentFilter.addAction("kcID");
        broadcastIntentFilter.addAction("push");
        broadcastManager.registerReceiver(
                localBroadcastReceiver,
                broadcastIntentFilter
        );

        checkBackground();

        if (getIntent().getStringExtra("remote_control_close") != null) {
            Log.i(T, "发送停止远程控制广播");
            Intent broadcastIntent = new Intent("remote_control_close");
            broadcastManager.sendBroadcast(broadcastIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        //广播界面状态
        Intent broadcastIntent = new Intent("ui");
        broadcastIntent.putExtra("main", true);
        broadcastManager.sendBroadcast(broadcastIntent);

        //退出会话界面后
        if (chatContact != null) {
            adapter.resetMessageCount(chatContact.id); // 重置未读消息数量
            chatContact = null; // 重置会话中的联系人
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        //广播界面状态
        Intent broadcastIntent = new Intent("ui");
        broadcastIntent.putExtra("main", false);
        broadcastManager.sendBroadcast(broadcastIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        broadcastManager.unregisterReceiver(localBroadcastReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.add) {
            Intent intent = new Intent(getApplicationContext(), ActivityAdd.class);
            startActivityForResult(intent, REQUEST_CODE_ADD);
        } else if (id == R.id.option) {
            Intent intent = new Intent(getApplicationContext(), ActivityOption.class);
            intent.putExtra("id", kcID);
            startActivityForResult(intent, REQUEST_CODE_OPTION);
        } else if (id == R.id.info) {
            startActivity(new Intent(getApplicationContext(), ActivityAbout.class));
        } else if (id == R.id.exit) {
            if (serviceIntent != null) {
                stopService(serviceIntent);
            }
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 检查允许后台运行结果
        if (requestCode == REQUEST_CODE_IGNORE_BATTERY_OPTIMIZATIONS) {
            if (resultCode == RESULT_OK) {
                init();
            } else {
                new MaterialAlertDialogBuilder(ActivityMain.this)
                        .setTitle("信任危机")
                        .setMessage("你选择了继续节省电量，切换其它应用或锁屏之后，手机会将我强行关闭，导致收不到消息！")
                        .setPositiveButton("已读", (dialog, which) -> {
                            init();
                        })
                        .show();
            }
        }

        if (requestCode == REQUEST_CODE_ADD && resultCode == RESULT_OK) {
            reloadContactList();
        }

        if (requestCode == REQUEST_CODE_OPTION && resultCode == RESULT_OK) {
            Log.d(T, "设置已经变更");
        }
    }

    @Override
    public void onRecyclerViewAdapterPeerInfoClick(KcAPI.Contact contact) {
        chatContact = contact; // 缓存会话中的联系人

        // 打开会话界面
        Intent intent = new Intent(getApplicationContext(), ActivityChat.class);
        intent.putExtra("myID", kcID);
        intent.putExtra("targetID", contact.id);
        startActivity(intent);
    }

    class LocalBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
//            Log.d(T, "主界面收到广播:" + intent.getAction());
            switch (intent.getAction()) {
                case "kcID":
                    kcID = intent.getStringExtra("data");

                    // 检查我的信息是否设置
                    KcAPI.getOption(
                            application,
                            error -> {
                                runOnUiThread(() -> {
                                    Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                                    finish();
                                });
                            },
                            myInfo -> {
                                if (myInfo.name.isEmpty()) {
                                    runOnUiThread(() -> startActivity(new Intent(getApplicationContext(), ActivityInfoForm.class)));
                                } else {
                                    nodeReady();
                                }
                            }
                    );

                    break;
                case "push":
                    String type = intent.getStringExtra("type");
                    if (type.equals("ContactDelete") || type.equals("ContactUpdate")) {
                        reloadContactList();
                    }
                    if (type.equals("PeerConnectState")) {
                        adapter.updateConnect(
                                intent.getStringExtra("id"),
                                intent.getBooleanExtra("isConnect", false)
                        );
                    }
                    if (type.equals("ChatMessage")) {
                        adapter.increaseMessageCount(
                                intent.getStringExtra("peerID")
                        );
                    }

                    break;
            }
        }
    }

    /**
     * 重新加载联系人列表
     */
    private void reloadContactList() {
        java.util.function.Consumer<String> onError = error -> {
            runOnUiThread(() -> {
                Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                finish();
            });
        };

        KcAPI.getConnectedPeerIDs(
                application,
                onError,
                connectedPeerIDs -> {
                    HashSet<String> connectedSet = Sets.newHashSet(connectedPeerIDs);
                    //
                    KcAPI.getChatMessageUnReadCount(
                            application,
                            onError,
                            unReadCountMap -> {
                                //
                                KcAPI.getContact(
                                        application,
                                        onError,
                                        result -> {
                                            ArrayList<KcAPI.Contact> contacts = Lists.newArrayList(result.values());

                                            runOnUiThread(() -> {
                                                adapter.set(
                                                        contacts,
                                                        connectedSet,
                                                        unReadCountMap
                                                );
                                            });
                                        }
                                );
                                //
                            }
                    );
                    //
                }
        );
    }

    /**
     * 节点就绪
     */
    private void nodeReady() {
        reloadContactList();

        // 处理文件分享
        if (getIntent().getParcelableExtra(Intent.EXTRA_STREAM) != null) {
            Uri fileUri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            Log.i(T, "收到文件分享:" + fileUri);

            Intent intent = new Intent(getApplicationContext(), ActivityShare.class);
            intent.putExtra("myID", kcID);
            intent.putExtra("uri", fileUri);
            startActivity(intent);
        }
    }

    private void init() {
        //启动服务
        serviceIntent = new Intent(getApplicationContext(), ServiceForeground.class);
        ContextCompat.startForegroundService(getApplicationContext(), serviceIntent);
    }

    private void checkBackground() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        if (powerManager == null) {
            init();
            return;
        }

        // 检查是否允许后台运行(停止电池优化)
        if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            Log.i(T, "需要后台运行权限!");

            // 申请后台运行
            Intent backgroundIntent = new Intent();
            backgroundIntent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            backgroundIntent.setData(Uri.parse("package:" + getApplicationContext().getPackageName()));
            startActivityForResult(backgroundIntent, REQUEST_CODE_IGNORE_BATTERY_OPTIMIZATIONS);
        } else {
            init();
        }
    }

}
