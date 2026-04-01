package com.lunar_prototype.iron_horizon.client.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBTruetype.stbtt_BakeFontBitmap;
import static org.lwjgl.stb.STBTruetype.stbtt_GetBakedQuad;

public class FontRenderer implements AutoCloseable {
    private final int textureId;
    private final STBTTBakedChar.Buffer cdata;
    private final float fontSize;
    private final int bitmapW;
    private final int bitmapH;

    public FontRenderer(String resourcePath, float fontSize) throws IOException {
        this.fontSize = fontSize;
        this.bitmapW = 512;
        this.bitmapH = 512;

        ByteBuffer ttf = loadResource(resourcePath);
        ByteBuffer bitmap = BufferUtils.createByteBuffer(bitmapW * bitmapH);
        this.cdata = STBTTBakedChar.malloc(96);

        stbtt_BakeFontBitmap(ttf, fontSize, bitmap, bitmapW, bitmapH, 32, cdata);

        // STBのアルファマップをRGBAに変換
        ByteBuffer rgba = BufferUtils.createByteBuffer(bitmapW * bitmapH * 4);
        for (int i = 0; i < bitmapW * bitmapH; i++) {
            byte val = bitmap.get(i);
            rgba.put((byte) 255); // R
            rgba.put((byte) 255); // G
            rgba.put((byte) 255); // B
            rgba.put(val);        // A
        }
        rgba.flip();

        this.textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, bitmapW, bitmapH, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private ByteBuffer loadResource(String resourcePath) throws IOException {
        try (InputStream is = FontRenderer.class.getResourceAsStream(resourcePath);
             ReadableByteChannel rbc = Channels.newChannel(is)) {
            ByteBuffer buffer = BufferUtils.createByteBuffer(1024 * 1024); // 1MB
            while (rbc.read(buffer) != -1) {
                if (buffer.remaining() == 0) {
                    ByteBuffer newBuffer = BufferUtils.createByteBuffer(buffer.capacity() * 2);
                    buffer.flip();
                    newBuffer.put(buffer);
                    buffer = newBuffer;
                }
            }
            buffer.flip();
            return buffer;
        }
    }

    public void drawText(String text, float x, float y, float r, float g, float b, float a, float scale) {
        glPushMatrix();
        // STBTT は y 座標をベースラインとして扱うため、
        // 以前の STBEasyFont (上端基準) との互換性のためにオフセットを加える (0.75f は Arial のアセント比率に近い調整値)
        glTranslatef(x, y + fontSize * 0.75f * scale, 0);
        glScalef(scale, scale, 1.0f);

        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, textureId);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(r, g, b, a);

        glBegin(GL_QUADS);
        float[] xPos = {0};
        float[] yPos = {0};
        STBTTAlignedQuad q = STBTTAlignedQuad.malloc();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 32 || c >= 128) continue;

            stbtt_GetBakedQuad(cdata, bitmapW, bitmapH, c - 32, xPos, yPos, q, true);
            
            glTexCoord2f(q.s0(), q.t0());
            glVertex2f(q.x0(), q.y0());
            
            glTexCoord2f(q.s1(), q.t0());
            glVertex2f(q.x1(), q.y0());
            
            glTexCoord2f(q.s1(), q.t1());
            glVertex2f(q.x1(), q.y1());
            
            glTexCoord2f(q.s0(), q.t1());
            glVertex2f(q.x0(), q.y1());
        }
        glEnd();
        q.free();
        glDisable(GL_TEXTURE_2D);
        glPopMatrix();
    }

    public float getStringWidth(String text) {
        float width = 0;
        float[] xPos = {0};
        float[] yPos = {0};
        STBTTAlignedQuad q = STBTTAlignedQuad.malloc();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 32 || c >= 128) continue;
            stbtt_GetBakedQuad(cdata, bitmapW, bitmapH, c - 32, xPos, yPos, q, true);
            width = q.x1();
        }
        q.free();
        return width;
    }

    public float getFontSize() {
        return fontSize;
    }

    @Override
    public void close() {
        glDeleteTextures(textureId);
        cdata.free();
    }
}
