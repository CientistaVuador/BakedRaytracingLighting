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
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class IntersectionUtils {

    public static boolean testRayAab(Vector3fc origin, Vector3fc dir, Vector3fc min, Vector3fc max) {
        return Intersectionf.testRayAab(origin, dir, min, max);
    }

    public static float intersectRayTriangle(Vector3fc origin, Vector3fc dir, Vector3fc a, Vector3fc b, Vector3fc c) {
        return Intersectionf.intersectRayTriangleFront(origin, dir, a, b, c, 1f / 10000f);
    }

    private static final ThreadLocal<Vector3f[]> threadLocalVectors = new ThreadLocal<>() {
        @Override
        protected Vector3f[] initialValue() {
            Vector3f[] vectors = new Vector3f[16];
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
        
        Vector3f box_center = min.lerp(max, 0.5f, vectors[index++]);

        float width = Math.abs(max.x() - min.x());
        float height = Math.abs(max.y() - min.y());
        float depth = Math.abs(max.z() - min.z());

        Vector3f box_extents = vectors[index++]
                .set(width, height, depth)
                .mul(0.5f);

        Vector3f v0 = a.sub(box_center, vectors[index++]);
        Vector3f v1 = b.sub(box_center, vectors[index++]);
        Vector3f v2 = c.sub(box_center, vectors[index++]);

        // Compute edge vectors for triangle
        Vector3f f0 = b.sub(a, vectors[index++]);
        Vector3f f1 = c.sub(b, vectors[index++]);
        Vector3f f2 = a.sub(c, vectors[index++]);

        Vector3f axis = vectors[index++];

        //// region Test axes a00..a22 (category 3)
        // Test axis a00
        Vector3f a00 = axis.set(0, -f0.z(), f0.y());
        float p0 = v0.dot(a00);
        float p1 = v1.dot(a00);
        float p2 = v2.dot(a00);
        float r = box_extents.y() * Math.abs(f0.z()) + box_extents.z() * Math.abs(f0.y());
        if (Math.max(-max(p0, p1, p2), min(p0, p1, p2)) > r) {
            return false;
        }

        // Test axis a01
        Vector3f a01 = axis.set(0, -f1.z(), f1.y());
        p0 = v0.dot(a01);
        p1 = v1.dot(a01);
        p2 = v2.dot(a01);
        r = box_extents.y() * Math.abs(f1.z()) + box_extents.z() * Math.abs(f1.y());
        if (Math.max(-max(p0, p1, p2), min(p0, p1, p2)) > r) {
            return false;
        }

        // Test axis a02
        Vector3f a02 = axis.set(0, -f2.z(), f2.y());
        p0 = v0.dot(a02);
        p1 = v1.dot(a02);
        p2 = v2.dot(a02);
        r = box_extents.y() * Math.abs(f2.z()) + box_extents.z() * Math.abs(f2.y());
        if (Math.max(-max(p0, p1, p2), min(p0, p1, p2)) > r) {
            return false;
        }

        // Test axis a10
        Vector3f a10 = axis.set(f0.z(), 0, -f0.x());
        p0 = v0.dot(a10);
        p1 = v1.dot(a10);
        p2 = v2.dot(a10);
        r = box_extents.x() * Math.abs(f0.z()) + box_extents.z() * Math.abs(f0.x());
        if (Math.max(-max(p0, p1, p2), min(p0, p1, p2)) > r) {
            return false;
        }

        // Test axis a11
        Vector3f a11 = axis.set(f1.z(), 0, -f1.x());
        p0 = v0.dot(a11);
        p1 = v1.dot(a11);
        p2 = v2.dot(a11);
        r = box_extents.x() * Math.abs(f1.z()) + box_extents.z() * Math.abs(f1.x());
        if (Math.max(-max(p0, p1, p2), min(p0, p1, p2)) > r) {
            return false;
        }

        // Test axis a12
        Vector3f a12 = axis.set(f2.z(), 0, -f2.x());
        p0 = v0.dot(a12);
        p1 = v1.dot(a12);
        p2 = v2.dot(a12);
        r = box_extents.x() * Math.abs(f2.z()) + box_extents.z() * Math.abs(f2.x());
        if (Math.max(-max(p0, p1, p2), min(p0, p1, p2)) > r) {
            return false;
        }

        // Test axis a20
        Vector3f a20 = axis.set(-f0.y(), f0.x(), 0);
        p0 = v0.dot(a20);
        p1 = v1.dot(a20);
        p2 = v2.dot(a20);
        r = box_extents.x() * Math.abs(f0.y()) + box_extents.y() * Math.abs(f0.x());
        if (Math.max(-max(p0, p1, p2), min(p0, p1, p2)) > r) {
            return false;
        }

        // Test axis a21
        Vector3f a21 = axis.set(-f1.y(), f1.x(), 0);
        p0 = v0.dot(a21);
        p1 = v1.dot(a21);
        p2 = v2.dot(a21);
        r = box_extents.x() * Math.abs(f1.y()) + box_extents.y() * Math.abs(f1.x());
        if (Math.max(-max(p0, p1, p2), min(p0, p1, p2)) > r) {
            return false;
        }

        // Test axis a22
        Vector3f a22 = axis.set(-f2.y(), f2.x(), 0);
        p0 = v0.dot(a22);
        p1 = v1.dot(a22);
        p2 = v2.dot(a22);
        r = box_extents.x() * Math.abs(f2.y()) + box_extents.y() * Math.abs(f2.x());
        if (Math.max(-max(p0, p1, p2), min(p0, p1, p2)) > r) {
            return false;
        }

        //// endregion
        //// region Test the three axes corresponding to the face normals of AABB b (category 1)
        // Exit if...
        // ... [-extents.X, extents.X] and [Min(v0.X,v1.X,v2.X), Max(v0.X,v1.X,v2.X)] do not overlap
        if (max(v0.x(), v1.x(), v2.x()) < -box_extents.x() || min(v0.x(), v1.x(), v2.x()) > box_extents.x()) {
            return false;
        }

        // ... [-extents.Y, extents.Y] and [Min(v0.Y,v1.Y,v2.Y), Max(v0.Y,v1.Y,v2.Y)] do not overlap
        if (max(v0.y(), v1.y(), v2.y()) < -box_extents.y() || min(v0.y(), v1.y(), v2.y()) > box_extents.y()) {
            return false;
        }

        // ... [-extents.Z, extents.Z] and [Min(v0.Z,v1.Z,v2.Z), Max(v0.Z,v1.Z,v2.Z)] do not overlap
        if (max(v0.z(), v1.z(), v2.z()) < -box_extents.z() || min(v0.z(), v1.z(), v2.z()) > box_extents.z()) {
            return false;
        }

        //// endregion
        //// region Test separating axis corresponding to triangle face normal (category 2)
        Vector3f plane_normal = f0.cross(f1, vectors[index++]);
        float plane_distance = Math.abs(plane_normal.dot(v0));

        // Compute the projection interval radius of b onto L(t) = b.c + t * p.n
        r = box_extents.x() * Math.abs(plane_normal.x()) + box_extents.y() * Math.abs(plane_normal.y()) + box_extents.z() * Math.abs(plane_normal.z());

        // Intersection occurs when plane distance falls within [-r,+r] interval
        if (plane_distance > r) {
            return false;
        }

        //// endregion
        return true;
    }
    
    private static float min(float a, float b, float c) {
        return Math.min(Math.min(a, b), c);
    }

    private static float max(float a, float b, float c) {
        return Math.max(Math.max(a, b), c);
    }

    private IntersectionUtils() {

    }
}
