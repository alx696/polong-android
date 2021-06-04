package red.lilu.app;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.TypedValue;
import android.view.ViewConfiguration;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.CameraXConfig;
import androidx.core.content.FileProvider;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.core.Single;

public class MyApplication extends Application implements CameraXConfig.Provider {

    private static final String T = "调试";
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final Gson gson = new Gson();
    private static RxDataStore<Preferences> preferencesDataStore;

    public static final Preferences.Key<String> SETTING_WEBSITE = PreferencesKeys.stringKey("setting-website");
    public static final Preferences.Key<String> SETTING_STATISTICS_API = PreferencesKeys.stringKey("setting-statisticsAPI");
    public static final Preferences.Key<Integer> SETTING_VERSION_CODE = PreferencesKeys.intKey("setting-versionCode");
    public static final Preferences.Key<Boolean> SETTING_REMOTE_CONTROL_ENABLE = PreferencesKeys.booleanKey("setting-remote_control-enable");

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
     * 秒数转为时长文字(1时23分7秒)
     */
    public String timeSecondsToText(long seconds) {
        StringBuilder sb = new StringBuilder();

        if (seconds >= 3600) {
            long l = TimeUnit.HOURS.convert(seconds, TimeUnit.SECONDS);
            sb.append(l)
                    .append("时");

            seconds = seconds - l * 3600;
        }

        if (seconds >= 60) {
            long l = TimeUnit.MINUTES.convert(seconds, TimeUnit.SECONDS);
            sb.append(l)
                    .append("分");

            seconds = seconds - l * 60;
        }

        if (seconds > 0) {
            sb.append(seconds)
                    .append("秒");
        }

        return sb.toString();
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
     * 指定服务是否运行
     *
     * @param fullClassName 例如 String.class.getName()
     * @return 是否
     */
    public boolean isServiceRunning(String fullClassName) {
        Log.d(T, String.format("当前应用ID：%s，服务类名：%s", getPackageName(), fullClassName));
        boolean isRunning = false;
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> runningServices = activityManager.getRunningServices(Integer.MAX_VALUE);
        for (ActivityManager.RunningServiceInfo rsi : runningServices) {
            if (rsi.service.getPackageName().equals(getPackageName()) &&
                    rsi.service.getClassName().equals(fullClassName)) {
                isRunning = true;
            }
        }
        return isRunning;
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
     * 以DP为单位获取像素距离
     */
    public int getDP(float v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                v,
                getResources().getDisplayMetrics()
        );
    }

    /**
     * 获取状态栏高度(像素)
     */
    public int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    /**
     * 获取导航栏高度(像素)
     */
    public int getNavigationBarHeight() {
        boolean hasMenuKey = ViewConfiguration.get(this).hasPermanentMenuKey();
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0 && !hasMenuKey) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    public static class FileInfo {
        String path;
        Long size;
        String nameWithoutExtension;
        String extension;

        public FileInfo(String path, Long size, String nameWithoutExtension, String extension) {
            this.path = path;
            this.size = size;
            this.nameWithoutExtension = nameWithoutExtension;
            this.extension = extension;
        }
    }

    /**
     * 文件名称去除不能使用的部分
     *
     * @return 空字符串表示完全没法使用
     */
    public static @NonNull
    String filenameClear(String filename) {
        if (filename == null) {
            return "";
        }

        Pattern p = Pattern.compile(".*[~`!@#$%^&*{}|:;\"'<,>?/+=]+.*");
        Matcher m = p.matcher(filename);

        filename = m.replaceAll("");

        filename = FilenameUtils.normalizeNoEndSeparator(filename);
        if (filename == null) {
            return "";
        }

        return FilenameUtils.getName(filename);
    }

    /**
     * 将Intent.ACTION_GET_CONTENT获取到的URI复制到文件存放目录
     * <p>默认情况下, Uri资源没有文件名称, 也不能进行操作, 故将其复制到应用可以控制的目录中.</p>
     *
     * @param uri Intent.ACTION_GET_CONTENT返回的Uri
     * @param dir 文件存放目录
     */
    public FileInfo fileFromUriToDir(Uri uri, File dir) throws Exception {
        // 获取文件名称
        String fileName = "";
        ContentResolver contentResolver = getContentResolver();
        try (
                Cursor cursor = contentResolver.query(uri, null, null, null, null, null)
        ) {
            if (cursor != null && cursor.moveToFirst()) {
                // Note it's called "Display Name".  This is provider-specific, and might not necessarily be the file name.
                fileName = cursor.getString(
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                );
            }
        }

        if (fileName.isEmpty()) {
            throw new IOException("查询不到文件没有名字:" + uri.toString());
        }

        // 解析文件名称
        String nameWithoutExtension = FilenameUtils.getBaseName(fileName);
        String extension = FilenameUtils.getExtension(fileName);

        // 防止名称重复
        File file = new File(dir, fileName);
        if (file.exists()) {
            nameWithoutExtension = String.format(Locale.CHINA, "%s[%d]", nameWithoutExtension, System.currentTimeMillis());
            file = new File(dir, nameWithoutExtension + "." + extension);
        }

        // 复制文件
        FileUtils.copyInputStreamToFile(
                contentResolver.openInputStream(uri),
                file
        );
        Log.d(T, "已经复制URI到指定目录:" + file.getAbsolutePath());

        return new FileInfo(file.getAbsolutePath(), file.length(), nameWithoutExtension, extension);
    }

    /**
     * 选择文件
     */
    public static void fileChoose(FragmentActivity activity, int requestCode) {
        // https://developer.android.com/training/data-storage/shared/documents-files#open-file
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * 复制文件到公共下载文件
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
                File targetFile = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        FilenameUtils.getName(path)
                );
                FileUtils.copyFile(new File(path), targetFile);  // 小米4C不支持？！

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
     * 计算位图在目标宽高下的inSampleSize
     * https://developer.android.com/topic/performance/graphics/load-bitmap#load-bitmap
     *
     * @param options   位图选项
     * @param reqWidth  目标宽度
     * @param reqHeight 目标高度
     * @return BitmapFactory.Options.inSampleSize
     */
    public static int calculateBitmapInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * 将文件解码为小位图
     * https://developer.android.com/topic/performance/graphics/load-bitmap#load-bitmap
     *
     * <code>
     * ByteArrayOutputStream bos = new ByteArrayOutputStream();
     * bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
     * // 处理 bos.toByteArray()
     * bitmap.recycle();
     * os.close();
     * </code>
     *
     * @param path      文件路径
     * @param reqWidth  限定宽度
     * @param reqHeight 限定高度
     * @return 限定宽高的小位图
     */
    public static Bitmap fileScaleBitmapFromFile(String path, int reqWidth, int reqHeight) throws IOException {
        // 获取位图尺寸
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        // 计算inSampleSize
        options.inSampleSize = calculateBitmapInSampleSize(options, reqWidth, reqHeight);

        // 按照inSampleSize解码位图
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);

        // 三星SM-G9500拍的照片BitmapFactory.Options得到的宽高是颠倒的, 读取Exif信息修正.
        // https://github.com/google/cameraview/issues/22#issuecomment-363047917
        int orientation = 1;
        ExifInterface exif = new ExifInterface(path);
        String attribute = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
        if (attribute != null) {
            orientation = Integer.parseInt(attribute);
        }
        if (orientation == 6 || orientation == 8) {
            final Matrix matrix = new Matrix();
            matrix.setRotate(orientation == 6 ? 90 : 270, (float) (bitmap.getWidth()) / 2, (float) (bitmap.getHeight()) / 2);
            bitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true
            );
        }

