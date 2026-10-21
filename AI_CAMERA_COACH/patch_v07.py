from pathlib import Path

p = Path('app/src/main/java/com/proangle/ai/OverlayCoachService.java')
s = p.read_text()

# Better face detector for partially covered / sideways portraits after orientation correction.
s = s.replace('.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)\n                .setMinFaceSize(0.05f)',
              '.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)\n                .setMinFaceSize(0.03f)')

# Face geometry is useful even if object detection cannot confidently see a whole person.
s = s.replace('    private volatile float facePreviewY = -1f;\n    private volatile boolean hasFace = false;',
'''    private volatile float facePreviewY = -1f;
    private volatile float facePreviewW = 0f;
    private volatile float facePreviewH = 0f;
    private volatile boolean hasFace = false;''')

old_analyze = '''        brightness = measureBrightnessAndUniformity(scaled);
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
'''

new_analyze = '''        brightness = measureBrightnessAndUniformity(scaled);
        int originalW = scaled.getWidth();
        int originalH = scaled.getHeight();
        int q = deviceQuarterTurn;
        Bitmap oriented = rotateBitmapForAnalysis(scaled, q);
        if (oriented != scaled) scaled.recycle();
        InputImage input = InputImage.fromBitmap(oriented, 0);
        analysisInFlight = true;

        objectDetector.process(input)
                .addOnSuccessListener(analysisExecutor, objects -> runFaceDetection(input, oriented, objects,
                        top, previewHeight, originalW, originalH, q))
                .addOnFailureListener(analysisExecutor, e -> runFaceDetection(input, oriented, Collections.emptyList(),
                        top, previewHeight, originalW, originalH, q));
    }

    private void runFaceDetection(InputImage input, Bitmap analysisBitmap, List<DetectedObject> objects,
                                  int previewTopPx, int previewHeightPx,
                                  int originalW, int originalH, int rotation) {
        faceDetector.process(input)
                .addOnSuccessListener(analysisExecutor, faces -> {
                    handleDetections(analysisBitmap.getWidth(), analysisBitmap.getHeight(), objects, faces,
                            previewTopPx, previewHeightPx, originalW, originalH, rotation);
                    analysisBitmap.recycle();
                    analysisInFlight = false;
                })
                .addOnFailureListener(analysisExecutor, e -> {
                    handleDetections(analysisBitmap.getWidth(), analysisBitmap.getHeight(), objects, Collections.emptyList(),
                            previewTopPx, previewHeightPx, originalW, originalH, rotation);
                    analysisBitmap.recycle();
                    analysisInFlight = false;
                });
    }

    private void handleDetections(int w, int h, List<DetectedObject> objects, List<Face> faces,
                                  int previewTopPx, int previewHeightPx,
                                  int originalW, int originalH, int rotation) {
        // Manual lock coordinates live in the original preview. Temporarily convert them
        // into the rotated analysis coordinate system so tracking still selects the right object.
        float savedLockX = manualLockX;
        float savedLockY = manualLockY;
        if (manualLockActive) {
            float[] rp = pointToRotated(savedLockX, savedLockY, rotation);
            manualLockX = rp[0];
            manualLockY = rp[1];
        }

        Rect faceRot = chooseBestFace(faces, w, h);
        Rect objectRot = chooseBestSubject(objects, faceRot, w, h);

        manualLockX = savedLockX;
        manualLockY = savedLockY;

        Rect bestFace = mapRectToOriginal(faceRot, originalW, originalH, rotation);
        Rect bestObject = mapRectToOriginal(objectRot, originalW, originalH, rotation);
        int ow = originalW;
        int oh = originalH;

        hasFace = bestFace != null;
        if (bestFace != null) {
            facePreviewX = clamp01(bestFace.exactCenterX() / ow);
            facePreviewY = clamp01(bestFace.exactCenterY() / oh);
            facePreviewW = clamp01(bestFace.width() / (float) ow);
            facePreviewH = clamp01(bestFace.height() / (float) oh);
        } else {
            facePreviewX = -1f;
            facePreviewY = -1f;
            facePreviewW = 0f;
            facePreviewH = 0f;
        }

        if (manualLockActive && bestObject != null) {
            manualLockX = clamp01(bestObject.exactCenterX() / ow);
            manualLockY = clamp01(bestObject.exactCenterY() / oh);
        } else if (manualLockActive && bestFace != null) {
            manualLockX = clamp01(bestFace.exactCenterX() / ow);
            manualLockY = clamp01(bestFace.exactCenterY() / oh);
        }

        Rect drawRect = bestObject != null ? bestObject : bestFace;
        subjectReliable = (bestObject != null && isReliableSubject(bestObject, bestFace, ow, oh))
                || (manualLockActive && bestFace != null);
'''

if old_analyze not in s:
    raise SystemExit('v0.7 analyze/handle target not found')
s = s.replace(old_analyze, new_analyze)

