package com.lunar_prototype.iron_horizon.client.render;

import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL30.*;

public final class FsrUpscaler {
    private int sceneFbo = 0;
    private int sceneColorTexture = 0;
    private int sceneDepthBuffer = 0;
    private int fsrProgram = 0;
    private int sceneWidth = 0;
    private int sceneHeight = 0;
    private int outputWidth = 0;
    private int outputHeight = 0;
    private boolean enabled = true;
    private FsrPreset preset = FsrPreset.QUALITY;
    private float sharpness = 0.2f;
    private boolean targetsDirty = true;

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            this.targetsDirty = true;
        }
    }

    public void setPreset(FsrPreset preset) {
        if (!Objects.equals(this.preset, preset)) {
            this.preset = preset;
            this.targetsDirty = true;
        }
    }

    public void setSharpness(float sharpness) {
        this.sharpness = clamp(sharpness, 0.0f, 2.0f);
    }

    public boolean isEnabled() {
        return enabled && preset != FsrPreset.OFF;
    }

    public float getRenderScale() {
        return isEnabled() ? preset.renderScale() : 1.0f;
    }

    public void init() {
        if (fsrProgram == 0) {
            fsrProgram = createProgram("/shaders/fsr_common.vert", "/shaders/fsr_mobile.frag");
        }
    }

    public void beginSceneRender(int framebufferWidth, int framebufferHeight) {
        if (!isEnabled()) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, framebufferWidth, framebufferHeight);
            return;
        }
        ensureTargets(framebufferWidth, framebufferHeight);
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo);
        glViewport(0, 0, sceneWidth, sceneHeight);
    }

    public void presentSceneToBackBuffer(int framebufferWidth, int framebufferHeight) {
        if (!isEnabled()) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, framebufferWidth, framebufferHeight);
            return;
        }
        ensureTargets(framebufferWidth, framebufferHeight);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, framebufferWidth, framebufferHeight);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);

        glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(fsrProgram);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneColorTexture);

        int sceneLoc = glGetUniformLocation(fsrProgram, "uScene");
        if (sceneLoc >= 0) {
            GL20.glUniform1i(sceneLoc, 0);
        }
        int inputSizeLoc = glGetUniformLocation(fsrProgram, "uInputSize");
        if (inputSizeLoc >= 0) {
            GL20.glUniform2f(inputSizeLoc, sceneWidth, sceneHeight);
        }
        int outputSizeLoc = glGetUniformLocation(fsrProgram, "uOutputSize");
        if (outputSizeLoc >= 0) {
            GL20.glUniform2f(outputSizeLoc, framebufferWidth, framebufferHeight);
        }
        int sharpnessLoc = glGetUniformLocation(fsrProgram, "uSharpness");
        if (sharpnessLoc >= 0) {
            GL20.glUniform1f(sharpnessLoc, clamp(sharpness, 0.0f, 2.0f));
        }
        uploadEasuConstants();

        glBegin(GL_QUADS);
        glTexCoord2f(0.0f, 0.0f);
        glVertex2f(-1.0f, -1.0f);
        glTexCoord2f(1.0f, 0.0f);
        glVertex2f(1.0f, -1.0f);
        glTexCoord2f(1.0f, 1.0f);
        glVertex2f(1.0f, 1.0f);
        glTexCoord2f(0.0f, 1.0f);
        glVertex2f(-1.0f, 1.0f);
        glEnd();

        glBindTexture(GL_TEXTURE_2D, 0);
        glUseProgram(0);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    public void cleanup() {
        if (sceneColorTexture != 0) {
            glDeleteTextures(sceneColorTexture);
            sceneColorTexture = 0;
        }
        if (sceneDepthBuffer != 0) {
            glDeleteRenderbuffers(sceneDepthBuffer);
            sceneDepthBuffer = 0;
        }
        if (sceneFbo != 0) {
            glDeleteFramebuffers(sceneFbo);
            sceneFbo = 0;
        }
        if (fsrProgram != 0) {
            GL20.glDeleteProgram(fsrProgram);
            fsrProgram = 0;
        }
    }

    private void ensureTargets(int framebufferWidth, int framebufferHeight) {
        int desiredSceneWidth = Math.max(1, Math.round(framebufferWidth * getRenderScale()));
        int desiredSceneHeight = Math.max(1, Math.round(framebufferHeight * getRenderScale()));
        if (!targetsDirty
                && sceneWidth == desiredSceneWidth
                && sceneHeight == desiredSceneHeight
                && outputWidth == framebufferWidth
                && outputHeight == framebufferHeight) {
            return;
        }
        deleteTargets();
        sceneWidth = desiredSceneWidth;
        sceneHeight = desiredSceneHeight;
        outputWidth = framebufferWidth;
        outputHeight = framebufferHeight;

        sceneFbo = glGenFramebuffers();
        sceneColorTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, sceneColorTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, sceneWidth, sceneHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        sceneDepthBuffer = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, sceneDepthBuffer);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, sceneWidth, sceneHeight);

        glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sceneColorTexture, 0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, sceneDepthBuffer);
        checkFramebuffer("scene");

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);
        targetsDirty = false;
    }

    private void deleteTargets() {
        if (sceneColorTexture != 0) {
            glDeleteTextures(sceneColorTexture);
            sceneColorTexture = 0;
        }
        if (sceneDepthBuffer != 0) {
            glDeleteRenderbuffers(sceneDepthBuffer);
            sceneDepthBuffer = 0;
        }
        if (sceneFbo != 0) {
            glDeleteFramebuffers(sceneFbo);
            sceneFbo = 0;
        }
    }

    private void uploadEasuConstants() {
        float inputViewportX = sceneWidth;
        float inputViewportY = sceneHeight;
        float inputSizeX = sceneWidth;
        float inputSizeY = sceneHeight;
        float outputSizeX = outputWidth;
        float outputSizeY = outputHeight;

        float[] con0 = new float[] {
                inputViewportX / outputSizeX,
                inputViewportY / outputSizeY,
                0.5f * inputViewportX / outputSizeX - 0.5f,
                0.5f * inputViewportY / outputSizeY - 0.5f
        };
        float[] con1 = new float[] {
                1.0f / inputSizeX,
                1.0f / inputSizeY,
                1.0f / inputSizeX,
                -1.0f / inputSizeY
        };
        float[] con2 = new float[] {
                -1.0f / inputSizeX,
                2.0f / inputSizeY,
                1.0f / inputSizeX,
                2.0f / inputSizeY
        };
        float[] con3 = new float[] {
                0.0f,
                4.0f / inputSizeY,
                0.0f,
                0.0f
        };

        setVec4Uniform("uCon0", con0);
        setVec4Uniform("uCon1", con1);
        setVec4Uniform("uCon2", con2);
        setVec4Uniform("uCon3", con3);
    }

    private void setVec4Uniform(String name, float[] values) {
        int loc = glGetUniformLocation(fsrProgram, name);
        if (loc >= 0) {
            GL20.glUniform4f(loc, values[0], values[1], values[2], values[3]);
        }
    }

    private int createProgram(String vertexResource, String fragmentResource) {
        int vertex = compileShader(GL_VERTEX_SHADER, readResource(vertexResource));
        int fragment = compileShader(GL_FRAGMENT_SHADER, readResource(fragmentResource));
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            throw new IllegalStateException("Failed to link shader program: " + GL20.glGetProgramInfoLog(program));
        }
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        return program;
    }

    private int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new IllegalStateException("Failed to compile shader: " + GL20.glGetShaderInfoLog(shader));
        }
        return shader;
    }

    private String readResource(String path) {
        try (InputStream in = FsrUpscaler.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing shader resource: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                return builder.toString();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read shader resource: " + path, e);
        }
    }

    private void checkFramebuffer(String label) {
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Incomplete " + label + " framebuffer: 0x" + Integer.toHexString(status));
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
