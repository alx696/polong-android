package red.lilu.app;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * 注意:悬浮窗口只在onCreate才能显示
 */
public class ServiceSystemAlertWindow extends Service {

    private static final String T = "调试";
    private WindowManager windowManager;
    private ViewGroup windowView;
    private TextView textViewTitle, textViewContent;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (!Settings.canDrawOverlays(getApplicationContext())) {
            Log.w(T, "没有显示悬浮窗口权限");
            return;
        }

        // To obtain a WindowManager of a different Display,
        // we need a Context for that display, so WINDOW_SERVICE is used
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // A LayoutInflater instance is created to retrieve the
        // LayoutInflater for the floating_layout xml
        LayoutInflater inflater = (LayoutInflater) getBaseContext().getSystemService(LAYOUT_INFLATER_SERVICE);

        // inflate a new view hierarchy from the floating_layout xml
        windowView = (ViewGroup) inflater.inflate(R.layout.system_alert_window, null);
        // find view
        textViewTitle = windowView.findViewById(R.id.text_title);
        textViewContent = windowView.findViewById(R.id.text_content);

        int layoutType;
        // WindowManager.LayoutParams takes a lot of parameters to set the
        // the parameters of the layout. One of them is Layout_type.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // If API Level is more than 26, we need TYPE_APPLICATION_OVERLAY
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            // If API Level is lesser than 26, then we can
            // use TYPE_SYSTEM_ERROR,
            // TYPE_SYSTEM_OVERLAY, TYPE_PHONE, TYPE_PRIORITY_PHONE.
            // But these are all
            // deprecated in API 26 and later. Here TYPE_TOAST works best.
            // 小米4c不支持TYPE_TOAST
            layoutType = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        }

        // The screen height and width are calculated, cause
        // the height and width of the floating window is set depending on this
        DisplayMetrics metrics = getApplicationContext().getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        // Now the Parameter of the floating-window layout is set.
        // 1) The Width of the window will be 55% of the phone width.
        // 2) The Height of the window will be 58% of the phone height.
        // 3) Layout_Type is already set.
        // 4) Next Parameter is Window_Flag. Here FLAG_NOT_FOCUSABLE is used. But
        // problem with this flag is key inputs can't be given to the EditText.
        // This problem is solved later.
        // 5) Next parameter is Layout_Format. System chooses a format that supports
        // translucency by PixelFormat.TRANSLUCENT
        WindowManager.LayoutParams windowLayoutParam = new WindowManager.LayoutParams(
                (int) (width * (0.6f)),
                WindowManager.LayoutParams.WRAP_CONTENT, // (int) (height * (0.58f))
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        // The Gravity of the Floating Window is set.
        // The Window will appear in the center of the screen
        windowLayoutParam.gravity = Gravity.CENTER;

        // X and Y value of the window is set
        windowLayoutParam.x = 0;
        windowLayoutParam.y = 0;

        // The ViewGroup that inflates the floating_layout.xml is
        // added to the WindowManager with all the parameters
        windowManager.addView(windowView, windowLayoutParam);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        textViewTitle.setText(
                intent.getStringExtra("title")
        );
        textViewContent.setText(
                intent.getStringExtra("content")
        );

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (windowView != null) {
            windowManager.removeViewImmediate(windowView);
        }

        stopSelf();
    }
}
