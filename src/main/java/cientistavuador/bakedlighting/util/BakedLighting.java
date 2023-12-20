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
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.joml.Matrix3f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.lwjgl.opengl.EXTTextureCompressionS3TC;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.system.MemoryUtil;

/**
 *
 * @author Cien
 */
public class BakedLighting {

    public static class Scene {

        private final List<Geometry> geometries = new ArrayList<>();

        private final Vector3f sunDirection = new Vector3f(-1f, -0.75f, 0.5f).normalize();
        private final Vector3f sunDirectionInverted = new Vector3f(this.sunDirection).negate();
        private final Vector3f sunDiffuseColor = new Vector3f(1.5f, 1.5f, 1.5f);
        private final Vector3f sunAmbientColor = new Vector3f(0.2f, 0.2f, 0.2f);

        public Scene() {

        }

        public List<Geometry> getGeometries() {
            return geometries;
        }

        public Vector3fc getSunDirection() {
            return sunDirection;
        }

        public Vector3fc getSunDirectionInverted() {
            return sunDirectionInverted;
        }

        public Vector3fc getSunDiffuseColor() {
            return sunDiffuseColor;
        }

        public Vector3fc getSunAmbientColor() {
            return sunAmbientColor;
        }

        public void setSunDirection(float x, float y, float z) {
            this.sunDirection.set(x, y, z).normalize();
            this.sunDirectionInverted.set(this.sunDirection).negate();
        }

        public void setSunDiffuseColor(float r, float g, float b) {
            this.sunDiffuseColor.set(r, g, b);
        }

        public void setSunAmbientColor(float r, float g, float b) {
            this.sunAmbientColor.set(r, g, b);
        }

        public void setSunDirection(Vector3fc direction) {
            setSunDirection(direction.x(), direction.y(), direction.z());
        }

        public void setSunDiffuseColor(Vector3fc color) {
            setSunDiffuseColor(color.x(), color.y(), color.z());
        }

        public void setSunAmbientColor(Vector3fc color) {
            setSunAmbientColor(color.x(), color.y(), color.z());
        }
    }

    public static class Status {

        private Future<Void> task;

        private String currentStatus = "Idle";
        private float currentProgress = 0f;
        private boolean error = false;

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

    public static Status bake(Scene scene, int lightmapSize) {
        Status status = new Status();
        BakedLighting baked = new BakedLighting(scene, lightmapSize, status);
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

    public static final int INDIRECT_BOUNCES = 4;
    public static final int INDIRECT_RAYS_PER_SAMPLE = 4;

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

    private static class ColorBuffer {

        private final int size;
        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final byte[] data;

        public ColorBuffer(int size) {
            this.sampleSize = 3;
            this.vectorSize = this.sampleSize * SAMPLES;
            this.lineSize = size * this.vectorSize;
            this.data = new byte[size * this.lineSize];
            this.size = size;
        }

        public void write(Vector3f color, int x, int y, int sample) {
            int vx = Math.min((int) (color.x() * 255f), 255);
            int vy = Math.min((int) (color.y() * 255f), 255);
            int vz = Math.min((int) (color.z() * 255f), 255);
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = (byte) vx;
            this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = (byte) vy;
            this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = (byte) vz;
        }

        public void read(Vector3f color, int x, int y, int sample) {
            if (x >= this.size) {
                x = this.size - 1;
            } else if (x < 0) {
                x = 0;
            }
            if (y >= this.size) {
                y = this.size - 1;
            } else if (y < 0) {
                y = 0;
            }

            byte vx = this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
            byte vy = this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
            byte vz = this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
            int vxu = ((int) vx) & 0xFF;
            int vyu = ((int) vy) & 0xFF;
            int vzu = ((int) vz) & 0xFF;
            float inv = 1f / 255f;
            color.set(
                    vxu * inv,
                    vyu * inv,
                    vzu * inv
            );
        }
    }

    private final Scene scene;
    private final List<Geometry> geometries;
    private final int lightmapSize;
    private final Status status;

    private final Map<MeshData, SoftwareTexture> sceneTextures = new HashMap<>();

    private float[] areas = null;
    private float largestArea = 0f;
    private float smallestArea = 0f;
    private int[] lightmapSizes = null;

    private int geometryIndex = 0;
    private Geometry geometry = null;
    private int geometryLightmapSize;
    private float[] vertices = null;
    private int[] indices = null;
    private float[] lightmapVertices = null;

    private BooleanBuffer sampleBuffer = null;
    private IndicesBuffer indicesBuffer = null;
    private PositionBuffer positionBuffer = null;
    private UnitVectorBuffer normalBuffer = null;
    private UnitVectorBuffer tangentBuffer = null;
    private ColorBuffer indirectColorBuffer = null;
    private ColorBuffer directColorBuffer = null;
    private ByteBuffer outputBuffer = null;
    private Cleaner.Cleanable outputBufferCleanable = null;

    private BakedLighting(Scene scene, int lightmapSize, Status status) {
        this.scene = scene;
        this.geometries = scene.getGeometries();
        this.lightmapSize = lightmapSize;
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

            this.status.stepProgressBar();
        }
    }

