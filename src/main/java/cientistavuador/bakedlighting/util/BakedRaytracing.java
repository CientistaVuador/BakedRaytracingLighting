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
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.lwjgl.opengl.EXTTextureCompressionS3TC;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.system.MemoryUtil;

/**
 *
 * @author Cien
 */
public class BakedRaytracing {

    public static final float PIXEL_OFFSET = 0.5f;
    private static final float[] SAMPLE_POSITIONS = {
        (1f + 0.5f) / 4f, (0f + 0.5f) / 4f,
        (3f + 0.5f) / 4f, (1f + 0.5f) / 4f,
        (0f + 0.5f) / 4f, (2f + 0.5f) / 4f,
        (2f + 0.5f) / 4f, (3f + 0.5f) / 4f
    };
    private static final int SAMPLES = 4;

    private static class PositionBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final float[] data;

        public PositionBuffer(int size) {
            this.sampleSize = 3;
            this.vectorSize = this.sampleSize * SAMPLES;
            this.lineSize = size * this.vectorSize;
            this.data = new float[size * this.lineSize];
        }

        public void write(Vector3f position, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = position.x();
            this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = position.y();
            this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = position.z();
        }

        public void read(Vector3f position, int x, int y, int sample) {
            position.set(
                    this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)],
                    this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)],
                    this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)]
            );
        }

    }

    private static class IndicesBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final int[] data;

        public IndicesBuffer(int size) {
            this.sampleSize = 3;
            this.vectorSize = this.sampleSize * SAMPLES;
            this.lineSize = size * this.vectorSize;
            this.data = new int[size * this.lineSize];
        }

        public void write(Vector3i indices, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = indices.x();
            this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = indices.y();
            this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = indices.z();
        }

        public void read(Vector3i indices, int x, int y, int sample) {
            indices.set(
                    this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)],
                    this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)],
                    this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)]
            );
        }

    }

    private static class BooleanBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final boolean[] data;

        public BooleanBuffer(int size) {
            this.sampleSize = 1;
            this.vectorSize = this.sampleSize * SAMPLES;
            this.lineSize = size * this.vectorSize;
            this.data = new boolean[size * this.lineSize];
        }

        public void write(boolean value, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = value;
        }

        public boolean read(int x, int y, int sample) {
            return this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
        }
    }

    private static class UnitVectorBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final byte[] data;

        public UnitVectorBuffer(int size) {
            this.sampleSize = 3;
            this.vectorSize = this.sampleSize * SAMPLES;
            this.lineSize = size * this.vectorSize;
            this.data = new byte[size * this.lineSize];
        }

        public void write(Vector3f unit, int x, int y, int sample) {
            int vx = (int) ((unit.x() + 1f) * 255f * 0.5f);
            int vy = (int) ((unit.y() + 1f) * 255f * 0.5f);
            int vz = (int) ((unit.z() + 1f) * 255f * 0.5f);
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = (byte) vx;
            this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = (byte) vy;
            this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = (byte) vz;
        }

        public void read(Vector3f unit, int x, int y, int sample) {
            byte vx = this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
            byte vy = this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
            byte vz = this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
            int vxu = ((int) vx) & 0xFF;
            int vyu = ((int) vy) & 0xFF;
            int vzu = ((int) vz) & 0xFF;
            float inv = 1f / (255f * 0.5f);
            unit.set(
                    (vxu * inv) - 1f,
                    (vyu * inv) - 1f,
                    (vzu * inv) - 1f
            );
        }

    }

    private final Geometry[] scene;
    private final Map<MeshData, SoftwareTexture> sceneTextures = new HashMap<>();
    private final int maxLightmapSize;
    private final Vector3f sunDirection;

    private float[] areas = null;
    private float largestArea = 0f;
    private float smallestArea = 0f;

    private Geometry geometry = null;
    private int lightmapSize;
    private float[] vertices = null;
    private int[] indices = null;

    private BooleanBuffer sampleBuffer = null;
    private IndicesBuffer indicesBuffer = null;
    private PositionBuffer positionBuffer = null;
    private UnitVectorBuffer normalBuffer = null;
    private UnitVectorBuffer tangentBuffer = null;
    private ByteBuffer outputBuffer = null;
    private Cleaner.Cleanable outputBufferCleanable = null;

    private Future<Void> processingTask = null;
    private String currentStatus = "Idle";
    private float progressBarStep = 0f;
    private float currentProgress = 0f;

    public BakedRaytracing(Geometry[] scene, int maxLightmapSize, Vector3fc sunDirection) {
        this.scene = scene;
        this.maxLightmapSize = maxLightmapSize;
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
        this.lightmapSize = this.maxLightmapSize;

        this.vertices = geometry.getMesh().getVertices();
        this.indices = geometry.getMesh().getIndices();

        this.sampleBuffer = new BooleanBuffer(this.lightmapSize);
        this.indicesBuffer = new IndicesBuffer(this.lightmapSize);
        this.positionBuffer = new PositionBuffer(this.lightmapSize);
        this.normalBuffer = new UnitVectorBuffer(this.lightmapSize);
        this.tangentBuffer = new UnitVectorBuffer(this.lightmapSize);
        final ByteBuffer output = MemoryUtil.memCalloc(this.lightmapSize * this.lightmapSize * 3);
        this.outputBufferCleanable = ObjectCleaner.get().register(output, () -> {
            MemoryUtil.memFree(output);
        });
        this.outputBuffer = output;

        this.currentProgress = 100f;
    }

    private float lerp(Vector3fc weights, int i0, int i1, int i2, int offset) {
        float va  = this.vertices[(i0 * MeshData.SIZE) + offset];
        float vb = this.vertices[(i1 * MeshData.SIZE) + offset];
        float vc = this.vertices[(i2 * MeshData.SIZE) + offset];
        return (va  * weights.x()) + (vb * weights.y()) + (vc * weights.z());
    }

    private int clamp(int v, int min, int max) {
        if (v > max) {
            return max;
        }
        if (v < min) {
            return min;
        }
        return v;
    }

    private void clampVector(Vector3f v, float min, float max) {
        float x = v.x();
        float y = v.y();
        float z = v.z();
        if (x > max) {
            x = max;
        } else if (x < min) {
            x = min;
        }
        if (y > max) {
            y = max;
        } else if (y < min) {
            y = min;
        }
        if (z > max) {
            z = max;
        } else if (z < min) {
            z = min;
        }
        v.set(x, y, z);
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

        Vector3f aOffset = new Vector3f();
        Vector3f bOffset = new Vector3f();
        Vector3f cOffset = new Vector3f();

        Vector3i indicesVector = new Vector3i();

        for (int i = 0; i < this.indices.length; i += 3) {
            this.currentStatus = "Computing Deferred Buffers (" + i + "/" + this.indices.length + ")";

            int i0 = this.indices[i + 0];
            int i1 = this.indices[i + 1];
            int i2 = this.indices[i + 2];

            indicesVector.set(i0, i1, i2);

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

            float v0Angle = this.vertices[v0 + MeshData.L_UV_ANGLE_OFFSET + 0];
            float v1Angle = this.vertices[v1 + MeshData.L_UV_ANGLE_OFFSET + 0];
            float v2Angle = this.vertices[v2 + MeshData.L_UV_ANGLE_OFFSET + 0];

            aOffset.set(Math.cos(v0Angle), Math.sin(v0Angle), 0f);
            bOffset.set(Math.cos(v1Angle), Math.sin(v1Angle), 0f);
            cOffset.set(Math.cos(v2Angle), Math.sin(v2Angle), 0f);

            clampVector(aOffset.mul(2f), -1f, 1f);
            clampVector(bOffset.mul(2f), -1f, 1f);
            clampVector(cOffset.mul(2f), -1f, 1f);

            aOffset.mul(PIXEL_OFFSET).add(a);
            bOffset.mul(PIXEL_OFFSET).add(b);
            cOffset.mul(PIXEL_OFFSET).add(c);

            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int s = 0; s < SAMPLES; s++) {
                        float sampleX = SAMPLE_POSITIONS[(s * 2) + 0];
                        float sampleY = SAMPLE_POSITIONS[(s * 2) + 1];

                        pixelPos.set(x + sampleX, y + sampleY, 0f);

                        RasterUtils.barycentricWeights(pixelPos, a, b, c, weights);

                        float wx = weights.x();
                        float wy = weights.y();
                        float wz = weights.z();

                        if (wx < 0f || wy < 0f || wz < 0f) {
                            continue;
                        }

                        RasterUtils.barycentricWeights(pixelPos, aOffset, bOffset, cOffset, weights);

                        this.sampleBuffer.write(true, x, y, s);
                        this.indicesBuffer.write(indicesVector, x, y, s);

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

                        this.positionBuffer.write(position, x, y, s);
                        this.normalBuffer.write(normal, x, y, s);
                        this.tangentBuffer.write(tangent, x, y, s);
                    }
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
        byte[] lineColorData = new byte[this.lightmapSize * 3];

        Vector3i triangle = new Vector3i();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        Vector3f tangent = new Vector3f();

        Vector3f outColor = new Vector3f();
        Vector3f colorAverage = new Vector3f();

        int lastValidX = -1;
        for (int x = 0; x < this.lightmapSize; x++) {
            colorAverage.zero();

            int pixelsProcessed = 0;
            for (int s = 0; s < SAMPLES; s++) {
                boolean filled = this.sampleBuffer.read(x, y, s);
                if (!filled) {
                    continue;
                }

                this.indicesBuffer.read(triangle, x, y, s);
                this.positionBuffer.read(position, x, y, s);
                this.normalBuffer.read(normal, x, y, s);
                this.tangentBuffer.read(tangent, x, y, s);

                outColor.zero();
                processSample(x, y, s, triangle, position, normal, tangent, outColor);

                colorAverage.add(outColor);
                pixelsProcessed++;
            }

            if (pixelsProcessed != 0) {
                colorAverage.div(pixelsProcessed);

                int r = (int) (Math.min(colorAverage.x(), 1f) * 255f);
                int g = (int) (Math.min(colorAverage.y(), 1f) * 255f);
                int b = (int) (Math.min(colorAverage.z(), 1f) * 255f);

                lineColorData[(x * 3) + 0] = (byte) r;
                lineColorData[(x * 3) + 1] = (byte) g;
                lineColorData[(x * 3) + 2] = (byte) b;

                lastValidX = x;
            } else if (lastValidX != -1) {
                System.arraycopy(lineColorData, lastValidX * 3, lineColorData, x * 3, 3);
            }
        }

        this.outputBuffer.put(y * this.lightmapSize * 3, lineColorData);
    }

    private void processSample(int pixelX, int pixelY, int sample, Vector3i triangle, Vector3fc position, Vector3fc normal, Vector3fc tangent, Vector3f outColor) {
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

        outColor
                .set(1.5f, 1.5f, 1.5f)
                .mul(Math.max(normal.dot(this.sunDirection), 0f))
                .mul(shadow)
                .add(0.2f, 0.2f, 0.2f);
    }
    
    private void createLightmapTexture() {
        this.currentStatus = "Creating texture";
        this.currentProgress = 0f;

        final ByteBuffer outputBufferCopy = this.outputBuffer;
        final Cleaner.Cleanable outputBufferCleanableCopy = this.outputBufferCleanable;
        final int lightmapSizeCopy = this.lightmapSize;
        final Geometry geometryCopy = this.geometry;

        Main.MAIN_TASKS.add(() -> {
            int texture = glGenTextures();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture);
            
            int internalFormat = GL_RGB8;
            if (GL.getCapabilities().GL_EXT_texture_compression_s3tc) {
                internalFormat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT;
            }
            glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, lightmapSizeCopy, lightmapSizeCopy, 0, GL_RGB, GL_UNSIGNED_BYTE, outputBufferCopy);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            glBindTexture(GL_TEXTURE_2D, 0);
            geometryCopy.setLightmapTextureHint(texture);

            outputBufferCleanableCopy.clean();
        });

        this.currentProgress = 100f;
    }

    private void clear() {
        this.geometry = null;
        this.currentStatus = "Idle";
        this.currentProgress = 0;
        this.sampleBuffer = null;
        this.indicesBuffer = null;
        this.positionBuffer = null;
        this.normalBuffer = null;
        this.tangentBuffer = null;
        this.outputBufferCleanable = null;
        this.outputBuffer = null;
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
                createLightmapTexture();
            }
            clear();
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
