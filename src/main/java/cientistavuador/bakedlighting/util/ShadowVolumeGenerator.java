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
import java.util.List;
import java.util.Map;
import org.joml.Vector2i;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public class ShadowVolumeGenerator {

    //must be flat shaded
    //the first 3 floats must be the position XYZ
    //slightly based on https://gamedev.stackexchange.com/questions/106742/generate-mesh-of-shadow-volume
    public static int[] generateShadowVolumeIndices(float[] vertices, int vertexSize, int[] indices) {
        //1-map the indices by the positions
        Map<Vector3f, List<Vector2i>> positionMap = new HashMap<>();
        for (int i = 0; i < indices.length; i++) {
            int vertexIndex = indices[i] * vertexSize;

            Vector3f position = new Vector3f(
                    vertices[vertexIndex + 0],
                    vertices[vertexIndex + 1],
                    vertices[vertexIndex + 2]
            );
            List<Vector2i> indexList = positionMap.get(position);
            if (indexList == null) {
                indexList = new ArrayList<>();
                positionMap.put(position, indexList);
            }
            indexList.add(new Vector2i(indices[i], i / 3));
        }

        //2-generate quads between the triangles with the same positions.
        Vector3f cache = new Vector3f();
        int[] generatedIndices = new int[indices.length * 4];
        int generatedIndicesIndex = 0;
        for (int i = 0; i < indices.length / 3; i++) {
            int vertA = indices[(i * 3) + 0];
            int vertB = indices[(i * 3) + 1];
            int vertC = indices[(i * 3) + 2];

            if ((generatedIndicesIndex + 3) > generatedIndices.length) {
                generatedIndices = Arrays.copyOf(generatedIndices, (generatedIndices.length * 2) + 3);
            }

            generatedIndices[generatedIndicesIndex + 0] = vertA;
            generatedIndices[generatedIndicesIndex + 1] = vertB;
            generatedIndices[generatedIndicesIndex + 2] = vertC;

            generatedIndicesIndex += 3;

            for (int j = 0; j < 3; j++) {
                int vA = 0;
                int vB = 0;
                
                switch (j) {
                    case 0 -> {
                        vA = vertA;
                        vB = vertB;
                    }
                    case 1 -> {
                        vA = vertB;
                        vB = vertC;
                    }
                    case 2 -> {
                        vA = vertC;
                        vB = vertA;
                    }
                }
                
                cache.set(
                        vertices[(vB * vertexSize) + 0],
                        vertices[(vB * vertexSize) + 1],
                        vertices[(vB * vertexSize) + 2]
                );
                List<Vector2i> indexList = positionMap.get(cache);
                for (Vector2i vX : indexList) {
                    if (vX.x() == vB && vX.y() == i) {
                        continue;
                    }
                    
                    cache.set(
                            vertices[(vA * vertexSize) + 0],
                            vertices[(vA * vertexSize) + 1],
                            vertices[(vA * vertexSize) + 2]
                    );
                    List<Vector2i> otherList = positionMap.get(cache);
                    boolean found = false;
                    for (Vector2i o : otherList) {
                        if (o.y() == vX.y()) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        continue;
                    }

                    if ((generatedIndicesIndex + 3) > generatedIndices.length) {
                        generatedIndices = Arrays.copyOf(generatedIndices, (generatedIndices.length * 2) + 3);
                    }

                    generatedIndices[generatedIndicesIndex + 0] = vA;
                    generatedIndices[generatedIndicesIndex + 1] = vX.x();
                    generatedIndices[generatedIndicesIndex + 2] = vB;

                    generatedIndicesIndex += 3;
                }
            }
            
        }

        //3-done
        return Arrays.copyOf(generatedIndices, generatedIndicesIndex);
    }

    private ShadowVolumeGenerator() {

    }
}
