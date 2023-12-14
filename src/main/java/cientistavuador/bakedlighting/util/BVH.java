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
import org.joml.RayAabIntersection;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class BVH implements Aab {

    public static final int MIN_AMOUNT_OF_INDICES = 2 * 3;

    private static boolean isLooping(BVH e) {
        BVH parent = e.getParent();
        if (parent == null) {
            return false;
        }
        BVH parentParent = parent.getParent();
        if (parentParent == null) {
            return false;
        }

        int currentIndices = e.getAmountOfIndices();
        int parentIndices = parent.getAmountOfIndices();
        int parentParentIndices = parentParent.getAmountOfIndices();

        return currentIndices == parentIndices && currentIndices == parentParentIndices;
    }

    private static void maxmin(float[] vertices, int vertexSize, int xyzOffset, Vector3f outMin, Vector3f outMax) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int v = 0; v < vertices.length; v += vertexSize) {
            float x = vertices[v + xyzOffset + 0];
            float y = vertices[v + xyzOffset + 1];
            float z = vertices[v + xyzOffset + 2];
            minX = Math.min(x, minX);
            minY = Math.min(y, minY);
            minZ = Math.min(z, minZ);
            maxX = Math.max(x, maxX);
            maxY = Math.max(y, maxY);
            maxZ = Math.max(z, maxZ);
        }

        outMin.set(minX, minY, minZ);
        outMax.set(maxX, maxY, maxZ);
    }

    public static BVH create(float[] vertices, int vertexSize, int xyzOffset, int[] indices) {
        if (vertices.length == 0) {
            BVH e = new BVH(vertices, vertexSize, xyzOffset, new Vector3f(), new Vector3f());
            e.indices = new int[0];
            return e;
        }
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();

        maxmin(vertices, vertexSize, xyzOffset, min, max);

        BVH top = new BVH(vertices, vertexSize, xyzOffset, min, max);
        top.indices = indices.clone();
        top.amountOfIndices = top.indices.length;

        Queue<BVH> processing = new ArrayDeque<>();
        List<BVH> toProcess = new ArrayList<>();

        processing.add(top);

        int axis = 0;

        Vector3f leftMin = new Vector3f();
        Vector3f leftMax = new Vector3f();
        Vector3f rightMin = new Vector3f();
        Vector3f rightMax = new Vector3f();

        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();

        int[] newIndices = new int[top.getIndices().length];
        int newIndicesIndex = 0;

        do {
            BVH parent;
            while ((parent = processing.poll()) != null) {
                parent.getMin(min);
                parent.getMax(max);

                float value = (min.get(axis) * 0.5f) + (max.get(axis) * 0.5f);

                leftMin.set(min);
                leftMax.set(max);
                rightMin.set(min);
                rightMax.set(max);

                leftMin.setComponent(axis, value);
                rightMax.setComponent(axis, value);

                BVH left = new BVH(vertices, vertexSize, xyzOffset, leftMin, leftMax);
                left.parent = parent;
                BVH right = new BVH(vertices, vertexSize, xyzOffset, rightMin, rightMax);
                right.parent = parent;

                parent.left = left;
                parent.right = right;

                BVH check = left;
                for (int i = 0; i < 2; i++) {
                    if (i == 1) {
                        check = right;
                    }
                    newIndicesIndex = 0;
                    for (int v = 0; v < parent.indices.length; v += 3) {
                        int i0 = parent.indices[v + 0];
                        int i1 = parent.indices[v + 1];
                        int i2 = parent.indices[v + 2];

                        float x0 = vertices[(i0 * vertexSize) + xyzOffset + 0];
                        float y0 = vertices[(i0 * vertexSize) + xyzOffset + 1];
                        float z0 = vertices[(i0 * vertexSize) + xyzOffset + 2];

                        float x1 = vertices[(i1 * vertexSize) + xyzOffset + 0];
                        float y1 = vertices[(i1 * vertexSize) + xyzOffset + 1];
                        float z1 = vertices[(i1 * vertexSize) + xyzOffset + 2];

                        float x2 = vertices[(i2 * vertexSize) + xyzOffset + 0];
                        float y2 = vertices[(i2 * vertexSize) + xyzOffset + 1];
                        float z2 = vertices[(i2 * vertexSize) + xyzOffset + 2];

                        a.set(x0, y0, z0);
                        b.set(x1, y1, z1);
                        c.set(x2, y2, z2);

                        if (IntersectionUtils.testTriangleAab(a, b, c, check.getMin(), check.getMax())) {
                            newIndices[newIndicesIndex + 0] = i0;
                            newIndices[newIndicesIndex + 1] = i1;
                            newIndices[newIndicesIndex + 2] = i2;
                            newIndicesIndex += 3;
                        }
                    }
                    check.indices = Arrays.copyOf(newIndices, newIndicesIndex);
                    check.amountOfIndices = check.indices.length;
                }

                parent.indices = null;

                if (left.getAmountOfIndices() > MIN_AMOUNT_OF_INDICES && !isLooping(left)) {
                    toProcess.add(left);
                }
                if (right.getAmountOfIndices() > MIN_AMOUNT_OF_INDICES && !isLooping(right)) {
                    toProcess.add(right);
                }
            }
            processing.addAll(toProcess);
            toProcess.clear();
            axis++;
            if (axis >= 3) {
                axis = 0;
            }
        } while (!processing.isEmpty());

        return top;
    }

    public static final float EPSILON = 0.002f;

    public static BVH createAlternative(float[] vertices, int vertexSize, int xyzOffset, int[] indices) {
        if (vertices.length == 0) {
            return new BVH(vertices, vertexSize, xyzOffset, new Vector3f(), new Vector3f());
        }
        
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

            BVH e = new BVH(vertices, vertexSize, xyzOffset,
                    minX - EPSILON, minY - EPSILON, minZ - EPSILON,
                    maxX + EPSILON, maxY + EPSILON, maxZ + EPSILON
            );
            e.amountOfIndices = 3;
            e.indices = new int[]{indices[i + 0], indices[i + 1], indices[i + 2]};
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

                    output = new BVH(vertices, vertexSize, xyzOffset, minX, minY, minZ, maxX, maxY, maxZ);
                    output.amountOfIndices = bvh.amountOfIndices + closest.amountOfIndices;
                    output.left = bvh;
                    output.right = closest;

                    bvh.parent = output;
                    closest.parent = output;
                } else {
                    output = new BVH(vertices, vertexSize, xyzOffset, bvh.getMin(), bvh.getMax());
                    output.amountOfIndices = bvh.amountOfIndices;
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
    private final int vertexSize;
    private final int xyzOffset;
    
    private final Vector3f min = new Vector3f();
    private final Vector3f max = new Vector3f();
    
    private BVH parent;
    private BVH left;
    private BVH right;
    private int amountOfIndices = 0;
    private int[] indices = null;

    private BVH(float[] vertices, int vertexSize, int xyzOffset, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.vertices = vertices;
        this.vertexSize = vertexSize;
        this.xyzOffset = xyzOffset;
        this.min.set(minX, minY, minZ);
        this.max.set(maxX, maxY, maxZ);
    }

    private BVH(float[] vertices, int vertexSize, int xyzOffset, Vector3fc min, Vector3fc max) {
        this(vertices, vertexSize, xyzOffset, min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
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

    public int getAmountOfIndices() {
        return amountOfIndices;
    }

    public int[] getIndices() {
        return indices;
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

    private class Triangle {
        private int i0;
        private int i1;
        private int i2;

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Triangle other = (Triangle) obj;
            if (this.i0 != other.i0) {
                return false;
            }
            if (this.i1 != other.i1) {
                return false;
            }
            return this.i2 == other.i2;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 47 * hash + this.i0;
            hash = 47 * hash + this.i1;
            hash = 47 * hash + this.i2;
            return hash;
        }
        
    }
    
    public List<LocalRayResult> testRay(Vector3fc localOrigin, Vector3fc localDirection) {
        RayAabIntersection aabTest = new RayAabIntersection(localOrigin.x(), localOrigin.y(), localOrigin.z(), localDirection.x(), localDirection.y(), localDirection.z());
        List<LocalRayResult> results = new ArrayList<>();
        
        Set<Triangle> tested = new HashSet<>();
        
        Triangle check = new Triangle();
        Vector3f hitpoint = new Vector3f();
        
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();
        
        Vector3f ba = new Vector3f();
        Vector3f ca = new Vector3f();
        Vector3f cross = new Vector3f();
        
        Queue<BVH> queue = new ArrayDeque<>();
        List<BVH> next = new ArrayList<>();

        queue.add(this);
        
        do {
            BVH e;
            while ((e = queue.poll()) != null) {
                if (aabTest.test(e.getMin().x(), e.getMin().y(), e.getMin().z(), e.getMax().x(), e.getMax().y(), e.getMax().z())) {
                    if (e.getLeft() == null && e.getRight() == null) {
                        int[] nodeIndices = e.getIndices();
                        for (int i = 0; i < nodeIndices.length; i += 3) {
                            int i0 = nodeIndices[i + 0];
                            int i1 = nodeIndices[i + 1];
                            int i2 = nodeIndices[i + 2];
                            
                            check.i0 = i0;
                            check.i1 = i1;
                            check.i2 = i2;
                            
                            if (!tested.contains(check)) {
                                Triangle f = new Triangle();
                                f.i0 = i0;
                                f.i1 = i1;
                                f.i2 = i2;
                                tested.add(f);
                                
                                a.set(this.vertices[(i0 * vertexSize) + xyzOffset + 0], this.vertices[(i0 * vertexSize) + xyzOffset + 1], this.vertices[(i0 * vertexSize) + xyzOffset + 2]);
                                b.set(this.vertices[(i1 * vertexSize) + xyzOffset + 0], this.vertices[(i1 * vertexSize) + xyzOffset + 1], this.vertices[(i1 * vertexSize) + xyzOffset + 2]);
                                c.set(this.vertices[(i2 * vertexSize) + xyzOffset + 0], this.vertices[(i2 * vertexSize) + xyzOffset + 1], this.vertices[(i2 * vertexSize) + xyzOffset + 2]);
                                
                                float hit = IntersectionUtils.intersectRayTriangle(localOrigin, localDirection, a, b, c);
                                if (hit >= 0f) {
                                    ba.set(b).sub(a).normalize();
                                    ca.set(c).sub(a).normalize();
                                    cross.set(ba).cross(ca).normalize();
                                    
                                    boolean frontFace = cross.dot(localDirection) < 0f;
                                    
                                    hitpoint.set(localDirection).mul(hit).add(localOrigin);
                                    results.add(new LocalRayResult(localOrigin, localDirection, hitpoint, i0, i1, i2, frontFace));
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

        return results;
    }

}
