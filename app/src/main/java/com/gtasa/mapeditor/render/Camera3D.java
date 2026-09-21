package com.gtasa.mapeditor.render;

import android.opengl.Matrix;

/**
 * 3D Camera for GTA SA Map coordinates (Z is Up, X is East, Y is North).
 */
public class Camera3D {

    public float targetX = 2000.0f;
    public float targetY = -1500.0f;
    public float targetZ = 15.0f;

    public float yaw = 45.0f;   // degrees around Z axis
    public float pitch = 30.0f; // degrees elevation from horizontal plane
    public float distance = 40.0f;

    public float eyeX, eyeY, eyeZ;

    public final float[] viewMatrix = new float[16];
    public final float[] projMatrix = new float[16];
    public final float[] viewProjMatrix = new float[16];

    public int viewportWidth = 1280;
    public int viewportHeight = 720;

    public Camera3D() {
        updateMatrices();
    }

    public void setViewport(int width, int height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
        float aspect = (float) width / Math.max(1, height);
        Matrix.perspectiveM(projMatrix, 0, 60.0f, aspect, 0.5f, 2000.0f);
        updateMatrices();
    }

    public void orbit(float deltaYaw, float deltaPitch) {
        yaw += deltaYaw;
        pitch += deltaPitch;

        // Clamp pitch to avoid gimbal lock / flipping upside down
        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;

        updateMatrices();
    }

    public void pan(float deltaScreenX, float deltaScreenY) {
        double radYaw = Math.toRadians(yaw);
        float forwardX = (float) -Math.sin(radYaw);
        float forwardY = (float) Math.cos(radYaw);

        float rightX = (float) Math.cos(radYaw);
        float rightY = (float) Math.sin(radYaw);

        float panSpeed = distance * 0.0015f;

        targetX += (-rightX * deltaScreenX + forwardX * deltaScreenY) * panSpeed;
        targetY += (-rightY * deltaScreenX + forwardY * deltaScreenY) * panSpeed;

        updateMatrices();
    }

    public void zoom(float scaleFactor) {
        distance /= scaleFactor;
        if (distance < 2.0f) distance = 2.0f;
        if (distance > 1000.0f) distance = 1000.0f;
        updateMatrices();
    }

    public void teleportTo(float x, float y, float z) {
        targetX = x;
        targetY = y;
        targetZ = z;
        updateMatrices();
    }

    public void updateMatrices() {
        double radYaw = Math.toRadians(yaw);
        double radPitch = Math.toRadians(pitch);

        float cosPitch = (float) Math.cos(radPitch);
        float sinPitch = (float) Math.sin(radPitch);
        float cosYaw = (float) Math.cos(radYaw);
        float sinYaw = (float) Math.sin(radYaw);

        eyeX = targetX + distance * cosPitch * sinYaw;
        eyeY = targetY - distance * cosPitch * cosYaw;
        eyeZ = targetZ + distance * sinPitch;

        // In GTA SA world, Z is UP
        Matrix.setLookAtM(viewMatrix, 0,
                eyeX, eyeY, eyeZ,
                targetX, targetY, targetZ,
                0.0f, 0.0f, 1.0f);

        Matrix.multiplyMM(viewProjMatrix, 0, projMatrix, 0, viewMatrix, 0);
    }

    public static class Ray {
        public final float[] origin = new float[3];
        public final float[] direction = new float[3];
    }

    /**
     * Unprojects a 2D screen coordinate into a 3D Ray in world space.
     */
    public Ray getTouchRay(float screenX, float screenY) {
        float x = (2.0f * screenX) / viewportWidth - 1.0f;
        float y = 1.0f - (2.0f * screenY) / viewportHeight;

        float[] invViewProj = new float[16];
        Matrix.invertM(invViewProj, 0, viewProjMatrix, 0);

        float[] nearPoint = new float[]{x, y, -1.0f, 1.0f};
        float[] farPoint = new float[]{x, y, 1.0f, 1.0f};

        float[] nearResult = new float[4];
        float[] farResult = new float[4];

        Matrix.multiplyMV(nearResult, 0, invViewProj, 0, nearPoint, 0);
        Matrix.multiplyMV(farResult, 0, invViewProj, 0, farPoint, 0);

        Ray ray = new Ray();
        ray.origin[0] = nearResult[0] / nearResult[3];
        ray.origin[1] = nearResult[1] / nearResult[3];
        ray.origin[2] = nearResult[2] / nearResult[3];

        float fx = farResult[0] / farResult[3];
        float fy = farResult[1] / farResult[3];
        float fz = farResult[2] / farResult[3];

        float dx = fx - ray.origin[0];
        float dy = fy - ray.origin[1];
        float dz = fz - ray.origin[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        ray.direction[0] = dx / len;
        ray.direction[1] = dy / len;
        ray.direction[2] = dz / len;

        return ray;
    }
}
