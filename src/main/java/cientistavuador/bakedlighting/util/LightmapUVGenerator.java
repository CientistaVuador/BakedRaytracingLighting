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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.joml.Vector4f;
import org.joml.Vector4i;

/**
 *
 * @author Cien
 */
public class LightmapUVGenerator {

    public static Pair<float[], float[]> generateLightmapUV(float[] vertices, int vertexSize, int xyzOffset, int lightmapSize) {
        if ((lightmapSize > 0) && ((lightmapSize & (lightmapSize - 1)) != 0)) {
            throw new IllegalArgumentException("Lightmap size is not a power of two.");
        }
        if (vertices.length % vertexSize != 0) {
            throw new IllegalArgumentException("Wrong size.");
        }
        if (vertices.length % (3 * vertexSize) != 0) {
            throw new IllegalArgumentException("Not a triangulated mesh.");
        }
        if (vertices.length == 0) {
            return new Pair<>(new float[0], new float[0]);
        }
        return new LightmapUVGenerator(vertices, vertexSize, xyzOffset, lightmapSize).process();
    }
    
    public static final float PRECISION_FIX = 0.001f;
    public static final float MINIMUM_QUAD_SIZE = 2f;
    public static final float SQUARE_TOLERANCE = 0.15f;
    public static final float WIDTH_TOLERANCE = 0.10f;
    public static final int MAX_QUAD_GROUP_SIZE = 16;

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

        float triangleArea;

        float width;
        float height;
        float x;
        float y;

        int v0;
        int v1;
        int v2;
        boolean secondTriangle = false;
        int sv0 = -1;
        int sv1 = -1;
        int sv2 = -1;

