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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class BakedRaytracing {

    private final Geometry[] scene;
    private final Map<MeshData, SoftwareTexture> sceneTextures = new HashMap<>();
    private final int maxLightmapSize;
    private final boolean clampToMeshLightmapSize;
    private final Vector3f sunDirection;

    private float[] areas = null;
    private float largestArea = 0f;
    private float smallestArea = 0f;

    private Geometry geometry = null;
    private int lightmapSize;
    private float[] vertices = null;
    private int[] indices = null;

    private float[] lightMap = null;
    private int[] marginMap = null;
    private int[] indexMap = null;
    private float[] positionMap = null;
    private float[] normalMap = null;
    private float[] tangentMap = null;

    private Future<Void> processingTask = null;
    private String currentStatus = "Idle";
    private float progressBarStep = 0f;
    private float currentProgress = 0f;

    public BakedRaytracing(Geometry[] scene, int maxLightmapSize, boolean clampToMeshLightmapSize, Vector3fc sunDirection) {
        this.scene = scene;
        this.maxLightmapSize = maxLightmapSize;
        this.clampToMeshLightmapSize = clampToMeshLightmapSize;
        this.sunDirection = new Vector3f(sunDirection);
    }

    private void waitForBVHs() {
        setProgressBarStep(this.scene.length);
        for (int i = 0; i < this.scene.length; i++) {
            Geometry geo = this.scene[i];
            this.currentStatus = "Waiting for BVH to compute (" + geo.getMesh().getName() + ")";
            geo.getMesh().getBVH();
            stepProgressBar();
        }
    }

    private void loadTextures() {
        setProgressBarStep(this.scene.length);
        for (int i = 0; i < this.scene.length; i++) {
            Geometry geo = this.scene[i];
            this.currentStatus = "Loading Texture (" + i + "/" + this.scene.length + ")";
            SoftwareTexture t = this.sceneTextures.get(geo.getMesh());
            if (t == null) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                Main.MAIN_TASKS.add(() -> {
                    try {
                        this.sceneTextures.put(
                                geo.getMesh(),
                                SoftwareTexture.fromGLTexture2D(geo.getMesh().getTextureHint())
                        );
                        Main.checkGLError();
                        future.complete(null);
                    } catch (Exception ex) {
                        future.completeExceptionally(ex);
                    }
                });
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
            }
            stepProgressBar();
        }
    }

    private void calculateAreas() {
        Vector3f p0 = new Vector3f();
        Vector3f p1 = new Vector3f();
        Vector3f p2 = new Vector3f();

        setProgressBarStep(this.scene.length);
        this.areas = new float[this.scene.length];
        for (int i = 0; i < this.scene.length; i++) {
            Geometry geo = this.scene[i];
            this.currentStatus = "Calculating area (" + geo.getMesh().getName() + ")";
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
            stepProgressBar();
        }
    }

    private void loadGeometry(int index) {
        this.geometry = this.scene[index];

        this.currentProgress = 0f;
        this.currentStatus = "Loading Geometry...";

        if (this.maxLightmapSize > LightmapUVGenerator.BASE_LIGHTMAP_SIZE) {
            float area = this.areas[index];
            area = (area - this.smallestArea) / (this.largestArea - this.smallestArea);
            area *= this.maxLightmapSize - LightmapUVGenerator.BASE_LIGHTMAP_SIZE;
            area += LightmapUVGenerator.BASE_LIGHTMAP_SIZE;
            this.lightmapSize = (int) Math.pow(2.0, Math.round(Math.log(area) / Math.log(2.0)));
        } else {
            this.lightmapSize = this.maxLightmapSize;
        }

        if (this.clampToMeshLightmapSize && this.lightmapSize < this.geometry.getMesh().getLightmapSizeHint()) {
            this.lightmapSize = this.geometry.getMesh().getLightmapSizeHint();
        }
        this.vertices = geometry.getMesh().getVertices();
        this.indices = geometry.getMesh().getIndices();
        this.lightMap = new float[this.lightmapSize * this.lightmapSize * 3];
        this.marginMap = new int[this.lightmapSize * this.lightmapSize];
        this.indexMap = new int[this.lightmapSize * this.lightmapSize * 3];
        this.positionMap = new float[this.lightmapSize * this.lightmapSize * 3];
        this.normalMap = new float[this.lightmapSize * this.lightmapSize * 3];
        this.tangentMap = new float[this.lightmapSize * this.lightmapSize * 3];
        for (int x = 0; x < this.lightmapSize; x++) {
            for (int y = 0; y < this.lightmapSize; y++) {
                this.marginMap[x + (y * this.lightmapSize)] = -1;
            }
        }

        this.currentProgress = 100f;
    }

    private float lerp(Vector3fc weights, int i0, int i1, int i2, int offset) {
        float va  = this.vertices[(i0 * MeshData.SIZE) + offset];
        float vb = this.vertices[(i1 * MeshData.SIZE) + offset];
        float vc = this.vertices[(i2 * MeshData.SIZE) + offset];
        return (va  * weights.x()) + (vb * weights.y()) + (vc * weights.z());
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

    private void computeBuffers() {
        setProgressBarStep(this.indices.length);

        Vector3f weights = new Vector3f();

        Vector3f pixelPos = new Vector3f();

        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        Vector3f tangent = new Vector3f();

        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();

        for (int i = 0; i < this.indices.length; i += 3) {
            this.currentStatus = "Computing Deferred Buffers (" + i + "/" + this.indices.length + ")";

            int i0 = this.indices[i + 0];
            int i1 = this.indices[i + 1];
            int i2 = this.indices[i + 2];
            int v0 = (i0 * MeshData.SIZE);
            int v1 = (i1 * MeshData.SIZE);
            int v2 = (i2 * MeshData.SIZE);

            float v0x = this.vertices[v0 + MeshData.L_UV_OFFSET + 0] * this.lightmapSize;
            float v0y = this.vertices[v0 + MeshData.L_UV_OFFSET + 1] * this.lightmapSize;

            float v1x = this.vertices[v1 + MeshData.L_UV_OFFSET + 0] * this.lightmapSize;
            float v1y = this.vertices[v1 + MeshData.L_UV_OFFSET + 1] * this.lightmapSize;

            float v2x = this.vertices[v2 + MeshData.L_UV_OFFSET + 0] * this.lightmapSize;
            float v2y = this.vertices[v2 + MeshData.L_UV_OFFSET + 1] * this.lightmapSize;

            a.set(v0x, v0y, 0f);
            b.set(v1x, v1y, 0f);
            c.set(v2x, v2y, 0f);

            int minX = (int) Math.floor(Math.min(v0x, Math.min(v1x, v2x)));
            int minY = (int) Math.floor(Math.min(v0y, Math.min(v1y, v2y)));
            int maxX = (int) Math.ceil(Math.max(v0x, Math.max(v1x, v2x)));
            int maxY = (int) Math.ceil(Math.max(v0y, Math.max(v1y, v2y)));

            minX = clamp(minX, 0, this.lightmapSize - 1);
            minY = clamp(minY, 0, this.lightmapSize - 1);
            maxX = clamp(maxX, 0, this.lightmapSize - 1);
            maxY = clamp(maxY, 0, this.lightmapSize - 1);

            int margin = calculateMargin(v0, v1, v2);
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    pixelPos.set(x + 0.5f, y + 0.5f, 0f);
                    RasterUtils.barycentricWeights(pixelPos, a, b, c, weights);

                    float wx = weights.x();
                    float wy = weights.y();
                    float wz = weights.z();

                    if (wx < 0f || wy < 0f || wz < 0f) {
                        continue;
                    }

                    this.marginMap[x + (y * this.lightmapSize)] = margin;

                    this.indexMap[((x * 3) + 0) + (y * this.lightmapSize * 3)] = i0;
                    this.indexMap[((x * 3) + 1) + (y * this.lightmapSize * 3)] = i1;
                    this.indexMap[((x * 3) + 2) + (y * this.lightmapSize * 3)] = i2;

                    position.set(
                            lerp(weights, i0, i1, i2, MeshData.XYZ_OFFSET + 0),
                            lerp(weights, i0, i1, i2, MeshData.XYZ_OFFSET + 1),
                            lerp(weights, i0, i1, i2, MeshData.XYZ_OFFSET + 2)
                    );
                    normal.set(
                            lerp(weights, i0, i1, i2, MeshData.N_XYZ_OFFSET + 0),
                            lerp(weights, i0, i1, i2, MeshData.N_XYZ_OFFSET + 1),
                            lerp(weights, i0, i1, i2, MeshData.N_XYZ_OFFSET + 2)
                    );
                    tangent.set(
                            lerp(weights, i0, i1, i2, MeshData.T_XYZ_OFFSET + 0),
                            lerp(weights, i0, i1, i2, MeshData.T_XYZ_OFFSET + 1),
                            lerp(weights, i0, i1, i2, MeshData.T_XYZ_OFFSET + 2)
                    );

                    this.geometry.getModel().transformProject(position);
                    this.geometry.getNormalModel().transform(normal).normalize();
                    this.geometry.getNormalModel().transform(tangent).normalize();

                    this.positionMap[((x * 3) + 0) + (y * this.lightmapSize * 3)] = position.x();
                    this.positionMap[((x * 3) + 1) + (y * this.lightmapSize * 3)] = position.y();
                    this.positionMap[((x * 3) + 2) + (y * this.lightmapSize * 3)] = position.z();

                    this.normalMap[((x * 3) + 0) + (y * this.lightmapSize * 3)] = normal.x();
                    this.normalMap[((x * 3) + 1) + (y * this.lightmapSize * 3)] = normal.y();
                    this.normalMap[((x * 3) + 2) + (y * this.lightmapSize * 3)] = normal.z();

                    this.tangentMap[((x * 3) + 0) + (y * this.lightmapSize * 3)] = tangent.x();
                    this.tangentMap[((x * 3) + 1) + (y * this.lightmapSize * 3)] = tangent.y();
                    this.tangentMap[((x * 3) + 2) + (y * this.lightmapSize * 3)] = tangent.z();
                }
            }

            for (int j = 0; j < 3; j++) {
                stepProgressBar();
            }
        }
    }

    private void bakeLightmap() {
        setProgressBarStep(this.lightmapSize);

        int amountOfCores = Runtime.getRuntime().availableProcessors();
        ExecutorService service = Executors.newFixedThreadPool(amountOfCores);
        List<Future<?>> tasks = new ArrayList<>(amountOfCores);
        for (int y = 0; y < this.lightmapSize; y += amountOfCores) {
            this.currentStatus = "Baking (" + y + "/" + this.lightmapSize + ")";
            for (int i = 0; i < amountOfCores; i++) {
                final int line = y + i;
                if (line >= this.lightmapSize) {
                    break;
                }
                tasks.add(service.submit(() -> {
                    processLine(line);
                }));
            }
            for (Future<?> f : tasks) {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException ex) {
                    service.shutdown();
                    throw new RuntimeException(ex);
                }
            }
            tasks.clear();
            for (int i = 0; i < amountOfCores; i++) {
                stepProgressBar();
            }
        }
        service.shutdown();
    }

    private void processLine(int y) {
        Vector3i triangle = new Vector3i();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        Vector3f tangent = new Vector3f();
        Vector3f outColor = new Vector3f();
        for (int x = 0; x < this.lightmapSize; x++) {
            int margin = this.marginMap[x + (y * this.lightmapSize)];
            if (margin == -1) {
                continue;
            }

            int i0 = this.indexMap[((x * 3) + 0) + (y * this.lightmapSize * 3)];
            int i1 = this.indexMap[((x * 3) + 1) + (y * this.lightmapSize * 3)];
            int i2 = this.indexMap[((x * 3) + 2) + (y * this.lightmapSize * 3)];

            float wx = this.positionMap[((x * 3) + 0) + (y * this.lightmapSize * 3)];
            float wy = this.positionMap[((x * 3) + 1) + (y * this.lightmapSize * 3)];
            float wz = this.positionMap[((x * 3) + 2) + (y * this.lightmapSize * 3)];

            float nx = this.normalMap[((x * 3) + 0) + (y * this.lightmapSize * 3)];
            float ny = this.normalMap[((x * 3) + 1) + (y * this.lightmapSize * 3)];
            float nz = this.normalMap[((x * 3) + 2) + (y * this.lightmapSize * 3)];

            float tx = this.tangentMap[((x * 3) + 0) + (y * this.lightmapSize * 3)];
            float ty = this.tangentMap[((x * 3) + 1) + (y * this.lightmapSize * 3)];
            float tz = this.tangentMap[((x * 3) + 2) + (y * this.lightmapSize * 3)];

            triangle.set(i0, i1, i2);
            position.set(wx, wy, wz);
            normal.set(nx, ny, nz);
            tangent.set(tx, ty, tz);

            outColor.zero();

            processPixel(x, y, triangle, position, normal, tangent, outColor);

            this.lightMap[((x * 3) + 0) + (y * this.lightmapSize * 3)] = outColor.x();
            this.lightMap[((x * 3) + 1) + (y * this.lightmapSize * 3)] = outColor.y();
            this.lightMap[((x * 3) + 2) + (y * this.lightmapSize * 3)] = outColor.z();
        }
    }

    private float testShadow(float pixelX, float pixelY, Vector3i triangle) {
        float v0x = this.vertices[(triangle.x() * MeshData.SIZE) + MeshData.L_UV_OFFSET + 0] * this.lightmapSize;
        float v0y = this.vertices[(triangle.x() * MeshData.SIZE) + MeshData.L_UV_OFFSET + 1] * this.lightmapSize;

        float v1x = this.vertices[(triangle.y() * MeshData.SIZE) + MeshData.L_UV_OFFSET + 0] * this.lightmapSize;
        float v1y = this.vertices[(triangle.y() * MeshData.SIZE) + MeshData.L_UV_OFFSET + 1] * this.lightmapSize;

        float v2x = this.vertices[(triangle.z() * MeshData.SIZE) + MeshData.L_UV_OFFSET + 0] * this.lightmapSize;
        float v2y = this.vertices[(triangle.z() * MeshData.SIZE) + MeshData.L_UV_OFFSET + 1] * this.lightmapSize;

        Vector3f a = new Vector3f(v0x, v0y, 0f);
        Vector3f b = new Vector3f(v1x, v1y, 0f);
        Vector3f c = new Vector3f(v2x, v2y, 0f);
        Vector3f p = new Vector3f(pixelX, pixelY, 0f);
        Vector3f weights = new Vector3f();
        RasterUtils.barycentricWeights(p, a, b, c, weights);

        Vector3f position = new Vector3f(
                lerp(weights, triangle.x(), triangle.y(), triangle.z(), MeshData.XYZ_OFFSET + 0),
                lerp(weights, triangle.x(), triangle.y(), triangle.z(), MeshData.XYZ_OFFSET + 1),
                lerp(weights, triangle.x(), triangle.y(), triangle.z(), MeshData.XYZ_OFFSET + 2)
        );

        float shadow = 1f;
        RayResult[] results = Geometry.testRay(position, this.sunDirection, this.scene);
        for (RayResult r : results) {
            if (r.getDistance() < 0.001f) {
                continue;
            }
            if (r.getGeometry() == this.geometry && r.i0() == triangle.x() && r.i1() == triangle.y() && r.i2() == triangle.z()) {
                continue;
            }
            shadow = 0f;
            break;
        }

        return shadow;
    }
    
    private void processPixel(int pixelX, int pixelY, Vector3i triangle, Vector3fc position, Vector3fc normal, Vector3fc tangent, Vector3f outColor) {
        int pcfSize = 4;
        float shadow = 0f;
        for (int x = 0; x < pcfSize; x++) {
            for (int y = 0; y < pcfSize; y++) {
                shadow += testShadow(
                        pixelX + (x * (1f / pcfSize)),
                        pixelY + (y * (1f / pcfSize)),
                        triangle
                );
            }
        }
        shadow /= pcfSize * pcfSize;
        
        outColor
                .set(1.5f, 1.5f, 1.5f)
                .mul(Math.max(normal.dot(this.sunDirection), 0f))
                .mul(shadow)
                .add(0.2f, 0.2f, 0.2f);
    }

    private void fillLightmapMargin() {
        this.currentStatus = "Filling Margins";
        setProgressBarStep(4);

        fillLightmapRight(false);
        stepProgressBar();
        fillLightmapLeft(false);
        stepProgressBar();
        fillLightmapTop(false);
        stepProgressBar();
        fillLightmapBottom(false);
        stepProgressBar();
    }

    private void fillLightmapCompletely() {
        this.currentStatus = "Filling Empty Pixels";
        setProgressBarStep(4);

        fillLightmapRight(true);
        stepProgressBar();
        fillLightmapLeft(true);
        stepProgressBar();
        fillLightmapTop(true);
        stepProgressBar();
        fillLightmapBottom(true);
        stepProgressBar();
    }

    private void fillLightmapRight(boolean ignoreMarginLimit) {
        for (int y = 0; y < this.lightmapSize; y++) {
            float colorR = 0f;
            float colorG = 0f;
            float colorB = 0f;
            int margin = -1;
            int marginSize = -1;
            for (int x = 0; x < this.lightmapSize; x++) {
                int currentMargin = this.marginMap[x + (y * this.lightmapSize)];
                if (currentMargin == -1 && (margin > 0 || (ignoreMarginLimit && marginSize >= 0))) {
                    this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 0] = colorR;
                    this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 1] = colorG;
                    this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 2] = colorB;
                    this.marginMap[x + (y * this.lightmapSize)] = marginSize;
                    margin--;
                } else {
                    margin = currentMargin;
                    marginSize = currentMargin;
                    colorR = this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 0];
                    colorG = this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 1];
                    colorB = this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 2];
                }
            }
        }
    }

    private void fillLightmapLeft(boolean ignoreMarginLimit) {
        for (int y = 0; y < this.lightmapSize; y++) {
            float colorR = 0f;
            float colorG = 0f;
            float colorB = 0f;
            int margin = -1;
            int marginSize = -1;
            for (int x = (this.lightmapSize - 1); x >= 0; x--) {
                int currentMargin = this.marginMap[x + (y * this.lightmapSize)];
                if (currentMargin == -1 && (margin > 0 || (ignoreMarginLimit && marginSize >= 0))) {
                    this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 0] = colorR;
                    this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 1] = colorG;
                    this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 2] = colorB;
                    this.marginMap[x + (y * this.lightmapSize)] = marginSize;
                    margin--;
                } else {
                    margin = currentMargin;
                    marginSize = currentMargin;
                    colorR = this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 0];
                    colorG = this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 1];
                    colorB = this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 2];
                }
            }
        }
    }

    private void fillLightmapTop(boolean ignoreMarginLimit) {
        for (int x = 0; x < this.lightmapSize; x++) {
            float colorR = 0f;
            float colorG = 0f;
            float colorB = 0f;
            int margin = -1;
            int marginSize = -1;
            for (int y = 0; y < this.lightmapSize; y++) {
                int currentMargin = this.marginMap[x + (y * this.lightmapSize)];
                if (currentMargin == -1 && (margin > 0 || (ignoreMarginLimit && marginSize >= 0))) {
                    this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 0] = colorR;
                    this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 1] = colorG;
                    this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 2] = colorB;
                    this.marginMap[x + (y * this.lightmapSize)] = marginSize;
                    margin--;
                } else {
                    margin = currentMargin;
                    marginSize = currentMargin;
                    colorR = this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 0];
                    colorG = this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 1];
                    colorB = this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 2];
                }
            }
        }
    }

    private void fillLightmapBottom(boolean ignoreMarginLimit) {
        for (int x = 0; x < this.lightmapSize; x++) {
            float colorR = 0f;
            float colorG = 0f;
            float colorB = 0f;
            int margin = -1;
            int marginSize = -1;
            for (int y = (this.lightmapSize - 1); y >= 0; y--) {
                int currentMargin = this.marginMap[x + (y * this.lightmapSize)];
                if (currentMargin == -1 && (margin > 0 || (ignoreMarginLimit && marginSize >= 0))) {
                    this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 0] = colorR;
                    this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 1] = colorG;
                    this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 2] = colorB;
                    this.marginMap[x + (y * this.lightmapSize)] = marginSize;
                    margin--;
                } else {
                    margin = currentMargin;
                    marginSize = currentMargin;
                    colorR = this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 0];
                    colorG = this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 1];
                    colorB = this.lightMap[((x * 3) + (y * this.lightmapSize * 3)) + 2];
                }
            }
        }
    }

    private void createLightmapTexture() {
        this.currentStatus = "Creating texture";
        this.currentProgress = 0f;

        final float[] lightmapCopy = this.lightMap.clone();
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

        this.currentProgress = 100f;
    }

    public String getCurrentStatus() {
        if (this.geometry != null) {
            return "[" + this.geometry.getMesh().getName() + "] " + this.currentStatus;
        }
        return currentStatus;
    }

    public int getCurrentProgress() {
        return (int) currentProgress;
    }

    public String getCurrentProgressBar() {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        int progress = getCurrentProgress() / 5;
        for (int i = 0; i < progress; i++) {
            builder.append('#');
        }
        for (int i = progress; i < 20; i++) {
            builder.append('.');
        }
        builder.append(']').append(" - ").append(getCurrentProgress()).append('%');
        return builder.toString();
    }

    public boolean isDone() {
        if (this.processingTask == null) {
            return false;
        }
        return this.processingTask.isDone();
    }

    public boolean isProcessing() {
        return this.processingTask != null;
    }

    private void setProgressBarStep(float max) {
        this.currentProgress = 0f;
        this.progressBarStep = 100f / max;
    }

    private void stepProgressBar() {
        if ((this.currentProgress + this.progressBarStep) > 100f) {
            this.currentProgress = 100f;
            return;
        }
        this.currentProgress += this.progressBarStep;
    }

    public void beginProcessing() {
        if (this.processingTask != null) {
            return;
        }
        this.processingTask = CompletableFuture.runAsync(() -> {
            waitForBVHs();
            loadTextures();
            calculateAreas();
            for (int i = 0; i < this.scene.length; i++) {
                loadGeometry(i);
                computeBuffers();
                bakeLightmap();
                fillLightmapMargin();
                fillLightmapCompletely();
                createLightmapTexture();
            }
            this.geometry = null;
            this.currentStatus = "Idle";
            this.currentProgress = 0;
        });
    }

    public void finishProcessing() {
        if (this.processingTask == null) {
            return;
        }
        try {
            this.processingTask.get();
        } catch (InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }
        this.processingTask = null;
    }

}
