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
import cientistavuador.bakedlighting.geometry.GeometriesLoader;
import cientistavuador.bakedlighting.geometry.Geometry;
import cientistavuador.bakedlighting.resources.mesh.MeshData;
import cientistavuador.bakedlighting.shader.GeometryProgram;
import cientistavuador.bakedlighting.text.GLFontRenderer;
import cientistavuador.bakedlighting.text.GLFontSpecification;
import cientistavuador.bakedlighting.text.GLFontSpecifications;
import cientistavuador.bakedlighting.texture.Textures;
import cientistavuador.bakedlighting.ubo.CameraUBO;
import cientistavuador.bakedlighting.ubo.UBOBindingPoints;
import cientistavuador.bakedlighting.util.BakedLighting;
import cientistavuador.bakedlighting.util.LightmapUVs;
import cientistavuador.bakedlighting.util.RayResult;
import cientistavuador.bakedlighting.util.SamplingMode;
import cientistavuador.bakedlighting.util.Scene;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

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

    private BakedLighting.Status status = BakedLighting.dummyStatus();

    private Game() {

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

        for (int i = 0; i < Geometries.GARAGE.length; i++) {
            this.scene.getGeometries().add(new Geometry(Geometries.GARAGE[i]));
        }
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
        
        this.scene.setIndirectLightingEnabled(true);
        this.scene.setDirectLightingEnabled(true);
        this.scene.setShadowsEnabled(true);
        
        this.scene.setFastModeEnabled(false);
        
        this.scene.setSamplingMode(SamplingMode.SAMPLE_16);

        /*float[] ciencolaVertices = Geometries.GARAGE[4].getVertices();
        LightmapUVs.GeneratorOutput output = LightmapUVs.generate(
        ciencolaVertices,
        MeshData.SIZE,
        MeshData.XYZ_OFFSET,
        null,
        1f / 0.05f
        );*/
        
        //System.out.println("Lightmap Size: "+output.getLightmapSize());
        
        //float[] uvs = output.getUVs();
        
        //try {
            //BufferedWriter out = new BufferedWriter(new FileWriter("saida.obj"));
            //for (int v = 0; v < uvs.length; v += (3 * 2)) {
                //float u0 = uvs[v + 0];
                //float v0 = uvs[v + 1];

                //float u1 = uvs[v + 2];
                //float v1 = uvs[v + 3];

                //float u2 = uvs[v + 4];
                //float v2 = uvs[v + 5];
                
                //out.write("v " + u0 + " " + v0);
                //out.newLine();
                //out.write("v " + u1 + " " + v1);
                //out.newLine();
                //out.write("v " + u2 + " " + v2);
                //out.newLine();
                //out.write("f " + ((v / 2) + 1) + " " + ((v / 2) + 2) + " " + ((v / 2) + 3));
                //out.newLine();
           // }
            //out.close();
        //} catch (IOException ex) {
        //    ex.printStackTrace(System.out);
        //}
    }

    public void loop() {
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

        camera.updateMovement();
        camera.updateUBO();

        Matrix4f cameraProjectionView = new Matrix4f(this.camera.getProjectionView());

        GeometryProgram program = GeometryProgram.INSTANCE;
        program.use();
        program.setProjectionView(cameraProjectionView);
        program.setTextureUnit(0);
        program.setLightmapTextureUnit(1);
        program.setLightingEnabled(false);
        for (Geometry geo : this.scene.getGeometries()) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, geo.getMesh().getTextureHint());
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, geo.getLightmapTextureHint());
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
        glUseProgram(0);

        AabRender.renderQueue(camera);
        LineRender.renderQueue(camera);

        String[] text = new String[]{
            new StringBuilder()
            .append("R - Bake Lightmap\n")
            .append(this.status.getASCIIProgressBar()).append('\n')
            .append("Status: ").append(this.status.getCurrentStatus()).append('\n')
            .append(this.status.getRaysPerSecondFormatted()).append('\n')
            .append("Video Memory Used By Lightmaps: ").append(this.status.getMemoryUsageFormatted()).append('\n')
            .append("FPS: ").append(Main.FPS).append('\n')
            .toString()
        };
        GLFontRenderer.render(-0.895f, -0.605f, new GLFontSpecification[]{GLFontSpecifications.SPACE_MONO_REGULAR_0_04_BLACK}, text);
        GLFontRenderer.render(-0.90f, -0.60f, new GLFontSpecification[]{GLFontSpecifications.SPACE_MONO_REGULAR_0_04_WHITE}, text);

        Main.WINDOW_TITLE += " (DrawCalls: " + Main.NUMBER_OF_DRAWCALLS + ", Vertices: " + Main.NUMBER_OF_VERTICES + ")";
        Main.WINDOW_TITLE += " (x:" + (int) Math.floor(camera.getPosition().x()) + ",y:" + (int) Math.floor(camera.getPosition().y()) + ",z:" + (int) Math.ceil(camera.getPosition().z()) + ")";
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
            if (this.status.isDone()) {
                for (Geometry geo : this.scene.getGeometries()) {
                    if (geo.getLightmapTextureHint() != Textures.EMPTY_LIGHTMAP_TEXTURE) {
                        glDeleteTextures(geo.getLightmapTextureHint());
                        geo.setLightmapTextureHint(Textures.EMPTY_LIGHTMAP_TEXTURE);
                    }
                }
                this.status = BakedLighting.bake(this.scene, 1f / 0.05f);
            }
        }
    }
    
    public void mouseCallback(long window, int button, int action, int mods) {

    }
}
