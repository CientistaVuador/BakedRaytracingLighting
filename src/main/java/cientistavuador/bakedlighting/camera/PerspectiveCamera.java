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
package cientistavuador.bakedlighting.camera;

import cientistavuador.bakedlighting.Main;
import cientistavuador.bakedlighting.ubo.CameraUBO;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A Somewhat simple 3D Camera
 * @author Shinoa Hiragi
 * @author Cien
 */
public class PerspectiveCamera implements Camera {

    public static final float DEFAULT_FOV = 90f;
    public static final float DEFAULT_PITCH = 0f;
    public static final float DEFAULT_YAW = -90f;
    
    //Camera fields
    private final Vector2f dimensions = new Vector2f(Main.WIDTH, Main.HEIGHT);
    
    private float nearPlane = DEFAULT_NEAR_PLANE;
    private float farPlane = DEFAULT_FAR_PLANE;
    private float fov = DEFAULT_FOV;
    
    //Camera axises
    private final Vector3f front = new Vector3f(0, 0, 1);
    private final Vector3f up = new Vector3f(0, 1, 0);
    private final Vector3f right = new Vector3f(1, 0, 0);
    
    //Position and Rotation
    private final Vector3d position = new Vector3d(DEFAULT_POSITION);
    private final Vector3f rotation = new Vector3f(DEFAULT_PITCH, DEFAULT_YAW, 0);
    
    //Matrices
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4d projectionView = new Matrix4d();
    
    //UBO
    private CameraUBO ubo = null;
    
    public PerspectiveCamera() {
        updateProjection();
        updateView();
    }

    @Override
    public Matrix4fc getView() {
        return view;
    }

    @Override
    public Matrix4fc getProjection() {
        return projection;
    }

    private void updateProjection() {
        this.projection
                .identity()
                .perspective(
                        (float) Math.toRadians(this.fov),
                        (this.dimensions.x() / this.dimensions.y()),
                        this.nearPlane,
                        this.farPlane
                );
        
        if (this.ubo != null) {
            this.ubo.setProjection(this.projection);
        }
        updateProjectionView();
    }
    
    private void updateView() {
        float pitchRadians = (float) Math.toRadians(this.rotation.x());
        float yawRadians = (float) Math.toRadians(this.rotation.y());
        
        this.front.set(
                Math.cos(pitchRadians) * Math.cos(yawRadians),
                Math.sin(pitchRadians),
                Math.cos(pitchRadians) * Math.sin(yawRadians)
        ).normalize();
        
        this.right.set(DEFAULT_WORLD_UP).cross(this.front).normalize();
        this.up.set(this.front).cross(this.right).normalize();
        
        this.view.identity().lookAt(
                0,
                0,
                0,
                0 + this.front.x(),
                0 + this.front.y(),
                0 + this.front.z(),
                this.up.x(), this.up.y(), this.up.z()
        );
        
        if (this.ubo != null) {
            this.ubo.setView(this.view);
        }
        updateProjectionView();
    }
    
    private void updateProjectionView() {
        this.projectionView
                .set(this.projection)
                .mul(this.view)
                .translate(-this.position.x(), -this.position.y(), -this.position.z())
                ;
    }

    @Override
    public void setUBO(CameraUBO ubo) {
        this.ubo = ubo;
        if (this.ubo != null) {
            this.ubo.setProjection(this.projection);
            this.ubo.setView(this.view);
            this.ubo.setPosition(this.position);
        }
    }

    @Override
    public void setNearPlane(float nearPlane) {
        this.nearPlane = nearPlane;
        updateProjection();
    }

    @Override
    public void setFarPlane(float farPlane) {
        this.farPlane = farPlane;
        updateProjection();
    }

    public void setFov(float fov) {
        this.fov = fov;
        updateProjection();
    }

    @Override
    public void setDimensions(float width, float height) {
        this.dimensions.set(width, height);
        updateProjection();
    }
    
    @Override
    public void setDimensions(Vector2fc dimensions) {
        setDimensions(dimensions.x(), dimensions.y());
    }

    @Override
    public void setPosition(double x, double y, double z) {
        this.position.set(x, y, z);
        if (this.ubo != null) {
            this.ubo.setPosition(this.position);
        }
        updateProjectionView();
    }
    
    @Override
    public void setPosition(Vector3dc position) {
        setPosition(position.x(), position.y(), position.z());
    }

    public void setRotation(float pitch, float yaw, float roll) {
        this.rotation.set(pitch, yaw, roll);
        updateView();
    }
    
    public void setRotation(Vector3fc rotation) {
        setRotation(rotation.x(), rotation.y(), rotation.z());
    }

    @Override
    public float getNearPlane() {
        return nearPlane;
    }

    @Override
    public float getFarPlane() {
        return farPlane;
    }

    public float getFov() {
        return fov;
    }

    @Override
    public Vector2fc getDimensions() {
        return dimensions;
    }

    @Override
    public Vector3dc getPosition() {
        return position;
    }

    public Vector3fc getRotation() {
        return rotation;
    }
    
    @Override
    public Vector3fc getFront() {
        return front;
    }

    @Override
    public Vector3fc getRight() {
        return right;
    }

    @Override
    public Vector3fc getUp() {
        return up;
    }

    @Override
    public Matrix4dc getProjectionView() {
        return projectionView;
    }

    @Override
    public CameraUBO getUBO() {
        return this.ubo;
    }
    
}
