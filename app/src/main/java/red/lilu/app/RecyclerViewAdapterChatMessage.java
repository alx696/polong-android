package red.lilu.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.util.Size;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import red.lilu.app.databinding.RecyclerViewChatMessageBinding;
import red.lilu.app.databinding.RecyclerViewChatMessageSelfBinding;

public class RecyclerViewAdapterChatMessage extends RecyclerView.Adapter<RecyclerViewAdapterChatMessage.ViewHolder> {
    private static final String T = "调试";
    private final HashSet<String> videoExtensionSet = Sets.newHashSet("mp4");
    private static final ArrayList<KcAPI.ChatMessage> dataList = new ArrayList<>();
    private final MyApplication application;
    private final java.util.function.Consumer<String> onFileShare;
    private final java.util.function.Consumer<String> onFileView;
    private final Callback callback;
    private final ClipboardManager clipboardManager;
    private String myID;
    private Bitmap myPhotoBitmap, targetPhotoBitmap;

    interface Callback {
        void onRecyclerViewAdapterChatMessageCopyFileToPublicDownload(String filePath);

        void onRecyclerViewAdapterChatMessageSend(String text, @Nullable String path);

        void onRecyclerViewAdapterChatMessageDelete(long id);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ViewBinding b;
        final ImageView photo;
        final TextView state;
        final LinearLayout layoutContent;
        final TextView text;
        final LinearLayout layoutFile;
        final ImageView imagePreview;
        final StyledPlayerView videoPreview;

        public ViewHolder(ViewBinding b) {
            super(b.getRoot());
            this.b = b;

            photo = b.getRoot().findViewById(R.id.image_photo);
            state = b.getRoot().findViewById(R.id.text_state);
            layoutContent = b.getRoot().findViewById(R.id.layout_content);
            text = b.getRoot().findViewById(R.id.text_text);
            layoutFile = b.getRoot().findViewById(R.id.layout_file);
            imagePreview = b.getRoot().findViewById(R.id.image_preview);
            videoPreview = b.getRoot().findViewById(R.id.video_preview);
        }
    }

