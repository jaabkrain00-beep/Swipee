package com.swipeclean.app;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PhotoManagerPlugin.class);
        super.onCreate(savedInstanceState);
        try {
            enableFullScreen();
        } catch (Throwable t) {
            // ถ้าตั้งเต็มจอไม่ได้ ให้แอปทำงานต่อได้ตามปกติ
        }
    }

    /** ให้เว็บวาดเต็มจอ ใต้แถบสถานะ/รอยบากกล้อง (ระยะขอบเว็บจะอ่านค่าผ่าน getInsets) */
    private void enableFullScreen() {
        Window w = getWindow();
        WindowCompat.setDecorFitsSystemWindows(w, false);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
        if (c != null) {
            c.setAppearanceLightStatusBars(true);
            c.setAppearanceLightNavigationBars(true);
        }
    }
}
