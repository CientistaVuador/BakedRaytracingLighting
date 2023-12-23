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

    public static void denoise(DenoiserIO io, int kernelSize, boolean averageSimilar, int similarSearchKernelSize, float similarTolerance, float sharpnessTolerance, boolean useGaussianWeights) {
        new Denoiser(io, kernelSize, averageSimilar, similarSearchKernelSize, similarTolerance, sharpnessTolerance, useGaussianWeights).process();
    }

    private final DenoiserIO io;
    private final int kernelSize;
    private final boolean averageSimilar;
    private final int similarSearchKernelSize;
    private final float similarTolerance;
    private final float sharpnessTolerance;
    private final boolean useGaussianWeights;

    private final float[] similarSearchGaussian;
    
    private Denoiser(DenoiserIO io, int kernelSize, boolean averageSimilar, int similarSearchKernelSize, float similarTolerance, float sharpnessTolerance, boolean useGaussianWeights) {
        this.io = io;
        this.kernelSize = kernelSize;
        this.averageSimilar = averageSimilar;
        this.similarSearchKernelSize = similarSearchKernelSize;
        this.similarTolerance = similarTolerance;
        this.sharpnessTolerance = sharpnessTolerance;
        this.useGaussianWeights = useGaussianWeights;
        this.similarSearchGaussian = new float[similarSearchKernelSize * similarSearchKernelSize];
        float sum = 0f;
        for (int y = 0; y < this.similarSearchKernelSize; y++) {
            for (int x = 0; x < this.similarSearchKernelSize; x++) {
                float xValue = (x - (this.similarSearchKernelSize / 2));
                float yValue = (y - (this.similarSearchKernelSize / 2));
                float weight = (float) Math.exp(-((xValue * xValue) + (yValue * yValue)));
                this.similarSearchGaussian[x + (y * this.similarSearchKernelSize)] = weight;
                sum += weight;
            }
        }
        for (int i = 0; i < this.similarSearchGaussian.length; i++) {
            this.similarSearchGaussian[i] /= sum;
        }
    }
    
    public void fillKernel(DenoiserColor[] pixels, int xCenter, int yCenter) {
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
            }
        }
    }

    public DenoiserColor findMedian(DenoiserColor[] pixels) {
        int filled = 0;
        for (int i = 0; i < pixels.length; i++) {
            DenoiserColor color = pixels[i];
            if (!color.outOfBounds) {
                filled++;
            }
        }
        
        if (filled == 0) {
            return null;
        }
        
        float[] redChannel = new float[filled];
        float[] greenChannel = new float[filled];
        float[] blueChannel = new float[filled];
        
        int filledIndex = 0;
        for (int i = 0; i < pixels.length; i++) {
            DenoiserColor color = pixels[i];
            if (!color.outOfBounds) {
                redChannel[filledIndex] = color.r;
                greenChannel[filledIndex] = color.g;
                blueChannel[filledIndex] = color.b;
                filledIndex++;
            }
        }
        
        Arrays.sort(redChannel);
        Arrays.sort(greenChannel);
        Arrays.sort(blueChannel);
        
        float medianR = redChannel[redChannel.length / 2];
        float medianG = greenChannel[greenChannel.length / 2];
        float medianB = blueChannel[blueChannel.length / 2];
        
        DenoiserColor median = pixels[pixels.length / 2];
        
        median.r = medianR;
        median.g = medianG;
        median.b = medianB;
        
        return median;
    }
    
    public boolean isSimilar(DenoiserColor a, DenoiserColor b, float tolerance) {
        return Math.sqrt(Math.pow(a.r - b.r, 2.0) + Math.pow(a.g - b.g, 2.0) + Math.pow(a.b - b.b, 2.0)) < tolerance;
    }

    public void averageSimilar(DenoiserColor toFindSimilar, int xCenter, int yCenter, DenoiserColor outAverage) {
        float r = 0f;
        float g = 0f;
        float b = 0f;
        float weightSum = 0f;
        for (int y = 0; y < this.similarSearchKernelSize; y++) {
            for (int x = 0; x < this.similarSearchKernelSize; x++) {
                int pX = (x + xCenter) - (this.similarSearchKernelSize / 2);
                int pY = (y + yCenter) - (this.similarSearchKernelSize / 2);

                if (this.io.outOfBounds(pX, pY)) {
                    continue;
                }

                this.io.read(pX, pY, outAverage);

                if (isSimilar(toFindSimilar, outAverage, this.similarTolerance)) {
                    float weight;
                    if (this.useGaussianWeights) {
                        weight = this.similarSearchGaussian[x + (y * this.similarSearchKernelSize)];
                    } else {
                        weight = 1f;
                    }
                    
                    r += (outAverage.r * weight);
                    g += (outAverage.g * weight);
                    b += (outAverage.b * weight);
                    
                    weightSum += weight;
                }
            }
        }
        float inverseWeight = 1f / weightSum;
        if (Float.isFinite(inverseWeight)) {
            outAverage.r = r * inverseWeight;
            outAverage.g = g * inverseWeight;
            outAverage.b = b * inverseWeight;
        } else {
            outAverage.r = toFindSimilar.r;
            outAverage.g = toFindSimilar.g;
            outAverage.b = toFindSimilar.b;
        }
    }

    public void process() {
        DenoiserColor outAverage = new DenoiserColor();

        DenoiserColor[] kernel = new DenoiserColor[this.kernelSize * this.kernelSize];
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] = new DenoiserColor();
        }
        
        DenoiserColor currentColor = new DenoiserColor();

        for (int y = 0; y < this.io.height(); y++) {
            for (int x = 0; x < this.io.width(); x++) {

                if (this.io.outOfBounds(x, y)) {
                    continue;
                }

                fillKernel(kernel, x, y);
                DenoiserColor median = findMedian(kernel);
                
                if (median == null) {
                    continue;
                }
                
                if (this.averageSimilar) {
                    this.io.read(x, y, currentColor);
                    if (isSimilar(median, currentColor, this.sharpnessTolerance)) {
                        outAverage.r = currentColor.r;
                        outAverage.g = currentColor.g;
                        outAverage.b = currentColor.b;
                    } else {
                        averageSimilar(median, x, y, outAverage);
                    }
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
