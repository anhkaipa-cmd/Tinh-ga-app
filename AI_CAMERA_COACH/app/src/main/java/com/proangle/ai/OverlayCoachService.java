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
import android.graphics.Rect;
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

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public class OverlayCoachService extends Service implements SensorEventListener {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final String CHANNEL_ID = "proangle_ai_coach";
    private static final int NOTIFICATION_ID = 7702;
    private static final int ANALYSIS_W = 480;
    private static final float PREVIEW_TOP = 0.14f;
    private static final float PREVIEW_BOTTOM = 0.75f;

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

    private FaceDetector faceDetector;
    private ObjectDetector objectDetector;
    private volatile boolean analysisInFlight = false;

    private int screenWidth;
    private int screenHeight;
    private int densityDpi;
    private long lastAnalysisMs = 0L;
    private int darkUniformFrames = 0;

    private volatile float subjectScreenX = -1f;
    private volatile float subjectScreenY = -1f;
    private volatile float subjectScreenW = 0f;
    private volatile float subjectScreenH = 0f;
    private volatile float subjectPreviewX = -1f;
    private volatile float subjectPreviewY = -1f;
    private volatile float subjectPreviewW = 0f;
    private volatile float subjectPreviewH = 0f;
    private volatile float subjectArea = 0f;
    private volatile boolean subjectReliable = false;

    private volatile float facePreviewX = -1f;
    private volatile float facePreviewY = -1f;
    private volatile boolean hasFace = false;

    private volatile float brightness = 128f;
    private volatile boolean previewBlocked = false;

    private final Executor analysisExecutor = command -> {
        Handler h = analysisHandler;
        if (h != null) h.post(command);
    };

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

        FaceDetectorOptions faceOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.05f)
                .build();
        faceDetector = FaceDetection.getClient(faceOptions);

        ObjectDetectorOptions objectOptions = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .build();
        objectDetector = ObjectDetection.getClient(objectOptions);

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
                .setContentText("Đang xác định chủ thể trên Camera Vivo")
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
        showGuidance("AI ĐANG KHÓA CHỦ THỂ…", 42, false);
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastAnalysisMs < 600 || analysisInFlight) return;
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
        int top = Math.max(0, Math.round(frame.getHeight() * PREVIEW_TOP));
        int bottom = Math.min(frame.getHeight(), Math.round(frame.getHeight() * PREVIEW_BOTTOM));
        int previewHeight = Math.max(1, bottom - top);

        Bitmap preview = Bitmap.createBitmap(frame, 0, top, frame.getWidth(), previewHeight);
        int analysisH = Math.max(360, Math.round(previewHeight * (ANALYSIS_W / (float) frame.getWidth())));
        Bitmap scaled = Bitmap.createScaledBitmap(preview, ANALYSIS_W, analysisH, true);
        preview.recycle();

        brightness = measureBrightnessAndUniformity(scaled);
        InputImage input = InputImage.fromBitmap(scaled, 0);
        analysisInFlight = true;

        objectDetector.process(input)
                .addOnSuccessListener(analysisExecutor, objects -> runFaceDetection(input, scaled, objects, top, previewHeight))
                .addOnFailureListener(analysisExecutor, e -> runFaceDetection(input, scaled, Collections.emptyList(), top, previewHeight));
    }

    private void runFaceDetection(InputImage input, Bitmap scaled, List<DetectedObject> objects,
                                  int previewTopPx, int previewHeightPx) {
        faceDetector.process(input)
                .addOnSuccessListener(analysisExecutor, faces -> {
                    handleDetections(scaled.getWidth(), scaled.getHeight(), objects, faces,
                            previewTopPx, previewHeightPx);
                    scaled.recycle();
                    analysisInFlight = false;
                })
                .addOnFailureListener(analysisExecutor, e -> {
                    handleDetections(scaled.getWidth(), scaled.getHeight(), objects, Collections.emptyList(),
                            previewTopPx, previewHeightPx);
                    scaled.recycle();
                    analysisInFlight = false;
                });
    }

    private void handleDetections(int w, int h, List<DetectedObject> objects, List<Face> faces,
                                  int previewTopPx, int previewHeightPx) {
        Rect bestFace = chooseBestFace(faces, w, h);
        hasFace = bestFace != null;
        if (bestFace != null) {
            facePreviewX = clamp01(bestFace.exactCenterX() / w);
            facePreviewY = clamp01(bestFace.exactCenterY() / h);
        } else {
            facePreviewX = -1f;
            facePreviewY = -1f;
        }

        Rect bestObject = chooseBestSubject(objects, bestFace, w, h);
        Rect drawRect = bestObject != null ? bestObject : bestFace;
        subjectReliable = bestObject != null && isReliableSubject(bestObject, bestFace, w, h);

        if (drawRect != null) {
            subjectPreviewX = clamp01(drawRect.exactCenterX() / w);
            subjectPreviewY = clamp01(drawRect.exactCenterY() / h);
            subjectPreviewW = clamp01(drawRect.width() / (float) w);
            subjectPreviewH = clamp01(drawRect.height() / (float) h);
            subjectArea = subjectPreviewW * subjectPreviewH;

            subjectScreenX = subjectPreviewX;
            subjectScreenY = clamp01((previewTopPx + subjectPreviewY * previewHeightPx) / (float) screenHeight);
            subjectScreenW = subjectPreviewW;
            subjectScreenH = clamp01(subjectPreviewH * previewHeightPx / (float) screenHeight);
        } else {
            subjectScreenX = -1f;
            subjectScreenY = -1f;
            subjectScreenW = 0f;
            subjectScreenH = 0f;
            subjectPreviewX = -1f;
            subjectPreviewY = -1f;
            subjectPreviewW = 0f;
            subjectPreviewH = 0f;
            subjectArea = 0f;
            subjectReliable = false;
        }

        mainHandler.post(this::updateAdvice);
    }

    private Rect chooseBestFace(List<Face> faces, int w, int h) {
        if (faces == null || faces.isEmpty()) return null;
        Rect best = null;
        float bestScore = -999f;
        for (Face face : faces) {
            Rect r = face.getBoundingBox();
            if (r.width() <= 0 || r.height() <= 0) continue;
            float area = r.width() * r.height() / (float) (w * h);
            float cx = r.exactCenterX() / w;
            float cy = r.exactCenterY() / h;
            float centrality = 1f - (Math.abs(cx - 0.5f) * 0.65f + Math.abs(cy - 0.48f) * 0.35f);
            float score = area * 8f + centrality;
            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }

    private Rect chooseBestSubject(List<DetectedObject> objects, Rect face, int w, int h) {
        if (objects == null || objects.isEmpty()) return null;
        float faceCx = face != null ? face.exactCenterX() : -1f;
        float faceCy = face != null ? face.exactCenterY() : -1f;
        Rect best = null;
        float bestScore = -999f;

        for (DetectedObject object : objects) {
            Rect r = object.getBoundingBox();
            if (r.width() <= 0 || r.height() <= 0) continue;
            float area = r.width() * r.height() / (float) (w * h);
            if (area < 0.012f || area > 0.93f) continue;

            float cx = r.exactCenterX() / w;
            float cy = r.exactCenterY() / h;
            float centerScore = 1f - (Math.abs(cx - 0.5f) * 0.7f + Math.abs(cy - 0.5f) * 0.3f);
            float sizeScore = Math.min(area, 0.42f) * 3.2f;
            float score = centerScore + sizeScore;

            if (face != null) {
                if (containsWithMargin(r, faceCx, faceCy, w * 0.03f, h * 0.03f)) {
                    score += 3.5f;
                    float objectToFaceArea = area / Math.max(0.001f,
                            face.width() * face.height() / (float) (w * h));
                    if (objectToFaceArea > 2.0f) score += 0.7f;
                } else {
                    float dx = (r.exactCenterX() - faceCx) / w;
                    float dy = (r.exactCenterY() - faceCy) / h;
                    score -= (float) Math.sqrt(dx * dx + dy * dy) * 3.0f;
                }
            }

            if (area > 0.72f) score -= 1.6f;
            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }

    private boolean containsWithMargin(Rect r, float x, float y, float mx, float my) {
        return x >= r.left - mx && x <= r.right + mx && y >= r.top - my && y <= r.bottom + my;
    }

    private boolean isReliableSubject(Rect subject, Rect face, int w, int h) {
        float area = subject.width() * subject.height() / (float) (w * h);
        if (area < 0.025f || area > 0.82f) return false;
        if (face == null) return area >= 0.05f;
        return containsWithMargin(subject, face.exactCenterX(), face.exactCenterY(),
                w * 0.025f, h * 0.025f);
    }

    private float measureBrightnessAndUniformity(Bitmap b) {
        long sum = 0;
        long sumSq = 0;
        int n = 0;
        for (int y = 20; y < b.getHeight() - 20; y += 24) {
            for (int x = 20; x < b.getWidth() - 20; x += 24) {
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
        if (mean < 7f && variance < 8f) darkUniformFrames++;
        else darkUniformFrames = 0;
        previewBlocked = darkUniformFrames >= 5;
        return mean;
    }

    private void updateAdvice() {
        if (previewBlocked) {
            showGuidance("KHÔNG ĐỌC ĐƯỢC PREVIEW VIVO", 0, true);
            coachView.setState(-1f, -1f, 0f, 0f, false, smoothedRoll, 0, true);
            return;
        }

        Advice a = evaluateAdvice();
        showGuidance(a.text, a.score, false);
        coachView.setState(subjectScreenX, subjectScreenY, subjectScreenW, subjectScreenH,
                subjectReliable, smoothedRoll, a.score, false);
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

        if (subjectPreviewX < 0f) {
            return new Advice(48, "LIA MÁY CHẬM • AI ĐANG TÌM CHỦ THỂ");
        }

        if (!subjectReliable) {
            if (hasFace) {
                return new Advice(58, "THẤY MẶT • CHƯA KHÓA CHẮC CẢ NGƯỜI");
            }
            return new Advice(55, "GIỮ KHOẢNG CÁCH • AI ĐANG XÁC ĐỊNH CHỦ THỂ");
        }

        if (subjectArea < 0.045f && Math.max(subjectPreviewW, subjectPreviewH) < 0.42f) {
            return new Advice(68, "TIẾN GẦN NHẸ • CHỦ THỂ CÒN NHỎ");
        }
        if (subjectArea > 0.62f || subjectPreviewW > 0.92f || subjectPreviewH > 0.90f) {
            return new Advice(70, "LÙI LẠI MỘT CHÚT • CHỪA KHÔNG GIAN");
        }

        boolean horizontalSubject = subjectPreviewW > subjectPreviewH * 1.15f;
        float focusX = hasFace ? facePreviewX : subjectPreviewX;
        float focusY = hasFace ? facePreviewY : subjectPreviewY;
        float targetX = focusX < 0.5f ? 0.333f : 0.667f;
        float targetY = hasFace ? (horizontalSubject ? 0.46f : 0.34f) : 0.50f;
        float dx = focusX - targetX;
        float dy = focusY - targetY;

        if (Math.abs(dx) > 0.10f) {
            return new Advice(76, dx < 0
                    ? "XOAY MÁY SANG TRÁI MỘT CHÚT"
                    : "XOAY MÁY SANG PHẢI MỘT CHÚT");
        }

        if (Math.abs(dy) > (horizontalSubject ? 0.12f : 0.09f)) {
            return new Advice(80, dy < 0
                    ? "NÂNG GÓC MÁY LÊN MỘT CHÚT"
                    : "HẠ GÓC MÁY XUỐNG MỘT CHÚT");
        }

        int score = Math.min(98, Math.round(94f
                - Math.abs(dx) * 24f
                - Math.abs(dy) * 18f
                - Math.abs(smoothedRoll) * 1.2f));
        return new Advice(score, "✓ ĐÃ KHÓA CHỦ THỂ • GIỮ MÁY • CHỤP");
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
        guidance.setTextSize(16f);
        guidance.setGravity(Gravity.CENTER);
        guidance.setPadding(dp(12), dp(8), dp(12), dp(8));
        guidance.setBackgroundColor(Color.argb(185, 0, 0, 0));
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(54));
        gp.gravity = Gravity.TOP;
        gp.setMargins(dp(18), dp(48), dp(18), 0);
        overlayRoot.addView(guidance, gp);

        scoreView = new TextView(this);
        scoreView.setText("--");
        scoreView.setTextColor(Color.rgb(95, 255, 120));
        scoreView.setTextSize(18f);
        scoreView.setGravity(Gravity.CENTER);
        scoreView.setBackgroundColor(Color.argb(180, 0, 0, 0));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(58), dp(58));
        sp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        sp.setMargins(dp(12), 0, 0, 0);
        overlayRoot.addView(scoreView, sp);

        closeView = new TextView(this);
        closeView.setText("×");
        closeView.setTextColor(Color.WHITE);
        closeView.setTextSize(26f);
        closeView.setGravity(Gravity.CENTER);
        closeView.setBackgroundColor(Color.argb(165, 0, 0, 0));
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
            coachView.setState(subjectScreenX, subjectScreenY, subjectScreenW, subjectScreenH,
                    subjectReliable, smoothedRoll,
                    scoreView != null ? parseScore(scoreView.getText().toString()) : 0,
                    previewBlocked);
        }
    }

    private int parseScore(String s) {
        try { return Integer.parseInt(s); } catch (Exception ignored) { return 0; }
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
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
        if (faceDetector != null) {
            try { faceDetector.close(); } catch (Exception ignored) { }
            faceDetector = null;
        }
        if (objectDetector != null) {
            try { objectDetector.close(); } catch (Exception ignored) { }
            objectDetector = null;
        }
        if (overlayRoot != null && windowManager != null) {
            try { windowManager.removeView(overlayRoot); } catch (Exception ignored) { }
            overlayRoot = null;
        }
        if (analysisThread != null) {
            analysisThread.quitSafely();
            analysisThread = null;
            analysisHandler = null;
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
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float subjectX = -1f;
        private float subjectY = -1f;
        private float subjectW = 0f;
        private float subjectH = 0f;
        private boolean reliable = false;
        private float roll = 0f;
        private int score = 0;
        private boolean blocked = false;

        CoachView(Context context) {
            super(context);
            gridPaint.setColor(Color.argb(55, 255, 255, 255));
            gridPaint.setStrokeWidth(dp(context, 1));
            accentPaint.setStyle(Paint.Style.STROKE);
            accentPaint.setStrokeWidth(dp(context, 2.4f));
            dimPaint.setColor(Color.argb(100, 255, 255, 255));
            dimPaint.setStrokeWidth(dp(context, 1));
            textPaint.setTextSize(dp(context, 11));
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }

        void setState(float sx, float sy, float sw, float sh, boolean reliable,
                      float roll, int score, boolean blocked) {
            this.subjectX = sx;
            this.subjectY = sy;
            this.subjectW = sw;
            this.subjectH = sh;
            this.reliable = reliable;
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
            float previewTop = h * PREVIEW_TOP;
            float previewBottom = h * PREVIEW_BOTTOM;
            float previewH = previewBottom - previewTop;

            c.drawLine(w / 3f, previewTop, w / 3f, previewBottom, gridPaint);
            c.drawLine(2f * w / 3f, previewTop, 2f * w / 3f, previewBottom, gridPaint);
            c.drawLine(0, previewTop + previewH / 3f, w, previewTop + previewH / 3f, gridPaint);
            c.drawLine(0, previewTop + 2f * previewH / 3f, w, previewTop + 2f * previewH / 3f, gridPaint);

            float cx = w / 2f;
            float cy = previewTop + previewH * 0.68f;
            accentPaint.setColor(score >= 90 ? Color.rgb(95, 255, 120) : Color.WHITE);
            c.save();
            c.rotate(roll, cx, cy);
            c.drawLine(cx - dp(getContext(), 62), cy, cx + dp(getContext(), 62), cy, accentPaint);
            c.restore();
            c.drawLine(cx - dp(getContext(), 76), cy, cx + dp(getContext(), 76), cy, dimPaint);

            if (subjectX >= 0f && !blocked) {
                float x = subjectX * w;
                float y = subjectY * h;
                float boxW = Math.max(dp(getContext(), 56), subjectW * w);
                float boxH = Math.max(dp(getContext(), 56), subjectH * h);
                int color = reliable
                        ? (score >= 90 ? Color.rgb(95, 255, 120) : Color.rgb(255, 225, 90))
                        : Color.rgb(255, 170, 65);
                accentPaint.setColor(color);
                c.drawRoundRect(x - boxW / 2f, y - boxH / 2f,
                        x + boxW / 2f, y + boxH / 2f,
                        dp(getContext(), 14), dp(getContext(), 14), accentPaint);

                textPaint.setColor(color);
                textPaint.setTextAlign(Paint.Align.LEFT);
                c.drawText(reliable ? "CHỦ THỂ" : "CHƯA CHẮC",
                        Math.max(dp(getContext(), 6), x - boxW / 2f),
                        Math.max(previewTop + dp(getContext(), 14), y - boxH / 2f - dp(getContext(), 6)),
                        textPaint);
            }
        }

        private static int dp(Context c, float v) {
            return Math.round(v * c.getResources().getDisplayMetrics().density);
        }
    }
}
