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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4i;

/**
 *
 * @author Cien
 */
public class ExperimentalLightmapUVGenerator {

    private class Vertex {
        public int vertex;

        @Override
        public int hashCode() {
            int hash = 7;
            for (int i = 0; i < 3; i++) {
                hash = 79 * hash + Float.floatToRawIntBits(ExperimentalLightmapUVGenerator.this.vertices[this.vertex + i]);
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
                float t = ExperimentalLightmapUVGenerator.this.vertices[this.vertex + i];
                float o = ExperimentalLightmapUVGenerator.this.vertices[other.vertex + i];
                if (t != o) {
                    return false;
                }
            }
            return true;
        }
    }

    public class Face {
        public Vector3f normal;
        public int[] triangles;
        public float[] uvs;
        public float width;
        public float height;
    }

    private static final float TOLERANCE = 1f - (1f / 256f);
    private static final int VERTEX_SIZE = 3;
    
    private final float pixelToWorldRatio;
    
    private final float[] vertices;

    private final Map<Vertex, List<Vertex>> mappedVertices = new HashMap<>();
    private final Set<Integer> processedTriangles = new HashSet<>();

    private final List<Face> faces = new ArrayList<>();

    public ExperimentalLightmapUVGenerator(float[] vertices, int vertexSize, int xyzOffset, Matrix4fc model, float pixelToWorldRatio) {
        this.vertices = new float[(vertices.length / vertexSize) * VERTEX_SIZE];
        Vector3f position = new Vector3f();
        for (int v = 0; v < vertices.length; v += vertexSize) {
            int vertex = v / vertexSize;
            int vxyz = v + xyzOffset;
            position.set(vertices[vxyz + 0], vertices[vxyz + 1], vertices[vxyz + 2]);
            if (model != null) {
                model.transformProject(position);
            }
            this.vertices[(vertex * VERTEX_SIZE) + 0] = position.x();
            this.vertices[(vertex * VERTEX_SIZE) + 1] = position.y();
            this.vertices[(vertex * VERTEX_SIZE) + 2] = position.z();
        }
        this.pixelToWorldRatio = pixelToWorldRatio;
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

    public void buildFaces() {
        for (int v = 0; v < this.vertices.length; v += (VERTEX_SIZE * 3)) {
            int triangle = (v / VERTEX_SIZE) / 3;
            if (this.processedTriangles.contains(triangle)) {
                continue;
            }
            this.faces.add(buildFace(triangle));
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

    public Face buildFace(int triangle) {
        this.processedTriangles.add(triangle);
        
        Set<Integer> ignoreSet = new HashSet<>();
        Vector4i edgeTriangle = new Vector4i();

        int[] triangles = new int[64];
        int trianglesIndex = 1;
        triangles[0] = triangle;

        Face face = new Face();

        Vector3f outNormal = new Vector3f();
        findNormal(triangle, outNormal);
        float normalX = outNormal.x();
        float normalY = outNormal.y();
        float normalZ = outNormal.z();

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
                    if (outNormal.dot(normalX, normalY, normalZ) >= TOLERANCE) {
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
    
    public void generateFacesUVs() {
        for (Face face:this.faces) {
            generateFaceUVs(face);
        }
    }
    
    public void generateFaceUVs(Face face) {
        float upX = 0f;
        float upY = 1f;
        float upZ = 0f;
        
        if (Math.abs(face.normal.dot(upX, upY, upZ)) >= TOLERANCE) {
            upY = 0f;
            upX = 1f;
        }
        
        Matrix4f lookAt = new Matrix4f()
                .lookAt(
                        0f, 0f, 0f,
                        -face.normal.x(), -face.normal.y(), -face.normal.z(),
                        upX, upY, upZ
                )
                ;
        
        Vector3f position = new Vector3f();
        
        float[] uvs = new float[face.triangles.length * 2 * 3];
        face.uvs = uvs;
        if (uvs.length == 0) {
            return;
        }
        
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float maxY = Float.MIN_VALUE;
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
        
        for (int v = 0; v < uvs.length; v += 2) {
            int vx = v + 0;
            int vy = v + 1;
            uvs[vx] = uvs[vx] - minX;
            uvs[vy] = uvs[vy] - minY;
        }
        
        face.width = Math.abs(maxX - minX);
        face.height = Math.abs(maxY - minY);
    }

    public List<Face> process() {
        mapVertices();
        buildFaces();
        generateFacesUVs();
        return this.faces;
    }

}
