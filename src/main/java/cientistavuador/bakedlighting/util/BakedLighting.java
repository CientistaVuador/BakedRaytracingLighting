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
import java.io.File;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import org.joml.Matrix3f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.system.MemoryUtil;

/**
 *
 * @author Cien
 */
public class BakedLighting {

    public static class Status {

        private Future<Void> task;

        private String currentStatus = "Idle";
        private float currentProgress = 0f;
        private boolean error = false;
        private long memoryUsage = 0;

        private float progressBarStep = 0f;

        private long timeStart = 0;
        private volatile long rays = 0;

        public Status() {

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

        public int getCurrentProgress() {
            return (int) this.currentProgress;
        }

        public String getCurrentStatus() {
            return currentStatus;
        }

        public String getASCIIProgressBar() {
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
            return this.task.isDone();
        }

        public boolean hasError() {
            return this.error;
        }

        public void throwException() throws ExecutionException {
            if (!isDone()) {
                return;
            }
            try {
                this.task.get();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }

        public double getRaysPerSecond() {
            double time = (System.currentTimeMillis() - this.timeStart);
            if (time == 0) {
                return 0;
            }
            double raysPerSecond = (this.rays / time) * 1000f;
            return raysPerSecond;
        }

        public String getRaysPerSecondFormatted() {
            StringBuilder b = new StringBuilder();
            Formatter formatter = new Formatter(b);
            formatter.format("%,.2f", getRaysPerSecond());
            b.append(" Rays Per Second");
            return b.toString();
        }

        public long getMemoryUsage() {
            return memoryUsage;
        }

        public String getMemoryUsageFormatted() {
            int unit = 0;
            long memory = this.memoryUsage;
            for (int i = 0; i < Integer.MAX_VALUE; i++) {
                memory /= 1000;
                if (memory > 0) {
                    unit++;
                } else {
                    break;
                }
            }
            memory = this.memoryUsage;
            String value = String.format("%.2f", memory / Math.pow(1000.0, unit));
            switch (unit) {
                case 0 ->
                    value += " B";
                case 1 ->
                    value += " KB";
                case 2 ->
                    value += " MB";
                case 3 ->
                    value += " GB";
                case 4 ->
                    value += " TB";
                default ->
                    value += " * " + Math.pow(1000.0, unit) + " B";
            }
            return value;
        }

    }

    public static Status dummyStatus() {
        Status status = new Status();

        status.currentStatus = "Idle";
        status.currentProgress = 0f;

        CompletableFuture<Void> future = new CompletableFuture<>();
        future.complete(null);
        status.task = future;

        return status;
    }

    public static Status bake(Scene scene, float pixelToWorldRatio) {
        Status status = new Status();
        BakedLighting baked = new BakedLighting(scene, pixelToWorldRatio, status);
        status.task = CompletableFuture.runAsync(() -> {
            try {
                baked.bake();
                status.currentProgress = 100f;
                status.currentStatus = "Done.";
            } catch (Throwable t) {
                status.currentProgress = 0f;
                status.currentStatus = "Error: " + t.getLocalizedMessage();
                status.error = true;
                throw t;
            }
        });
        return status;
    }

    private static class WeightsBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final float[] data;

        public WeightsBuffer(int size, int samples) {
            this.sampleSize = 3;
            this.vectorSize = this.sampleSize * samples;
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

    private static class IntegerDataBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final int[] data;

        public IntegerDataBuffer(int size, int samples) {
            this.sampleSize = 1;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new int[size * this.lineSize];
        }

        public void write(int data, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = data;
        }
        
        public int read(int x, int y, int sample) {
            return this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
        }

    }

    private static class BooleanBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final boolean[] data;

        public BooleanBuffer(int size, int samples) {
            this.sampleSize = 1;
            this.vectorSize = this.sampleSize * samples;
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

    private static class ColorBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final float[] data;

        public ColorBuffer(int size, int samples) {
            this.sampleSize = 3;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new float[size * this.lineSize];
        }

        public void write(Vector3f color, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = color.x();
            this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = color.y();
            this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = color.z();
        }

        public void read(Vector3f color, int x, int y, int sample) {
            float vx = this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
            float vy = this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
            float vz = this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
            color.set(
                    vx,
                    vy,
                    vz
            );
        }
    }

    private static class GrayBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final float[] data;

        public GrayBuffer(int size, int samples) {
            this.sampleSize = 1;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new float[size * this.lineSize];
        }

        public void write(float value, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = value;
        }

        public float read(int x, int y, int sample) {
            return this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
        }
    }

