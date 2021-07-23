package red.lilu.app;

import android.Manifest;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.core.Single;

public class MyApplication extends Application {

    private static final String T = "调试";
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final Gson gson = new Gson();
    private static RxDataStore<Preferences> preferencesDataStore;

    public static final Preferences.Key<String> SETTING_WEBSITE = PreferencesKeys.stringKey("setting-website");
    public static final Preferences.Key<String> SETTING_STATISTICS_API = PreferencesKeys.stringKey("setting-statisticsAPI");
    public static final Preferences.Key<Integer> SETTING_VERSION_CODE = PreferencesKeys.intKey("setting-versionCode");
    public static final Preferences.Key<Boolean> SETTING_REMOTE_CONTROL_ENABLE = PreferencesKeys.booleanKey("setting-remote_control-enable");

    public static final HashSet<String> imageExtensionSet = Sets.newHashSet("webp", "png", "jpg", "jpeg", "bmp");

    @Override
    public void onCreate() {
        super.onCreate();

        preferencesDataStore = new RxPreferenceDataStoreBuilder(getApplicationContext(), "settings").build();
        if (preferencesDataStore.data().blockingFirst().get(MyApplication.SETTING_WEBSITE) == null) {
            preferencesDataStore.updateDataAsync(
                    p -> {
                        MutablePreferences mutablePreferences = p.toMutablePreferences();

                        mutablePreferences.set(MyApplication.SETTING_WEBSITE, "https://lilu.red/app/");
                        mutablePreferences.set(MyApplication.SETTING_VERSION_CODE, 0);
                        mutablePreferences.set(MyApplication.SETTING_STATISTICS_API, "https://lilu.red/statistics/");

                        return Single.just(mutablePreferences);
                    }
            )
                    .blockingSubscribe();
        }
        if (preferencesDataStore.data().blockingFirst().get(SETTING_REMOTE_CONTROL_ENABLE) == null) {
            preferencesDataStore.updateDataAsync(p -> {
                MutablePreferences mutablePreferences = p.toMutablePreferences();

                mutablePreferences.set(SETTING_REMOTE_CONTROL_ENABLE, false);

                return Single.just(mutablePreferences);
            })
                    .blockingSubscribe();
        }
    }

