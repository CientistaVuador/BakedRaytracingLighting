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
package cientistavuador.bakedlighting.util.bakedraytracing;

import cientistavuador.bakedlighting.debug.DebugCounter;
import cientistavuador.bakedlighting.geometry.Geometries;
import cientistavuador.bakedlighting.geometry.Geometry;
import cientistavuador.bakedlighting.resources.mesh.MeshData;
import cientistavuador.bakedlighting.resources.mesh.MeshResources;
import cientistavuador.bakedlighting.texture.Textures;
import cientistavuador.bakedlighting.util.RasterUtils;
import cientistavuador.bakedlighting.util.RayResult;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class BakedTest {

    public static final int AMBIENT = 0x7F_7F_7F_FF;
    public static final int DIFFUSE = 0xFF_FF_FF_FF;

    public static void bake(Geometry g, Geometry[] scene, Vector3fc sunDirection) {
        DebugCounter counter = new DebugCounter("Lightmap Generation");
        counter.markStart("lightmap "+g.getMesh().getName()+" "+MeshResources.LIGHTMAP_SIZE);
        
        int lightmapSize = MeshResources.LIGHTMAP_SIZE;
        int[] lightmap = new int[lightmapSize * lightmapSize];

        int[] indices = g.getMesh().getIndices();
        float[] vertices = g.getMesh().getVertices();

        Vector3f weights = new Vector3f();

        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();
        Vector3f p = new Vector3f();

        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i + 0];
            int i1 = indices[i + 1];
            int i2 = indices[i + 2];
            int v0 = (i0 * MeshData.SIZE);
            int v1 = (i1 * MeshData.SIZE);
            int v2 = (i2 * MeshData.SIZE);

            float v0x = vertices[v0 + MeshData.L_UV_OFFSET + 0] * lightmapSize;
            float v0y = vertices[v0 + MeshData.L_UV_OFFSET + 1] * lightmapSize;
            float v1x = vertices[v1 + MeshData.L_UV_OFFSET + 0] * lightmapSize;
            float v1y = vertices[v1 + MeshData.L_UV_OFFSET + 1] * lightmapSize;
            float v2x = vertices[v2 + MeshData.L_UV_OFFSET + 0] * lightmapSize;
            float v2y = vertices[v2 + MeshData.L_UV_OFFSET + 1] * lightmapSize;

            int minX = (int) Math.floor(Math.min(v0x, Math.min(v1x, v2x)));
            int minY = (int) Math.floor(Math.min(v0y, Math.min(v1y, v2y)));
            int maxX = (int) Math.ceil(Math.max(v0x, Math.max(v1x, v2x)));
            int maxY = (int) Math.ceil(Math.max(v0y, Math.max(v1y, v2y)));

            minX = clamp(minX, 0, lightmapSize);
            minY = clamp(minY, 0, lightmapSize);
            maxX = clamp(maxX, 0, lightmapSize);
            maxY = clamp(maxY, 0, lightmapSize);

            a.set(v0x, v0y, 0f);
            b.set(v1x, v1y, 0f);
            c.set(v2x, v2y, 0f);

            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    p.set(x + 0.5f, y + 0.5f, 0f);
                    RasterUtils.barycentricWeights(p, a, b, c, weights);

                    float wx = weights.x();
                    float wy = weights.y();
                    float wz = weights.z();

                    if (wx < 0f || wy < 0f || wz < 0f) {
                        continue;
                    }

                    float v0wx = vertices[v0 + MeshData.XYZ_OFFSET + 0];
                    float v0wy = vertices[v0 + MeshData.XYZ_OFFSET + 1];
                    float v0wz = vertices[v0 + MeshData.XYZ_OFFSET + 2];
                    float v1wx = vertices[v1 + MeshData.XYZ_OFFSET + 0];
                    float v1wy = vertices[v1 + MeshData.XYZ_OFFSET + 1];
                    float v1wz = vertices[v1 + MeshData.XYZ_OFFSET + 2];
                    float v2wx = vertices[v2 + MeshData.XYZ_OFFSET + 0];
                    float v2wy = vertices[v2 + MeshData.XYZ_OFFSET + 1];
                    float v2wz = vertices[v2 + MeshData.XYZ_OFFSET + 2];

                    float worldx = v0wx * weights.x() + v1wx * weights.y() + v2wx * weights.z();
                    float worldy = v0wy * weights.x() + v1wy * weights.y() + v2wy * weights.z();
                    float worldz = v0wz * weights.x() + v1wz * weights.y() + v2wz * weights.z();

                    p.set(worldx, worldy, worldz);

                    RayResult[] results = Geometry.testRay(p, sunDirection, scene);

                    if (results.length == 0) {
                        lightmap[x + (y * lightmapSize)] = DIFFUSE;
                    } else {
                        boolean hitSun = true;
                        for (RayResult r : results) {
                            if (r.i0() != i0 && r.i1() != i1 && r.i2() != i2) {
                                hitSun = false;
                                break;
                            }
                        }
                        if (hitSun) {
                            lightmap[x + (y * lightmapSize)] = DIFFUSE;
                        } else {
                            lightmap[x + (y * lightmapSize)] = AMBIENT;
                        }
                    }

                }
            }
        }
        int texture = glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, lightmapSize, lightmapSize, 0, GL_RGBA, GL_UNSIGNED_INT_8_8_8_8, lightmap);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

        glBindTexture(GL_TEXTURE_2D, 0);
        g.setLightmapTextureHint(texture);
        
        counter.markEnd("lightmap "+g.getMesh().getName()+" "+MeshResources.LIGHTMAP_SIZE);
        counter.print();
    }

    private static int clamp(int v, int min, int max) {
        if (v > max) {
            return max;
        }
        if (v < min) {
            return min;
        }
        return v;
    }

}
