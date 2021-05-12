package red.lilu.app;

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
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Timer;
import java.util.TimerTask;

import kc.Kc;

public class FService extends Service implements kc.FeedCallback {

    private static final String T = "调试";
    public static final String NOTIFICATION_CHANNEL_FOREGROUND_ID = "前台服务";
    public static final String NOTIFICATION_CHANNEL_MESSAGE_ID = "会话消息";
    public static final int NOTIFICATION_FOREGROUND_ID = 1;
    public static final int NOTIFICATION_MESSAGE_ID = 2;
    public static final int NOTIFICATION_MESSAGE_ID_OLD = 3;
    private MyApplication application;
    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;
    private PendingIntent mainActivityPendingIntent;
    private LocalBroadcastManager broadcastManager;
    private LocalBroadcastReceiver localBroadcastReceiver;
    private Timer timer;
    private boolean stop = false;
    private String kcID = "";
    private HashMap<String, KcAPI.Contact> contactMap = new HashMap<>();
    private final LinkedHashMap<Person, Notification.MessagingStyle.Message> notificationMessageMap = new LinkedHashMap<>();
    private boolean mainUiShow = true; //主界面是否显示
    private String chatTargetID = ""; //会话界面对方ID
    private WindowManager windowManager; // 用于显示悬浮窗口
    private ViewGroup floatWindowView; // 用于显示悬浮窗口

    public FService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(T, "服务创建");

        application = (MyApplication) getApplication();

