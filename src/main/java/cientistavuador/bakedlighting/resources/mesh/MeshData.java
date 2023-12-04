/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <https://unlicense.org>
 */
package cientistavuador.bakedlighting.resources.mesh;

import cientistavuador.bakedlighting.Main;
import java.util.Arrays;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class MeshData {

    //position (vec3), texture uv (vec2), normal (vec3), tangent (vec3), lightmap uv (vec2)
    public static final int SIZE = 3 + 2 + 3 + 3 + 2;
    
    public static final int XYZ_OFFSET = 0;
    public static final int UV_OFFSET = 0 + 3;
    public static final int N_XYZ_OFFSET = 0 + 3 + 2;
    public static final int T_XYZ_OFFSET = 0 + 3 + 2 + 3;
    public static final int L_UV_OFFSET = 0 + 3 + 2 + 3 + 3;
    
    private final String name;
    private final float[] vertices;
    private final int[] indices;
    private int vao = 0;
    private int ebo = 0;
    private int vbo = 0;
    private int textureHint = 0;

    public MeshData(String name, float[] vertices, int[] indices) {
        this.name = name;
        this.vertices = vertices;
        this.indices = indices;
    }

    public String getName() {
        return name;
    }

    public float[] getVertices() {
        return vertices;
    }

    public int getAmountOfVerticesComponents() {
        return vertices.length;
    }

    public int getAmountOfVertices() {
        return vertices.length / MeshData.SIZE;
    }

    public int[] getIndices() {
        return indices;
    }

    public int getAmountOfIndices() {
        return indices.length;
    }

    public boolean hasVAO() {
        return this.vao != 0;
    }

    public int getVAO() {
        if (this.vao == 0) {
            this.vao = glGenVertexArrays();
            glBindVertexArray(this.vao);

            this.ebo = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, getIndices(), GL_STATIC_DRAW);

            this.vbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, getVertices(), GL_STATIC_DRAW);

            //position
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, MeshData.SIZE * Float.BYTES, (XYZ_OFFSET * Float.BYTES));

            //texture
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, MeshData.SIZE * Float.BYTES, (UV_OFFSET * Float.BYTES));

            //normal
            glEnableVertexAttribArray(2);
            glVertexAttribPointer(2, 3, GL_FLOAT, false, MeshData.SIZE * Float.BYTES, (N_XYZ_OFFSET * Float.BYTES));
            
            //tangent
            glEnableVertexAttribArray(3);
            glVertexAttribPointer(3, 3, GL_FLOAT, false, MeshData.SIZE * Float.BYTES, (T_XYZ_OFFSET * Float.BYTES));
            
            //lightmap uv
            glEnableVertexAttribArray(4);
            glVertexAttribPointer(4, 2, GL_FLOAT, false, MeshData.SIZE * Float.BYTES, (L_UV_OFFSET * Float.BYTES));
            
            glBindBuffer(GL_ARRAY_BUFFER, 0);

            glBindVertexArray(0);
        }
        return this.vao;
    }

    public void deleteVAO() {
        if (this.vao != 0) {
            glDeleteVertexArrays(this.vao);
            glDeleteBuffers(this.ebo);
            glDeleteBuffers(this.vbo);
            this.vao = 0;
            this.ebo = 0;
            this.vbo = 0;
        }
    }

    public int getTextureHint() {
        return textureHint;
    }

    public void setTextureHint(int textureHint) {
        this.textureHint = textureHint;
    }

    public void bind() {
        glBindVertexArray(getVAO());
    }

    public void render(int offset, int length) {
        glDrawElements(GL_TRIANGLES, length, GL_UNSIGNED_INT, offset * Integer.BYTES);
        Main.NUMBER_OF_DRAWCALLS++;
        Main.NUMBER_OF_VERTICES += length;
    }

    public void render() {
        render(0, this.indices.length);
    }

    public void unbind() {
        glBindVertexArray(0);
    }

    public void bindRenderUnbind() {
        bind();
        render();
        unbind();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Arrays.hashCode(this.vertices);
        hash = 97 * hash + Arrays.hashCode(this.indices);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MeshData other = (MeshData) obj;
        if (!Arrays.equals(this.vertices, other.vertices)) {
            return false;
        }
        return Arrays.equals(this.indices, other.indices);
    }

}
