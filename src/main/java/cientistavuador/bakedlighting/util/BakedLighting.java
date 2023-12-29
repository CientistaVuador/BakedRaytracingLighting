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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector3ic;
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

        private final Vector3f sunDirection = new Vector3f(0.5f, -1f, 1f).normalize();
        private final Vector3f sunDirectionInverted = new Vector3f(this.sunDirection).negate();
        private final Vector3f sunDiffuseColor = new Vector3f(1.5f, 1.5f, 1.5f);
        private final Vector3f sunAmbientColor = new Vector3f(0.4f, 0.4f, 0.45f);
        
        private boolean directLightingEnabled = true;
        private boolean shadowsEnabled = true;
        private boolean indirectLightingEnabled = true;
        
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

        public boolean isDirectLightingEnabled() {
            return directLightingEnabled;
        }

        public boolean isShadowsEnabled() {
            return shadowsEnabled;
        }

        public boolean isIndirectLightingEnabled() {
            return indirectLightingEnabled;
        }

        public void setDirectLightingEnabled(boolean directLightingEnabled) {
            this.directLightingEnabled = directLightingEnabled;
        }

        public void setShadowsEnabled(boolean shadowsEnabled) {
            this.shadowsEnabled = shadowsEnabled;
        }

        public void setIndirectLightingEnabled(boolean indirectLightingEnabled) {
            this.indirectLightingEnabled = indirectLightingEnabled;
        }
        
    }

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

    public static final float RAY_OFFSET = 0.001f;
    public static final int INDIRECT_BOUNCES = 4;
    public static final int INDIRECT_RAYS_PER_SAMPLE = 8;
    public static final int DIRECT_SHADOW_RAYS_PER_SAMPLE = 12;
    public static final float DIRECT_SUN_SIZE = 0.03f;

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

        public PositionBuffer(int size, int samples) {
            this.sampleSize = 3;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new float[size * this.lineSize];
        }

        public PositionBuffer(int size) {
            this(size, SAMPLES);
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

        public IndicesBuffer(int size, int samples) {
            this.sampleSize = 3;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new int[size * this.lineSize];
        }

        public IndicesBuffer(int size) {
            this(size, SAMPLES);
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

        public BooleanBuffer(int size, int samples) {
            this.sampleSize = 1;
            this.vectorSize = this.sampleSize * SAMPLES;
            this.lineSize = size * this.vectorSize;
            this.data = new boolean[size * this.lineSize];
        }

        public BooleanBuffer(int size) {
            this(size, SAMPLES);
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

        public UnitVectorBuffer(int size, int samples) {
            this.sampleSize = 3;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new byte[size * this.lineSize];
        }

        public UnitVectorBuffer(int size) {
            this(size, SAMPLES);
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

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final byte[] data;

        public ColorBuffer(int size, int samples) {
            this.sampleSize = 3;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new byte[size * this.lineSize];
        }

        public ColorBuffer(int size) {
            this(size, SAMPLES);
        }

        public void write(Vector3f color, int x, int y, int sample) {
            int vx = (int) (color.x() * 255f);
            int vy = (int) (color.y() * 255f);
            int vz = (int) (color.z() * 255f);
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = (byte) vx;
            this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = (byte) vy;
            this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = (byte) vz;
        }

        public void read(Vector3f color, int x, int y, int sample) {
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

    private static class GrayBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final byte[] data;

        public GrayBuffer(int size) {
            this.sampleSize = 1;
            this.vectorSize = this.sampleSize * SAMPLES;
            this.lineSize = size * this.vectorSize;
            this.data = new byte[size * this.lineSize];
        }

        public void write(float value, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = (byte) ((int) (value * 255f));
        }

        public float read(int x, int y, int sample) {
            int gray = ((int) this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)]) & 0xFF;
            return gray / 255f;
        }
    }

    private final Scene scene;
    private final List<Geometry> geometries;
    private final int lightmapSize;
    private final Status status;

    private final Map<Integer, SoftwareTexture> sceneTextures = new HashMap<>();

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
    private int[] denoiserQuads = null;

    private BooleanBuffer sampleBuffer = null;

    private IndicesBuffer indicesBuffer = null;
    private PositionBuffer positionBuffer = null;
    private UnitVectorBuffer normalBuffer = null;
    private UnitVectorBuffer tangentBuffer = null;

    private ColorBuffer indirectColorBuffer = null;
    private ColorBuffer directColorBuffer = null;
    private GrayBuffer reverseShadowBuffer = null;

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
        this.denoiserQuads = this.geometry
                .getMesh()
                .getLightmapMesh(this.geometryLightmapSize)
                .getDenoiserQuads();

        this.sampleBuffer = new BooleanBuffer(this.geometryLightmapSize);
        this.indicesBuffer = new IndicesBuffer(this.geometryLightmapSize);
        this.positionBuffer = new PositionBuffer(this.geometryLightmapSize);
        this.normalBuffer = new UnitVectorBuffer(this.geometryLightmapSize);
        this.tangentBuffer = new UnitVectorBuffer(this.geometryLightmapSize);
        this.indirectColorBuffer = new ColorBuffer(this.geometryLightmapSize);
        this.directColorBuffer = new ColorBuffer(this.geometryLightmapSize);
        this.reverseShadowBuffer = new GrayBuffer(this.geometryLightmapSize);
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

            float v0x = this.lightmapVertices[(i0 * 2) + 0];
            float v0y = this.lightmapVertices[(i0 * 2) + 1];

            float v1x = this.lightmapVertices[(i1 * 2) + 0];
            float v1y = this.lightmapVertices[(i1 * 2) + 1];

            float v2x = this.lightmapVertices[(i2 * 2) + 0];
            float v2y = this.lightmapVertices[(i2 * 2) + 1];

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
        Matrix3f TBN = new Matrix3f();

        Vector3f outColor = new Vector3f();
        Vector3f outIndirectColor = new Vector3f();
        float[] outReversedShadow = {0f};

        for (int x = 0; x < this.geometryLightmapSize; x++) {
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

                TBN.set(tangent, normal.cross(tangent, new Vector3f()), normal);

                outColor.zero();
                outIndirectColor.zero();
                outReversedShadow[0] = 1f;

                processSample(
                        x,
                        y,
                        s,
                        triangle,
                        position,
                        normal,
                        tangent,
                        TBN,
                        outColor,
                        outIndirectColor,
                        outReversedShadow
                );

                outColor.set(
                        Math.min(Math.max(outColor.x(), 0f), 1f),
                        Math.min(Math.max(outColor.y(), 0f), 1f),
                        Math.min(Math.max(outColor.z(), 0f), 1f)
                );
                outIndirectColor.set(
                        Math.min(Math.max(outIndirectColor.x(), 0f), 1f),
                        Math.min(Math.max(outIndirectColor.y(), 0f), 1f),
                        Math.min(Math.max(outIndirectColor.z(), 0f), 1f)
                );
                outReversedShadow[0] = Math.min(Math.max(outReversedShadow[0], 0f), 1f);

                this.indirectColorBuffer.write(outIndirectColor, x, y, s);
                this.directColorBuffer.write(outColor, x, y, s);
                this.reverseShadowBuffer.write(outReversedShadow[0], x, y, s);
            }
        }
    }

    private void randomWeights(int pixelX, int pixelY, int sample, Vector3ic triangle, Vector3f outWeights) {
        int i0 = triangle.x();
        int i1 = triangle.y();
        int i2 = triangle.z();

        float sampleX = SAMPLE_POSITIONS[(sample * 2) + 0];
        float sampleY = SAMPLE_POSITIONS[(sample * 2) + 1];

        float pX = pixelX + sampleX;
        float pY = pixelY + sampleY;

        float v0x = this.lightmapVertices[(i0 * 2) + 0];
        float v0y = this.lightmapVertices[(i0 * 2) + 1];

        float v1x = this.lightmapVertices[(i1 * 2) + 0];
        float v1y = this.lightmapVertices[(i1 * 2) + 1];

        float v2x = this.lightmapVertices[(i2 * 2) + 0];
        float v2y = this.lightmapVertices[(i2 * 2) + 1];

        do {
            RasterUtils.barycentricWeights(
                    (float) (pX + (Math.random() - 0.5)), (float) (pY + (Math.random() - 0.5)), 0f,
                    v0x, v0y, 0f,
                    v1x, v1y, 0f,
                    v2x, v2y, 0f,
                    outWeights
            );
        } while (outWeights.x() < 0 || outWeights.y() < 0 || outWeights.z() < 0);
    }

    private void randomSunDirection(Vector3f outDirection) {
        outDirection.set(
                (Math.random() * 2.0) - 1.0,
                (Math.random() * 2.0) - 1.0,
                (Math.random() * 2.0) - 1.0
        )
                .normalize()
                .mul(DIRECT_SUN_SIZE)
                .add(this.scene.getSunDirectionInverted())
                .normalize();
    }

    private void processSample(
            int pixelX,
            int pixelY,
            int sample,
            Vector3i triangle,
            Vector3fc position,
            Vector3fc normal,
            Vector3fc tangent,
            Matrix3fc TBN,
            Vector3f outColor,
            Vector3f outIndirectColor,
            float[] outReversedShadow
    ) {
        if (this.scene.isDirectLightingEnabled()) {
            processDirect(pixelX, pixelY, sample, triangle, position, normal, tangent, TBN, outColor);
        }
        if (this.scene.isShadowsEnabled()) {
            processShadow(pixelX, pixelY, sample, triangle, position, normal, tangent, TBN, outReversedShadow);
        }
        if (this.scene.isIndirectLightingEnabled()) {
            processIndirect(pixelX, pixelY, sample, triangle, position, normal, tangent, TBN, outIndirectColor);
        }
    }

    private void processIndirect(
            int pixelX,
            int pixelY,
            int sample,
            Vector3i triangle,
            Vector3fc position,
            Vector3fc normal,
            Vector3fc tangent,
            Matrix3fc TBN,
            Vector3f outColor
    ) {
        Vector3f randomDirection = new Vector3f();
        Vector3f finalIndirect = new Vector3f();
        for (int j = 0; j < INDIRECT_RAYS_PER_SAMPLE; j++) {
            Vector3f indirectLight = new Vector3f();
            List<Vector3f> bounceColors = new ArrayList<>();
            Vector3f bounceDir = TBN.transform(new Vector3f().set(
                    (Math.random() - 0.5f) * 2f,
                    (Math.random() - 0.5f) * 2f,
                    Math.random()
            ).normalize());

            Vector3f hitWeights = new Vector3f();
            Vector3f hitNormal = new Vector3f();
            float[] colorOutput = new float[4];

            randomWeights(pixelX, pixelY, sample, triangle, hitWeights);

            Vector3f bouncePos = new Vector3f();

            MeshUtils.calculateTriangleNormal(
                    this.vertices,
                    MeshData.SIZE,
                    MeshData.XYZ_OFFSET,
                    triangle.x(), triangle.y(), triangle.z(),
                    bouncePos
            );

            this.geometry.getNormalModel().transform(bouncePos).normalize().mul(RAY_OFFSET);

            float offsetX = bouncePos.x();
            float offsetY = bouncePos.y();
            float offsetZ = bouncePos.z();

            bouncePos.set(
                    lerp(hitWeights, triangle.x(), triangle.y(), triangle.z(), MeshData.XYZ_OFFSET + 0),
                    lerp(hitWeights, triangle.x(), triangle.y(), triangle.z(), MeshData.XYZ_OFFSET + 1),
                    lerp(hitWeights, triangle.x(), triangle.y(), triangle.z(), MeshData.XYZ_OFFSET + 2)
            );

            this.geometry.getModel().transformProject(bouncePos).add(offsetX, offsetY, offsetZ);

            for (int i = 0; i < INDIRECT_BOUNCES; i++) {
                if (i != 0) {
                    randomSunDirection(randomDirection);
                    this.status.rays++;
                    if (!Geometry.fastTestRay(bouncePos, randomDirection, this.scene.getGeometries())) {
                        indirectLight.set(this.scene.getSunDiffuseColor());
                        break;
                    }
                }

                RayResult[] results = Geometry.testRay(bouncePos, bounceDir, this.scene.getGeometries());
                this.status.rays++;
                if (results.length == 0) {
                    indirectLight.set(this.scene.getSunAmbientColor());
                    break;
                }
                RayResult hitPos = results[0];

                hitPos.weights(hitWeights);

                float normalX = hitPos.lerp(hitWeights, MeshData.N_XYZ_OFFSET + 0);
                float normalY = hitPos.lerp(hitWeights, MeshData.N_XYZ_OFFSET + 1);
                float normalZ = hitPos.lerp(hitWeights, MeshData.N_XYZ_OFFSET + 2);

                hitNormal.set(normalX, normalY, normalZ).normalize();
                hitPos.getGeometry().getNormalModel().transform(hitNormal);

                bounceDir.reflect(hitNormal);

                float u = hitPos.lerp(hitWeights, MeshData.UV_OFFSET + 0);
                float v = hitPos.lerp(hitWeights, MeshData.UV_OFFSET + 1);

                this.sceneTextures.get(hitPos.getGeometry().getMesh().getTextureHint()).sampleNearest(u, v, colorOutput, 0);
                bounceColors.add(new Vector3f(colorOutput));

                bouncePos.set(hitPos.getTriangleNormal());
                if (!hitPos.frontFace()) {
                    bouncePos.negate();
                }
                bouncePos.mul(RAY_OFFSET);

                bouncePos.add(hitPos.getHitPosition());
            }
            for (Vector3f bounce : bounceColors) {
                indirectLight.mul(bounce);
            }
            finalIndirect.add(indirectLight);
        }
        finalIndirect.div(INDIRECT_RAYS_PER_SAMPLE);
        outColor.set(finalIndirect);
    }

    private void processDirect(
            int pixelX,
            int pixelY,
            int sample,
            Vector3i triangle,
            Vector3fc position,
            Vector3fc normal,
            Vector3fc tangent,
            Matrix3fc TBN,
            Vector3f outColor
    ) {
        outColor
                .set(this.scene.getSunDiffuseColor())
                .mul(Math.max(normal.dot(this.scene.getSunDirectionInverted()), 0f));
    }

    private void processShadow(
            int pixelX,
            int pixelY,
            int sample,
            Vector3i triangle,
            Vector3fc position,
            Vector3fc normal,
            Vector3fc tangent,
            Matrix3fc TBN,
            float[] outReversedShadow
    ) {
        Vector3f randomDirection = new Vector3f();

        Vector3f offsetOrigin = new Vector3f();

        MeshUtils.calculateTriangleNormal(this.vertices,
                MeshData.SIZE,
                MeshData.XYZ_OFFSET,
                triangle.x(), triangle.y(), triangle.z(),
                offsetOrigin
        );

        offsetOrigin.mul(RAY_OFFSET).add(position);

        float shadow = 0f;
        for (int i = 0; i < DIRECT_SHADOW_RAYS_PER_SAMPLE; i++) {
            randomSunDirection(randomDirection);
            if (Geometry.fastTestRay(offsetOrigin, randomDirection, this.scene.getGeometries())) {
                shadow++;
            }
            this.status.rays++;
        }
        shadow /= DIRECT_SHADOW_RAYS_PER_SAMPLE;

        outReversedShadow[0] = 1f - shadow;
    }

    private void denoiseBuffers() {
        final ColorBuffer indirectOutput = new ColorBuffer(this.geometryLightmapSize);
        final GrayBuffer reversedShadowOutput = new GrayBuffer(this.geometryLightmapSize);

        int amountOfCores = Runtime.getRuntime().availableProcessors();
        ExecutorService service = Executors.newFixedThreadPool(amountOfCores);
        List<Future<?>> tasks = new ArrayList<>(amountOfCores);

        this.status.setProgressBarStep(this.denoiserQuads.length);
        for (int i = 0; i < this.denoiserQuads.length; i += (4 * amountOfCores)) {
            setStatusText("Denoising (" + i + "/" + this.denoiserQuads.length + ")");

            for (int j = 0; j < amountOfCores; j++) {
                int quad = i + (j * 4);
                if (quad >= this.denoiserQuads.length) {
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
                for (int j = 0; j < 4; j++) {
                    this.status.stepProgressBar();
                }
            }

            tasks.clear();
        }

        this.indirectColorBuffer = indirectOutput;
        this.reverseShadowBuffer = reversedShadowOutput;
    }

    private void denoiseQuad(ColorBuffer indirectOutput, GrayBuffer reversedShadowOutput, int i) {
        Vector3f color = new Vector3f();

        int minX = clamp(this.denoiserQuads[i + 0], 0, this.geometryLightmapSize);
        int minY = clamp(this.denoiserQuads[i + 1], 0, this.geometryLightmapSize);
        int maxX = clamp(this.denoiserQuads[i + 2], 0, this.geometryLightmapSize);
        int maxY = clamp(this.denoiserQuads[i + 3], 0, this.geometryLightmapSize);

        final int width = maxX - minX;
        final int height = maxY - minY;
        final int xOffset = minX;
        final int yOffset = minY;
        final boolean[] sampleMap = new boolean[width * height * SAMPLES];
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
                for (int s = 0; s < SAMPLES; s++) {
                    if (!this.sampleBuffer.read(x, y, s)) {
                        continue;
                    }

                    this.indirectColorBuffer.read(color, x, y, s);
                    r += color.x();
                    g += color.y();
                    b += color.z();
                    reversedShadow += this.reverseShadowBuffer.read(x, y, s);
                    sampleCount++;

                    sampleMap[s + ((x - xOffset) * SAMPLES) + ((y - yOffset) * width * SAMPLES)] = true;
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
                    
                    if (reversedShadow >= (254f/255f)) {
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
                for (int s = 0; s < SAMPLES; s++) {
                    if (sampleMap[s + (x * SAMPLES) + (y * width * SAMPLES)]) {
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
                8f
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
                for (int s = 0; s < SAMPLES; s++) {
                    if (sampleMap[s + (x * SAMPLES) + (y * width * SAMPLES)]) {
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
                21,
                1.5f
        );
    }

    private void sumBuffersAndOutput() {
        byte[] lineColorData = new byte[this.geometryLightmapSize * 3];

        Vector3f lastValidAverage = new Vector3f();
        Vector3f direct = new Vector3f();
        Vector3f indirect = new Vector3f();
        Vector3f sampleAverage = new Vector3f();

        this.status.setProgressBarStep(this.geometryLightmapSize);
        for (int y = 0; y < this.geometryLightmapSize; y++) {
            setStatusText("Writing to Output Buffer (" + y + "/" + this.geometryLightmapSize + ")");

            lastValidAverage.zero();
            for (int x = 0; x < this.geometryLightmapSize; x++) {
                int processedSamples = 0;
                for (int s = 0; s < SAMPLES; s++) {
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
                    for (int s = 0; s < SAMPLES; s++) {
                        this.directColorBuffer.read(direct, x, y, s);
                        sampleAverage.add(direct);
                    }
                    direct.set(sampleAverage.mul(invProcessedSamples));

                    //shadow
                    float reversedShadowAverage = 0f;
                    for (int s = 0; s < SAMPLES; s++) {
                        reversedShadowAverage += this.reverseShadowBuffer.read(x, y, s);
                    }
                    reversedShadowAverage *= invProcessedSamples;

                    direct.mul(reversedShadowAverage);

                    //indirect
                    sampleAverage.zero();
                    for (int s = 0; s < SAMPLES; s++) {
                        this.indirectColorBuffer.read(indirect, x, y, s);
                        sampleAverage.add(indirect);
                    }
                    indirect.set(sampleAverage.mul(invProcessedSamples));

                    direct.add(indirect);

                    lastValidAverage.set(direct);
                } else {
                    direct.set(lastValidAverage);
                }

                int rColor = Math.min((int) (direct.x() * 255f), 255);
                int gColor = Math.min((int) (direct.y() * 255f), 255);
                int bColor = Math.min((int) (direct.z() * 255f), 255);
                rColor = Math.max(rColor, 0);
                gColor = Math.max(gColor, 0);
                bColor = Math.max(bColor, 0);

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
                this.status.memoryUsage += (lightmapSizeCopy * lightmapSizeCopy) / 2;
            } else {
                this.status.memoryUsage += lightmapSizeCopy * lightmapSizeCopy * 3;
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
            denoiseBuffers();
            sumBuffersAndOutput();
            createLightmapTexture();
        }
    }

}