    private void calculateAreas() {
        this.status.setProgressBarStep(this.geometries.size());

        Vector3f p0 = new Vector3f();
        Vector3f p1 = new Vector3f();
        Vector3f p2 = new Vector3f();

        this.areas = new float[this.geometries.size()];
        for (int i = 0; i < this.geometries.size(); i++) {
            Geometry geo = this.scene.getGeometries().get(i);

            setStatusText("Calculating Area (" + geo.getMesh().getName() + ")");

            Matrix4fc model = geo.getModel();

            float[] verts = geo.getMesh().getVertices();
            int[] indis = geo.getMesh().getIndices();

            float area = 0f;
            for (int j = 0; j < indis.length; j += 3) {
                int v0 = (indis[j + 0] * MeshData.SIZE) + MeshData.XYZ_OFFSET;
                int v1 = (indis[j + 1] * MeshData.SIZE) + MeshData.XYZ_OFFSET;
                int v2 = (indis[j + 2] * MeshData.SIZE) + MeshData.XYZ_OFFSET;

                float v0x = verts[v0 + 0];
                float v0y = verts[v0 + 1];
                float v0z = verts[v0 + 2];

                float v1x = verts[v1 + 0];
                float v1y = verts[v1 + 1];
                float v1z = verts[v1 + 2];

                float v2x = verts[v2 + 0];
                float v2y = verts[v2 + 1];
                float v2z = verts[v2 + 2];

                p0.set(v0x, v0y, v0z);
                p1.set(v1x, v1y, v1z);
                p2.set(v2x, v2y, v2z);

                model.transformProject(p0);
                model.transformProject(p1);
                model.transformProject(p2);

                float a = (float) Math.sqrt(Math.pow(p0.x() - p1.x(), 2.0) + Math.pow(p0.y() - p1.y(), 2.0) + Math.pow(p0.z() - p1.z(), 2.0));
                float b = (float) Math.sqrt(Math.pow(p1.x() - p2.x(), 2.0) + Math.pow(p1.y() - p2.y(), 2.0) + Math.pow(p1.z() - p2.z(), 2.0));
                float c = (float) Math.sqrt(Math.pow(p2.x() - p0.x(), 2.0) + Math.pow(p2.y() - p0.y(), 2.0) + Math.pow(p2.z() - p0.z(), 2.0));

                float sp = (a + b + c) * 0.5f;
                area += (float) Math.sqrt(sp * (sp - a) * (sp - b) * (sp - c));
            }

            this.areas[i] = area;
            this.largestArea = Math.max(this.largestArea, area);
            this.smallestArea = Math.min(this.smallestArea, area);

            this.status.stepProgressBar();
        }
    }

