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
    
    private final List<Geometry> geometries = new ArrayList<>();
    
    private float sunSize = 0.03f;
    private final Vector3f sunDirection = new Vector3f(0.5f, -1f, 1f).normalize();
    private final Vector3f sunDirectionInverted = new Vector3f(this.sunDirection).negate();
    private final Vector3f sunDiffuseColor = new Vector3f(1.3f, 1.3f, 1.3f);
    private final Vector3f sunAmbientColor = new Vector3f(0.4f, 0.4f, 0.5f);
    
    private SamplingMode samplingMode = SamplingMode.SAMPLE_4;
    
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
    
    public Scene() {
    }

    public List<Geometry> getGeometries() {
        return geometries;
    }

    public float getSunSize() {
        return sunSize;
    }

    public Vector3fc getSunDirection() {
        return sunDirection;
    }

    public Vector3fc getSunDirectionInverted() {
        return sunDirectionInverted;
    }

    public Vector3fc getSunDiffuseColor() {
        return sunDiffuseColor;
    }

    public Vector3fc getSunAmbientColor() {
        return sunAmbientColor;
    }

    public void setSunSize(float sunSize) {
        this.sunSize = sunSize;
    }
    
    public void setSunDirection(float x, float y, float z) {
        this.sunDirection.set(x, y, z).normalize();
        this.sunDirectionInverted.set(this.sunDirection).negate();
    }

    public void setSunDiffuseColor(float r, float g, float b) {
        this.sunDiffuseColor.set(r, g, b);
    }

    public void setSunAmbientColor(float r, float g, float b) {
        this.sunAmbientColor.set(r, g, b);
    }

    public void setSunDirection(Vector3fc direction) {
        setSunDirection(direction.x(), direction.y(), direction.z());
    }

    public void setSunDiffuseColor(Vector3fc color) {
        setSunDiffuseColor(color.x(), color.y(), color.z());
    }

    public void setSunAmbientColor(Vector3fc color) {
        setSunAmbientColor(color.x(), color.y(), color.z());
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


}
