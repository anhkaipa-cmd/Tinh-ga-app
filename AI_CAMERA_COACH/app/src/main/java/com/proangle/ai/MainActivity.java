package com.proangle.ai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 2001;
    private static final int REQ_CAPTURE = 2002;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(40), dp(24), dp(28));
        root.setBackgroundColor(Color.rgb(9, 10, 13));

        TextView title = new TextView(this);
        title.setText("ProAngle AI");
        title.setTextColor(Color.WHITE);
        title.setTextSize(31f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(-1, -2, 0, 0, 0, dp(6)));

        TextView sub = new TextView(this);
        sub.setText("NHIẾP ẢNH GIA AI TRÊN CAMERA VIVO");
        sub.setTextColor(Color.rgb(95, 255, 120));
        sub.setTextSize(12f);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, lp(-1, -2, 0, 0, 0, dp(28)));

        TextView explain = new TextView(this);
        explain.setText("Bản v0.2 không dùng camera riêng. AI Coach sẽ chạy nổi trực tiếp trên Camera Vivo gốc, đọc bố cục từ màn hình và chỉ bạn xoay trái/phải, tiến/lùi, cân máy rồi mới chụp.\n\nLần đầu cần cho phép:\n1. Hiển thị trên ứng dụng khác\n2. Chia sẻ toàn bộ màn hình");
        explain.setTextColor(Color.rgb(225, 228, 234));
        explain.setTextSize(16f);
        explain.setLineSpacing(0f, 1.18f);
        root.addView(explain, lp(-1, -2, 0, 0, 0, dp(26)));

        Button start = new Button(this);
        start.setText("BẬT AI COACH + MỞ CAMERA VIVO");
        start.setTextSize(16f);
        start.setTextColor(Color.BLACK);
        start.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(95, 255, 120));
        bg.setCornerRadius(dp(20));
        start.setBackground(bg);
        start.setOnClickListener(v -> startCoachFlow());
        root.addView(start, lp(-1, dp(62), 0, 0, 0, dp(18)));

        TextView note = new TextView(this);
        note.setText("Nếu Camera Vivo chặn việc đọc preview, AI sẽ báo “Không đọc được preview Vivo”. Khi đó ảnh vẫn chụp bằng camera gốc, còn mình sẽ đổi cách lấy bố cục ở bản sau.");
        note.setTextColor(Color.rgb(150, 155, 165));
        note.setTextSize(13f);
        note.setLineSpacing(0f, 1.15f);
        root.addView(note, lp(-1, -2, 0, 0, 0, 0));

        setContentView(root);
    }

    private void startCoachFlow() {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
            return;
        }
        requestScreenCapture();
    }

    private void requestScreenCapture() {
        if (projectionManager == null) {
            Toast.makeText(this, "Không tìm thấy chức năng chia sẻ màn hình.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent captureIntent;
        if (Build.VERSION.SDK_INT >= 34) {
            captureIntent = projectionManager.createScreenCaptureIntent(
                    MediaProjectionConfig.createConfigForDefaultDisplay());
        } else {
            captureIntent = projectionManager.createScreenCaptureIntent();
        }
        startActivityForResult(captureIntent, REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                requestScreenCapture();
            } else {
                Toast.makeText(this, "Cần quyền hiển thị nổi để AI nằm trên Camera Vivo.", Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (requestCode == REQ_CAPTURE) {
            if (resultCode != RESULT_OK || data == null) {
                Toast.makeText(this, "Cần cho phép chia sẻ màn hình để AI xem bố cục của Camera Vivo.", Toast.LENGTH_LONG).show();
                return;
            }

            Intent service = new Intent(this, OverlayCoachService.class);
            service.putExtra(OverlayCoachService.EXTRA_RESULT_CODE, resultCode);
            service.putExtra(OverlayCoachService.EXTRA_RESULT_DATA, data);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }

            Toast.makeText(this, "AI Coach đã bật. Đang mở Camera Vivo gốc…", Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(this::openOriginalCamera, 650);
        }
    }

    private void openOriginalCamera() {
        try {
            Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            if (i.resolveActivity(getPackageManager()) == null) {
                i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            }
            if (i.resolveActivity(getPackageManager()) != null) {
                startActivity(i);
            } else {
                Toast.makeText(this, "Không tìm thấy Camera Vivo gốc.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Không mở được camera gốc: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
