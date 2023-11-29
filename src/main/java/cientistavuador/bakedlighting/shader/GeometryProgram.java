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
package cientistavuador.bakedlighting.shader;

import cientistavuador.bakedlighting.util.BetterUniformSetter;
import cientistavuador.bakedlighting.util.ProgramCompiler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class GeometryProgram {
    
    public static final int MAX_AMOUNT_OF_LIGHTS = 8;
    
    public static class PointLight {
        
        private boolean enabled = true;
        
        private final Vector3f position = new Vector3f();
        private final Vector3f ambient = new Vector3f(0.2f, 0.2f, 0.2f);
        private final Vector3f diffuse = new Vector3f(0.8f, 0.8f, 0.8f);
        
        public PointLight() {
            
        }

        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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

        public Vector3fc getAmbient() {
            return ambient;
        }
        
        public void setAmbient(float r, float g, float b) {
            this.ambient.set(r, g, b);
        }
        
        public void setAmbient(Vector3fc ambient) {
            setAmbient(ambient.x(), ambient.y(), ambient.z());
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
        
    }
    
    private static class PointLightUniforms extends PointLight {
        
        private final int index;
        
        private final int enabledLocation;
        private final int positionLocation;
        private final int ambientLocation;
        private final int diffuseLocation;
        
        private boolean enabledRequiresUpdate = false;
        private boolean positionRequiresUpdate = false;
        private boolean ambientRequiresUpdate = false;
        private boolean diffuseRequiresUpdate = false;
        
        public PointLightUniforms(int index) {
            this.index = index;
            this.enabledLocation = glGetUniformLocation(SHADER_PROGRAM, "lights["+index+"].enabled");
            this.positionLocation = glGetUniformLocation(SHADER_PROGRAM, "lights["+index+"].position");
            this.ambientLocation = glGetUniformLocation(SHADER_PROGRAM, "lights["+index+"].ambient");
            this.diffuseLocation = glGetUniformLocation(SHADER_PROGRAM, "lights["+index+"].diffuse");
        }

        public int getIndex() {
            return index;
        }
        
        @Override
        public void setEnabled(boolean enabled) {
            if (isEnabled() != enabled) {
                this.enabledRequiresUpdate = true;
            }
            super.setEnabled(enabled);
        }

        @Override
        public void setPosition(float x, float y, float z) {
            if (!getPosition().equals(x, y, z)) {
                this.positionRequiresUpdate = true;
            }
            super.setPosition(x, y, z);
        }
        
        @Override
        public void setAmbient(float r, float g, float b) {
            if (!getAmbient().equals(r, g, b)) {
                this.ambientRequiresUpdate = true;
            }
            super.setAmbient(r, g, b);
        }
        
        @Override
        public void setDiffuse(float r, float g, float b) {
            if (!getDiffuse().equals(r, g, b)) {
                this.diffuseRequiresUpdate = true;
            }
            super.setDiffuse(r, g, b);
        }
        
        public void updateUniforms() {
            if (this.enabledRequiresUpdate) {
                glUniform1i(this.enabledLocation, (isEnabled() ? 1 : 0));
                this.enabledRequiresUpdate = false;
            }
            if (this.positionRequiresUpdate) {
                Vector3fc position = getPosition();
                glUniform3f(this.positionLocation, position.x(), position.y(), position.z());
                this.positionRequiresUpdate = false;
            }
            if (this.ambientRequiresUpdate) {
                Vector3fc ambient = getAmbient();
                glUniform3f(this.ambientLocation, ambient.x(), ambient.y(), ambient.z());
                this.positionRequiresUpdate = false;
            }
            if (this.diffuseRequiresUpdate) {
                Vector3fc diffuse = getDiffuse();
                glUniform3f(this.diffuseLocation, diffuse.x(), diffuse.y(), diffuse.z());
                this.diffuseRequiresUpdate = false;
            }
        }
    }
    
    public static final int SHADER_PROGRAM = ProgramCompiler.compile(
            """
            #version 330 core
            
            uniform mat4 projectionView;
            uniform mat4 model;
            uniform mat3 normalModel;
            
            layout (location = 0) in vec3 vertexPosition;
            layout (location = 1) in vec2 vertexUv;
            layout (location = 2) in vec3 vertexNormal;
            
            out vec3 position;
            out vec2 uv;
            out vec3 normal;
            
            void main() {
                vec4 pos = model * vec4(vertexPosition, 1.0);
                
                position = pos.xyz;
                uv = vertexUv;
                normal = normalize(normalModel * vertexNormal);
                
                gl_Position = projectionView * pos;
            }
            """
            ,
            """
            #version 330 core
            
            struct PointLight {
                bool enabled;
                vec3 position;
                vec3 ambient;
                vec3 diffuse;
            };
            
            uniform vec4 color;
            uniform sampler2D tex;
            
            uniform bool lightingEnabled;
            
            uniform vec3 sunDirection;
            uniform vec3 sunAmbient;
            uniform vec3 sunDiffuse;
            
            uniform PointLight lights[MAX_AMOUNT_OF_LIGHTS];
            
            in vec3 position;
            in vec2 uv;
            in vec3 normal;
            
            layout (location = 0) out vec4 colorOutput;
            
            const float gamma = 2.2;
            
            void main() {
                vec4 textureColor = texture(tex, uv);
                colorOutput = textureColor * color;
                if (lightingEnabled) {
                    textureColor.rgb = pow(textureColor.rgb * color.rgb, vec3(gamma));
                    vec3 resultOutput = vec3(0.0);
                    
                    //sun
                    resultOutput += sunDiffuse * max(dot(normal, -sunDirection), 0.0) * textureColor.rgb;
                    resultOutput += sunAmbient * textureColor.rgb;
                    
                    //point lights
                    for (int i = 0; i < MAX_AMOUNT_OF_LIGHTS; i++) {
                        PointLight light = lights[i];
                        if (light.enabled) {
                            vec3 lightDir = normalize(light.position - position);
                            float distance = distance(light.position, position);
                            float attenuation = 1.0 / (distance*distance);
                            
                            resultOutput += light.diffuse * max(dot(normal, lightDir), 0.0) * attenuation * textureColor.rgb;
                            resultOutput += light.ambient * attenuation * textureColor.rgb;
                        }
                    }
                    
                    colorOutput = vec4(pow(resultOutput, vec3(1.0/gamma)), textureColor.a * color.a);
                }
            }
            """,
            new HashMap<>() {{
                put("MAX_AMOUNT_OF_LIGHTS", Integer.toString(MAX_AMOUNT_OF_LIGHTS));
            }}
    );
    
    private static final BetterUniformSetter UNIFORMS = new BetterUniformSetter(SHADER_PROGRAM);
    
    public static final GeometryProgram INSTANCE = new GeometryProgram();
    
    private final Matrix4f projectionView = new Matrix4f();
    private final Matrix4f model = new Matrix4f();
    private final Matrix3f normalModel = new Matrix3f();
    private final Vector4f color = new Vector4f();
    private int textureUnit = 0;
    
    private boolean lightingEnabled = false;
    
    private final Vector3f sunDirection = new Vector3f();
    private final Vector3f sunAmbient = new Vector3f();
    private final Vector3f sunDiffuse = new Vector3f();
    
    private final PointLightUniforms[] lightsUniforms = new PointLightUniforms[MAX_AMOUNT_OF_LIGHTS];
    private final List<PointLight> lights = new ArrayList<>();
    
    private GeometryProgram() {
        for (int i = 0; i < this.lightsUniforms.length; i++) {
            this.lightsUniforms[i] = new PointLightUniforms(i);
        }
    }

    public void use() {
        glUseProgram(SHADER_PROGRAM);
    }
    
    public Matrix4fc getProjectionView() {
        return projectionView;
    }

    public Matrix4fc getModel() {
        return model;
    }
    
    public Matrix3fc getNormalModel() {
        return normalModel;
    }

    public int getTextureUnit() {
        return textureUnit;
    }

    public Vector4fc getColor() {
        return color;
    }

    public Vector3fc getSunDirection() {
        return sunDirection;
    }

    public Vector3fc getSunAmbient() {
        return sunAmbient;
    }

    public Vector3fc getSunDiffuse() {
        return sunDiffuse;
    }

    public List<PointLight> getLights() {
        return lights;
    }
    
    public void updateLightsUniforms() {
        int uniformsIndex = 0;
        int lightsIndex = 0;
        while (uniformsIndex < this.lightsUniforms.length) {
            PointLightUniforms uniforms = this.lightsUniforms[uniformsIndex];
            if (lightsIndex >= this.lights.size()) {
                uniforms.setEnabled(false);
                uniformsIndex++;
                continue;
            }
            
            PointLight light = this.lights.get(lightsIndex);
            if (light == null || !light.isEnabled()) {
                lightsIndex++;
                continue;
            }
            
            uniforms.setEnabled(true);
            uniforms.setPosition(light.getPosition());
            uniforms.setDiffuse(light.getDiffuse());
            uniforms.setAmbient(light.getAmbient());
            
            uniformsIndex++;
            lightsIndex++;
        }
        
        for (PointLightUniforms p:this.lightsUniforms) {
            p.updateUniforms();
        }
    }

    public boolean isLightingEnabled() {
        return lightingEnabled;
    }
    
    public void setSunDirection(float x, float y, float z) {
        this.sunDirection.set(x, y, z).normalize();
        glUniform3f(UNIFORMS.locationOf("sunDirection"), this.sunDirection.x(), this.sunDirection.y(), this.sunDirection.z());
    }
    
    public void setSunDirection(Vector3f dir) {
        setSunDirection(dir.x(), dir.y(), dir.z());
    }
    
    public void setSunAmbient(float r, float g, float b) {
        this.sunAmbient.set(r, g, b);
        glUniform3f(UNIFORMS.locationOf("sunAmbient"), r, g, b);
    }
    
    public void setSunAmbient(Vector3f ambient) {
        setSunAmbient(ambient.x(), ambient.y(), ambient.z());
    }
    
    public void setSunDiffuse(float r, float g, float b) {
        this.sunDiffuse.set(r, g, b);
        glUniform3f(UNIFORMS.locationOf("sunDiffuse"), r, g, b);
    }
    
    public void setSunDiffuse(Vector3f diffuse) {
        setSunDiffuse(diffuse.x(), diffuse.y(), diffuse.z());
    }

    public void setLightingEnabled(boolean lightingEnabled) {
        this.lightingEnabled = lightingEnabled;
        glUniform1i(UNIFORMS.locationOf("lightingEnabled"), (lightingEnabled ? 1 : 0));
    }
    
    public void setProjectionView(Matrix4f projectionView) {
        this.projectionView.set(projectionView);
        BetterUniformSetter.uniformMatrix4fv(UNIFORMS.locationOf("projectionView"), projectionView);
    }
    
    public void setModel(Matrix4f model) {
        this.normalModel.set(this.model.set(model).invert().transpose());
        this.model.set(model);
        
        BetterUniformSetter.uniformMatrix4fv(UNIFORMS.locationOf("model"), this.model);
        BetterUniformSetter.uniformMatrix3fv(UNIFORMS.locationOf("normalModel"), this.normalModel);
    }
    
    public void setTextureUnit(int unit) {
        this.textureUnit = unit;
        glUniform1i(UNIFORMS.locationOf("tex"), unit);
    }
    
    public void setColor(float r, float g, float b, float a) {
        this.color.set(r, g, b, a);
        glUniform4f(UNIFORMS.locationOf("color"), r, g, b, a);
    }
    
    public void setColor(Vector4fc color) {
        setColor(color.x(), color.y(), color.z(), color.w());
    }
    
}
