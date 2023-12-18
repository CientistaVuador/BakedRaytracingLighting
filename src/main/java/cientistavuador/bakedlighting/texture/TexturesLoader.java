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
import cientistavuador.bakedlighting.resources.image.ImageResources;
import cientistavuador.bakedlighting.resources.image.NativeImage;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.KHRDebug;

/**
 *
 * @author Cien
 */
public class TexturesLoader {

    public static final boolean USE_ANISOTROPIC_FILTERING = true;
    public static final boolean DEBUG_OUTPUT = true;

    public static int[] load(String... names) {
        if (names.length == 0) {
            if (DEBUG_OUTPUT) {
                System.out.println("No textures to load.");
            }
            return new int[0];
        }

        if (DEBUG_OUTPUT) {
            System.out.println("Loading textures...");
        }

        ArrayDeque<Future<NativeImage>> futureDatas = new ArrayDeque<>();
        NativeImage[] images = new NativeImage[names.length];

        for (int i = 0; i < images.length; i++) {
            final int index = i;
            if (DEBUG_OUTPUT) {
                System.out.println("Loading texture '" + names[index] + "' with index " + index);
            }
            futureDatas.add(CompletableFuture.supplyAsync(() -> {
                NativeImage e = ImageResources.load(names[index], 4);
                if (DEBUG_OUTPUT) {
                    System.out.println("Finished loading texture '" + names[index] + "' with index " + index + ", " + e.getWidth() + "x" + e.getHeight());
                }
                return e;
            }));
        }

        Future<NativeImage> future;
        int index = 0;
        RuntimeException exception = null;
        while ((future = futureDatas.poll()) != null) {
            try {
                images[index] = future.get();
                index++;
            } catch (InterruptedException | ExecutionException ex) {
                exception = new RuntimeException(ex);
            }
        }
        if (exception != null) {
            for (NativeImage image : images) {
                if (image == null) {
                    continue;
                }
                image.free();
            }
            throw exception;
        }

        int[] textures = new int[images.length];

        glActiveTexture(GL_TEXTURE0);
        for (int i = 0; i < textures.length; i++) {
            if (DEBUG_OUTPUT) {
                System.out.println("Sending texture '" + names[i] + "', index " + i + " to the gpu.");
            }
            NativeImage image = images[i];

            int texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, image.getWidth(), image.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, image.getData());

            glGenerateMipmap(GL_TEXTURE_2D);

            image.free();

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

            if (USE_ANISOTROPIC_FILTERING && GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                glTexParameterf(
                        GL_TEXTURE_2D,
                        GL_TEXTURE_MAX_ANISOTROPY_EXT,
                        glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT)
                );
            }
            
            if (Main.DEBUG_ENABLED && GL.getCapabilities().GL_KHR_debug) {
                KHRDebug.glObjectLabel(GL_TEXTURE, texture, "Texture_" + names[i]);
            }
            
            glBindTexture(GL_TEXTURE_2D, 0);

            textures[i] = texture;
            if (DEBUG_OUTPUT) {
                System.out.println("Finished sending texture '" + names[i] + "', index " + i + " to the gpu with object id " + texture + ".");
            }
        }

        if (DEBUG_OUTPUT) {
            System.out.println("Finished loading textures.");
        }
        return textures;
    }

    private TexturesLoader() {

    }

}
