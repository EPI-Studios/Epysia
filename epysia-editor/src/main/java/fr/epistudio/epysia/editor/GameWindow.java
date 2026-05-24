package fr.epistudio.epysia.editor;

import com.miry.core.SecondaryWindow;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.nio.FloatBuffer;

import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
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
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

public final class GameWindow implements AutoCloseable {

    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;
    private static final float CLEAR_R = 0.04f;
    private static final float CLEAR_G = 0.05f;
    private static final float CLEAR_B = 0.07f;

    private static final String VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec2 a_pos;
            layout(location = 1) in vec2 a_uv;
            out vec2 v_uv;
            void main() {
                v_uv = a_uv;
                gl_Position = vec4(a_pos, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            in vec2 v_uv;
            out vec4 o_color;
            uniform sampler2D u_source;
            void main() {
                o_color = texture(u_source, v_uv);
            }
            """;

    private final SecondaryWindow window;
    private final long editorContextHandle;
    private final GLCapabilities gameCapabilities;
    private final int blitProgram;
    private final int blitSourceUniform;
    private final int quadVao;
    private final int quadVbo;

    public GameWindow(long editorContextHandle, String projectName) {
        this.editorContextHandle = editorContextHandle;
        this.window = new SecondaryWindow(editorContextHandle, "Epysia Play: " + projectName,
                DEFAULT_WIDTH, DEFAULT_HEIGHT);
        this.window.makeContextCurrent();
        this.gameCapabilities = GL.createCapabilities();
        this.blitProgram = createBlitProgram();
        this.blitSourceUniform = glGetUniformLocation(blitProgram, "u_source");
        this.quadVao = glGenVertexArrays();
        this.quadVbo = glGenBuffers();
        uploadQuad();
        glfwMakeContextCurrent(editorContextHandle);
    }

    private void uploadQuad() {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(24);
        buffer.put(new float[]{
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                1f, 1f, 1f, 1f,
                -1f, -1f, 0f, 0f,
                1f, 1f, 1f, 1f,
                -1f, 1f, 0f, 1f
        }).flip();
        glBindVertexArray(quadVao);
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 16, 0L);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 16, 8L);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glBindVertexArray(0);
    }

    private int createBlitProgram() {
        int vertexShader = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        int program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("Blit program link failed: " + log);
        }
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        return program;
    }

    private int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Shader compile failed: " + log);
        }
        return shader;
    }

    public boolean shouldClose() {
        return window.shouldClose();
    }

    public void render(int sourceTextureGlName) {
        window.makeContextCurrent();
        GL.setCapabilities(gameCapabilities);
        glViewport(0, 0, Math.max(1, window.framebufferWidth()), Math.max(1, window.framebufferHeight()));
        glDisable(GL_DEPTH_TEST);
        glClearColor(CLEAR_R, CLEAR_G, CLEAR_B, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        if (sourceTextureGlName > 0) {
            blitSource(sourceTextureGlName);
        }
        window.swapBuffers();
        glfwMakeContextCurrent(editorContextHandle);
    }

    private void blitSource(int sourceTextureGlName) {
        glUseProgram(blitProgram);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sourceTextureGlName);
        glUniform1i(blitSourceUniform, 0);
        glBindVertexArray(quadVao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
    }

    public int windowWidth() {
        return window.windowWidth();
    }

    public int windowHeight() {
        return window.windowHeight();
    }

    @Override
    public void close() {
        window.makeContextCurrent();
        GL.setCapabilities(gameCapabilities);
        glDeleteVertexArrays(quadVao);
        glDeleteBuffers(quadVbo);
        glDeleteProgram(blitProgram);
        glfwMakeContextCurrent(editorContextHandle);
        window.close();
    }
}
