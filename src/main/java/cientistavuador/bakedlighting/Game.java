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
package cientistavuador.bakedlighting;

import cientistavuador.bakedlighting.camera.FreeCamera;
import cientistavuador.bakedlighting.debug.AabRender;
import cientistavuador.bakedlighting.debug.LineRender;
import cientistavuador.bakedlighting.geometry.Geometries;
import cientistavuador.bakedlighting.geometry.Geometry;
import cientistavuador.bakedlighting.popups.BakePopup;
import cientistavuador.bakedlighting.resources.mesh.MeshData;
import cientistavuador.bakedlighting.shader.GeometryProgram;
import cientistavuador.bakedlighting.text.GLFontRenderer;
import cientistavuador.bakedlighting.text.GLFontSpecification;
import cientistavuador.bakedlighting.text.GLFontSpecifications;
import cientistavuador.bakedlighting.texture.Textures;
import cientistavuador.bakedlighting.ubo.CameraUBO;
import cientistavuador.bakedlighting.ubo.UBOBindingPoints;
import cientistavuador.bakedlighting.util.BakedLighting;
import cientistavuador.bakedlighting.util.RayResult;
import cientistavuador.bakedlighting.util.SamplingMode;
import cientistavuador.bakedlighting.util.Scene;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.GL42C;

/**
 *
 * @author Cien
 */
public class Game {

    private static final Game GAME = new Game();

    public static Game get() {
        return GAME;
    }

    private final FreeCamera camera = new FreeCamera();
    private final List<RayResult> rays = new ArrayList<>();
    private final Scene scene = new Scene();
    private final BakedLighting.BakedLightingOutput writeToTexture = new BakedLighting.BakedLightingOutput() {
        private Geometry geometry = null;
        private MeshData.LightmapMesh mesh = null;
        private int lightmapSize = 0;
        private String[] groups = null;
        private int texture = 0;
        private int count = 0;

        @Override
        public void prepare(Geometry geometry, MeshData.LightmapMesh mesh, int lightmapSize, String[] groups) {
            this.geometry = geometry;
            this.mesh = mesh;
            this.lightmapSize = lightmapSize;
            this.groups = groups;
            this.count = groups.length;

            this.texture = glGenTextures();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D_ARRAY, this.texture);

            if (Main.isSupported(4, 2)) {
                GL42C.glTexStorage3D(GL_TEXTURE_2D_ARRAY, 1, GL_RGB9_E5, this.lightmapSize, this.lightmapSize, this.groups.length);
            } else {
                glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGB9_E5, this.lightmapSize, this.lightmapSize, this.groups.length, 0, GL_RGBA, GL_FLOAT, 0);
            }
            
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            glBindTexture(GL_TEXTURE_2D_ARRAY, 0);

