package com.gtasa.mapeditor.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean-room parser for RenderWare 3D (.dff) models.
 * Converts raw RW chunks into OpenGL ES renderable meshes.
 */
public class DFFParser {

    private static final int CHUNK_STRUCT        = 0x0001;
    private static final int CHUNK_STRING        = 0x0002;
    private static final int CHUNK_TEXTURE       = 0x0006;
    private static final int CHUNK_MATERIAL      = 0x0007;
    private static final int CHUNK_MATERIALLIST  = 0x0008;
    private static final int CHUNK_FRAMELIST     = 0x000E;
    private static final int CHUNK_GEOMETRY      = 0x000F;
    private static final int CHUNK_CLUMP         = 0x0010;
    private static final int CHUNK_GEOMETRYLIST  = 0x001A;
    private static final int CHUNK_BIN_MESH      = 0x050E; // 1294
    private static final int CHUNK_NATIVE_DATA   = 0x0510; // 1296

    public static class SubMesh {
        public String textureName = "";
        public int startIndex;
        public int indexCount;
        public int glTextureId = 0;
    }

    public static class Mesh {
        public FloatBuffer vertexBuffer;
        public FloatBuffer uvBuffer;
        public FloatBuffer normalBuffer;
        public ShortBuffer indexBuffer;

        public int numVertices;
        public int numIndices;
        public final List<SubMesh> subMeshes = new ArrayList<>();

        public float minX, minY, minZ;
        public float maxX, maxY, maxZ;

        public float getCenterX() { return (minX + maxX) * 0.5f; }
        public float getCenterY() { return (minY + maxY) * 0.5f; }
        public float getCenterZ() { return (minZ + maxZ) * 0.5f; }

