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
import cientistavuador.bakedlighting.texture.Textures;
import cientistavuador.bakedlighting.util.BVH;
import cientistavuador.bakedlighting.util.LightmapUVGenerator;
import cientistavuador.bakedlighting.util.MeshUtils;
import cientistavuador.bakedlighting.util.ObjectCleaner;
import cientistavuador.bakedlighting.util.Pair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.KHRDebug;

/**
 *
 * @author Cien
 */
public class MeshData {

    //position (vec3), texture uv (vec2), normal (vec3), tangent (vec3), lightmap uv (vec2), lightmap uv angle (float)
    public static final int SIZE = 3 + 2 + 3 + 3 + 2;

    public static final int XYZ_OFFSET = 0;
    public static final int UV_OFFSET = 0 + 3;
    public static final int N_XYZ_OFFSET = 0 + 3 + 2;
    public static final int T_XYZ_OFFSET = 0 + 3 + 2 + 3;
    public static final int L_UV_OFFSET = 0 + 3 + 2 + 3 + 3;

    private static void configureBoundVAO() {
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
    }

    public static class LightmapMesh {

        private final MeshData parent;
        private final int lightmapSize;
        private final CompletableFuture<LightmapUVGenerator.LightmapUVGeneratorOutput> futureLightmap;

        private float[] lightmapUVsBake;
        private float[] lightmapUVsRender;
        private int[] denoiserQuads;

        private int vbo = 0;
        private int vao = 0;

        public LightmapMesh(MeshData parent, int lightmapSize) {
            this.parent = parent;
            this.lightmapSize = lightmapSize;
            this.futureLightmap = CompletableFuture.supplyAsync(() -> {
                return MeshUtils.generateLightmapUV(
                        parent.getVertices(),
                        MeshData.SIZE,
                        MeshData.XYZ_OFFSET,
                        lightmapSize
                );
            });
        }

        public MeshData getParent() {
            return parent;
        }

        public int getLightmapSize() {
            return lightmapSize;
        }

        private void ensureProcessingIsDone() {
            if (this.lightmapUVsBake != null && this.lightmapUVsRender != null) {
                return;
            }
            try {
                LightmapUVGenerator.LightmapUVGeneratorOutput output = futureLightmap.get();
                float[] forBake = output.forBaking();
                float[] forRender = output.forRendering();
                int[] forDenoising = output.forDenoising();
                this.lightmapUVsBake = forBake;
                this.lightmapUVsRender = forRender;
                this.denoiserQuads = forDenoising;
            } catch (InterruptedException | ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }

        public float[] getLightmapUVsBake() {
            ensureProcessingIsDone();
            return lightmapUVsBake;
        }

        public float[] getLightmapUVsRender() {
            ensureProcessingIsDone();
            return lightmapUVsRender;
        }

        public int[] getDenoiserQuads() {
            ensureProcessingIsDone();
            return denoiserQuads;
        }

        public int getVAO() {
            if (this.vao == 0) {
                ensureProcessingIsDone();
                this.parent.getVAO();

                this.vao = glGenVertexArrays();
                glBindVertexArray(this.vao);

                //mesh
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.parent.ebo);

                glBindBuffer(GL_ARRAY_BUFFER, this.parent.vbo);
                configureBoundVAO();
                glBindBuffer(GL_ARRAY_BUFFER, 0);

                //lightmap uv
                this.vbo = glGenBuffers();
                glBindBuffer(GL_ARRAY_BUFFER, this.vbo);
                glBufferData(GL_ARRAY_BUFFER, getLightmapUVsRender(), GL_STATIC_DRAW);
                glEnableVertexAttribArray(4);
                glVertexAttribPointer(4, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
                glBindBuffer(GL_ARRAY_BUFFER, 0);

                glBindVertexArray(0);

                if (Main.DEBUG_ENABLED && GL.getCapabilities().GL_KHR_debug) {
                    KHRDebug.glObjectLabel(GL_VERTEX_ARRAY, this.vao, "Lightmap_" + this.lightmapSize + "_Mesh_" + parent.getName());
                }

                final int vaoFinal = this.vao;
                final int vboFinal = this.vbo;

                ObjectCleaner.get().register(this, () -> {
                    Main.MAIN_TASKS.add(() -> {
                        glDeleteVertexArrays(vaoFinal);
                        glDeleteBuffers(vboFinal);
                    });
                });
            }
            return vao;
        }

        public boolean isDone() {
            return this.futureLightmap.isDone();
        }

    }

