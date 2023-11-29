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

import cientistavuador.bakedlighting.debug.AabRender;
import org.joml.Intersectionf;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public interface Aab { 
    public default boolean testAab3D(Aab other) {
        Vector3f thisMin = new Vector3f();
        Vector3f thisMax = new Vector3f();
        getMin(thisMin);
        getMax(thisMax);
        
        Vector3f otherMin = new Vector3f();
        Vector3f otherMax = new Vector3f();
        other.getMin(otherMin);
        other.getMax(otherMax);
        
        return Intersectionf.testAabAab(thisMin, thisMax, otherMin, otherMax);
    }
    public default boolean testAab2D(Aab other) {
        Vector3f thisMin = new Vector3f();
        Vector3f thisMax = new Vector3f();
        getMin(thisMin);
        getMax(thisMax);
        thisMin.setComponent(2, 0f);
        thisMax.setComponent(2, 0f);
        
        Vector3f otherMin = new Vector3f();
        Vector3f otherMax = new Vector3f();
        other.getMin(otherMin);
        other.getMax(otherMax);
        otherMin.setComponent(2, 0f);
        otherMax.setComponent(2, 0f);
        
        return Intersectionf.testAabAab(thisMin, thisMax, otherMin, otherMax);
    }
    public default void queueAabRender() {
        Vector3f thisMin = new Vector3f();
        Vector3f thisMax = new Vector3f();
        getMin(thisMin);
        getMax(thisMax);
        AabRender.queueRender(
                thisMin.x(), thisMin.y(), thisMin.z(),
                thisMax.x(), thisMax.y(), thisMax.z()
        );
    }
    public void getMin(Vector3f min);
    public void getMax(Vector3f max);
}
