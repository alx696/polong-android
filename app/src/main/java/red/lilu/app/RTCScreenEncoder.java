package red.lilu.app;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.os.HandlerCompat;

import java.nio.ByteBuffer;

/**
 * 屏幕编码
 */
public class RTCScreenEncoder {

    private static final String T = "调试";
    private static final String VideoMimeType = MediaFormat.MIMETYPE_VIDEO_AVC;
    private final MyApplication application;
    private final MediaProjection mediaProjection;
    private final java.util.function.Consumer<String> onError;
    private final Handler handler;
    private MediaCodec videoEncodeMediaCodec;
    private Surface videoPersistentInputSurface;
    private int screenDensity;
    private int screenWidth, screenHeight;

    public static class Data {
        public long presentationTimeUs;
        public byte[] bytes;

        public Data(long presentationTimeUs, byte[] bytes) {
            this.presentationTimeUs = presentationTimeUs;
            this.bytes = bytes;
        }
    }

    public RTCScreenEncoder(MyApplication application,
                            MediaProjection mediaProjection,
                            java.util.function.Consumer<String> onError,
                            java.util.function.Consumer<Data> onData) {
        this.application = application;
        this.mediaProjection = mediaProjection;
        this.onError = onError;
        this.handler = HandlerCompat.createAsync(Looper.getMainLooper());
        try {
            // 获取屏幕大小
            WindowManager windowManager = (WindowManager) application.getSystemService(Context.WINDOW_SERVICE);
            Display defaultDisplay = windowManager.getDefaultDisplay();
            final DisplayMetrics metrics = new DisplayMetrics();
            // use getMetrics is 2030, use getRealMetrics is 2160, the diff is NavigationBar's height
            defaultDisplay.getRealMetrics(metrics);
            screenDensity = metrics.densityDpi;
            screenWidth = metrics.widthPixels;//size.x;
            screenHeight = metrics.heightPixels;//size.y;
            Log.d(T, String.format("屏幕宽高: %d, %d", screenWidth, screenHeight));

            // 视频编码格式
            MediaFormat videoEncodeFormat = MediaFormat.createVideoFormat(VideoMimeType, screenWidth, screenHeight);
            videoEncodeFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface); //使用createInputSurface()创建的Surface来输入数据
            videoEncodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, 4000000); //每秒字节
            videoEncodeFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30); //每秒帧率
            videoEncodeFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); //关键帧间隔秒数

            // 选择视频编码器
            String videoEncoderName = new MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(videoEncodeFormat);
            if (videoEncoderName == null) {
                Log.w(T, "没有视频硬件编码器可用");
                onError.accept("没有视频硬件编码器可用");
                return;
            }
            Log.d(T, "可用视频硬件编码器:" + videoEncoderName);

            // 创建视频编码器
            videoEncodeMediaCodec = MediaCodec.createByCodecName(videoEncoderName);
            videoEncodeMediaCodec.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                    // 使用输入Surface时，没有可访问的输入缓冲区.
                    // https://developer.android.google.cn/reference/android/media/MediaCodec?hl=en#using-an-input-surface
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                    if (info.size <= 0) {
                        codec.releaseOutputBuffer(index, false);

                        if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                            try {
                                videoEncodeMediaCodec.stop();
                                videoEncodeMediaCodec.release();
                                videoEncodeMediaCodec = null;
                                videoPersistentInputSurface.release();
                                videoPersistentInputSurface = null;
                            } catch (Exception e) {
                                Log.d(T, e.getMessage());
                            } finally {
                                Log.i(T, "视频编码器停止工作");
                            }
                        }

                        return;
                    }

                    ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                    byte[] outputBytes = new byte[outputBuffer.remaining()];
                    outputBuffer.get(outputBytes, 0, outputBytes.length);
//                        Log.d(T, "硬件编码输出:" + outputBytes.length);

                    if (info.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                        // AAC和H264格式在合成器的首帧必须写入编解码器专用数据
                        // https://developer.android.google.cn/reference/android/media/MediaCodec?hl=en#CSD
                        Log.i(T, String.format("视频编解码器专用数据 时序:%d 大小:%d", info.presentationTimeUs, info.size));
                    }

                    // 回调数据
                    onData.accept(new Data(
                            info.presentationTimeUs,
                            outputBytes
                    ));

                    codec.releaseOutputBuffer(index, false);
                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                    Log.w(T, e);
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                    Log.i(T, "视频编码器开始工作");
                }
            }, handler);
            videoEncodeMediaCodec.configure(videoEncodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            videoPersistentInputSurface = MediaCodec.createPersistentInputSurface();
            videoEncodeMediaCodec.setInputSurface(videoPersistentInputSurface);
            videoEncodeMediaCodec.start();
        } catch (Exception e) {
            Log.w(T, e);
            onError.accept(e.getMessage());
        }
    }

    public void start() {
        try {
            mediaProjection.createVirtualDisplay(
                    "screenshot",
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    videoPersistentInputSurface,
                    null,
                    handler
            );
        } catch (Exception e) {
            Log.w(T, e);
            onError.accept(e.getMessage());
        }
    }

    public void stop() {
        if (mediaProjection != null) {
            mediaProjection.stop();
        }

        if (videoEncodeMediaCodec != null) {
            try {
                videoEncodeMediaCodec.signalEndOfInputStream();
            } catch (Exception e) {
                //忽略
            }
        }
    }
}
