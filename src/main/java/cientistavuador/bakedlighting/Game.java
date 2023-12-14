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
import cientistavuador.bakedlighting.shader.GeometryProgram;
import cientistavuador.bakedlighting.text.GLFontRenderer;
import cientistavuador.bakedlighting.text.GLFontSpecification;
import cientistavuador.bakedlighting.text.GLFontSpecifications;
import cientistavuador.bakedlighting.ubo.CameraUBO;
import cientistavuador.bakedlighting.ubo.UBOBindingPoints;
import cientistavuador.bakedlighting.util.BakedRaytracing;
import cientistavuador.bakedlighting.util.RayResult;
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
    private final Geometry[] geometries = new Geometry[Geometries.GARAGE.length];
    private RayResult ray = null;
    private final BakedRaytracing baked = new BakedRaytracing(geometries, 512, true, new Vector3f(-1f, -0.75f, 0.5f).normalize().negate());

    private Game() {

    }

    public void start() {
        camera.setPosition(0, 8f, 16f);
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

        for (int i = 0; i < geometries.length; i++) {
            geometries[i] = new Geometry(Geometries.GARAGE[i]);
        }
    }
    
    public void loop() {
        if (ray != null) {
            LineRender.queueRender(ray.getOrigin(), ray.getHitpoint());
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
        for (int i = 0; i < geometries.length; i++) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, geometries[i].getMesh().getTextureHint());
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, geometries[i].getLightmapTextureHint());
            program.setModel(geometries[i].getModel());
            geometries[i].getMesh().bindRenderUnbind();
        }
        glUseProgram(0);

        AabRender.renderQueue(camera);
        LineRender.renderQueue(camera);

        String[] text = new String[]{
            new StringBuilder()
                    .append("R - Bake Lightmap\n")
                    .append(this.baked.getCurrentProgressBar()).append('\n')
                    .append("Status: ").append(this.baked.getCurrentStatus()).append('\n')
                    .toString()
        };
        GLFontRenderer.render(-0.795f, -0.605f, new GLFontSpecification[] {GLFontSpecifications.SPACE_MONO_REGULAR_0_04_BLACK}, text);
        GLFontRenderer.render(-0.80f, -0.60f, new GLFontSpecification[] {GLFontSpecifications.SPACE_MONO_REGULAR_0_04_WHITE}, text);

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
            RayResult[] result = Geometry.testRay(origin, direction, geometries);
            if (result.length != 0) {
                this.ray = result[0];
            }
            c.markEnd("ray");
            c.print();
        }
        if (key == GLFW_KEY_R && action == GLFW_PRESS) {
            if (this.baked.isDone()) {
                this.baked.finishProcessing();
            }
            if (!this.baked.isProcessing()) {
                this.baked.beginProcessing();
            }
        }
    }

    public void mouseCallback(long window, int button, int action, int mods) {

    }
}
