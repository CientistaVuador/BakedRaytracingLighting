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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.joml.Intersectionf;

/**
 *
 * @author Cien
 */
public class LightmapUVGenerator {

    public static final int BASE_LIGHTMAP_SIZE = 64;
    public static final float PRECISION_FIX = 0.001f;
    public static final int MINIMUM_TRIANGLE_SIZE = 4;

    private class Vertex {

        int vertex;

        @Override
        public int hashCode() {
            int hash = 7;
            for (int i = 0; i < 3; i++) {
                hash = 79 * hash + Float.floatToRawIntBits(LightmapUVGenerator.this.vertices[this.vertex + LightmapUVGenerator.this.xyzOffset + i]);
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
                float t = LightmapUVGenerator.this.vertices[this.vertex + LightmapUVGenerator.this.xyzOffset + i];
                float o = LightmapUVGenerator.this.vertices[other.vertex + LightmapUVGenerator.this.xyzOffset + i];
                if (t != o) {
                    return false;
                }
            }
            return true;
        }
    }

    private class Quad {

        float x;
        float y;
        float area;
        float size;
        boolean topAvailable = true;
        boolean rightAvailable = true;
        int v0;
        int v1;
        int v2;
        boolean secondTriangle = false;
        int sv0 = -1;
        int sv1 = -1;
        int sv2 = -1;
        float width;
        float height;
        float margin;
    }

    private final float[] vertices;
    private final int vertexSize;
    private final int xyzOffset;
    private final int outUVOffset;
    private final Map<Vertex, List<Vertex>> mappedVertices = new HashMap<>();
    private final List<Quad> quads = new ArrayList<>();
    private final Set<Integer> processedTriangles = new HashSet<>();
    private float totalWidth = 0f;
    private float totalHeight = 0f;

    public LightmapUVGenerator(float[] vertices, int vertexSize, int xyzOffset, int outUVOffset) {
        this.vertices = vertices;
        this.vertexSize = vertexSize;
        this.xyzOffset = xyzOffset;
        this.outUVOffset = outUVOffset;
    }

