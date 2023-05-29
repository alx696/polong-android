package red.lilu.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.content.Intent;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ServiceAccessibilityRemoteControl extends AccessibilityService {

    private static final String T = "调试";
    private Timer timer;
    private int count;

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
            // 一加3T只有对应应用处于可见状态才能获取
            if (event.getPackageName() != null) {
                packageName = event.getPackageName().toString();
            }
            // 一加3T只有对应应用处于可见状态才能获取
            if (event.getClassName() != null) {
                className = event.getClassName().toString();
            }
//            Log.d(T, String.format("无障碍服务onAccessibilityEvent：%s, %s, %s", eventName, packageName, className));

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

            // 破笼远程任务
            if (packageName.equals("red.lilu.app.polong")
                    && className.equals("red.lilu.app.ActivityRemoteControlTaskDo")) {
                Log.d(T, "执行破笼远程任务");

                // 提取任务内容然后执行
                AccessibilityNodeInfo rootNodeInfo = getRootInActiveWindow();
                if (rootNodeInfo != null) {
                    List<AccessibilityNodeInfo> nodeInfoList = rootNodeInfo.findAccessibilityNodeInfosByViewId("red.lilu.app.polong:id/text_task");
//                    Log.d(T, "找到元素数量:" + nodeInfoList.size());

                    if (nodeInfoList.size() > 0) {
                        AccessibilityNodeInfo taskNodeInfo = nodeInfoList.get(0);
                        String json = taskNodeInfo.getText().toString();
                        Log.d(T, "已经读取远程任务内容");
                        performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                        doTask(json);
                    } else {
                        Log.w(T, "没有找到远程任务内容");
                    }
                }
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

    /*private void dingding() {
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
    }*/

    private void doTask(String json) {
        Log.i(T, "执行任务:" + json);

        // 解析任务
        ArrayList<Task> tasks = new Gson().fromJson(
                json,
                new TypeToken<ArrayList<Task>>() {
                }.getType());

        // 显示浮动窗口进行提示
        Intent intentServiceSystemAlertWindow = new Intent(getApplicationContext(), ServiceSystemAlertWindow.class);
        intentServiceSystemAlertWindow.putExtra("title", "正在执行远程任务");
        intentServiceSystemAlertWindow.putExtra("content", "测试");
        startService(intentServiceSystemAlertWindow);

        // 进到首页
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);

        long timeline = 0;
        count = 0;
        for (Task task : tasks) {
            timeline += task.wait;

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Log.d(T, "延时点击:" + task.name);

                    if (task.name.equals("[back]")) {
                        performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                    } else {
                        click(task.x, task.y);
                    }

                    count++;
                    if (count == tasks.size()) {
                        Log.d(T, "执行完毕");

                        // 关闭浮动窗口
                        stopService(intentServiceSystemAlertWindow);
                    }
                }
            }, timeline);
        }
    }

}