        return bitmap;
    }

    @NonNull
    @Override
    public CameraXConfig getCameraXConfig() {
        return Camera2Config.defaultConfig();
    }

    public static class CameraSize {
        int width, height;

        public CameraSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    public static class CameraInfo {
        /**
         * 朝向:标识
         */
        LinkedHashMap<String, String> facingMap = new LinkedHashMap<>();
        /**
         * 标识:输出格式Set
         */
        LinkedHashMap<String, LinkedHashSet<Integer>> formatMap = new LinkedHashMap<>();
        /**
         * 标识:输出尺寸Set
         */
        LinkedHashMap<String, LinkedHashSet<CameraSize>> sizeMap = new LinkedHashMap<>();
    }

    /**
     * 参考 https://android.googlesource.com/platform/cts/+/b9fc825/tests/tests/hardware/src/android/hardware/camera2/cts/CameraTestUtils.java#502
     */
    public static void cameraInfo(CameraManager cameraManager,
                                  java.util.function.Consumer<String> onError,
                                  java.util.function.Consumer<String> onWarn,
                                  java.util.function.Consumer<CameraInfo> onDone) {
        Log.i(T, "相机检测");

        CompletableFuture.runAsync(() -> {
            try {
                String[] cameraIdList = cameraManager.getCameraIdList();
                if (cameraIdList.length == 0) {
                    onError.accept("没有相机设备");
                    return;
                }

                CameraInfo cameraInfo = new CameraInfo();
                for (String id : cameraIdList) {
                    Log.d(T, "相机标识:" + id);
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);

                    // 朝向
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    String facingText = "";
                    if (facing == CameraMetadata.LENS_FACING_FRONT) {
                        facingText = "前";
                    } else if (facing == CameraMetadata.LENS_FACING_BACK) {
                        facingText = "后";
                    } else if (facing == CameraMetadata.LENS_FACING_EXTERNAL) {
                        facingText = "外接";
                    }
                    if (facingText.isEmpty()) {
                        onWarn.accept(
                                String.format(
                                        Locale.CHINA,
                                        "相机: %s 没有朝向信息!",
                                        id
                                )
                        );
                        continue;
                    }
                    Log.d(T, "相机朝向:" + facingText);
                    cameraInfo.facingMap.put(facingText, id);

                    // 支持的可用流配置
                    LinkedHashSet<Integer> outputFormatSet = new LinkedHashSet<>();
                    cameraInfo.formatMap.put(facingText, outputFormatSet);
                    LinkedHashSet<CameraSize> outputSizeSet = new LinkedHashSet<>();
                    cameraInfo.sizeMap.put(facingText, outputSizeSet);
                    StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    //支持输出格式
                    for (int outputFormat : streamConfigurationMap.getOutputFormats()) {
                        if (streamConfigurationMap.isOutputSupportedFor(outputFormat)) {
                            Log.d(T, String.format(Locale.CHINA, "支持输出格式:%d", outputFormat));
                            outputFormatSet.add(outputFormat);
                        }
                    }
                    if (outputFormatSet.size() == 0) {
                        onWarn.accept(
                                String.format(
                                        Locale.CHINA,
                                        "相机: %s 没有支持的输出格式!",
                                        id
                                )
                        );
                        continue;
                    }
                    //支持输出尺寸
                    int checkOutputSizeForm = ImageFormat.JPEG;
                    if (outputFormatSet.contains(ImageFormat.YUV_420_888)) {
                        checkOutputSizeForm = ImageFormat.YUV_420_888;
                    }
                    Log.d(T, String.format(Locale.CHINA, "获取支持输出尺寸的格式为:%d", checkOutputSizeForm));
                    Size[] outputSizes = streamConfigurationMap.getOutputSizes(checkOutputSizeForm);
                    if (outputSizes != null && outputSizes.length > 0) {
                        for (Size size : outputSizes) {
                            Log.i(T, String.format("支持输出尺寸:%d,%d", size.getWidth(), size.getHeight()));
                            outputSizeSet.add(
                                    new CameraSize(size.getWidth(), size.getHeight())
                            );
                        }
                    }
                    if (outputSizeSet.size() == 0) {
                        onWarn.accept(
                                String.format(
                                        Locale.CHINA,
                                        "相机: %s 没有支持的输出尺寸!",
                                        id
                                )
                        );
                        continue;
                    }
                    //高速视频尺寸
                    Size[] highSpeedVideoSizes = streamConfigurationMap.getHighSpeedVideoSizes();
                    if (highSpeedVideoSizes != null) {
                        for (Size size : highSpeedVideoSizes) {
                            Log.d(T, String.format("高速视频尺寸:%d,%d", size.getWidth(), size.getHeight()));
                            Range<Integer>[] highSpeedVideoFpsRanges = streamConfigurationMap.getHighSpeedVideoFpsRangesFor(size);
                            for (Range<Integer> r : highSpeedVideoFpsRanges) {
                                Log.d(T, String.format("高速视频尺寸的FPS:%d,%d", r.getLower(), r.getUpper()));
                            }
                        }
                    }
                }
                onDone.accept(cameraInfo);
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(
                        String.format(
                                Locale.CHINA,
                                "异常: %s",
                                e.getMessage()
                        )
                );
            }
        }, executorService);
    }

    public static boolean cameraIsSupportSize(int w, int h, LinkedHashSet<CameraSize> set) {
        for (CameraSize s : set) {
            if (s.width == w && s.height == h) {
                return true;
            }
        }

        return false;
    }

}
