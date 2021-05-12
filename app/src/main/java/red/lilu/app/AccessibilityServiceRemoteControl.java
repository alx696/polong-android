package red.lilu.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class AccessibilityServiceRemoteControl extends AccessibilityService {

    private static final String T = "调试";
    private Timer timer;

    /**
     * 点击坐标
     *
     * @param x 开发者选项中启用指针位置后的x
     * @param y 开发者选项中启用指针位置后的y
     */
    private void click(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);

        dispatchGesture(
                new GestureDescription.Builder()
                        .addStroke(
                                new GestureDescription.StrokeDescription(
                                        path,
                                        0L,
                                        200L
                                )
                        )
                        .build(),
                null,
                null
        );
    }

    private void delayClick(String name, float x, float y, long delay) {
        Log.d(T, "准备延时点击:" + name);
        timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        Log.d(T, "进行延时点击:" + name);
                        click(x, y);
                    }
                },
                delay
        );
    }

    @Override
    public void onCreate() {
        super.onCreate();

        timer = new Timer();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            String eventName = AccessibilityEvent.eventTypeToString(event.getEventType());
            String packageName = "";
            String className = "";
            // 一加3T在TYPE_WINDOW_STATE_CHANGED时没有
            if (event.getPackageName() != null) {
                packageName = event.getPackageName().toString();
            }
            // 一加3T在TYPE_WINDOW_STATE_CHANGED时没有
            if (event.getClassName() != null) {
                className = event.getClassName().toString();
            }
            Log.d(T, String.format("无障碍服务onAccessibilityEvent：%s, %s, %s", eventName, packageName, className));

            // 获取通知内容（小米4C不支持）
            if (packageName.equals("red.lilu.app.polong")
                    && event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                Notification notification = (Notification) event.getParcelableData();
                if (notification != null) {
                    String title = notification.extras.getString(Notification.EXTRA_TITLE);
                    String text = notification.extras.getString(Notification.EXTRA_TEXT);
                    Log.d(T, String.format("通知内容：%s, %s", title, text));

//                    if (text.equals("daka")) {
//                        Log.i(T, "执行任务：daka");
//                        dingding();
//                    }
                }
            }

            if (packageName.equals("red.lilu.app.polong")
                    && className.equals("red.lilu.app.ActivityRemoteControlTaskDo")) {
                Log.w(T, "执行远程任务");
            }
        } catch (Exception e) {
            Log.w(T, e);
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(T, "onInterrupt");
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

    private void dingding() {
        // 应用图标 140,1660
        // 工作台 525,1810
        // 考勤打卡 126,960
        // 打卡按钮 535,1065
        ArrayList<Task> tasks = Lists.newArrayList(
                new Task("点击应用图标", 140F, 1660F, 3000),
                new Task("点击工作台", 525F, 1810F, 10000),
                new Task("点击考勤打卡", 126F, 960F, 9000),
                new Task("点击打卡按钮", 535F, 1065F, 9000)
        );

        performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);

        long timeline = 0;
        for (Task task : tasks) {
            timeline += task.wait;
            delayClick(task.name, task.x, task.y, timeline);
        }

        timeline += 3000;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            }
        }, timeline);

        timeline += 3000;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            }
        }, timeline);
    }

}
