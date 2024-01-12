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
import cientistavuador.bakedlighting.texture.Textures;
import java.util.Map;

/**
 *
 * @author Cien
 */
public class Geometries {

    public static final MeshData[] GARAGE;
    public static final MeshData CIENCOLA;
    public static final MeshData SPHERE;
    
    static {
        Map<String, MeshData> meshes = GeometriesLoader.load("garage.obj.gz", "ciencola.obj", "sphere.obj");
        MeshData bricks = meshes.get("garage.obj.gz@bricks");
        MeshData concrete = meshes.get("garage.obj.gz@concrete");
        MeshData grass = meshes.get("garage.obj.gz@grass");
        MeshData red = meshes.get("garage.obj.gz@red");
        MeshData monkey = meshes.get("garage.obj.gz@monkey");
        MeshData torus = meshes.get("garage.obj.gz@torus");
        MeshData sphere = meshes.get("garage.obj.gz@sphere");
        bricks.setTextureHint(Textures.BRICKS);
        concrete.setTextureHint(Textures.CONCRETE);
        grass.setTextureHint(Textures.GRASS);
        red.setTextureHint(Textures.RED);
        torus.setTextureHint(Textures.CONCRETE);
        sphere.setTextureHint(Textures.RED);
        GARAGE = new MeshData[] {concrete, grass, bricks, red, torus, sphere, monkey};
        
        MeshData ciencola = meshes.get("ciencola.obj");
        ciencola.setTextureHint(Textures.CIENCOLA);
        CIENCOLA = ciencola;
        
        MeshData debugSphere = meshes.get("sphere.obj");
        debugSphere.setTextureHint(Textures.WHITE_TEXTURE);
        SPHERE = debugSphere;
    }
    
    public static void init() {
        
    }

    private Geometries() {

    }

}