        public float getRadius() {
            float dx = maxX - minX;
            float dy = maxY - minY;
            float dz = maxZ - minZ;
            return (float) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.5);
        }
    }

    public static Mesh parse(byte[] dffData) {
        if (dffData == null || dffData.length < 12) {
            return createFallbackCube();
        }

        try {
            ByteBuffer buf = ByteBuffer.wrap(dffData).order(ByteOrder.LITTLE_ENDIAN);
            Header clumpHdr = readHeader(buf);
            if (clumpHdr.type != CHUNK_CLUMP) {
                return createFallbackCube();
            }

            // Find GeometryList chunk
            while (buf.remaining() >= 12) {
                Header h = readHeader(buf);
                if (h.type == CHUNK_GEOMETRYLIST) {
                    return parseGeometryList(buf, h);
                } else {
                    buf.position(Math.min(buf.limit(), buf.position() + h.size));
                }
            }
        } catch (Exception e) {
            // Safe fallback if format contains unexpected mods
        }

        return createFallbackCube();
    }

    private static Mesh parseGeometryList(ByteBuffer buf, Header geomListHdr) {
        Header structHdr = readHeader(buf);
        int numGeometries = buf.getInt();

        if (numGeometries <= 0 || buf.remaining() < 12) {
            return createFallbackCube();
        }

        // Parse first geometry
        Header geomHdr = readHeader(buf);
        if (geomHdr.type != CHUNK_GEOMETRY) {
            return createFallbackCube();
        }

        Header geomStructHdr = readHeader(buf);
        int flags = buf.getShort() & 0xFFFF;
        int numTexCoordSets = buf.get() & 0xFF;
        boolean hasNativeGeometry = (buf.get() & 0xFF) != 0;
        int numTriangles = buf.getInt();
        int numVertices = buf.getInt();
        int numMorphTargets = buf.getInt();

        boolean hasTexCoords = (flags & 0x0004) != 0 || numTexCoordSets > 0;
        boolean hasVertexColors = (flags & 0x0008) != 0;
        boolean hasNormals = (flags & 0x0010) != 0;

        float[] vertices = new float[numVertices * 3];
        float[] uvs = new float[numVertices * 2];
        float[] normals = new float[numVertices * 3];
        List<Short> indexList = new ArrayList<>();
        List<String> materialTextures = new ArrayList<>();

        if (!hasNativeGeometry) {
            if (hasVertexColors) {
                buf.position(buf.position() + numVertices * 4); // skip RGBA colors
            }
            if (hasTexCoords) {
                for (int i = 0; i < numVertices * 2; i++) {
                    uvs[i] = buf.getFloat();
                }
                // Skip extra UV sets if any
                if (numTexCoordSets > 1) {
                    buf.position(buf.position() + numVertices * 2 * 4 * (numTexCoordSets - 1));
                }
            }
            // Read triangles
            for (int i = 0; i < numTriangles; i++) {
                short v2 = buf.getShort();
                short v1 = buf.getShort();
                buf.getShort(); // mat id
                short v3 = buf.getShort();
                indexList.add(v1);
                indexList.add(v2);
                indexList.add(v3);
            }

            // Morph target (vertices + normals)
            buf.position(buf.position() + 16 + 4 + 4); // bounding sphere + hasVertices + hasNormals
            for (int i = 0; i < numVertices * 3; i++) {
                vertices[i] = buf.getFloat();
            }
            if (hasNormals) {
                for (int i = 0; i < numVertices * 3; i++) {
                    normals[i] = buf.getFloat();
                }
            }
        }

        // Next chunk: MaterialList
        if (buf.remaining() >= 12) {
            Header matListHdr = readHeader(buf);
            if (matListHdr.type == CHUNK_MATERIALLIST) {
                Header matStructHdr = readHeader(buf);
                int matCount = buf.getInt();
                buf.position(buf.position() + matCount * 4); // skip material indices

                for (int m = 0; m < matCount && buf.remaining() >= 12; m++) {
                    Header matHdr = readHeader(buf);
                    int matEndPos = buf.position() + matHdr.size;
                    String texName = "";

                    while (buf.position() < matEndPos && buf.remaining() >= 12) {
                        Header ch = readHeader(buf);
                        if (ch.type == CHUNK_TEXTURE) {
                            int texEnd = buf.position() + ch.size;
                            while (buf.position() < texEnd && buf.remaining() >= 12) {
                                Header subH = readHeader(buf);
                                if (subH.type == CHUNK_STRING) {
                                    byte[] strBytes = new byte[subH.size];
                                    buf.get(strBytes);
                                    int nullIdx = 0;
                                    while (nullIdx < strBytes.length && strBytes[nullIdx] != 0) nullIdx++;
                                    texName = new String(strBytes, 0, nullIdx, StandardCharsets.US_ASCII);
                                    break;
                                } else {
                                    buf.position(Math.min(buf.limit(), buf.position() + subH.size));
                                }
                            }
                            buf.position(texEnd);
                            break;
                        } else {
                            buf.position(Math.min(buf.limit(), buf.position() + ch.size));
                        }
                    }
                    materialTextures.add(texName);
                    buf.position(Math.min(buf.limit(), matEndPos));
                }
            }
        }

        // Construct Mesh object
        Mesh mesh = new Mesh();
        mesh.numVertices = numVertices;
        mesh.numIndices = indexList.size();

        mesh.minX = Float.MAX_VALUE; mesh.minY = Float.MAX_VALUE; mesh.minZ = Float.MAX_VALUE;
        mesh.maxX = -Float.MAX_VALUE; mesh.maxY = -Float.MAX_VALUE; mesh.maxZ = -Float.MAX_VALUE;

        for (int i = 0; i < numVertices; i++) {
            float vx = vertices[i * 3];
            float vy = vertices[i * 3 + 1];
            float vz = vertices[i * 3 + 2];
            mesh.minX = Math.min(mesh.minX, vx);
            mesh.maxX = Math.max(mesh.maxX, vx);
            mesh.minY = Math.min(mesh.minY, vy);
            mesh.maxY = Math.max(mesh.maxY, vy);
            mesh.minZ = Math.min(mesh.minZ, vz);
            mesh.maxZ = Math.max(mesh.maxZ, vz);
        }

        mesh.vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mesh.vertexBuffer.put(vertices).position(0);

        mesh.uvBuffer = ByteBuffer.allocateDirect(uvs.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mesh.uvBuffer.put(uvs).position(0);

        mesh.normalBuffer = ByteBuffer.allocateDirect(normals.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mesh.normalBuffer.put(normals).position(0);

        short[] indexArr = new short[indexList.size()];
        for (int i = 0; i < indexList.size(); i++) {
            indexArr[i] = indexList.get(i);
        }
        mesh.indexBuffer = ByteBuffer.allocateDirect(indexArr.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        mesh.indexBuffer.put(indexArr).position(0);

        // Map submesh
        SubMesh sub = new SubMesh();
        sub.startIndex = 0;
        sub.indexCount = indexArr.length;
        if (!materialTextures.isEmpty()) {
            sub.textureName = materialTextures.get(0);
        }
        mesh.subMeshes.add(sub);

        return mesh;
    }

    public static Mesh createFallbackCube() {
        float[] cubeVerts = {
                -1f,-1f,-1f,  1f,-1f,-1f,  1f, 1f,-1f, -1f, 1f,-1f, // front
                -1f,-1f, 1f,  1f,-1f, 1f,  1f, 1f, 1f, -1f, 1f, 1f, // back
        };
        short[] cubeIndices = {
                0,1,2, 0,2,3,  4,6,5, 4,7,6,  4,0,3, 4,3,7,
                1,5,6, 1,6,2,  3,2,6, 3,6,7,  4,5,1, 4,1,0
        };
        float[] cubeUvs = new float[8 * 2];

        Mesh mesh = new Mesh();
        mesh.numVertices = 8;
        mesh.numIndices = cubeIndices.length;
        mesh.minX = -1f; mesh.minY = -1f; mesh.minZ = -1f;
        mesh.maxX = 1f;  mesh.maxY = 1f;  mesh.maxZ = 1f;

        mesh.vertexBuffer = ByteBuffer.allocateDirect(cubeVerts.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mesh.vertexBuffer.put(cubeVerts).position(0);

        mesh.uvBuffer = ByteBuffer.allocateDirect(cubeUvs.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mesh.uvBuffer.put(cubeUvs).position(0);

        mesh.indexBuffer = ByteBuffer.allocateDirect(cubeIndices.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        mesh.indexBuffer.put(cubeIndices).position(0);

        SubMesh sub = new SubMesh();
        sub.startIndex = 0;
        sub.indexCount = cubeIndices.length;
        mesh.subMeshes.add(sub);
        return mesh;
    }

    private static class Header {
        int type;
        int size;
        int version;
    }

    private static Header readHeader(ByteBuffer buf) {
        Header h = new Header();
        h.type = buf.getInt();
        h.size = buf.getInt();
        h.version = buf.getInt();
        return h;
    }
}
