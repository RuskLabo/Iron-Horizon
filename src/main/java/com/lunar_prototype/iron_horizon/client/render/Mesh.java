package com.lunar_prototype.iron_horizon.client.render;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

public class Mesh implements AutoCloseable {
    private static final int FLOATS_PER_VERTEX = 8;
    private static final int POSITION_OFFSET = 0;
    private static final int NORMAL_OFFSET = 3 * Float.BYTES;
    private static final int TEXCOORD_OFFSET = 6 * Float.BYTES;

    private final int vboId;
    private final int eboId;
    private final int indexCount;
    private final int vertexStrideBytes;

    public Mesh(float[] vertexData, int[] indices) {
        this.vertexStrideBytes = FLOATS_PER_VERTEX * Float.BYTES;
        this.indexCount = indices.length;

        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertexData.length);
        vertexBuffer.put(vertexData).flip();
        IntBuffer indexBuffer = BufferUtils.createIntBuffer(indices.length);
        indexBuffer.put(indices).flip();

        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_STATIC_DRAW);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public void draw() {
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);

        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(3, GL_FLOAT, vertexStrideBytes, POSITION_OFFSET);

        glEnableClientState(GL_NORMAL_ARRAY);
        glNormalPointer(GL_FLOAT, vertexStrideBytes, NORMAL_OFFSET);

        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        glTexCoordPointer(2, GL_FLOAT, vertexStrideBytes, TEXCOORD_OFFSET);

        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0L);

        glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        glDisableClientState(GL_NORMAL_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    @Override
    public void close() {
        glDeleteBuffers(vboId);
        glDeleteBuffers(eboId);
    }
}
