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
package cientistavuador.bakedlighting.texture;

import cientistavuador.bakedlighting.Main;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.KHRDebug;

/**
 *
 * @author Cien
 */
public class Textures {

    public static final int EMPTY_LIGHTMAP;
    public static final int ERROR_TEXTURE;
    public static final int WHITE_TEXTURE;

    static {
        EMPTY_LIGHTMAP = glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, EMPTY_LIGHTMAP);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA32F, 1, 1, 1, 0, GL_RGBA, GL_FLOAT, new float[] {1f, 1f, 1f, 1f});
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

        if (Main.DEBUG_ENABLED && GL.getCapabilities().GL_KHR_debug) {
            KHRDebug.glObjectLabel(GL_TEXTURE, EMPTY_LIGHTMAP, "Empty Lightmap");
        }

        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
        
        WHITE_TEXTURE = glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, WHITE_TEXTURE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 1, 1, 0, GL_RGBA, GL_FLOAT, new float[] {1f, 1f, 1f, 1f});
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

        if (Main.DEBUG_ENABLED && GL.getCapabilities().GL_KHR_debug) {
            KHRDebug.glObjectLabel(GL_TEXTURE, EMPTY_LIGHTMAP, "White Texture");
        }

        glBindTexture(GL_TEXTURE_2D, 0);
        
        int blackPixel = 0x00_00_00_FF;
        int pinkPixel = 0xFF_00_FF_FF;
        int emptyTextureSize = 64;
        int[] emptyTexturePixels = new int[emptyTextureSize * emptyTextureSize];
        for (int y = 0; y < emptyTextureSize; y++) {
            int pixelA = pinkPixel;
            int pixelB = blackPixel;
            if (y % 2 != 0) {
                pixelA = blackPixel;
                pixelB = pinkPixel;
            }
            for (int x = 0; x < emptyTextureSize; x++) {
                if (x % 2 == 0) {
                    emptyTexturePixels[x + (y * emptyTextureSize)] = pixelA;
                } else {
                    emptyTexturePixels[x + (y * emptyTextureSize)] = pixelB;
                }
            }
        }

        ERROR_TEXTURE = glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, ERROR_TEXTURE);
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGBA8,
                emptyTextureSize,
                emptyTextureSize,
                0,
                GL_RGBA,
                GL_UNSIGNED_INT_8_8_8_8,
                emptyTexturePixels
        );
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        
        if (Main.DEBUG_ENABLED && GL.getCapabilities().GL_KHR_debug) {
            KHRDebug.glObjectLabel(GL_TEXTURE, ERROR_TEXTURE, "Empty/Error Texture");
        }
        
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public static final int BRICKS;
    public static final int CONCRETE;
    public static final int GRASS;
    public static final int RED;
    public static final int CIENCOLA;

    static {
        int[] textures = TexturesLoader.load(
                "bricks.png",
                "concrete.png",
                "grass.png",
                "red.png",
                "ciencola_diffuse_512.png"
        );
        BRICKS = textures[0];
        CONCRETE = textures[1];
        GRASS = textures[2];
        RED = textures[3];
        CIENCOLA = textures[4];
    }

    public static void init() {

    }

    private Textures() {

    }
}
