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

import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class RasterUtils {
    
    public static final float DEGENERATE_AREA = 0.00001f;
    
    public static void barycentricWeights(Vector3fc p, Vector3fc a, Vector3fc b, Vector3fc c, Vector3f outWeights) {
        barycentricWeights(
                p.x(), p.y(), p.z(),
                a.x(), a.y(), a.z(),
                b.x(), b.y(), b.z(),
                c.x(), c.y(), c.z(),
                outWeights);
    }
    
    public static void barycentricWeights(
            float pX, float pY, float pZ,
            float aX, float aY, float aZ,
            float bX, float bY, float bZ,
            float cX, float cY, float cZ,
            Vector3f outWeights
    ) {
        float v0x = bX - aX;
        float v0y = bY - aY;
        float v0z = bZ - aZ;
        
        float v1x = cX - aX;
        float v1y = cY - aY;
        float v1z = cZ - aZ;
        
        float v2x = pX - aX;
        float v2y = pY - aY;
        float v2z = pZ - aZ;
        
        float d00 = dot(v0x, v0y, v0z, v0x, v0y, v0z);
        float d01 = dot(v0x, v0y, v0z, v1x, v1y, v1z);
        float d11 = dot(v1x, v1y, v1z, v1x, v1y, v1z);
        float d20 = dot(v2x, v2y, v2z, v0x, v0y, v0z);
        float d21 = dot(v2x, v2y, v2z, v1x, v1y, v1z);
        
        float invdenom = 1f / ((d00 * d11) - (d01 * d01));
        float v = ((d11 * d20) - (d01 * d21)) * invdenom;
        float w = ((d00 * d21) - (d01 * d20)) * invdenom;
        float u = 1f - v - w;
        
        outWeights.set(u, v, w);
    }
    
    private static float dot(float x0, float y0, float z0, float x1, float y1, float z1) {
        return x0 * x1 + y0 * y1 + z0 * z1;
    }
    
    private RasterUtils() {

    }

}
