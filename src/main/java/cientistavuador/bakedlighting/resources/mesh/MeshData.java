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
import cientistavuador.bakedlighting.util.LightmapUVs;
import cientistavuador.bakedlighting.util.MeshUtils;
import cientistavuador.bakedlighting.util.ObjectCleaner;
import cientistavuador.bakedlighting.util.Pair;
import java.util.ArrayList;
import java.util.List;
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
    
    public static final float EPSILON = 0.00001f;
    
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
        private final float pixelToWorldRatio;
        private final float scaleX;
        private final float scaleY;
        private final float scaleZ;
        
        private final CompletableFuture<LightmapUVs.GeneratorOutput> futureLightmap;
        
        private boolean done = false;
        private LightmapUVs.LightmapperQuad[] quads = null;
        private float[] uvs = null;
        private int lightmapSize = 0;
        
        private int vbo = 0;
        private int vao = 0;

        public LightmapMesh(MeshData parent, float worldToPixelRatio, float scaleX, float scaleY, float scaleZ) {
            this.parent = parent;
            this.pixelToWorldRatio = worldToPixelRatio;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
            this.futureLightmap = CompletableFuture.supplyAsync(() -> {
                return MeshUtils.generateLightmapUVs(
                        parent.getVertices(),
                        MeshData.SIZE,
                        MeshData.XYZ_OFFSET,
                        this.pixelToWorldRatio,
                        scaleX, scaleY, scaleZ
                );
            });
        }

        public MeshData getParent() {
            return parent;
        }

        public float getPixelToWorldRatio() {
            return pixelToWorldRatio;
        }

        public float getScaleX() {
            return scaleX;
        }

        public float getScaleY() {
            return scaleY;
        }

        public float getScaleZ() {
            return scaleZ;
        }

        private void ensureProcessingIsDone() {
            if (this.done) {
                return;
            }
            try {
                LightmapUVs.GeneratorOutput output = this.futureLightmap.get();
                this.quads = output.getQuads();
                this.uvs = output.getUVs();
                this.lightmapSize = output.getLightmapSize();
                this.done = true;
            } catch (InterruptedException | ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }

        public int getLightmapSize() {
            ensureProcessingIsDone();
            return lightmapSize;
        }

        public float[] getUVs() {
            ensureProcessingIsDone();
            return uvs;
        }

        public LightmapUVs.LightmapperQuad[] getQuads() {
            ensureProcessingIsDone();
            return quads;
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
                glBufferData(GL_ARRAY_BUFFER, getUVs(), GL_STATIC_DRAW);
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
            return this.done;
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
    private int textureHint = Textures.ERROR_TEXTURE;

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
    
    public LightmapMesh getLightmapMesh(float pixelToWorldRatio, float scaleX, float scaleY, float scaleZ) {
        if (!hasLightmapSupport()) {
            return null;
        }
        synchronized (this.lightmapMeshes) {
            for (LightmapMesh m : this.lightmapMeshes) {
                if (Math.abs(m.getPixelToWorldRatio() - pixelToWorldRatio) <= EPSILON
                        && Math.abs(scaleX - m.getScaleX()) <= EPSILON
                        && Math.abs(scaleY - m.getScaleY()) <= EPSILON
                        && Math.abs(scaleZ - m.getScaleZ()) <= EPSILON
                        ) {
                    return m;
                }
            }
        }
        return null;
    }
    
    public LightmapMesh[] getLightmapMeshes() {
        synchronized (this.lightmapMeshes) {
            return this.lightmapMeshes.toArray(LightmapMesh[]::new);
        }
    }
    
    public LightmapMesh scheduleLightmapMesh(float pixelToWorldRatio, float scaleX, float scaleY, float scaleZ) {
        if (!hasLightmapSupport()) {
            return null;
        }
        synchronized (this.lightmapMeshes) {
            LightmapMesh mesh = getLightmapMesh(pixelToWorldRatio, scaleX, scaleY, scaleZ);
            if (mesh != null) {
                return mesh;
            }
            mesh = new LightmapMesh(this, pixelToWorldRatio, scaleX, scaleY, scaleZ);
            this.lightmapMeshes.add(mesh);
            return mesh;
        }
    }
    
}
