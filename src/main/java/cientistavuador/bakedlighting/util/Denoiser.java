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

import java.util.Arrays;

/**
 *
 * @author Cien
 */
public class Denoiser {

    public static class DenoiserColor {

        private boolean outOfBounds;
        public float r;
        public float g;
        public float b;
    }

    public static interface DenoiserIO {

        public int width();

        public int height();

        public boolean outOfBounds(int x, int y);

        public void write(int x, int y, DenoiserColor color);

        public void read(int x, int y, DenoiserColor color);
    }

    public static void denoise(DenoiserIO io, int kernelSize, boolean averageSimilar, int similarSearchKernelSize, float similarTolerance) {
        new Denoiser(io, kernelSize, averageSimilar, similarSearchKernelSize, similarTolerance).process();
    }

    private final DenoiserIO io;
    private final int kernelSize;
    private final boolean averageSimilar;
    private final int similarSearchKernelSize;
    private final float similarTolerance;

    private Denoiser(DenoiserIO io, int kernelSize, boolean averageSimilar, int similarSearchKernelSize, float similarTolerance) {
        this.io = io;
        this.kernelSize = kernelSize;
        this.averageSimilar = averageSimilar;
        this.similarSearchKernelSize = similarSearchKernelSize;
        this.similarTolerance = similarTolerance;
    }

    public float luminance(DenoiserColor color) {
        if (color.outOfBounds) {
            return -1f;
        }
        return (0.299f * color.r) + (0.587f * color.g) + (0.114f * color.b);
    }

    public int fillKernel(DenoiserColor[] pixels, int xCenter, int yCenter) {
        int filled = 0;
        for (int y = 0; y < this.kernelSize; y++) {
            for (int x = 0; x < this.kernelSize; x++) {
                int pX = (x + xCenter) - (this.kernelSize / 2);
                int pY = (y + yCenter) - (this.kernelSize / 2);

                DenoiserColor kernelColor = pixels[x + (y * this.kernelSize)];

                if (this.io.outOfBounds(pX, pY)) {
                    kernelColor.outOfBounds = true;
                    kernelColor.r = -1f;
                    kernelColor.g = -1f;
                    kernelColor.b = -1f;
                    continue;
                }

                this.io.read(pX, pY, kernelColor);
                kernelColor.outOfBounds = false;

                filled++;
            }
        }
        return filled;
    }

    public void sortKernelByLuminance(DenoiserColor[] pixels) {
        Arrays.sort(pixels, (o1, o2) -> {
            float o1Luminance = luminance(o1);
            float o2Luminance = luminance(o2);
            return Float.compare(o1Luminance, o2Luminance);
        });
    }

    public boolean compare(DenoiserColor a, DenoiserColor b) {
        float dR = Math.abs(a.r - b.r);
        float dG = Math.abs(a.g - b.g);
        float dB = Math.abs(a.b - b.b);
        return (dR < this.similarTolerance) && (dG < this.similarTolerance) && (dB < this.similarTolerance);
    }

    public void averageSimilar(DenoiserColor toFindSimilar, int xCenter, int yCenter, DenoiserColor outAverage) {
        float r = toFindSimilar.r;
        float g = toFindSimilar.g;
        float b = toFindSimilar.b;
        int count = 1;
        for (int y = 0; y < this.similarSearchKernelSize; y++) {
            for (int x = 0; x < this.similarSearchKernelSize; x++) {
                int pX = (x + xCenter) - (this.similarSearchKernelSize / 2);
                int pY = (y + yCenter) - (this.similarSearchKernelSize / 2);

                if (pX == xCenter && pY == yCenter) {
                    continue;
                }

                if (this.io.outOfBounds(pX, pY)) {
                    continue;
                }

                this.io.read(pX, pY, outAverage);

                if (compare(toFindSimilar, outAverage)) {
                    r += outAverage.r;
                    g += outAverage.g;
                    b += outAverage.b;
                    count++;
                }
            }
        }
        float invcount = 1f / count;
        r *= invcount;
        g *= invcount;
        b *= invcount;
        outAverage.r = r;
        outAverage.g = g;
        outAverage.b = b;
    }

    public void process() {
        DenoiserColor outAverage = new DenoiserColor();

        DenoiserColor[] kernel = new DenoiserColor[this.kernelSize * this.kernelSize];
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] = new DenoiserColor();
        }

        for (int y = 0; y < this.io.height(); y++) {
            for (int x = 0; x < this.io.width(); x++) {

                if (this.io.outOfBounds(x, y)) {
                    continue;
                }

                int filled = fillKernel(kernel, x, y);
                sortKernelByLuminance(kernel);

                int filledIndex = 0;
                DenoiserColor median = null;
                for (int i = 0; i < kernel.length; i++) {
                    DenoiserColor color = kernel[i];
                    if (!color.outOfBounds) {
                        if (filledIndex == (filled / 2)) {
                            median = color;
                            break;
                        }
                        filledIndex++;
                    }
                }

                if (median == null) {
                    continue;
                }

                if (this.averageSimilar) {
                    averageSimilar(median, x, y, outAverage);
                } else {
                    outAverage.r = median.r;
                    outAverage.g = median.g;
                    outAverage.b = median.b;
                }

                this.io.write(x, y, outAverage);
            }
        }
    }
}
