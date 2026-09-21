package com.gtasa.mapeditor.render;

import android.opengl.GLES20;

/**
 * GLSL Shader programs for GTA SA Map Editor 3D Viewport.
 */
public class Shaders {

    public static final String OBJECT_VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uModelMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "attribute vec3 aNormal;\n" +
            "varying vec2 vTexCoord;\n" +
            "varying vec3 vNormal;\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * aPosition;\n" +
            "    vTexCoord = aTexCoord;\n" +
            "    vNormal = normalize((uModelMatrix * vec4(aNormal, 0.0)).xyz);\n" +
            "}\n";

    public static final String OBJECT_FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform int uHasTexture;\n" +
            "uniform vec4 uColor;\n" +
            "varying vec2 vTexCoord;\n" +
            "varying vec3 vNormal;\n" +
            "void main() {\n" +
            "    vec3 lightDir = normalize(vec3(0.5, 0.8, 0.5));\n" +
            "    float diff = max(dot(vNormal, lightDir), 0.0);\n" +
            "    vec3 ambient = vec3(0.4, 0.4, 0.4);\n" +
            "    vec3 diffuse = vec3(0.6, 0.6, 0.6) * diff;\n" +
            "    vec3 lighting = ambient + diffuse;\n" +
            "    vec4 baseColor = uColor;\n" +
            "    if (uHasTexture == 1) {\n" +
            "        baseColor = texture2D(uTexture, vTexCoord) * uColor;\n" +
            "    }\n" +
            "    gl_FragColor = vec4(baseColor.rgb * lighting, baseColor.a);\n" +
            "}\n";

    public static final String GIZMO_VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * aPosition;\n" +
            "}\n";

    public static final String GIZMO_FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "uniform vec4 uColor;\n" +
            "void main() {\n" +
            "    gl_FragColor = uColor;\n" +
            "}\n";

    public static int createProgram(String vertexCode, String fragmentCode) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            GLES20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    private static int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
