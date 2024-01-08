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

import org.joml.Intersectionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class IntersectionUtils {

    private static final Vector3fc V_100 = new Vector3f(1f, 0f, 0f);
    private static final Vector3fc V_010 = new Vector3f(0f, 1f, 0f);
    private static final Vector3fc V_001 = new Vector3f(0f, 0f, 1f);

    public static boolean testRayAab(Vector3fc origin, Vector3fc dir, Vector3fc min, Vector3fc max) {
        return Intersectionf.testRayAab(origin, dir, min, max);
    }

    public static float intersectRayTriangle(Vector3fc origin, Vector3fc dir, Vector3fc a, Vector3fc b, Vector3fc c) {
        return Intersectionf.intersectRayTriangle(origin, dir, a, b, c, 1f / 100000f);
    }
    
    public static boolean testLineTriangle(
            float p0x, float p0y, float p0z,
            float p1x, float p1y, float p1z,
            float v0x, float v0y, float v0z,
            float v1x, float v1y, float v1z,
            float v2x, float v2y, float v2z
    ) {
        float dirX = p1x - p0x;
        float dirY = p1y - p0y;
        float dirZ = p1z - p0z;
        float test = Intersectionf.intersectRayTriangle(
                p0x, p0y, p0z,
                dirX, dirY, dirZ,
                v0x, v0y, v0z,
                v1x, v1y, v1z,
                v2x, v2y, v2z,
                1f / 100000f
        );
        return test >= 0f && test <= 1f;
    }
    
    public static boolean testLineTriangle(Vector3fc p0, Vector3fc p1, Vector3fc a, Vector3fc b, Vector3fc c) {
        return testLineTriangle(
                p0.x(), p0.y(), p0.z(),
                p1.x(), p1.y(), p1.z(),
                a.x(), a.y(), a.z(),
                b.x(), b.y(), b.z(),
                c.x(), c.y(), c.z()
        );
    }
    
    public static boolean testLineAab(
            float p0x, float p0y, float p0z,
            float p1x, float p1y, float p1z,
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ
    ) {
        return Intersectionf.intersectLineSegmentAab(
                p0x, p0y, p0z,
                p1x, p1y, p1z,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                new Vector2f()
        ) != Intersectionf.OUTSIDE;
    }
    
    public static boolean testLineAab(Vector3fc p0, Vector3fc p1, Vector3fc min, Vector3fc max) {
        return testLineAab(
                p0.x(), p0.y(), p0.z(),
                p1.x(), p1.y(), p1.z(),
                min.x(), min.y(), min.z(),
                max.x(), max.y(), max.z()
        );
    }
    
    private static final ThreadLocal<Vector3f[]> threadLocalVectors = new ThreadLocal<>() {
        @Override
        protected Vector3f[] initialValue() {
            Vector3f[] vectors = new Vector3f[24];
            for (int i = 0; i < vectors.length; i++) {
                vectors[i] = new Vector3f();
            }
            return vectors;
        }
    };

    //https://gist.github.com/zvonicek/fe73ba9903f49d57314cf7e8e0f05dcf
    public static boolean testTriangleAab(Vector3fc a, Vector3fc b, Vector3fc c, Vector3fc min, Vector3fc max) {
        Vector3f[] vectors = threadLocalVectors.get();
        int index = 0;

        Vector3f aabbCentre = vectors[index++].set(
                min.x() * 0.5f + max.x() * 0.5f,
                min.y() * 0.5f + max.y() * 0.5f,
                min.z() * 0.5f + max.z() * 0.5f
        );
        Vector3f aabbExtents = vectors[index++].set(
                Math.abs(max.x() - min.x()),
                Math.abs(max.y() - min.y()),
                Math.abs(max.z() - min.z())
        );

        Vector3f v0 = vectors[index++].set(a).sub(aabbCentre);
        Vector3f v1 = vectors[index++].set(b).sub(aabbCentre);
        Vector3f v2 = vectors[index++].set(c).sub(aabbCentre);

        Vector3f ab = vectors[index++].set(v1).sub(v0).normalize();
        Vector3f bc = vectors[index++].set(v2).sub(v1).normalize();
        Vector3f ca = vectors[index++].set(v0).sub(v2).normalize();

        //Cross ab, bc, and ca with (1, 0, 0)
        Vector3f a00 = vectors[index++].set(0.0f, -ab.z(), ab.y());
        Vector3f a01 = vectors[index++].set(0.0f, -bc.z(), bc.y());
        Vector3f a02 = vectors[index++].set(0.0f, -ca.z(), ca.y());

        //Cross ab, bc, and ca with (0, 1, 0)
        Vector3f a10 = vectors[index++].set(ab.z(), 0.0f, -ab.x());
        Vector3f a11 = vectors[index++].set(bc.z(), 0.0f, -bc.x());
        Vector3f a12 = vectors[index++].set(ca.z(), 0.0f, -ca.x());

        //Cross ab, bc, and ca with (0, 0, 1)
        Vector3f a20 = vectors[index++].set(-ab.y(), ab.x(), 0.0f);
        Vector3f a21 = vectors[index++].set(-bc.y(), bc.x(), 0.0f);
        Vector3f a22 = vectors[index++].set(-ca.y(), ca.x(), 0.0f);

        if (!AABB_Tri_SAT(v0, v1, v2, aabbExtents, a00)
                || !AABB_Tri_SAT(v0, v1, v2, aabbExtents, a01)
                || !AABB_Tri_SAT(v0, v1, v2, aabbExtents, a02)
                || !AABB_Tri_SAT(v0, v1, v2, aabbExtents, a10)
                || !AABB_Tri_SAT(v0, v1, v2, aabbExtents, a11)
                || !AABB_Tri_SAT(v0, v1, v2, aabbExtents, a12)
                || !AABB_Tri_SAT(v0, v1, v2, aabbExtents, a20)
                || !AABB_Tri_SAT(v0, v1, v2, aabbExtents, a21)
                || !AABB_Tri_SAT(v0, v1, v2, aabbExtents, a22)
                || !AABB_Tri_SAT(v0, v1, v2, aabbExtents, V_100)
                || !AABB_Tri_SAT(v0, v1, v2, aabbExtents, V_010)
                || !AABB_Tri_SAT(v0, v1, v2, aabbExtents, V_001)
                || !AABB_Tri_SAT(v0, v1, v2, aabbExtents, ab.cross(bc, vectors[index++]))) {
            return false;
        }
        return true;
    }

    private static boolean AABB_Tri_SAT(Vector3f v0, Vector3fc v1, Vector3fc v2, Vector3fc aabbExtents, Vector3fc axis) {
        float p0 = v0.dot(axis);
        float p1 = v1.dot(axis);
        float p2 = v2.dot(axis);

        float r = aabbExtents.x() * Math.abs(V_100.dot(axis))
                + aabbExtents.y() * Math.abs(V_010.dot(axis))
                + aabbExtents.z() * Math.abs(V_001.dot(axis));

        float maxP = Math.max(p0, Math.max(p1, p2));
        float minP = Math.min(p0, Math.min(p1, p2));

        return !(Math.max(-maxP, minP) > r);
    }

    private IntersectionUtils() {

    }
}
