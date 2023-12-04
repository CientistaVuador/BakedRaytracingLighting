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

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL33C;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.system.MemoryStack;

/**
 *
 * @author Cien
 */
public class BetterUniformSetter {

    public static void uniformMatrix3fv(int location, Matrix3fc matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrixBuffer = stack.mallocFloat(3 * 3);
            matrix.get(matrixBuffer);
            GL33C.glUniformMatrix3fv(location, false, matrixBuffer);
        }
    }
    
    public static void uniformMatrix4fv(int location, Matrix4fc matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrixBuffer = stack.mallocFloat(4 * 4);
            matrix.get(matrixBuffer);
            GL33C.glUniformMatrix4fv(location, false, matrixBuffer);
        }
    }
    
    private final int program;
    private final String[] uniforms;
    private final Map<String, Integer> locations = new HashMap<>();
    
    public BetterUniformSetter(int program) {
        this.program = program;
        
        this.uniforms = new String[glGetProgrami(program, GL_ACTIVE_UNIFORMS)];
        for (int i = 0; i < this.uniforms.length; i++) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                String uniform = glGetActiveUniform(program, i, stack.callocInt(1), stack.callocInt(1));
                this.uniforms[i] = uniform;
                this.locations.put(uniform, glGetUniformLocation(program, uniform));
            }
        }
    }

    public int getProgram() {
        return program;
    }

    public String[] getUniforms() {
        return uniforms.clone();
    }
    
    public int locationOf(String uniform) {
        Integer e = this.locations.get(uniform);
        if (e == null) {
            return -1;
        }
        return e;
    }
    
}
