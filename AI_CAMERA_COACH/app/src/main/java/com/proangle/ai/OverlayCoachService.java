package com.proangle.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.FaceDetector;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.Locale;

public class OverlayCoachService extends Service implements SensorEventListener {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final String CHANNEL_ID = "proangle_ai_coach";
    private static final int NOTIFICATION_ID = 7702;
    private static final int ANALYSIS_W = 320;
    private static final int ANALYSIS_H = 640;

    private WindowManager windowManager;
    private FrameLayout overlayRoot;
    private CoachView coachView;
    private TextView guidance;
    private TextView scoreView;
    private TextView closeView;

    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private android.hardware.display.VirtualDisplay virtualDisplay;
    private HandlerThread analysisThread;
    private Handler analysisHandler;
    private Handler mainHandler;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float smoothedRoll = 0f;

    private int screenWidth;
    private int screenHeight;
    private int densityDpi;
    private long lastAnalysisMs = 0L;
    private int darkUniformFrames = 0;

    private volatile float faceX = -1f;
    private volatile float faceY = -1f;
    private volatile float faceSize = 0f;
    private volatile float brightness = 128f;
    private volatile boolean previewBlocked = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(getMainLooper());
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
        }
        createOverlay();
        startAnalysisThread();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mediaProjection != null) return START_NOT_STICKY;
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent resultData;
        if (Build.VERSION.SDK_INT >= 33) {
            resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        } else {
            resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        }
        if (resultData == null || resultCode == 0) {
            showGuidance("KHÔNG CÓ QUYỀN ĐỌC MÀN HÌNH", 0, true);
            stopSelf();
            return START_NOT_STICKY;
        }

        setupProjection(resultCode, resultData);
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID,
                        "ProAngle AI Coach",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("AI phân tích bố cục khi Camera Vivo đang mở");
                nm.createNotificationChannel(ch);
            }
        }
    }

    private Notification buildNotification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("ProAngle AI đang hoạt động")
                .setContentText("Đang phân tích bố cục trên Camera Vivo")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
    }

    private void setupProjection(int resultCode, Intent data) {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (mpm == null) {
            showGuidance("THIẾT BỊ KHÔNG HỖ TRỢ", 0, true);
            return;
        }

        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        densityDpi = dm.densityDpi;

        mediaProjection = mpm.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            showGuidance("KHÔNG KHỞI ĐỘNG ĐƯỢC AI", 0, true);
            return;
        }
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopSelf();
            }
        }, mainHandler);

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(this::onImageAvailable, analysisHandler);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ProAngleNativeCameraCapture",
                screenWidth,
                screenHeight,
                densityDpi,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                analysisHandler);
        showGuidance("AI ĐANG TÌM CHỦ THỂ…", 42, false);
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastAnalysisMs < 450) return;
            lastAnalysisMs = now;

            Bitmap frame = imageToBitmap(image);
            if (frame == null) return;
            analyzeFrame(frame);
            frame.recycle();
        } catch (Throwable ignored) {
        } finally {
            if (image != null) image.close();
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) return null;
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;
        int bitmapWidth = screenWidth + rowPadding / pixelStride;

        Bitmap padded = Bitmap.createBitmap(bitmapWidth, screenHeight, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, screenWidth, screenHeight);
        padded.recycle();
        return cropped;
    }

    private void analyzeFrame(Bitmap frame) {
        Bitmap scaled = Bitmap.createScaledBitmap(frame, ANALYSIS_W, ANALYSIS_H, true);
        brightness = measureBrightnessAndUniformity(scaled);

        Bitmap rgb565 = Bitmap.createBitmap(ANALYSIS_W, ANALYSIS_H, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(rgb565);
        c.drawBitmap(scaled, 0, 0, null);
        scaled.recycle();

        FaceDetector detector = new FaceDetector(ANALYSIS_W, ANALYSIS_H, 3);
        FaceDetector.Face[] faces = new FaceDetector.Face[3];
        int count = detector.findFaces(rgb565, faces);

        float bestConfidence = -1f;
        FaceDetector.Face best = null;
        for (int i = 0; i < count; i++) {
            FaceDetector.Face f = faces[i];
            if (f != null && f.confidence() > bestConfidence) {
                bestConfidence = f.confidence();
                best = f;
            }
        }

        if (best != null && bestConfidence >= 0.35f) {
            PointF mid = new PointF();
            best.getMidPoint(mid);
            faceX = mid.x / ANALYSIS_W;
            faceY = mid.y / ANALYSIS_H;
            faceSize = Math.min(1f, (best.eyesDistance() * 4.2f) / ANALYSIS_H);
        } else {
            faceX = -1f;
            faceY = -1f;
            faceSize = 0f;
        }
        rgb565.recycle();

        mainHandler.post(this::updateAdvice);
    }

    private float measureBrightnessAndUniformity(Bitmap b) {
        long sum = 0;
        long sumSq = 0;
        int n = 0;
        int startY = ANALYSIS_H / 5;
        int endY = ANALYSIS_H * 4 / 5;
        for (int y = startY; y < endY; y += 20) {
            for (int x = 20; x < ANALYSIS_W - 20; x += 20) {
                int color = b.getPixel(x, y);
                int lum = (Color.red(color) * 30 + Color.green(color) * 59 + Color.blue(color) * 11) / 100;
                sum += lum;
                sumSq += (long) lum * lum;
                n++;
            }
        }
        if (n == 0) return 0f;
        float mean = sum / (float) n;
        float variance = sumSq / (float) n - mean * mean;
        if (mean < 7f && variance < 8f) {
            darkUniformFrames++;
        } else {
            darkUniformFrames = 0;
        }
        previewBlocked = darkUniformFrames >= 5;
        return mean;
    }

    private void updateAdvice() {
        if (previewBlocked) {
            showGuidance("KHÔNG ĐỌC ĐƯỢC PREVIEW VIVO", 0, true);
            coachView.setState(-1f, -1f, 0f, smoothedRoll, 0, true);
            return;
        }

        Advice a = evaluateAdvice();
        showGuidance(a.text, a.score, false);
        coachView.setState(faceX, faceY, faceSize, smoothedRoll, a.score, false);
    }

    private Advice evaluateAdvice() {
        if (Math.abs(smoothedRoll) > 3.0f) {
            int score = Math.max(38, 88 - Math.round(Math.abs(smoothedRoll) * 4f));
            String dir = smoothedRoll > 0 ? "XOAY NHẸ SANG PHẢI" : "XOAY NHẸ SANG TRÁI";
            return new Advice(score, dir + " • CÂN LẠI MÁY");
        }

        if (brightness < 34f) {
            return new Advice(55, "THIẾU SÁNG • XOAY VỀ PHÍA NGUỒN SÁNG");
        }

        if (faceX < 0f) {
            return new Advice(48, "LIA MÁY CHẬM • AI ĐANG TÌM CHỦ THỂ");
        }

        if (faceSize < 0.16f) {
            return new Advice(67, "TIẾN GẦN HƠN • HOẶC THỬ 2×");
        }
        if (faceSize > 0.46f) {
            return new Advice(70, "LÙI LẠI MỘT CHÚT • CHỪA KHÔNG GIAN");
        }

        float targetX = faceX < 0.5f ? 0.333f : 0.667f;
        float dx = faceX - targetX;
        float dy = faceY - 0.34f;

        if (Math.abs(dx) > 0.085f) {
            if (dx < 0) {
                return new Advice(76, "XOAY MÁY SANG TRÁI MỘT CHÚT");
            } else {
                return new Advice(76, "XOAY MÁY SANG PHẢI MỘT CHÚT");
            }
        }

        if (Math.abs(dy) > 0.075f) {
            if (dy < 0) {
                return new Advice(80, "NÂNG GÓC MÁY LÊN MỘT CHÚT");
            } else {
                return new Advice(80, "HẠ GÓC MÁY XUỐNG MỘT CHÚT");
            }
        }

        int score = Math.min(98, Math.round(93f - Math.abs(dx) * 25f - Math.abs(dy) * 18f));
        return new Advice(score, "✓ GÓC ĐẸP • GIỮ MÁY • CHỤP");
    }

    private void createOverlay() {
        overlayRoot = new FrameLayout(this);
        overlayRoot.setBackgroundColor(Color.TRANSPARENT);

        coachView = new CoachView(this);
        overlayRoot.addView(coachView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        guidance = new TextView(this);
        guidance.setText("AI ĐANG KHỞI ĐỘNG…");
        guidance.setTextColor(Color.WHITE);
        guidance.setTextSize(17f);
        guidance.setGravity(Gravity.CENTER);
        guidance.setPadding(dp(16), dp(10), dp(16), dp(10));
        guidance.setBackgroundColor(Color.argb(190, 0, 0, 0));
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(58));
        gp.gravity = Gravity.TOP;
        gp.setMargins(dp(18), dp(48), dp(18), 0);
        overlayRoot.addView(guidance, gp);

        scoreView = new TextView(this);
        scoreView.setText("--");
        scoreView.setTextColor(Color.rgb(95, 255, 120));
        scoreView.setTextSize(18f);
        scoreView.setGravity(Gravity.CENTER);
        scoreView.setBackgroundColor(Color.argb(185, 0, 0, 0));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(58), dp(58));
        sp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        sp.setMargins(dp(12), 0, 0, 0);
        overlayRoot.addView(scoreView, sp);

        closeView = new TextView(this);
        closeView.setText("×");
        closeView.setTextColor(Color.WHITE);
        closeView.setTextSize(26f);
        closeView.setGravity(Gravity.CENTER);
        closeView.setBackgroundColor(Color.argb(175, 0, 0, 0));
        closeView.setOnClickListener(v -> stopSelf());
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(44), dp(44));
        cp.gravity = Gravity.TOP | Gravity.RIGHT;
        cp.setMargins(0, dp(4), dp(8), 0);
        overlayRoot.addView(closeView, cp);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        windowManager.addView(overlayRoot, params);
    }

    private void showGuidance(String text, int score, boolean error) {
        if (guidance == null || scoreView == null) return;
        guidance.setText(text);
        guidance.setTextColor(error ? Color.rgb(255, 105, 105) : Color.WHITE);
        scoreView.setText(score > 0 ? String.valueOf(score) : "AI");
        scoreView.setTextColor(score >= 90 ? Color.rgb(95, 255, 120) : Color.rgb(255, 225, 90));
    }

    private void startAnalysisThread() {
        analysisThread = new HandlerThread("ProAngleScreenAnalysis");
        analysisThread.start();
        analysisHandler = new Handler(analysisThread.getLooper());
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float roll = (float) Math.toDegrees(Math.atan2(-event.values[0], event.values[1]));
        if (roll > 45f) roll = 45f;
        if (roll < -45f) roll = -45f;
        smoothedRoll = 0.88f * smoothedRoll + 0.12f * roll;
        if (coachView != null) {
            coachView.setState(faceX, faceY, faceSize, smoothedRoll,
                    scoreView != null ? parseScore(scoreView.getText().toString()) : 0,
                    previewBlocked);
        }
    }

    private int parseScore(String s) {
        try { return Integer.parseInt(s); } catch (Exception ignored) { return 0; }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public void onDestroy() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader.close();
            imageReader = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            try { mediaProjection.stop(); } catch (Exception ignored) { }
            mediaProjection = null;
        }
        if (overlayRoot != null && windowManager != null) {
            try { windowManager.removeView(overlayRoot); } catch (Exception ignored) { }
            overlayRoot = null;
        }
        if (analysisThread != null) {
            analysisThread.quitSafely();
            analysisThread = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static class Advice {
        final int score;
        final String text;
        Advice(int score, String text) {
            this.score = score;
            this.text = text;
        }
    }

    private static class CoachView extends View {
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float faceX = -1f;
        private float faceY = -1f;
        private float faceSize = 0f;
        private float roll = 0f;
        private int score = 0;
        private boolean blocked = false;

        CoachView(Context context) {
            super(context);
            gridPaint.setColor(Color.argb(60, 255, 255, 255));
            gridPaint.setStrokeWidth(dp(context, 1));
            accentPaint.setStyle(Paint.Style.STROKE);
            accentPaint.setStrokeWidth(dp(context, 2.4f));
            dimPaint.setColor(Color.argb(95, 255, 255, 255));
            dimPaint.setStrokeWidth(dp(context, 1));
        }

        void setState(float fx, float fy, float fs, float roll, int score, boolean blocked) {
            this.faceX = fx;
            this.faceY = fy;
            this.faceSize = fs;
            this.roll = roll;
            this.score = score;
            this.blocked = blocked;
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth();
            float h = getHeight();

            c.drawLine(w / 3f, 0, w / 3f, h, gridPaint);
            c.drawLine(2f * w / 3f, 0, 2f * w / 3f, h, gridPaint);
            c.drawLine(0, h / 3f, w, h / 3f, gridPaint);
            c.drawLine(0, 2f * h / 3f, w, 2f * h / 3f, gridPaint);

            float cx = w / 2f;
            float cy = h * 0.61f;
            accentPaint.setColor(score >= 90 ? Color.rgb(95, 255, 120) : Color.WHITE);
            c.save();
            c.rotate(roll, cx, cy);
            c.drawLine(cx - dp(getContext(), 62), cy, cx + dp(getContext(), 62), cy, accentPaint);
            c.restore();
            c.drawLine(cx - dp(getContext(), 76), cy, cx + dp(getContext(), 76), cy, dimPaint);

            if (faceX >= 0f && !blocked) {
                float boxW = Math.max(dp(getContext(), 80), faceSize * h * 0.72f);
                float boxH = Math.max(dp(getContext(), 110), faceSize * h);
                float x = faceX * w;
                float y = faceY * h;
                accentPaint.setColor(score >= 90 ? Color.rgb(95, 255, 120) : Color.rgb(255, 225, 90));
                c.drawRoundRect(x - boxW / 2f, y - boxH / 2f, x + boxW / 2f, y + boxH / 2f,
                        dp(getContext(), 14), dp(getContext(), 14), accentPaint);
            }
        }

        private static int dp(Context c, float v) {
            return Math.round(v * c.getResources().getDisplayMetrics().density);
        }
    }
}