    public RecyclerViewAdapterChatMessage(MyApplication application,
                                          java.util.function.Consumer<String> onFileShare,
                                          java.util.function.Consumer<String> onFileView,
                                          Callback callback) {
        this.application = application;
        this.onFileShare = onFileShare;
        this.onFileView = onFileView;
        this.callback = callback;

        clipboardManager = (ClipboardManager) application.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public int getItemViewType(int position) {
        if (dataList.get(position).fromPeerID.equals(myID)) {
            return 1;
        } else {
            return 2;
        }
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        if (viewType == 1) {
            return new ViewHolder(
                    RecyclerViewChatMessageSelfBinding.inflate(
                            LayoutInflater.from(viewGroup.getContext()),
                            viewGroup,
                            false
                    )
            );
        } else {
            return new ViewHolder(
                    RecyclerViewChatMessageBinding.inflate(
                            LayoutInflater.from(viewGroup.getContext()),
                            viewGroup,
                            false
                    )
            );
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, final int position) {
        KcAPI.ChatMessage data = dataList.get(position);

        // 显示头像
        if (data.fromPeerID.equals(myID) && myPhotoBitmap != null) {
            GlideApp.with(application)
                    .load(myPhotoBitmap)
                    .circleCrop()
                    .into(h.photo);
        } else if (!data.fromPeerID.equals(myID) && targetPhotoBitmap != null) {
            GlideApp.with(application)
                    .load(targetPhotoBitmap)
                    .circleCrop()
                    .into(h.photo);
        } else {
            h.photo.setImageResource(R.drawable.ic_image);
        }

        // 显示状态内容
        h.state.setText(data.state);
        // 调整状态颜色
        int stateColor = Color.rgb(255, 179, 0);
        if (data.state.equals("失败")) {
            stateColor = Color.rgb(200, 0, 0);
        } else if (data.state.equals("完成")) {
            stateColor = Color.rgb(204, 204, 204);
        }
        h.state.setTextColor(stateColor);
        // 显示或隐藏状态信息
        if (data.state.equals("完成")) {
            h.state.setVisibility(View.GONE);
        } else {
            h.state.setVisibility(View.VISIBLE);
        }

        // 显示内容
        h.layoutContent.setOnCreateContextMenuListener(null);
        h.text.setVisibility(View.GONE);
        h.layoutFile.setVisibility(View.GONE);
        h.imagePreview.setVisibility(View.GONE);
        h.videoPreview.setVisibility(View.GONE);
        h.videoPreview.setPlayer(null);
        if (data.file_size > 0) {
            h.layoutFile.setVisibility(View.VISIBLE);
            TextView fileNameView = h.layoutFile.findViewById(R.id.text_file_name);
            TextView fileSizeView = h.layoutFile.findViewById(R.id.text_file_size);
            fileNameView.setText(data.file_name);
            fileSizeView.setText(Tool.byteCountToDisplaySize(data.file_size));

            // 文件路径
            File dataFile = new File(data.file_path);
            if (!dataFile.exists()) {
                fileNameView.setTextColor(Color.rgb(200, 0, 0));
            } else {
                fileNameView.setTextColor(Color.rgb(0, 0, 0));

                // 显示预览
                if (data.state.equals("完成")) {
                    // 图片预览
                    if (MyApplication.imageExtensionSet.contains(data.file_extension.toLowerCase())) {
                        showImagePreview(dataFile, h.imagePreview);
                    }
                    // TODO 存在OOM问题!
//                // 视频预览
//                if (videoExtensionSet.contains(data.fileExtension.toLowerCase())) {
//                    showVideoPreview(dataFile, h.videoPreview);
//                }
                }

                h.layoutContent.setOnClickListener(v -> {
                    if (MyApplication.imageExtensionSet.contains(data.file_extension.toLowerCase())
                            || videoExtensionSet.contains(data.file_extension.toLowerCase())) {
                        onFileView.accept(
                                dataFile.getAbsolutePath()
                        );
                    } else {
                        onFileShare.accept(
                                dataFile.getAbsolutePath()
                        );
                    }
                });
            }

            h.layoutContent.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                @Override
                public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                    if (dataFile.exists() && data.state.equals("失败")) {
                        menu.add(0, 10, 10, "重发")
                                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(MenuItem item) {
                                        callback.onRecyclerViewAdapterChatMessageSend("", dataFile.getAbsolutePath());

                                        return true;
                                    }
                                });
                    }

                    if (dataFile.exists() && data.state.equals("完成")) {
                        menu.add(0, 20, 20, "分享")
                                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(MenuItem item) {
                                        onFileShare.accept(
                                                dataFile.getAbsolutePath()
                                        );

                                        return true;
                                    }
                                });
                        menu.add(0, 30, 30, "复制到下载文件夹")
                                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(MenuItem item) {
                                        callback.onRecyclerViewAdapterChatMessageCopyFileToPublicDownload(dataFile.getAbsolutePath());

                                        return true;
                                    }
                                });
                    }

                    if (data.state.equals("失败") || data.state.equals("完成")) {
                        menu.add(0, 40, 40, "删除")
                                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(MenuItem item) {
                                        callback.onRecyclerViewAdapterChatMessageDelete(data.id);

                                        return true;
                                    }
                                });
                    }
                }
            });
        } else {
            h.text.setVisibility(View.VISIBLE);
            h.text.setText(data.text);

            h.layoutContent.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                @Override
                public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                    if (data.state.equals("失败")) {
                        menu.add(0, 10, 10, "重发")
                                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                                    @Override
                                    public boolean onMenuItemClick(MenuItem item) {
                                        callback.onRecyclerViewAdapterChatMessageSend(data.text, null);

                                        return true;
                                    }
                                });
                    }

                    menu.add(0, 20, 20, "复制")
                            .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                                @Override
                                public boolean onMenuItemClick(MenuItem item) {
                                    ClipData clipData = ClipData.newPlainText(
                                            "复制文字",
                                            data.text
                                    );
                                    clipboardManager.setPrimaryClip(clipData);
                                    Toast.makeText(application.getApplicationContext(), "已经复制", Toast.LENGTH_SHORT).show();

                                    return true;
                                }
                            });

                    menu.add(0, 30, 30, "删除")
                            .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                                @Override
                                public boolean onMenuItemClick(MenuItem item) {
                                    callback.onRecyclerViewAdapterChatMessageDelete(data.id);

                                    return true;
                                }
                            });
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    private Size limitPreviewSize(float width, float height) {
        float limit = application.getResources().getDisplayMetrics().density * 240;
        if (width > limit) {
            //缩小宽度
            height = limit / width * height;
            width = limit;
        }
        if (height > limit) {
            //缩小高度
            width = limit / height * width;
            height = limit;
        }
        return new Size((int) width, (int) height);
    }

    private void showImagePreview(File file, ImageView view) {
        //三星SM-G9500拍的照片BitmapFactory.Options得到的宽高是颠倒的, 必须读取Exif信息修正.
        int orientation = 1;
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            String attribute = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
            if (attribute != null) {
                orientation = Integer.parseInt(attribute);
            }
        } catch (IOException e) {
            Log.w(T, e);
        }

        //读取图片大小
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        //按图片大小比例调整预览视图大小
        Size size;
        if (orientation == 6 || orientation == 8) {
            //三星SM-G9500拍的照片修正宽高
            size = limitPreviewSize(options.outHeight, options.outWidth);
        } else {
            size = limitPreviewSize(options.outWidth, options.outHeight);
        }
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        layoutParams.width = size.getWidth();
        layoutParams.height = size.getHeight();
        view.setLayoutParams(layoutParams);
        view.setVisibility(View.VISIBLE);

        //显示图片
        Glide.with(application.getApplicationContext())
                .load(file)
                .into(view);
    }

    private void showVideoPreview(File file, StyledPlayerView view) {
        view.setVisibility(View.VISIBLE);
        SimpleExoPlayer player = new SimpleExoPlayer.Builder(application.getApplicationContext()).build();
        view.setPlayer(player);
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onVideoSizeChanged(@NonNull EventTime eventTime, int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                // https://github.com/google/ExoPlayer/issues/4729#issuecomment-416862649
                Size size = limitPreviewSize(width, height);
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                layoutParams.width = size.getWidth();
                layoutParams.height = size.getHeight();
                view.setLayoutParams(layoutParams);
            }
        });
        MediaItem mediaItem = MediaItem.fromUri(
                Uri.fromFile(file)
        );
        player.setMediaItem(mediaItem);
        player.prepare();
    }

    private int getPosition(long id) {
        int position = -1;
        for (KcAPI.ChatMessage info : dataList) {
            position++;

            if (info.id == id) {
                return position;
            }
        }
        return position;
    }

    /**
     * 设置数据
     */
    public void set(String myID, Bitmap myPhotoBitmap, Bitmap targetPhotoBitmap, ArrayList<KcAPI.ChatMessage> list) {
        this.myID = myID;
        this.myPhotoBitmap = myPhotoBitmap;
        this.targetPhotoBitmap = targetPhotoBitmap;
        dataList.clear();
        dataList.addAll(list);
        notifyDataSetChanged();
    }

    /**
     * 更新对方头像
     */
    public void updateTargetBitmap(Bitmap targetPhotoBitmap) {
        this.targetPhotoBitmap = targetPhotoBitmap;
        notifyDataSetChanged();
    }

    /**
     * 清空
     */
    public void clear() {
        dataList.clear();
        notifyDataSetChanged();
    }

    /**
     * 增加
     */
    public void add(KcAPI.ChatMessage data) {
        dataList.add(data);
        notifyItemInserted(dataList.size() - 1);
    }

    /**
     * 删除
     */
    public void delete(long id) {
        int position = getPosition(id);
        if (position != -1) {
            dataList.remove(position);
            notifyItemRemoved(position);
        }
    }

    /**
     * 更新状态
     */
    public void updateState(long id, String state) {
        int position = getPosition(id);
        if (position != -1) {
            dataList.get(position).state = state;
            notifyItemChanged(position);
        }
    }
}
