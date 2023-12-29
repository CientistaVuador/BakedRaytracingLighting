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
public class MarginAutomata {

    public static class MarginAutomataColor {

        public float r;
        public float g;
        public float b;
    }

    public static interface MarginAutomataIO {

        public int width();

        public int height();

        public boolean outOfBounds(int x, int y);

        public boolean empty(int x, int y);

        public void read(int x, int y, MarginAutomataColor color);

        public void write(int x, int y, MarginAutomataColor color);

        public default void writeEmptyPixel(int x, int y) {

        }
    }

    public static void generateMargin(MarginAutomataIO io, int iterations) {
        new MarginAutomata(io, iterations).process();
    }

    private static final int[] NEIGHBORS_POSITIONS = new int[]{
        0, 1, //top
        0, -1, //bottom
        -1, 0, //left
        1, 0, //right
    };
    private static final int NEIGHBORS = NEIGHBORS_POSITIONS.length / 2;

    private static final int[] NEIGHBORS_POSITIONS_ROTATED = new int[]{
        1, 1, //top-right
        -1, 1, //top-left
        1, -1, //bottom-right
        -1, -1 //bottom-left
    };
    private static final int NEIGHBORS_ROTATED = NEIGHBORS_POSITIONS_ROTATED.length / 2;

    private final MarginAutomataIO io;
    private final int iterations;

    private int width;
    private int height;

    private float[] colorMap = null;
    private boolean[] emptyMap = null;

    private float[] nextColorMap = null;
    private boolean[] nextEmptyMap = null;

    private MarginAutomata(MarginAutomataIO io, int iterations) {
        this.io = io;
        if (iterations < 0) {
            iterations = Integer.MAX_VALUE;
        }
        this.iterations = iterations;
    }

    public void load() {
        this.width = this.io.width();
        this.height = this.io.height();

        this.colorMap = new float[this.width * this.height * 3];
        this.emptyMap = new boolean[this.width * this.height];

        this.nextColorMap = new float[this.width * this.height * 3];
        this.nextEmptyMap = new boolean[this.width * this.height];

        MarginAutomataColor color = new MarginAutomataColor();

        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                if (this.io.outOfBounds(x, y)) {
                    continue;
                }

                if (this.io.empty(x, y)) {
                    this.emptyMap[x + (y * this.width)] = true;
                    continue;
                }

                this.io.read(x, y, color);

                int colorIndex = (x * 3) + (y * this.width * 3);
                this.colorMap[colorIndex + 0] = color.r;
                this.colorMap[colorIndex + 1] = color.g;
                this.colorMap[colorIndex + 2] = color.b;
            }
        }
    }

    public boolean iterate() {
        boolean finished = true;
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                if (this.io.outOfBounds(x, y)) {
                    continue;
                }

                int emptyIndex = x + (y * this.width);
                int colorIndex = (x * 3) + (y * this.width * 3);

                if (!this.emptyMap[emptyIndex]) {
                    System.arraycopy(
                            this.colorMap,
                            colorIndex,
                            this.nextColorMap,
                            colorIndex,
                            3
                    );
                    this.nextEmptyMap[emptyIndex] = false;
                    continue;
                }

                float r = 0f;
                float g = 0f;
                float b = 0f;
                int numSamples = 0;
                for (int s = 0; s < NEIGHBORS; s++) {
                    int xOffset = NEIGHBORS_POSITIONS[(s * 2) + 0];
                    int yOffset = NEIGHBORS_POSITIONS[(s * 2) + 1];

                    int sX = x + xOffset;
                    int sY = y + yOffset;

                    if (this.io.outOfBounds(sX, sY)) {
                        continue;
                    }

                    if (this.emptyMap[sX + (sY * this.width)]) {
                        continue;
                    }

                    int sampleColorIndex = (sX * 3) + (sY * this.width * 3);
                    r += this.colorMap[sampleColorIndex + 0];
                    g += this.colorMap[sampleColorIndex + 1];
                    b += this.colorMap[sampleColorIndex + 2];
                    numSamples++;
                }

                if (numSamples == 0) {
                    for (int s = 0; s < NEIGHBORS_ROTATED; s++) {
                        int xOffset = NEIGHBORS_POSITIONS_ROTATED[(s * 2) + 0];
                        int yOffset = NEIGHBORS_POSITIONS_ROTATED[(s * 2) + 1];

                        int sX = x + xOffset;
                        int sY = y + yOffset;

                        if (this.io.outOfBounds(sX, sY)) {
                            continue;
                        }

                        if (this.emptyMap[sX + (sY * this.width)]) {
                            continue;
                        }

                        int sampleColorIndex = (sX * 3) + (sY * this.width * 3);
                        r += this.colorMap[sampleColorIndex + 0];
                        g += this.colorMap[sampleColorIndex + 1];
                        b += this.colorMap[sampleColorIndex + 2];
                        numSamples++;
                    }
                }
                
                if (numSamples == 0) {
                    this.nextEmptyMap[emptyIndex] = true;
                    continue;
                }

                float invNumSamples = 1f / numSamples;
                r *= invNumSamples;
                g *= invNumSamples;
                b *= invNumSamples;

                this.nextColorMap[colorIndex + 0] = r;
                this.nextColorMap[colorIndex + 1] = g;
                this.nextColorMap[colorIndex + 2] = b;
                this.nextEmptyMap[emptyIndex] = false;
                
                finished = false;
            }
        }
        return finished;
    }

    public void flipMaps() {
        float[] currentColor = this.colorMap;
        boolean[] currentEmpty = this.emptyMap;
        this.colorMap = this.nextColorMap;
        this.emptyMap = this.nextEmptyMap;
        this.nextColorMap = currentColor;
        this.nextEmptyMap = currentEmpty;
    }

    public void output() {
        MarginAutomataColor output = new MarginAutomataColor();

        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                if (this.io.outOfBounds(x, y)) {
                    continue;
                }

                if (this.emptyMap[x + (y * this.width)]) {
                    this.io.writeEmptyPixel(x, y);
                    continue;
                }

                int colorIndex = (x * 3) + (y * this.width * 3);
                output.r = this.colorMap[colorIndex + 0];
                output.g = this.colorMap[colorIndex + 1];
                output.b = this.colorMap[colorIndex + 2];
                this.io.write(x, y, output);
            }
        }
    }

    public void process() {
        load();
        for (int i = 0; i < this.iterations; i++) {
            if (iterate()) {
                break;
            }
            flipMaps();
        }
        output();
    }
}