    private void scheduleLightmaps() {
        this.status.setProgressBarStep(this.geometries.size());

        this.lightmapSizes = new int[this.geometries.size()];
        for (int i = 0; i < this.lightmapSizes.length; i++) {
            Geometry geo = this.geometries.get(i);

            setStatusText("(" + i + "/" + this.geometries.size() + ") Scheduling Lightmap UV (" + geo.getMesh().getName() + ")");

            //todo: lightmap size calculation
            int geoLightmapSize = this.lightmapSize;

            geo.getMesh().scheduleLightmapMesh(geoLightmapSize);

            this.lightmapSizes[i] = geoLightmapSize;
            this.status.stepProgressBar();
        }
    }

    private void waitForLightmaps() {
        this.status.setProgressBarStep(this.geometries.size());

        for (int i = 0; i < this.lightmapSizes.length; i++) {
            Geometry geo = this.geometries.get(i);
            int size = this.lightmapSizes[i];

            setStatusText("(" + i + "/" + this.geometries.size() + ") Waiting for Lightmap UV (" + geo.getMesh().getName() + ") [" + size + "x" + size + "]");

            geo.getMesh().getLightmapMesh(size).getLightmapUVsBake();

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

        this.geometryLightmapSize = this.lightmapSizes[index];

        this.vertices = this.geometry.getMesh().getVertices();
        this.indices = this.geometry.getMesh().getIndices();
        this.lightmapVertices = this.geometry
                .getMesh()
                .getLightmapMesh(this.geometryLightmapSize)
                .getLightmapUVsBake();

        this.sampleBuffer = new BooleanBuffer(this.geometryLightmapSize);
        this.indicesBuffer = new IndicesBuffer(this.geometryLightmapSize);
        this.positionBuffer = new PositionBuffer(this.geometryLightmapSize);
        this.normalBuffer = new UnitVectorBuffer(this.geometryLightmapSize);
        this.tangentBuffer = new UnitVectorBuffer(this.geometryLightmapSize);
        this.indirectColorBuffer = new ColorBuffer(this.geometryLightmapSize);
        this.directColorBuffer = new ColorBuffer(this.geometryLightmapSize);
        final ByteBuffer output = MemoryUtil.memCalloc(this.geometryLightmapSize * this.geometryLightmapSize * 3);
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
        this.status.setProgressBarStep(this.indices.length);

        Vector3f weights = new Vector3f();

        Vector3f pixelPos = new Vector3f();

        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        Vector3f tangent = new Vector3f();

        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();

        Vector3i indicesVector = new Vector3i();

        for (int i = 0; i < this.indices.length; i += 3) {
            setStatusText("Computing Deferred Buffers (" + i + "/" + this.indices.length + ")");

            int i0 = this.indices[i + 0];
            int i1 = this.indices[i + 1];
            int i2 = this.indices[i + 2];

            indicesVector.set(i0, i1, i2);

            float v0x = this.lightmapVertices[(i0 * 2) + 0] * this.geometryLightmapSize;
            float v0y = this.lightmapVertices[(i0 * 2) + 1] * this.geometryLightmapSize;

            float v1x = this.lightmapVertices[(i1 * 2) + 0] * this.geometryLightmapSize;
            float v1y = this.lightmapVertices[(i1 * 2) + 1] * this.geometryLightmapSize;

            float v2x = this.lightmapVertices[(i2 * 2) + 0] * this.geometryLightmapSize;
            float v2y = this.lightmapVertices[(i2 * 2) + 1] * this.geometryLightmapSize;

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
                this.status.stepProgressBar();
            }
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

    private void processLine(int y) {

        Vector3i triangle = new Vector3i();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        Vector3f tangent = new Vector3f();

        Vector3f outColor = new Vector3f();
        Vector3f outIndirectColor = new Vector3f();

        Vector3f colorAverage = new Vector3f();

        int lastValidX = -1;
        for (int x = 0; x < this.geometryLightmapSize; x++) {
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
                normal.normalize();
                this.tangentBuffer.read(tangent, x, y, s);
                tangent.normalize();

                outColor.zero();
                outIndirectColor.zero();
                processSample(x, y, s, triangle, position, normal, tangent, outColor, outIndirectColor);

                this.indirectColorBuffer.write(outIndirectColor, x, y, s);
                this.directColorBuffer.write(outColor, x, y, s);

                colorAverage.add(outColor);
                pixelsProcessed++;
            }
        }
    }

    private void processSample(
            int pixelX,
            int pixelY,
            int sample,
            Vector3i triangle,
            Vector3fc position,
            Vector3fc normal,
            Vector3fc tangent,
            Vector3f outColor,
            Vector3f outIndirectColor
    ) {
        Matrix3f TBN = new Matrix3f(tangent, normal.cross(tangent, new Vector3f()), normal);

        Vector3f finalIndirect = new Vector3f();
        for (int j = 0; j < INDIRECT_RAYS_PER_SAMPLE; j++) {
            Vector3f indirectLight = new Vector3f();
            List<Vector3f> bounceColor = new ArrayList<>();
            Vector3f bounceDir = TBN.transform(new Vector3f().set((Math.random() - 0.5f) * 2.0, (Math.random() - 0.5f) * 2.0, 1f).normalize());
            Vector3f bouncePos = new Vector3f(position);

            Vector3f hitWeights = new Vector3f();
            Vector3f hitNormal = new Vector3f();
            float[] colorOutput = new float[4];

            Vector3i hitTriangle = new Vector3i(triangle);
            Geometry hitGeometry = this.geometry;

            for (int i = 0; i < INDIRECT_BOUNCES; i++) {
                if (i != 0) {
                    boolean hitSun = true;
                    RayResult[] results = Geometry.testRay(bouncePos, this.scene.getSunDirectionInverted(), this.scene.getGeometries());
                    for (RayResult r : results) {
                        if (r.getDistance() < 0.001f) {
                            continue;
                        }
                        if (r.getGeometry() == hitGeometry && r.i0() == hitTriangle.x() && r.i1() == hitTriangle.y() && r.i2() == hitTriangle.z()) {
                            continue;
                        }
                        hitSun = false;
                        break;
                    }
                    this.status.rays++;
                    if (hitSun) {
                        indirectLight.set(this.scene.getSunDiffuseColor());
                        break;
                    }
                }
                RayResult hitPos = null;
                RayResult[] results = Geometry.testRay(bouncePos, bounceDir, this.scene.getGeometries());
                for (RayResult r : results) {
                    if (r.getDistance() < 0.001f) {
                        continue;
                    }
                    if (r.getGeometry() == hitGeometry && r.i0() == hitTriangle.x() && r.i1() == hitTriangle.y() && r.i2() == hitTriangle.z()) {
                        continue;
                    }
                    hitPos = r;
                    break;
                }
                this.status.rays++;
                if (hitPos == null) {
                    indirectLight.set(this.scene.getSunAmbientColor());
                    break;
                }
                hitGeometry = hitPos.getGeometry();
                hitTriangle.set(hitPos.i0(), hitPos.i1(), hitPos.i2());

                hitPos.weights(hitWeights);

                float normalX = hitPos.lerp(hitWeights, MeshData.N_XYZ_OFFSET + 0);
                float normalY = hitPos.lerp(hitWeights, MeshData.N_XYZ_OFFSET + 1);
                float normalZ = hitPos.lerp(hitWeights, MeshData.N_XYZ_OFFSET + 2);
                hitNormal.set(normalX, normalY, normalZ).normalize();
                hitPos.getGeometry().getNormalModel().transform(hitNormal);

                bounceDir.reflect(hitNormal);

                float u = hitPos.lerp(hitWeights, MeshData.UV_OFFSET + 0);
                float v = hitPos.lerp(hitWeights, MeshData.UV_OFFSET + 1);

                this.sceneTextures.get(hitPos.getGeometry().getMesh()).sampleBilinear(u, v, colorOutput, 0);

                bounceColor.add(new Vector3f(colorOutput));

                bouncePos.set(hitPos.getHitpoint());
            }
            for (Vector3f bounce : bounceColor) {
                indirectLight.mul(bounce);
            }
            finalIndirect.add(indirectLight);
        }
        finalIndirect.div(INDIRECT_RAYS_PER_SAMPLE);
        outIndirectColor.set(finalIndirect);

        float shadow = 1f;
        RayResult[] results = Geometry.testRay(position, this.scene.getSunDirectionInverted(), this.scene.getGeometries());
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
        this.status.rays++;

        outColor
                .set(this.scene.getSunDiffuseColor())
                .mul(Math.max(normal.dot(this.scene.getSunDirectionInverted()), 0f))
                .mul(shadow);
    }

    private void denoiseIndirectBuffer() {
        //todo
    }

    private void sumColorBuffersAndOutput() {
        byte[] lineColorData = new byte[this.geometryLightmapSize * 3];

        Vector3f direct = new Vector3f();
        Vector3f indirect = new Vector3f();
        Vector3f sampleAverage = new Vector3f();

        this.status.setProgressBarStep(this.geometryLightmapSize);
        for (int y = 0; y < this.geometryLightmapSize; y++) {
            setStatusText("Writing to Output Buffer ("+y+"/"+this.geometryLightmapSize+")");
            
            for (int x = 0; x < this.geometryLightmapSize; x++) {
                int processedPixels = 0;
                for (int s = 0; s < SAMPLES; s++) {
                    if (this.sampleBuffer.read(x, y, s)) {
                        processedPixels++;
                    }
                }

                direct.zero();
                indirect.zero();

                if (processedPixels != 0) {
                    //direct
                    sampleAverage.zero();
                    for (int s = 0; s < SAMPLES; s++) {
                        this.directColorBuffer.read(direct, x, y, s);
                        sampleAverage.add(direct);
                    }
                    direct.set(sampleAverage.div(processedPixels));

                    //indirect
                    sampleAverage.zero();
                    for (int s = 0; s < SAMPLES; s++) {
                        this.indirectColorBuffer.read(indirect, x, y, s);
                        sampleAverage.add(indirect);
                    }
                    indirect.set(sampleAverage.div(processedPixels));
                }

                direct.add(indirect);
                direct.set(indirect);

                int rColor = Math.min((int) (direct.x() * 255f), 255);
                int gColor = Math.min((int) (direct.y() * 255f), 255);
                int bColor = Math.min((int) (direct.z() * 255f), 255);

                lineColorData[(x * 3) + 0] = (byte) rColor;
                lineColorData[(x * 3) + 1] = (byte) gColor;
                lineColorData[(x * 3) + 2] = (byte) bColor;
            }
            this.outputBuffer.put(y * this.geometryLightmapSize * 3, lineColorData);
            
            this.status.stepProgressBar();
        }

    }

    private void createLightmapTexture() {
        setStatusText("Creating Texture (" + this.geometryLightmapSize + "x" + this.geometryLightmapSize + ")");
        this.status.currentProgress = 0f;

        final ByteBuffer outputBufferCopy = this.outputBuffer;
        final Cleaner.Cleanable outputBufferCleanableCopy = this.outputBufferCleanable;
        final int lightmapSizeCopy = this.geometryLightmapSize;
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
            geometryCopy.setLightmapTextureSizeHint(lightmapSizeCopy);

            outputBufferCleanableCopy.clean();
        });

        this.status.currentProgress = 100f;
    }

    public void bake() {
        loadTextures();
        calculateAreas();
        scheduleLightmaps();
        waitForLightmaps();
        waitForBVHs();
        for (int i = 0; i < this.geometries.size(); i++) {
            loadGeometry(i);
            computeBuffers();
            bakeLightmap();
            denoiseIndirectBuffer();
            sumColorBuffersAndOutput();
            createLightmapTexture();
        }
    }

}