    private final Scene scene;
    private final List<Geometry> geometries;
    private final float pixelToWorldRatio;
    private final Status status;

    private final Map<Integer, SoftwareTexture> sceneTextures = new HashMap<>();

    private MeshData.LightmapMesh[] lightmaps = null;

    private int geometryIndex = 0;
    private Geometry geometry = null;
    private int geometryLightmapSize;
    private float[] vertices = null;
    private int[] indices = null;
    private LightmapUVs.LightmapperQuad[] lightmapperQuads = null;
    private MeshData.LightmapMesh lightmap = null;

    private BooleanBuffer sampleBuffer = null;

    private IntegerDataBuffer trianglesBuffer = null;
    private IntegerDataBuffer quadsBuffer = null;
    private WeightsBuffer weightsBuffer = null;

    private ColorBuffer indirectColorBuffer = null;
    private ColorBuffer directColorBuffer = null;
    private GrayBuffer reverseShadowBuffer = null;

    private ColorBuffer resultBuffer = null;

    private ByteBuffer outputBuffer = null;
    private Cleaner.Cleanable outputBufferCleanable = null;

    private BakedLighting(Scene scene, float pixelToWorldRatio, Status status) {
        this.scene = scene;
        this.geometries = scene.getGeometries();
        this.pixelToWorldRatio = pixelToWorldRatio;
        this.status = status;
    }

    private void setStatusText(String s) {
        if (this.geometry != null) {
            s = "(" + this.geometryIndex + "/" + this.geometries.size() + ") [" + this.geometry.getMesh().getName() + "] " + s;
        }
        this.status.currentStatus = s;
    }

