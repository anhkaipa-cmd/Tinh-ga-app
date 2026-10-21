from pathlib import Path

p = Path('app/src/main/java/com/proangle/ai/OverlayCoachService.java')
s = p.read_text()

# Keep a stable ML Kit tracking id after the user long-presses a subject.
s = s.replace(
    '    private volatile float manualLockY = 0.5f;\n',
    '    private volatile float manualLockY = 0.5f;\n    private volatile Integer manualTrackingId = null;\n'
)

# Replace subject selection with true tracking-id lock first, nearest-point reacquire second.
start = s.index('    private Rect chooseBestSubject(List<DetectedObject> objects, Rect face, int w, int h) {')
end = s.index('    private boolean containsWithMargin', start)
new_subject = '''    private Rect chooseBestSubject(List<DetectedObject> objects, Rect face, int w, int h) {
        if (objects == null || objects.isEmpty()) return null;

        if (manualLockActive && manualTrackingId != null) {
            for (DetectedObject object : objects) {
                Integer id = object.getTrackingId();
                if (id != null && id.equals(manualTrackingId)) {
                    Rect r = object.getBoundingBox();
                    if (r.width() > 0 && r.height() > 0) {
                        manualLockX = r.exactCenterX() / w;
                        manualLockY = r.exactCenterY() / h;
                        return r;
                    }
                }
            }
            // Tracking id can change after a brief occlusion. Reacquire around the last anchor.
            manualTrackingId = null;
        }

        float faceCx = face != null ? face.exactCenterX() : -1f;
        float faceCy = face != null ? face.exactCenterY() : -1f;
        Rect best = null;
        DetectedObject bestObject = null;
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

            if (manualLockActive) {
                float dx = cx - manualLockX;
                float dy = cy - manualLockY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                boolean containsPress = manualLockX >= r.left / (float) w && manualLockX <= r.right / (float) w
                        && manualLockY >= r.top / (float) h && manualLockY <= r.bottom / (float) h;
                score += containsPress ? 18f : Math.max(-12f, 7f - dist * 24f);
            } else if (face != null) {
                if (containsWithMargin(r, faceCx, faceCy, w * 0.03f, h * 0.03f)) {
                    score += 3.5f;
                }
            }

            if (area > 0.72f) score -= 1.6f;
            if (score > bestScore) {
                bestScore = score;
                best = r;
                bestObject = object;
            }
        }

        if (manualLockActive && best != null) {
            float cx = best.exactCenterX() / w;
            float cy = best.exactCenterY() / h;
            float dx = cx - manualLockX;
            float dy = cy - manualLockY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > 0.38f) return null;
            manualLockX = cx;
            manualLockY = cy;
            if (bestObject != null) manualTrackingId = bestObject.getTrackingId();
        }
        return best;
    }

'''
s = s[:start] + new_subject + s[end:]

# Floating controls: no separate LOCK button. Scan and close stay outside the native shutter area.
start = s.index('    private void createFloatingControls() {')
end = s.index('    private void beginManualLock()', start)
new_controls = '''    private void createFloatingControls() {
        scanButton = makeControl("TÌM GÓC");
        scanButton.setOnClickListener(v -> startAngleScan());
        windowManager.addView(scanButton, controlParams(88, 38, Gravity.TOP | Gravity.LEFT, 12, 112));

        closeView = makeControl("×");
        closeView.setTextSize(24f);
        closeView.setOnClickListener(v -> stopSelf());
        windowManager.addView(closeView, controlParams(42, 42, Gravity.TOP | Gravity.RIGHT, 8, 4));

        createHoldToLockLayer();
    }

'''
s = s[:start] + new_controls + s[end:]

# Replace button-activated tap selection with direct press-and-hold on the preview.
start = s.index('    private void beginManualLock() {')
end = s.index('    private void removeLockCapture()', start)
new_hold = '''    private void createHoldToLockLayer() {
        if (lockCaptureView != null) return;
        if (screenHeight <= 0) {
            DisplayMetrics dm = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(dm);
            screenWidth = dm.widthPixels;
            screenHeight = dm.heightPixels;
        }

        lockCaptureView = new View(this);
        lockCaptureView.setBackgroundColor(Color.TRANSPARENT);
        final Handler holdHandler = new Handler(getMainLooper());
        final float[] down = new float[2];
        final boolean[] fired = new boolean[] { false };
        final Runnable[] pending = new Runnable[1];

        lockCaptureView.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getX();
                    down[1] = event.getY();
                    fired[0] = false;
                    pending[0] = () -> {
                        fired[0] = true;
                        manualLockX = clamp01(down[0] / Math.max(1f, v.getWidth()));
                        float screenNormY = PREVIEW_TOP + down[1] / Math.max(1f, screenHeight);
                        manualLockY = clamp01((screenNormY - PREVIEW_TOP) / (PREVIEW_BOTTOM - PREVIEW_TOP));
                        manualLockActive = true;
                        manualTrackingId = null;
                        scanTargetActive = false;
                        scanning = false;
                        try { v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); } catch (Exception ignored) { }
                        showGuidance("🔒 ĐÃ KHÓA CHỦ THỂ • GIỮ MÁY HOẶC TÌM GÓC", 82, false);
                    };
                    holdHandler.postDelayed(pending[0], 600);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - down[0];
                    float dy = event.getY() - down[1];
                    if (dx * dx + dy * dy > dp(18) * dp(18)) {
                        if (pending[0] != null) holdHandler.removeCallbacks(pending[0]);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (pending[0] != null) holdHandler.removeCallbacks(pending[0]);
                    return true;
            }
            return true;
        });

        // Capture only the photographic area. Native Vivo shutter/zoom/mode controls remain touchable.
        int captureBottom = Math.round(screenHeight * 0.66f);
        int captureTop = Math.round(screenHeight * PREVIEW_TOP);
        int captureHeight = Math.max(dp(120), captureBottom - captureTop);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                captureHeight,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.y = captureTop;
        windowManager.addView(lockCaptureView, lp);
    }

    private void beginManualLock() {
        // Kept for compatibility with older calls. v0.6 locks by long-pressing the subject directly.
        showGuidance("NHẤN GIỮ TRỰC TIẾP LÊN CHỦ THỂ 0,6 GIÂY", 60, false);
    }

'''
s = s[:start] + new_hold + s[end:]

s = s.replace('showGuidance("AI ĐANG KHÓA CHỦ THỂ…", 42, false);',
              'showGuidance("NHẤN GIỮ CHỦ THỂ 0,6 GIÂY ĐỂ KHÓA", 42, false);')

# Do not remove the always-on hold layer after locking; only on service destroy.
s = s.replace('                removeLockCapture();\n                lockButton.setText("KHÓA LẠI");',
              '                if (scanButton != null) scanButton.setText("TÌM GÓC");')

# Version bump after v0.5 patch.
g = Path('app/build.gradle')
t = g.read_text()
t = t.replace('versionCode 5', 'versionCode 6')
t = t.replace("versionName '0.5.0'", "versionName '0.6.0'")
g.write_text(t)

p.write_text(s)
print('v0.6 long-press subject lock patch applied')
