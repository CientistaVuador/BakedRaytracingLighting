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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Cien
 */
public class MeshUtils {

    public static final float PRECISION_FIX = 1f / 500f;

    public static void generateTangent(float[] vertices, int vertexSize, int xyzOffset, int uvOffset, int outTangentXYZOffset) {
        if (vertices.length % vertexSize != 0) {
            throw new IllegalArgumentException("Wrong size.");
        }
        if (vertices.length % (3 * vertexSize) != 0) {
            throw new IllegalArgumentException("Not a triangulated mesh.");
        }
        for (int v = 0; v < vertices.length; v += (vertexSize * 3)) {
            int v0 = v;
            int v1 = v + vertexSize;
            int v2 = v + (vertexSize * 2);

            float v0x = vertices[v0 + xyzOffset + 0];
            float v0y = vertices[v0 + xyzOffset + 1];
            float v0z = vertices[v0 + xyzOffset + 2];
            float v0u = vertices[v0 + uvOffset + 0];
            float v0v = vertices[v0 + uvOffset + 1];

            float v1x = vertices[v1 + xyzOffset + 0];
            float v1y = vertices[v1 + xyzOffset + 1];
            float v1z = vertices[v1 + xyzOffset + 2];
            float v1u = vertices[v1 + uvOffset + 0];
            float v1v = vertices[v1 + uvOffset + 1];

            float v2x = vertices[v2 + xyzOffset + 0];
            float v2y = vertices[v2 + xyzOffset + 1];
            float v2z = vertices[v2 + xyzOffset + 2];
            float v2u = vertices[v2 + uvOffset + 0];
            float v2v = vertices[v2 + uvOffset + 1];

            float edge1x = v1x - v0x;
            float edge1y = v1y - v0y;
            float edge1z = v1z - v0z;

            float edge2x = v2x - v0x;
            float edge2y = v2y - v0y;
            float edge2z = v2z - v0z;

            float deltaUV1u = v1u - v0u;
            float deltaUV1v = v1v - v0v;

            float deltaUV2u = v2u - v0u;
            float deltaUV2v = v2v - v0v;

            float f = 1f / ((deltaUV1u * deltaUV2v) - (deltaUV2u * deltaUV1v));

            float tangentX = f * ((deltaUV2v * edge1x) - (deltaUV1v * edge2x));
            float tangentY = f * ((deltaUV2v * edge1y) - (deltaUV1v * edge2y));
            float tangentZ = f * ((deltaUV2v * edge1z) - (deltaUV1v * edge2z));

            float length = (float) (1.0 / Math.sqrt((tangentX * tangentX) + (tangentY * tangentY) + (tangentZ * tangentZ)));
            tangentX *= length;
            tangentY *= length;
            tangentZ *= length;

            vertices[v0 + outTangentXYZOffset + 0] = tangentX;
            vertices[v0 + outTangentXYZOffset + 1] = tangentY;
            vertices[v0 + outTangentXYZOffset + 2] = tangentZ;

            vertices[v1 + outTangentXYZOffset + 0] = tangentX;
            vertices[v1 + outTangentXYZOffset + 1] = tangentY;
            vertices[v1 + outTangentXYZOffset + 2] = tangentZ;

            vertices[v2 + outTangentXYZOffset + 0] = tangentX;
            vertices[v2 + outTangentXYZOffset + 1] = tangentY;
            vertices[v2 + outTangentXYZOffset + 2] = tangentZ;
        }
    }

    private static class Vertex {

        final float[] vertices;
        final int vertexSize;
        final int vertexIndex;
        final int vertexCount;

        public Vertex(float[] vertices, int vertexSize, int vertexIndex, int vertexCount) {
            this.vertices = vertices;
            this.vertexSize = vertexSize;
            this.vertexIndex = vertexIndex;
            this.vertexCount = vertexCount;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            for (int i = 0; i < this.vertexSize; i++) {
                hash = 27 * hash + Float.floatToRawIntBits(this.vertices[this.vertexIndex + i]);
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
            int indexA = this.vertexIndex;
            int indexB = other.vertexIndex;
            for (int i = 0; i < this.vertexSize; i++) {
                if (this.vertices[indexA + i] != this.vertices[indexB + i]) {
                    return false;
                }
            }
            return true;
        }
    }

    public static Pair<float[], int[]> generateIndices(float[] vertices, int vertexSize) {
        Map<Vertex, Vertex> verticesMap = new HashMap<>();

        float[] verticesIndexed = new float[64];
        int verticesIndexedIndex = 0;

        int[] indices = new int[64];
        int indicesIndex = 0;

        int vertexCount = 0;

        for (int v = 0; v < vertices.length; v += vertexSize) {
            Vertex current = new Vertex(vertices, vertexSize, v, vertexCount);
            Vertex other = verticesMap.get(current);

            if (other != null) {
                if (indicesIndex >= indices.length) {
                    indices = Arrays.copyOf(indices, indices.length * 2);
                }
                indices[indicesIndex] = other.vertexCount;
                indicesIndex++;
                continue;
            }

            verticesMap.put(current, current);

            if ((verticesIndexedIndex + vertexSize) > verticesIndexed.length) {
                verticesIndexed = Arrays.copyOf(verticesIndexed, verticesIndexed.length * 2);
            }
            System.arraycopy(vertices, v, verticesIndexed, verticesIndexedIndex, vertexSize);
            verticesIndexedIndex += vertexSize;

            if (indicesIndex >= indices.length) {
                indices = Arrays.copyOf(indices, indices.length * 2);
            }
            indices[indicesIndex] = vertexCount;
            indicesIndex++;

            vertexCount++;
        }

        return new Pair<>(
                Arrays.copyOf(verticesIndexed, verticesIndexedIndex),
                Arrays.copyOf(indices, indicesIndex)
        );
    }
    
    public static Pair<float[], int[]> unindex(float[] vertices, int[] indices, int vertexSize) {
        float[] unindexedVertices = new float[indices.length * vertexSize];
        int[] unindexedIndices = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            System.arraycopy(vertices, indices[i] * vertexSize, unindexedVertices, i * vertexSize, vertexSize);
            unindexedIndices[i] = i;
        }
        return new Pair<>(unindexedVertices, unindexedIndices);
    }
    
    public static Pair<float[], float[]> generateLightmapUV(float[] vertices, int vertexSize, int xyzOffset, int lightmapSize) {
        return LightmapUVGenerator.generateLightmapUV(vertices, vertexSize, xyzOffset, lightmapSize);
    }
    
    private MeshUtils() {

    }

}