    private void loadTextures() {
        this.status.setProgressBarStep(this.geometries.size());

        for (Geometry geo : this.geometries) {
            setStatusText("Loading Texture (" + geo.getMesh().getName() + ")");

            SoftwareTexture t = this.sceneTextures.get(geo.getMesh().getTextureHint());
            if (t == null) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                Main.MAIN_TASKS.add(() -> {
                    try {
                        this.sceneTextures.put(
                                geo.getMesh().getTextureHint(),
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

            this.status.stepProgressBar();
        }
    }

    private void scheduleLightmaps() {
        this.status.setProgressBarStep(this.geometries.size());

        Vector3f scale = new Vector3f();

        this.lightmaps = new MeshData.LightmapMesh[this.geometries.size()];
        for (int i = 0; i < this.lightmaps.length; i++) {
            Geometry geo = this.geometries.get(i);

            setStatusText("(" + i + "/" + this.geometries.size() + ") Scheduling Lightmap UV (" + geo.getMesh().getName() + ")");

            geo.getModel().getScale(scale);
            this.lightmaps[i] = geo.getMesh()
                    .scheduleLightmapMesh(
                            this.pixelToWorldRatio,
                            scale.x(), scale.y(), scale.z()
                    );
            
            this.status.stepProgressBar();
        }
    }

    private void waitForLightmaps() {
        this.status.setProgressBarStep(this.geometries.size());

        for (int i = 0; i < this.lightmaps.length; i++) {
            Geometry geo = this.geometries.get(i);

            setStatusText("(" + i + "/" + this.geometries.size() + ") Waiting for Lightmap UV (" + geo.getMesh().getName() + ")");

            this.lightmaps[i].getLightmapSize();

            this.status.stepProgressBar();
        }
    }

    private void waitForBVHs() {
        this.status.setProgressBarStep(this.geometries.size());
        for (Geometry geo : this.geometries) {
            setStatusText("Waiting For BVH To Compute (" + geo.getMesh().getName() + ")");
            geo.getMesh().getBVH();
            this.status.stepProgressBar();
        }
    }

    private void loadGeometry(int index) {
        this.geometryIndex = index;
        this.geometry = this.geometries.get(index);

        this.status.currentProgress = 0f;
        setStatusText("Loading Geometry...");

        this.lightmap = this.lightmaps[index];
        this.geometryLightmapSize = this.lightmap.getLightmapSize();
        this.lightmapperQuads = this.lightmap.getQuads();

        this.vertices = this.geometry.getMesh().getVertices();
        this.indices = this.geometry.getMesh().getIndices();

        int numSamples = this.scene.getSamplingMode().numSamples();

        this.sampleBuffer = new BooleanBuffer(this.geometryLightmapSize, numSamples);
        this.trianglesBuffer = new IntegerDataBuffer(this.geometryLightmapSize, numSamples);
        this.quadsBuffer = new IntegerDataBuffer(this.geometryLightmapSize, numSamples);
        this.weightsBuffer = new WeightsBuffer(this.geometryLightmapSize, numSamples);
        this.indirectColorBuffer = new ColorBuffer(this.geometryLightmapSize, numSamples);
        this.directColorBuffer = new ColorBuffer(this.geometryLightmapSize, numSamples);
        this.reverseShadowBuffer = new GrayBuffer(this.geometryLightmapSize, numSamples);
        this.resultBuffer = new ColorBuffer(this.geometryLightmapSize, 1);
        final ByteBuffer output = MemoryUtil.memCalloc(this.geometryLightmapSize * this.geometryLightmapSize * 4);
        this.outputBufferCleanable = ObjectCleaner.get().register(output, () -> {
            MemoryUtil.memFree(output);
        });
        this.outputBuffer = output;

        this.status.currentProgress = 100f;
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

    private void computeBuffers() {
        Vector3f weights = new Vector3f();

        Vector3f pixelPos = new Vector3f();

        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();

        this.status.setProgressBarStep(this.lightmapperQuads.length);

        for (int i = 0; i < this.lightmapperQuads.length; i++) {
            setStatusText("Computing Deferred Buffers (" + i + "/" + this.lightmapperQuads.length + ")");

            LightmapUVs.LightmapperQuad quad = this.lightmapperQuads[i];

            int[] triangles = quad.getTriangles();
            float[] lightmapVertices = quad.getUVs();

            for (int j = 0; j < triangles.length; j++) {
                int triangle = triangles[j];
                
                float v0x = lightmapVertices[(((j * 3) + 0) * 2) + 0] + quad.getX();
                float v0y = lightmapVertices[(((j * 3) + 0) * 2) + 1] + quad.getY();

                float v1x = lightmapVertices[(((j * 3) + 1) * 2) + 0] + quad.getX();
                float v1y = lightmapVertices[(((j * 3) + 1) * 2) + 1] + quad.getY();

                float v2x = lightmapVertices[(((j * 3) + 2) * 2) + 0] + quad.getX();
                float v2y = lightmapVertices[(((j * 3) + 2) * 2) + 1] + quad.getY();

                a.set(v0x, v0y, 0f);
                b.set(v1x, v1y, 0f);
                c.set(v2x, v2y, 0f);

                int minX = (int) Math.floor(Math.min(v0x, Math.min(v1x, v2x)));
                int minY = (int) Math.floor(Math.min(v0y, Math.min(v1y, v2y)));
                int maxX = (int) Math.ceil(Math.max(v0x, Math.max(v1x, v2x)));
                int maxY = (int) Math.ceil(Math.max(v0y, Math.max(v1y, v2y)));

                minX = clamp(minX, 0, this.geometryLightmapSize - 1);
                minY = clamp(minY, 0, this.geometryLightmapSize - 1);
                maxX = clamp(maxX, 0, this.geometryLightmapSize - 1);
                maxY = clamp(maxY, 0, this.geometryLightmapSize - 1);

                SamplingMode mode = this.scene.getSamplingMode();

                for (int y = minY; y <= maxY; y++) {
                    for (int x = minX; x <= maxX; x++) {
                        for (int s = 0; s < mode.numSamples(); s++) {
                            float sampleX = mode.sampleX(s);
                            float sampleY = mode.sampleY(s);

                            pixelPos.set(x + sampleX, y + sampleY, 0f);

                            RasterUtils.barycentricWeights(pixelPos, a, b, c, weights);

                            float wx = weights.x();
                            float wy = weights.y();
                            float wz = weights.z();

                            if (wx < 0f || wy < 0f || wz < 0f) {
                                continue;
                            }

                            this.sampleBuffer.write(true, x, y, s);
                            this.trianglesBuffer.write(triangle, x, y, s);
                            this.quadsBuffer.write(i, x, y, s);
                            this.weightsBuffer.write(weights, x, y, s);
                        }
                    }
                }
            }

            this.status.stepProgressBar();
        }
    }

    private void bakeLightmap() {
        int amountOfCores = Runtime.getRuntime().availableProcessors();

        this.status.setProgressBarStep(this.geometryLightmapSize);

        ExecutorService service = Executors.newFixedThreadPool(amountOfCores);
        List<Future<?>> tasks = new ArrayList<>(amountOfCores);

        this.status.timeStart = System.currentTimeMillis();
        this.status.rays = 0;
        for (int y = 0; y < this.geometryLightmapSize; y += amountOfCores) {

            setStatusText("Baking (" + y + "/" + this.geometryLightmapSize + ")");

            for (int i = 0; i < amountOfCores; i++) {
                final int line = y + i;
                if (line >= this.geometryLightmapSize) {
                    break;
                }
                tasks.add(service.submit(() -> {
                    processLine(line);
                }));
            }

            for (Future<?> f : tasks) {
                try {
                    f.get();
                    this.status.stepProgressBar();
                } catch (InterruptedException | ExecutionException ex) {
                    service.shutdown();
                    throw new RuntimeException(ex);
                }
            }

            tasks.clear();
        }
        this.status.rays = 0;

        service.shutdown();
    }

    private static class SampleState {

        public final Random random = new Random();
        public int x;
        public int y;
        public int s;
        public int triangle = -1;
        public final Vector3f triangleNormal = new Vector3f();
        public final Vector3f position = new Vector3f();
        public final Vector3f normal = new Vector3f();
        public final Matrix3f TBN = new Matrix3f();
    }

    private class DirectState {

        public final Vector3f output = new Vector3f();
    }

    private class ShadowState {

        public float output;
        public final Vector3f randomDirection = new Vector3f();
        public final Vector3f offsetOrigin = new Vector3f();

    }

    private class IndirectState {

        public final Vector3f output = new Vector3f();
        public final Vector3f[] bounceColors = new Vector3f[BakedLighting.this.scene.getIndirectBounces()];

        {
            for (int i = 0; i < this.bounceColors.length; i++) {
                this.bounceColors[i] = new Vector3f();
            }
        }
        public final Vector3f lightColor = new Vector3f();
        public final Vector3f randomLightDirection = new Vector3f();
        public final Vector3f bouncePosition = new Vector3f();
        public final Vector3f bounceDirection = new Vector3f();
        public final Vector3f bounceWeights = new Vector3f();
        public final float[] bounceColor = new float[4];
    }

    private void processLine(int y) {
        SampleState state = new SampleState();
        DirectState direct = new DirectState();
        ShadowState shadow = new ShadowState();
        IndirectState indirect = new IndirectState();

        Vector3f weights = new Vector3f();

        Vector3f tangent = new Vector3f();
        Vector3f bitangent = new Vector3f();

        int i0 = -1;
        int i1 = -1;
        int i2 = -1;

        for (int x = 0; x < this.geometryLightmapSize; x++) {
            for (int s = 0; s < this.scene.getSamplingMode().numSamples(); s++) {
                boolean filled = this.sampleBuffer.read(x, y, s);
                if (!filled) {
                    continue;
                }

                state.x = x;
                state.y = y;
                state.s = s;

                int currentTriangle = this.trianglesBuffer.read(x, y, s);
                if (currentTriangle != state.triangle) {
                    state.triangle = currentTriangle;
                    i0 = this.indices[(currentTriangle * 3) + 0];
                    i1 = this.indices[(currentTriangle * 3) + 1];
                    i2 = this.indices[(currentTriangle * 3) + 2];
                    MeshUtils.calculateTriangleNormal(
                            this.vertices,
                            MeshData.SIZE,
                            MeshData.XYZ_OFFSET,
                            i0, i1, i2,
                            state.triangleNormal
                    );
                    this.geometry.getNormalModel().transform(state.triangleNormal);
                }

                this.weightsBuffer.read(weights, x, y, s);

                state.position.set(
                        lerp(weights, i0, i1, i2, MeshData.XYZ_OFFSET + 0),
                        lerp(weights, i0, i1, i2, MeshData.XYZ_OFFSET + 1),
                        lerp(weights, i0, i1, i2, MeshData.XYZ_OFFSET + 2)
                );
                state.normal.set(
                        lerp(weights, i0, i1, i2, MeshData.N_XYZ_OFFSET + 0),
                        lerp(weights, i0, i1, i2, MeshData.N_XYZ_OFFSET + 1),
                        lerp(weights, i0, i1, i2, MeshData.N_XYZ_OFFSET + 2)
                );

                tangent.set(
                        lerp(weights, i0, i1, i2, MeshData.T_XYZ_OFFSET + 0),
                        lerp(weights, i0, i1, i2, MeshData.T_XYZ_OFFSET + 1),
                        lerp(weights, i0, i1, i2, MeshData.T_XYZ_OFFSET + 2)
                );

                this.geometry.getModel().transformProject(state.position);
                this.geometry.getNormalModel().transform(state.normal);
                this.geometry.getNormalModel().transform(tangent);

                state.normal.normalize();
                tangent.normalize();
                bitangent.set(state.normal).cross(tangent).normalize();

                state.TBN.set(tangent, bitangent, state.normal);

                direct.output.zero();
                shadow.output = 0f;
                indirect.output.zero();

                processSample(
                        state,
                        direct,
                        shadow,
                        indirect
                );

                this.directColorBuffer.write(direct.output, x, y, s);
                this.reverseShadowBuffer.write(shadow.output, x, y, s);
                this.indirectColorBuffer.write(indirect.output, x, y, s);
            }
        }
    }

    private void randomSunDirection(Vector3f outDirection, Random random) {
        float x;
        float y;
        float z;
        float dist;

        do {
            x = (random.nextFloat() * 2f) - 1f;
            y = (random.nextFloat() * 2f) - 1f;
            z = (random.nextFloat() * 2f) - 1f;
            dist = (x * x) + (y * y) + (z * z);
        } while (dist > 1f);

        outDirection.set(
                x,
                y,
                z
        )
                .normalize()
                .mul(this.scene.getSunSize())
                .add(this.scene.getSunDirectionInverted())
                .normalize();
    }

    private void randomTangentDirection(Vector3f outDirection, Random random) {
        float x;
        float y;
        float z;
        float dist;

        do {
            x = (random.nextFloat() * 2f) - 1f;
            y = (random.nextFloat() * 2f) - 1f;
            z = random.nextFloat();
            dist = (x * x) + (y * y) + (z * z);
        } while (dist > 1f);

        outDirection.set(
                x,
                y,
                z
        )
                .normalize();
    }

    private void processSample(
            SampleState state,
            DirectState direct,
            ShadowState shadow,
            IndirectState indirect
    ) {
        if (this.scene.isDirectLightingEnabled()) {
            processDirect(state, direct);
        }
        if (this.scene.isShadowsEnabled()) {
            processShadow(state, shadow);
        }
        if (this.scene.isIndirectLightingEnabled()) {
            processIndirect(state, indirect);
        }
    }

    private void processDirect(
            SampleState state,
            DirectState direct
    ) {
        direct.output
                .set(this.scene.getSunDiffuseColor())
                .mul(Math.max(state.normal.dot(this.scene.getSunDirectionInverted()), 0f));
    }

    private void processShadow(
            SampleState state,
            ShadowState shadow
    ) {
        shadow.offsetOrigin
                .set(state.triangleNormal)
                .mul(this.scene.getRayOffset())
                .add(state.position);

        float shadowValue = 0f;
        for (int i = 0; i < this.scene.getIndirectRaysPerSample(); i++) {
            randomSunDirection(shadow.randomDirection, state.random);
            if (Geometry.fastTestRay(shadow.offsetOrigin, shadow.randomDirection, this.scene.getGeometries())) {
                shadowValue++;
            }
            this.status.rays++;
        }
        shadowValue /= this.scene.getIndirectRaysPerSample();

        shadow.output = 1f - shadowValue;
    }

    private void processIndirect(
            SampleState state,
            IndirectState indirect
    ) {
        for (int i = 0; i < this.scene.getIndirectRaysPerSample(); i++) {
            randomTangentDirection(indirect.bounceDirection, state.random);
            state.TBN.transform(indirect.bounceDirection);

            float rayOffset = this.scene.getRayOffset();
            float offsetX = state.triangleNormal.x() * rayOffset;
            float offsetY = state.triangleNormal.y() * rayOffset;
            float offsetZ = state.triangleNormal.z() * rayOffset;

            indirect.bouncePosition
                    .set(state.position)
                    .add(offsetX, offsetY, offsetZ);

            boolean foundLight = false;
            int bounceCount = 0;
            for (int j = 0; j < this.scene.getIndirectBounces(); j++) {
                if (j != 0) {
                    randomSunDirection(indirect.randomLightDirection, state.random);
                    this.status.rays++;
                    if (!Geometry.fastTestRay(indirect.bouncePosition, indirect.randomLightDirection, this.scene.getGeometries())) {
                        foundLight = true;
                        indirect.lightColor.set(this.scene.getSunDiffuseColor());
                        break;
                    }
                }

                this.status.rays++;
                RayResult[] results = Geometry.testRay(indirect.bouncePosition, indirect.bounceDirection, this.scene.getGeometries());
                if (results.length == 0) {
                    foundLight = true;
                    indirect.lightColor.set(this.scene.getSunAmbientColor());
                    break;
                }

                RayResult closestRay = results[0];
                closestRay.weights(indirect.bounceWeights);

                float u = closestRay.lerp(indirect.bounceWeights, MeshData.UV_OFFSET + 0);
                float v = closestRay.lerp(indirect.bounceWeights, MeshData.UV_OFFSET + 1);

                SoftwareTexture rayTexture = this.sceneTextures.get(closestRay.getGeometry().getMesh().getTextureHint());
                rayTexture.sampleNearest(u, v, indirect.bounceColor, 0);
                indirect.bounceColors[bounceCount].set(indirect.bounceColor);
                bounceCount++;

                indirect.bouncePosition.set(closestRay.getTriangleNormal());
                if (!closestRay.frontFace()) {
                    indirect.bouncePosition.negate();
                }
                indirect.bouncePosition
                        .mul(this.scene.getRayOffset())
                        .add(closestRay.getHitPosition());

                indirect.bounceDirection.reflect(closestRay.getTriangleNormal());
            }

            if (foundLight) {
                if (bounceCount != 0) {
                    Vector3f first = indirect.bounceColors[0];
                    for (int j = 1; j < bounceCount; j++) {
                        first.mul(indirect.bounceColors[j]);
                    }
                    first.mul(this.scene.getIndirectLightReflectionFactor());
                    float r = Math.min(Math.max(first.x(), 0f), 1f);
                    float g = Math.min(Math.max(first.y(), 0f), 1f);
                    float b = Math.min(Math.max(first.z(), 0f), 1f);
                    indirect.lightColor.mul(r, g, b);
                }
                indirect.output.add(indirect.lightColor);
            }
        }
        indirect.output.div(this.scene.getIndirectRaysPerSample());
    }

    private void denoiseBuffers() {
        int numSamples = this.scene.getSamplingMode().numSamples();
        final ColorBuffer indirectOutput = new ColorBuffer(this.geometryLightmapSize, numSamples);
        final GrayBuffer reversedShadowOutput = new GrayBuffer(this.geometryLightmapSize, numSamples);

        int amountOfCores = Runtime.getRuntime().availableProcessors();
        ExecutorService service = Executors.newFixedThreadPool(amountOfCores);
        List<Future<?>> tasks = new ArrayList<>(amountOfCores);

        this.status.setProgressBarStep(this.lightmapperQuads.length);
        for (int i = 0; i < this.lightmapperQuads.length; i += amountOfCores) {
            setStatusText("Denoising (" + i + "/" + this.lightmapperQuads.length + ")");

            for (int j = 0; j < amountOfCores; j++) {
                int quad = i + j;
                if (quad >= this.lightmapperQuads.length) {
                    break;
                }
                tasks.add(service.submit(() -> {
                    denoiseQuad(indirectOutput, reversedShadowOutput, quad);
                }));
            }
            
            for (Future<?> f : tasks) {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
                this.status.stepProgressBar();
            }
            
            tasks.clear();
        }

        this.indirectColorBuffer = indirectOutput;
        this.reverseShadowBuffer = reversedShadowOutput;
    }

    private void denoiseQuad(ColorBuffer indirectOutput, GrayBuffer reversedShadowOutput, int i) {
        int numSamples = this.scene.getSamplingMode().numSamples();

        Vector3f color = new Vector3f();

        LightmapUVs.LightmapperQuad quad = this.lightmapperQuads[i];
        
        int minX = clamp(quad.getX(), 0, this.geometryLightmapSize);
        int minY = clamp(quad.getY(), 0, this.geometryLightmapSize);
        int maxX = clamp(quad.getX() + quad.getWidth(), 0, this.geometryLightmapSize);
        int maxY = clamp(quad.getY() + quad.getHeight(), 0, this.geometryLightmapSize);
        
        final int width = maxX - minX;
        final int height = maxY - minY;
        final int xOffset = minX;
        final int yOffset = minY;
        final boolean[] sampleMap = new boolean[width * height * numSamples];
        final boolean[] boundsMap = new boolean[width * height];
        final boolean[] ignoreMap = new boolean[width * height];
        final float[] colorMap = new float[width * height * 3];
        final float[] reversedShadowMap = new float[width * height];

        if (width <= 0 || height <= 0) {
            return;
        }

        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                float r = 0f;
                float g = 0f;
                float b = 0f;
                float reversedShadow = 0f;
                int sampleCount = 0;
                for (int s = 0; s < numSamples; s++) {
                    if (!this.sampleBuffer.read(x, y, s)) {
                        continue;
                    }

                    this.indirectColorBuffer.read(color, x, y, s);
                    r += color.x();
                    g += color.y();
                    b += color.z();
                    reversedShadow += this.reverseShadowBuffer.read(x, y, s);
                    sampleCount++;

                    sampleMap[s + ((x - xOffset) * numSamples) + ((y - yOffset) * width * numSamples)] = true;
                }
                if (sampleCount != 0) {
                    float invSampleCount = 1f / sampleCount;
                    r *= invSampleCount;
                    g *= invSampleCount;
                    b *= invSampleCount;
                    reversedShadow *= invSampleCount;

                    int localX = x - xOffset;
                    int localY = y - yOffset;

                    colorMap[(localX * 3) + (localY * width * 3) + 0] = r;
                    colorMap[(localX * 3) + (localY * width * 3) + 1] = g;
                    colorMap[(localX * 3) + (localY * width * 3) + 2] = b;
                    reversedShadowMap[localX + (localY * width)] = reversedShadow;

                    boundsMap[localX + (localY * width)] = true;

                    if (reversedShadow >= 0.9999994f) {
                        ignoreMap[localX + (localY * width)] = true;
                    }
                }
            }
        }

        GaussianBlur.GaussianIO indirectIO = new GaussianBlur.GaussianIO() {
            private final Vector3f ioColor = new Vector3f();

            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }

            @Override
            public boolean outOfBounds(int x, int y) {
                if (x < 0 || y < 0 || x >= width || y >= height) {
                    return true;
                }
                return !boundsMap[x + (y * width)];
            }

            @Override
            public void write(int x, int y, GaussianBlur.GaussianColor color) {
                this.ioColor.set(color.r, color.g, color.b);
                for (int s = 0; s < numSamples; s++) {
                    if (sampleMap[s + (x * numSamples) + (y * width * numSamples)]) {
                        indirectOutput.write(this.ioColor, x + xOffset, y + yOffset, s);
                    }
                }
            }

            @Override
            public void read(int x, int y, GaussianBlur.GaussianColor color) {
                color.r = colorMap[(x * 3) + (y * width * 3) + 0];
                color.g = colorMap[(x * 3) + (y * width * 3) + 1];
                color.b = colorMap[(x * 3) + (y * width * 3) + 2];
            }
        };

        GaussianBlur.blur(
                indirectIO,
                41,
                this.scene.getIndirectLightingBlurArea()
        );

        GaussianBlur.GaussianIO reversedShadowIO = new GaussianBlur.GaussianIO() {
            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }

            @Override
            public boolean outOfBounds(int x, int y) {
                if (x < 0 || y < 0 || x >= width || y >= height) {
                    return true;
                }
                return !boundsMap[x + (y * width)];
            }

            @Override
            public boolean ignore(int x, int y) {
                return ignoreMap[x + (y * width)];
            }

            @Override
            public void write(int x, int y, GaussianBlur.GaussianColor color) {
                float shadow = (color.r + color.g + color.b) / 3f;
                for (int s = 0; s < numSamples; s++) {
                    if (sampleMap[s + (x * numSamples) + (y * width * numSamples)]) {
                        reversedShadowOutput.write(shadow, x + xOffset, y + yOffset, s);
                    }
                }
            }

            @Override
            public void read(int x, int y, GaussianBlur.GaussianColor color) {
                float shadow = reversedShadowMap[x + (y * width)];
                color.r = shadow;
                color.g = shadow;
                color.b = shadow;
            }
        };

        GaussianBlur.blur(
                reversedShadowIO,
                41,
                this.scene.getShadowBlurArea()
        );
    }

    private void combineBuffers() {
        int numSamples = this.scene.getSamplingMode().numSamples();

        Vector3f currentColor = new Vector3f();

        Vector3f direct = new Vector3f();
        Vector3f indirect = new Vector3f();
        Vector3f sampleAverage = new Vector3f();

        this.status.setProgressBarStep(this.geometryLightmapSize);
        for (int y = 0; y < this.geometryLightmapSize; y++) {
            setStatusText("Combining Buffers (" + y + "/" + this.geometryLightmapSize + ")");
            
            for (int x = 0; x < this.geometryLightmapSize; x++) {
                int processedSamples = 0;
                for (int s = 0; s < numSamples; s++) {
                    if (this.sampleBuffer.read(x, y, s)) {
                        processedSamples++;
                    }
                }

                direct.zero();
                indirect.zero();

                if (processedSamples != 0) {
                    float invProcessedSamples = 1f / processedSamples;

                    //direct
                    sampleAverage.zero();
                    for (int s = 0; s < numSamples; s++) {
                        this.directColorBuffer.read(direct, x, y, s);
                        sampleAverage.add(direct);
                    }
                    direct.set(sampleAverage.mul(invProcessedSamples));

                    //shadow
                    float reversedShadowAverage = 0f;
                    for (int s = 0; s < numSamples; s++) {
                        reversedShadowAverage += this.reverseShadowBuffer.read(x, y, s);
                    }
                    reversedShadowAverage *= invProcessedSamples;

                    direct.mul(reversedShadowAverage);

                    //indirect
                    sampleAverage.zero();
                    for (int s = 0; s < numSamples; s++) {
                        this.indirectColorBuffer.read(indirect, x, y, s);
                        sampleAverage.add(indirect);
                    }
                    indirect.set(sampleAverage.mul(invProcessedSamples));

                    direct.add(indirect);
                }
                
                this.resultBuffer.read(currentColor, x, y, 0);
                currentColor.add(direct);
                this.resultBuffer.write(currentColor, x, y, 0);
            }
            this.status.stepProgressBar();
        }

    }

    private void generateMargins() {
        
    }
    
    private void output() {
        byte[] lineColorData = new byte[this.geometryLightmapSize * 4];

        Vector3f color = new Vector3f();

        this.status.setProgressBarStep(this.geometryLightmapSize);
        for (int y = 0; y < this.geometryLightmapSize; y++) {
            setStatusText("Writing to Output Buffer (" + y + "/" + this.geometryLightmapSize + ")");
            for (int x = 0; x < this.geometryLightmapSize; x++) {
                this.resultBuffer.read(color, x, y, 0);

                int rColor = Math.min((int) (color.x() * 255f), 255);
                int gColor = Math.min((int) (color.y() * 255f), 255);
                int bColor = Math.min((int) (color.z() * 255f), 255);
                rColor = Math.max(rColor, 0);
                gColor = Math.max(gColor, 0);
                bColor = Math.max(bColor, 0);

                lineColorData[(x * 4) + 0] = (byte) rColor;
                lineColorData[(x * 4) + 1] = (byte) gColor;
                lineColorData[(x * 4) + 2] = (byte) bColor;
                lineColorData[(x * 4) + 3] = (byte) 0xFF;
            }
            this.outputBuffer.put(lineColorData);
            
            this.status.stepProgressBar();
        }
        this.outputBuffer.rewind();
    }

    private void createLightmapTexture() {
        setStatusText("Creating Texture (" + this.geometryLightmapSize + "x" + this.geometryLightmapSize + ")");
        this.status.currentProgress = 0f;
        
        final ByteBuffer outputBufferCopy = this.outputBuffer;
        final Cleaner.Cleanable outputBufferCleanableCopy = this.outputBufferCleanable;
        final int lightmapSizeCopy = this.geometryLightmapSize;
        final Geometry geometryCopy = this.geometry;
        final MeshData.LightmapMesh lightmapMeshCopy = this.lightmap;
        
        Main.MAIN_TASKS.add(() -> {
            int texture = glGenTextures();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture);
            
            this.status.memoryUsage += lightmapSizeCopy * lightmapSizeCopy * 3;
            glTexImage2D(
                    GL_TEXTURE_2D, 0,
                    GL_RGB8, lightmapSizeCopy, lightmapSizeCopy,
                    0,
                    GL_RGBA, GL_UNSIGNED_BYTE, outputBufferCopy
            );
            
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            glBindTexture(GL_TEXTURE_2D, 0);

            geometryCopy.setLightmapTextureHint(texture);
            geometryCopy.setLightmapMesh(lightmapMeshCopy);
            
            outputBufferCleanableCopy.clean();
        });

        this.status.currentProgress = 100f;
    }

    public void bake() {
        loadTextures();
        scheduleLightmaps();
        waitForLightmaps();
        waitForBVHs();
        for (int i = 0; i < this.geometries.size(); i++) {
            loadGeometry(i);
            computeBuffers();
            bakeLightmap();
            denoiseBuffers();
            combineBuffers();
            generateMargins();
            output();
            createLightmapTexture();
        }
    }

}
