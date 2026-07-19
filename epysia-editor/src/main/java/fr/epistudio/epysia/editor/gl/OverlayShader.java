package fr.epistudio.epysia.editor.gl;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glUseProgram;

public final class OverlayShader implements AutoCloseable {

    public static final float NO_FADE_DISTANCE = 1.0e8f;

    private static final Vector3f NO_FADE_ORIGIN = new Vector3f();
    private static final float FADE_START_FRACTION = 0.55f;

    private static final String VERTEX_SOURCE = """
            #version 430 core
            layout(location = 0) in vec3 inPosition;
            layout(location = 1) in vec3 inColor;
            uniform mat4 uMvp;
            out vec3 vColor;
            out vec3 vWorldPosition;
            void main() {
                vColor = inColor;
                vWorldPosition = inPosition;
                gl_Position = uMvp * vec4(inPosition, 1.0);
            }
            """;

    private static final String FRAGMENT_SOURCE = """
            #version 430 core
            in vec3 vColor;
            in vec3 vWorldPosition;
            uniform float uAlpha;
            uniform float uColorMul;
            uniform vec3 uCameraPosition;
            uniform float uFadeStart;
            uniform float uFadeEnd;
            out vec4 outColor;
            void main() {
                float cameraDistance = distance(vWorldPosition, uCameraPosition);
                float fade = 1.0 - smoothstep(uFadeStart, uFadeEnd, cameraDistance);
                outColor = vec4(vColor * uColorMul, uAlpha * fade);
            }
            """;

    private final int program;
    private final int mvpLocation;
    private final int alphaLocation;
    private final int colorMulLocation;
    private final int cameraPositionLocation;
    private final int fadeStartLocation;
    private final int fadeEndLocation;

    public OverlayShader() {
        int vertex = compile(GL_VERTEX_SHADER, VERTEX_SOURCE);
        int fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT_SOURCE);
        program = link(vertex, fragment);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        mvpLocation = glGetUniformLocation(program, "uMvp");
        alphaLocation = glGetUniformLocation(program, "uAlpha");
        colorMulLocation = glGetUniformLocation(program, "uColorMul");
        cameraPositionLocation = glGetUniformLocation(program, "uCameraPosition");
        fadeStartLocation = glGetUniformLocation(program, "uFadeStart");
        fadeEndLocation = glGetUniformLocation(program, "uFadeEnd");
    }

    public void bind(Matrix4f mvp, float alpha, float colorMultiplier) {
        bind(mvp, alpha, colorMultiplier, NO_FADE_ORIGIN, NO_FADE_DISTANCE);
    }

    public void bind(Matrix4f mvp, float alpha, float colorMultiplier,
                     Vector3f cameraPosition, float fadeEndDistance) {
        glUseProgram(program);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            mvp.get(buffer);
            glUniformMatrix4fv(mvpLocation, false, buffer);
        }
        glUniform1f(alphaLocation, alpha);
        glUniform1f(colorMulLocation, colorMultiplier);
        glUniform3f(cameraPositionLocation, cameraPosition.x, cameraPosition.y, cameraPosition.z);
        glUniform1f(fadeStartLocation, fadeEndDistance * FADE_START_FRACTION);
        glUniform1f(fadeEndLocation, fadeEndDistance);
    }

    public void setFade(Vector3f cameraPosition, float fadeEndDistance) {
        glUniform3f(cameraPositionLocation, cameraPosition.x, cameraPosition.y, cameraPosition.z);
        glUniform1f(fadeStartLocation, fadeEndDistance * FADE_START_FRACTION);
        glUniform1f(fadeEndLocation, fadeEndDistance);
    }

    public void unbind() {
        glUseProgram(0);
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException("Overlay shader compile failed: " + glGetShaderInfoLog(shader));
        }
        return shader;
    }

    private static int link(int vertex, int fragment) {
        int linked = glCreateProgram();
        glAttachShader(linked, vertex);
        glAttachShader(linked, fragment);
        glLinkProgram(linked);
        if (glGetProgrami(linked, GL_LINK_STATUS) == 0) {
            throw new IllegalStateException("Overlay shader link failed: " + glGetProgramInfoLog(linked));
        }
        return linked;
    }

    @Override
    public void close() {
        glDeleteProgram(program);
    }
}
