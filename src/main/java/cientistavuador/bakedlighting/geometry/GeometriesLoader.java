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
package cientistavuador.bakedlighting.geometry;

import cientistavuador.bakedlighting.resources.mesh.MeshData;
import cientistavuador.bakedlighting.resources.mesh.MeshResources;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 *
 * @author Cien
 */
public class GeometriesLoader {

    public static final boolean DEBUG_OUTPUT = true;

    public static Map<String, MeshData> load(String... names) {
        if (names.length == 0) {
            if (DEBUG_OUTPUT) {
                System.out.println("No geometries to load.");
            }
            return new HashMap<>();
        }

        if (DEBUG_OUTPUT) {
            System.out.println("Loading geometries...");
        }

        ArrayDeque<Future<MeshData[]>> futureDatas = new ArrayDeque<>();
        List<MeshData> datas = new ArrayList<>();

        for (int i = 0; i < names.length; i++) {
            final int index = i;
            if (DEBUG_OUTPUT) {
                System.out.println("Loading geometry '" + names[index]);
            }
            futureDatas.add(CompletableFuture.supplyAsync(() -> {
                MeshData[] e = MeshResources.load(names[index]);
                if (DEBUG_OUTPUT) {
                    for (MeshData m : e) {
                        System.out.println("Finished loading geometry '" + m.getName() + ": " + (m.getVertices().length / MeshData.SIZE) + " vertices, " + m.getIndices().length + " indices");
                    }
                }
                return e;
            }));
        }

        Future<MeshData[]> future;
        int index = 0;
        while ((future = futureDatas.poll()) != null) {
            try {
                datas.addAll(Arrays.asList(future.get()));
                index++;
            } catch (InterruptedException | ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }

        for (int i = 0; i < datas.size(); i++) {
            MeshData data = datas.get(i);
            if (DEBUG_OUTPUT) {
                System.out.println("Sending geometry '" + data.getName() + "', index " + i + " to the gpu.");
            }
            int vao = data.getVAO();
            if (DEBUG_OUTPUT) {
                System.out.println("Finished sending geometry '" + data.getName() + "', index " + i + " to the gpu with object id " + vao + ".");
            }
        }

        if (DEBUG_OUTPUT) {
            System.out.println("Finished loading geometries.");
        }
        HashMap<String, MeshData> map = new HashMap<>();
        for (MeshData m : datas) {
            map.put(m.getName(), m);
        }
        return map;
    }

    private GeometriesLoader() {

    }
}
