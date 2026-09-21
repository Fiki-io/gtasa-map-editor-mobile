package com.gtasa.mapeditor.render;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * Custom GLSurfaceView with multi-touch camera and gizmo gesture handling.
 */
public class MapGLSurfaceView extends GLSurfaceView {

    private final MapRenderer renderer;
    private final ScaleGestureDetector scaleDetector;

    private float lastTouchX;
    private float lastTouchY;
    private float touchStartX;
    private float touchStartYVal;
    private boolean isDraggingGizmo = false;

    private float lastPanX;
    private float lastPanY;

    public MapGLSurfaceView(Context context) {
        this(context, null);
    }

    public MapGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);

        renderer = new MapRenderer();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                renderer.camera.zoom(detector.getScaleFactor());
                return true;
            }
        });
    }

    public MapRenderer getMapRenderer() {
        return renderer;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        int pointerCount = event.getPointerCount();

        if (pointerCount == 2) {
            // Two-finger Pan
            float midX = (event.getX(0) + event.getX(1)) * 0.5f;
            float midY = (event.getY(0) + event.getY(1)) * 0.5f;

            if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                lastPanX = midX;
                lastPanY = midY;
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = midX - lastPanX;
                float dy = midY - lastPanY;
                renderer.camera.pan(dx, dy);
                lastPanX = midX;
                lastPanY = midY;
            }
            return true;
        }

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = x;
                touchStartYVal = y;
                lastTouchX = x;
                lastTouchY = y;
                isDraggingGizmo = false;

                // Check if touch hits Gizmo axis first
                if (renderer.getSelectedItem() != null) {
                    Camera3D.Ray ray = renderer.camera.getTouchRay(x, y);
                    float dist = (float) Math.sqrt(
                            Math.pow(renderer.camera.eyeX - renderer.getSelectedItem().posX, 2) +
                            Math.pow(renderer.camera.eyeY - renderer.getSelectedItem().posY, 2) +
                            Math.pow(renderer.camera.eyeZ - renderer.getSelectedItem().posZ, 2)
                    );
                    int axis = renderer.gizmo.pickAxis(ray, renderer.getSelectedItem(), dist);
                    if (axis != TransformGizmo.AXIS_NONE) {
                        renderer.gizmo.setActiveAxis(axis);
                        isDraggingGizmo = true;
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = x - lastTouchX;
                float dy = y - lastTouchY;

                if (isDraggingGizmo && renderer.getSelectedItem() != null) {
                    renderer.gizmo.applyTransform(renderer.getSelectedItem(), renderer.gizmo.getActiveAxis(), dx, dy, renderer.camera);
                } else {
                    // Orbit camera
                    renderer.camera.orbit(-dx * 0.25f, -dy * 0.25f);
                }

                lastTouchX = x;
                lastTouchY = y;
                break;

            case MotionEvent.ACTION_UP:
                float totalMove = (float) Math.hypot(x - touchStartX, y - touchStartYVal);
                if (totalMove < 15.0f && !isDraggingGizmo) {
                    // Tap detected: select object
                    renderer.pickObject(x, y);
                }
                renderer.gizmo.setActiveAxis(TransformGizmo.AXIS_NONE);
                isDraggingGizmo = false;
                break;
        }

        return true;
    }
}
