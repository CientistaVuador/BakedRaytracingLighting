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
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class Scene {

    public static class Light {

        private final Vector3f diffuse = new Vector3f(1.3f, 1.3f, 1.3f);
        
        private float lightSize = 0.02f;

        protected Light() {

        }
        
        public Vector3fc getDiffuse() {
            return diffuse;
        }
        
        public void setDiffuse(float r, float g, float b) {
            this.diffuse.set(r, g, b);
        }

        public void setDiffuse(Vector3fc diffuse) {
            setDiffuse(diffuse.x(), diffuse.y(), diffuse.z());
        }

        public float getLightSize() {
            return lightSize;
        }

        public void setLightSize(float lightSize) {
            this.lightSize = lightSize;
        }
        
        public float getLuminance() {
            return getDiffuse().length();
        }
        
    }

    public static class DirectionalLight extends Light {

        private final Vector3f ambient = new Vector3f(0.2f, 0.3f, 0.4f);
        
        private final Vector3f direction = new Vector3f(0.5f, -2f, -1f).normalize();
        private final Vector3f directionNegated = new Vector3f(this.direction).negate();

        public DirectionalLight() {

        }

        public Vector3fc getDirection() {
            return direction;
        }

        public Vector3fc getDirectionNegated() {
            return directionNegated;
        }

        public void setDirection(float x, float y, float z) {
            this.direction.set(x, y, z).normalize();
            this.directionNegated.set(this.direction).negate();
        }

        public void setDirection(Vector3fc direction) {
            setDiffuse(direction.x(), direction.y(), direction.z());
        }

        public Vector3fc getAmbient() {
            return ambient;
        }

        public void setAmbient(float r, float g, float b) {
            this.ambient.set(r, g, b);
        }

        public void setAmbient(Vector3fc ambient) {
            setAmbient(ambient.x(), ambient.y(), ambient.z());
        }

    }

    public static class PointLight extends Light {

        private final Vector3f position = new Vector3f(-1f, 2f, 6f);
        private float bakeCutoff = 1f / 255f;

        public PointLight() {

        }

        public Vector3fc getPosition() {
            return position;
        }

        public void setPosition(float x, float y, float z) {
            this.position.set(x, y, z);
        }

        public void setPosition(Vector3fc position) {
            setPosition(position.x(), position.y(), position.z());
        }

        public float getBakeCutoff() {
            return bakeCutoff;
        }

        public void setBakeCutoff(float bakeCutoff) {
            this.bakeCutoff = bakeCutoff;
        }
        
        
    }

    private final List<Geometry> geometries = new ArrayList<>();
    private final List<Light> lights = new ArrayList<>();

    private SamplingMode samplingMode = SamplingMode.SAMPLE_5;

    private boolean directLightingEnabled = true;

    private boolean shadowsEnabled = true;
    private int shadowRaysPerSample = 12;
    private float shadowBlurArea = 1.5f;

    private boolean indirectLightingEnabled = true;
    private int indirectRaysPerSample = 8;
    private int indirectBounces = 4;
    private float indirectLightingBlurArea = 6f;
    private float indirectLightReflectionFactor = 1f;

    private float rayOffset = 0.001f;
    private boolean fillEmptyValuesWithLightColors = false;

    private boolean fastModeEnabled = false;

    public Scene() {

    }

    public List<Geometry> getGeometries() {
        return geometries;
    }

    public List<Light> getLights() {
        return lights;
    }

    public SamplingMode getSamplingMode() {
        return samplingMode;
    }

    public void setSamplingMode(SamplingMode samplingMode) {
        this.samplingMode = samplingMode;
    }

    public boolean isDirectLightingEnabled() {
        return directLightingEnabled;
    }

    public void setDirectLightingEnabled(boolean directLightingEnabled) {
        this.directLightingEnabled = directLightingEnabled;
    }

    public boolean isShadowsEnabled() {
        return shadowsEnabled;
    }

    public void setShadowsEnabled(boolean shadowsEnabled) {
        this.shadowsEnabled = shadowsEnabled;
    }

    public int getShadowRaysPerSample() {
        return shadowRaysPerSample;
    }

    public void setShadowRaysPerSample(int shadowRaysPerSample) {
        this.shadowRaysPerSample = shadowRaysPerSample;
    }

    public float getShadowBlurArea() {
        return shadowBlurArea;
    }

    public void setShadowBlurArea(float shadowBlurArea) {
        this.shadowBlurArea = shadowBlurArea;
    }

    public boolean isIndirectLightingEnabled() {
        return indirectLightingEnabled;
    }

    public void setIndirectLightingEnabled(boolean indirectLightingEnabled) {
        this.indirectLightingEnabled = indirectLightingEnabled;
    }

    public int getIndirectRaysPerSample() {
        return indirectRaysPerSample;
    }

    public void setIndirectRaysPerSample(int indirectRaysPerSample) {
        this.indirectRaysPerSample = indirectRaysPerSample;
    }

    public int getIndirectBounces() {
        return indirectBounces;
    }

    public void setIndirectBounces(int indirectBounces) {
        this.indirectBounces = indirectBounces;
    }

    public float getIndirectLightingBlurArea() {
        return indirectLightingBlurArea;
    }

    public void setIndirectLightingBlurArea(float indirectLightingBlurArea) {
        this.indirectLightingBlurArea = indirectLightingBlurArea;
    }

    public float getIndirectLightReflectionFactor() {
        return indirectLightReflectionFactor;
    }

    public void setIndirectLightReflectionFactor(float indirectLightReflectionFactor) {
        this.indirectLightReflectionFactor = indirectLightReflectionFactor;
    }

    public float getRayOffset() {
        return rayOffset;
    }

    public void setRayOffset(float rayOffset) {
        this.rayOffset = rayOffset;
    }

    public boolean isFastModeEnabled() {
        return fastModeEnabled;
    }

    public void setFastModeEnabled(boolean fastModeEnabled) {
        this.fastModeEnabled = fastModeEnabled;
    }

    public void setFillEmptyValuesWithLightColors(boolean fillEmptyValuesWithLightColors) {
        this.fillEmptyValuesWithLightColors = fillEmptyValuesWithLightColors;
    }

    public boolean fillEmptyValuesWithLightColors() {
        return fillEmptyValuesWithLightColors;
    }

}
