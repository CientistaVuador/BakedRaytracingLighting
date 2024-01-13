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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

/**
 *
 * @author Cien
 */
public class SamplingModeLoader {
    protected static final Map<String, float[]> SAMPLES = new HashMap<>();
    protected static final Map<String, BufferedImage> SAMPLES_IMAGES = new HashMap<>();
    
    static {
        try {
            try (ZipInputStream zipRead = new ZipInputStream(
                    SamplingModeLoader.class.getResourceAsStream("SamplingModes.zip"),
                    StandardCharsets.UTF_8
            )) {
                ZipEntry entry;
                while ((entry = zipRead.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    BufferedImage image = ImageIO.read(zipRead);
                    float[] samples = load(image);
                    SAMPLES.put(entry.getName(), samples);
                    SAMPLES_IMAGES.put(entry.getName(), image);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    
    private static float[] load(BufferedImage image) {
        int gridWidth = image.getWidth();
        int gridHeight = image.getHeight();
        
        float[] samplePositions = new float[64];
        int samplePositionsIndex = 0;
        
        for (int y = 0; y < image.getWidth(); y++) {
            for (int x = 0; x < image.getHeight(); x++) {
                int argb = image.getRGB(x, y);
                
                float inv = 1f / 255f;
                float red = ((argb >> 16) & 0xFF) * inv;
                float green = ((argb >> 8) & 0xFF) * inv;
                float blue = ((argb >> 0) & 0xFF) * inv;
                
                float luminance = (red + green + blue) / 3f;
                
                if (luminance < 0.5f) {
                    float sampleX = x + 0.5f;
                    float sampleY = ((gridHeight - 1) - y) + 0.5f;
                    sampleX /= gridWidth;
                    sampleY /= gridHeight;
                    
                    if ((samplePositionsIndex + 2) > samplePositions.length) {
                        samplePositions = Arrays.copyOf(samplePositions, samplePositions.length * 2);
                    }
                    samplePositions[samplePositionsIndex + 0] = sampleX;
                    samplePositions[samplePositionsIndex + 1] = sampleY;
                    samplePositionsIndex += 2;
                }
            }
        }
        
        return Arrays.copyOf(samplePositions, samplePositionsIndex);
    }
    
    private SamplingModeLoader() {
        
    }
    
}
