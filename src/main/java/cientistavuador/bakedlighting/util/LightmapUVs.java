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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4i;

/**
 *
 * @author Cien
 */
public class LightmapUVs {

    public static class LightmapperQuad {

        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int[] triangles;
        private final float[] uvs;

        public LightmapperQuad(int x, int y, int width, int height, int[] triangles, float[] uvs) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.triangles = triangles;
            this.uvs = uvs;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int[] getTriangles() {
            return triangles;
        }

        public float[] getUVs() {
            return uvs;
        }
    }

    public static class GeneratorOutput {

        private final int lightmapSize;
        private final float[] uvs;
        private final LightmapperQuad[] quads;

        public GeneratorOutput(int lightmapSize, float[] uvs, LightmapperQuad[] quads) {
            this.lightmapSize = lightmapSize;
            this.uvs = uvs;
            this.quads = quads;
        }

        public int getLightmapSize() {
            return lightmapSize;
        }

        public LightmapperQuad[] getQuads() {
            return quads;
        }

        public float[] getUVs() {
            return uvs;
        }

    }

    public static GeneratorOutput generate(float[] vertices, int vertexSize, int xyzOffset, float pixelToWorldRatio, float scaleX, float scaleY, float scaleZ) {
        return new LightmapUVs(vertices, vertexSize, xyzOffset, pixelToWorldRatio, scaleX, scaleY, scaleZ).process();
    }

    private class Vertex {

        public int vertex;

