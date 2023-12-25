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

import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class LocalRayResult {
    
    private final Vector3f localOrigin = new Vector3f();
    private final Vector3f localDirection = new Vector3f();
    private final Vector3f localHitPosition = new Vector3f();
    private final Vector3f localTriangleNormal = new Vector3f();
    private final int triangle;
    private final boolean frontFace;

    public LocalRayResult(Vector3fc localOrigin, Vector3fc localDirection, Vector3fc localHitPosition, Vector3fc localNormal, int triangle, boolean frontFace) {
        this.localOrigin.set(localOrigin);
        this.localDirection.set(localDirection);
        this.localHitPosition.set(localHitPosition);
        this.localTriangleNormal.set(localNormal);
        this.triangle = triangle;
        this.frontFace = frontFace;
    }

    public Vector3fc getLocalOrigin() {
        return localOrigin;
    }

    public Vector3fc getLocalDirection() {
        return localDirection;
    }

    public Vector3fc getLocalHitPosition() {
        return localHitPosition;
    }

    public Vector3f getLocalTriangleNormal() {
        return localTriangleNormal;
    }
    
    public int triangle() {
        return this.triangle;
    }
    
    public boolean frontFace() {
        return frontFace;
    }

}