        boolean grouped = false;
    }

    private class QuadsGroup {

        float triangleArea;
        float minimumSize;

        float size;
        final List<Quad> quads = new ArrayList<>();
    }

    private class QuadTree {

        float x;
        float y;
        float size;

        QuadTree bottomLeft;
        QuadTree bottomRight;
        QuadTree topLeft;
        QuadTree topRight;

        QuadsGroup quads;
    }

    private final float[] vertices;
    private final int vertexSize;
    private final int xyzOffset;
    private final int lightmapSize;

    private final Map<Vertex, List<Vertex>> mappedVertices = new HashMap<>();

    private final List<Quad> quads = new ArrayList<>();
    private final Set<Integer> processedTriangles = new HashSet<>();

    private final List<QuadsGroup> groupsList = new ArrayList<>();

    private QuadsGroup[] groups = null;

    private int lowestLevel = Integer.MAX_VALUE;
    private int highestLevel = Integer.MIN_VALUE;
    private final Map<Integer, List<QuadTree>> quadTreesMap = new HashMap<>();
    private QuadTree top;

    private float totalWidth = 0f;
    private float totalHeight = 0f;

    private LightmapUVGenerator(float[] vertices, int vertexSize, int xyzOffset, int lightmapSize) {
        this.vertices = vertices;
        this.vertexSize = vertexSize;
        this.xyzOffset = xyzOffset;
        this.lightmapSize = lightmapSize;
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

    private void findSideTriangle(int va, int vb, Vector4i output) {
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
            int currentTriangle = (vav.vertex / this.vertexSize) / 3;
            if (this.processedTriangles.contains(currentTriangle)) {
                continue;
            }
            sv0 = vav.vertex;
            for (Vertex vbv : vbVertices) {
                int otherTriangle = (vbv.vertex / this.vertexSize) / 3;
                if (otherTriangle == currentTriangle) {
                    sv2 = vbv.vertex;
                    striangle = currentTriangle;
                    break searchTriangle;
                }
            }
        }

        if (striangle != -1) {
            for (int i = 0; i < 3; i++) {
                int r = (striangle * 3 * this.vertexSize) + (this.vertexSize * i);
                if (r != sv0 && r != sv2) {
                    sv1 = r;
                    break;
                }
            }

            output.set(sv0, sv1, sv2, striangle);
            return;
        }
        output.set(-1, -1, -1, -1);
    }

    private void calculateArea(Vector4i triangle, Vector4f output) {
        int v0 = triangle.x();
        int v1 = triangle.y();
        int v2 = triangle.z();

        float v0x = this.vertices[v0 + this.xyzOffset + 0];
        float v0y = this.vertices[v0 + this.xyzOffset + 1];
        float v0z = this.vertices[v0 + this.xyzOffset + 2];

        float v1x = this.vertices[v1 + this.xyzOffset + 0];
        float v1y = this.vertices[v1 + this.xyzOffset + 1];
        float v1z = this.vertices[v1 + this.xyzOffset + 2];

        float v2x = this.vertices[v2 + this.xyzOffset + 0];
        float v2y = this.vertices[v2 + this.xyzOffset + 1];
        float v2z = this.vertices[v2 + this.xyzOffset + 2];

        float a = (float) Math.sqrt(Math.pow(v0x - v1x, 2.0) + Math.pow(v0y - v1y, 2.0) + Math.pow(v0z - v1z, 2.0));
        float b = (float) Math.sqrt(Math.pow(v1x - v2x, 2.0) + Math.pow(v1y - v2y, 2.0) + Math.pow(v1z - v2z, 2.0));
        float c = (float) Math.sqrt(Math.pow(v2x - v0x, 2.0) + Math.pow(v2y - v0y, 2.0) + Math.pow(v2z - v0z, 2.0));

        float aSquare = a * a;
        float bSquare = b * b;
        float cSquare = c * c;
        float abcSquare = (aSquare + bSquare - cSquare);
        abcSquare *= abcSquare;

        output.set(
                a,
                b,
                c,
                (float) (0.25f * Math.sqrt(4f * aSquare * bSquare - abcSquare))
        );
    }

    private void adjustQuad(Quad q, Vector4f area) {
        float v0v1size = area.x();
        float v2v0size = area.z();
        if (v2v0size > v0v1size) {
            int v1 = q.v1;
            int v2 = q.v2;
            int sv0 = q.sv0;
            int sv2 = q.sv2;

            q.v1 = v2;
            q.v2 = v1;
            q.sv0 = sv2;
            q.sv2 = sv0;

            float temp = v0v1size;
            v0v1size = v2v0size;
            v2v0size = temp;
        }
        q.width = v0v1size;
        q.height = v2v0size;
    }

    private void mapQuads() {
        Vector4i current = new Vector4i();
        Vector4i sideA = new Vector4i();
        Vector4i sideB = new Vector4i();
        Vector4i sideC = new Vector4i();

        Vector4f currentArea = new Vector4f();
        Vector4f sideAArea = new Vector4f();
        Vector4f sideBArea = new Vector4f();
        Vector4f sideCArea = new Vector4f();

        Vector4i[] sideTriangles = new Vector4i[]{sideA, sideB, sideC};
        Vector4f[] sideAreas = new Vector4f[]{sideAArea, sideBArea, sideCArea};

        for (int v = 0; v < this.vertices.length; v += (this.vertexSize * 3)) {
            int triangle = (v / this.vertexSize) / 3;
            if (this.processedTriangles.contains(triangle)) {
                continue;
            }
            this.processedTriangles.add(triangle);

            int v0 = v;
            int v1 = v + this.vertexSize;
            int v2 = v + (this.vertexSize * 2);

            current.set(v0, v1, v2, triangle);
            findSideTriangle(v0, v1, sideA);
            findSideTriangle(v1, v2, sideB);
            findSideTriangle(v2, v0, sideC);

            calculateArea(current, currentArea);

            for (int i = 0; i < sideTriangles.length; i++) {
                Vector4i sideTriangle = sideTriangles[i];
                Vector4f sideTriangleArea = sideAreas[i];
                if (sideTriangle.w() != -1) {
                    calculateArea(sideTriangle, sideTriangleArea);
                }
            }

            Vector4i best = null;
            Vector4f bestArea = null;
            int bestSide = 0;
            float bestSideSize = 0f;

            for (int i = 0; i < sideTriangles.length; i++) {
                Vector4i sideTriangle = sideTriangles[i];
                Vector4f sideTriangleArea = sideAreas[i];
                if (sideTriangle.w() != -1) {
                    if (sideTriangleArea.z() > bestSideSize) {
                        best = sideTriangle;
                        bestArea = sideTriangleArea;
                        bestSideSize = sideTriangleArea.z();
                        bestSide = i;
                    }
                }
            }

            Quad q = new Quad();
            this.quads.add(q);

            q.v0 = current.x();
            q.v1 = current.y();
            q.v2 = current.z();
            q.triangleArea = currentArea.w();

            float currentAreaX = currentArea.x();
            float currentAreaY = currentArea.y();
            float currentAreaZ = currentArea.z();

            if (best != null && bestArea != null) {
                switch (bestSide) {
                    case 0 -> {
                        q.v0 = current.z();
                        q.v1 = current.x();
                        q.v2 = current.y();
                        currentArea.set(
                                currentAreaZ,
                                currentAreaX,
                                currentAreaY
                        );
                    }
                    case 1 -> {
                        q.v0 = current.x();
                        q.v1 = current.y();
                        q.v2 = current.z();
                        currentArea.set(
                                currentAreaX,
                                currentAreaY,
                                currentAreaZ
                        );
                    }
                    case 2 -> {
                        q.v0 = current.y();
                        q.v1 = current.z();
                        q.v2 = current.x();
                        currentArea.set(
                                currentAreaY,
                                currentAreaZ,
                                currentAreaX
                        );
                    }
                }
                this.processedTriangles.add(best.w());
                q.secondTriangle = true;
                q.sv0 = best.x();
                q.sv1 = best.y();
                q.sv2 = best.z();
                q.triangleArea += bestArea.w();
            }

            adjustQuad(q, currentArea);
        }
    }

    private void calculateQuadAreas() {
        float totalArea = 0f;
        for (Quad q : this.quads) {
            totalArea += (q.width * q.height);
        }
        for (Quad q : this.quads) {
            float area = q.width * q.height;
            float size = (float) Math.sqrt(area);
            float aspectWidth = q.width / size;
            float aspectHeight = q.height / size;
            area = (area / totalArea) * (this.lightmapSize * this.lightmapSize);
            size = (float) Math.sqrt(area);
            float width = size * aspectWidth;
            float height = size * aspectHeight;
            q.width = (float) Math.pow(2.0, Math.round(Math.log(width) / Math.log(2.0)));
            q.height = (float) Math.pow(2.0, Math.round(Math.log(height) / Math.log(2.0)));
        }
    }

    private boolean isSquare(float width, float height) {
        float aspect = width / height;
        return aspect > (1f - SQUARE_TOLERANCE) && aspect < (1f + SQUARE_TOLERANCE);
    }

    private void groupQuads() {
        Comparator<Quad> comparator = (o1, o2) -> {
            if (o1.height > o2.height) {
                return 1;
            }
            if (o1.height < o2.height) {
                return -1;
            }
            return 0;
        };

        for (Quad q : this.quads) {
            if (q.grouped) {
                continue;
            }
            if (isSquare(q.width, q.height)) {
                QuadsGroup group = new QuadsGroup();
                group.size = q.width;
                group.minimumSize = MINIMUM_QUAD_SIZE;
                group.triangleArea = q.triangleArea;
                group.quads.add(q);

                this.groupsList.add(group);
                q.grouped = true;
                continue;
            }
            QuadsGroup group = new QuadsGroup();
            group.quads.add(q);
            q.grouped = true;

            float width = q.width;
            float height = q.height;

            List<Quad> closestWidth = new ArrayList<>();
            for (Quad e : this.quads) {
                if (e.grouped) {
                    continue;
                }
                float ratio = width / e.width;
                if (ratio > (1f - WIDTH_TOLERANCE) && ratio < (1f + WIDTH_TOLERANCE)) {
                    closestWidth.add(e);
                }
            }

            closestWidth.sort(comparator);

            for (Quad e : closestWidth) {
                if ((height + e.height) > width) {
                    break;
                }
                height += e.height;
                e.grouped = true;
                group.quads.add(e);
            }

            int closestPotSize = (int) Math.pow(2.0, Math.floor(Math.log(group.quads.size()) / Math.log(2.0)));
            
            if (closestPotSize > MAX_QUAD_GROUP_SIZE) {
                closestPotSize = MAX_QUAD_GROUP_SIZE;
            }
            
            if (closestPotSize != group.quads.size()) {
                List<Quad> accepted = new ArrayList<>();
                for (int i = 0; i < group.quads.size(); i++) {
                    Quad e = group.quads.get(i);
                    if (i < closestPotSize) {
                        accepted.add(e);
                    } else {
                        e.grouped = false;
                    }
                }
                group.quads.clear();
                group.quads.addAll(accepted);
            }

            group.minimumSize = group.quads.size() * MINIMUM_QUAD_SIZE;
            for (Quad e : group.quads) {
                group.triangleArea += e.triangleArea;
            }

            group.size = width;
            this.groupsList.add(group);
        }

        this.groups = this.groupsList.toArray(QuadsGroup[]::new);
        Comparator<QuadsGroup> groupComparator = (o1, o2) -> {
            return Float.compare(o1.triangleArea, o2.triangleArea);
        };
        Arrays.sort(this.groups, groupComparator.reversed());
    }

    private void adjustQuadGroups() {
        if (this.groups.length == 1) {
            this.groups[0].size = this.lightmapSize;
            return;
        }

        final float maxArea = this.lightmapSize * this.lightmapSize;
        float area = 0f;
        for (QuadsGroup q : this.groups) {
            if (q.size < q.minimumSize) {
                q.size = q.minimumSize;
            }
            if (q.size >= this.lightmapSize) {
                q.size = this.lightmapSize * 0.5f;
            }
            area += q.size * q.size;
        }

        float areaLeft = maxArea - area;

        if (areaLeft == 0f) {
            return;
        }

        if (areaLeft < 0) {
            boolean noChange = false;
            loop:
            while (!noChange) {
                noChange = true;
                for (int i = (this.groups.length - 1); i >= 0; i--) {
                    QuadsGroup q = this.groups[i];
                    float increaseInArea = (q.size * q.size) * (3f / 4f);
                    if (q.size > q.minimumSize) {
                        areaLeft += increaseInArea;
                        q.size *= 0.5f;
                        if (areaLeft >= 0) {
                            break loop;
                        }
                        noChange = false;
                    }
                }
            }
        }
        
        if (areaLeft < 0) {
            System.out.println("not enough resolution.");
        }
        
        boolean noChange = false;
        while (!noChange) {
            noChange = true;
            for (QuadsGroup q : this.groups) {
                float decreaseInArea = ((q.size * 2f) * (q.size * 2f)) * (3f / 4f);
                if ((areaLeft - decreaseInArea) >= 0f) {
                    areaLeft -= decreaseInArea;
                    q.size *= 2f;
                    noChange = false;
                }
            }
        }
    }

    private void createQuadTrees() {
        for (int i = 0; i < this.groups.length; i++) {
            QuadsGroup g = this.groups[i];

            int level = (int) Math.floor(Math.log(g.size) / Math.log(2.0));

            QuadTree tree = new QuadTree();
            tree.quads = g;
            tree.size = (float) Math.pow(2.0, level);

            List<QuadTree> levelList = this.quadTreesMap.get(level);
            if (levelList == null) {
                levelList = new ArrayList<>();
                this.quadTreesMap.put(level, levelList);
            }
            levelList.add(tree);

            this.lowestLevel = Math.min(this.lowestLevel, level);
            this.highestLevel = Math.max(this.highestLevel, level);
        }
    }

    private void connectQuadTrees() {
        int topLevel = 0;
        for (int i = this.lowestLevel; i < Integer.MAX_VALUE; i++) {
            float currentLevelSize = (float) Math.pow(2.0, i);
            int nextLevel = i + 1;
            float nextLevelSize = (float) Math.pow(2.0, nextLevel);

            List<QuadTree> treeList = this.quadTreesMap.get(i);
            List<QuadTree> nextLevelList = this.quadTreesMap.get(nextLevel);

            if (nextLevelList == null) {
                if (treeList.size() == 1 && i >= this.highestLevel) {
                    topLevel = i;
                    break;
                } else {
                    nextLevelList = new ArrayList<>();
                    this.quadTreesMap.put(nextLevel, nextLevelList);
                }
            }

            int listSize = treeList.size();
            for (int j = 0; j < listSize; j += 4) {
                QuadTree bottomLeft = null;
                QuadTree bottomRight = null;
                QuadTree topLeft = null;
                QuadTree topRight = null;
                getQuads:
                {
                    int index = j;
                    bottomLeft = treeList.get(index++);
                    if (index >= listSize) {
                        break getQuads;
                    }
                    bottomRight = treeList.get(index++);
                    if (index >= listSize) {
                        break getQuads;
                    }
                    topLeft = treeList.get(index++);
                    if (index >= listSize) {
                        break getQuads;
                    }
                    topRight = treeList.get(index++);
                }

                QuadTree upperTree = new QuadTree();
                upperTree.size = nextLevelSize;

                upperTree.bottomLeft = bottomLeft;
                bottomLeft.x = 0f;
                bottomLeft.y = 0f;

                if (bottomRight != null) {
                    upperTree.bottomRight = bottomRight;
                    bottomRight.x = currentLevelSize;
                    bottomRight.y = 0f;
                }

                if (topLeft != null) {
                    upperTree.topLeft = topLeft;
                    topLeft.x = 0f;
                    topLeft.y = currentLevelSize;
                }

                if (topRight != null) {
                    upperTree.topRight = topRight;
                    topRight.x = currentLevelSize;
                    topRight.y = currentLevelSize;
                }

                nextLevelList.add(upperTree);
            }
        }
        this.top = this.quadTreesMap.get(topLevel).get(0);
    }

    private void translateQuadTrees() {
        Queue<QuadTree> toProcess = new ArrayDeque<>();
        toProcess.add(this.top);

        QuadTree q;
        while ((q = toProcess.poll()) != null) {
            float xOffset = q.x;
            float yOffset = q.y;

            if (q.bottomLeft != null) {
                toProcess.add(q.bottomLeft);
                q.bottomLeft.x += xOffset;
                q.bottomLeft.y += yOffset;
            }
            if (q.bottomRight != null) {
                toProcess.add(q.bottomRight);
                q.bottomRight.x += xOffset;
                q.bottomRight.y += yOffset;
            }
            if (q.topLeft != null) {
                toProcess.add(q.topLeft);
                q.topLeft.x += xOffset;
                q.topLeft.y += yOffset;
            }
            if (q.topRight != null) {
                toProcess.add(q.topRight);
                q.topRight.x += xOffset;
                q.topRight.y += yOffset;
            }
        }
    }

    private void ungroupQuadTrees() {
        this.quads.clear();

        Queue<QuadTree> toProcess = new ArrayDeque<>();
        toProcess.add(this.top);

        QuadTree q;
        while ((q = toProcess.poll()) != null) {
            if (q.quads != null) {
                float size = q.size;
                List<Quad> quadsList = q.quads.quads;
                float heightPerQuad = size / quadsList.size();
                for (int i = 0; i < quadsList.size(); i++) {
                    Quad quad = quadsList.get(i);
                    quad.width = size;
                    quad.height = heightPerQuad;
                    quad.x = q.x;
                    quad.y = q.y + (heightPerQuad * i);
                    this.quads.add(quad);
                }

                this.totalHeight = Math.max(this.totalHeight, q.y + q.size);
                this.totalWidth = Math.max(this.totalWidth, q.x + q.size);
                continue;
            }

            if (q.bottomLeft != null) {
                toProcess.add(q.bottomLeft);
            }
            if (q.bottomRight != null) {
                toProcess.add(q.bottomRight);
            }
            if (q.topLeft != null) {
                toProcess.add(q.topLeft);
            }
            if (q.topRight != null) {
                toProcess.add(q.topRight);
            }
        }
    }

    private Pair<float[], float[]> outputUVs() {
        float[] forBake = new float[(this.vertices.length / this.vertexSize) * 2];
        float[] forRender = new float[(this.vertices.length / this.vertexSize) * 2];

        for (Quad q : this.quads) {
            int v0 = (q.v0 / this.vertexSize) * 2;
            int v1 = (q.v1 / this.vertexSize) * 2;
            int v2 = (q.v2 / this.vertexSize) * 2;
            int sv0 = (q.sv0 / this.vertexSize) * 2;
            int sv1 = (q.sv1 / this.vertexSize) * 2;
            int sv2 = (q.sv2 / this.vertexSize) * 2;

            final float offset = 0.5f;

            float invLightmapSize = 1f / this.lightmapSize;

            float minX = q.x;
            float minY = q.y;

            float maxX = q.x + q.width;
            float maxY = q.y + q.height;

            forBake[v0 + 0] = minX * invLightmapSize;
            forBake[v0 + 1] = minY * invLightmapSize;

            forBake[v1 + 0] = maxX * invLightmapSize;
            forBake[v1 + 1] = minY * invLightmapSize;

            forBake[v2 + 0] = minX * invLightmapSize;
            forBake[v2 + 1] = maxY * invLightmapSize;

            forRender[v0 + 0] = (minX + offset) * invLightmapSize;
            forRender[v0 + 1] = (minY + offset) * invLightmapSize;

            forRender[v1 + 0] = (maxX - offset) * invLightmapSize;
            forRender[v1 + 1] = (minY + offset) * invLightmapSize;

            forRender[v2 + 0] = (minX + offset) * invLightmapSize;
            forRender[v2 + 1] = (maxY - offset) * invLightmapSize;

            if (q.secondTriangle) {
                forBake[sv0 + 0] = maxX * invLightmapSize;
                forBake[sv0 + 1] = minY * invLightmapSize;

                forBake[sv1 + 0] = maxX * invLightmapSize;
                forBake[sv1 + 1] = maxY * invLightmapSize;

                forBake[sv2 + 0] = minX * invLightmapSize;
                forBake[sv2 + 1] = maxY * invLightmapSize;

                forRender[sv0 + 0] = (maxX - offset) * invLightmapSize;
                forRender[sv0 + 1] = (minY + offset) * invLightmapSize;

                forRender[sv1 + 0] = (maxX - offset) * invLightmapSize;
                forRender[sv1 + 1] = (maxY - offset) * invLightmapSize;

                forRender[sv2 + 0] = (minX + offset) * invLightmapSize;
                forRender[sv2 + 1] = (maxY - offset) * invLightmapSize;
            }

        }

        return new Pair<>(forBake, forRender);
    }

    public Pair<float[], float[]> process() {
        mapVertices();
        mapQuads();
        calculateQuadAreas();
        groupQuads();
        adjustQuadGroups();
        createQuadTrees();
        connectQuadTrees();
        translateQuadTrees();
        ungroupQuadTrees();
        return outputUVs();
    }

}
