package red.lilu.app;

import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import red.lilu.app.databinding.RecyclerViewContactBinding;

public class RecyclerViewAdapterContact extends RecyclerView.Adapter<RecyclerViewAdapterContact.ViewHolder> {
    private static final String T = "调试";
    private ArrayList<KcAPI.Contact> dataList = new ArrayList<>();
    private HashSet<String> connectedSet = new HashSet<>(); //连接状态节点ID
    private HashMap<String, Long> messageCountMap = new HashMap<>(); //未读消息数量
    private final MyApplication application;
    private final Callback callback;

    public class ViewHolder extends RecyclerView.ViewHolder {
        final RecyclerViewContactBinding b;

        public ViewHolder(RecyclerViewContactBinding b) {
            super(b.getRoot());
            this.b = b;

            b.getRoot().setOnClickListener(v -> callback.onRecyclerViewAdapterPeerInfoClick(
                    dataList.get(getAdapterPosition())
            ));
        }
    }

    interface Callback {
        void onRecyclerViewAdapterPeerInfoClick(KcAPI.Contact data);
    }

    public RecyclerViewAdapterContact(MyApplication application, Callback callback) {
        this.application = application;
        this.callback = callback;
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        return new ViewHolder(
                RecyclerViewContactBinding.inflate(
                        LayoutInflater.from(viewGroup.getContext()),
                        viewGroup,
                        false
                )
        );
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, final int position) {
        KcAPI.Contact data = dataList.get(position);

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

        // 显示消息数量
        long messageCount = 0;
        if (messageCountMap.containsKey(data.id)) {
            messageCount = messageCountMap.get(data.id);
        }
        if (messageCount == 0) {
            h.b.layoutMessageCount.setVisibility(View.GONE);
        } else {
            h.b.textMessageCount.setText(
                    messageCount > 9 ? "9+" : String.valueOf(messageCount)
            );
            h.b.layoutMessageCount.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    /**
     * 设置数据
     */
    public void set(ArrayList<KcAPI.Contact> dataList,
                    HashSet<String> connectedSet,
                    HashMap<String, Long> messageCountMap) {
        this.dataList = dataList;
        this.connectedSet = connectedSet;
        this.messageCountMap = messageCountMap;
        notifyDataSetChanged();
    }

    private int getPosition(String id) {
        int position = -1;
        for (KcAPI.Contact info : dataList) {
            position++;

            if (info.id.equals(id)) {
                return position;
            }
        }
        return position;
    }

    /**
     * 更新连接状态
     */
    public void updateConnect(String id, boolean isConnect) {
        if (isConnect) {
            connectedSet.add(id);
        } else {
            connectedSet.remove(id);
        }

        int position = getPosition(id);
        if (position != -1) {
            notifyItemChanged(position);
        }
    }

    /**
     * 增加消息数量
     */
    public void increaseMessageCount(String id) {
        long count = 0;
        if (messageCountMap.containsKey(id)) {
            count = messageCountMap.get(id);
        }
        count++;
        messageCountMap.put(id, count);

        int position = getPosition(id);
        if (position != -1) {
            notifyItemChanged(position);
        }
    }

    /**
     * 重置消息数量
     */
    public void resetMessageCount(String id) {
        messageCountMap.put(id, 0L);
        int position = getPosition(id);
        if (position != -1) {
            notifyItemChanged(position);
        }
    }

}
