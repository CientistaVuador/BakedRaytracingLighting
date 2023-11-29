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
package cientistavuador.bakedlighting.shader;

import cientistavuador.bakedlighting.util.BetterUniformSetter;
import cientistavuador.bakedlighting.util.ProgramCompiler;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class BackgroundProgram {
    
    public static final int SHADER_PROGRAM = ProgramCompiler.compile(
            """
            #version 330 core
            
            uniform vec2 scale;
            
            layout (location = 0) in vec3 vertexPosition;
            layout (location = 1) in vec2 vertexUv;
            
            out vec2 uv;
            
            void main() {
                gl_Position = vec4(vertexPosition, 1.0).xyww;
                uv = ((vertexUv - vec2(0.5)) * scale) + vec2(0.5);
            }
            """
            ,
            """
            #version 330 core
            
            uniform sampler2D background;
            
            in vec2 uv;
            
            layout (location = 0) out vec4 colorOutput;
            
            void main() {
                colorOutput = vec4(texture(background, uv).rgb, 1.0);
            }
            """
    );
    
    public static final BetterUniformSetter UNIFORMS = new BetterUniformSetter(SHADER_PROGRAM);
    
    public static void sendUniforms(float scaleX, float scaleY, int backgroundTexture) {
        glUniform2f(UNIFORMS.locationOf("scale"), scaleX, scaleY);
        
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, backgroundTexture);
        glUniform1i(UNIFORMS.locationOf("background"), 0);
    }
    
    private BackgroundProgram() {
        
    }
    
}
