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
import cientistavuador.bakedlighting.geometry.Geometries;
import cientistavuador.bakedlighting.resources.mesh.MeshData;
import cientistavuador.bakedlighting.resources.mesh.MeshResources;
import cientistavuador.bakedlighting.shader.GeometryProgram;
import cientistavuador.bakedlighting.texture.Textures;
import cientistavuador.bakedlighting.ubo.CameraUBO;
import cientistavuador.bakedlighting.ubo.UBOBindingPoints;
import cientistavuador.bakedlighting.util.SoftwareRenderer;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.FloatBuffer;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.openal.AL11.*;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.system.MemoryStack;

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
    }

    public void loop() {
        camera.updateMovement();
        Matrix4f cameraProjectionView = new Matrix4f(this.camera.getProjectionView());
        
        GeometryProgram program = GeometryProgram.INSTANCE;
        program.use();
        program.setProjectionView(cameraProjectionView);
        glActiveTexture(GL_TEXTURE0);
        for (MeshData e : Geometries.GARAGE) {
            glBindTexture(GL_TEXTURE_2D, e.getTextureHint());
            e.bindRenderUnbind();
        }
        glUseProgram(0);

        AabRender.renderQueue(camera);

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

    }

    public void mouseCallback(long window, int button, int action, int mods) {

    }
}