        //显示前台运行通知
        Intent mainActivityIntent = new Intent(getApplicationContext(), ActivityMain.class);
        mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mainActivityPendingIntent = PendingIntent.getActivity(
                getApplicationContext(), 0, mainActivityIntent, PendingIntent.FLAG_CANCEL_CURRENT
        );
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
        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_FOREGROUND_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("去中心化对等网络")
                .setContentText("平等互联,自由通信")
                .setContentIntent(mainActivityPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        startForeground(NOTIFICATION_FOREGROUND_ID, notification);

        //唤醒(对一加手机有效, 否则后台状态就冻结了.)
        // https://developer.android.com/training/scheduling/wakelock#cpu
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "iim:wakelock");
            wakeLock.acquire();
        }

        broadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
        localBroadcastReceiver = new LocalBroadcastReceiver();
        IntentFilter broadcastIntentFilter = new IntentFilter();
        broadcastIntentFilter.addAction("ui");

        //启动kc(持续运行)
        application.getExecutorService().execute(() -> {
            try {
                // 启动
                Kc.start(getFilesDir().getAbsolutePath(), KcAPI.getFileDirectory(application).getAbsolutePath(), 0, FService.this);
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

                broadcastManager.registerReceiver(
                        localBroadcastReceiver,
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
        broadcastManager.unregisterReceiver(localBroadcastReceiver);
        timer.cancel();
        Kc.stop();
        notificationManager.cancelAll();

        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }

        if (floatWindowView != null) {
            windowManager.removeView(floatWindowView);
        }
    }

    @Override
    public void feedCallbackOnContactDelete(String id) {
        Log.i(T, "收到推送-删除联系人: " + id);

        Intent pushIntent = new Intent("push");
        pushIntent.putExtra("type", "ContactDelete");
        pushIntent.putExtra("data", id);
        broadcastManager.sendBroadcast(pushIntent);
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
        broadcastManager.sendBroadcast(pushIntent);
    }

    @Override
    public void feedCallbackOnPeerConnectState(String id, boolean isConnect) {
        Log.i(T, "收到推送-节点连接状态变化: " + id);

        Intent pushIntent = new Intent("push");
        pushIntent.putExtra("type", "PeerConnectState");
        pushIntent.putExtra("id", id);
        pushIntent.putExtra("isConnect", isConnect);
        broadcastManager.sendBroadcast(pushIntent);
    }

    @Override
    public void feedCallbackOnChatMessage(String peerID, String json) {
        Log.i(T, "收到推送-会话消息: " + json);

        Intent pushIntent = new Intent("push");
        pushIntent.putExtra("type", "ChatMessage");
        pushIntent.putExtra("peerID", peerID);
        pushIntent.putExtra("json", json);
        broadcastManager.sendBroadcast(pushIntent);

        showChatMessageNotification(peerID, json);

        Intent intent = new Intent(getApplicationContext(), ActivityRemoteControlTaskDo.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
//        PendingIntent pendingIntent = PendingIntent.getActivity(
//                getApplicationContext(), 0, intent, PendingIntent.FLAG_CANCEL_CURRENT
//        );
        startActivity(intent);
    }

    @Override
    public void feedCallbackOnChatMessageState(String peerID, long messageID, String state) {
        Log.i(T, "收到推送-会话消息状态: " + messageID + "," + state);

        Intent pushIntent = new Intent("push");
        pushIntent.putExtra("type", "ChatMessageState");
        pushIntent.putExtra("peerID", peerID);
        pushIntent.putExtra("messageID", messageID);
        pushIntent.putExtra("state", state);
        broadcastManager.sendBroadcast(pushIntent);
    }

    class LocalBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(T, "服务收到广播:" + intent.getAction());
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
            }
        }
    }

    /**
     * 发出节点就绪广播
     */
    private void sendReadyBroadcast() {
        Intent idIntent = new Intent("kcID");
        idIntent.putExtra("data", kcID);
        broadcastManager.sendBroadcast(idIntent);
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
                updateForegroundNotification(state.peerCount, state.connCount);
            }
        };
        timer = new Timer();
        timer.schedule(refreshStateTimerTask, 6000, 6000);
    }

    /**
     * 更新前台通知
     */
    private void updateForegroundNotification(int peerCount, int connCount) {
        notificationManager.notify(
                NOTIFICATION_FOREGROUND_ID,
                new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_FOREGROUND_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("去中心化对等网络")
                        .setContentText(String.format("节点数量: %d 连接数量: %d", peerCount, connCount))
                        .setContentIntent(mainActivityPendingIntent)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .build()
        );
    }

    /**
     * 显示会话消息通知
     *
     * @param json 会话消息
     */
    private void showChatMessageNotification(String peerID, String json) {
        // 自己发送的, 正在会话的, 主界面开启等情况不通知
        if (peerID.equals(kcID) || chatTargetID.equals(peerID) || mainUiShow) {
            return;
        }

        KcAPI.ChatMessage m = application.getGson().fromJson(
                json,
                new TypeToken<KcAPI.ChatMessage>() {
                }.getType()
        );
        String mContent = m.text;
        if (m.fileSize > 0) {
            mContent = String.format("[文件] %s.%s", m.fileNameWithoutExtension, m.fileExtension);
        }
        KcAPI.Contact contact = contactMap.get(m.fromPeerID);
        String contactName = contact.nameRemark.isEmpty() ? contact.name : contact.nameRemark;

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
                    .setContentIntent(mainActivityPendingIntent)
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
                    .setContentIntent(mainActivityPendingIntent)
                    .build();
            notificationManager.notify(NOTIFICATION_MESSAGE_ID_OLD, notification);
        }
    }

    /**
     * 显示悬浮窗口
     * 参考 https://www.geeksforgeeks.org/how-to-make-a-floating-window-application-in-android/
     */
    private void showFloatWindow() {
        if (!Settings.canDrawOverlays(getApplicationContext())) {
            Log.w(T, "没有显示悬浮窗口权限");
            return;
        }

        // The screen height and width are calculated, cause
        // the height and width of the floating window is set depending on this
        DisplayMetrics metrics = getApplicationContext().getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        Log.w(T, String.format("宽高: %d, %d", width, height));

        // To obtain a WindowManager of a different Display,
        // we need a Context for that display, so WINDOW_SERVICE is used
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // A LayoutInflater instance is created to retrieve the
        // LayoutInflater for the floating_layout xml
        LayoutInflater inflater = (LayoutInflater) getBaseContext().getSystemService(LAYOUT_INFLATER_SERVICE);

        // inflate a new view hierarchy from the floating_layout xml
        floatWindowView = (ViewGroup) inflater.inflate(R.layout.float_window_remote_control_task_do, null);

        int layoutType;
        // WindowManager.LayoutParams takes a lot of parameters to set the
        // the parameters of the layout. One of them is Layout_type.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // If API Level is more than 26, we need TYPE_APPLICATION_OVERLAY
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            // If API Level is lesser than 26, then we can
            // use TYPE_SYSTEM_ERROR,
            // TYPE_SYSTEM_OVERLAY, TYPE_PHONE, TYPE_PRIORITY_PHONE.
            // But these are all
            // deprecated in API 26 and later. Here TYPE_TOAST works best.
            // 小米4c不支持TYPE_TOAST
            layoutType = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        }

        // Now the Parameter of the floating-window layout is set.
        // 1) The Width of the window will be 55% of the phone width.
        // 2) The Height of the window will be 58% of the phone height.
        // 3) Layout_Type is already set.
        // 4) Next Parameter is Window_Flag. Here FLAG_NOT_FOCUSABLE is used. But
        // problem with this flag is key inputs can't be given to the EditText.
        // This problem is solved later.
        // 5) Next parameter is Layout_Format. System chooses a format that supports
        // translucency by PixelFormat.TRANSLUCENT
        WindowManager.LayoutParams floatWindowLayoutParam = new WindowManager.LayoutParams(
                (int) (width * (0.6f)),
                WindowManager.LayoutParams.WRAP_CONTENT, // (int) (height * (0.58f))
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        // The Gravity of the Floating Window is set.
        // The Window will appear in the center of the screen
        floatWindowLayoutParam.gravity = Gravity.CENTER;

        // X and Y value of the window is set
        floatWindowLayoutParam.x = 0;
        floatWindowLayoutParam.y = 0;

        // The ViewGroup that inflates the floating_layout.xml is
        // added to the WindowManager with all the parameters
        windowManager.addView(floatWindowView, floatWindowLayoutParam);
    }

}
