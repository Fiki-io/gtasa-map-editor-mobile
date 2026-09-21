package com.gtasa.mapeditor.render;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.gtasa.mapeditor.core.IPLParser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 3D Interactive Transform Gizmo for moving and rotating selected map objects.
 */
public class TransformGizmo {

    public static final int AXIS_NONE  = 0;
    public static final int AXIS_X     = 1;
    public static final int AXIS_Y     = 2;
    public static final int AXIS_Z     = 3;
    public static final int AXIS_ROT_Z = 4;

    private int activeAxis = AXIS_NONE;
    private final FloatBuffer axisVertexBuffer;
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    public float gizmoScale = 3.0f;

    public TransformGizmo() {
        // Vertex lines for 3 axes: X(red), Y(green), Z(blue)
        float[] lines = {
                // X axis
                0.0f, 0.0f, 0.0f,  1.0f, 0.0f, 0.0f,
                // Y axis
                0.0f, 0.0f, 0.0f,  0.0f, 1.0f, 0.0f,
                // Z axis
                0.0f, 0.0f, 0.0f,  0.0f, 0.0f, 1.0f,
        };

        axisVertexBuffer = ByteBuffer.allocateDirect(lines.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        axisVertexBuffer.put(lines).position(0);
    }

    public void render(int gizmoProgram, Camera3D camera, IPLParser.IPLItem item) {
        if (item == null) return;

        GLES20.glUseProgram(gizmoProgram);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST); // Draw gizmo on top
        GLES20.glLineWidth(8.0f);

        int posHandle = GLES20.glGetAttribLocation(gizmoProgram, "aPosition");
        int mvpHandle = GLES20.glGetUniformLocation(gizmoProgram, "uMVPMatrix");
        int colorHandle = GLES20.glGetUniformLocation(gizmoProgram, "uColor");

        GLES20.glEnableVertexAttribArray(posHandle);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, axisVertexBuffer);

        // Adjust scale relative to camera distance so gizmo remains visible
        float dist = (float) Math.sqrt(
                Math.pow(camera.eyeX - item.posX, 2) +
                Math.pow(camera.eyeY - item.posY, 2) +
                Math.pow(camera.eyeZ - item.posZ, 2)
        );
        float dynamicScale = Math.max(1.0f, dist * 0.15f);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, item.posX, item.posY, item.posZ);
        Matrix.scaleM(modelMatrix, 0, dynamicScale, dynamicScale, dynamicScale);
        Matrix.multiplyMM(mvpMatrix, 0, camera.viewProjMatrix, 0, modelMatrix, 0);

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);

        // Draw X axis (Red)
        GLES20.glUniform4f(colorHandle, activeAxis == AXIS_X ? 1.0f : 0.9f, 0.1f, 0.1f, 1.0f);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);

        // Draw Y axis (Green)
        GLES20.glUniform4f(colorHandle, 0.1f, activeAxis == AXIS_Y ? 1.0f : 0.9f, 0.1f, 1.0f);
        GLES20.glDrawArrays(GLES20.GL_LINES, 2, 2);

        // Draw Z axis (Blue)
        GLES20.glUniform4f(colorHandle, 0.1f, 0.3f, activeAxis == AXIS_Z ? 1.0f : 0.9f, 1.0f);
        GLES20.glDrawArrays(GLES20.GL_LINES, 4, 2);

        GLES20.glDisableVertexAttribArray(posHandle);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    public int pickAxis(Camera3D.Ray ray, IPLParser.IPLItem item, float cameraDist) {
        if (item == null) return AXIS_NONE;

        float threshold = Math.max(0.5f, cameraDist * 0.04f);

        // Point on ray closest to X axis segment
        float distToX = distanceRayToSegment(ray,
                item.posX, item.posY, item.posZ,
                item.posX + threshold * 4, item.posY, item.posZ);

        float distToY = distanceRayToSegment(ray,
                item.posX, item.posY, item.posZ,
                item.posX, item.posY + threshold * 4, item.posZ);

        float distToZ = distanceRayToSegment(ray,
                item.posX, item.posY, item.posZ,
                item.posX, item.posY, item.posZ + threshold * 4);

        float minDist = Math.min(distToX, Math.min(distToY, distToZ));

        if (minDist < threshold) {
            if (minDist == distToX) return AXIS_X;
            if (minDist == distToY) return AXIS_Y;
            return AXIS_Z;
        }

        return AXIS_NONE;
    }

    public void applyTransform(IPLParser.IPLItem item, int axis, float deltaScreenX, float deltaScreenY, Camera3D camera) {
        if (item == null || axis == AXIS_NONE) return;

        float sensitivity = camera.distance * 0.0015f;
        double radYaw = Math.toRadians(camera.yaw);

        switch (axis) {
            case AXIS_X:
                item.posX += (float) (deltaScreenX * Math.cos(radYaw) - deltaScreenY * Math.sin(radYaw)) * sensitivity;
                item.isModified = true;
                break;
            case AXIS_Y:
                item.posY += (float) (deltaScreenX * Math.sin(radYaw) + deltaScreenY * Math.cos(radYaw)) * sensitivity;
                item.isModified = true;
                break;
            case AXIS_Z:
                item.posZ -= deltaScreenY * sensitivity;
                item.isModified = true;
                break;
            case AXIS_ROT_Z:
                // Rotate quaternion around Z axis
                double angleDelta = Math.toRadians(deltaScreenX * 0.5f);
                float sinHalf = (float) Math.sin(angleDelta * 0.5);
                float cosHalf = (float) Math.cos(angleDelta * 0.5);

                float newRotZ = item.rotZ * cosHalf + item.rotW * sinHalf;
                float newRotW = item.rotW * cosHalf - item.rotZ * sinHalf;
                item.rotZ = newRotZ;
                item.rotW = newRotW;
                item.isModified = true;
                break;
        }
    }

    public void setActiveAxis(int axis) {
        this.activeAxis = axis;
    }

    public int getActiveAxis() {
        return activeAxis;
    }

    private float distanceRayToSegment(Camera3D.Ray ray, float x1, float y1, float z1, float x2, float y2, float z2) {
        // Approximate distance from ray to 3D segment midpoint
        float mx = (x1 + x2) * 0.5f;
        float my = (y1 + y2) * 0.5f;
        float mz = (z1 + z2) * 0.5f;

        float vx = mx - ray.origin[0];
        float vy = my - ray.origin[1];
        float vz = mz - ray.origin[2];

        float proj = vx * ray.direction[0] + vy * ray.direction[1] + vz * ray.direction[2];
        if (proj < 0) return Float.MAX_VALUE;

        float px = ray.origin[0] + ray.direction[0] * proj;
        float py = ray.origin[1] + ray.direction[1] * proj;
        float pz = ray.origin[2] + ray.direction[2] * proj;

        float dx = px - mx;
        float dy = py - my;
        float dz = pz - mz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
