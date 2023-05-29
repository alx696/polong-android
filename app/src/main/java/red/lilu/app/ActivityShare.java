package red.lilu.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.HashSet;

import red.lilu.app.databinding.ActivityShareBinding;
import red.lilu.app.databinding.RecyclerViewShareBinding;

public class ActivityShare extends AppCompatActivity {

    private static final String T = "调试";
    private ActivityShareBinding b;
    private static MyApplication application;
    private static RecyclerViewAdapterMy adapter;
    private String myID;
    private Uri uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityShareBinding.inflate(getLayoutInflater());
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

        // 开始业务
        init();
    }

    private class RecyclerViewAdapterMy extends RecyclerView.Adapter<RecyclerViewAdapterMy.ViewHolder> {

        private ArrayList<KcAPI.Contact> list = new ArrayList<>();
        private HashSet<String> connectedSet = new HashSet<>(); //连接状态节点ID

        public class ViewHolder extends RecyclerView.ViewHolder {
            final RecyclerViewShareBinding b;

            public ViewHolder(RecyclerViewShareBinding b) {
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
                    RecyclerViewShareBinding.inflate(
                            LayoutInflater.from(viewGroup.getContext()),
                            viewGroup,
                            false
                    )
            );
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, final int position) {
            KcAPI.Contact data = list.get(position);

            // 显示头像
            if (!data.photo.isEmpty()) {
                GlideApp.with(application)
                        .load(
                                Base64.decode(data.photo, Base64.NO_WRAP)
                        )
                        .circleCrop()
                        .into(h.b.imagePhoto);
            }

            // 显示名字
            String name = data.name;
            if (!data.nameRemark.isEmpty()) {
                name = data.nameRemark;
            }
            h.b.textName.setText(name);

            // 显示文字状态
            h.b.textState.setText(data.id.substring(data.id.length() - 4));

            // 显示连接状态
            h.b.imageConnectState.setImageResource(R.drawable.ic_link_off);
            if (connectedSet.contains(data.id)) {
                h.b.imageConnectState.setImageResource(R.drawable.ic_link);
            }

            h.b.getRoot().setOnClickListener(v -> {
                Log.i(T, "选择联系人:" + data.id);

                // 打开会话界面
                Intent intent = new Intent(getApplicationContext(), ActivityChat.class);
                intent.putExtra("myID", myID);
                intent.putExtra("targetID", data.id);
                intent.putExtra("uri", uri);
                startActivity(intent);

                finish();
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        public void setList(ArrayList<KcAPI.Contact> list) {
            this.list = list;
            notifyDataSetChanged();
        }

        public void setConnected(HashSet<String> connectedSet) {
            this.connectedSet = connectedSet;
            notifyDataSetChanged();
        }

    }

    private void init() {
        myID = getIntent().getStringExtra("myID");
        uri = getIntent().getParcelableExtra("uri");
        Log.i(T, "分享文件:" + uri);

        KcAPI.getContact(
                application,
                error -> {
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                        finish();
                    });
                },
                result -> {
                    ArrayList<KcAPI.Contact> list = Lists.newArrayList(result.values());
//                    Log.d(T, "联系人:" + application.getGson().toJson(list));

                    runOnUiThread(() -> {
                        adapter.setList(list);
                    });

                    updateConnect();
                }
        );
    }

    private void updateConnect() {
        KcAPI.getConnectedPeerIDs(
                application,
                error -> {
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                        finish();
                    });
                },
                connectedPeerIDs -> {
                    HashSet<String> set = Sets.newHashSet(connectedPeerIDs);
//                    Log.d(T, "连接:" + application.getGson().toJson(set));

                    runOnUiThread(() -> {
                        adapter.setConnected(set);
                    });
                }
        );
    }

}
