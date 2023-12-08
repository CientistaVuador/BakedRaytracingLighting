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
package cientistavuador.bakedlighting.debug;

import cientistavuador.bakedlighting.Main;
import cientistavuador.bakedlighting.camera.Camera;
import cientistavuador.bakedlighting.ubo.CameraUBO;
import cientistavuador.bakedlighting.util.BetterUniformSetter;
import cientistavuador.bakedlighting.util.ProgramCompiler;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.joml.Vector3fc;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class LineRender {
    private static final String VERTEX_SHADER = 
            """
            #version 330 core
            
            layout (std140) uniform Camera {
                mat4 projection;
                mat4 view;
                ivec4 icamPos;
                vec4 dcamPos;
            };
            
            uniform ivec3 iPos[2];
            uniform vec3 dPos[2];
            
            layout (location = 0) in int lineIndex;
            
            void main() {
                vec3 resultVertex = vec3(iPos[lineIndex] - icamPos.xyz) + (dPos[lineIndex] - dcamPos.xyz);
                gl_Position = projection * view * vec4(resultVertex, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = 
            """
            #version 330 core
            
            layout (location = 0) out vec4 outputColor;
            
            void main() {
                outputColor = vec4(0.0, 0.0, 1.0, 1.0);
            }
            """;

    private static final ConcurrentLinkedQueue<Runnable> renderQueue = new ConcurrentLinkedQueue<>();

    private static final int SHADER_PROGRAM = ProgramCompiler.compile(VERTEX_SHADER, FRAGMENT_SHADER);
    private static final int CAMERA_UBO_INDEX = glGetUniformBlockIndex(SHADER_PROGRAM, "Camera");
    private static final BetterUniformSetter UNIFORMS = new BetterUniformSetter(SHADER_PROGRAM);
    private static final int VAO;

    static {
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, new int[] {
            0, 1
        }, GL_STATIC_DRAW);

        VAO = glGenVertexArrays();
        glBindVertexArray(VAO);

        glEnableVertexAttribArray(0);
        glVertexAttribIPointer(0, 1, GL_INT, 1 * Integer.BYTES, 0);
        
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    public static void beginRendering(Camera camera) {
        CameraUBO ubo = camera.getUBO();
        if (ubo == null) {
            throw new NullPointerException("Camera UBO is null");
        }

        glUseProgram(SHADER_PROGRAM);
        glUniformBlockBinding(SHADER_PROGRAM, CAMERA_UBO_INDEX, ubo.getBindingPoint());

        glBindVertexArray(VAO);
    }

    public static void render(double x0, double y0, double z0, double x1, double y1, double z1) {
        int xInt0 = (int) Math.floor(x0);
        int yInt0 = (int) Math.floor(y0);
        int zInt0 = (int) Math.ceil(z0);
        float xDec0 = (float) (x0 - xInt0);
        float yDec0 = (float) (y0 - yInt0);
        float zDec0 = (float) (z0 - zInt0);
        
        int xInt1 = (int) Math.floor(x1);
        int yInt1 = (int) Math.floor(y1);
        int zInt1 = (int) Math.ceil(z1);
        float xDec1 = (float) (x1 - xInt1);
        float yDec1 = (float) (y1 - yInt1);
        float zDec1 = (float) (z1 - zInt1);
        
        glUniform3i(UNIFORMS.locationOf("iPos[0]"), xInt0, yInt0, zInt0);
        glUniform3f(UNIFORMS.locationOf("dPos[0]"), xDec0, yDec0, zDec0);
        glUniform3i(UNIFORMS.locationOf("iPos[1]"), xInt1, yInt1, zInt1);
        glUniform3f(UNIFORMS.locationOf("dPos[1]"), xDec1, yDec1, zDec1);
        
        glDrawArrays(GL_LINES, 0, 2);
        Main.NUMBER_OF_DRAWCALLS++;
        Main.NUMBER_OF_VERTICES += 2;
    }

    public static void endRendering() {
        glBindVertexArray(0);
        glUseProgram(0);
    }

    public static void queueRender(double x0, double y0, double z0, double x1, double y1, double z1) {
        renderQueue.add(() -> {
            render(x0, y0, z0, x1, y1, z1);
        });
    }
    
    public static void queueRender(Vector3fc a, Vector3fc b) {
        queueRender(a.x(), a.y(), a.z(), b.x(), b.y(), b.z());
    }

    public static int renderQueue(Camera camera) {
        int drawCalls = 0;

        beginRendering(camera);
        Runnable r;
        while ((r = renderQueue.poll()) != null) {
            r.run();
            drawCalls++;
        }
        endRendering();

        return drawCalls;
    }
}