    private void mapVertices() {
        for (int v = 0; v < this.vertices.length; v += this.vertexSize) {
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

    private void setToLargerSideAndCalculateArea(Quad q) {
        float v0x = vertices[q.v0 + this.xyzOffset + 0];
        float v0y = vertices[q.v0 + this.xyzOffset + 1];
        float v0z = vertices[q.v0 + this.xyzOffset + 2];

        float v1x = vertices[q.v1 + this.xyzOffset + 0];
        float v1y = vertices[q.v1 + this.xyzOffset + 1];
        float v1z = vertices[q.v1 + this.xyzOffset + 2];

        float v2x = vertices[q.v2 + this.xyzOffset + 0];
        float v2y = vertices[q.v2 + this.xyzOffset + 1];
        float v2z = vertices[q.v2 + this.xyzOffset + 2];

        float a = (float) Math.sqrt(Math.pow(v0x - v1x, 2.0) + Math.pow(v0y - v1y, 2.0) + Math.pow(v0z - v1z, 2.0));
        float b = (float) Math.sqrt(Math.pow(v1x - v2x, 2.0) + Math.pow(v1y - v2y, 2.0) + Math.pow(v1z - v2z, 2.0));
        float c = (float) Math.sqrt(Math.pow(v2x - v0x, 2.0) + Math.pow(v2y - v0y, 2.0) + Math.pow(v2z - v0z, 2.0));

        int v0 = q.v2;
        int v1 = q.v0;
        int v2 = q.v1;
        float largerSide = a;
        if (b > largerSide) {
            largerSide = b;
            v0 = q.v0;
            v1 = q.v1;
            v2 = q.v2;
        }
        if (c > largerSide) {
            v0 = q.v1;
            v1 = q.v2;
            v2 = q.v0;
        }

        q.v0 = v0;
        q.v1 = v1;
        q.v2 = v2;

        float sp = (a + b + c) * 0.5f;
        float area = (float) Math.sqrt(sp * (sp - a) * (sp - b) * (sp - c));

        q.area = area;
    }

    private void calculateAreaSecondTriangle(Quad q) {
        float v0x = vertices[q.sv0 + this.xyzOffset + 0];
        float v0y = vertices[q.sv0 + this.xyzOffset + 1];
        float v0z = vertices[q.sv0 + this.xyzOffset + 2];

        float v1x = vertices[q.sv1 + this.xyzOffset + 0];
        float v1y = vertices[q.sv1 + this.xyzOffset + 1];
        float v1z = vertices[q.sv1 + this.xyzOffset + 2];

        float v2x = vertices[q.sv2 + this.xyzOffset + 0];
        float v2y = vertices[q.sv2 + this.xyzOffset + 1];
        float v2z = vertices[q.sv2 + this.xyzOffset + 2];

        float a = (float) Math.sqrt(Math.pow(v0x - v1x, 2.0) + Math.pow(v0y - v1y, 2.0) + Math.pow(v0z - v1z, 2.0));
        float b = (float) Math.sqrt(Math.pow(v1x - v2x, 2.0) + Math.pow(v1y - v2y, 2.0) + Math.pow(v1z - v2z, 2.0));
        float c = (float) Math.sqrt(Math.pow(v2x - v0x, 2.0) + Math.pow(v2y - v0y, 2.0) + Math.pow(v2z - v0z, 2.0));

        float sp = (a + b + c) * 0.5f;
        float area = (float) Math.sqrt(sp * (sp - a) * (sp - b) * (sp - c));

        q.area += area;
    }

    private void mapQuads() {
        for (int v = 0; v < this.vertices.length; v += (this.vertexSize * 3)) {
            int triangle = (v / this.vertexSize) / 3;
            if (this.processedTriangles.contains(triangle)) {
                continue;
            }
            this.processedTriangles.add(triangle);

            int v0 = v;
            int v1 = v + this.vertexSize;
            int v2 = v + (this.vertexSize * 2);
            Quad q = new Quad();
            q.v0 = v0;
            q.v1 = v1;
            q.v2 = v2;

            setToLargerSideAndCalculateArea(q);

            int striangle = -1;

            Vertex e = new Vertex();
            e.vertex = q.v1;
            List<Vertex> v1Vertices = this.mappedVertices.get(e);
            e.vertex = q.v2;
            List<Vertex> v2Vertices = this.mappedVertices.get(e);

            int sv0 = -1;
            int sv1 = -1;
            int sv2 = -1;

            searchTriangle:
            for (Vertex v1v : v1Vertices) {
                striangle = (v1v.vertex / this.vertexSize) / 3;
                if (this.processedTriangles.contains(striangle)) {
                    striangle = -1;
                    continue;
                }
                sv0 = v1v.vertex;
                for (Vertex v2v : v2Vertices) {
                    int otherTriangle = (v2v.vertex / this.vertexSize) / 3;
                    if (otherTriangle == striangle) {
                        sv2 = v2v.vertex;
                        break searchTriangle;
                    }
                }
                striangle = -1;
            }

            if (striangle != -1) {
                for (int i = 0; i < 3; i++) {
                    int r = (striangle * 3 * this.vertexSize) + (this.vertexSize * i);
                    if (r != sv0 && r != sv2) {
                        sv1 = r;
                        break;
                    }
                }

                q.secondTriangle = true;
                q.sv0 = sv0;
                q.sv1 = sv1;
                q.sv2 = sv2;

                calculateAreaSecondTriangle(q);

                this.processedTriangles.add(striangle);
            }

            this.quads.add(q);
        }
    }

    private void calculateQuadAreasAndSort() {
        float totalArea = 0f;
        for (Quad q : this.quads) {
            totalArea += q.area;
        }
        for (Quad q : this.quads) {
            q.area = (q.area / totalArea) * (BASE_LIGHTMAP_SIZE * BASE_LIGHTMAP_SIZE);
            q.size = (float) Math.sqrt(q.area);
        }
        Comparator<Quad> comparator = (o1, o2) -> {
            if (o1.area < o2.area) {
                return 1;
            }
            if (o1.area > o2.area) {
                return -1;
            }
            return 0;
        };
        this.quads.sort(comparator);
    }

    private boolean testAab(float x, float y, float size, Quad other) {
        x += PRECISION_FIX;
        y += PRECISION_FIX;
        return Intersectionf.testAabAab(
                x, y, 0f, x + size, y + size, 0f,
                other.x, other.y, 0f, other.x + other.size, other.y + other.size, 0f
        );
    }

    private void connectQuads() {
        List<Quad> connectedQuads = new ArrayList<>();
        Queue<Quad> toProcess = new ArrayDeque<>(this.quads);
        float width = 0f;
        float height = 0f;
        boolean rightAxis = true;
        Quad q;
        while ((q = toProcess.poll()) != null) {
            if (connectedQuads.isEmpty()) {
                q.x = 0f;
                q.y = 0f;
                width = q.size;
                height = q.size;
                connectedQuads.add(q);
                continue;
            }

            for (Quad c : connectedQuads) {
                if (!c.rightAvailable && rightAxis) {
                    continue;
                }
                if (!c.topAvailable && !rightAxis) {
                    continue;
                }

                float x;
                float y;
                if (rightAxis) {
                    x = c.x + c.size;
                    y = c.y;
                } else {
                    x = c.x;
                    y = c.y + c.size;
                }

                boolean available = true;
                for (Quad o : connectedQuads) {
                    if (testAab(x, y, q.size, o)) {
                        available = false;
                        break;
                    }
                }
                if (available) {
                    q.x = x;
                    q.y = y;
                    if (rightAxis) {
                        c.rightAvailable = false;
                    } else {
                        c.topAvailable = false;
                    }
                    width = Math.max(width, q.x + q.size);
                    height = Math.max(height, q.y + q.size);
                    break;
                }
            }

            rightAxis = height > width;
            connectedQuads.add(q);
        }
        this.totalWidth = width;
        this.totalHeight = height;
    }

    private void fitQuads() {
        float size = BASE_LIGHTMAP_SIZE;
        float scaleX = size / this.totalWidth;
        float scaleY = size / this.totalHeight;
        for (Quad q : this.quads) {
            q.x *= scaleX;
            q.y *= scaleY;
            q.width = q.size * scaleX;
            q.height = q.size * scaleY;
            if ((q.x + q.width) > size) {
                q.width = size - q.x;
            }
            if ((q.y + q.height) > size) {
                q.height = size - q.y;
            }
        }
        this.totalWidth = size;
        this.totalHeight = size;
    }

    private void calculateMargin() {
        for (Quad q : this.quads) {
            float internalResolution = 1f;
            float width = q.width * internalResolution;
            float height = q.height * internalResolution;
            while ((width - 2f) < MINIMUM_TRIANGLE_SIZE && (height - 2f) < MINIMUM_TRIANGLE_SIZE) {
                internalResolution *= 2f;
                width *= 2f;
                height *= 2f;
            }
            q.margin = 1f / internalResolution;
        }
    }

    private void outputUVs() {
        for (Quad q : this.quads) {
            float invLightmapSize = 1f / BASE_LIGHTMAP_SIZE;

            float v0u = q.x + q.margin;
            float v0v = q.y + q.margin;

            float v1u = (q.x + q.width) - q.margin;
            float v1v = q.y + q.margin;

            float v2u = q.x + q.margin;
            float v2v = (q.y + q.height) - q.margin;

            this.vertices[q.v0 + this.outUVOffset + 0] = v0u * invLightmapSize;
            this.vertices[q.v0 + this.outUVOffset + 1] = v0v * invLightmapSize;

            this.vertices[q.v1 + this.outUVOffset + 0] = v1u * invLightmapSize;
            this.vertices[q.v1 + this.outUVOffset + 1] = v1v * invLightmapSize;

            this.vertices[q.v2 + this.outUVOffset + 0] = v2u * invLightmapSize;
            this.vertices[q.v2 + this.outUVOffset + 1] = v2v * invLightmapSize;

            if (q.secondTriangle) {
                v0u = (q.x + q.width) - q.margin;
                v0v = q.y + q.margin;

                v1u = (q.x + q.width) - q.margin;
                v1v = (q.y + q.height) - q.margin;

                v2u = q.x + q.margin;
                v2v = (q.y + q.height) - q.margin;

                this.vertices[q.sv0 + this.outUVOffset + 0] = v0u * invLightmapSize;
                this.vertices[q.sv0 + this.outUVOffset + 1] = v0v * invLightmapSize;

                this.vertices[q.sv1 + this.outUVOffset + 0] = v1u * invLightmapSize;
                this.vertices[q.sv1 + this.outUVOffset + 1] = v1v * invLightmapSize;

                this.vertices[q.sv2 + this.outUVOffset + 0] = v2u * invLightmapSize;
                this.vertices[q.sv2 + this.outUVOffset + 1] = v2v * invLightmapSize;
            }
        }
    }

    public void process() {
        mapVertices();
        mapQuads();
        calculateQuadAreasAndSort();
        connectQuads();
        fitQuads();
        calculateMargin();
        outputUVs();
    }

}
