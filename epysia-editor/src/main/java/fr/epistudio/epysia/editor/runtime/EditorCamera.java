package fr.epistudio.epysia.editor.runtime;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class EditorCamera {

    private static final float LOOK_SENSITIVITY = 0.0035f;
    private static final float MOVE_SPEED = 6.0f;
    private static final float BOOST_MULTIPLIER = 3.0f;
    private static final float MAX_PITCH_RADIANS = (float) Math.toRadians(89.0);
    private static final float DEFAULT_FOV_DEGREES = 60.0f;
    private static final float DEFAULT_NEAR = 0.05f;
    private static final float DEFAULT_FAR = 500.0f;
    private static final float FRAME_DURATION_SECONDS = 0.2f;
    private static final float MINIMUM_FOCUS_DISTANCE = 0.75f;
    private static final float DOLLY_STEP_FRACTION = 0.15f;
    private static final float MINIMUM_ORTHOGRAPHIC_SIZE = 0.1f;
    private static final float MAXIMUM_ORTHOGRAPHIC_SIZE = 500.0f;
    private static final float ORTHOGRAPHIC_ZOOM_BASE = 0.9f;
    private static final float TWO_DIMENSIONAL_DEPTH = 10.0f;
    private static final float TWO_DIMENSIONAL_FRAME_MARGIN = 1.2f;

    private final GameObject host = new GameObject("Editor Camera");
    private final Transform3D transform;
    private final Camera3D camera;
    private final Quaternionf scratchRotation = new Quaternionf();
    private final Vector3f scratchForward = new Vector3f();
    private final Vector3f scratchRight = new Vector3f();
    private final Vector3f scratchMovement = new Vector3f();
    private float moveSpeed = MOVE_SPEED;
    private float boostMultiplier = BOOST_MULTIPLIER;
    private float yawRadians = (float) Math.toRadians(35.0);
    private float pitchRadians = (float) Math.toRadians(-25.0);
    private float lastMouseX = Float.NaN;
    private float lastMouseY = Float.NaN;
    private boolean lookActive;
    private final Vector3f focusPoint = new Vector3f();
    private final Vector3f frameStartPosition = new Vector3f();
    private final Vector3f frameTargetPosition = new Vector3f();
    private float focusDistance = 10.0f;
    private float frameElapsedSeconds;
    private boolean framing;
    private boolean orbitActive;
    private float orbitLastMouseX = Float.NaN;
    private float orbitLastMouseY = Float.NaN;
    private boolean twoDimensional;
    private final Vector3f storedPerspectivePosition = new Vector3f();
    private float storedPerspectiveYaw;
    private float storedPerspectivePitch;
    private boolean panActive;
    private float panLastMouseX = Float.NaN;
    private float panLastMouseY = Float.NaN;

    public EditorCamera() {
        this.transform = new Transform3D().setPosition(6.0f, 5.0f, 8.0f);
        this.camera = new Camera3D()
                .setFieldOfViewDegrees(DEFAULT_FOV_DEGREES)
                .setNearFar(DEFAULT_NEAR, DEFAULT_FAR)
                .setActive(true);
        host.addComponent(transform);
        host.addComponent(camera);
        applyOrientation();
    }

    public Camera3D camera() {
        return camera;
    }

    public Transform3D transform() {
        return transform;
    }

    public Matrix4f viewMatrix(Matrix4f destination) {
        return transform.worldMatrix().invert(destination);
    }

    public Matrix4f projectionMatrix(Matrix4f destination) {
        if (camera.orthographic()) {
            float halfHeight = camera.orthographicSize();
            float halfWidth = halfHeight * camera.aspectRatio();
            return destination.identity().setOrtho(-halfWidth, halfWidth, -halfHeight, halfHeight,
                    camera.nearPlane(), camera.farPlane());
        }
        return destination.identity().perspective(
                (float) Math.toRadians(camera.fieldOfViewDegrees()),
                camera.aspectRatio(), camera.nearPlane(), camera.farPlane());
    }

    public boolean twoDimensional() {
        return twoDimensional;
    }

    public void setTwoDimensional(boolean enabled) {
        if (twoDimensional == enabled) {
            return;
        }
        twoDimensional = enabled;
        if (enabled) {
            enterTwoDimensional();
        } else {
            exitTwoDimensional();
        }
    }

    private void enterTwoDimensional() {
        storedPerspectivePosition.set(transform.position());
        storedPerspectiveYaw = yawRadians;
        storedPerspectivePitch = pitchRadians;
        yawRadians = 0.0f;
        pitchRadians = 0.0f;
        applyOrientation();
        transform.setPosition(transform.position().x, transform.position().y, TWO_DIMENSIONAL_DEPTH);
        camera.setOrthographic(true);
        framing = false;
    }

    private void exitTwoDimensional() {
        camera.setOrthographic(false);
        yawRadians = storedPerspectiveYaw;
        pitchRadians = storedPerspectivePitch;
        transform.setPosition(storedPerspectivePosition.x, storedPerspectivePosition.y,
                storedPerspectivePosition.z);
        applyOrientation();
        framing = false;
    }

    public float orthographicSize() {
        return camera.orthographicSize();
    }

    public void applyOrthographicZoom(float scrollDelta) {
        if (scrollDelta == 0.0f) {
            return;
        }
        float size = camera.orthographicSize() * (float) Math.pow(ORTHOGRAPHIC_ZOOM_BASE, scrollDelta);
        camera.setOrthographicSize(clamp(size, MINIMUM_ORTHOGRAPHIC_SIZE, MAXIMUM_ORTHOGRAPHIC_SIZE));
    }

    public void updatePan(float mouseX, float mouseY, boolean panHeld, float unitsPerPixel) {
        if (!panHeld) {
            panActive = false;
            return;
        }
        if (!panActive) {
            panActive = true;
            panLastMouseX = mouseX;
            panLastMouseY = mouseY;
            return;
        }
        transform.translate(-(mouseX - panLastMouseX) * unitsPerPixel,
                (mouseY - panLastMouseY) * unitsPerPixel, 0.0f);
        panLastMouseX = mouseX;
        panLastMouseY = mouseY;
    }

    public void setAspectRatio(float aspect) {
        if (aspect > 0.0f) {
            camera.setAspectRatio(aspect);
        }
    }

    public void updateLook(float mouseX, float mouseY, boolean rightMouseHeld) {
        if (!rightMouseHeld) {
            lookActive = false;
            return;
        }
        if (!lookActive) {
            lookActive = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return;
        }
        yawRadians -= (mouseX - lastMouseX) * LOOK_SENSITIVITY;
        pitchRadians = clamp(pitchRadians - (mouseY - lastMouseY) * LOOK_SENSITIVITY,
                -MAX_PITCH_RADIANS, MAX_PITCH_RADIANS);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        applyOrientation();
    }

    public void updateMovement(boolean forward, boolean backward, boolean left, boolean right,
                                boolean up, boolean down, boolean boost, float deltaTimeSeconds) {
        if (!lookActive) {
            return;
        }
        transform.rotation().transform(0.0f, 0.0f, -1.0f, scratchForward);
        transform.rotation().transform(1.0f, 0.0f, 0.0f, scratchRight);
        scratchMovement.set(0.0f);
        if (forward) scratchMovement.add(scratchForward);
        if (backward) scratchMovement.sub(scratchForward);
        if (right) scratchMovement.add(scratchRight);
        if (left) scratchMovement.sub(scratchRight);
        if (up) scratchMovement.add(0.0f, 1.0f, 0.0f);
        if (down) scratchMovement.sub(0.0f, 1.0f, 0.0f);
        if (scratchMovement.lengthSquared() <= 0.0f) {
            return;
        }
        float speed = boost ? moveSpeed * boostMultiplier : moveSpeed;
        scratchMovement.normalize().mul(speed * deltaTimeSeconds);
        transform.translate(scratchMovement.x, scratchMovement.y, scratchMovement.z);
    }

    public void setMoveSpeed(float moveSpeed) {
        if (moveSpeed > 0.0f) {
            this.moveSpeed = moveSpeed;
        }
    }

    public void setBoostMultiplier(float boostMultiplier) {
        if (boostMultiplier >= 1.0f) {
            this.boostMultiplier = boostMultiplier;
        }
    }

    public void applyZoom(float scrollDelta) {
        if (scrollDelta == 0.0f) {
            return;
        }
        transform.rotation().transform(0.0f, 0.0f, -1.0f, scratchForward);
        float step = 0.5f * scrollDelta;
        transform.translate(scratchForward.x * step, scratchForward.y * step, scratchForward.z * step);
    }

    public float focusDistance() {
        return focusDistance;
    }

    public void frame(Vector3f center, float radius) {
        if (twoDimensional) {
            frameTwoDimensional(center, radius);
            return;
        }
        float halfFov = (float) Math.toRadians(camera.fieldOfViewDegrees()) * 0.5f;
        float distance = Math.max(MINIMUM_FOCUS_DISTANCE, (float) (radius / Math.tan(halfFov)) * 1.4f);
        focusPoint.set(center);
        focusDistance = distance;
        transform.rotation().transform(0.0f, 0.0f, -1.0f, scratchForward);
        frameStartPosition.set(transform.position());
        frameTargetPosition.set(center).sub(scratchForward.mul(distance, new Vector3f()));
        frameElapsedSeconds = 0.0f;
        framing = true;
    }

    private void frameTwoDimensional(Vector3f center, float radius) {
        focusPoint.set(center.x, center.y, 0.0f);
        camera.setOrthographicSize(clamp(radius * TWO_DIMENSIONAL_FRAME_MARGIN,
                MINIMUM_ORTHOGRAPHIC_SIZE, MAXIMUM_ORTHOGRAPHIC_SIZE));
        frameStartPosition.set(transform.position());
        frameTargetPosition.set(center.x, center.y, TWO_DIMENSIONAL_DEPTH);
        frameElapsedSeconds = 0.0f;
        framing = true;
    }

    public void updateFraming(float deltaSeconds) {
        if (!framing) {
            return;
        }
        frameElapsedSeconds += deltaSeconds;
        float progress = Math.min(1.0f, frameElapsedSeconds / FRAME_DURATION_SECONDS);
        float eased = progress * progress * (3.0f - 2.0f * progress);
        scratchMovement.set(frameStartPosition).lerp(frameTargetPosition, eased);
        transform.setPosition(scratchMovement.x, scratchMovement.y, scratchMovement.z);
        if (progress >= 1.0f) {
            framing = false;
        }
    }

    public void updateOrbit(float mouseX, float mouseY, boolean orbitHeld) {
        if (!orbitHeld) {
            orbitActive = false;
            return;
        }
        if (!orbitActive) {
            beginOrbit(mouseX, mouseY);
            return;
        }
        yawRadians -= (mouseX - orbitLastMouseX) * LOOK_SENSITIVITY;
        pitchRadians = clamp(pitchRadians - (mouseY - orbitLastMouseY) * LOOK_SENSITIVITY,
                -MAX_PITCH_RADIANS, MAX_PITCH_RADIANS);
        orbitLastMouseX = mouseX;
        orbitLastMouseY = mouseY;
        applyOrientation();
        moveToOrbitPosition();
    }

    private void beginOrbit(float mouseX, float mouseY) {
        orbitActive = true;
        orbitLastMouseX = mouseX;
        orbitLastMouseY = mouseY;
        framing = false;
        focusDistance = Math.max(MINIMUM_FOCUS_DISTANCE, transform.position().distance(focusPoint));
    }

    private void moveToOrbitPosition() {
        transform.rotation().transform(0.0f, 0.0f, -1.0f, scratchForward);
        scratchMovement.set(focusPoint).sub(scratchForward.mul(focusDistance, new Vector3f()));
        transform.setPosition(scratchMovement.x, scratchMovement.y, scratchMovement.z);
    }

    public void applyDolly(float scrollDelta) {
        if (scrollDelta == 0.0f) {
            return;
        }
        framing = false;
        float remaining = Math.max(MINIMUM_FOCUS_DISTANCE, transform.position().distance(focusPoint));
        float step = remaining * DOLLY_STEP_FRACTION * scrollDelta;
        transform.rotation().transform(0.0f, 0.0f, -1.0f, scratchForward);
        transform.translate(scratchForward.x * step, scratchForward.y * step, scratchForward.z * step);
        focusDistance = Math.max(MINIMUM_FOCUS_DISTANCE, focusDistance - step);
    }

    public void applyViewMatrix(Matrix4f view) {
        Matrix4f world = view.invert(new Matrix4f());
        Vector3f position = world.getTranslation(new Vector3f());
        world.transformDirection(scratchForward.set(0.0f, 0.0f, -1.0f));
        if (scratchForward.lengthSquared() < 1.0e-8f) {
            return;
        }
        scratchForward.normalize();
        pitchRadians = clamp((float) Math.asin(scratchForward.y), -MAX_PITCH_RADIANS, MAX_PITCH_RADIANS);
        yawRadians = (float) Math.atan2(-scratchForward.x, -scratchForward.z);
        transform.setPosition(position.x, position.y, position.z);
        framing = false;
        applyOrientation();
    }

    private void applyOrientation() {
        scratchRotation.identity().rotateY(yawRadians).rotateX(pitchRadians);
        transform.setRotation(scratchRotation);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
