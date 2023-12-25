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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import org.joml.Intersectionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class BVH implements Aab {

    public static BVH create(float[] vertices, int[] indices, int vertexSize, int xyzOffset) {
        if (vertices.length == 0) {
            return new BVH(vertices, indices, vertexSize, xyzOffset, new Vector3f(), new Vector3f());
        }

        final float aabOffset = 0.0001f;

        BVH[] currentArray = new BVH[indices.length / 3];
        for (int i = 0; i < indices.length; i += 3) {
            int v0 = (indices[i + 0] * vertexSize) + xyzOffset;
            int v1 = (indices[i + 1] * vertexSize) + xyzOffset;
            int v2 = (indices[i + 2] * vertexSize) + xyzOffset;

            float minX = Math.min(vertices[v0 + 0], Math.min(vertices[v1 + 0], vertices[v2 + 0]));
            float minY = Math.min(vertices[v0 + 1], Math.min(vertices[v1 + 1], vertices[v2 + 1]));
            float minZ = Math.min(vertices[v0 + 2], Math.min(vertices[v1 + 2], vertices[v2 + 2]));
            float maxX = Math.max(vertices[v0 + 0], Math.max(vertices[v1 + 0], vertices[v2 + 0]));
            float maxY = Math.max(vertices[v0 + 1], Math.max(vertices[v1 + 1], vertices[v2 + 1]));
            float maxZ = Math.max(vertices[v0 + 2], Math.max(vertices[v1 + 2], vertices[v2 + 2]));

            if (Math.abs(maxX - minX) < aabOffset) {
                minX -= aabOffset;
                maxX += aabOffset;
            }

            if (Math.abs(maxY - minY) < aabOffset) {
                minY -= aabOffset;
                maxY += aabOffset;
            }

            if (Math.abs(maxZ - minZ) < aabOffset) {
                minZ -= aabOffset;
                maxZ += aabOffset;
            }

            BVH e = new BVH(
                    vertices,
                    indices,
                    vertexSize,
                    xyzOffset,
                    minX, minY, minZ,
                    maxX, maxY, maxZ
            );
            e.amountOfTriangles = 1;
            e.triangles = new int[]{i / 3};
            currentArray[i / 3] = e;
        }
        BVH[] nextArray = new BVH[(int) Math.ceil(currentArray.length / 2.0)];

        while (currentArray.length != 1) {
            for (int i = 0; i < currentArray.length; i += 2) {
                BVH bvh = currentArray[i];

                BVH output;
                if ((i + 1) < currentArray.length) {
                    float centerX = bvh.getMin().x() * 0.5f + bvh.getMax().x() * 0.5f;
                    float centerY = bvh.getMin().y() * 0.5f + bvh.getMax().y() * 0.5f;
                    float centerZ = bvh.getMin().z() * 0.5f + bvh.getMax().z() * 0.5f;

                    BVH closest = null;
                    float distance = Float.POSITIVE_INFINITY;
                    int index = 0;
                    for (int j = (i + 1); j < currentArray.length; j++) {
                        BVH other = currentArray[j];

                        float otherCenterX = other.getMin().x() * 0.5f + other.getMax().x() * 0.5f;
                        float otherCenterY = other.getMin().y() * 0.5f + other.getMax().y() * 0.5f;
                        float otherCenterZ = other.getMin().z() * 0.5f + other.getMax().z() * 0.5f;

                        float currentDistance = (float) Math.sqrt(Math.pow(centerX - otherCenterX, 2.0) + Math.pow(centerY - otherCenterY, 2.0) + Math.pow(centerZ - otherCenterZ, 2.0));

                        if (currentDistance < distance) {
                            distance = currentDistance;
                            closest = other;
                            index = j;
                        }
                    }
                    if (closest == null) {
                        throw new NullPointerException("Impossible NPE!");
                    }
                    currentArray[index] = currentArray[i + 1];

                    float minX = Math.min(Math.min(bvh.getMin().x(), closest.getMin().x()), Math.min(bvh.getMax().x(), closest.getMax().x()));
                    float minY = Math.min(Math.min(bvh.getMin().y(), closest.getMin().y()), Math.min(bvh.getMax().y(), closest.getMax().y()));
                    float minZ = Math.min(Math.min(bvh.getMin().z(), closest.getMin().z()), Math.min(bvh.getMax().z(), closest.getMax().z()));
                    float maxX = Math.max(Math.max(bvh.getMin().x(), closest.getMin().x()), Math.max(bvh.getMax().x(), closest.getMax().x()));
                    float maxY = Math.max(Math.max(bvh.getMin().y(), closest.getMin().y()), Math.max(bvh.getMax().y(), closest.getMax().y()));
                    float maxZ = Math.max(Math.max(bvh.getMin().z(), closest.getMin().z()), Math.max(bvh.getMax().z(), closest.getMax().z()));

                    output = new BVH(vertices, indices, vertexSize, xyzOffset, minX, minY, minZ, maxX, maxY, maxZ);
                    output.amountOfTriangles = bvh.amountOfTriangles + closest.amountOfTriangles;
                    output.left = bvh;
                    output.right = closest;

                    bvh.parent = output;
                    closest.parent = output;
                } else {
                    output = new BVH(vertices, indices, vertexSize, xyzOffset, bvh.getMin(), bvh.getMax());
                    output.amountOfTriangles = bvh.amountOfTriangles;
                    output.left = bvh;
                    bvh.parent = output;
                }

                nextArray[i / 2] = output;
            }
            currentArray = nextArray;
            nextArray = new BVH[(int) Math.ceil(currentArray.length / 2.0)];
        }

        return currentArray[0];
    }

    private final float[] vertices;
    private final int[] indices;
    private final int vertexSize;
    private final int xyzOffset;

    private final Vector3f min = new Vector3f();
    private final Vector3f max = new Vector3f();

    private BVH parent;
    private BVH left;
    private BVH right;
    private int amountOfTriangles = 0;
    private int[] triangles = null;

    private BVH(float[] vertices, int[] indices, int vertexSize, int xyzOffset, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.vertices = vertices;
        this.indices = indices;
        this.vertexSize = vertexSize;
        this.xyzOffset = xyzOffset;
        this.min.set(minX, minY, minZ);
        this.max.set(maxX, maxY, maxZ);
    }

    private BVH(float[] vertices, int[] indices, int vertexSize, int xyzOffset, Vector3fc min, Vector3fc max) {
        this(vertices, indices, vertexSize, xyzOffset, min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    public float[] getVertices() {
        return vertices;
    }

    public int getVertexSize() {
        return vertexSize;
    }

    public int getXyzOffset() {
        return xyzOffset;
    }

    public BVH getParent() {
        return parent;
    }

    public BVH getLeft() {
        return left;
    }

    public BVH getRight() {
        return right;
    }

    public int getAmountOfTriangles() {
        return amountOfTriangles;
    }

    public int[] getTriangles() {
        return triangles;
    }

    public Vector3fc getMin() {
        return min;
    }

    public Vector3fc getMax() {
        return max;
    }

    @Override
    public void getMin(Vector3f min) {
        min.set(this.min);
    }

    @Override
    public void getMax(Vector3f max) {
        max.set(this.max);
    }

    private boolean fastTestRay(Vector3f a, Vector3f b, Vector3f c, Set<Integer> tested, BVH e, Vector3fc localOrigin, Vector3fc localDirection) {
        if (Intersectionf.testRayAab(localOrigin, localDirection, e.getMin(), e.getMax())) {
            if (e.getLeft() == null && e.getRight() == null) {
                int[] nodeTriangles = e.getTriangles();
                for (int i = 0; i < nodeTriangles.length; i++) {
                    int triangle = nodeTriangles[i];

                    int v0xyz = (this.indices[(triangle * 3) + 0] * this.vertexSize) + this.xyzOffset;
                    int v1xyz = (this.indices[(triangle * 3) + 1] * this.vertexSize) + this.xyzOffset;
                    int v2xyz = (this.indices[(triangle * 3) + 2] * this.vertexSize) + this.xyzOffset;

                    if (!tested.contains(triangle)) {
                        tested.add(triangle);

                        a.set(
                                this.vertices[v0xyz + 0],
                                this.vertices[v0xyz + 1],
                                this.vertices[v0xyz + 2]
                        );
                        b.set(
                                this.vertices[v1xyz + 0],
                                this.vertices[v1xyz + 1],
                                this.vertices[v1xyz + 2]
                        );
                        c.set(
                                this.vertices[v2xyz + 0],
                                this.vertices[v2xyz + 1],
                                this.vertices[v2xyz + 2]
                        );

                        float hit = IntersectionUtils.intersectRayTriangle(localOrigin, localDirection, a, b, c);
                        if (hit >= 0f) {
                            return true;
                        }
                    }
                }
            }

            if (e.getLeft() != null) {
                if (fastTestRay(a, b, c, tested, e.getLeft(), localOrigin, localDirection)) {
                    return true;
                }
            }
            if (e.getRight() != null) {
                if (fastTestRay(a, b, c, tested, e.getRight(), localOrigin, localDirection)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean fastTestRay(Vector3fc localOrigin, Vector3fc localDirection) {
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();

        Set<Integer> tested = new HashSet<>();

        return fastTestRay(a, b, c, tested, this, localOrigin, localDirection);
    }

    public List<LocalRayResult> testRay(Vector3fc localOrigin, Vector3fc localDirection) {
        List<LocalRayResult> resultsOutput = new ArrayList<>();

        Vector3f normal = new Vector3f();
        Vector3f hitposition = new Vector3f();

        Set<Integer> tested = new HashSet<>();

        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();

        Queue<BVH> queue = new ArrayDeque<>();
        List<BVH> next = new ArrayList<>();

        queue.add(this);

        do {
            BVH e;
            while ((e = queue.poll()) != null) {
                if (Intersectionf.testRayAab(localOrigin, localDirection, e.getMin(), e.getMax())) {
                    if (e.getLeft() == null && e.getRight() == null) {
                        int[] nodeTriangles = e.getTriangles();
                        for (int i = 0; i < nodeTriangles.length; i++) {
                            int triangle = nodeTriangles[i];

                            int i0 = this.indices[(triangle * 3) + 0];
                            int i1 = this.indices[(triangle * 3) + 1];
                            int i2 = this.indices[(triangle * 3) + 2];

                            int v0xyz = (i0 * this.vertexSize) + this.xyzOffset;
                            int v1xyz = (i1 * this.vertexSize) + this.xyzOffset;
                            int v2xyz = (i2 * this.vertexSize) + this.xyzOffset;

                            if (!tested.contains(triangle)) {
                                tested.add(triangle);

                                a.set(
                                        this.vertices[v0xyz + 0],
                                        this.vertices[v0xyz + 1],
                                        this.vertices[v0xyz + 2]
                                );
                                b.set(
                                        this.vertices[v1xyz + 0],
                                        this.vertices[v1xyz + 1],
                                        this.vertices[v1xyz + 2]
                                );
                                c.set(
                                        this.vertices[v2xyz + 0],
                                        this.vertices[v2xyz + 1],
                                        this.vertices[v2xyz + 2]
                                );

                                float hit = IntersectionUtils.intersectRayTriangle(localOrigin, localDirection, a, b, c);
                                if (hit >= 0f) {
                                    MeshUtils.calculateTriangleNormal(
                                            this.vertices,
                                            this.vertexSize,
                                            this.xyzOffset,
                                            i0,
                                            i1,
                                            i2,
                                            normal
                                    );
                                    boolean frontFace = normal.dot(localDirection) < 0f;

                                    hitposition.set(localDirection).mul(hit).add(localOrigin);

                                    resultsOutput.add(new LocalRayResult(localOrigin, localDirection, hitposition, normal, triangle, frontFace));
                                }
                            }
                        }
                        continue;
                    }

                    if (e.getLeft() != null) {
                        next.add(e.getLeft());
                    }
                    if (e.getRight() != null) {
                        next.add(e.getRight());
                    }
                }
            }
            queue.addAll(next);
            next.clear();
        } while (!queue.isEmpty());

        return resultsOutput;
    }

}
