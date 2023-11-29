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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 *
 * @author Cien
 */
public class Base64Image {
    
    public static String toBase64Image(BufferedImage image) {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream(Short.MAX_VALUE);
        try {
            if (!ImageIO.write(image, "PNG", byteArray)) {
                return null;
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return Base64.getEncoder().encodeToString(byteArray.toByteArray());
    }
    
    public static BufferedImage toImage(String s) {
        ByteArrayInputStream byteArray = new ByteArrayInputStream(Base64.getDecoder().decode(s));
        BufferedImage image;
        try {
            image = ImageIO.read(byteArray);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return image;
    }
    
    public static byte[] to32BitRGBAImage(String s) {
        return to32BitRGBAImage(toImage(s));
    }
    
    public static byte[] to32BitRGBAImage(BufferedImage image) {
        if (image == null) {
            return null;
        }
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        byte[] output = new byte[width*height*4];
        for (int i = 0; i < width*height; i++) {
            int x = i % width;
            int y = i / width;
            
            int argb = image.getRGB(x, y);
            
            output[(i * 4) + 0] = (byte) (argb >>> 16);
            output[(i * 4) + 1] = (byte) (argb >>> 8);
            output[(i * 4) + 2] = (byte) (argb >>> 0);
            output[(i * 4) + 3] = (byte) (argb >>> 24);
        }
        
        return output;
    }
    
    private Base64Image() {
        
    }
    
}
