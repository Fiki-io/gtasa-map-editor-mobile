package com.gtasa.mapeditor.render;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import com.gtasa.mapeditor.core.DFFParser;
import com.gtasa.mapeditor.core.IDEParser;
import com.gtasa.mapeditor.core.IMGArchive;
import com.gtasa.mapeditor.core.IPLParser;
import com.gtasa.mapeditor.core.TexturePackReader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * High-performance OpenGL ES 2.0 Renderer for GTA SA Map Editor.
 */
public class MapRenderer implements GLSurfaceView.Renderer {

    public final Camera3D camera = new Camera3D();
    public final TransformGizmo gizmo = new TransformGizmo();

    private int objectProgram;
    private int gizmoProgram;

    private IPLParser iplParser;
    private IDEParser ideParser;
    private IMGArchive imgArchive;
    private TexturePackReader texturePackReader;

    private final Map<String, DFFParser.Mesh> modelCache = new HashMap<>();
    private IPLParser.IPLItem selectedItem = null;

    private FloatBuffer gridBuffer;
    private int gridVertexCount = 0;

    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    public float maxDrawDistance = 400.0f;

    public interface OnObjectSelectedListener {
        void onObjectSelected(IPLParser.IPLItem item);
    }

    private OnObjectSelectedListener selectionListener;

    public void setSelectionListener(OnObjectSelectedListener listener) {
        this.selectionListener = listener;
    }

    public void setDataSources(IPLParser ipl, IDEParser ide, IMGArchive img, TexturePackReader tpk) {
        this.iplParser = ipl;
        this.ideParser = ide;
        this.imgArchive = img;
        this.texturePackReader = tpk;
        this.selectedItem = null;
        this.modelCache.clear();
    }

    public void setSelectedItem(IPLParser.IPLItem item) {
        this.selectedItem = item;
    }

    public IPLParser.IPLItem getSelectedItem() {
        return selectedItem;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.12f, 0.12f, 0.14f, 1.0f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        objectProgram = Shaders.createProgram(Shaders.OBJECT_VERTEX_SHADER, Shaders.OBJECT_FRAGMENT_SHADER);
        gizmoProgram = Shaders.createProgram(Shaders.GIZMO_VERTEX_SHADER, Shaders.GIZMO_FRAGMENT_SHADER);

        buildGroundGrid();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        camera.setViewport(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        camera.updateMatrices();

        renderGroundGrid();

        if (iplParser != null) {
            renderMapObjects();
        }

        if (selectedItem != null && !selectedItem.isDeleted) {
            gizmo.render(gizmoProgram, camera, selectedItem);
        }
    }

    private void renderMapObjects() {
        GLES20.glUseProgram(objectProgram);

        int posHandle = GLES20.glGetAttribLocation(objectProgram, "aPosition");
        int texHandle = GLES20.glGetAttribLocation(objectProgram, "aTexCoord");
        int normHandle = GLES20.glGetAttribLocation(objectProgram, "aNormal");

        int mvpHandle = GLES20.glGetUniformLocation(objectProgram, "uMVPMatrix");
        int modelHandle = GLES20.glGetUniformLocation(objectProgram, "uModelMatrix");
        int colorHandle = GLES20.glGetUniformLocation(objectProgram, "uColor");
        int hasTexHandle = GLES20.glGetUniformLocation(objectProgram, "uHasTexture");
        int texSamplerHandle = GLES20.glGetUniformLocation(objectProgram, "uTexture");

        List<IPLParser.IPLItem> items = iplParser.getItems();

        for (int i = 0; i < items.size(); i++) {
            IPLParser.IPLItem item = items.get(i);
            if (item.isDeleted) continue;

            // Distance culling
            float distSq = (item.posX - camera.targetX) * (item.posX - camera.targetX)
                    + (item.posY - camera.targetY) * (item.posY - camera.targetY);
            if (distSq > maxDrawDistance * maxDrawDistance) {
                continue;
            }

            DFFParser.Mesh mesh = getOrLoadMesh(item.modelName);
            if (mesh == null) continue;

            // Calculate Model Matrix (Position + Quaternion Rotation)
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.translateM(modelMatrix, 0, item.posX, item.posY, item.posZ);

            // Quaternion to rotation matrix
            float qx = item.rotX, qy = item.rotY, qz = item.rotZ, qw = item.rotW;
            float[] rotM = new float[]{
                    1 - 2*qy*qy - 2*qz*qz, 2*qx*qy - 2*qz*qw,     2*qx*qz + 2*qy*qw,     0,
                    2*qx*qy + 2*qz*qw,     1 - 2*qx*qx - 2*qz*qz, 2*qy*qz - 2*qx*qw,     0,
                    2*qx*qz - 2*qy*qw,     2*qy*qz + 2*qx*qw,     1 - 2*qx*qx - 2*qy*qy, 0,
                    0,                     0,                     0,                     1
            };
            Matrix.multiplyMM(modelMatrix, 0, modelMatrix, 0, rotM, 0);

            Matrix.multiplyMM(mvpMatrix, 0, camera.viewProjMatrix, 0, modelMatrix, 0);

            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, modelMatrix, 0);

            // Highlight selected item
            boolean isSelected = (item == selectedItem);
            if (isSelected) {
                GLES20.glUniform4f(colorHandle, 1.2f, 1.2f, 0.3f, 1.0f);
            } else {
                GLES20.glUniform4f(colorHandle, 1.0f, 1.0f, 1.0f, 1.0f);
            }

            // Bind vertex buffers
            GLES20.glEnableVertexAttribArray(posHandle);
            mesh.vertexBuffer.position(0);
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, mesh.vertexBuffer);

            if (mesh.uvBuffer != null) {
                GLES20.glEnableVertexAttribArray(texHandle);
                mesh.uvBuffer.position(0);
                GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, mesh.uvBuffer);
            }

