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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 *
 * @author Cien
 */
public class SoftwareRenderer {

    //camera position, world position, uv, world normal, color
    private static final int VERTEX_SIZE = 4 + 3 + 2 + 3 + 4;

    private static final int CX = 0;
    private static final int CY = 1;
    private static final int CZ = 2;
    private static final int CW_INV = 3;
    private static final int CW = CW_INV;
    private static final int X = 4;
    private static final int Y = 5;
    private static final int Z = 6;
    private static final int U = 7;
    private static final int V = 8;
    private static final int NX = 9;
    private static final int NY = 10;
    private static final int NZ = 11;
    private static final int R = 12;
    private static final int G = 13;
    private static final int B = 14;
    private static final int A = 15;

    //texture
    public static interface Texture {

        public int width();

        public int height();

        public default void sampleNearest(float x, float y, float[] result, int offset) {
            int width = width();
            int height = height();
            int pX = (((int) Math.floor(Math.abs(x) * width)) % width);
            int pY = (((int) Math.floor(Math.abs(y) * height)) % height);
            fetch(pX, pY, result, offset);
        }

        public default void sampleBilinear(float x, float y, float[] result, int offset) {
            int width = width();
            int height = height();

            float pX = Math.abs((x * width) - 0.5f);
            float pY = Math.abs((y * height) - 0.5f);

            int bottomLeftX = (int) Math.floor(pX);
            int bottomLeftY = (int) Math.floor(pY);

            float weightX = pX - bottomLeftX;
            float weightY = pY - bottomLeftY;

            fetch(bottomLeftX % width, bottomLeftY % height, result, offset);
            float bottomLeftR = result[offset + 0];
            float bottomLeftG = result[offset + 1];
            float bottomLeftB = result[offset + 2];
            float bottomLeftA = result[offset + 3];

            int bottomRightX = (int) Math.ceil(pX);
            int bottomRightY = (int) Math.floor(pY);

            fetch(bottomRightX % width, bottomRightY % height, result, offset);
            float bottomRightR = result[offset + 0];
            float bottomRightG = result[offset + 1];
            float bottomRightB = result[offset + 2];
            float bottomRightA = result[offset + 3];

            int topLeftX = (int) Math.floor(pX);
            int topLeftY = (int) Math.ceil(pY);

            fetch(topLeftX % width, topLeftY % height, result, offset);
            float topLeftR = result[offset + 0];
            float topLeftG = result[offset + 1];
            float topLeftB = result[offset + 2];
            float topLeftA = result[offset + 3];

            int topRightX = (int) Math.ceil(pX);
            int topRightY = (int) Math.ceil(pY);

            fetch(topRightX % width, topRightY % height, result, offset);
            float topRightR = result[offset + 0];
            float topRightG = result[offset + 1];
            float topRightB = result[offset + 2];
            float topRightA = result[offset + 3];

            result[offset + 0] = (bottomLeftR * (1f - weightX) * (1f - weightY)) + (bottomRightR * weightX * (1f - weightY)) + (topLeftR * (1f - weightX) * weightY) + (topRightR * weightX * weightY);
            result[offset + 1] = (bottomLeftG * (1f - weightX) * (1f - weightY)) + (bottomRightG * weightX * (1f - weightY)) + (topLeftG * (1f - weightX) * weightY) + (topRightG * weightX * weightY);
            result[offset + 2] = (bottomLeftB * (1f - weightX) * (1f - weightY)) + (bottomRightB * weightX * (1f - weightY)) + (topLeftB * (1f - weightX) * weightY) + (topRightB * weightX * weightY);
            result[offset + 3] = (bottomLeftA * (1f - weightX) * (1f - weightY)) + (bottomRightA * weightX * (1f - weightY)) + (topLeftA * (1f - weightX) * weightY) + (topRightA * weightX * weightY);
        }

        public void fetch(int x, int y, float[] result, int offset);
    }

    //surface
    public class Surface {

        public static final int DEFAULT_WIDTH = 200;
        public static final int DEFAULT_HEIGHT = 150;

        private final int width;
        private final int height;

        private final float[] colorBuffer;
        private final float[] depthBuffer;

        private final Texture colorBufferTexture;
        private final Texture depthBufferTexture;

        public Surface(int width, int height) {
            this.width = width;
            this.height = height;
            this.colorBuffer = new float[width * height * 4];
            this.depthBuffer = new float[width * height];
            this.colorBufferTexture = new Texture() {
                @Override
                public int width() {
                    return Surface.this.width;
                }

                @Override
                public int height() {
                    return Surface.this.height;
                }

                @Override
                public void fetch(int x, int y, float[] result, int offset) {
                    result[offset + 0] = Surface.this.colorBuffer[((x + (y * width())) * 4) + 0];
                    result[offset + 1] = Surface.this.colorBuffer[((x + (y * width())) * 4) + 1];
                    result[offset + 2] = Surface.this.colorBuffer[((x + (y * width())) * 4) + 2];
                    result[offset + 3] = Surface.this.colorBuffer[((x + (y * width())) * 4) + 3];
                }
            };
            this.depthBufferTexture = new Texture() {
                @Override
                public int width() {
                    return Surface.this.width;
                }

                @Override
                public int height() {
                    return Surface.this.height;
                }

                @Override
                public void fetch(int x, int y, float[] result, int offset) {
                    float depth = Surface.this.depthBuffer[x + (y * width())];
                    result[offset + 0] = depth;
                    result[offset + 1] = depth;
                    result[offset + 2] = depth;
                    result[offset + 3] = 1f;
                }
            };
        }