            Game.this.memoryUsage += this.lightmapSize * this.lightmapSize * 4 * this.groups.length;
        }

        @Override
        public void write(float[] lightmap, int groupIndex) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D_ARRAY, this.texture);

            glTexSubImage3D(
                    GL_TEXTURE_2D_ARRAY, 0,
                    0, 0, groupIndex,
                    this.lightmapSize, this.lightmapSize, 1,
                    GL_RGB, GL_FLOAT, lightmap);

            glBindTexture(GL_TEXTURE_2D_ARRAY, 0);

            this.count--;
            if (this.count == 0) {
                this.geometry.setLightmapTextureHint(texture);
                this.geometry.setLightmapMesh(this.mesh);
            }
        }
    };

    private long memoryUsage = 0;
    private BakedLighting.Status status = BakedLighting.dummyStatus();
    private float interiorIntensity = 1f;
    private float sunIntensity = 1f;
    private boolean interiorEnabled = true;
    private boolean sunEnabled = true;

    private boolean bakeWindowOpen = false;

    private Game() {

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

    public void start() {
        camera.setPosition(1f, 3f, -5f);
        camera.setUBO(CameraUBO.create(UBOBindingPoints.PLAYER_CAMERA));

        GeometryProgram program = GeometryProgram.INSTANCE;
        program.use();
        program.setModel(new Matrix4f());
        program.setColor(1f, 1f, 1f, 1f);
        program.setSunDirection(new Vector3f(-1f, -1f, 0f).normalize());
        program.setSunDiffuse(1f, 1f, 1f);
        program.setSunAmbient(0.2f, 0.2f, 0.2f);
        program.setTextureUnit(0);
        program.setLightingEnabled(true);
        glUseProgram(0);

        for (int i = 0; i < Geometries.GARAGE.length - 1; i++) {
            this.scene.getGeometries().add(new Geometry(Geometries.GARAGE[i]));
        }
        Geometry monkey = new Geometry(Geometries.GARAGE[Geometries.GARAGE.length - 1]);
        this.scene.getGeometries().add(monkey);

        Geometry ciencola = new Geometry(Geometries.CIENCOLA);
        this.scene.getGeometries().add(ciencola);

        Matrix4f matrix = new Matrix4f()
                .translate(-7f, 1f, -2f)
                .scale(1f, 1.2f, 1f);

        ciencola.setModel(matrix);

        ciencola = new Geometry(Geometries.CIENCOLA);
        this.scene.getGeometries().add(ciencola);

        matrix = new Matrix4f()
                .translate(0f, 0.595f, -5f)
                .scale(1f, 1.2f, 1f)
                .scale(0.10f);

        ciencola.setModel(matrix);

        ciencola = new Geometry(Geometries.CIENCOLA);
        this.scene.getGeometries().add(ciencola);

        matrix = new Matrix4f()
                .translate(5.5f, 1f, 0f)
                .scale(1f, 1.2f, 1f);

        ciencola.setModel(matrix);

        ciencola = new Geometry(Geometries.CIENCOLA);
        this.scene.getGeometries().add(ciencola);

        matrix = new Matrix4f()
                .translate(-5.5f, 1f, 0f)
                .scale(1f, 1.2f, 1f);

        ciencola.setModel(matrix);

        this.scene.setIndirectLightingEnabled(true);
        this.scene.setDirectLightingEnabled(true);
        this.scene.setShadowsEnabled(true);

        this.scene.setIndirectLightingBlurArea(4f);
        this.scene.setShadowBlurArea(1.2f);

        this.scene.setSamplingMode(SamplingMode.SAMPLE_16);

        this.scene.setFastModeEnabled(false);

        Scene.PointLight point = new Scene.PointLight();
        point.setPosition(0f, 2f, 6f);
        point.setDiffuse(2f, 2f, 2f);
        point.setLightSize(0.2f);
        point.setGroupName("interior");
        this.scene.getLights().add(point);

        Scene.SpotLight spot0 = new Scene.SpotLight();
        spot0.setPosition(-5.9f, 3.5f, 0);
        spot0.setDiffuse(2f, 2f, 2f);
        spot0.setLightSize(0.2f);
        spot0.setGroupName("interior");
        this.scene.getLights().add(spot0);

        Scene.SpotLight spot1 = new Scene.SpotLight();
        spot1.setPosition(5.9f, 3.5f, 0);
        spot1.setDiffuse(2f, 2f, 2f);
        spot1.setLightSize(0.2f);
        spot1.setGroupName("interior");
        this.scene.getLights().add(spot1);

        Scene.DirectionalLight sun = new Scene.DirectionalLight();
        sun.setGroupName("sun");
        this.scene.getLights().add(sun);

        /*float[] ciencolaVertices = e.getMesh().getVertices();
        LightmapUVs.GeneratorOutput output = LightmapUVs.generate(
        ciencolaVertices,
        MeshData.SIZE,
        MeshData.XYZ_OFFSET,
        1f / 0.5f,
        1f,
        1f,
        1f
        );
        
        System.out.println("Lightmap Size: " + output.getLightmapSize());
        
        float[] uvs = output.getUVs();
        
        try {
        BufferedWriter out = new BufferedWriter(new FileWriter("saida.obj"));
        for (int v = 0; v < uvs.length; v += (3 * 2)) {
        float u0 = uvs[v + 0];
        float v0 = uvs[v + 1];
        
        float u1 = uvs[v + 2];
        float v1 = uvs[v + 3];
        
        float u2 = uvs[v + 4];
        float v2 = uvs[v + 5];
        
        out.write("v " + u0 + " " + v0);
        out.newLine();
        out.write("v " + u1 + " " + v1);
        out.newLine();
        out.write("v " + u2 + " " + v2);
        out.newLine();
        out.write("f " + ((v / 2) + 1) + " " + ((v / 2) + 2) + " " + ((v / 2) + 3));
        out.newLine();
        }
        out.close();
        } catch (IOException ex) {
        ex.printStackTrace(System.out);
        }*/
    }

    public void loop() {
        if (!this.status.isDone()) {
            try {
                Thread.sleep(16);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }

        for (RayResult r : this.rays) {
            LineRender.queueRender(r.getOrigin(), r.getHitPosition());
        }

        if (this.status.hasError()) {
            try {
                this.status.throwException();
            } catch (ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }

        float speed = 1f;

        if (this.interiorEnabled) {
            this.interiorIntensity += Main.TPF * speed;
        } else {
            this.interiorIntensity -= Main.TPF * speed;
        }

        if (this.sunEnabled) {
            this.sunIntensity += Main.TPF * speed;
        } else {
            this.sunIntensity -= Main.TPF * speed;
        }

        this.interiorIntensity = Math.min(Math.max(this.interiorIntensity, 0f), 1f);
        this.sunIntensity = Math.min(Math.max(this.sunIntensity, 0f), 1f);

        GeometryProgram.INSTANCE.setBakedLightGroupIntensity(0, this.interiorIntensity);
        GeometryProgram.INSTANCE.setBakedLightGroupIntensity(1, this.sunIntensity);

        camera.updateMovement();
        camera.updateUBO();

        Matrix4f cameraProjectionView = new Matrix4f(this.camera.getProjectionView());

        GeometryProgram program = GeometryProgram.INSTANCE;
        program.use();
        program.updateLightsUniforms();
        program.setProjectionView(cameraProjectionView);
        program.setTextureUnit(0);
        program.setLightmapTextureUnit(1);
        program.setLightingEnabled(false);
        program.setColor(1f, 1f, 1f, 1f);
        for (Geometry geo : this.scene.getGeometries()) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, geo.getMesh().getTextureHint());
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D_ARRAY, geo.getLightmapTextureHint());
            program.setModel(geo.getModel());

            MeshData mesh = geo.getMesh();
            MeshData.LightmapMesh lightmap = geo.getLightmapMesh();

            if (lightmap == null || !lightmap.isDone()) {
                glBindVertexArray(mesh.getVAO());
            } else {
                glBindVertexArray(lightmap.getVAO());
            }
            mesh.render();
            glBindVertexArray(0);
        }
        for (Scene.Light light : this.scene.getLights()) {
            if (light instanceof Scene.PointLight p) {
                float r = p.getDiffuse().x();
                float g = p.getDiffuse().y();
                float b = p.getDiffuse().z();
                float max = Math.max(r, Math.max(g, b));
                if (max > 1f) {
                    float invmax = 1f / max;
                    r *= invmax;
                    g *= invmax;
                    b *= invmax;
                }
                program.setColor(r, g, b, 1f);
                Matrix4f model = new Matrix4f();
                model.translate(p.getPosition()).scale(p.getLightSize());
                program.setModel(model);

                MeshData sphere = Geometries.SPHERE;

                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, sphere.getTextureHint());
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D_ARRAY, Textures.EMPTY_LIGHTMAP);

                glBindVertexArray(Geometries.SPHERE.getVAO());
                sphere.render();
                glBindVertexArray(0);
            }
        }
        glUseProgram(0);

        AabRender.renderQueue(camera);
        LineRender.renderQueue(camera);

        String[] text = new String[]{
            new StringBuilder()
                .append("ESC - Move Camera\n")
                .append("I - Toggle Interior Lights\n")
                .append("F - Toggle Sun\n")
                .append("R - Bake Lightmap\n")
                .append(this.status.getASCIIProgressBar()).append('\n')
                .append(this.status.getCurrentStatus()).append('\n')
                .append(this.status.getRaysPerSecondFormatted()).append('\n')
                .append("Video Memory Used By Lightmaps: ").append(getMemoryUsageFormatted()).append('\n')
                .append("FPS: ").append(Main.FPS).append('\n')
                .append("Estimated Time: ").append(this.status.getEstimatedTimeFormatted()).append("\n")
            .toString()
        };
        GLFontRenderer.render(-0.895f, -0.605f, new GLFontSpecification[]{GLFontSpecifications.SPACE_MONO_REGULAR_0_035_BLACK}, text);
        GLFontRenderer.render(-0.90f, -0.60f, new GLFontSpecification[]{GLFontSpecifications.SPACE_MONO_REGULAR_0_035_WHITE}, text);

        Main.WINDOW_TITLE += " (DrawCalls: " + Main.NUMBER_OF_DRAWCALLS + ", Vertices: " + Main.NUMBER_OF_VERTICES + ")";
        Main.WINDOW_TITLE += " (x:" + (int) Math.floor(camera.getPosition().x()) + ",y:" + (int) Math.floor(camera.getPosition().y()) + ",z:" + (int) Math.ceil(camera.getPosition().z()) + ")";
    }

    public void bakePopupCallback(BakePopup popup) {
        if (!this.status.isDone()) {
            return;
        }

        for (Geometry geo : this.scene.getGeometries()) {
            if (geo.getLightmapTextureHint() != Textures.EMPTY_LIGHTMAP) {
                glDeleteTextures(geo.getLightmapTextureHint());
                geo.setLightmapTextureHint(Textures.EMPTY_LIGHTMAP);
            }
        }
        this.memoryUsage = 0;

        try {
            //config
            popup.getPixelToWorldRatio().commitEdit();
            float pixelToWorldRatio = ((Number) popup.getPixelToWorldRatio().getValue()).floatValue();
            SamplingMode samplingMode = (SamplingMode) popup.getSamplingMode().getSelectedItem();
            popup.getRayOffset().commitEdit();
            float rayOffset = ((Number) popup.getRayOffset().getValue()).floatValue();
            boolean fillEmptyValues = popup.getFillEmptyValues().isSelected();
            boolean fastMode = popup.getFastMode().isSelected();

            this.scene.setSamplingMode(samplingMode);
            this.scene.setRayOffset(rayOffset);
            this.scene.setFillDisabledValuesWithLightColors(fillEmptyValues);
            this.scene.setFastModeEnabled(fastMode);

            //direct
            boolean directEnabled = popup.getDirectLighting().isSelected();
            popup.getDirectAttenuation().commitEdit();
            float attenuation = ((Number) popup.getDirectAttenuation().getValue()).floatValue();

            this.scene.setDirectLightingEnabled(directEnabled);
            this.scene.setDirectLightingAttenuation(attenuation);

            //shadows
            boolean shadowsEnabled = popup.getShadows().isSelected();
            popup.getShadowRays().commitEdit();
            int shadowRays = ((Number) popup.getShadowRays().getValue()).intValue();
            popup.getShadowBlur().commitEdit();
            float shadowBlur = ((Number) popup.getShadowBlur().getValue()).floatValue();

            this.scene.setShadowsEnabled(shadowsEnabled);
            this.scene.setShadowRaysPerSample(shadowRays);
            this.scene.setShadowBlurArea(shadowBlur);

            //indirect
            boolean indirectEnabled = popup.getIndirectLighting().isSelected();
            popup.getIndirectRays().commitEdit();
            int indirectRays = ((Number) popup.getIndirectRays().getValue()).intValue();
            popup.getIndirectBounces().commitEdit();
            int bounces = ((Number) popup.getIndirectBounces().getValue()).intValue();
            popup.getIndirectBlur().commitEdit();
            float indirectBlur = ((Number) popup.getIndirectBlur().getValue()).floatValue();
            popup.getIndirectReflectionFactor().commitEdit();
            float reflectionFactor = ((Number) popup.getIndirectReflectionFactor().getValue()).floatValue();

            this.scene.setIndirectLightingEnabled(indirectEnabled);
            this.scene.setIndirectRaysPerSample(indirectRays);
            this.scene.setIndirectBounces(bounces);
            this.scene.setIndirectLightingBlurArea(indirectBlur);
            this.scene.setIndirectLightReflectionFactor(reflectionFactor);

            this.status = BakedLighting.bake(this.writeToTexture, this.scene, pixelToWorldRatio);
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void mouseCursorMoved(double x, double y) {
        camera.mouseCursorMoved(x, y);
    }

    public void windowSizeChanged(int width, int height) {
        camera.setDimensions(width, height);
    }

    public void keyCallback(long window, int key, int scancode, int action, int mods) {
        if (key == GLFW_KEY_SPACE && action == GLFW_PRESS) {
            this.rays.clear();

            Vector3f origin = new Vector3f().set(camera.getPosition());
            Vector3f direction = new Vector3f().set(camera.getFront());

            int bounces = 8;
            float offset = 0.0001f;
            for (int i = 0; i < bounces; i++) {
                RayResult[] results = Geometry.testRay(origin, direction, this.scene.getGeometries());
                if (results.length == 0) {
                    break;
                }
                RayResult result = results[0];
                this.rays.add(result);

                Vector3f hitWeights = new Vector3f();
                Vector3f hitNormal = new Vector3f();

                result.weights(hitWeights);

                float normalX = result.lerp(hitWeights, MeshData.N_XYZ_OFFSET + 0);
                float normalY = result.lerp(hitWeights, MeshData.N_XYZ_OFFSET + 1);
                float normalZ = result.lerp(hitWeights, MeshData.N_XYZ_OFFSET + 2);

                hitNormal.set(normalX, normalY, normalZ).normalize();
                result.getGeometry().getNormalModel().transform(hitNormal);

                origin.set(result.getTriangleNormal());
                if (!result.frontFace()) {
                    origin.negate();
                }
                origin.mul(offset).add(result.getHitPosition());

                direction.reflect(hitNormal);
            }
        }
        if (key == GLFW_KEY_R && action == GLFW_PRESS) {
            if (!this.bakeWindowOpen) {
                this.bakeWindowOpen = true;
                BakePopup.show((t) -> {
                    Main.MAIN_TASKS.add(() -> {
                        Game.this.bakePopupCallback(t);
                    });
                }, (t) -> {
                    this.bakeWindowOpen = false;
                });
                if (this.camera.isCaptureMouse()) {
                    this.camera.pressEscape();
                }
            }
        }
        if (key == GLFW_KEY_I && action == GLFW_PRESS) {
            this.interiorEnabled = !this.interiorEnabled;
        }
        if (key == GLFW_KEY_F && action == GLFW_PRESS) {
            this.sunEnabled = !this.sunEnabled;
        }
    }

    public void mouseCallback(long window, int button, int action, int mods) {

    }
}
