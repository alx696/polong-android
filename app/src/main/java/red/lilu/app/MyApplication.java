package red.lilu.app;

import android.Manifest;
import android.app.Application;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.datastore.core.Serializer;
import androidx.datastore.rxjava3.RxDataStore;
import androidx.datastore.rxjava3.RxDataStoreBuilder;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import red.lilu.app.data_store.Pref;

public class MyApplication extends Application {

    private static final String T = "调试";
    private String versionName = "";
    private int versionCode = 0;
    private static final Gson gson = new Gson();
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    private static class PrefSerializer implements Serializer<Pref> {
        @Override
        public Pref getDefaultValue() {
            return Pref.getDefaultInstance();
        }

        @androidx.annotation.Nullable
        @Override
        public Pref readFrom(@NonNull InputStream inputStream, @NonNull Continuation<? super Pref> continuation) {
            try {
                return Pref.parseFrom(inputStream);
            } catch (IOException e) {
                continuation.resumeWith(e);
            }
            return null;
        }

        @androidx.annotation.Nullable
        @Override
        public Pref writeTo(Pref pref, @NonNull OutputStream outputStream, @NonNull Continuation<? super Unit> continuation) {
            try {
                pref.writeTo(outputStream);
            } catch (IOException e) {
                continuation.resumeWith(e);
            }

            return pref;
        }
    }

    private static RxDataStore<Pref> dataStore;

    public static final HashSet<String> imageExtensionSet = Sets.newHashSet("webp", "png", "jpg", "jpeg", "bmp");

    @Override
    public void onCreate() {
        super.onCreate();

        // 获取版本名称
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionCode = packageInfo.versionCode;
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(T, e);
        }

        dataStore = new RxDataStoreBuilder<>(
                getApplicationContext(), "pref", new PrefSerializer()
        ).build();
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public Gson getGson() {
        return gson;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public RxDataStore<Pref> getDataStore() {
        return dataStore;
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

    /**
     * 当文件源是URI时使用不便, 故将其复制到可控文件夹中.
     */
    public void uriToDir(Uri uri, File dir,
                         java.util.function.Consumer<File> onDone,
                         java.util.function.Consumer<String> onError) {
        ContentResolver contentResolver = getContentResolver();
        String name = "";
        try (Cursor cursor = contentResolver.query(uri, null, null, null, null, null)) {
            if (cursor == null) {
                onError.accept("无法查询URI信息");
                return;
            }

            int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (columnIndex < 0) {
                onError.accept("无法获取名称索引");
                return;
            }

            if (cursor.moveToFirst()) {
                name = cursor.getString(columnIndex);
            }
        }

        if (name.isEmpty()) {
            onError.accept("无法获取文件名称");
            return;
        }

        // 防止文件重复
        if (new File(dir, name).exists()) {
            String fileExtension = Files.getFileExtension(name);
            if (!fileExtension.isEmpty()) {
                fileExtension = "." + fileExtension;
            }
            name = String.format("%s[%s]%s", Files.getNameWithoutExtension(name), System.currentTimeMillis(), fileExtension);
        }

        // 复制文件
        File outputFile = new File(dir, name);
        try (
                InputStream inputStream = contentResolver.openInputStream(uri);
                FileOutputStream outputStream = new FileOutputStream(outputFile)
        ) {
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            onDone.accept(outputFile);
        } catch (IOException e) {
            Log.w(T, e);
            onError.accept(e.getMessage());
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
    public static void fileView(FragmentActivity activity, String path, @Nullable String title,
                                java.util.function.Consumer<String> onError) {
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
        Log.d(T, "查看文件类型:" + mimeType);
        if (mimeType != null) {
            intent.setDataAndType(uri, mimeType);
        } else {
            intent.setData(uri);
        }

        // 检查是否有可以处理文件的应用
        // https://developer.android.com/guide/components/intents-filters#imatch
        ComponentName resolveActivity = intent.resolveActivity(activity.getPackageManager());
        if (resolveActivity == null) {
            Log.w(T, "没有能够查看此文件的应用!");
            onError.accept("没有能够查看此文件的应用!");
            return;
        }
        Log.d(T, "查看此文件的默认应用:" + resolveActivity.getClassName());

        if (title != null) {
            // 强制选择
            try {
                activity.startActivity(
                        Intent.createChooser(intent, title)
                );
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        } else {
            activity.startActivity(intent);
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