    public RxDataStore<Preferences> getPreferencesDataStore() {
        return preferencesDataStore;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public Gson getGson() {
        return gson;
    }

    /**
     * 获取包信息
     */
    public PackageInfo getPackageInfo() {
        try {
            PackageInfo packageInfo = getPackageManager()
                    .getPackageInfo(
                            getPackageName(),
                            0
                    );
            Log.d(T, String.format(Locale.CHINA, "版本编号:%d 版本名称:%s", packageInfo.versionCode, packageInfo.versionName));
            return packageInfo;
        } catch (Exception e) {
            Log.w(T, "获取包信息失败");
        }
        return null;
    }

    /**
     * 指定无障碍服务是否开启
     *
     * @param fullClassName 例如 AccessibilityServiceRemoteControl.class.getName()
     * @return 是否
     */
    public boolean isAccessibilitySettingsOn(String fullClassName) {
        final String service = getPackageName() + "/" + fullClassName;

        try {
            int accessibilityEnabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
//            Log.d(T, "无障碍服务是否开启设置：" + accessibilityEnabled);

            String enabledServices = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (enabledServices != null) {
                TextUtils.SimpleStringSplitter simpleStringSplitter = new TextUtils.SimpleStringSplitter(':');
                simpleStringSplitter.setString(enabledServices);
                while (simpleStringSplitter.hasNext()) {
                    String accessabilityService = simpleStringSplitter.next();
//                    Log.d(T, "已经开启的无障碍服务：" + accessabilityService);

                    if (accessabilityService.equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            } else {
                Log.w(T, "没有找到已经开启的无障碍服务设置");
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.w(T, "没有找到无障碍服务设置: " + e.getMessage());
        }

        return false;
    }

    /**
     * 亮屏
     * 注意：如果按电源键不能直接使用（需要滑动或解锁）则功能无效！
     */
    public void screenOn() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null && !powerManager.isInteractive()) {
            Log.d(T, "设备处于熄屏状态，进行亮屏");

            // 亮屏
            PowerManager.WakeLock unlockWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                    getPackageName() + ":wakelock:wakeup"
            );
            unlockWakeLock.acquire();
            unlockWakeLock.release();
        } else {
            Log.d(T, "没有电源管理器，或处于亮屏状态");
        }
    }

    private static class DNSResponseAnswer {
        String data;
    }

    private static class DNSResponse {
        LinkedList<DNSResponseAnswer> Answer;
    }

    public void dnsTxt(String domain,
                       java.util.function.Consumer<String> onError,
                       java.util.function.Consumer<ArrayList<String>> onDone) {
        Log.d(T, "查询DNS TXT:" + domain);

        CompletableFuture.runAsync(() -> {
            try {
                ArrayList<String> list = new ArrayList<>();

                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url("https://doh.pub/dns-query?type=16&name=" + domain)
                        .header("accept", "application/dns-json")
                        .build();
                try (okhttp3.Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        onError.accept(String.format(Locale.CHINA, "查询DNS TXT接口出错:%d", response.code()));
                        return;
                    }

                    String json = response.body().string();
                    DNSResponse dnsResponse = new Gson().fromJson(json, DNSResponse.class);
                    if (dnsResponse.Answer != null) {
                        for (DNSResponseAnswer dnsAnswer : dnsResponse.Answer) {
                            String data = dnsAnswer.data;
                            data = data.substring(1, data.length() - 1);
                            list.add(data);
                        }
                    }
                }

                onDone.accept(list);
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, executorService);
    }

    /**
     * 开始发送匿名统计信息
     */
    public void statistics(int versionCode,
                           java.util.function.Consumer<String> onError) {
        Preferences preferences = preferencesDataStore.data().blockingFirst();
        String url = String.format(Locale.CHINA, "%s?package=%s&versionCode=%d", preferences.get(MyApplication.SETTING_STATISTICS_API), getPackageName(), versionCode);
        Log.d(T, "开始发送匿名统计信息（仅发送应用名称和版本）:" + url);

        CompletableFuture.runAsync(() -> {
            try {
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(url)
                        .build();
                try (okhttp3.Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String error = String.format(Locale.CHINA, "发送匿名统计信息出错:%d", response.code());
                        Log.w(T, error);
                        onError.accept(error);
                        return;
                    }

                    Log.d(T, "完成发送匿名统计信息（仅发送应用名称和版本）");
                }
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, executorService);
    }

    public void updateFromDNS(String domain, java.util.function.Consumer<String> onVersionUpdate) {
        try {
            // 获取网站地址
            dnsTxt(
                    domain,
                    error -> {
                        Log.w(T, error);
                    },
                    list -> {
                        String website = "";
                        int versionCode = 0;
                        String statisticsAPI = "";
                        for (String txt : list) {
                            Log.d(T, txt);

                            if (txt.startsWith("[site]")) {
                                website = txt.replaceFirst(Pattern.quote("[site]"), "");
                                Log.d(T, "DNS中网站:" + website);
                            } else if (txt.startsWith("[code]")) {
                                versionCode = Integer.parseInt(
                                        txt.replaceFirst(Pattern.quote("[code]"), "")
                                );
                                Log.d(T, "DNS中版本编号:" + versionCode);
                            } else if (txt.startsWith("[sapi]")) {
                                statisticsAPI = txt.replaceFirst(Pattern.quote("[sapi]"), "");
                                Log.d(T, "DNS中统计接口:" + statisticsAPI);
                            }
                        }

                        //保存
                        String finalWebsite = website;
                        int finalVersionCode = versionCode;
                        String finalStatisticsAPI = statisticsAPI;
                        Single<Preferences> updateResult = preferencesDataStore.updateDataAsync(
                                p -> {
                                    MutablePreferences mutablePreferences = p.toMutablePreferences();

                                    if (!finalWebsite.isEmpty()) {
                                        mutablePreferences.set(MyApplication.SETTING_WEBSITE, finalWebsite);
                                    }
                                    if (finalVersionCode != 0) {
                                        mutablePreferences.set(MyApplication.SETTING_VERSION_CODE, finalVersionCode);
                                    }
                                    if (!finalStatisticsAPI.isEmpty()) {
                                        mutablePreferences.set(MyApplication.SETTING_STATISTICS_API, finalStatisticsAPI);
                                    }

                                    return Single.just(mutablePreferences);
                                }
                        );
                        updateResult.blockingSubscribe();

                        //回调
                        PackageInfo packageInfo = getPackageInfo();
                        if (packageInfo == null) {
                            Log.w(T, "获取包信息失败");
                            return;
                        }
                        if (finalVersionCode > packageInfo.versionCode) {
                            onVersionUpdate.accept(packageInfo.versionName);
                        }

                        //统计
                        statistics(
                                packageInfo.versionCode, error -> {
                                }
                        );
                    }
            );
        } catch (Exception e) {
            Log.w(T, "获取版本失败");
        }
    }

    /**
     * 分享文件
     */
    public static void fileShare(FragmentActivity activity, String path, java.util.function.Consumer<String> onError) {
        Log.d(T, "分享文件:" + path);

        MediaScannerConnection.scanFile(
                activity,
                new String[]{path},
                null,
                (resultPath, uri) -> {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.setType("application/octet-stream"); //其他文件
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    // 检查是否有可以处理文件的应用
                    // https://developer.android.com/guide/components/intents-filters#imatch
                    ComponentName resolveActivity = intent.resolveActivity(activity.getPackageManager());
                    if (resolveActivity == null) {
                        Log.w(T, "没有处理此文件的应用!");
                        onError.accept("没有处理此文件的应用!");
                    }

                    Log.d(T, "处理此文件的默认应用:" + resolveActivity.getClassName());

                    try {
                        // 强制选择
                        activity.startActivity(
                                Intent.createChooser(intent, "选择分享方式")
                        );
                    } catch (Exception e) {
                        Log.w(T, e);
                        onError.accept(e.getMessage());
                    }

                    //参考https://developer.android.com/training/sharing/send.html#send-binary-content
                    //注意:直接分享时微信和TIM都无法找到文件,只有使用MediaScannerConnection.scanFile后才能分享成功.
                }
        );
    }

    /**
     * 查看文件
     */
    public static void fileView(FragmentActivity activity, String path, java.util.function.Consumer<String> onError) {
        Log.d(T, "查看文件:" + path);
        File file = new File(path);

        // 首先,生成Provider的Content Uri
        // 注意: 文件目录必须配置, 否则抛 IllegalArgumentException: Failed to find configured root that contains
        // 注意: Intent需要设置 FLAG_GRANT_READ_URI_PERMISSION 或 FLAG_GRANT_WRITE_URI_PERMISSION
        Uri uri = FileProvider.getUriForFile(
                activity,
                String.format("%s.provider", activity.getPackageName()),
                file
        );

        // 接着, 设置Intent
        Intent intent = new Intent(Intent.ACTION_VIEW);

        // 接着, 标记赋予读权限(否则某些系统中调用的应用无法获取文件内容)
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // 接着, 设置内容
        String mimeType = activity.getContentResolver().getType(uri);
        Log.d(T, "文件类型:" + mimeType);
        if (mimeType != null) {
            intent.setDataAndType(uri, mimeType);
        } else {
            intent.setData(uri);
        }

        // 检查是否有可以处理文件的应用
        // https://developer.android.com/guide/components/intents-filters#imatch
        ComponentName resolveActivity = intent.resolveActivity(activity.getPackageManager());
        if (resolveActivity == null) {
            Log.w(T, "没有处理此文件的应用!");
            onError.accept("没有处理此文件的应用!");
        }

        Log.d(T, "可以处理此文件的默认应用:" + resolveActivity.getClassName());

        try {
            // 强制选择
            activity.startActivity(
                    Intent.createChooser(intent, "选择查看方式")
            );
        } catch (Exception e) {
            Log.w(T, e);
            onError.accept(e.getMessage());
        }
    }

    /**
     * 文件复制到公共下载目录
     */
    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    public static void fileCopyToDownload(FragmentActivity activity,
                                          String path,
                                          MyApplication application,
                                          java.util.function.Consumer<String> onError,
                                          java.util.function.Consumer<String> onDone) {
        AlertDialog waitDialog = new MaterialAlertDialogBuilder(activity)
                .setCancelable(false)
                .setView(R.layout.dialog_wait) // 嵌入视图
                .show();
        TextView text_tips = waitDialog.findViewById(R.id.text_tips);
        text_tips.setText("正在复制");

        CompletableFuture.runAsync(() -> {
            try {
                File sourceFile = new File(path);
                File targetFile = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        sourceFile.getName()
                );

                if (targetFile.exists()) {
                    waitDialog.cancel();
                    onError.accept("已经存在同名文件，复制失败！");
                    return;
                }

                Files.copy(sourceFile, targetFile);

                // Tell the media scanner about the new file so that it is
                // immediately available to the user. 某些应用不进行此步骤找不到文件！
                MediaScannerConnection.scanFile(
                        activity,
                        new String[]{targetFile.getAbsolutePath()},
                        null,
                        (scanPath, scanUri) -> {
                            Log.d(T, "媒体扫描完成-路径: " + scanPath + "，URI" + scanUri);
                            waitDialog.cancel();
                            onDone.accept("");
                        }
                );
            } catch (Exception e) {
                Log.w(T, e);
                waitDialog.cancel();
                onError.accept(e.getMessage());
            }
        }, application.getExecutorService());
    }

}
