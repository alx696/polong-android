package red.lilu.app;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis.Analyzer;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

import qc.Qc;

// https://github.com/googlesamples/glass-enterprise-samples/blob/master/QRCodeScannerSample/app/src/main/java/com/example/glass/qrcodescannersample/QRCodeImageAnalysis.java
public class ImageAnalyzerQrCode implements Analyzer {

    private static final String T = "调试";
    private final ImageAnalyzerQrCodeCallback callback;

    public ImageAnalyzerQrCode(ImageAnalyzerQrCodeCallback callback) {
        this.callback = callback;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        final ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        final byte[] imageBytes = new byte[buffer.remaining()];
        buffer.get(imageBytes);
        final int width = image.getWidth();
        final int height = image.getHeight();

//        Log.d(T, String.format("大小: %d, 尺寸: %d,%d", imageBytes.length, width, height));
        /*
        一加3T测试时不论使用com.google.zxing:core:3.4.1还是go版本都不能成功解码, 原因是可能的中心数量为0:
        https://github.com/makiuchi-d/gozxing/blob/25f730ed83da0092eeb43c1ef94d114438660702/qrcode/detector/finder_pattern_finder.go#L16

        比对没有发现明显异常, 只是图片尺寸一加是1080,1080, 比三星和小米都大.
         */

        try {
            callback.onImageAnalyzerQrCodeResult(
                    Qc.decodeYUV(imageBytes, width, height)
            );
        } catch (Exception e) {
//            Log.w(T, e);
            image.close(); //解码失败时才销毁图像继续下个解码, 防止界面关闭前反复解码.
        }
    }

    interface ImageAnalyzerQrCodeCallback {
        void onImageAnalyzerQrCodeResult(String result);
    }
}
