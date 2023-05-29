package red.lilu.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

import red.lilu.app.databinding.ActivityQrcodeBinding;

public class ActivityQrCode extends AppCompatActivity implements ImageAnalyzerQrCode.ImageAnalyzerQrCodeCallback {

    private static final String T = "调试";
    private static final int PERMISSION_REQUEST_CODE_CAMERA = 1;
    private ActivityQrcodeBinding b;
    private MyApplication application;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityQrcodeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        application = (MyApplication) getApplication();

        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            getCameraProvide();
        } else {
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    PERMISSION_REQUEST_CODE_CAMERA
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE_CAMERA) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCameraProvide();
            } else {
                Toast.makeText(getApplicationContext(), "没有相机权限!", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    void getCameraProvide() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCamera(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    void bindCamera(@NonNull ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        Size size = new Size(b.preview.getWidth(), b.preview.getHeight());

        Preview preview = new Preview.Builder()
                .setTargetResolution(size)
                .build();
        preview.setSurfaceProvider(b.preview.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(size)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(application.getExecutorService(), new ImageAnalyzerQrCode(this));

        // https://developer.android.com/training/camerax/configuration
        ViewPort viewPort = b.preview.getViewPort();
        UseCaseGroup useCaseGroup = new UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageAnalysis)
                .setViewPort(viewPort)
                .build();

        Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, useCaseGroup);
    }

    @Override
    public void onImageAnalyzerQrCodeResult(String result) {
        Log.d(T, "解码:" + result);

        Intent intent = new Intent();
        intent.putExtra("text", result);
        setResult(RESULT_OK, intent);

        finish();
    }
}
