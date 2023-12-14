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

import cientistavuador.bakedlighting.geometry.Geometry;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class RayResult extends LocalRayResult implements Comparable<RayResult> {
    
    private final Geometry geometry;
    private final Vector3f origin = new Vector3f();
    private final Vector3f direction = new Vector3f();
    private final Vector3f hitpoint = new Vector3f();
    private final float distance;
    
    public RayResult(LocalRayResult local, Geometry geometry) {
        super(
                local.getLocalOrigin(),
                local.getLocalDirection(),
                local.getLocalHitpoint(),
                local.i0(),
                local.i1(),
                local.i2(),
                local.frontFace()
        );
        this.geometry = geometry;
        this.origin.set(local.getLocalOrigin());
        this.direction.set(local.getLocalDirection());
        this.hitpoint.set(local.getLocalHitpoint());
        geometry.getModel().transformProject(this.origin);
        geometry.getNormalModel().transform(this.direction);
        geometry.getModel().transformProject(this.hitpoint);
        this.distance = this.origin.distance(this.hitpoint);
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public Vector3fc getOrigin() {
        return origin;
    }

    public Vector3fc getDirection() {
        return direction;
    }

    public Vector3fc getHitpoint() {
        return hitpoint;
    }

    public float getDistance() {
        return distance;
    }
    
    @Override
    public int compareTo(RayResult o) {
        if (this.getDistance() > o.getDistance()) {
            return 1;
        }
        if (this.getDistance() < o.getDistance()) {
            return -1;
        }
        return 0;
    }

    @Override
    public String toString() {
        return "origin:"+
                this.getOrigin().x()+
                ","+
                this.getOrigin().y()+
                ","+
                this.getOrigin().z()+
                ";dir:"+
                this.getDirection().x()+
                ","+
                this.getDirection().y()+
                ","+
                this.getDirection().z()+
                ";hit:"+
                this.getHitpoint().x()+
                ","+
                this.getHitpoint().y()+
                ","+
                this.getHitpoint().z()+
                ";dist:"+
                this.getDistance()+
                ";front:"+
                this.frontFace()
                ;
    }
    
}