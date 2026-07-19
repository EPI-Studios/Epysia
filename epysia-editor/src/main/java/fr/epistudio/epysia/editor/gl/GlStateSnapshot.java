package fr.epistudio.epysia.editor.gl;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_BOX;
import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGetBoolean;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glGetIntegerv;
import static org.lwjgl.opengl.GL11.glIsEnabled;
import static org.lwjgl.opengl.GL11.glScissor;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA;
import static org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

public final class GlStateSnapshot {

    private final int drawFramebuffer;
    private final int readFramebuffer;
    private final int vao;
    private final int program;
    private final int elementBuffer;
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private final boolean depthTest;
    private final boolean depthWriteMask;
    private final boolean cullFace;
    private final boolean blend;
    private final boolean scissorTest;
    private final int blendSrcRgb;
    private final int blendDstRgb;
    private final int blendSrcAlpha;
    private final int blendDstAlpha;

    private GlStateSnapshot() {
        drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        readFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        program = glGetInteger(GL_CURRENT_PROGRAM);
        elementBuffer = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetIntegerv(GL_SCISSOR_BOX, scissorBox);
        depthTest = glIsEnabled(GL_DEPTH_TEST);
        depthWriteMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        cullFace = glIsEnabled(GL_CULL_FACE);
        blend = glIsEnabled(GL_BLEND);
        scissorTest = glIsEnabled(GL_SCISSOR_TEST);
        blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        blendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
        blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
    }

    public static GlStateSnapshot capture() {
        return new GlStateSnapshot();
    }

    public void restore() {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
        glBindVertexArray(vao);
        glUseProgram(program);
        org.lwjgl.opengl.GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        toggle(GL_DEPTH_TEST, depthTest);
        glDepthMask(depthWriteMask);
        toggle(GL_CULL_FACE, cullFace);
        toggle(GL_BLEND, blend);
        toggle(GL_SCISSOR_TEST, scissorTest);
        glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
    }

    private static void toggle(int capability, boolean enabled) {
        if (enabled) {
            glEnable(capability);
        } else {
            glDisable(capability);
        }
    }
}
