package com.voxelgame.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * First-person camera. Manages position, rotation (yaw/pitch),
 * and computes the view and projection matrices each frame.
 */
public class Camera {

    private final Vector3f position = new Vector3f(0, 80, 0);
    private float yaw = -90.0f;   // looking along -Z initially
    private float pitch = 0.0f;

    private final Vector3f front = new Vector3f(0, 0, -1);
    private final Vector3f up = new Vector3f(0, 1, 0);
    private final Vector3f right = new Vector3f(1, 0, 0);

    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();

    private float fov = 70.0f;
    private float nearPlane = 0.1f;
    private float farPlane = 500.0f;

    public void updateVectors() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        front.x = (float) (Math.cos(pitchRad) * Math.cos(yawRad));
        front.y = (float) Math.sin(pitchRad);
        front.z = (float) (Math.cos(pitchRad) * Math.sin(yawRad));
        front.normalize();

        // Right = front x worldUp
        front.cross(0, 1, 0, right);
        right.normalize();

        // Up = right x front
        right.cross(front, up);
        up.normalize();
    }

    public Matrix4f getViewMatrix() {
        Vector3f center = new Vector3f(position).add(front);
        viewMatrix.identity().lookAt(position, center, up);
        return viewMatrix;
    }

    public Matrix4f getProjectionMatrix(int width, int height) {
        float aspect = (float) width / Math.max(height, 1);
        projectionMatrix.identity().perspective(
            (float) Math.toRadians(fov), aspect, nearPlane, farPlane
        );
        return projectionMatrix;
    }

    public void rotate(float dYaw, float dPitch) {
        yaw += dYaw;
        pitch += dPitch;
        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;
        updateVectors();
    }

    public Vector3f getPosition() { return position; }
    public Vector3f getFront() { return front; }
    public Vector3f getRight() { return right; }
    public Vector3f getUp() { return up; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setYaw(float yaw) { this.yaw = yaw; updateVectors(); }
    public void setPitch(float pitch) { this.pitch = pitch; updateVectors(); }
    public float getFov() { return fov; }
    public float getNearPlane() { return nearPlane; }
    public float getFarPlane() { return farPlane; }
}
