package red.lilu.app;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import kc.Kc;

public class ServiceForeground extends Service implements kc.FeedCallback {

    private static final String T = "调试";
    public static final String NOTIFICATION_CHANNEL_FOREGROUND_ID = "前台服务";
    public static final String NOTIFICATION_CHANNEL_MESSAGE_ID = "会话消息";
    public static final int NOTIFICATION_FOREGROUND_ID = 1;
    public static final int NOTIFICATION_MESSAGE_ID = 2;
    public static final int NOTIFICATION_MESSAGE_ID_OLD = 3;
    public static final int NOTIFICATION_REMOTE_CONTROL = 4;
    public static final int MESSENGER_WHAT_REMOTE_CONTROL_ASK = 1;
    private MyApplication application;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;
    private LocalBroadcastManager localBroadcastManager;
    private LocalBroadcastReceiver broadcastReceiver;
    private Timer timer;
    private boolean stop = false;
    private String kcID = "";
    private HashMap<String, KcAPI.Contact> contactMap = new HashMap<>();
    private final LinkedHashMap<Person, Notification.MessagingStyle.Message> notificationMessageMap = new LinkedHashMap<>();
    private boolean mainUiShow = true; //主界面是否显示
    private String chatTargetID = ""; //会话界面对方ID
    private KeyguardManager keyguardManager;
    private RTCScreenEncoder rtcScreenEncoder;
    private KcAPI.Contact remoteControlContact; // 远程控制联系人

    public ServiceForeground() {
    }

    /**
     * Handler of incoming messages from clients.
     */
    private class IncomingHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSENGER_WHAT_REMOTE_CONTROL_ASK:
                    Log.i(T, "收到远程控制询问结果");

                    if (msg.obj == null) {
                        refuseRemoteControl();
                    } else {
                        acceptRemoteControl(
                                (MediaProjection) msg.obj
                        );
                    }

