package red.lilu.app;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;

public class RTCVideoDecoder {

    private static final String T = "调试";
    private static final String VideoMimeType = MediaFormat.MIMETYPE_VIDEO_AVC;
    private MediaCodec videoDecodeMediaCodec;

    public RTCVideoDecoder(int width, int height,
                           ByteBuffer csd0ByteBuffer,
                           SurfaceTexture targetSurfaceTexture,
                           java.util.function.Consumer<String> onError) {
        Log.i(T, "视频解码器开始工作");
        try {
            // 创建解码格式时必须设置CSD, 否则画面无法正常显示
            // https://android.googlesource.com/platform/cts/+/jb-mr2-release/tests/tests/media/src/android/media/cts/EncodeDecodeTest.java#573
            MediaFormat videoDecodeFormat = MediaFormat.createVideoFormat(VideoMimeType, width, height);
            videoDecodeFormat.setByteBuffer("csd-0", csd0ByteBuffer);
//            // 下面方法只有在录制端是竖屏状态时能够保持播放方向与录制端一致, 横屏状态时方向总是差了180度. 调整输出Surface效果更好.
//            videoDecodeFormat.setInteger(MediaFormat.KEY_ROTATION, orientation);
            // 创建视频解码器
            videoDecodeMediaCodec = MediaCodec.createDecoderByType(VideoMimeType);
            videoDecodeMediaCodec.configure(videoDecodeFormat, new Surface(targetSurfaceTexture), null, 0);
            videoDecodeMediaCodec.start();
        } catch (Exception e) {
            Log.w(T, e);
            onError.accept(e.getMessage());
        }
    }

    public void decode(byte[] data, long presentationTimeUs) {
        try {
            int decodeInputBufferIndex = videoDecodeMediaCodec.dequeueInputBuffer(-1);
            ByteBuffer decodeByteBuffer = videoDecodeMediaCodec.getInputBuffer(decodeInputBufferIndex);
            decodeByteBuffer.clear();
            decodeByteBuffer.put(data);
            videoDecodeMediaCodec.queueInputBuffer(decodeInputBufferIndex, 0, data.length, presentationTimeUs, 0);
            // 显示图像
            // https://blog.csdn.net/bit_kaki/article/details/52105733
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = videoDecodeMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            while (outputBufferIndex >= 0) {
                videoDecodeMediaCodec.releaseOutputBuffer(outputBufferIndex, true);
                outputBufferIndex = videoDecodeMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Exception e) {
            // 停止时容易产生IllegalStateException异常, 忽略.
//            Log.w(T, e);
        }
    }

    public void stop() {
        Log.i(T, "视频解码器停止工作");
        try {
            if (videoDecodeMediaCodec != null) {
                videoDecodeMediaCodec.stop();
                videoDecodeMediaCodec.release();
                videoDecodeMediaCodec = null;
            }
        } catch (Exception e) {
            Log.w(T, e);
        }
    }
}
