package com.gtasa.mapeditor.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.gtasa.mapeditor.core.IPLParser;
import com.gtasa.mapeditor.render.Camera3D;

import java.io.File;

/**
 * 2D Interactive Radar Minimap using GTA SA mapa.png.
 * World coordinate range: -3000 to +3000 on both X and Y.
 */
public class RadarOverlayView extends View {

    private Bitmap mapBitmap;
    private final Paint blipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Camera3D camera;
    private IPLParser.IPLItem selectedItem;

    public interface OnTeleportListener {
        void onTeleport(float worldX, float worldY);
    }

    private OnTeleportListener teleportListener;

    public RadarOverlayView(Context context) {
        this(context, null);
    }

    public RadarOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        blipPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3.0f);
    }

    public void setMapImage(File mapaPngFile) {
        if (mapaPngFile != null && mapaPngFile.exists()) {
            try {
                mapBitmap = BitmapFactory.decodeFile(mapaPngFile.getAbsolutePath());
                invalidate();
            } catch (Exception ignored) {}
        }
    }

    public void setCamera(Camera3D camera) {
        this.camera = camera;
    }

    public void setSelectedItem(IPLParser.IPLItem item) {
        this.selectedItem = item;
        invalidate();
    }

    public void setOnTeleportListener(OnTeleportListener listener) {
        this.teleportListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // Draw background map
        if (mapBitmap != null) {
            canvas.drawBitmap(mapBitmap, null, new android.graphics.Rect(0, 0, w, h), null);
        } else {
            canvas.drawColor(Color.parseColor("#1B2A38"));
        }

        canvas.drawRect(0, 0, w, h, borderPaint);

        // Draw Selected Object Blip (Yellow)
        if (selectedItem != null && !selectedItem.isDeleted) {
            float ox = worldToRadarX(selectedItem.posX, w);
            float oy = worldToRadarY(selectedItem.posY, h);
            blipPaint.setColor(Color.YELLOW);
            canvas.drawCircle(ox, oy, 7.0f, blipPaint);
        }

        // Draw Camera Blip (Red)
        if (camera != null) {
            float cx = worldToRadarX(camera.targetX, w);
            float cy = worldToRadarY(camera.targetY, h);

            blipPaint.setColor(Color.RED);
            canvas.drawCircle(cx, cy, 6.0f, blipPaint);

            // Draw direction triangle/line
            double rad = Math.toRadians(camera.yaw);
            float dirX = cx - (float) Math.sin(rad) * 16.0f;
            float dirY = cy - (float) Math.cos(rad) * 16.0f;
            canvas.drawLine(cx, cy, dirX, dirY, blipPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();

            float worldX = radarToWorldX(x, getWidth());
            float worldY = radarToWorldY(y, getHeight());

            if (teleportListener != null) {
                teleportListener.onTeleport(worldX, worldY);
            }
            invalidate();
            return true;
        }
        return true;
    }

    private float worldToRadarX(float wx, int width) {
        return ((wx + 3000.0f) / 6000.0f) * width;
    }

    private float worldToRadarY(float wy, int height) {
        return ((3000.0f - wy) / 6000.0f) * height;
    }

    private float radarToWorldX(float rx, int width) {
        return (rx / width) * 6000.0f - 3000.0f;
    }

    private float radarToWorldY(float ry, int height) {
        return 3000.0f - (ry / height) * 6000.0f;
    }
}
