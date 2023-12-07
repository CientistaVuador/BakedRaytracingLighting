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

import cientistavuador.bakedlighting.resources.mesh.MeshData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
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

    private static void maxmin(MeshData mesh, Vector3f outMin, Vector3f outMax) {
        float[] vertices = mesh.getVertices();

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int v = 0; v < vertices.length; v += MeshData.SIZE) {
            float x = vertices[v + MeshData.XYZ_OFFSET + 0];
            float y = vertices[v + MeshData.XYZ_OFFSET + 1];
            float z = vertices[v + MeshData.XYZ_OFFSET + 2];
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

    public static BVH create(MeshData mesh) {
        float[] vertices = mesh.getVertices();
        if (vertices.length == 0) {
            BVH e = new BVH(mesh, null, new Vector3f(), new Vector3f());
            e.indices = new int[0];
            return e;
        }
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();

        maxmin(mesh, min, max);

        BVH top = new BVH(mesh, null, min, max);
        top.indices = mesh.getIndices().clone();
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

                BVH left = new BVH(mesh, parent, leftMin, leftMax);
                BVH right = new BVH(mesh, parent, rightMin, rightMax);

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

                        float x0 = vertices[(i0 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 0];
                        float y0 = vertices[(i0 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 1];
                        float z0 = vertices[(i0 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 2];

                        float x1 = vertices[(i1 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 0];
                        float y1 = vertices[(i1 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 1];
                        float z1 = vertices[(i1 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 2];

                        float x2 = vertices[(i2 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 0];
                        float y2 = vertices[(i2 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 1];
                        float z2 = vertices[(i2 * MeshData.SIZE) + MeshData.XYZ_OFFSET + 2];

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

    private final MeshData mesh;

    private final BVH parent;

    private final Vector3f min = new Vector3f();
    private final Vector3f max = new Vector3f();

    private BVH left;
    private BVH right;
    private int amountOfIndices = 0;
    private int[] indices = null;

    private BVH(MeshData mesh, BVH parent, Vector3fc min, Vector3fc max) {
        this.mesh = mesh;
        this.parent = parent;
        this.min.set(min);
        this.max.set(max);
    }

    public MeshData getMesh() {
        return mesh;
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

}