        public Surface() {
            this(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public Texture getColorBufferTexture() {
            return colorBufferTexture;
        }

        public Texture getDepthBufferTexture() {
            return depthBufferTexture;
        }

        public void setColor(int x, int y, float[] rgba) {
            this.colorBuffer[((x + (y * getWidth())) * 4) + 0] = rgba[0];
            this.colorBuffer[((x + (y * getWidth())) * 4) + 1] = rgba[1];
            this.colorBuffer[((x + (y * getWidth())) * 4) + 2] = rgba[2];
            this.colorBuffer[((x + (y * getWidth())) * 4) + 2] = rgba[3];
        }

        public void setColor(int x, int y, float[] rgbaArray, int offset, int length) {
            System.arraycopy(rgbaArray, offset, this.colorBuffer, (x + (y * getWidth())) * 4, length);
        }

        public void setDepth(int x, int y, float depth) {
            this.depthBuffer[x + (y * getWidth())] = depth;
        }

        public void setDepth(int x, int y, float[] depthArray, int offset, int length) {
            System.arraycopy(depthArray, offset, this.depthBuffer, x + (y * getWidth()), length);
        }

        public void getColor(int x, int y, float[] rgba) {
            rgba[0] = this.colorBuffer[((x + (y * getWidth())) * 4) + 0];
            rgba[1] = this.colorBuffer[((x + (y * getWidth())) * 4) + 1];
            rgba[2] = this.colorBuffer[((x + (y * getWidth())) * 4) + 2];
            rgba[3] = this.colorBuffer[((x + (y * getWidth())) * 4) + 3];
        }

        public void getColor(int x, int y, float[] rgbaArray, int offset, int length) {
            System.arraycopy(this.colorBuffer, (x + (y * getWidth())) * 4, rgbaArray, offset, length);
        }

        public float getDepth(int x, int y) {
            return this.depthBuffer[x + (y * getWidth())];
        }

        public void getDepth(int x, int y, float[] depthArray, int offset, int length) {
            System.arraycopy(this.depthBuffer, x + (y * getWidth()), depthArray, offset, length);
        }

        public void clearColor(float r, float g, float b, float a) {
            for (int x = 0; x < getWidth(); x++) {
                for (int y = 0; y < getHeight(); y++) {
                    this.colorBuffer[((x + (y * getWidth())) * 4) + 0] = r;
                    this.colorBuffer[((x + (y * getWidth())) * 4) + 1] = g;
                    this.colorBuffer[((x + (y * getWidth())) * 4) + 2] = b;
                    this.colorBuffer[((x + (y * getWidth())) * 4) + 3] = a;
                }
            }
        }

        public void clearDepth(float depth) {
            Arrays.fill(this.depthBuffer, depth);
        }
    }

    //awt interop
    public static Texture wrapImageToTexture(BufferedImage image) {
        return new Texture() {
            private final BufferedImage wrapped = image;

            @Override
            public int width() {
                return this.wrapped.getWidth();
            }

            @Override
            public int height() {
                return this.wrapped.getHeight();
            }

            @Override
            public void fetch(int x, int y, float[] result, int offset) {
                int pixel = this.wrapped.getRGB(x, (height() - 1) - y);
                result[offset + 0] = ((pixel >> 16) & 0xFF) / 255f;
                result[offset + 1] = ((pixel >> 8) & 0xFF) / 255f;
                result[offset + 2] = ((pixel >> 0) & 0xFF) / 255f;
                result[offset + 3] = ((pixel >> 24) & 0xFF) / 255f;
            }
        };
    }

    public static Texture imageTo16BitsTexture(BufferedImage image) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int[] bufferedData = image.getRGB(0, 0, width, height, null, 0, width);
        final short[] pixelData = new short[width * height];

        for (int i = 0; i < width * height; i++) {
            int x = i % width;
            int y = i / width;
            int imagePixel = bufferedData[x + (((height - 1) - y) * width)];
            int rBits = (int) ((((imagePixel >> 16) & 0xFF) / 255f) * 15f);
            int gBits = (int) ((((imagePixel >> 8) & 0xFF) / 255f) * 15f);
            int bBits = (int) ((((imagePixel >> 0) & 0xFF) / 255f) * 15f);
            int aBits = (int) ((((imagePixel >> 24) & 0xFF) / 255f) * 15f);
            pixelData[i] = (short) ((rBits << 12) | (gBits << 8) | (bBits << 4) | (aBits << 0));
        }

        return new Texture() {
            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }

            @Override
            public void fetch(int x, int y, float[] result, int offset) {
                int pixel = pixelData[x + (y * width())] & 0xFFFF;
                float r = ((pixel >> 12) & 0x0F) / 15f;
                float g = ((pixel >> 8) & 0x0F) / 15f;
                float b = ((pixel >> 4) & 0x0F) / 15f;
                float a = ((pixel >> 0) & 0x0F) / 15f;
                result[offset + 0] = r;
                result[offset + 1] = g;
                result[offset + 2] = b;
                result[offset + 3] = a;
            }
        };
    }

    public static Texture imageTo256ColorsTexture(BufferedImage image) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int[] bufferedData = image.getRGB(0, 0, width, height, null, 0, width);
        final byte[] pixelData = new byte[width * height];

        for (int i = 0; i < width * height; i++) {
            int x = i % width;
            int y = i / width;
            int imagePixel = bufferedData[x + (((height - 1) - y) * width)];
            float alpha = ((imagePixel >> 24) & 0xFF) / 255f;
            int rBits = (int) ((((imagePixel >> 16) & 0xFF) / 255f) * 7f);
            int gBits = (int) ((((imagePixel >> 8) & 0xFF) / 255f) * 7f);
            int bBits = (int) ((((imagePixel >> 0) & 0xFF) / 255f) * 3f);
            if (rBits == 0 && gBits == 0 && bBits == 0) {
                rBits = 2;
                gBits = 2;
                bBits = 1;
            }
            if (alpha < 0.5f) {
                rBits = 0;
                gBits = 0;
                bBits = 0;
            }
            pixelData[i] = (byte) ((rBits << 5) | (gBits << 2) | (bBits << 0));
        }

        return new Texture() {
            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }

