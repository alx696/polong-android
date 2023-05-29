package red.lilu.app;

import android.util.Log;

import java.util.concurrent.CompletableFuture;

import qc.Qc;

public class QcAPI {

    private static final String T = "调试";

    /**
     * 生成二维码
     *
     * @param path 文件保存路径
     * @param text 文字
     */
    public static void encode(String path, String text,
                              java.util.concurrent.Executor executor,
                              java.util.function.Consumer<String> onError,
                              java.util.function.Consumer<String> onDone) {
        CompletableFuture.runAsync(() -> {
            try {
                Qc.encode(path, text);

                onDone.accept("");
            } catch (Exception e) {
                Log.w(T, e);
                onError.accept(e.getMessage());
            }
        }, executor);
    }

}
