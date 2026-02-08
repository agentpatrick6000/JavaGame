package com.voxelgame.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;

/**
 * Compiles, links, and manages OpenGL shader programs.
 */
public class Shader {

    private int programId;
    private final Map<String, Integer> uniformCache = new HashMap<>();

    public Shader(String vertexPath, String fragmentPath) {
        String vertSrc = loadResource(vertexPath);
        String fragSrc = loadResource(fragmentPath);

        int vertShader = compileShader(vertSrc, GL_VERTEX_SHADER);
        int fragShader = compileShader(fragSrc, GL_FRAGMENT_SHADER);

        programId = glCreateProgram();
        glAttachShader(programId, vertShader);
        glAttachShader(programId, fragShader);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            throw new RuntimeException("Shader link error: " + log);
        }

        glDeleteShader(vertShader);
        glDeleteShader(fragShader);
    }

    private int compileShader(String source, int type) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            String typeName = type == GL_VERTEX_SHADER ? "vertex" : "fragment";
            throw new RuntimeException("Shader compile error (" + typeName + "): " + log);
        }
        return shader;
    }

    private String loadResource(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Shader resource not found: " + path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader: " + path, e);
        }
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    private int getUniformLocation(String name) {
        return uniformCache.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    public void setMat4(String name, Matrix4f matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            matrix.get(fb);
            glUniformMatrix4fv(getUniformLocation(name), false, fb);
        }
    }

    public void setInt(String name, int value) {
        glUniform1i(getUniformLocation(name), value);
    }

    public void setFloat(String name, float value) {
        glUniform1f(getUniformLocation(name), value);
    }

    public void setVec3(String name, float x, float y, float z) {
        glUniform3f(getUniformLocation(name), x, y, z);
    }

    public void setVec3(String name, Vector3f v) {
        glUniform3f(getUniformLocation(name), v.x, v.y, v.z);
    }

    public void setVec4(String name, float x, float y, float z, float w) {
        glUniform4f(getUniformLocation(name), x, y, z, w);
    }

    public void setVec2(String name, float x, float y) {
        glUniform2f(getUniformLocation(name), x, y);
    }

    public void cleanup() {
        glDeleteProgram(programId);
    }

    public int getProgramId() { return programId; }
    
    /** Get uniform location (for audit/debug). Returns -1 if not found. */
    public int queryUniformLocation(String name) {
        return glGetUniformLocation(programId, name);
    }
    
    /** Get current float uniform value (for audit). Requires program to be bound. */
    public float getUniformFloat(String name) {
        int loc = getUniformLocation(name);
        if (loc < 0) return Float.NaN;
        float[] val = new float[1];
        glGetUniformfv(programId, loc, val);
        return val[0];
    }
    
    /** Get current vec3 uniform value (for audit). Requires program to be bound. */
    public float[] getUniformVec3(String name) {
        int loc = getUniformLocation(name);
        if (loc < 0) return new float[]{Float.NaN, Float.NaN, Float.NaN};
        float[] val = new float[3];
        glGetUniformfv(programId, loc, val);
        return val;
    }
    
    /** Get current int uniform value (for audit). Requires program to be bound. */
    public int getUniformInt(String name) {
        int loc = getUniformLocation(name);
        if (loc < 0) return -999;
        int[] val = new int[1];
        glGetUniformiv(programId, loc, val);
        return val[0];
    }
}
