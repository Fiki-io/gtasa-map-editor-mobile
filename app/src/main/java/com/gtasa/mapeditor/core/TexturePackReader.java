package com.gtasa.mapeditor.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.ETC1;
import android.opengl.ETC1Util;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Reader for GTA SA Texture Pack (textures.tpk) and external image fallback (PNG/JPG).
 */
public class TexturePackReader implements AutoCloseable {

    public static class TextureInfo {
        public String name;
        public String alias;
        public boolean alpha;
        public int width;
        public int height;
        public long fileOffset;
        public int dataSize;

        public boolean isAlias() {
            return alias != null && !alias.isEmpty();
        }
    }

    private final File tpkFile;
    private final File customTexturesDir;
    private RandomAccessFile raf;
    private final Map<String, TextureInfo> textureMap = new HashMap<>();
    private final Map<String, Integer> glTextureCache = new HashMap<>();

    public TexturePackReader(File tpkFile, File customTexturesDir) {
        this.tpkFile = tpkFile;
        this.customTexturesDir = customTexturesDir;
    }

    public void open() throws IOException {
        close();
        if (tpkFile == null || !tpkFile.exists()) {
            return;
        }

        raf = new RandomAccessFile(tpkFile, "r");
        byte[] countBytes = new byte[2];
        raf.readFully(countBytes);
        int numTextures = ((countBytes[1] & 0xFF) << 8) | (countBytes[0] & 0xFF);

        long currentOffset = 2;

        for (int i = 0; i < numTextures; i++) {
            int nameLen = raf.read();
            byte[] nameBytes = new byte[nameLen];
            raf.readFully(nameBytes);
            String name = new String(nameBytes, StandardCharsets.US_ASCII).toLowerCase();

            int aliasLen = raf.read();
            byte[] aliasBytes = new byte[aliasLen];
            raf.readFully(aliasBytes);
            String alias = aliasLen > 0 ? new String(aliasBytes, StandardCharsets.US_ASCII).toLowerCase() : "";

            currentOffset += 2 + nameLen + aliasLen;

            TextureInfo info = new TextureInfo();
            info.name = name;
            info.alias = alias;

            if (aliasLen == 0) {
                info.alpha = raf.read() != 0;

                byte[] wh = new byte[4];
                raf.readFully(wh);
                info.width = ((wh[1] & 0xFF) << 8) | (wh[0] & 0xFF);
                info.height = ((wh[3] & 0xFF) << 8) | (wh[2] & 0xFF);

                byte[] sizeBytes = new byte[4];
                raf.readFully(sizeBytes);
                info.dataSize = ((sizeBytes[3] & 0xFF) << 24) | ((sizeBytes[2] & 0xFF) << 16)
                        | ((sizeBytes[1] & 0xFF) << 8) | (sizeBytes[0] & 0xFF);

                currentOffset += 9;
                info.fileOffset = currentOffset;

                // Skip the image data in the stream to move to next entry
                raf.seek(currentOffset + info.dataSize);
                currentOffset += info.dataSize;
            }

            textureMap.put(name, info);
        }
    }

    public int getTextureCount() {
        return textureMap.size();
    }

    /**
     * Resolves the texture ID for OpenGL ES. Caches uploaded textures in GPU memory.
     */
    public synchronized int getOrCreateGLTexture(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return 0;
        }

        String name = rawName.toLowerCase();
        if (glTextureCache.containsKey(name)) {
            return glTextureCache.get(name);
        }

        // 1. Check if external PNG/JPG exists first (allows modding & replacement)
        if (customTexturesDir != null && customTexturesDir.exists()) {
            File pngFile = new File(customTexturesDir, name + ".png");
            if (!pngFile.exists()) {
                pngFile = new File(customTexturesDir, name + ".jpg");
            }
            if (pngFile.exists()) {
                try {
                    Bitmap bmp = BitmapFactory.decodeFile(pngFile.getAbsolutePath());
                    if (bmp != null) {
                        int texId = uploadBitmapToGL(bmp);
                        bmp.recycle();
                        glTextureCache.put(name, texId);
                        return texId;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2. Load from textures.tpk
        TextureInfo info = textureMap.get(name);
        if (info == null) {
            return 0;
        }

        // Handle alias
        if (info.isAlias()) {
            return getOrCreateGLTexture(info.alias);
        }

        try {
            int texId = uploadTPKTextureToGL(info);
            glTextureCache.put(name, texId);
            return texId;
        } catch (Exception e) {
            return 0;
        }
    }

    private int uploadBitmapToGL(Bitmap bitmap) {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int texId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);

        return texId;
    }

    private int uploadTPKTextureToGL(TextureInfo info) throws IOException {
        if (raf == null) return 0;

        raf.seek(info.fileOffset);
        byte[] rawData = new byte[info.dataSize];
        raf.readFully(rawData);

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int texId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

        if (!info.alpha) {
            // ETC1 Compressed texture
            ByteBuffer buffer = ByteBuffer.allocateDirect(rawData.length).order(ByteOrder.nativeOrder());
            buffer.put(rawData).position(0);
            GLES20.glCompressedTexImage2D(GLES20.GL_TEXTURE_2D, 0,
                    ETC1.ETC1_RGB8_OES, info.width, info.height, 0, rawData.length, buffer);
        } else {
            // RGBA4444 format (16-bit)
            ByteBuffer buffer = ByteBuffer.allocateDirect(rawData.length).order(ByteOrder.nativeOrder());
            buffer.put(rawData).position(0);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    info.width, info.height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_SHORT_4_4_4_4, buffer);
        }

        return texId;
    }

    public void clearGLCache() {
        for (int texId : glTextureCache.values()) {
            if (texId > 0) {
                GLES20.glDeleteTextures(1, new int[]{texId}, 0);
            }
        }
        glTextureCache.clear();
    }

    @Override
    public void close() {
        clearGLCache();
        if (raf != null) {
            try {
                raf.close();
            } catch (IOException ignored) {}
            raf = null;
        }
        textureMap.clear();
    }
}