# The rest of handleDetections must use original dimensions, not rotated detector dimensions.
s = s.replace('subjectPreviewX = clamp01(drawRect.exactCenterX() / w);\n            subjectPreviewY = clamp01(drawRect.exactCenterY() / h);\n            subjectPreviewW = clamp01(drawRect.width() / (float) w);\n            subjectPreviewH = clamp01(drawRect.height() / (float) h);',
'''subjectPreviewX = clamp01(drawRect.exactCenterX() / ow);
            subjectPreviewY = clamp01(drawRect.exactCenterY() / oh);
            subjectPreviewW = clamp01(drawRect.width() / (float) ow);
            subjectPreviewH = clamp01(drawRect.height() / (float) oh);''')

# Insert orientation helpers before brightness measurement.
marker = '    private float measureBrightnessAndUniformity(Bitmap b) {'
helpers = '''    private Bitmap rotateBitmapForAnalysis(Bitmap src, int rotation) {
        int r = ((rotation % 360) + 360) % 360;
        if (r == 0) return src;
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.postRotate(r);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    private float[] pointToRotated(float x, float y, int rotation) {
        int r = ((rotation % 360) + 360) % 360;
        if (r == 90) return new float[] { 1f - y, x };
        if (r == 180) return new float[] { 1f - x, 1f - y };
        if (r == 270) return new float[] { y, 1f - x };
        return new float[] { x, y };
    }

    private Rect mapRectToOriginal(Rect rr, int originalW, int originalH, int rotation) {
        if (rr == null) return null;
        int r = ((rotation % 360) + 360) % 360;
        Rect out;
        if (r == 90) {
            out = new Rect(rr.top, originalH - rr.right, rr.bottom, originalH - rr.left);
        } else if (r == 180) {
            out = new Rect(originalW - rr.right, originalH - rr.bottom,
                    originalW - rr.left, originalH - rr.top);
        } else if (r == 270) {
            out = new Rect(originalW - rr.bottom, rr.left,
                    originalW - rr.top, rr.right);
        } else {
            out = new Rect(rr);
        }
        out.left = Math.max(0, Math.min(originalW, out.left));
        out.right = Math.max(0, Math.min(originalW, out.right));
        out.top = Math.max(0, Math.min(originalH, out.top));
        out.bottom = Math.max(0, Math.min(originalH, out.bottom));
        return out;
    }

'''
if marker not in s:
    raise SystemExit('v0.7 helper insertion target not found')
s = s.replace(marker, helpers + marker)

# Make portrait advice useful instead of merely saying detection is uncertain.
old_uncertain = '''        if (!subjectReliable) {
            if (hasFace) {
                return new Advice(58, "THẤY MẶT • CHƯA KHÓA CHẮC CẢ NGƯỜI");
            }
            return new Advice(55, "GIỮ KHOẢNG CÁCH • AI ĐANG XÁC ĐỊNH CHỦ THỂ");
        }
'''
new_uncertain = '''        if (!subjectReliable) {
            if (hasFace) {
                float faceArea = facePreviewW * facePreviewH;
                if (faceArea > 0.20f || facePreviewW > 0.55f || facePreviewH > 0.58f) {
                    return new Advice(68, "LÙI NHẸ 20–30 CM • CHỪA KHÔNG GIAN QUANH MẶT");
                }
                if (facePreviewX < 0.26f) return new Advice(70, "DỊCH KHUNG SANG TRÁI NHẸ • ĐƯA MẶT RA KHỎI MÉP");
                if (facePreviewX > 0.74f) return new Advice(70, "DỊCH KHUNG SANG PHẢI NHẸ • ĐƯA MẶT RA KHỎI MÉP");
                return new Advice(66, "GIỮ MẶT Ở 1/3 KHUNG • LIA CHẬM ĐỂ TÌM GÓC SẠCH HƠN");
            }
            if (manualLockActive) {
                return new Advice(60, "ĐÃ KHÓA CHỦ THỂ • LIA CHẬM TRÁI ↔ PHẢI ĐỂ AI TÌM LẠI");
            }
            return new Advice(55, "NHẤN GIỮ CHỦ THỂ • SAU ĐÓ LIA NHẸ ĐỂ AI TƯ VẤN GÓC");
        }
'''
if old_uncertain not in s:
    raise SystemExit('v0.7 uncertain advice target not found')
s = s.replace(old_uncertain, new_uncertain)

# A locked face can be the subject even when the generic object model misses the whole body.
s = s.replace('return new Advice(48, manualLockActive', 'return new Advice(58, manualLockActive')

# Version bump after v0.6 patch.
g = Path('app/build.gradle')
t = g.read_text()
t = t.replace('versionCode 6', 'versionCode 7')
t = t.replace("versionName '0.6.0'", "versionName '0.7.0'")
g.write_text(t)

p.write_text(s)
print('v0.7 orientation-aware portrait advice patch applied')
