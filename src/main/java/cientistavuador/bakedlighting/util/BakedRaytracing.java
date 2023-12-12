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
package cientistavuador.bakedlighting.util;

import cientistavuador.bakedlighting.Main;
import cientistavuador.bakedlighting.geometry.Geometry;
import cientistavuador.bakedlighting.resources.mesh.MeshData;
import java.util.concurrent.CompletableFuture;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class BakedRaytracing {

    private final Geometry[] scene;
    private final int maxLightmapSize;
    private final Vector3f sunDirection;

    private float[] areas = null;
    private float largestArea = 0f;
    private float smallestArea = 0f;

    private Geometry geometry = null;
    private int lightmapSize;
    private float[] vertices = null;
    private int[] indices = null;

    private float[] lightmap = null;
    private int[] marginMap = null;

    private int i0 = 0;
    private int i1 = 0;
    private int i2 = 0;
    private final Vector3f a = new Vector3f();
    private final Vector3f b = new Vector3f();
    private final Vector3f c = new Vector3f();

    public BakedRaytracing(Geometry[] scene, int maxLightmapSize, Vector3fc sunDirection) {
        this.scene = scene;
        this.maxLightmapSize = maxLightmapSize;
        this.sunDirection = new Vector3f(sunDirection);
    }

    private void calculateAreas() {
        Vector3f p0 = new Vector3f();
        Vector3f p1 = new Vector3f();
        Vector3f p2 = new Vector3f();

        this.areas = new float[this.scene.length];
        for (int i = 0; i < this.scene.length; i++) {
            Geometry geo = this.scene[i];

            Matrix4fc model = geo.getModel();

            float[] gvertices = geo.getMesh().getVertices();
            int[] gindices = geo.getMesh().getIndices();

            float area = 0f;
            for (int j = 0; j < gindices.length; j += 3) {
                int v0 = gindices[j + 0] * MeshData.SIZE;
                int v1 = gindices[j + 1] * MeshData.SIZE;
                int v2 = gindices[j + 2] * MeshData.SIZE;

                float v0x = gvertices[v0 + MeshData.XYZ_OFFSET + 0];
                float v0y = gvertices[v0 + MeshData.XYZ_OFFSET + 1];
                float v0z = gvertices[v0 + MeshData.XYZ_OFFSET + 2];

                float v1x = gvertices[v1 + MeshData.XYZ_OFFSET + 0];
                float v1y = gvertices[v1 + MeshData.XYZ_OFFSET + 1];
                float v1z = gvertices[v1 + MeshData.XYZ_OFFSET + 2];

                float v2x = gvertices[v2 + MeshData.XYZ_OFFSET + 0];
                float v2y = gvertices[v2 + MeshData.XYZ_OFFSET + 1];
                float v2z = gvertices[v2 + MeshData.XYZ_OFFSET + 2];

                p0.set(v0x, v0y, v0z);
                p1.set(v1x, v1y, v1z);
                p2.set(v2x, v2y, v2z);

                model.transformProject(p0);
                model.transformProject(p1);
                model.transformProject(p2);

                float distA = (float) Math.sqrt(Math.pow(p0.x() - p1.x(), 2.0) + Math.pow(p0.y() - p1.y(), 2.0) + Math.pow(p0.z() - p1.z(), 2.0));
                float distB = (float) Math.sqrt(Math.pow(p1.x() - p2.x(), 2.0) + Math.pow(p1.y() - p2.y(), 2.0) + Math.pow(p1.z() - p2.z(), 2.0));
                float distC = (float) Math.sqrt(Math.pow(p2.x() - p0.x(), 2.0) + Math.pow(p2.y() - p0.y(), 2.0) + Math.pow(p2.z() - p0.z(), 2.0));

                float sp = (distA + distB + distC) * 0.5f;
                area += (float) Math.sqrt(sp * (sp - distA) * (sp - distB) * (sp - distC));
            }

            this.areas[i] = area;
            this.largestArea = Math.max(this.largestArea, area);
            this.smallestArea = Math.min(this.smallestArea, area);
        }
    }

    private void loadGeometry(int index) {
        float area = this.areas[index];
        area = (area - this.smallestArea) / (this.largestArea - this.smallestArea);
        this.lightmapSize = (int) (area * (this.maxLightmapSize - LightmapUVGenerator.BASE_LIGHTMAP_SIZE));
        this.lightmapSize += LightmapUVGenerator.BASE_LIGHTMAP_SIZE;
        this.lightmapSize = (int) Math.pow(2.0, 32 - Integer.numberOfLeadingZeros(this.lightmapSize - 1));

        this.geometry = this.scene[index];
        this.vertices = geometry.getMesh().getVertices();
        this.indices = geometry.getMesh().getIndices();
        this.lightmap = new float[this.lightmapSize * this.lightmapSize * 3];
        this.marginMap = new int[this.lightmapSize * this.lightmapSize];
        for (int x = 0; x < this.lightmapSize; x++) {
            for (int y = 0; y < this.lightmapSize; y++) {
                this.marginMap[x + (y * this.lightmapSize)] = -1;
            }
        }
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

    private int calculateMargin(int v0, int v1, int v2) {
        float v0x = this.vertices[v0 + MeshData.L_UV_OFFSET + 0];
        float v0y = this.vertices[v0 + MeshData.L_UV_OFFSET + 1];
        float v1x = this.vertices[v1 + MeshData.L_UV_OFFSET + 0];
        float v1y = this.vertices[v1 + MeshData.L_UV_OFFSET + 1];
        float v2x = this.vertices[v2 + MeshData.L_UV_OFFSET + 0];
        float v2y = this.vertices[v2 + MeshData.L_UV_OFFSET + 1];

        float minX = Math.min(v0x, Math.min(v1x, v2x));
        float minY = Math.min(v0y, Math.min(v1y, v2y));
        float maxX = Math.max(v0x, Math.max(v1x, v2x));
        float maxY = Math.max(v0y, Math.max(v1y, v2y));

        float width = Math.abs(maxX - minX);
        float height = Math.abs(maxY - minY);

        float currentResolution = LightmapUVGenerator.BASE_LIGHTMAP_SIZE;
        int minimumSize = LightmapUVGenerator.MINIMUM_TRIANGLE_SIZE;
        int margin = (int) (this.lightmapSize / currentResolution);
        while ((width * currentResolution) < minimumSize && (height * currentResolution) < minimumSize) {
            currentResolution *= 2;
            margin /= 2;
        }

        return margin;
    }

    private void calculateLightmap() {
        Vector3f weights = new Vector3f();

        Vector3f p = new Vector3f();

        for (int i = 0; i < this.indices.length; i += 3) {
            this.i0 = this.indices[i + 0];
            this.i1 = this.indices[i + 1];
            this.i2 = this.indices[i + 2];
            int v0 = (this.i0 * MeshData.SIZE);
            int v1 = (this.i1 * MeshData.SIZE);
            int v2 = (this.i2 * MeshData.SIZE);

            float v0x = this.vertices[v0 + MeshData.L_UV_OFFSET + 0] * this.lightmapSize;
            float v0y = this.vertices[v0 + MeshData.L_UV_OFFSET + 1] * this.lightmapSize;

            float v1x = this.vertices[v1 + MeshData.L_UV_OFFSET + 0] * this.lightmapSize;
            float v1y = this.vertices[v1 + MeshData.L_UV_OFFSET + 1] * this.lightmapSize;

            float v2x = this.vertices[v2 + MeshData.L_UV_OFFSET + 0] * this.lightmapSize;
            float v2y = this.vertices[v2 + MeshData.L_UV_OFFSET + 1] * this.lightmapSize;

            int minX = (int) Math.floor(Math.min(v0x, Math.min(v1x, v2x)));
            int minY = (int) Math.floor(Math.min(v0y, Math.min(v1y, v2y)));
            int maxX = (int) Math.ceil(Math.max(v0x, Math.max(v1x, v2x)));
            int maxY = (int) Math.ceil(Math.max(v0y, Math.max(v1y, v2y)));

            minX = clamp(minX, 0, this.lightmapSize - 1);
            minY = clamp(minY, 0, this.lightmapSize - 1);
            maxX = clamp(maxX, 0, this.lightmapSize - 1);
            maxY = clamp(maxY, 0, this.lightmapSize - 1);

            this.a.set(v0x, v0y, 0f);
            this.b.set(v1x, v1y, 0f);
            this.c.set(v2x, v2y, 0f);

            Vector3f outColor = new Vector3f();

            int margin = calculateMargin(v0, v1, v2);
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    p.set(x + 0.5f, y + 0.5f, 0f);
                    RasterUtils.barycentricWeights(p, this.a, this.b, this.c, weights);

                    float wx = weights.x();
                    float wy = weights.y();
                    float wz = weights.z();

                    if (wx < 0f || wy < 0f || wz < 0f) {
                        continue;
                    }

                    calculateLightmapPixel(x + 0.5f, y + 0.5f, weights, outColor);

                    this.lightmap[(x * 3) + (y * this.lightmapSize * 3) + 0] = outColor.x();
                    this.lightmap[(x * 3) + (y * this.lightmapSize * 3) + 1] = outColor.y();
                    this.lightmap[(x * 3) + (y * this.lightmapSize * 3) + 2] = outColor.z();
                    this.marginMap[x + (y * this.lightmapSize)] = margin;
                }
            }
        }
    }

    private void calculateLightmapPixel(float x, float y, Vector3fc weights, Vector3f outColor) {
        float v0wx = this.vertices[(this.i0 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 0];
        float v0wy = this.vertices[(this.i0 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 1];
        float v0wz = this.vertices[(this.i0 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 2];

        float v1wx = this.vertices[(this.i1 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 0];
        float v1wy = this.vertices[(this.i1 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 1];
        float v1wz = this.vertices[(this.i1 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 2];

        float v2wx = this.vertices[(this.i2 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 0];
        float v2wy = this.vertices[(this.i2 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 1];
        float v2wz = this.vertices[(this.i2 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 2];

        float worldx = v0wx * weights.x() + v1wx * weights.y() + v2wx * weights.z();
        float worldy = v0wy * weights.x() + v1wy * weights.y() + v2wy * weights.z();
        float worldz = v0wz * weights.x() + v1wz * weights.y() + v2wz * weights.z();

        Vector3f worldpos = new Vector3f(worldx, worldy, worldz);

        float v0nx = this.vertices[(this.i0 * MeshData.SIZE) + MeshData.N_XYZ_OFFSET + 0];
        float v0ny = this.vertices[(this.i0 * MeshData.SIZE) + MeshData.N_XYZ_OFFSET + 1];
        float v0nz = this.vertices[(this.i0 * MeshData.SIZE) + MeshData.N_XYZ_OFFSET + 2];

        float v1nx = this.vertices[(this.i1 * MeshData.SIZE) + MeshData.N_XYZ_OFFSET + 0];
        float v1ny = this.vertices[(this.i1 * MeshData.SIZE) + MeshData.N_XYZ_OFFSET + 1];
        float v1nz = this.vertices[(this.i1 * MeshData.SIZE) + MeshData.N_XYZ_OFFSET + 2];

        float v2nx = this.vertices[(this.i2 * MeshData.SIZE) + MeshData.N_XYZ_OFFSET + 0];
        float v2ny = this.vertices[(this.i2 * MeshData.SIZE) + MeshData.N_XYZ_OFFSET + 1];
        float v2nz = this.vertices[(this.i2 * MeshData.SIZE) + MeshData.N_XYZ_OFFSET + 2];

        float worldnx = v0nx * weights.x() + v1nx * weights.y() + v2nx * weights.z();
        float worldny = v0ny * weights.x() + v1ny * weights.y() + v2ny * weights.z();
        float worldnz = v0nz * weights.x() + v1nz * weights.y() + v2nz * weights.z();

        Vector3f worldnormal = new Vector3f(worldnx, worldny, worldnz).normalize();
        
        int pcfSize = 4;
        float shadow = 0f;
        for (int xOffset = 0; xOffset < pcfSize; xOffset++) {
            for (int yOffset = 0; yOffset < pcfSize; yOffset++) {
                float px = ((float) Math.floor(x)) + ((xOffset + 0.5f) * (1f / pcfSize));
                float py = ((float) Math.floor(y)) + ((yOffset + 0.5f) * (1f / pcfSize));
                if (!isInShadow(px, py)) {
                    shadow += 1f;
            }
        }
        }
        shadow /= pcfSize * pcfSize;
        
        outColor
                .set(1.5f, 1.5f, 1.5f)
                .mul(Math.max(worldnormal.dot(this.sunDirection), 0f))
                .mul(shadow)
                .add(0.2f, 0.2f, 0.2f)
                ;
    }

    private boolean isInShadow(float pixelX, float pixelY) {
        Vector3f weights = new Vector3f();
        Vector3f p = new Vector3f(pixelX, pixelY, 0f);
        
        RasterUtils.barycentricWeights(p, this.a, this.b, this.c, weights);
        
        float v0wx = this.vertices[(this.i0 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 0];
        float v0wy = this.vertices[(this.i0 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 1];
        float v0wz = this.vertices[(this.i0 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 2];

        float v1wx = this.vertices[(this.i1 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 0];
        float v1wy = this.vertices[(this.i1 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 1];
        float v1wz = this.vertices[(this.i1 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 2];

        float v2wx = this.vertices[(this.i2 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 0];
        float v2wy = this.vertices[(this.i2 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 1];
        float v2wz = this.vertices[(this.i2 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 2];

        float worldx = v0wx * weights.x() + v1wx * weights.y() + v2wx * weights.z();
        float worldy = v0wy * weights.x() + v1wy * weights.y() + v2wy * weights.z();
        float worldz = v0wz * weights.x() + v1wz * weights.y() + v2wz * weights.z();

        Vector3f worldpos = new Vector3f(worldx, worldy, worldz);
        
        RayResult[] results = Geometry.testRay(worldpos, this.sunDirection, this.scene);
        
        for (RayResult r : results) {
            if (r.i0() != this.i0 && r.i1() != this.i1 && r.i2() != this.i2) {
                if (r.getDistance() > 0.001f) {
                    return true;
                }
            }
        }
        return false;
    }

    private void fillLightmapMargin() {
        fillLightmapRight(false);
        fillLightmapLeft(false);
        fillLightmapTop(false);
        fillLightmapBottom(false);
    }
    
    private void fillLightmapCompletely() {
        fillLightmapRight(true);
        fillLightmapLeft(true);
        fillLightmapTop(true);
        fillLightmapBottom(true);
    }

    private void fillLightmapRight(boolean ignoreMarginLimit) {
        for (int y = 0; y < this.lightmapSize; y++) {
            float colorR = 0f;
            float colorG = 0f;
            float colorB = 0f;
            int margin = 0;
            int marginSize = 0;
            for (int x = 0; x < this.lightmapSize; x++) {
                int currentMargin = this.marginMap[x + (y * this.lightmapSize)];
                if (currentMargin == -1 && (margin > 0 || (ignoreMarginLimit && marginSize > 0))) {
                    this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 0] = colorR;
                    this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 1] = colorG;
                    this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 2] = colorB;
                    this.marginMap[x + (y * this.lightmapSize)] = marginSize;
                    margin--;
                } else {
                    margin = currentMargin;
                    marginSize = currentMargin;
                    colorR = this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 0];
                    colorG = this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 1];
                    colorB = this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 2];
                }
            }
        }
    }

    private void fillLightmapLeft(boolean ignoreMarginLimit) {
        for (int y = 0; y < this.lightmapSize; y++) {
            float colorR = 0f;
            float colorG = 0f;
            float colorB = 0f;
            int margin = 0;
            int marginSize = 0;
            for (int x = (this.lightmapSize - 1); x >= 0; x--) {
                int currentMargin = this.marginMap[x + (y * this.lightmapSize)];
                if (currentMargin == -1 && (margin > 0 || (ignoreMarginLimit && marginSize > 0))) {
                    this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 0] = colorR;
                    this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 1] = colorG;
                    this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 2] = colorB;
                    this.marginMap[x + (y * this.lightmapSize)] = marginSize;
                    margin--;
                } else {
                    margin = currentMargin;
                    marginSize = currentMargin;
                    colorR = this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 0];
                    colorG = this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 1];
                    colorB = this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 2];
                }
            }
        }
    }

    private void fillLightmapTop(boolean ignoreMarginLimit) {
        for (int x = 0; x < this.lightmapSize; x++) {
            float colorR = 0f;
            float colorG = 0f;
            float colorB = 0f;
            int margin = 0;
            int marginSize = 0;
            for (int y = 0; y < this.lightmapSize; y++) {
                int currentMargin = this.marginMap[x + (y * this.lightmapSize)];
                if (currentMargin == -1 && (margin > 0 || (ignoreMarginLimit && marginSize > 0))) {
                    this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 0] = colorR;
                    this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 1] = colorG;
                    this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 2] = colorB;
                    this.marginMap[x + (y * this.lightmapSize)] = marginSize;
                    margin--;
                } else {
                    margin = currentMargin;
                    marginSize = currentMargin;
                    colorR = this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 0];
                    colorG = this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 1];
                    colorB = this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 2];
                }
            }
        }
    }

    private void fillLightmapBottom(boolean ignoreMarginLimit) {
        for (int x = 0; x < this.lightmapSize; x++) {
            float colorR = 0f;
            float colorG = 0f;
            float colorB = 0f;
            int margin = 0;
            int marginSize = 0;
            for (int y = (this.lightmapSize - 1); y >= 0; y--) {
                int currentMargin = this.marginMap[x + (y * this.lightmapSize)];
                if (currentMargin == -1 && (margin > 0 || (ignoreMarginLimit && marginSize > 0))) {
                    this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 0] = colorR;
                    this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 1] = colorG;
                    this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 2] = colorB;
                    this.marginMap[x + (y * this.lightmapSize)] = marginSize;
                    margin--;
                } else {
                    margin = currentMargin;
                    marginSize = currentMargin;
                    colorR = this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 0];
                    colorG = this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 1];
                    colorB = this.lightmap[((x * 3) + (y * this.lightmapSize * 3)) + 2];
                }
            }
        }
    }

    private void createLightmapTexture() {
        final float[] lightmapCopy = this.lightmap.clone();
        final int lightmapSizeCopy = this.lightmapSize;
        final Geometry geometryCopy = this.geometry;

        Main.MAIN_TASKS.add(() -> {
            int texture = glGenTextures();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, lightmapSizeCopy, lightmapSizeCopy, 0, GL_RGB, GL_FLOAT, lightmapCopy);

            glGenerateMipmap(GL_TEXTURE_2D);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            
            glBindTexture(GL_TEXTURE_2D, 0);
            geometryCopy.setLightmapTextureHint(texture);
        });
    }

    public void bake() {
        CompletableFuture.runAsync(() -> {
            calculateAreas();
            for (int i = 0; i < this.scene.length; i++) {
                loadGeometry(i);
                calculateLightmap();
                //fillLightmapMargin();
                //fillLightmapCompletely();
                createLightmapTexture();
            }
        });
    }

}