        @Override
        public int hashCode() {
            int hash = 7;
            for (int i = 0; i < 3; i++) {
                hash = 79 * hash + Float.floatToRawIntBits(LightmapUVs.this.vertices[this.vertex + i]);
            }
            return hash;
        }

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
            final Vertex other = (Vertex) obj;
            for (int i = 0; i < 3; i++) {
                float t = LightmapUVs.this.vertices[this.vertex + i];
                float o = LightmapUVs.this.vertices[other.vertex + i];
                if (t != o) {
                    return false;
                }
            }
            return true;
        }
    }

    private class Face {

        public Vector3f normal;
        public int[] triangles;
        public float[] uvs;
        public float width;
        public float height;
    }

    private class AttachmentPoint {

        public final int x;
        public final int y;

        public AttachmentPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 31 * hash + this.x;
            hash = 31 * hash + this.y;
            return hash;
        }

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
            final AttachmentPoint other = (AttachmentPoint) obj;
            if (this.x != other.x) {
                return false;
            }
            return this.y == other.y;
        }
    }

    private class Quad {

        public Face face;
        public int x;
        public int y;
        public int width;
        public int height;

        public boolean rotate90 = false;
    }

    private class QuadBVH {

        public int minX;
        public int minY;
        public int maxX;
        public int maxY;
        public QuadBVH left;
        public QuadBVH right;
        public Quad value;
    }

    private static final float EPSILON = 1f - (1f / 256f);
    private static final int VERTEX_SIZE = 3;
    public static final int MARGIN = 1;
    private static final int OPTIMIZATION_TRIGGER = 1024;
    public static volatile boolean MAINTAIN_ROTATION = false;

    private final float[] vertices;
    private final float pixelToWorldRatio;

    private final Map<Vertex, List<Vertex>> mappedVertices = new HashMap<>();
    private final Set<Integer> processedTriangles = new HashSet<>();

    private final List<Face> faces = new ArrayList<>();

    private final List<Quad> quads = new ArrayList<>();

    private final List<Quad> addedQuads = new ArrayList<>();
    private final List<QuadBVH> optimizedQuads = new ArrayList<>();
    private int unoptimizedStartIndex = 0;

    private final List<LightmapperQuad> lightmapperQuads = new ArrayList<>();
    private final boolean maintainRotation;

    private LightmapUVs(float[] vertices, int vertexSize, int xyzOffset, float pixelToWorldRatio, float scaleX, float scaleY, float scaleZ) {
        this.vertices = new float[(vertices.length / vertexSize) * VERTEX_SIZE];
        for (int v = 0; v < vertices.length; v += vertexSize) {
            int vertex = v / vertexSize;
            int vxyz = v + xyzOffset;
            this.vertices[(vertex * VERTEX_SIZE) + 0] = vertices[vxyz + 0] * scaleX;
            this.vertices[(vertex * VERTEX_SIZE) + 1] = vertices[vxyz + 1] * scaleY;
            this.vertices[(vertex * VERTEX_SIZE) + 2] = vertices[vxyz + 2] * scaleZ;
        }
        this.pixelToWorldRatio = pixelToWorldRatio;
        this.maintainRotation = MAINTAIN_ROTATION;
    }

    private void mapVertices() {
        for (int v = 0; v < this.vertices.length; v += VERTEX_SIZE) {
            Vertex e = new Vertex();
            e.vertex = v;
            List<Vertex> verts = mappedVertices.get(e);
            if (verts == null) {
                verts = new ArrayList<>();
                mappedVertices.put(e, verts);
            }
            verts.add(e);
        }
    }

    private void buildFaces() {
        for (int v = 0; v < this.vertices.length; v += (VERTEX_SIZE * 3)) {
            int triangle = (v / VERTEX_SIZE) / 3;
            if (this.processedTriangles.contains(triangle)) {
                continue;
            }
            Face face = buildFace(triangle);
            if (face != null) {
                this.faces.add(face);
            }
        }
    }

    private void findNormal(int triangle, Vector3f outNormal) {
        int v0 = ((triangle * 3) + 0) * VERTEX_SIZE;
        int v1 = ((triangle * 3) + 1) * VERTEX_SIZE;
        int v2 = ((triangle * 3) + 2) * VERTEX_SIZE;

        float v0x = this.vertices[v0 + 0];
        float v0y = this.vertices[v0 + 1];
        float v0z = this.vertices[v0 + 2];

        float v1x = this.vertices[v1 + 0];
        float v1y = this.vertices[v1 + 1];
        float v1z = this.vertices[v1 + 2];

        float v2x = this.vertices[v2 + 0];
        float v2y = this.vertices[v2 + 1];
        float v2z = this.vertices[v2 + 2];

        outNormal
                .set(v1x, v1y, v1z)
                .sub(v0x, v0y, v0z)
                .normalize();

        float v1v0x = outNormal.x();
        float v1v0y = outNormal.y();
        float v1v0z = outNormal.z();

        outNormal
                .set(v2x, v2y, v2z)
                .sub(v0x, v0y, v0z)
                .normalize();

        float v2v0x = outNormal.x();
        float v2v0y = outNormal.y();
        float v2v0z = outNormal.z();

        outNormal
                .set(v1v0x, v1v0y, v1v0z)
                .cross(v2v0x, v2v0y, v2v0z)
                .normalize();
    }

    private void findEdgeTriangle(int va, int vb, Vector4i output, Set<Integer> ignoreSet) {
        int striangle = -1;

        Vertex e = new Vertex();
        e.vertex = va;
        List<Vertex> vaVertices = this.mappedVertices.get(e);
        e.vertex = vb;
        List<Vertex> vbVertices = this.mappedVertices.get(e);

        int sv0 = -1;
        int sv1 = -1;
        int sv2 = -1;

        searchTriangle:
        for (Vertex vav : vaVertices) {
            int currentTriangle = (vav.vertex / VERTEX_SIZE) / 3;
            if (this.processedTriangles.contains(currentTriangle)) {
                continue;
            }
            if (ignoreSet.contains(currentTriangle)) {
                continue;
            }
            sv0 = vav.vertex;
            for (Vertex vbv : vbVertices) {
                int otherTriangle = (vbv.vertex / VERTEX_SIZE) / 3;
                if (otherTriangle == currentTriangle) {
                    sv1 = vbv.vertex;
                    striangle = currentTriangle;
                    break searchTriangle;
                }
            }
        }

        if (striangle != -1) {
            for (int i = 0; i < 3; i++) {
                int r = (striangle * 3 * VERTEX_SIZE) + (VERTEX_SIZE * i);
                if (r != sv0 && r != sv1) {
                    sv2 = r;
                    break;
                }
            }

            output.set(sv0, sv1, sv2, striangle);
            return;
        }
        output.set(-1, -1, -1, -1);
    }

    private Face buildFace(int triangle) {
        this.processedTriangles.add(triangle);

        Vector3f outNormal = new Vector3f();
        findNormal(triangle, outNormal);
        float normalX = outNormal.x();
        float normalY = outNormal.y();
        float normalZ = outNormal.z();
        
        if (!outNormal.isFinite()) {
            return null;
        }
        
        Face face = new Face();
        
        Set<Integer> ignoreSet = new HashSet<>();
        Vector4i edgeTriangle = new Vector4i();

        int[] triangles = new int[64];
        int trianglesIndex = 1;
        triangles[0] = triangle;

        long v0 = (((triangle * 3) + 0) * VERTEX_SIZE);
        long v1 = (((triangle * 3) + 1) * VERTEX_SIZE);
        long v2 = (((triangle * 3) + 2) * VERTEX_SIZE);

        long[] edges = new long[]{
            (v0 << 32) | (v1),
            (v1 << 32) | (v2),
            (v2 << 32) | (v0)
        };

        while (edges.length > 0) {
            long[] nextEdges = new long[64];
            int nextEdgesIndex = 0;

            for (long currentEdge : edges) {
                int edgeV0 = (int) (currentEdge >> 32);
                int edgeV1 = (int) (currentEdge);

                while (true) {
                    findEdgeTriangle(edgeV0, edgeV1, edgeTriangle, ignoreSet);

                    if (edgeTriangle.w() == -1) {
                        break;
                    }

                    findNormal(edgeTriangle.w(), outNormal);
                    if (outNormal.isFinite() && outNormal.dot(normalX, normalY, normalZ) >= EPSILON) {
                        break;
                    }

                    ignoreSet.add(edgeTriangle.w());
                    edgeTriangle.set(-1, -1, -1, -1);
                }

                if (edgeTriangle.w() == -1) {
                    continue;
                }

                this.processedTriangles.add(edgeTriangle.w());

                long sv0 = edgeTriangle.x();
                long sv1 = edgeTriangle.y();
                long sv2 = edgeTriangle.z();

                long edge0 = (sv0 << 32) | (sv1);
                long edge1 = (sv1 << 32) | (sv2);
                long edge2 = (sv2 << 32) | (sv0);

                if ((nextEdgesIndex + 4) > nextEdges.length) {
                    nextEdges = Arrays.copyOf(nextEdges, nextEdges.length * 2);
                }
                nextEdges[nextEdgesIndex + 0] = currentEdge;
                nextEdges[nextEdgesIndex + 1] = edge0;
                nextEdges[nextEdgesIndex + 2] = edge1;
                nextEdges[nextEdgesIndex + 3] = edge2;
                nextEdgesIndex += 4;

                if (trianglesIndex >= triangles.length) {
                    triangles = Arrays.copyOf(triangles, triangles.length * 2);
                }
                triangles[trianglesIndex] = edgeTriangle.w();
                trianglesIndex++;
            }
            edges = Arrays.copyOf(nextEdges, nextEdgesIndex);
        }

        outNormal.set(normalX, normalY, normalZ);
        face.triangles = Arrays.copyOf(triangles, trianglesIndex);
        face.normal = outNormal;

        return face;
    }

    private void generateFacesUVs() {
        for (Face face : this.faces) {
            generateFaceUVs(face);
        }
    }

    private void findMinMax(float[] uvs, Vector4f output) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < uvs.length; i += 2) {
            float x = uvs[i + 0];
            float y = uvs[i + 1];

            minX = Math.min(x, minX);
            minY = Math.min(y, minY);

            maxX = Math.max(x, maxX);
            maxY = Math.max(y, maxY);
        }
        output.set(minX, minY, maxX, maxY);
    }

    private void rotate(float[] uvs, float angle) {
        float angleRadians = (float) Math.toRadians(angle);
        Vector3f vec = new Vector3f();
        for (int i = 0; i < uvs.length; i += 2) {
            float x = uvs[i + 0];
            float y = uvs[i + 1];

            vec.set(x, y, 0f).rotateZ(angleRadians);

            uvs[i + 0] = vec.x();
            uvs[i + 1] = vec.y();
        }
    }

    private void findBestRotation(float[] uvs, Vector4f minMaxOutput) {
        float bestRotation = 0f;
        findMinMax(uvs, minMaxOutput);
        float bestSmallerArea = (minMaxOutput.z() - minMaxOutput.x()) * (minMaxOutput.w() - minMaxOutput.y());

        float[] clone = uvs.clone();
        for (int i = 0; i < 89; i++) {
            rotate(clone, 1f);
            findMinMax(clone, minMaxOutput);

            float area = (minMaxOutput.z() - minMaxOutput.x()) * (minMaxOutput.w() - minMaxOutput.y());

            if (area < bestSmallerArea) {
                bestRotation = i + 1f;
                bestSmallerArea = area;
            }
        }

        rotate(uvs, bestRotation);
        findMinMax(uvs, minMaxOutput);
    }

    private void generateFaceUVs(Face face) {
        float upX = 0f;
        float upY = 1f;
        float upZ = 0f;

        if (Math.abs(face.normal.dot(upX, upY, upZ)) >= EPSILON) {
            upY = 0f;
            upX = 1f;
        }

        Matrix4f lookAt = new Matrix4f()
                .lookAt(
                        0f, 0f, 0f,
                        -face.normal.x(), -face.normal.y(), -face.normal.z(),
                        upX, upY, upZ
                );

        Vector3f position = new Vector3f();

        float[] uvs = new float[face.triangles.length * 2 * 3];
        face.uvs = uvs;
        if (uvs.length == 0) {
            return;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < face.triangles.length; i++) {
            int triangle = face.triangles[i];

            int v0 = ((triangle * 3) + 0) * VERTEX_SIZE;
            int v1 = ((triangle * 3) + 1) * VERTEX_SIZE;
            int v2 = ((triangle * 3) + 2) * VERTEX_SIZE;

            float v0x = this.vertices[v0 + 0];
            float v0y = this.vertices[v0 + 1];
            float v0z = this.vertices[v0 + 2];

            float v1x = this.vertices[v1 + 0];
            float v1y = this.vertices[v1 + 1];
            float v1z = this.vertices[v1 + 2];

            float v2x = this.vertices[v2 + 0];
            float v2y = this.vertices[v2 + 1];
            float v2z = this.vertices[v2 + 2];

            int uv0 = ((i * 3) + 0) * 2;
            int uv1 = ((i * 3) + 1) * 2;
            int uv2 = ((i * 3) + 2) * 2;

            lookAt.transformProject(position.set(v0x, v0y, v0z));

            float uv0x = position.x() * this.pixelToWorldRatio;
            float uv0y = position.y() * this.pixelToWorldRatio;

            lookAt.transformProject(position.set(v1x, v1y, v1z));

            float uv1x = position.x() * this.pixelToWorldRatio;
            float uv1y = position.y() * this.pixelToWorldRatio;

            lookAt.transformProject(position.set(v2x, v2y, v2z));

            float uv2x = position.x() * this.pixelToWorldRatio;
            float uv2y = position.y() * this.pixelToWorldRatio;

            uvs[uv0 + 0] = uv0x;
            uvs[uv0 + 1] = uv0y;

            uvs[uv1 + 0] = uv1x;
            uvs[uv1 + 1] = uv1y;

            uvs[uv2 + 0] = uv2x;
            uvs[uv2 + 1] = uv2y;

            minX = Math.min(Math.min(uv0x, uv1x), Math.min(uv2x, minX));
            minY = Math.min(Math.min(uv0y, uv1y), Math.min(uv2y, minY));
            maxX = Math.max(Math.max(uv0x, uv1x), Math.max(uv2x, maxX));
            maxY = Math.max(Math.max(uv0y, uv1y), Math.max(uv2y, maxY));
        }

        if (!this.maintainRotation) {
            for (int v = 0; v < uvs.length; v += 2) {
                int vx = v + 0;
                int vy = v + 1;
                uvs[vx] = (uvs[vx] - ((minX * 0.5f) + (maxX * 0.5f)));
                uvs[vy] = (uvs[vy] - ((minY * 0.5f) + (maxY * 0.5f)));
            }

            Vector4f minMax = new Vector4f();
            findBestRotation(uvs, minMax);

            minX = minMax.x();
            minY = minMax.y();
            maxX = minMax.z();
            maxY = minMax.w();
        }

        for (int v = 0; v < uvs.length; v += 2) {
            int vx = v + 0;
            int vy = v + 1;
            uvs[vx] = (uvs[vx] - minX);
            uvs[vy] = (uvs[vy] - minY);
        }

        face.width = Math.abs(maxX - minX);
        face.height = Math.abs(maxY - minY);
    }

    private void createQuads() {
        for (Face face : this.faces) {
            Quad quad = new Quad();
            quad.face = face;
            quad.width = (int) (Math.ceil(face.width));
            quad.height = (int) (Math.ceil(face.height));
            quad.width += (MARGIN * 2);
            quad.height += (MARGIN * 2);

            float moveCenterX = (quad.width - face.width) / 2f;
            float moveCenterY = (quad.height - face.height) / 2f;
            for (int v = 0; v < face.uvs.length; v += 2) {
                face.uvs[v + 0] += moveCenterX;
                face.uvs[v + 1] += moveCenterY;
            }

            this.quads.add(quad);
        }
        this.faces.clear();
    }

    private boolean testAabAab(
            int minXA, int minYA,
            int maxXA, int maxYA,
            int minXB, int minYB,
            int maxXB, int maxYB
    ) {
        return maxXA > minXB && maxYA > minYB
                && minXA < maxXB && minYA < maxYB;
    }

    private QuadBVH generateBVH(List<Quad> quads) {
        QuadBVH[] current = new QuadBVH[quads.size()];
        int currentLength = current.length;

        for (int i = 0; i < current.length; i++) {
            QuadBVH bvh = new QuadBVH();
            Quad quad = quads.get(i);
            bvh.value = quad;
            bvh.minX = quad.x;
            bvh.minY = quad.y;

            int width = quad.width;
            int height = quad.height;
            if (quad.rotate90) {
                width = quad.height;
                height = quad.width;
            }

            bvh.maxX = quad.x + width;
            bvh.maxY = quad.y + height;
            current[i] = bvh;
        }

        QuadBVH[] next = new QuadBVH[current.length];
        int nextIndex = 0;

        while (currentLength != 1) {
            for (int i = 0; i < currentLength; i++) {
                QuadBVH bvh = current[i];

                if (bvh == null) {
                    continue;
                }

                int minX = bvh.minX;
                int minY = bvh.minY;
                int maxX = bvh.maxX;
                int maxY = bvh.maxY;

                float centerX = ((minX + 0.5f) * 0.5f) + ((maxX + 0.5f) * 0.5f);
                float centerY = ((minY + 0.5f) * 0.5f) + ((maxY + 0.5f) * 0.5f);

                QuadBVH closest = null;
                float closestDistance = 0f;
                int closestIndex = 0;
                for (int j = 0; j < currentLength; j++) {
                    QuadBVH other = current[j];

                    if (i == j) {
                        continue;
                    }

                    if (other == null) {
                        continue;
                    }

                    float otherCenterX = ((other.minX + 0.5f) * 0.5f) + ((other.maxX + 0.5f) * 0.5f);
                    float otherCenterY = ((other.minY + 0.5f) * 0.5f) + ((other.maxY + 0.5f) * 0.5f);

                    float dX = centerX - otherCenterX;
                    float dY = centerY - otherCenterY;

                    float dist = (float) Math.sqrt((dX * dX) + (dY * dY));

                    if (dist < closestDistance || closest == null) {
                        closest = other;
                        closestDistance = dist;
                        closestIndex = j;
                    }
                }

                current[i] = null;

                if (closest == null) {
                    next[nextIndex++] = bvh;
                    continue;
                }

                current[closestIndex] = null;

                QuadBVH merge = new QuadBVH();
                merge.left = bvh;
                merge.right = closest;
                merge.minX = Math.min(Math.min(bvh.minX, closest.minX), Math.min(bvh.maxX, closest.maxX));
                merge.minY = Math.min(Math.min(bvh.minY, closest.minY), Math.min(bvh.maxY, closest.maxY));
                merge.maxX = Math.max(Math.max(bvh.minX, closest.minX), Math.max(bvh.maxX, closest.maxX));
                merge.maxY = Math.max(Math.max(bvh.minY, closest.minY), Math.max(bvh.maxY, closest.maxY));
                next[nextIndex++] = merge;
            }

            QuadBVH[] currentStore = current;

            current = next;
            next = currentStore;

            currentLength = nextIndex;
            nextIndex = 0;
        }

        return current[0];
    }

    private boolean checkCollision(QuadBVH bvh, int minX, int minY, int maxX, int maxY) {
        if (testAabAab(
                minX, minY, maxX, maxY,
                bvh.minX, bvh.minY, bvh.maxX, bvh.maxY)) {
            if (bvh.value != null) {
                return true;
            }

            boolean left = bvh.left != null && checkCollision(bvh.left, minX, minY, maxX, maxY);
            boolean right = bvh.right != null && checkCollision(bvh.right, minX, minY, maxX, maxY);

            return left || right;
        }
        return false;
    }

    private boolean inCollision(int minX, int minY, int maxX, int maxY) {
        for (QuadBVH bvh : this.optimizedQuads) {
            if (checkCollision(bvh, minX, minY, maxX, maxY)) {
                return true;
            }
        }

        for (int i = this.unoptimizedStartIndex; i < this.addedQuads.size(); i++) {
            Quad other = this.addedQuads.get(i);

            int otherMinX = other.x;
            int otherMinY = other.y;

            int otherMaxX;
            int otherMaxY;
            if (!other.rotate90) {
                otherMaxX = other.x + other.width;
                otherMaxY = other.y + other.height;
            } else {
                otherMaxX = other.x + other.height;
                otherMaxY = other.y + other.width;
            }

            if (testAabAab(
                    minX, minY, maxX, maxY,
                    otherMinX, otherMinY, otherMaxX, otherMaxY
            )) {
                return true;
            }
        }
        return false;
    }

    private boolean inCollision(Quad q) {
        int x = q.x;
        int y = q.y;
        int width = q.width;
        int height = q.height;
        if (q.rotate90) {
            width = q.height;
            height = q.width;
        }

        int minX = x;
        int minY = y;
        int maxX = x + width;
        int maxY = y + height;

        return inCollision(minX, minY, maxX, maxY);
    }

    private AttachmentPoint findBestAttachmentPoint(Quad q, Set<AttachmentPoint> attachments, int currentWidth, int currentHeight) {
        AttachmentPoint best = null;
        boolean rotated = false;
        int bestArea = 0;
        for (AttachmentPoint p : attachments) {
            q.x = p.x;
            q.y = p.y;

            int width = Math.max(q.x + q.width, currentWidth);
            int height = Math.max(q.y + q.height, currentHeight);
            int widthRotated = Math.max(q.x + q.height, currentWidth);
            int heightRotated = Math.max(q.y + q.width, currentHeight);

            int area = width * height;
            int areaRotated = widthRotated * heightRotated;

            q.rotate90 = area > areaRotated;

            int finalArea = q.rotate90 ? areaRotated : area;

            if (finalArea < bestArea || best == null) {
                if (area == areaRotated) {
                    q.rotate90 = false;
                    if (!inCollision(q)) {
                        rotated = false;
                    } else {
                        q.rotate90 = true;
                        if (inCollision(q)) {
                            continue;
                        }
                        rotated = true;
                    }
                } else {
                    if (inCollision(q)) {
                        continue;
                    }
                    rotated = q.rotate90;
                }
                best = p;
                bestArea = finalArea;
            }

        }
        if (best != null) {
            q.x = best.x;
            q.y = best.y;
            q.rotate90 = rotated;
            return best;
        }
        return null;
    }

    private boolean isUseless(AttachmentPoint p, int smallestSize) {
        int minX = p.x;
        int minY = p.y;
        int maxX = p.x + smallestSize;
        int maxY = p.y + smallestSize;

        return inCollision(minX, minY, maxX, maxY);
    }

    private void fitQuads() {
        if (this.quads.isEmpty()) {
            return;
        }

        int smallestSize = Integer.MAX_VALUE;
        for (Quad q : this.quads) {
            smallestSize = Math.min(smallestSize, Math.min(q.width, q.height));
        }

        Comparator<Quad> comparator = (o1, o2) -> {
            int o1Area = o1.width * o1.height;
            int o2Area = o2.width * o2.height;
            return Integer.compare(o1Area, o2Area);
        };
        this.quads.sort(comparator.reversed());

        Quad first = this.quads.get(0);
        this.addedQuads.add(first);

        Set<AttachmentPoint> topPoints = new HashSet<>();
        Set<AttachmentPoint> rightPoints = new HashSet<>();

        topPoints.add(new AttachmentPoint(0, first.height));
        rightPoints.add(new AttachmentPoint(first.width, 0));

        int currentWidth = first.width;
        int currentHeight = first.height;

        for (int i = 1; i < this.quads.size(); i++) {
            Quad q = this.quads.get(i);

            Set<AttachmentPoint> priority;
            Set<AttachmentPoint> fallback;

            if (currentWidth > currentHeight) {
                priority = topPoints;
                fallback = rightPoints;
            } else {
                priority = rightPoints;
                fallback = topPoints;
            }

            Set<AttachmentPoint> usedSet;

            AttachmentPoint point = findBestAttachmentPoint(q, priority, currentWidth, currentHeight);
            usedSet = priority;
            if (point == null) {
                point = findBestAttachmentPoint(q, fallback, currentWidth, currentHeight);
                usedSet = fallback;
            }

            if (point == null) {
                throw new RuntimeException("Lighmap UV Generator: Something really went wrong, could not a place a quad in a infinity space.");
            }

            usedSet.remove(point);

            int x = q.x;
            int y = q.y;
            int width = q.width;
            int height = q.height;
            if (q.rotate90) {
                width = q.height;
                height = q.width;
            }

            topPoints.add(new AttachmentPoint(x, y + height));
            rightPoints.add(new AttachmentPoint(x + width, y));

            this.addedQuads.add(q);

            currentWidth = Math.max(currentWidth, x + width);
            currentHeight = Math.max(currentHeight, y + height);

            if ((this.addedQuads.size() - this.unoptimizedStartIndex) >= OPTIMIZATION_TRIGGER) {
                QuadBVH bvh = generateBVH(this.addedQuads.subList(this.unoptimizedStartIndex, this.unoptimizedStartIndex + OPTIMIZATION_TRIGGER));
                this.optimizedQuads.add(bvh);
                this.unoptimizedStartIndex += OPTIMIZATION_TRIGGER;

                List<AttachmentPoint> removeTop = new ArrayList<>();
                for (AttachmentPoint p : topPoints) {
                    if (isUseless(p, smallestSize)) {
                        removeTop.add(p);
                    }
                }

                List<AttachmentPoint> removeRight = new ArrayList<>();
                for (AttachmentPoint p : rightPoints) {
                    if (isUseless(p, smallestSize)) {
                        removeRight.add(p);
                    }
                }

                for (AttachmentPoint top : removeTop) {
                    topPoints.remove(top);
                }

                for (AttachmentPoint right : removeRight) {
                    rightPoints.remove(right);
                }
            }
        }
    }

    private void rotateUVsAndQuads() {
        for (Quad q : this.addedQuads) {
            float[] uvs = q.face.uvs;

            for (int v = 0; v < uvs.length; v += 2) {
                float x;
                float y;

                if (!q.rotate90) {
                    x = uvs[v + 0];
                    y = uvs[v + 1];
                } else {
                    x = uvs[v + 1];
                    y = uvs[v + 0];
                }

                uvs[v + 0] = x;
                uvs[v + 1] = y;
            }

            if (q.rotate90) {
                int width = q.width;
                int height = q.height;
                q.width = height;
                q.height = width;
            }

            q.rotate90 = false;
        }
    }

    private void generateLightmapperQuads() {
        for (Quad q : this.addedQuads) {
            int x = q.x;
            int y = q.y;
            int width = q.width;
            int height = q.height;

            this.lightmapperQuads.add(new LightmapperQuad(
                    x, y, width, height,
                    q.face.triangles, q.face.uvs
            ));
        }
    }

    private GeneratorOutput output() {
        int lightmapSize = 0;
        for (LightmapperQuad q : this.lightmapperQuads) {
            lightmapSize = Math.max(lightmapSize, Math.max(q.x + q.width, q.y + q.height));
        }

        float[] uvs = new float[(this.vertices.length / VERTEX_SIZE) * 2];

        float invLightmapSize = 1f / lightmapSize;
        for (LightmapperQuad q : this.lightmapperQuads) {
            for (int i = 0; i < q.triangles.length; i++) {
                int localv0 = ((i * 3) + 0) * 2;
                int localv1 = ((i * 3) + 1) * 2;
                int localv2 = ((i * 3) + 2) * 2;

                float localv0x = q.uvs[localv0 + 0];
                float localv0y = q.uvs[localv0 + 1];

                float localv1x = q.uvs[localv1 + 0];
                float localv1y = q.uvs[localv1 + 1];

                float localv2x = q.uvs[localv2 + 0];
                float localv2y = q.uvs[localv2 + 1];

                int globalTriangle = q.triangles[i];

                int globalv0 = ((globalTriangle * 3) + 0) * 2;
                int globalv1 = ((globalTriangle * 3) + 1) * 2;
                int globalv2 = ((globalTriangle * 3) + 2) * 2;

                uvs[globalv0 + 0] = (localv0x + q.x) * invLightmapSize;
                uvs[globalv0 + 1] = (localv0y + q.y) * invLightmapSize;

                uvs[globalv1 + 0] = (localv1x + q.x) * invLightmapSize;
                uvs[globalv1 + 1] = (localv1y + q.y) * invLightmapSize;

                uvs[globalv2 + 0] = (localv2x + q.x) * invLightmapSize;
                uvs[globalv2 + 1] = (localv2y + q.y) * invLightmapSize;
            }
        }

        return new GeneratorOutput(
                lightmapSize,
                uvs,
                this.lightmapperQuads.toArray(LightmapperQuad[]::new)
        );
    }

    private GeneratorOutput process() {
        mapVertices();
        buildFaces();
        generateFacesUVs();
        createQuads();
        fitQuads();
        rotateUVsAndQuads();
        generateLightmapperQuads();
        return output();
    }

}