            if (mesh.normalBuffer != null) {
                GLES20.glEnableVertexAttribArray(normHandle);
                mesh.normalBuffer.position(0);
                GLES20.glVertexAttribPointer(normHandle, 3, GLES20.GL_FLOAT, false, 0, mesh.normalBuffer);
            }

            // Draw SubMeshes
            for (DFFParser.SubMesh subMesh : mesh.subMeshes) {
                int texId = 0;
                if (texturePackReader != null && subMesh.textureName != null && !subMesh.textureName.isEmpty()) {
                    texId = texturePackReader.getOrCreateGLTexture(subMesh.textureName);
                }

                if (texId > 0) {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
                    GLES20.glUniform1i(texSamplerHandle, 0);
                    GLES20.glUniform1i(hasTexHandle, 1);
                } else {
                    GLES20.glUniform1i(hasTexHandle, 0);
                }

                mesh.indexBuffer.position(subMesh.startIndex);
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, subMesh.indexCount, GLES20.GL_UNSIGNED_SHORT, mesh.indexBuffer);
            }

            GLES20.glDisableVertexAttribArray(posHandle);
            if (mesh.uvBuffer != null) GLES20.glDisableVertexAttribArray(texHandle);
            if (mesh.normalBuffer != null) GLES20.glDisableVertexAttribArray(normHandle);
        }
    }

    private DFFParser.Mesh getOrLoadMesh(String modelName) {
        if (modelName == null || modelName.isEmpty()) return null;
        String key = modelName.toLowerCase();
        if (modelCache.containsKey(key)) {
            return modelCache.get(key);
        }

        DFFParser.Mesh mesh = null;
        if (imgArchive != null) {
            try {
                byte[] dffData = imgArchive.readEntry(key + ".dff");
                if (dffData != null) {
                    mesh = DFFParser.parse(dffData);
                }
            } catch (Exception ignored) {}
        }

        if (mesh == null) {
            mesh = DFFParser.createFallbackCube();
        }

        modelCache.put(key, mesh);
        return mesh;
    }

    private void renderGroundGrid() {
        GLES20.glUseProgram(gizmoProgram);
        int posHandle = GLES20.glGetAttribLocation(gizmoProgram, "aPosition");
        int mvpHandle = GLES20.glGetUniformLocation(gizmoProgram, "uMVPMatrix");
        int colorHandle = GLES20.glGetUniformLocation(gizmoProgram, "uColor");

        GLES20.glEnableVertexAttribArray(posHandle);
        gridBuffer.position(0);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, gridBuffer);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, camera.viewProjMatrix, 0, modelMatrix, 0);
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
        GLES20.glUniform4f(colorHandle, 0.25f, 0.25f, 0.28f, 0.8f);

        GLES20.glLineWidth(1.0f);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, gridVertexCount);

        GLES20.glDisableVertexAttribArray(posHandle);
    }

    private void buildGroundGrid() {
        int size = 500;
        int step = 20;
        int lines = (size * 2 / step + 1) * 2;
        float[] verts = new float[lines * 2 * 3];
        int idx = 0;

        for (int i = -size; i <= size; i += step) {
            // Line along X
            verts[idx++] = -size; verts[idx++] = i; verts[idx++] = 0;
            verts[idx++] = size;  verts[idx++] = i; verts[idx++] = 0;
            // Line along Y
            verts[idx++] = i; verts[idx++] = -size; verts[idx++] = 0;
            verts[idx++] = i; verts[idx++] = size;  verts[idx++] = 0;
        }

        gridVertexCount = idx / 3;
        gridBuffer = ByteBuffer.allocateDirect(verts.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        gridBuffer.put(verts).position(0);
    }

    public boolean pickObject(float screenX, float screenY) {
        if (iplParser == null) return false;

        Camera3D.Ray ray = camera.getTouchRay(screenX, screenY);
        List<IPLParser.IPLItem> items = iplParser.getItems();

        IPLParser.IPLItem closestItem = null;
        float closestDist = Float.MAX_VALUE;

        for (IPLParser.IPLItem item : items) {
            if (item.isDeleted) continue;

            DFFParser.Mesh mesh = getOrLoadMesh(item.modelName);
            float radius = (mesh != null) ? Math.max(2.0f, mesh.getRadius()) : 3.0f;

            float vx = item.posX - ray.origin[0];
            float vy = item.posY - ray.origin[1];
            float vz = item.posZ - ray.origin[2];

            float proj = vx * ray.direction[0] + vy * ray.direction[1] + vz * ray.direction[2];
            if (proj > 0) {
                float px = ray.origin[0] + ray.direction[0] * proj;
                float py = ray.origin[1] + ray.direction[1] * proj;
                float pz = ray.origin[2] + ray.direction[2] * proj;

                float distToRay = (float) Math.sqrt(
                        Math.pow(px - item.posX, 2) +
                        Math.pow(py - item.posY, 2) +
                        Math.pow(pz - item.posZ, 2)
                );

                if (distToRay <= radius && proj < closestDist) {
                    closestDist = proj;
                    closestItem = item;
                }
            }
        }

        if (closestItem != null) {
            selectedItem = closestItem;
            if (selectionListener != null) {
                selectionListener.onObjectSelected(selectedItem);
            }
            return true;
        }

        return false;
    }
}
