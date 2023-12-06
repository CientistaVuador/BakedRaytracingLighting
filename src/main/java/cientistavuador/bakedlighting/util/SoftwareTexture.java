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

import static org.lwjgl.opengl.GL33C.*;

//texture
public interface SoftwareTexture {

    public static final SoftwareTexture EMPTY = new SoftwareTexture() {
        @Override
        public int width() {
            return 1;
        }

        @Override
        public int height() {
            return 1;
        }

        @Override
        public void fetch(int x, int y, float[] result, int offset) {
            for (int i = 0; i < 4; i++) {
                result[offset + i] = 1f;
            }
        }
    };

    private static TextureWrapping fromGLWrappingEnum(int en) {
        switch (en) {
            case GL_REPEAT -> {
                return TextureWrapping.REPEAT;
            }
            case GL_MIRRORED_REPEAT -> {
                return TextureWrapping.MIRRORED_REPEAT;
            }
            case GL_CLAMP_TO_EDGE, GL_CLAMP_TO_BORDER -> {
                return TextureWrapping.CLAMP_TO_EDGE;
            }
        }
        return TextureWrapping.REPEAT;
    }
    
    private static TextureFiltering fromGLFilteringEnum(int en) {
        switch (en) {
            case GL_LINEAR, GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR_MIPMAP_NEAREST -> {
                return TextureFiltering.BILINEAR;
            }
            case GL_NEAREST, GL_NEAREST_MIPMAP_NEAREST, GL_NEAREST_MIPMAP_LINEAR -> {
                return TextureFiltering.NEAREST;
            }
        }
        return TextureFiltering.BILINEAR;
    }
    
    public static SoftwareTexture fromGLTexture2D(int texture) {
        if (texture == 0) {
            return EMPTY;
        }
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);

        int width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
        int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        int[] pixels = new int[width * height];
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_INT_8_8_8_8, pixels);
        
        TextureWrapping wX = fromGLWrappingEnum(glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S));
        TextureWrapping wY = fromGLWrappingEnum(glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T));
        TextureFiltering f = fromGLFilteringEnum(glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER));
        
        glBindTexture(GL_TEXTURE_2D, 0);
        
        return new SoftwareTexture() {
            private final int w = width;
            private final int h = height;
            private final int[] p = pixels;
            private final TextureWrapping wrapX = wX;
            private final TextureWrapping wrapY = wY;
            private final TextureFiltering filter = f;

            @Override
            public int width() {
                return this.w;
            }

            @Override
            public int height() {
                return this.h;
            }

            @Override
            public TextureWrapping getWrappingX() {
                return this.wrapX;
            }

            @Override
            public TextureWrapping getWrappingY() {
                return this.wrapY;
            }

            @Override
            public TextureFiltering getPreferredFiltering() {
                return this.filter;
            }
            
            @Override
            public void fetch(int x, int y, float[] result, int offset) {
                int pixel = this.p[x + (y * height())];
                float r = ((pixel >> 24) & 0xFF) / 255f;
                float g = ((pixel >> 16) & 0xFF) / 255f;
                float b = ((pixel >> 8) & 0xFF) / 255f;
                float a = ((pixel >> 0) & 0xFF) / 255f;
                result[offset + 0] = r;
                result[offset + 1] = g;
                result[offset + 2] = b;
                result[offset + 3] = a;
            }
        };
    }

    public static enum TextureWrapping {
        REPEAT, MIRRORED_REPEAT, CLAMP_TO_EDGE;
    }

    public static enum TextureFiltering {
        NEAREST, BILINEAR;
    }

    public int width();

    public int height();

    public default TextureWrapping getWrappingX() {
        return TextureWrapping.REPEAT;
    }

    public default TextureWrapping getWrappingY() {
        return TextureWrapping.REPEAT;
    }

    public default TextureFiltering getPreferredFiltering() {
        return TextureFiltering.BILINEAR;
    }

    public default int wrapX(int x) {
        return wrap(x, getWrappingX(), true);
    }

    public default int wrapY(int y) {
        return wrap(y, getWrappingY(), false);
    }

    private int wrap(int p, TextureWrapping wrapping, boolean xAxis) {
        int axisSize = (xAxis ? width() : height());
        switch (wrapping) {
            case REPEAT -> {
                return Math.abs(p % axisSize);
            }
            case MIRRORED_REPEAT -> {
                int repeatValue = (int) Math.floor(((double) p) / axisSize);
                p = Math.abs(p % axisSize);
                if (repeatValue % 2 != 0) {
                    p = (axisSize - 1) - p;
                }
                return p;
            }
            case CLAMP_TO_EDGE -> {
                if (p >= axisSize) {
                    return axisSize - 1;
                }
                if (p < 0) {
                    return 0;
                }
                return p;
            }
        }
        return Math.abs(p % axisSize);
    }

    public default void sampleNearest(float x, float y, float[] result, int offset) {
        int width = width();
        int height = height();
        int pX = wrapX(((int) Math.floor(Math.abs(x) * width)));
        int pY = wrapY(((int) Math.floor(Math.abs(y) * height)));
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
        fetch(wrapX(bottomLeftX), wrapY(bottomLeftY), result, offset);

        float bottomLeftR = result[offset + 0];
        float bottomLeftG = result[offset + 1];
        float bottomLeftB = result[offset + 2];
        float bottomLeftA = result[offset + 3];
        int bottomRightX = (int) Math.ceil(pX);
        int bottomRightY = (int) Math.floor(pY);
        fetch(wrapX(bottomRightX), wrapY(bottomRightY), result, offset);

        float bottomRightR = result[offset + 0];
        float bottomRightG = result[offset + 1];
        float bottomRightB = result[offset + 2];
        float bottomRightA = result[offset + 3];
        int topLeftX = (int) Math.floor(pX);
        int topLeftY = (int) Math.ceil(pY);
        fetch(wrapX(topLeftX), wrapY(topLeftY), result, offset);

        float topLeftR = result[offset + 0];
        float topLeftG = result[offset + 1];
        float topLeftB = result[offset + 2];
        float topLeftA = result[offset + 3];
        int topRightX = (int) Math.ceil(pX);
        int topRightY = (int) Math.ceil(pY);
        fetch(wrapX(topRightX), wrapY(topRightY), result, offset);

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