            @Override
            public void fetch(int x, int y, float[] result, int offset) {
                int pixel = pixelData[x + (y * width())] & 0xFF;
                float r = ((pixel >> 5) & 0x07) / 7f;
                float g = ((pixel >> 2) & 0x07) / 7f;
                float b = ((pixel >> 0) & 0x03) / 3f;
                float a = (r == 0f && g == 0f && b == 0f ? 0f : 1f);
                result[offset + 0] = r;
                result[offset + 1] = g;
                result[offset + 2] = b;
                result[offset + 3] = a;
            }
        };
    }

    public static Texture imageToTexture(BufferedImage image) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int[] bufferedData = image.getRGB(0, 0, width, height, null, 0, width);
        final float[] pixelData = new float[width * height * 4];

        for (int i = 0; i < width * height; i++) {
            int x = i % width;
            int y = i / width;

            int pixel = bufferedData[x + (((height - 1) - y) * width)];

            pixelData[((x + (y * width)) * 4) + 0] = ((pixel >> 16) & 0xFF) / 255f;
            pixelData[((x + (y * width)) * 4) + 1] = ((pixel >> 8) & 0xFF) / 255f;
            pixelData[((x + (y * width)) * 4) + 2] = ((pixel >> 0) & 0xFF) / 255f;
            pixelData[((x + (y * width)) * 4) + 3] = ((pixel >> 24) & 0xFF) / 255f;
        }

        return new Texture() {
            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }

            @Override
            public void fetch(int x, int y, float[] result, int offset) {
                System.arraycopy(pixelData, (x + (y * width)) * 4, result, offset, 4);
            }
        };
    }

    public static BufferedImage textureToImage(Texture t) {
        if (t == null) {
            throw new NullPointerException("Texture is null.");
        }

        int width = t.width();
        int height = t.height();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        float[] cache = new float[4];
        int[] intCache = new int[4];
        int[] data = new int[width * height];

        for (int i = 0; i < width * height; i++) {
            int x = i % width;
            int y = i / width;

            t.fetch(x, (height - y) - 1, cache, 0);
            for (int j = 0; j < intCache.length; j++) {
                intCache[j] = Math.min((int) (cache[j] * 255f), 255);
            }
            data[i] = (intCache[3] << 24) | (intCache[0] << 16) | (intCache[1] << 8) | (intCache[2] << 0);
        }

        image.setRGB(0, 0, width, height, data, 0, width);

        return image;
    }

    //vertex builder
    public static class MeshBuilder {

        //local position, uv, normal, color
        private static final int LOCAL_VERTEX_SIZE = 3 + 2 + 3 + 4;

        public static final int VERTEX_SIZE = LOCAL_VERTEX_SIZE;

        public static final int POS_X = 0;
        public static final int POS_Y = 1;
        public static final int POS_Z = 2;
        public static final int TEX_U = 3;
        public static final int TEX_V = 4;
        public static final int NRM_X = 5;
        public static final int NRM_Y = 6;
        public static final int NRM_Z = 7;
        public static final int CLR_R = 8;
        public static final int CLR_G = 9;
        public static final int CLR_B = 10;
        public static final int CLR_A = 11;

        private float[] positions = new float[64];
        private int positionsIndex = 0;

        private float[] uvs = new float[64];
        private int uvsIndex = 0;

        private float[] normals = new float[64];
        private int normalsIndex = 0;

        private float[] colors = new float[64];
        private int colorsIndex = 0;

        private float[] vertices = new float[LOCAL_VERTEX_SIZE * 32];
        private int verticesIndex = 0;

        public MeshBuilder() {

        }

        public int position(float x, float y, float z) {
            if ((this.positionsIndex + 3) > this.positions.length) {
                this.positions = Arrays.copyOf(this.positions, this.positions.length * 2);
            }

            this.positions[this.positionsIndex + 0] = x;
            this.positions[this.positionsIndex + 1] = y;
            this.positions[this.positionsIndex + 2] = z;

            this.positionsIndex += 3;

            return (this.positionsIndex / 3);
        }

        public int texture(float u, float v) {
            if ((this.uvsIndex + 2) > this.uvs.length) {
                this.uvs = Arrays.copyOf(this.uvs, this.uvs.length * 2);
            }

            this.uvs[this.uvsIndex + 0] = u;
            this.uvs[this.uvsIndex + 1] = v;

            this.uvsIndex += 2;

            return (this.uvsIndex / 2);
        }

        public int normal(float nX, float nY, float nZ) {
            if ((this.normalsIndex + 3) > this.normals.length) {
                this.normals = Arrays.copyOf(this.normals, this.normals.length * 2);
            }

            this.normals[this.normalsIndex + 0] = nX;
            this.normals[this.normalsIndex + 1] = nY;
            this.normals[this.normalsIndex + 2] = nZ;

            this.normalsIndex += 3;

            return (this.normalsIndex / 3);
        }

        public int color(float r, float g, float b, float a) {
            if ((this.colorsIndex + 4) > this.colors.length) {
                this.colors = Arrays.copyOf(this.colors, this.colors.length * 2);
            }

            this.colors[this.colorsIndex + 0] = r;
            this.colors[this.colorsIndex + 1] = g;
            this.colors[this.colorsIndex + 2] = b;
            this.colors[this.colorsIndex + 3] = a;

            this.colorsIndex += 4;

            return (this.colorsIndex / 4);
        }

        public void vertex(int positionIndex, int textureIndex, int normalIndex, int colorIndex) {
            float x = 0f;
            float y = 0f;
            float z = 0f;
            if (positionIndex != 0) {
                positionIndex--;

                x = this.positions[(positionIndex * 3) + 0];
                y = this.positions[(positionIndex * 3) + 1];
                z = this.positions[(positionIndex * 3) + 2];
            }

            float u = 0f;
            float v = 0f;
            if (textureIndex != 0) {
                textureIndex--;

                u = this.uvs[(textureIndex * 2) + 0];
                v = this.uvs[(textureIndex * 2) + 1];
            }

            float nX = Float.NaN;
            float nY = Float.NaN;
            float nZ = Float.NaN;
            if (normalIndex != 0) {
                normalIndex--;

                nX = this.normals[(normalIndex * 3) + 0];
                nY = this.normals[(normalIndex * 3) + 1];
                nZ = this.normals[(normalIndex * 3) + 2];
            }

            float r = 1f;
            float g = 1f;
            float b = 1f;
            float a = 1f;
            if (colorIndex != 0) {
                colorIndex--;

                r = this.colors[(colorIndex * 4) + 0];
                g = this.colors[(colorIndex * 4) + 1];
                b = this.colors[(colorIndex * 4) + 2];
                a = this.colors[(colorIndex * 4) + 3];
            }

            if ((this.verticesIndex + LOCAL_VERTEX_SIZE) > this.vertices.length) {
                this.vertices = Arrays.copyOf(this.vertices, (this.vertices.length * 2) + LOCAL_VERTEX_SIZE);
            }

            //local position
            this.vertices[this.verticesIndex + POS_X] = x;
            this.vertices[this.verticesIndex + POS_Y] = y;
            this.vertices[this.verticesIndex + POS_Z] = z;

            //uv
            this.vertices[this.verticesIndex + TEX_U] = u;
            this.vertices[this.verticesIndex + TEX_V] = v;

            //normal
            this.vertices[this.verticesIndex + NRM_X] = nX;
            this.vertices[this.verticesIndex + NRM_Y] = nY;
            this.vertices[this.verticesIndex + NRM_Z] = nZ;

            //color
            this.vertices[this.verticesIndex + CLR_R] = r;
            this.vertices[this.verticesIndex + CLR_G] = g;
            this.vertices[this.verticesIndex + CLR_B] = b;
            this.vertices[this.verticesIndex + CLR_A] = a;

            this.verticesIndex += LOCAL_VERTEX_SIZE;

            if ((this.verticesIndex / LOCAL_VERTEX_SIZE) % 3 == 0) {
                int v2 = this.verticesIndex - LOCAL_VERTEX_SIZE;
                int v1 = v2 - LOCAL_VERTEX_SIZE;
                int v0 = v1 - LOCAL_VERTEX_SIZE;

                float v0nx = this.vertices[v0 + NRM_X];
                float v0ny = this.vertices[v0 + NRM_Y];
                float v0nz = this.vertices[v0 + NRM_Z];

                float v1nx = this.vertices[v1 + NRM_X];
                float v1ny = this.vertices[v1 + NRM_Y];
                float v1nz = this.vertices[v1 + NRM_Z];

                float v2nx = this.vertices[v2 + NRM_X];
                float v2ny = this.vertices[v2 + NRM_Y];
                float v2nz = this.vertices[v2 + NRM_Z];

                if (flatShaded(v0nx, v0ny, v0nz) || flatShaded(v1nx, v1ny, v1nz) || flatShaded(v2nx, v2ny, v2nz)) {
                    float v0x = this.vertices[v0 + POS_X];
                    float v0y = this.vertices[v0 + POS_Y];
                    float v0z = this.vertices[v0 + POS_Z];

                    float v1x = this.vertices[v1 + POS_X];
                    float v1y = this.vertices[v1 + POS_Y];
                    float v1z = this.vertices[v1 + POS_Z];

                    float v2x = this.vertices[v2 + POS_X];
                    float v2y = this.vertices[v2 + POS_Y];
                    float v2z = this.vertices[v2 + POS_Z];

                    Vector3f A = new Vector3f();
                    Vector3f B = new Vector3f();
                    Vector3f N = new Vector3f();

                    A.set(v1x, v1y, v1z).sub(v0x, v0y, v0z);
                    B.set(v2x, v2y, v2z).sub(v0x, v0y, v0z);
                    N.set(A).cross(B).normalize();

                    v0nx = N.x();
                    v0ny = N.y();
                    v0nz = N.z();

                    this.vertices[v0 + NRM_X] = v0nx;
                    this.vertices[v0 + NRM_Y] = v0ny;
                    this.vertices[v0 + NRM_Z] = v0nz;

                    this.vertices[v1 + NRM_X] = v0nx;
                    this.vertices[v1 + NRM_Y] = v0ny;
                    this.vertices[v1 + NRM_Z] = v0nz;

                    this.vertices[v2 + NRM_X] = v0nx;
                    this.vertices[v2 + NRM_Y] = v0ny;
                    this.vertices[v2 + NRM_Z] = v0nz;
                }
            }
        }

        private boolean flatShaded(float x, float y, float z) {
            return Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z);
        }

        public float[] vertices() {
            if ((this.verticesIndex / LOCAL_VERTEX_SIZE) % 3 != 0) {
                throw new IllegalArgumentException("The stream does not contains triangles. (The number of vertices cannot be divided by 3)");
            }

            return Arrays.copyOf(this.vertices, this.verticesIndex);
        }

    }

    //vertex transformer
    private class VertexTransformer {

        private static final Vector4f[] clippingEdges = new Vector4f[]{
            new Vector4f(-1, 0, 0, 1),
            new Vector4f(1, 0, 0, 1),
            new Vector4f(0, -1, 0, 1),
            new Vector4f(0, 1, 0, 1),
            new Vector4f(0, 0, -1, 1),
            new Vector4f(0, 0, 1, 1)
        };
        
        private final float[] localVertices;
        private final Matrix4f projectionView = new Matrix4f();
        private final Matrix4f model = new Matrix4f();
        private final Matrix3f normalModel = new Matrix3f();
        private final int width;
        private final int height;

        private float[] tverts = null;
        private float[] vertices = new float[VERTEX_SIZE * 64];
        private int verticesIndex = 0;

        public VertexTransformer(SoftwareRenderer renderer) {
            float[] localVerts = renderer.getMesh();
            this.localVertices = localVerts;
            this.projectionView
                    .set(renderer.getProjection())
                    .mul(renderer.getView())
                    .translate(renderer.getCameraPosition().negate(new Vector3f()));
            this.model
                    .set(renderer.getModel());
            if (renderer.isBillboardingEnabled()) {
                this.model.mul(renderer.getView().invert(new Matrix4f()));
            }
            this.normalModel.set(new Matrix4f(this.model).invert().transpose());
            this.width = renderer.getWidth();
            this.height = renderer.getHeight();
        }

        private float[] transformVertices() {
            float[] output = new float[(this.localVertices.length / MeshBuilder.LOCAL_VERTEX_SIZE) * VERTEX_SIZE];
            Vector4f pos = new Vector4f();
            Vector3f normal = new Vector3f();
            for (int v = 0; v < this.localVertices.length; v += MeshBuilder.LOCAL_VERTEX_SIZE) {
                int vout = (v / MeshBuilder.LOCAL_VERTEX_SIZE) * VERTEX_SIZE;

                pos.set(
                        this.localVertices[v + 0],
                        this.localVertices[v + 1],
                        this.localVertices[v + 2],
                        1f
                );
                this.model.transform(pos);
                output[vout + X] = pos.x();
                output[vout + Y] = pos.y();
                output[vout + Z] = pos.z();

                this.projectionView.transform(pos);
                output[vout + CX] = pos.x();
                output[vout + CY] = pos.y();
                output[vout + CZ] = pos.z();
                output[vout + CW] = pos.w();

                output[vout + U] = this.localVertices[v + 3];
                output[vout + V] = this.localVertices[v + 4];

                normal.set(
                        this.localVertices[v + 5],
                        this.localVertices[v + 6],
                        this.localVertices[v + 7]
                );
                this.normalModel.transform(normal).normalize();
                output[vout + NX] = normal.x();
                output[vout + NY] = normal.y();
                output[vout + NZ] = normal.z();

                output[vout + R] = this.localVertices[v + 8];
                output[vout + G] = this.localVertices[v + 9];
                output[vout + B] = this.localVertices[v + 10];
                output[vout + A] = this.localVertices[v + 11];
            }
            return output;
        }

        public float[] transformAndClip() {
            this.tverts = transformVertices();

            this.verticesIndex = 0;
            for (int i = 0; i < this.tverts.length; i += (VERTEX_SIZE * 3)) {
                int v0 = i;
                int v1 = v0 + VERTEX_SIZE;
                int v2 = v1 + VERTEX_SIZE;

                if (mustClip(v0) || mustClip(v1) || mustClip(v2)) {
                    clip(v0, v1, v2);
                    continue;
                }

                if (!ccw(this.tverts, v0, v1, v2)) {
                    continue;
                }

                vertex(v0);
                vertex(v1);
                vertex(v2);
            }

            if ((this.verticesIndex / VERTEX_SIZE) % 3 != 0) {
                throw new IllegalArgumentException("The stream does not contains triangles. (The number of vertices cannot be divided by 3)");
            }

            float[] resultVertices = Arrays.copyOf(this.vertices, this.verticesIndex);
            prepareForRasterization(resultVertices);
            return resultVertices;
        }

        private boolean ccw(float[] verts, int v0, int v1, int v2) {
            float v0invcw = 1f / verts[v0 + CW];
            float v1invcw = 1f / verts[v1 + CW];
            float v2invcw = 1f / verts[v2 + CW];

            float v0cxw = verts[v0 + CX] * v0invcw;
            float v0cyw = verts[v0 + CY] * v0invcw;

            float v1cxw = verts[v1 + CX] * v1invcw;
            float v1cyw = verts[v1 + CY] * v1invcw;

            float v2cxw = verts[v2 + CX] * v2invcw;
            float v2cyw = verts[v2 + CY] * v2invcw;

            float ccw = (v1cxw - v0cxw) * (v2cyw - v0cyw) - (v2cxw - v0cxw) * (v1cyw - v0cyw);
            return ccw > 0f;
        }

        private boolean mustClip(int v) {
            float invcw = 1f / this.tverts[v + CW];
            return (this.tverts[v + CX] * invcw) > 1f
                    || (this.tverts[v + CX] * invcw) < -1f
                    || (this.tverts[v + CY] * invcw) > 1f
                    || (this.tverts[v + CY] * invcw) < -1f
                    || (this.tverts[v + CZ] * invcw) > 1f
                    || (this.tverts[v + CZ] * invcw) < -1f;
        }

        private void clip(int v0, int v1, int v2) {
            //https://read.cash/@Metalhead33/software-renderer-4-complex-shapes-z-buffers-alpha-blending-perspective-correction-cameras-c1ebfd00

            int verticesIndexStore = this.verticesIndex;
            float[] verticesStore = this.vertices;

            this.verticesIndex = 0;
            this.vertices = new float[VERTEX_SIZE * 36];

            vertex(v0);
            vertex(v1);
            vertex(v2);

            int[] inputList = new int[36];
            int inputListIndex = 0;
            int[] outputList = new int[36];
            int outputListIndex = 0;

            inputList[0] = 0;
            inputList[1] = 1;
            inputList[2] = 2;
            inputListIndex += 3;

            for (Vector4f clippingEdge : VertexTransformer.clippingEdges) {
                if (inputListIndex < 3) {
                    continue;
                }
                outputListIndex = 0;
                int idxPrev = inputList[0];
                //inputList, not output
                //outputList.add(idxPrev);
                inputList[inputListIndex] = idxPrev;
                inputListIndex++;
                float dpPrev = calculateDp(clippingEdge, idxPrev);
                for (int j = 1; j < inputListIndex; ++j) {
                    int idx = inputList[j];
                    float dp = calculateDp(clippingEdge, idx);

                    if (dpPrev >= 0) {
                        outputList[outputListIndex] = idxPrev;
                        outputListIndex++;
                    }

                    if (Math.signum(dp) != Math.signum(dpPrev)) {
                        float t = dp < 0 ? dpPrev / (dpPrev - dp) : -dpPrev / (dp - dpPrev);
                        int v = interpolateVertex(idxPrev, idx, 1f - t, t);
                        outputList[outputListIndex] = v;
                        outputListIndex++;
                    }

                    idxPrev = idx;
                    dpPrev = dp;

                }
                int[] e = inputList;

                inputListIndex = outputListIndex;
                inputList = outputList;
                outputList = e;
            }

            if (inputListIndex < 3) {
                this.vertices = verticesStore;
                this.verticesIndex = verticesIndexStore;
                return;
            }

            int[] resultIndices = new int[3 + ((inputListIndex - 3) * 3)];
            int resultIndicesIndex = 0;

            resultIndices[0] = inputList[0];
            resultIndices[1] = inputList[1];
            resultIndices[2] = inputList[2];
            resultIndicesIndex += 3;
            for (int j = 3; j < inputListIndex; j++) {
                resultIndices[resultIndicesIndex + 0] = inputList[0];
                resultIndices[resultIndicesIndex + 1] = inputList[j - 1];
                resultIndices[resultIndicesIndex + 2] = inputList[j];
                resultIndicesIndex += 3;
            }

            float[] resultVertices = this.vertices;

            this.vertices = verticesStore;
            this.verticesIndex = verticesIndexStore;

            processResultIndices(resultIndices, resultVertices);
        }

        private float calculateDp(Vector4fc clippingEdge, int vi) {
            vi *= VERTEX_SIZE;
            float cx = this.vertices[vi + CX];
            float cy = this.vertices[vi + CY];
            float cz = this.vertices[vi + CZ];
            float cw = this.vertices[vi + CW];
            return (clippingEdge.x() * cx)
                    + (clippingEdge.y() * cy)
                    + (clippingEdge.z() * cz)
                    + (clippingEdge.w() * cw);
        }

        private void processResultIndices(int[] indices, float[] verts) {
            for (int j = 0; j < (indices.length / 3); j++) {
                int v0 = indices[(j * 3) + 0] * VERTEX_SIZE;
                int v1 = indices[(j * 3) + 1] * VERTEX_SIZE;
                int v2 = indices[(j * 3) + 2] * VERTEX_SIZE;

                if (!ccw(verts, v0, v1, v2)) {
                    continue;
                }

                if ((this.verticesIndex + (VERTEX_SIZE * 3)) > this.vertices.length) {
                    this.vertices = Arrays.copyOf(this.vertices, (this.vertices.length * 2) + (VERTEX_SIZE * 3));
                }

                System.arraycopy(verts, v0, this.vertices, this.verticesIndex + (0 * VERTEX_SIZE), VERTEX_SIZE);
                System.arraycopy(verts, v1, this.vertices, this.verticesIndex + (1 * VERTEX_SIZE), VERTEX_SIZE);
                System.arraycopy(verts, v2, this.vertices, this.verticesIndex + (2 * VERTEX_SIZE), VERTEX_SIZE);

                this.verticesIndex += (VERTEX_SIZE * 3);
            }
        }

        private void prepareForRasterization(float[] vertices) {
            for (int v = 0; v < vertices.length; v += VERTEX_SIZE) {
                float cwinv = 1f / vertices[v + CW_INV];
                for (int i = 0; i < VERTEX_SIZE; i++) {
                    vertices[v + i] = vertices[v + i] * cwinv;
                }
                for (int i = CX; i <= CZ; i++) {
                    vertices[v + i] = (vertices[v + i] + 1.0f) * 0.5f;
                }
                vertices[v + CX] = vertices[v + CX] * this.width;
                vertices[v + CY] = vertices[v + CY] * this.height;
                vertices[v + CW_INV] = cwinv;
            }
        }

        private int interpolateVertex(int vai, int vbi, float w0, float w1) {
            vai *= VERTEX_SIZE;
            vbi *= VERTEX_SIZE;
            
            if ((this.verticesIndex + VERTEX_SIZE) > this.vertices.length) {
                this.vertices = Arrays.copyOf(this.vertices, (this.vertices.length * 2) + VERTEX_SIZE);
            }

            float[] output = new float[VERTEX_SIZE];
            for (int i = 0; i < VERTEX_SIZE; i++) {
                float valueA = this.vertices[vai + i];
                float valueB = this.vertices[vbi + i];
                output[i] = (valueA * w0) + (valueB * w1);
            }
            System.arraycopy(output, 0, this.vertices, this.verticesIndex, output.length);

            this.verticesIndex += VERTEX_SIZE;

            return (this.verticesIndex / VERTEX_SIZE) - 1;
        }

        private void vertex(int v) {
            if ((this.verticesIndex + VERTEX_SIZE) > this.vertices.length) {
                this.vertices = Arrays.copyOf(this.vertices, (this.vertices.length * 2) + VERTEX_SIZE);
            }

            System.arraycopy(this.tverts, v, this.vertices, this.verticesIndex, VERTEX_SIZE);

            this.verticesIndex += VERTEX_SIZE;
        }

    }

    //light
    public static interface Light {

        public Vector3f getDiffuseColor();

        public Vector3f getAmbientColor();

        public Vector3f getPosition();

        public void calculateDiffuseAmbientFactors(float x, float y, float z, float nx, float ny, float nz, float[] diffuseAmbientFactors, int offset);
    }

    //point light
    public static class PointLight implements Light {

        private final Vector3f diffuseColor = new Vector3f(0.8f, 0.8f, 0.8f);
        private final Vector3f ambientColor = new Vector3f(0.3f, 0.3f, 0.3f);
        private final Vector3f position = new Vector3f(0f, 0f, 0f);

        public PointLight() {

        }

        @Override
        public Vector3f getDiffuseColor() {
            return this.diffuseColor;
        }

        @Override
        public Vector3f getAmbientColor() {
            return this.ambientColor;
        }

        @Override
        public Vector3f getPosition() {
            return this.position;
        }

        @Override
        public void calculateDiffuseAmbientFactors(float x, float y, float z, float nx, float ny, float nz, float[] diffuseAmbientFactors, int offset) {
            float lightDirX = this.position.x() - x;
            float lightDirY = this.position.y() - y;
            float lightDirZ = this.position.z() - z;
            float lightDirLengthInverse = (float) (1.0 / Math.sqrt((lightDirX * lightDirX) + (lightDirY * lightDirY) + (lightDirZ * lightDirZ)));
            lightDirX *= lightDirLengthInverse;
            lightDirY *= lightDirLengthInverse;
            lightDirZ *= lightDirLengthInverse;
            diffuseAmbientFactors[offset + 0] = Math.max((lightDirX * nx) + (lightDirY * ny) + (lightDirZ * nz), 0f) * lightDirLengthInverse;
            diffuseAmbientFactors[offset + 1] = lightDirLengthInverse;
        }

    }

    //spot light
    public static class SpotLight extends PointLight {

        private static final float cutOffAmbientCosRad = (float) Math.cos(Math.toRadians(160f));
        private static final float outerCutOffAmbientCosRad = (float) Math.cos(Math.toRadians(180f));

        private final Vector3f direction = new Vector3f(0f, -1f, 0f);
        private float cutOff = 10f;
        private float outerCutOff = 45f;

        private float cutOffCosRad = (float) Math.cos(Math.toRadians(this.cutOff));
        private float outerCutOffCosRad = (float) Math.cos(Math.toRadians(this.outerCutOff));

        public SpotLight() {

        }

        public Vector3f getDirection() {
            return direction;
        }

        public float getCutOff() {
            return cutOff;
        }

        public float getOuterCutOff() {
            return outerCutOff;
        }

        public void setCutOff(float cutOff) {
            this.cutOff = cutOff;
            this.cutOffCosRad = (float) Math.cos(Math.toRadians(this.cutOff));
        }

        public void setOuterCutOff(float outerCutOff) {
            this.outerCutOff = outerCutOff;
            this.outerCutOffCosRad = (float) Math.cos(Math.toRadians(this.outerCutOff));
        }

        @Override
        public void calculateDiffuseAmbientFactors(float x, float y, float z, float nx, float ny, float nz, float[] diffuseAmbientFactors, int offset) {
            float lightDirX = getPosition().x() - x;
            float lightDirY = getPosition().y() - y;
            float lightDirZ = getPosition().z() - z;
            float lightDirLengthInverse = (float) (1.0 / Math.sqrt((lightDirX * lightDirX) + (lightDirY * lightDirY) + (lightDirZ * lightDirZ)));
            lightDirX *= lightDirLengthInverse;
            lightDirY *= lightDirLengthInverse;
            lightDirZ *= lightDirLengthInverse;

            float theta = (lightDirX * -this.direction.x()) + (lightDirY * -this.direction.y()) + (lightDirZ * -this.direction.z());

            float epsilonDiffuse = this.cutOffCosRad - this.outerCutOffCosRad;
            float intensityDiffuse = Math.min(Math.max((theta - this.outerCutOffCosRad) / epsilonDiffuse, 0.0f), 1.0f);

            float epsilonAmbient = SpotLight.cutOffAmbientCosRad - SpotLight.outerCutOffAmbientCosRad;
            float intensityAmbient = Math.min(Math.max((theta - SpotLight.outerCutOffAmbientCosRad) / epsilonAmbient, 0.0f), 1.0f);

            diffuseAmbientFactors[offset + 0] = Math.max((lightDirX * nx) + (lightDirY * ny) + (lightDirZ * nz), 0f) * lightDirLengthInverse * intensityDiffuse;
            diffuseAmbientFactors[offset + 1] = lightDirLengthInverse * intensityAmbient;
        }

    }

    //rasterizer
    private class Rasterizer {

        private final SoftwareRenderer renderer;
        private final float[] vertices;

        public Rasterizer(SoftwareRenderer renderer, float[] transformedVertices) {
            this.renderer = renderer;
            this.vertices = transformedVertices;
        }

        public void render() {
            int width = this.renderer.getWidth();
            int height = this.renderer.getHeight();
            int numberOfTriangles = this.vertices.length / (VERTEX_SIZE * 3);
            for (int i = 0; i < numberOfTriangles; i++) {
                int v0 = i * (VERTEX_SIZE * 3);
                int v1 = v0 + VERTEX_SIZE;
                int v2 = v1 + VERTEX_SIZE;

                float v0cx = this.vertices[v0 + CX];
                float v0cy = this.vertices[v0 + CY];

                float v1cx = this.vertices[v1 + CX];
                float v1cy = this.vertices[v1 + CY];

                float v2cx = this.vertices[v2 + CX];
                float v2cy = this.vertices[v2 + CY];

                float inverse = 1f / ((v1cy - v2cy) * (v0cx - v2cx) + (v2cx - v1cx) * (v0cy - v2cy));

                float maxX = Math.max(Math.max(v0cx, v1cx), v2cx);
                float maxY = Math.max(Math.max(v0cy, v1cy), v2cy);

                float minX = Math.min(Math.min(v0cx, v1cx), v2cx);
                float minY = Math.min(Math.min(v0cy, v1cy), v2cy);

                int maxXP = clamp((int) Math.ceil(maxX), 0, width);
                int maxYP = clamp((int) Math.ceil(maxY), 0, height);
                int minXP = clamp((int) Math.floor(minX), 0, width - 1);
                int minYP = clamp((int) Math.floor(minY), 0, height - 1);
                
                boolean multithreadActivated = (maxXP - minXP) >= 32 && this.renderer.isMultithreadEnabled();

                Future<?>[] tasks = new Future<?>[maxYP - minYP];
                for (int y = minYP; y < maxYP; y++) {
                    if (multithreadActivated) {
                        int finalY = y;
                        tasks[y - minYP] = CompletableFuture.runAsync(() -> {
                            renderLine(inverse, finalY, minXP, maxXP, v0, v1, v2);
                        });
                    } else {
                        renderLine(inverse, y, minXP, maxXP, v0, v1, v2);
                    }
                }
                if (multithreadActivated) {
                    try {
                        for (Future<?> task : tasks) {
                            task.get();
                        }
                    } catch (InterruptedException | ExecutionException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }

        private int clamp(int v, int min, int max) {
            return Math.max(Math.min(v, max), min);
        }

        private void renderLine(float inverse, int y, int minX, int maxX, int v0, int v1, int v2) {
            float[] surfaceDepth = new float[maxX - minX];
            this.renderer.getSurface().getDepth(minX, y, surfaceDepth, 0, surfaceDepth.length);
            float[] surfaceColor = new float[(maxX - minX) * 4];
            this.renderer.getSurface().getColor(minX, y, surfaceColor, 0, surfaceColor.length);

            float[] textureColor = new float[4];
            float[] diffuseAmbientFactors = new float[2];
            for (int x = minX; x < maxX; x++) {
                int pixelIndex = x - minX;

                float xPos = x + 0.5f;
                float yPos = y + 0.5f;

                float wv0 = ((this.vertices[v1 + CY] - this.vertices[v2 + CY]) * (xPos - this.vertices[v2 + CX]) + (this.vertices[v2 + CX] - this.vertices[v1 + CX]) * (yPos - this.vertices[v2 + CY])) * inverse;
                float wv1 = ((this.vertices[v2 + CY] - this.vertices[v0 + CY]) * (xPos - this.vertices[v2 + CX]) + (this.vertices[v0 + CX] - this.vertices[v2 + CX]) * (yPos - this.vertices[v2 + CY])) * inverse;
                float wv2 = 1 - wv0 - wv1;
                if (wv0 < 0f || wv1 < 0f || wv2 < 0f) {
                    continue;
                }
                
                float invw = (wv0 * this.vertices[v0 + CW_INV]) + (wv1 * this.vertices[v1 + CW_INV]) + (wv2 * this.vertices[v2 + CW_INV]);
                float w = 1f / invw;

                float depth = (wv0 * this.vertices[v0 + CZ]) + (wv1 * this.vertices[v1 + CZ]) + (wv2 * this.vertices[v2 + CZ]);
                float currentDepth = surfaceDepth[pixelIndex];
                if (depth > currentDepth) {
                    surfaceDepth[pixelIndex] = currentDepth;
                    continue;
                }
                surfaceDepth[pixelIndex] = depth;

                if (this.renderer.isDepthOnlyEnabled()) {
                    continue;
                }

                float u = ((wv0 * this.vertices[v0 + U]) + (wv1 * this.vertices[v1 + U]) + (wv2 * this.vertices[v2 + U])) * w;
                float v = ((wv0 * this.vertices[v0 + V]) + (wv1 * this.vertices[v1 + V]) + (wv2 * this.vertices[v2 + V])) * w;

                Vector4fc color = this.renderer.getColor();
                float cr = color.x();
                float cg = color.y();
                float cb = color.z();
                float ca = color.w();

                cr *= ((wv0 * this.vertices[v0 + R]) + (wv1 * this.vertices[v1 + R]) + (wv2 * this.vertices[v2 + R])) * w;
                cg *= ((wv0 * this.vertices[v0 + G]) + (wv1 * this.vertices[v1 + G]) + (wv2 * this.vertices[v2 + G])) * w;
                cb *= ((wv0 * this.vertices[v0 + B]) + (wv1 * this.vertices[v1 + B]) + (wv2 * this.vertices[v2 + B])) * w;
                ca *= ((wv0 * this.vertices[v0 + A]) + (wv1 * this.vertices[v1 + A]) + (wv2 * this.vertices[v2 + A])) * w;

                Texture texture = this.renderer.getTexture();
                if (texture != null) {
                    if (this.renderer.isBilinearFilteringEnabled()) {
                        texture.sampleBilinear(u, v, textureColor, 0);
                    } else {
                        texture.sampleNearest(u, v, textureColor, 0);
                    }

                    cr *= textureColor[0];
                    cg *= textureColor[1];
                    cb *= textureColor[2];
                    ca *= textureColor[3];
                }

                List<Light> lights = this.renderer.getLights();
                if (this.renderer.isLightingEnabled() && (!lights.isEmpty() || this.renderer.isSunEnabled())) {
                    float nx = ((wv0 * this.vertices[v0 + NX]) + (wv1 * this.vertices[v1 + NX]) + (wv2 * this.vertices[v2 + NX])) * w;
                    float ny = ((wv0 * this.vertices[v0 + NY]) + (wv1 * this.vertices[v1 + NY]) + (wv2 * this.vertices[v2 + NY])) * w;
                    float nz = ((wv0 * this.vertices[v0 + NZ]) + (wv1 * this.vertices[v1 + NZ]) + (wv2 * this.vertices[v2 + NZ])) * w;
                    float lengthinv = (float) (1.0 / Math.sqrt((nx * nx) + (ny * ny) + (nz * nz)));
                    nx *= lengthinv;
                    ny *= lengthinv;
                    nz *= lengthinv;

                    float r = 0f;
                    float g = 0f;
                    float b = 0f;

                    if (this.renderer.isSunEnabled()) {
                        Vector3fc lightAmbient = this.renderer.getSunAmbient();
                        r += lightAmbient.x() * cr;
                        g += lightAmbient.y() * cg;
                        b += lightAmbient.z() * cb;

                        Vector3fc lightDirection = this.renderer.getSunDirection();
                        float diffuse = Math.max((nx * -lightDirection.x()) + (ny * -lightDirection.y()) + (nz * -lightDirection.z()), 0f);

                        Vector3fc lightDiffuse = this.renderer.getSunDiffuse();
                        r += lightDiffuse.x() * diffuse * cr;
                        g += lightDiffuse.y() * diffuse * cg;
                        b += lightDiffuse.z() * diffuse * cb;
                    }

                    if (!lights.isEmpty()) {
                        float worldx = ((wv0 * this.vertices[v0 + X]) + (wv1 * this.vertices[v1 + X]) + (wv2 * this.vertices[v2 + X])) * w;
                        float worldy = ((wv0 * this.vertices[v0 + Y]) + (wv1 * this.vertices[v1 + Y]) + (wv2 * this.vertices[v2 + Y])) * w;
                        float worldz = ((wv0 * this.vertices[v0 + Z]) + (wv1 * this.vertices[v1 + Z]) + (wv2 * this.vertices[v2 + Z])) * w;

                        for (Light light : lights) {
                            if (light != null) {
                                light.calculateDiffuseAmbientFactors(worldx, worldy, worldz, nx, ny, nz, diffuseAmbientFactors, 0);

                                r += diffuseAmbientFactors[0] * light.getDiffuseColor().x() * cr;
                                g += diffuseAmbientFactors[0] * light.getDiffuseColor().y() * cg;
                                b += diffuseAmbientFactors[0] * light.getDiffuseColor().z() * cb;

                                r += diffuseAmbientFactors[1] * light.getAmbientColor().x() * cr;
                                g += diffuseAmbientFactors[1] * light.getAmbientColor().y() * cg;
                                b += diffuseAmbientFactors[1] * light.getAmbientColor().z() * cb;
                            }
                        }
                    }

                    cr = r;
                    cg = g;
                    cb = b;
                }

                float outR = 0.0f, outG = 0.0f, outB = 0.0f, outA;
                calculateAlpha:
                {
                    float srcR = cr, srcG = cg, srcB = cb, srcA = ca;
                    float dstA = surfaceColor[(pixelIndex * 4) + 3];
                    if (dstA == 1f) {
                        float dstR = surfaceColor[(pixelIndex * 4) + 0];
                        float dstG = surfaceColor[(pixelIndex * 4) + 1];
                        float dstB = surfaceColor[(pixelIndex * 4) + 2];
                        outR = (srcR * srcA) + (dstR * (1f - srcA));
                        outG = (srcG * srcA) + (dstG * (1f - srcA));
                        outB = (srcB * srcA) + (dstB * (1f - srcA));
                        outA = 1f;
                        break calculateAlpha;
                    }
                    outA = srcA + dstA * (1f - srcA);
                    if (outA == 0f) {
                        break calculateAlpha;
                    }
                    float dstR = surfaceColor[(pixelIndex * 4) + 0];
                    float dstG = surfaceColor[(pixelIndex * 4) + 1];
                    float dstB = surfaceColor[(pixelIndex * 4) + 2];
                    float invOutA = 1f / outA;
                    outR = (srcR * srcA + dstR * dstA * (1f - srcA)) * invOutA;
                    outG = (srcG * srcA + dstG * dstA * (1f - srcA)) * invOutA;
                    outB = (srcB * srcA + dstB * dstA * (1f - srcA)) * invOutA;
                }
                surfaceColor[(pixelIndex * 4) + 0] = outR;
                surfaceColor[(pixelIndex * 4) + 1] = outG;
                surfaceColor[(pixelIndex * 4) + 2] = outB;
                surfaceColor[(pixelIndex * 4) + 3] = outA;
            }

            this.renderer.getSurface().setDepth(minX, y, surfaceDepth, 0, surfaceDepth.length);
            this.renderer.getSurface().setColor(minX, y, surfaceColor, 0, surfaceColor.length);
        }

    }

    //surface
    private Surface frontSurface;
    private Surface backSurface;

    //vertex builder
    private MeshBuilder builder = null;

    //surface state
    private final Vector4f clearColor = new Vector4f(0.2f, 0.4f, 0.6f, 1f);
    private float clearDepth = 1f;

    //rasterizer/processor state
    private boolean depthOnlyEnabled = false;
    private boolean bilinearFilteringEnabled = false;
    private boolean multithreadEnabled = true;
    private boolean billboardingEnabled = false;
    private boolean lightingEnabled = false;
    private boolean sunEnabled = false;

    //sun state
    private final Vector3f sunDirection = new Vector3f(-1f, -1f, -1f).normalize();
    private final Vector3f sunDiffuse = new Vector3f(0.8f, 0.75f, 0.70f);
    private final Vector3f sunAmbient = new Vector3f(0.3f, 0.3f, 0.3f);

    //lights state
    private final List<Light> lights = new ArrayList<>();

    //camera state
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Vector3f cameraPosition = new Vector3f();

    //object state
    private float[] vertices = null;
    private final Matrix4f model = new Matrix4f();
    private Texture texture = null;
    private final Vector4f color = new Vector4f(1f, 1f, 1f, 1f);

    public SoftwareRenderer(int width, int height) {
        this.frontSurface = new Surface(width, height);
        this.backSurface = new Surface(width, height);
    }

    public SoftwareRenderer() {
        this(Surface.DEFAULT_WIDTH, Surface.DEFAULT_HEIGHT);
    }

    //surface
    public Surface getSurface() {
        return this.frontSurface;
    }

    public Surface getFrontSurface() {
        return frontSurface;
    }

    public Surface getBackSurface() {
        return backSurface;
    }

    public float getClearDepth() {
        return clearDepth;
    }

    public void setClearDepth(float clearDepth) {
        this.clearDepth = clearDepth;
    }

    public Vector4f getClearColor() {
        return clearColor;
    }

    public void clearBuffers() {
        this.frontSurface.clearDepth(this.clearDepth);
        this.frontSurface.clearColor(this.clearColor.x(), this.clearColor.y(), this.clearColor.z(), this.clearColor.w());
    }

    public void resize(int width, int height) {
        this.frontSurface = new Surface(width, height);
        this.backSurface = new Surface(width, height);
    }

    public int getWidth() {
        return this.frontSurface.getWidth();
    }

    public int getHeight() {
        return this.frontSurface.getHeight();
    }

    public Texture colorBuffer() {
        return this.frontSurface.getColorBufferTexture();
    }

    public Texture depthBuffer() {
        return this.frontSurface.getDepthBufferTexture();
    }

    public BufferedImage colorBufferToImage() {
        return SoftwareRenderer.textureToImage(this.colorBuffer());
    }

    public BufferedImage depthBufferToImage() {
        return SoftwareRenderer.textureToImage(this.depthBuffer());
    }

    public void flipSurfaces() {
        Surface front = this.frontSurface;
        Surface back = this.backSurface;
        this.frontSurface = back;
        this.backSurface = front;
    }

    //mesh builder
    public void beginMesh() {
        this.builder = new MeshBuilder();
    }

    public int position(float x, float y, float z) {
        return this.builder.position(x, y, z);
    }

    public int texture(float u, float v) {
        return this.builder.texture(u, v);
    }

    public int normal(float nX, float nY, float nZ) {
        return this.builder.normal(nX, nY, nZ);
    }

    public int color(float r, float g, float b, float a) {
        return this.builder.color(r, g, b, a);
    }

    public void vertex(int positionIndex, int textureIndex, int normalIndex, int colorIndex) {
        this.builder.vertex(positionIndex, textureIndex, normalIndex, colorIndex);
    }

    public float[] finishMesh() {
        MeshBuilder e = this.builder;
        this.builder = null;
        return e.vertices();
    }

    public void finishMeshAndSet() {
        this.setMesh(this.finishMesh());
    }

    //vertex processor state
    public float[] getMesh() {
        return vertices;
    }

    public void setMesh(float[] vertices) {
        this.vertices = vertices;
    }

    public Matrix4f getProjection() {
        return projection;
    }

    public Matrix4f getView() {
        return view;
    }

    public Vector3f getCameraPosition() {
        return cameraPosition;
    }

    public Matrix4f getModel() {
        return model;
    }

    public boolean isBillboardingEnabled() {
        return billboardingEnabled;
    }

    public void setBillboardingEnabled(boolean billboardingEnabled) {
        this.billboardingEnabled = billboardingEnabled;
    }

    //rasterizer
    public void setTexture(Texture texture) {
        this.texture = texture;
    }

    public Texture getTexture() {
        return texture;
    }

    public Vector3f getSunDirection() {
        return sunDirection;
    }

    public Vector3f getSunDiffuse() {
        return sunDiffuse;
    }

    public Vector3f getSunAmbient() {
        return sunAmbient;
    }

    public boolean isDepthOnlyEnabled() {
        return depthOnlyEnabled;
    }

    public void setDepthOnlyEnabled(boolean depthOnlyEnabled) {
        this.depthOnlyEnabled = depthOnlyEnabled;
    }

    public boolean isBilinearFilteringEnabled() {
        return bilinearFilteringEnabled;
    }

    public void setBilinearFilteringEnabled(boolean bilinearFilteringEnabled) {
        this.bilinearFilteringEnabled = bilinearFilteringEnabled;
    }

    public boolean isMultithreadEnabled() {
        return multithreadEnabled;
    }

    public void setMultithreadEnabled(boolean multithreadEnabled) {
        if (this.multithreadEnabled && !multithreadEnabled) {

        }
        this.multithreadEnabled = multithreadEnabled;
    }

    public List<Light> getLights() {
        return lights;
    }

    public Vector4f getColor() {
        return color;
    }

    public boolean isLightingEnabled() {
        return lightingEnabled;
    }

    public void setLightingEnabled(boolean lightingEnabled) {
        this.lightingEnabled = lightingEnabled;
    }

    public boolean isSunEnabled() {
        return sunEnabled;
    }

    public void setSunEnabled(boolean sunEnabled) {
        this.sunEnabled = sunEnabled;
    }

    //render
    public int render() {
        if (this.vertices == null || this.vertices.length == 0) {
            return 0;
        }
        float[] transformed = new VertexTransformer(this).transformAndClip();
        new Rasterizer(this, transformed).render();

        return (transformed.length / VERTEX_SIZE);
    }
}
