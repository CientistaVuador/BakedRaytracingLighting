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

/**
 *
 * @author Cien
 */
public class GaussianBlur {

    public static class GaussianColor {

        public float r;
        public float g;
        public float b;
    }

    public static interface GaussianIO {

        public int width();

        public int height();

        public boolean outOfBounds(int x, int y);

        public default boolean ignore(int x, int y) {
            return false;
        }

        public void write(int x, int y, GaussianColor color);

        public void read(int x, int y, GaussianColor color);
    }

    public static void blur(GaussianIO io, int kernelSize, float area) {
        new GaussianBlur(io, kernelSize, area).process();
    }

    private final GaussianIO io;
    private final int kernelSize;

    private final float[] gaussianWeights;

    private GaussianBlur(GaussianIO io, int kernelSize, float area) {
        this.io = io;
        this.kernelSize = kernelSize;
        float sum = 0f;
        float inverseArea = 1f / area;
        this.gaussianWeights = new float[this.kernelSize];
        for (int x = 0; x < this.kernelSize; x++) {
            float xValue = (x - (this.kernelSize / 2));
            xValue *= inverseArea;
            float weight = (float) Math.exp(-(xValue * xValue));
            this.gaussianWeights[x] = weight;
            sum += weight;
        }
        for (int i = 0; i < this.gaussianWeights.length; i++) {
            this.gaussianWeights[i] /= sum;
        }
    }

    public void process() {
        GaussianColor readGaussian = new GaussianColor();

        float[] colorMap = new float[(this.io.height() * this.io.width()) * 3];

        for (int y = 0; y < this.io.height(); y++) {
            for (int x = 0; x < this.io.width(); x++) {

                if (this.io.outOfBounds(x, y)) {
                    continue;
                }

                if (this.io.ignore(x, y)) {
                    this.io.read(x, y, readGaussian);
                    colorMap[0 + (x * 3) + (y * this.io.width() * 3)] = readGaussian.r;
                    colorMap[1 + (x * 3) + (y * this.io.width() * 3)] = readGaussian.g;
                    colorMap[2 + (x * 3) + (y * this.io.width() * 3)] = readGaussian.b;
                    continue;
                }

                float r = 0f;
                float g = 0f;
                float b = 0f;
                float weightSum = 0f;
                for (int kernelX = 0; kernelX < this.kernelSize; kernelX++) {
                    int pX = x + (kernelX - (this.kernelSize / 2));

                    if (this.io.outOfBounds(pX, y)) {
                        continue;
                    }
                    
                    if (this.io.ignore(pX, y)) {
                        continue;
                    }

                    this.io.read(pX, y, readGaussian);

                    float weight = this.gaussianWeights[kernelX];
                    r += (readGaussian.r * weight);
                    g += (readGaussian.g * weight);
                    b += (readGaussian.b * weight);
                    weightSum += weight;
                }
                float inverseWeightSum = 1f / weightSum;
                r *= inverseWeightSum;
                g *= inverseWeightSum;
                b *= inverseWeightSum;

                colorMap[0 + (x * 3) + (y * this.io.width() * 3)] = r;
                colorMap[1 + (x * 3) + (y * this.io.width() * 3)] = g;
                colorMap[2 + (x * 3) + (y * this.io.width() * 3)] = b;
            }
        }

        GaussianColor outGaussian = new GaussianColor();

        for (int y = 0; y < this.io.height(); y++) {
            for (int x = 0; x < this.io.width(); x++) {

                if (this.io.outOfBounds(x, y)) {
                    continue;
                }
                
                if (this.io.ignore(x, y)) {
                    outGaussian.r = colorMap[0 + (x * 3) + (y * this.io.width() * 3)];
                    outGaussian.g = colorMap[1 + (x * 3) + (y * this.io.width() * 3)];
                    outGaussian.b = colorMap[2 + (x * 3) + (y * this.io.width() * 3)];
                    this.io.write(x, y, outGaussian);
                    continue;
                }

                float r = 0f;
                float g = 0f;
                float b = 0f;
                float weightSum = 0f;
                for (int kernelY = 0; kernelY < this.kernelSize; kernelY++) {
                    int pY = y + (kernelY - (this.kernelSize / 2));

                    if (this.io.outOfBounds(x, pY)) {
                        continue;
                    }
                    
                    if (this.io.ignore(x, pY)) {
                        continue;
                    }
                    
                    float readR = colorMap[0 + (x * 3) + (pY * this.io.width() * 3)];
                    float readG = colorMap[1 + (x * 3) + (pY * this.io.width() * 3)];
                    float readB = colorMap[2 + (x * 3) + (pY * this.io.width() * 3)];

                    float weight = this.gaussianWeights[kernelY];
                    r += (readR * weight);
                    g += (readG * weight);
                    b += (readB * weight);
                    weightSum += weight;
                }
                float inverseWeightSum = 1f / weightSum;
                r *= inverseWeightSum;
                g *= inverseWeightSum;
                b *= inverseWeightSum;

                outGaussian.r = r;
                outGaussian.g = g;
                outGaussian.b = b;
                this.io.write(x, y, outGaussian);
            }
        }
    }
}
