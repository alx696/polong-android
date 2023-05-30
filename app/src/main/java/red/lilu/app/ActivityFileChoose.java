package red.lilu.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Ordering;
import com.google.common.io.Files;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;

import red.lilu.app.databinding.ActivityFileChooseBinding;
import red.lilu.app.databinding.RecyclerViewFileChooseBinding;

/**
 * 选择文件
 * <p>如需单选设置Intent参数 <code>intent.putExtra("single", true);</code></p>
 * <p>通过ActivityResult返回所选文件路径，单选时<code>getStringExtra("path")</code>，多选时<code>getStringArrayExtra("paths")</code></p>
 */
public class ActivityFileChoose extends AppCompatActivity {

    private static final String T = "调试";
    private static final int REQUEST_CODE_MANAGE_EXTERNAL_STORAGE = 1;
    private static final int REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 2;
    private static final LinkedHashMap<String, String> pathMap = new LinkedHashMap<>();
    private ActivityFileChooseBinding b;
    private static MyApplication application;
    private static RecyclerViewAdapterMy adapter;
    private boolean single; // 是否单选
    private File dir; // 当前目录

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityFileChooseBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);

        // 准备公用
        application = (MyApplication) getApplication();

        //准备界面
        b.recycler.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        b.recycler.setLayoutManager(layoutManager);
        DividerItemDecoration itemDecoration = new DividerItemDecoration(
                b.recycler.getContext(),
                layoutManager.getOrientation()
        );
        b.recycler.addItemDecoration(itemDecoration);
        adapter = new RecyclerViewAdapterMy();
        b.recycler.setAdapter(adapter);

        //获取参数
        single = getIntent().getBooleanExtra("single", false);
        Log.i(T, "是否单选:" + single);

        // 申请所有文件权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            new MaterialAlertDialogBuilder(ActivityFileChoose.this)
                    .setTitle("需要你来操作")
                    .setMessage(String.format("接下来请找并点击 %s ，授予所有文件的管理权限。", getString(R.string.app_name)))
                    .setNegativeButton("取消", (dialog, which) -> {
                        finish();
                    })
                    .setPositiveButton("继续", (dialog, which) -> {
                        // 打开设置界面
                        startActivityForResult(
                                new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                                REQUEST_CODE_MANAGE_EXTERNAL_STORAGE
                        );
                    })
                    .show();
            return;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                && ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE_WRITE_EXTERNAL_STORAGE
            );

            return;
        }

        // 开始业务
        init();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.file_choose, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int menuId = item.getItemId();

        if (menuId == R.id.close) {
            finish();
        }
        if (menuId == R.id.done) {
            done();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_MANAGE_EXTERNAL_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                init();
            } else {
                new MaterialAlertDialogBuilder(ActivityFileChoose.this)
                        .setTitle("信任危机")
                        .setMessage("没有权限，无法工作！")
                        .setPositiveButton("已读", (dialog, which) -> {
                            finish();
                        })
                        .show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                init();
            } else {
                new MaterialAlertDialogBuilder(ActivityFileChoose.this)
                        .setTitle("信任危机")
                        .setMessage("没有权限，无法工作！")
                        .setPositiveButton("已读", (dialog, which) -> {
                            finish();
                        })
                        .show();
            }
        }
    }

    private static class FileInfo {
        String path, name;

        public FileInfo(String path, String name) {
            this.path = path;
            this.name = name;
        }
    }

    private static final Ordering<FileInfo> fileInfoOrderingName = new Ordering<FileInfo>() {
        @Override
        public int compare(FileInfo left, FileInfo right) {
            return left.name.toLowerCase().compareTo(right.name.toLowerCase());
        }
    };

    private static String fileSize(long size) {
        if (size >= 1024 * 1024 * 1024) {
            return String.format(Locale.CHINA, "%.3f GB", (float) size / 1024 / 1024 / 1024);
        } else if (size >= 1024 * 1024) {
            return String.format(Locale.CHINA, "%.3f MB", (float) size / 1024 / 1024);
        }
        return String.format(Locale.CHINA, "%.3f KB", (float) size / 1024);
    }

    private class RecyclerViewAdapterMy extends RecyclerView.Adapter<RecyclerViewAdapterMy.ViewHolder> {

        private ArrayList<FileInfo> list = new ArrayList<>();
        private final HashSet<String> set = new HashSet<>();

        public class ViewHolder extends RecyclerView.ViewHolder {
            final RecyclerViewFileChooseBinding b;

            public ViewHolder(RecyclerViewFileChooseBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }

        public RecyclerViewAdapterMy() {
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
            return new ViewHolder(
                    RecyclerViewFileChooseBinding.inflate(
                            LayoutInflater.from(viewGroup.getContext()),
                            viewGroup,
                            false
                    )
            );
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, final int position) {
            FileInfo data = list.get(position);
            String info = "";

            boolean canOpen = true;
            File file = new File(data.path);
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files == null) {
                    canOpen = false;
                    Log.w(T, "不能打开:" + file.getAbsolutePath());
                    info = "不能打开";
                } else {
                    info = String.format("%s个文件(夹)", files.length);
                }
            } else {
                info = fileSize(file.length());
            }

            h.b.textName.setText(data.name);
            h.b.textInfo.setText(info);

            // 同步选中状态
            if (set.contains(data.path)) {
                h.b.imageCheck.setImageResource(R.drawable.ic_check_box);
            } else {
                h.b.imageCheck.setImageResource(R.drawable.ic_check_box_outline);
            }
            if (file.isFile()) {
                h.b.imageCheck.setVisibility(View.VISIBLE);
                h.b.layoutCheck.setOnClickListener(v -> {
                    if (set.contains(data.path)) {
                        //取消选择
                        set.remove(data.path);
                        h.b.imageCheck.setImageResource(R.drawable.ic_check_box_outline);
                    } else {
                        if (single) {
                            set.clear();
                        }

                        set.add(data.path);
                        h.b.imageCheck.setImageResource(R.drawable.ic_check_box);
                    }

                    if (single) {
                        notifyDataSetChanged();
                    }
                });
            } else {
                h.b.imageCheck.setVisibility(View.GONE);
            }

            Glide.with(application.getApplicationContext())
                    .clear(h.b.imagePreview);
            if (file.isDirectory()) {
                // 预览图
                if (canOpen) {
                    h.b.imagePreview.setImageResource(R.drawable.ic_folder);
                } else {
                    h.b.imagePreview.setImageResource(R.drawable.ic_security);
                }

                // 打开
                boolean finalCanOpen = canOpen;
                h.b.getRoot().setOnClickListener(v -> {
                    if (finalCanOpen) {
                        updatePath(
                                data.path,
                                data.name
                        );
                    }
                });
            } else {
                // 预览图
                if (MyApplication.imageExtensionSet.contains(Files.getFileExtension(file.getName()).toLowerCase())) {
                    Glide.with(application.getApplicationContext())
                            .load(data.path)
                            .into(h.b.imagePreview);
                } else {
                    h.b.imagePreview.setImageResource(R.drawable.ic_insert_drive_file);
                }

                // 打开
                h.b.getRoot().setOnClickListener(v -> {
                    MyApplication.fileView(
                            ActivityFileChoose.this,
                            data.path,
                            null,
                            error -> {
                                runOnUiThread(() -> {
                                    Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                                });
                            }
                    );
                });
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        /**
         * 设置数据
         */
        public void set(ArrayList<FileInfo> list) {
            this.list = list;
            notifyDataSetChanged();
        }

        /**
         * 获取选择
         */
        public HashSet<String> getCheck() {
            return set;
        }

    }

    private void init() {
        updatePath(Environment.getExternalStorageDirectory().getPath(), "存储");
    }

    private void updatePath(String path, String name) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();

        boolean exists = false;
        for (String p : pathMap.keySet()) {
            map.put(p, pathMap.get(p));

            if (p.equals(path)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            map.put(path, name);
        }

        // 更新map,更新视图
        pathMap.clear();
        ChipGroup chipGroup = b.chipGroupPath;
        chipGroup.removeAllViews();
        for (String p : map.keySet()) {
            pathMap.put(p, map.get(p));

            //https://stackoverflow.com/questions/50494502/how-can-i-add-the-new-android-chips-dynamically-in-android?answertab=active#tab-top
            Chip chip = new Chip(chipGroup.getContext());
            chip.setText(
                    map.get(p)
            );
            chipGroup.addView(chip);

            chip.setOnClickListener((v) -> {
                String chipText = chip.getText().toString();
                updatePath(
                        p,
                        chipText
                );
            });
        }
        b.scrollPath.postDelayed(() -> b.scrollPath.fullScroll(HorizontalScrollView.FOCUS_RIGHT), 300L);

        showPath(path);
    }

    private void showPath(String path) {
        Log.d(T, String.format("显示路径 %s", path));
        // 当前目录
        File[] files = new File(path).listFiles();
        if (files == null) {
            Log.w(T, String.format("路径 %s 没有文件", path));
            return;
        }

        ArrayList<FileInfo> list = new ArrayList<>();
        for (File file : files) {
            list.add(
                    new FileInfo(file.getAbsolutePath(), file.getName())
            );
        }
        list.sort(fileInfoOrderingName);

        adapter.set(list);
    }

    private void done() {
        HashSet<String> checkSet = adapter.getCheck();
        if (checkSet.size() == 0) {
            new MaterialAlertDialogBuilder(ActivityFileChoose.this)
                    .setTitle("没有选择")
                    .setMessage("没有选择任何文件")
                    .setNegativeButton("关闭", (dialog, which) -> {
                        dialog.cancel();
                    })
                    .show();

            return;
        }
        Log.i(T, "选择文件: " + application.getGson().toJson(checkSet));

        Intent intent = new Intent();

        if (single) {
            intent.putExtra("path", checkSet.iterator().next());
        } else {
            intent.putExtra("paths", checkSet.toArray(new String[0]));
        }

        setResult(
                RESULT_OK,
                intent
        );
        finish();
    }

}