    private final String name;

    private final float[] vertices;
    private final int[] indices;
    private final CompletableFuture<BVH> futureBvh;

    private final boolean lightmapSupport;
    private final List<LightmapMesh> lightmapMeshes = new ArrayList<>();

    private BVH bvh = null;
    private int vao = 0;
    private int ebo = 0;
    private int vbo = 0;
    private int textureHint = Textures.EMPTY_TEXTURE;

    public MeshData(String name, float[] vertices, int[] indices, boolean addLightmapSupport) {
        this.name = name;
        if (addLightmapSupport) {
            Pair<float[], int[]> unindexed = MeshUtils.unindex(vertices, indices, MeshData.SIZE);
            vertices = unindexed.getA();
            indices = unindexed.getB();
        }
        this.lightmapSupport = addLightmapSupport;
        this.vertices = vertices;
        this.indices = indices;
        this.futureBvh = CompletableFuture.supplyAsync(() -> {
            return BVH.create(this.vertices, this.indices, MeshData.SIZE, MeshData.XYZ_OFFSET);
        });
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
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, getIndices(), GL_STATIC_DRAW);

            this.vbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, this.vbo);
            glBufferData(GL_ARRAY_BUFFER, getVertices(), GL_STATIC_DRAW);

            configureBoundVAO();

            //lightmap uv
            glEnableVertexAttribArray(4);
            glVertexAttribPointer(4, 2, GL_FLOAT, false, MeshData.SIZE * Float.BYTES, (L_UV_OFFSET * Float.BYTES));

            glBindBuffer(GL_ARRAY_BUFFER, 0);

            glBindVertexArray(0);

            if (Main.DEBUG_ENABLED && GL.getCapabilities().GL_KHR_debug) {
                KHRDebug.glObjectLabel(GL_VERTEX_ARRAY, this.vao, "Mesh_" + getName());
            }

            final int vaoFinal = this.vao;
            final int eboFinal = this.ebo;
            final int vboFinal = this.vbo;

            ObjectCleaner.get().register(this, () -> {
                Main.MAIN_TASKS.add(() -> {
                    glDeleteVertexArrays(vaoFinal);
                    glDeleteBuffers(eboFinal);
                    glDeleteBuffers(vboFinal);
                });
            });
        }
        return this.vao;
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

    public BVH getBVH() {
        if (this.bvh == null) {
            try {
                this.bvh = this.futureBvh.get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }
        return this.bvh;
    }

    public boolean hasLightmapSupport() {
        return this.lightmapSupport;
    }

    public LightmapMesh getLightmapMesh(int lightmapSize) {
        if (!hasLightmapSupport()) {
            return null;
        }
        LightmapMesh result = null;
        synchronized (this.lightmapMeshes) {
            for (LightmapMesh m : this.lightmapMeshes) {
                if (m.getLightmapSize() == lightmapSize) {
                    result = m;
                    break;
                }
            }
        }
        return result;
    }

    public int[] getSupportedLightmapSizes() {
        if (!hasLightmapSupport()) {
            return new int[0];
        }
        int[] output;
        synchronized (this.lightmapMeshes) {
            output = new int[this.lightmapMeshes.size()];
            for (int i = 0; i < this.lightmapMeshes.size(); i++) {
                output[i] = this.lightmapMeshes.get(i).getLightmapSize();
            }
        }
        return output;
    }

    public LightmapMesh scheduleLightmapMesh(int lightmapSize) {
        if (!hasLightmapSupport()) {
            return null;
        }
        LightmapMesh mesh;
        synchronized (this.lightmapMeshes) {
            mesh = getLightmapMesh(lightmapSize);
            if (mesh != null) {
                return mesh;
            }
            mesh = new LightmapMesh(this, lightmapSize);
            this.lightmapMeshes.add(mesh);
        }
        return mesh;
    }
    
}
