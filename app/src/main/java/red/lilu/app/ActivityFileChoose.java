package red.lilu.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Ordering;
import com.google.common.io.Files;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

import red.lilu.app.databinding.ActivityFileChooseBinding;
import red.lilu.app.databinding.RecyclerViewFileChooseBinding;

public class ActivityFileChoose extends AppCompatActivity {

    private static final String T = "调试";
    private static final int REQUEST_CODE_PERMISSION = 1;
    private ActivityFileChooseBinding b;
    private static MyApplication application;
    private static RecyclerViewAdapterMy adapter;
    private static File directory;
    private static File lastDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityFileChooseBinding.inflate(getLayoutInflater());
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
        adapter = new RecyclerViewAdapterMy();
        b.recyclerView.setAdapter(adapter);

        checkPermission();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.file_choose, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.close) {
            finish();
        } else if (id == R.id.done) {
            if (adapter.getCheck().size() == 0) {
                Toast.makeText(getApplicationContext(), "没有选择文件！", Toast.LENGTH_LONG).show();

                return super.onOptionsItemSelected(item);
            }

            Intent intent = new Intent();
            intent.putExtra("paths", TextUtils.join(",", adapter.getCheck()));
            setResult(RESULT_OK, intent);
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                init();
            } else {
                new MaterialAlertDialogBuilder(ActivityFileChoose.this)
                        .setTitle("信任危机")
                        .setMessage("即如此，那再见！")
                        .setPositiveButton("已读", (dialog, which) -> {
                            finish();
                        })
                        .show();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //拦截返回(按返回键, 点返回按钮)
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            //禁止返回

            if (lastDir == null || lastDir.getAbsolutePath().equals(directory.getAbsolutePath())) {
                finish();
            } else {
                showDirectory(lastDir.getParentFile());
            }

            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    static class Data {
        String path, type, name, info;

        public Data(String path, String type, String name, String info) {
            this.path = path;
            this.type = type;
            this.name = name;
            this.info = info;
        }
    }

    private static final Ordering<Data> orderingName = new Ordering<Data>() {
        @Override
        public int compare(@NullableDecl Data left, @NullableDecl Data right) {
            return left.name.toLowerCase().compareTo(right.name.toLowerCase());
        }
    };

    private static class RecyclerViewAdapterMy extends RecyclerView.Adapter<RecyclerViewAdapterMy.ViewHolder> {

        private ArrayList<Data> list = new ArrayList<>();
        private final HashSet<String> set = new HashSet<>();

        public static class ViewHolder extends RecyclerView.ViewHolder {
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
        public RecyclerViewAdapterMy.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
            return new RecyclerViewAdapterMy.ViewHolder(
                    RecyclerViewFileChooseBinding.inflate(
                            LayoutInflater.from(viewGroup.getContext()),
                            viewGroup,
                            false
                    )
            );
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerViewAdapterMy.ViewHolder h, final int position) {
            Data data = list.get(position);

            h.b.textName.setText(data.name);
            h.b.textInfo.setText(data.info);

            if (set.contains(data.path)) {
                h.b.imageCheck.setImageResource(R.drawable.ic_check_box);
            } else {
                h.b.imageCheck.setImageResource(R.drawable.ic_check_box_outline);
            }

            Glide.with(application.getApplicationContext())
                    .clear(h.b.imagePreview);
            if (data.type.equals("目录")) {
                h.b.imagePreview.setImageResource(R.drawable.ic_folder);

                h.b.imageCheck.setVisibility(View.INVISIBLE);

                h.b.getRoot().setOnClickListener(v -> {
                    showDirectory(new File(data.path));
                });
            } else {
                if (data.type.equals("图片")) {
                    Glide.with(application.getApplicationContext())
                            .load(data.path)
                            .into(h.b.imagePreview);
                } else {
                    h.b.imagePreview.setImageResource(R.drawable.ic_insert_drive_file);
                }

                h.b.imageCheck.setVisibility(View.VISIBLE);

                h.b.getRoot().setOnClickListener(v -> {
                    if (set.contains(data.path)) {
                        //取消选择
                        set.remove(data.path);
                        h.b.imageCheck.setImageResource(R.drawable.ic_check_box_outline);
                    } else {
                        set.add(data.path);
                        h.b.imageCheck.setImageResource(R.drawable.ic_check_box);
                    }
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
        public void set(ArrayList<Data> list) {
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

    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE_PERMISSION
            );
        } else {
            init();
        }
    }

    private static void showDirectory(File dir) {
        lastDir = dir;

        ArrayList<Data> list = new ArrayList<>();
        for (File file : dir.listFiles()) {
            String type = "文件";
            String info = "";
            if (file.isDirectory()) {
                type = "目录";

                info = String.format(Locale.CHINA, "%d 个文件", file.list().length);
            } else {
                if (MyApplication.imageExtensionSet.contains(Files.getFileExtension(file.getName()).toLowerCase())) {
                    type = "图片";
                }

                info = String.format(Locale.CHINA, "%.2f MB", ((float) file.length()) / 1024 / 1024);
            }

            list.add(new Data(
                    file.getAbsolutePath(),
                    type,
                    file.getName(),
                    info
            ));
        }

        list.sort(orderingName);

        adapter.set(list);
    }

    private void init() {
        directory = Environment.getExternalStorageDirectory();

        showDirectory(directory);
    }

}
