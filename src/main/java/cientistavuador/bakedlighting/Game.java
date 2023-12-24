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
import cientistavuador.bakedlighting.debug.DebugCounter;
import cientistavuador.bakedlighting.debug.LineRender;
import cientistavuador.bakedlighting.geometry.Geometries;
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
import cientistavuador.bakedlighting.util.RayResult;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    private RayResult ray = null;
    private final BakedLighting.Scene scene = new BakedLighting.Scene();
    
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
                .scale(1f, 1.2f, 1f)
                ;

        ciencola.setModel(matrix);

        ciencola = new Geometry(Geometries.CIENCOLA);
        this.scene.getGeometries().add(ciencola);

        matrix = new Matrix4f()
                .translate(0f, 0.595f, -5f)
                .scale(1f, 1.2f, 1f)
                .scale(0.10f);

        ciencola.setModel(matrix);
    }

    public void loop() {
        if (ray != null) {
            LineRender.queueRender(ray.getOrigin(), ray.getHitpoint());
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
            int lightmapSize = geo.getLightmapTextureSizeHint();

            MeshData.LightmapMesh lightmapMesh = mesh.getLightmapMesh(lightmapSize);
            if (lightmapMesh == null) {
                glBindVertexArray(mesh.getVAO());
            } else {
                glBindVertexArray(lightmapMesh.getVAO());
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
            Vector3f origin = new Vector3f().set(camera.getPosition());
            Vector3f direction = new Vector3f().set(camera.getFront());

            DebugCounter c = new DebugCounter();
            c.markStart("ray");
            RayResult[] result = Geometry.testRay(origin, this.scene.getSunDirectionInverted(), this.scene.getGeometries());
            if (result.length != 0) {
                this.ray = result[0];
            }
            System.out.println(this.ray);
            c.markEnd("ray");
            c.print();
        }
        if (key == GLFW_KEY_R && action == GLFW_PRESS) {
            if (this.status.isDone()) {
                for (Geometry geo : this.scene.getGeometries()) {
                    if (geo.getLightmapTextureHint() != Textures.EMPTY_LIGHTMAP_TEXTURE) {
                        glDeleteTextures(geo.getLightmapTextureHint());
                        geo.setLightmapTextureHint(Textures.EMPTY_LIGHTMAP_TEXTURE);
                    }
                }
                this.status = BakedLighting.bake(this.scene, 1024);
            }
        }
    }

    public void mouseCallback(long window, int button, int action, int mods) {

    }
}