                    break;
                default:
                    super.handleMessage(msg);
            }
        }

    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    Messenger messenger;

    @Override
    public IBinder onBind(Intent intent) {
        messenger = new Messenger(new IncomingHandler());
        return messenger.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(T, "服务创建");

        application = (MyApplication) getApplication();

        //显示前台运行通知
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel foregroundNotificationChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_FOREGROUND_ID,
                    NOTIFICATION_CHANNEL_FOREGROUND_ID,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            foregroundNotificationChannel.setDescription("用于说明后台服务运行状态");
            foregroundNotificationChannel.enableLights(true); //要点亮LED指示灯必须在此设置!
            foregroundNotificationChannel.setLightColor(
                    Color.YELLOW //LED颜色,支持MAGENTA(紫色),YELLOW(一加为柠檬色)
            );
            notificationManager.createNotificationChannel(foregroundNotificationChannel);

            NotificationChannel messageNotificationChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_MESSAGE_ID,
                    NOTIFICATION_CHANNEL_MESSAGE_ID,
                    NotificationManager.IMPORTANCE_HIGH
            );
            messageNotificationChannel.setDescription("用于提醒未读消息");
            messageNotificationChannel.enableLights(true); //要点亮LED指示灯必须在此设置!
            messageNotificationChannel.setLightColor(Color.GRAY);
            notificationManager.createNotificationChannel(messageNotificationChannel);
        }
        startForeground(
                NOTIFICATION_FOREGROUND_ID,
                getForegroundNotification("平等互联,自由通信")
        );

        //唤醒(对一加手机有效, 否则后台状态就冻结了.)
        // https://developer.android.com/training/scheduling/wakelock#cpu
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, getPackageName() + ":wakelock");
            wakeLock.acquire();
        }
        keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        localBroadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
        broadcastReceiver = new LocalBroadcastReceiver();
        IntentFilter broadcastIntentFilter = new IntentFilter();
        broadcastIntentFilter.addAction("ui");
        broadcastIntentFilter.addAction("remote_control_close");

        //启动kc(持续运行)
        application.getExecutorService().execute(() -> {
            try {
                // 启动
                Kc.start(getFilesDir().getAbsolutePath(), KcAPI.getFileDirectory(application).getAbsolutePath(), 0, ServiceForeground.this);
            } catch (Exception e) {
                Log.w(T, e);
            }
        });
        //等待kc启动完毕, 发出节点就绪广播
        application.getExecutorService().execute(() -> {
            try {
                // 等待启动完成
                while (!stop) {
                    kcID = Kc.getID();
                    if (!kcID.isEmpty()) {
                        break;
                    }
                    Thread.sleep(1000);
                }
                Log.i(T, "节点启动完毕:" + kcID);

                // 缓存联系人
                KcAPI.getContact(
                        application,
                        error -> {
                            Log.w(T, error);
                        },
                        map -> {
                            contactMap = map;
                        }
                );

                localBroadcastManager.registerReceiver(
                        broadcastReceiver,
                        broadcastIntentFilter
                );
                sendReadyBroadcast();
                startTimer();
            } catch (Exception e) {
                Log.w(T, e);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(T, "服务启动命令");

        // 触发kc已经启动流程
        if (!kcID.isEmpty()) {
            sendReadyBroadcast();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(T, "服务销毁");

        stop = true;
        localBroadcastManager.unregisterReceiver(broadcastReceiver);
        timer.cancel();
        Kc.stop();
        notificationManager.cancelAll();

        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    @Override
    public void feedCallbackOnContactDelete(String id) {
        Log.i(T, "收到推送-删除联系人: " + id);

        Intent pushIntent = new Intent("push");
        pushIntent.putExtra("type", "ContactDelete");
        pushIntent.putExtra("id", id);
        localBroadcastManager.sendBroadcast(pushIntent);
    }

    @Override
    public void feedCallbackOnContactUpdate(String json) {
        Log.i(T, "收到推送-更新联系人");

        KcAPI.Contact contact = application.getGson().fromJson(
                json,
                new TypeToken<KcAPI.Contact>() {
                }.getType()
        );
        if (contact == null) {
            Log.w(T, "联系人JSON转对象失败" + json);
            return;
        }

        contactMap.put(contact.id, contact);

        Intent pushIntent = new Intent("push");
        pushIntent.putExtra("type", "ContactUpdate");
        pushIntent.putExtra("data", contact.id);
        localBroadcastManager.sendBroadcast(pushIntent);
    }

    @Override
    public void feedCallbackOnPeerConnectState(String id, boolean isConnect) {
        Log.i(T, "收到推送-节点连接状态变化: " + id);

        Intent pushIntent = new Intent("push");
        pushIntent.putExtra("type", "PeerConnectState");
        pushIntent.putExtra("id", id);
        pushIntent.putExtra("isConnect", isConnect);
        localBroadcastManager.sendBroadcast(pushIntent);
    }

    private static class Task {
        String name;
        float x, y;
        long wait;

        public Task(String name, float x, float y, long wait) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.wait = wait;
        }
    }

    @Override
    public void feedCallbackOnChatMessage(String peerID, String json) {
        Log.i(T, "收到推送-会话消息: " + json);

        Intent pushIntent = new Intent("push");
        pushIntent.putExtra("type", "ChatMessage");
        pushIntent.putExtra("peerID", peerID);
        pushIntent.putExtra("json", json);
        localBroadcastManager.sendBroadcast(pushIntent);

        KcAPI.ChatMessage chatMessage = application.getGson().fromJson(
                json,
                new TypeToken<KcAPI.ChatMessage>() {
                }.getType()
        );

        showChatMessageNotification(peerID, chatMessage);

        handleRemoteTask(chatMessage);
    }

    @Override
    public void feedCallbackOnChatMessageState(String peerID, long messageID, String state) {
        Log.i(T, "收到推送-会话消息状态: " + messageID + "," + state);

        Intent pushIntent = new Intent("push");
        pushIntent.putExtra("type", "ChatMessageState");
        pushIntent.putExtra("peerID", peerID);
        pushIntent.putExtra("messageID", messageID);
        pushIntent.putExtra("state", state);
        localBroadcastManager.sendBroadcast(pushIntent);
    }

    @Override
    public void feedCallbackOnRemoteControlRequest(String id) {
        Log.i(T, "收到远程控制请求:" + id);

        remoteControlContact = contactMap.get(id);
        if (remoteControlContact == null) {
            Log.w(T, "没有找到联系人，拒绝远程控制请求");
            refuseRemoteControl();
            return;
        }

        // 显示提示信息并询问用户
        application.screenOn();
        Intent intent = new Intent(getApplicationContext(), ActivityRemoteControlAsk.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("id", id);
        intent.putExtra("name", remoteControlContact.name);
        startActivity(intent);
    }

    @Override
    public void feedCallbackOnRemoteControlResponse(String s) {
        Log.d(T, "远程控制收到视频信息:" + s);

        Intent pushIntent = new Intent("push");
        pushIntent.putExtra("type", "RemoteControlReceiveVideoInfo");
        pushIntent.putExtra("json", s);
        localBroadcastManager.sendBroadcast(pushIntent);
    }

    @Override
    public void feedCallbackOnRemoteControlVideo(long presentationTimeUs, byte[] bytes) {
//        Log.d(T, "远程控制收到视频数据:" + presentationTimeUs + "," + bytes.length);

        Intent pushIntent = new Intent("push");
        pushIntent.putExtra("type", "RemoteControlReceiveVideoData");
        pushIntent.putExtra("presentationTimeUs", presentationTimeUs);
        pushIntent.putExtra("data", bytes);
        localBroadcastManager.sendBroadcast(pushIntent);
    }

    @Override
    public void feedCallbackOnRemoteControlClose() {
        Log.d(T, "远程控制收到关闭");

        Intent pushIntent = new Intent("remote_control_close");
        localBroadcastManager.sendBroadcast(pushIntent);
    }

    class LocalBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
//            Log.d(T, "服务收到广播:" + intent.getAction());
            switch (intent.getAction()) {
                case "ui":
                    if (intent.hasExtra("main")) {
                        mainUiShow = intent.getBooleanExtra("main", false);
                    } else if (intent.hasExtra("chatTargetID")) {
                        chatTargetID = intent.getStringExtra("chatTargetID");
                    }
                    // 主界面显示时移除所有消息通知
                    if (mainUiShow) {
                        notificationMessageMap.clear();
                        notificationManager.cancel(NOTIFICATION_MESSAGE_ID);
                        notificationManager.cancel(NOTIFICATION_MESSAGE_ID_OLD);
                    }

                    break;

                case "remote_control_close":
                    Toast.makeText(getApplicationContext(), "远程控制关闭", Toast.LENGTH_LONG).show();
                    stopShareScreen();

                    break;
            }
        }
    }

    private Notification getForegroundNotification(String text) {
        Intent activityIntent = new Intent(getApplicationContext(), ActivityMain.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                getApplicationContext(), 0, activityIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT
        );

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_FOREGROUND_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("去中心化对等网络")
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    /**
     * 发出节点就绪广播
     */
    private void sendReadyBroadcast() {
        Intent idIntent = new Intent("kcID");
        idIntent.putExtra("data", kcID);
        localBroadcastManager.sendBroadcast(idIntent);
    }

    /**
     * 启动定时器
     */
    private void startTimer() {
        TimerTask refreshStateTimerTask = new TimerTask() {
            @Override
            public void run() {
                KcAPI.State state = application.getGson().fromJson(
                        new String(
                                Kc.getState()
                        ),
                        new TypeToken<KcAPI.State>() {
                        }.getType()
                );
                notificationManager.notify(
                        NOTIFICATION_FOREGROUND_ID,
                        getForegroundNotification(
                                String.format(Locale.CHINA, "节点数量: %d 连接数量: %d", state.peerCount, state.connCount)
                        )
                );
            }
        };
        timer = new Timer();
        timer.schedule(refreshStateTimerTask, 6000, 6000);
    }

    /**
     * 显示会话消息通知
     */
    private void showChatMessageNotification(String peerID, KcAPI.ChatMessage m) {
        // 自己发送的, 正在会话的, 主界面开启等情况不通知
        if (peerID.equals(kcID) || chatTargetID.equals(peerID) || mainUiShow) {
            return;
        }

        String mContent = m.text;
        if (m.file_size > 0) {
            mContent = String.format("[文件] %s", m.file_name);
        }
        KcAPI.Contact contact = contactMap.get(m.fromPeerID);
        String contactName = contact.nameRemark.isEmpty() ? contact.name : contact.nameRemark;

        Intent activityIntent = new Intent(getApplicationContext(), ActivityMain.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                getApplicationContext(), 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Person person = new Person.Builder()
                    .setName(contactName)
                    .build();
            notificationMessageMap.put(
                    person,
                    new Notification.MessagingStyle.Message(mContent, System.currentTimeMillis(), person)
            );
            Notification.MessagingStyle messagingStyle = new Notification.MessagingStyle(person);
            for (Person p : notificationMessageMap.keySet()) {
                messagingStyle.addMessage(
                        notificationMessageMap.get(p)
                );
            }
            Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_MESSAGE_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("新的消息")
                    .setStyle(messagingStyle)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setAutoCancel(true) //点击清除
                    .setContentIntent(pendingIntent)
                    .build();
            notificationManager.notify(NOTIFICATION_MESSAGE_ID, notification);
        } else {
            Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_MESSAGE_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("最新消息")
                    .setContentText(String.format("%s : %s", contactName, mContent))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setLights(Color.GRAY, 100, 100)
                    .setAutoCancel(true) //点击清除
                    .setContentIntent(pendingIntent)
                    .build();
            notificationManager.notify(NOTIFICATION_MESSAGE_ID_OLD, notification);
        }
    }

    /**
     * 显示远程控制通知
     */
    private void showRemoteControlNotification() {
        Intent activityIntent = new Intent(getApplicationContext(), ActivityMain.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activityIntent.putExtra("remote_control_close", "");
        PendingIntent pendingIntent = PendingIntent.getActivity(
                getApplicationContext(), 1, activityIntent, PendingIntent.FLAG_ONE_SHOT
        );

        notificationManager.notify(
                NOTIFICATION_REMOTE_CONTROL,
                new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_MESSAGE_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("正被远程控制")
                        .setContentText("点我停止远程控制")
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .build()
        );
    }

    private void handleRemoteTask(KcAPI.ChatMessage chatMessage) {
        if (!chatMessage.text.equals("daka") || chatMessage.fromPeerID.equals(kcID)) {
            return;
        }

        // 检查是否加密锁定
        if (keyguardManager != null && keyguardManager.isKeyguardLocked()) {
            Log.w(T, "屏幕解锁需要密码，不能执行远程控制！");

            // 自动回复问题
            KcAPI.sendChatMessageText(
                    chatMessage.fromPeerID,
                    "注意：屏幕解锁需要密码，不能执行远程控制！",
                    application,
                    text -> {
                        Log.w(T, "自动回复失败：" + text);
                    },
                    text -> {
                        Log.d(T, "自动回复成功: 无法执行远程任务");
                    }
            );

//                Intent intent = new Intent(getApplicationContext(), ActivityRemoteControlTaskDo.class);
//                intent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
//                intent.putExtra("json", "[]");
//                startActivity(intent);
            return;
        }

        // 亮屏
        application.screenOn();

        ArrayList<Task> tasks = Lists.newArrayList(
                new Task("点击应用图标", 160F, 1770F, 3000),
                new Task("[back]", 0F, 0F, 16000)
        );
        String taskJson = application.getGson().toJson(tasks);

        Log.d(T, "显示触发远程任务窗口");
        Intent intent = new Intent(getApplicationContext(), ActivityRemoteControlTaskDo.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("json", taskJson);
        startActivity(intent);
        //-
    }

    private void refuseRemoteControl() {
        KcAPI.remoteControlClose(
                remoteControlContact.id,
                application,
                error -> {
                    Log.w(T, error);
                },
                done -> {
                    //
                }
        );
    }

    private void acceptRemoteControl(MediaProjection mediaProjection) {
        DisplayMetrics metrics = getApplicationContext().getResources().getDisplayMetrics();
        KcAPI.RemoteControlInfo info = new KcAPI.RemoteControlInfo(metrics.widthPixels, metrics.heightPixels);
        KcAPI.remoteControlSendResponse(
                remoteControlContact.id,
                info,
                application,
                error -> {
                    Log.w(T, error);
                },
                done -> {
                    startShareScreen(mediaProjection);
                }
        );
    }

    private void startShareScreen(MediaProjection mediaProjection) {
        rtcScreenEncoder = new RTCScreenEncoder(
                application,
                mediaProjection,
                error -> {
                    Log.w(T, error);
                },
                data -> {
                    if (remoteControlContact == null) {
                        return;
                    }

                    KcAPI.remoteControlSendVideo(
                            remoteControlContact.id,
                            data.presentationTimeUs,
                            data.bytes,
                            application,
                            error -> {
                                Log.w(T, error);
                            },
                            done -> {
                                //
                            }
                    );
                }
        );
        rtcScreenEncoder.start();

        showRemoteControlNotification();
    }

    private void stopShareScreen() {
        notificationManager.cancel(NOTIFICATION_REMOTE_CONTROL);

        if (rtcScreenEncoder != null) {
            rtcScreenEncoder.stop();
        }

        // 发出停止远程控制命令
        if (remoteControlContact != null) {
            KcAPI.remoteControlClose(
                    remoteControlContact.id,
                    application,
                    error -> {
                        Log.w(T, error);
                    },
                    done -> {
                        remoteControlContact = null;
                    }
            );
        }
    }

}
