package com.proangle.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceTexture;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Comparator;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int REQ_CAMERA = 1001;

    private TextureView textureView;
    private CoachOverlay overlay;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewBuilder;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private String cameraId;
    private Size previewSize;
    private Rect activeArray;
    private int sensorOrientation = 90;
    private int faceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float smoothedRoll = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        textureView = new TextureView(this);
        root.addView(textureView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        overlay = new CoachOverlay(this);
        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        Button openVivo = new Button(this);
        openVivo.setText("MỞ CAMERA VIVO GỐC");
        openVivo.setTextColor(Color.BLACK);
        openVivo.setTextSize(16f);
        openVivo.setAllCaps(false);
        openVivo.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(95, 255, 120));
        bg.setCornerRadius(dp(18));
        openVivo.setBackground(bg);
        FrameLayout.LayoutParams buttonLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(58));
        buttonLp.gravity = Gravity.BOTTOM;
        buttonLp.setMargins(dp(22), 0, dp(22), dp(24));
        root.addView(openVivo, buttonLp);
        openVivo.setOnClickListener(v -> openOriginalCamera());

        setContentView(root);

        textureView.setSurfaceTextureListener(surfaceListener);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraThread();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        if (textureView.isAvailable()) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopCameraThread();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        super.onPause();
    }

    private final TextureView.SurfaceTextureListener surfaceListener = new TextureView.SurfaceTextureListener() {
        @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) { openCamera(); }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return true; }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
    };

    private void startCameraThread() {
        cameraThread = new HandlerThread("ProAngleCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopCameraThread() {
        if (cameraThread != null) {
            cameraThread.quitSafely();
            try { cameraThread.join(); } catch (InterruptedException ignored) { }
            cameraThread = null;
            cameraHandler = null;
        }
    }

    private void openCamera() {
        if (cameraDevice != null || cameraHandler == null) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) return;
        try {
            chooseBackCamera(manager);
            if (cameraId == null) {
                Toast.makeText(this, "Không tìm thấy camera sau.", Toast.LENGTH_LONG).show();
                return;
            }
            manager.openCamera(cameraId, stateCallback, cameraHandler);
        } catch (Exception e) {
            Toast.makeText(this, "Không mở được camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void chooseBackCamera(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;

            cameraId = id;
            Integer so = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = so == null ? 90 : so;
            activeArray = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

            int[] modes = c.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
            faceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
            if (modes != null) {
                for (int m : modes) {
                    if (m == CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL) {
                        faceDetectMode = m;
                        break;
                    }
                    if (m == CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE) faceDetectMode = m;
                }
            }

            StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
                previewSize = choosePreviewSize(sizes);
            }
            break;
        }
    }

    private Size choosePreviewSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return new Size(1280, 720);
        return Arrays.stream(sizes)
                .filter(s -> s.getWidth() <= 1920 && s.getHeight() <= 1080)
                .max(Comparator.comparingLong(s -> (long) s.getWidth() * s.getHeight()))
                .orElse(sizes[0]);
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createPreviewSession();
        }
        @Override public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }
        @Override public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Camera lỗi: " + error, Toast.LENGTH_LONG).show());
        }
    };

    private void createPreviewSession() {
        try {
            SurfaceTexture st = textureView.getSurfaceTexture();
            if (st == null || cameraDevice == null) return;
            if (previewSize == null) previewSize = new Size(1280, 720);
            st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(st);

            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(surface);
            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            previewBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            if (faceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF) {
                previewBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, faceDetectMode);
            }

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    try {
                        captureSession.setRepeatingRequest(previewBuilder.build(), captureCallback, cameraHandler);
                    } catch (CameraAccessException e) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Không chạy được camera preview.", Toast.LENGTH_LONG).show());
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession session) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Không cấu hình được camera.", Toast.LENGTH_LONG).show());
                }
            }, cameraHandler);
        } catch (CameraAccessException e) {
            Toast.makeText(this, "Lỗi camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
            if (faces == null || faces.length == 0 || activeArray == null) {
                overlay.post(() -> overlay.updateFace(false, 0, 0, 0, 0, 0));
                return;
            }
            Face best = faces[0];
            for (Face f : faces) {
                if (area(f.getBounds()) > area(best.getBounds())) best = f;
            }
            final float[] mapped = mapFace(best.getBounds());
            final int count = faces.length;
            overlay.post(() -> overlay.updateFace(true, mapped[0], mapped[1], mapped[2], mapped[3], count));
        }
    };

    private long area(Rect r) { return (long) r.width() * r.height(); }

    private float[] mapFace(Rect b) {
        float nx = (b.centerX() - activeArray.left) / (float) activeArray.width();
        float ny = (b.centerY() - activeArray.top) / (float) activeArray.height();
        float nw = b.width() / (float) activeArray.width();
        float nh = b.height() / (float) activeArray.height();

        int rotation = getPreviewRotation();
        float x, y, w, h;
        if (rotation == 90) {
            x = 1f - ny; y = nx; w = nh; h = nw;
        } else if (rotation == 180) {
            x = 1f - nx; y = 1f - ny; w = nw; h = nh;
        } else if (rotation == 270) {
            x = ny; y = 1f - nx; w = nh; h = nw;
        } else {
            x = nx; y = ny; w = nw; h = nh;
        }
        return new float[]{clamp01(x), clamp01(y), clamp01(w), clamp01(h)};
    }

    private int getPreviewRotation() {
        int displayDegrees;
        int r = getWindowManager().getDefaultDisplay().getRotation();
        if (r == Surface.ROTATION_90) displayDegrees = 90;
        else if (r == Surface.ROTATION_180) displayDegrees = 180;
        else if (r == Surface.ROTATION_270) displayDegrees = 270;
        else displayDegrees = 0;
        return (sensorOrientation - displayDegrees + 360) % 360;
    }

    private float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private void closeCamera() {
        if (captureSession != null) {
            try { captureSession.close(); } catch (Exception ignored) { }
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void openOriginalCamera() {
        try {
            Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            if (i.resolveActivity(getPackageManager()) == null) {
                i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            }
            if (i.resolveActivity(getPackageManager()) != null) {
                Toast.makeText(this, "Chuyển sang camera gốc để chụp bằng xử lý ảnh của Vivo.", Toast.LENGTH_SHORT).show();
                startActivity(i);
            } else {
                Toast.makeText(this, "Không tìm thấy ứng dụng camera gốc.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Không mở được camera gốc.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else if (requestCode == REQ_CAMERA) {
            Toast.makeText(this, "Cần quyền Camera để AI Coach xem bố cục.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float roll = (float) Math.toDegrees(Math.atan2(-event.values[0], event.values[1]));
        if (roll > 45f) roll = 45f;
        if (roll < -45f) roll = -45f;
        smoothedRoll = 0.86f * smoothedRoll + 0.14f * roll;
        overlay.setRoll(smoothedRoll);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static class CoachOverlay extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean hasFace = false;
        private float fx, fy, fw, fh;
        private int faceCount = 0;
        private float roll = 0f;

        CoachOverlay(Context c) {
            super(c);
            setWillNotDraw(false);
            grid.setColor(Color.argb(75, 255, 255, 255));
            grid.setStrokeWidth(dp(1));
            facePaint.setStyle(Paint.Style.STROKE);
            facePaint.setStrokeWidth(dp(2.5f));
            facePaint.setColor(Color.rgb(95, 255, 120));
        }

        void updateFace(boolean found, float x, float y, float w, float h, int count) {
            hasFace = found; fx = x; fy = y; fw = w; fh = h; faceCount = count; invalidate();
        }

        void setRoll(float v) { roll = v; postInvalidateOnAnimation(); }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth();
            float h = getHeight();

            drawGrid(c, w, h);
            drawHeader(c, w);
            Advice a = evaluate();
            drawAdvice(c, w, a);
            drawFace(c, w, h, a.good);
            drawLevel(c, w, h, a.good);
            drawScore(c, w, h, a.score, a.good);
        }

        private void drawGrid(Canvas c, float w, float h) {
            c.drawLine(w/3f, 0, w/3f, h, grid);
            c.drawLine(2*w/3f, 0, 2*w/3f, h, grid);
            c.drawLine(0, h/3f, w, h/3f, grid);
            c.drawLine(0, 2*h/3f, w, 2*h/3f, grid);
        }

        private void drawHeader(Canvas c, float w) {
            p.setColor(Color.argb(165, 0, 0, 0));
            c.drawRoundRect(dp(14), dp(18), w-dp(14), dp(72), dp(18), dp(18), p);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            p.setTextSize(sp(18));
            p.setColor(Color.WHITE);
            c.drawText("ProAngle AI", dp(28), dp(44), p);
            p.setTypeface(android.graphics.Typeface.DEFAULT);
            p.setTextSize(sp(12));
            p.setColor(Color.LTGRAY);
            c.drawText("Nhiếp ảnh gia AI • phân tích trực tiếp", dp(28), dp(62), p);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setColor(Color.rgb(95,255,120));
            p.setTextSize(sp(12));
            c.drawText(faceCount > 0 ? (faceCount + " người") : "Chân dung", w-dp(28), dp(54), p);
        }

        private void drawAdvice(Canvas c, float w, Advice a) {
            float top = dp(90), bottom = dp(188);
            p.setColor(Color.argb(180, 0, 0, 0));
            c.drawRoundRect(dp(18), top, w-dp(18), bottom, dp(20), dp(20), p);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            p.setTextSize(sp(22));
            p.setColor(a.good ? Color.rgb(95,255,120) : Color.rgb(255,220,55));
            c.drawText(a.title, w/2f, top+dp(38), p);

            p.setTypeface(android.graphics.Typeface.DEFAULT);
            p.setTextSize(sp(14));
            p.setColor(Color.WHITE);
            c.drawText(a.subtitle, w/2f, top+dp(68), p);
        }

        private void drawFace(Canvas c, float w, float h, boolean good) {
            if (!hasFace) return;
            float left = (fx - fw/2f) * w;
            float top = (fy - fh/2f) * h;
            float right = (fx + fw/2f) * w;
            float bottom = (fy + fh/2f) * h;
            facePaint.setColor(good ? Color.rgb(95,255,120) : Color.rgb(255,220,55));
            c.drawRoundRect(left, top, right, bottom, dp(12), dp(12), facePaint);
        }

        private void drawLevel(Canvas c, float w, float h, boolean good) {
            float cx = w/2f;
            float cy = h*0.63f;
            p.setStrokeWidth(dp(3));
            p.setColor(good ? Color.rgb(95,255,120) : Color.argb(210,255,255,255));
            c.save();
            c.rotate(roll, cx, cy);
            c.drawLine(cx-dp(58), cy, cx+dp(58), cy, p);
            c.restore();
            p.setStrokeWidth(dp(1));
            p.setColor(Color.argb(110,255,255,255));
            c.drawLine(cx-dp(70), cy, cx+dp(70), cy, p);
        }

        private void drawScore(Canvas c, float w, float h, int score, boolean good) {
            float x = dp(60), y = h-dp(120), r = dp(36);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(185, 0, 0, 0));
            c.drawCircle(x, y, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(5));
            p.setColor(good ? Color.rgb(95,255,120) : Color.rgb(255,220,55));
            c.drawCircle(x, y, r-dp(3), p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            p.setTextSize(sp(18));
            p.setColor(Color.WHITE);
            c.drawText(String.valueOf(score), x, y+dp(6), p);
            p.setTypeface(android.graphics.Typeface.DEFAULT);
            p.setTextSize(sp(11));
            p.setColor(Color.LTGRAY);
            c.drawText("BỐ CỤC", x, y+dp(54), p);
        }

        private Advice evaluate() {
            if (!hasFace) {
                return new Advice(42, false, "TÌM CHỦ THỂ", "Đưa người vào khung hình • giữ máy dọc");
            }

            float targetX = fx < 0.5f ? 0.333f : 0.667f;
            float targetY = 0.34f;
            float dx = Math.abs(fx - targetX);
            float dy = Math.abs(fy - targetY);

            float penalty = dx*95f + dy*52f + Math.min(28f, Math.abs(roll)*3.2f);
            if (fh < 0.13f) penalty += (0.13f-fh)*180f;
            if (fh > 0.43f) penalty += (fh-0.43f)*170f;
            int score = Math.max(35, Math.min(99, Math.round(100f - penalty)));

            if (Math.abs(roll) > 3.0f) {
                String side = roll > 0 ? "nghiêng trái" : "nghiêng phải";
                return new Advice(score, false, "CÂN LẠI MÁY", String.format(java.util.Locale.US, "Máy đang %s %.1f°", side, Math.abs(roll)));
            }
            if (fh < 0.13f) {
                return new Advice(score, false, "TIẾN GẦN HƠN", "Chủ thể hơi nhỏ • thử 2× nếu đứng xa");
            }
            if (fh > 0.43f) {
                return new Advice(score, false, "LÙI LẠI MỘT CHÚT", "Tạo thêm khoảng thở quanh khuôn mặt");
            }
            if (dx > 0.075f) {
                if (fx < targetX) {
                    return new Advice(score, false, "← XOAY MÁY SANG TRÁI", "Đưa chủ thể gần điểm 1/3 đẹp hơn");
                } else {
                    return new Advice(score, false, "XOAY MÁY SANG PHẢI →", "Đưa chủ thể gần điểm 1/3 đẹp hơn");
                }
            }
            if (fy > 0.45f) {
                return new Advice(score, false, "↓ HẠ MÁY MỘT CHÚT", "Đưa khuôn mặt cao hơn trong khung hình");
            }
            if (fy < 0.22f) {
                return new Advice(score, false, "↑ NÂNG MÁY MỘT CHÚT", "Cho thêm khoảng trống phía trên đầu");
            }

            boolean good = score >= 88;
            if (good) return new Advice(score, true, "✓ GÓC ĐẸP — GIỮ MÁY", "Bố cục cân • sẵn sàng chụp");
            return new Advice(score, false, "TINH CHỈNH NHẸ", "Giữ chủ thể ở 1/3 và cân đường chân trời");
        }

        private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
        private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }

        private static class Advice {
            final int score; final boolean good; final String title; final String subtitle;
            Advice(int score, boolean good, String title, String subtitle) {
                this.score = score; this.good = good; this.title = title; this.subtitle = subtitle;
            }
        }
    }
}
