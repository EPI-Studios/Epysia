package fr.epistudio.epysia.render.lighting;

import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.SurfaceShaderComposer;

public final class ProbeFaceCopyShader {

    public static final String PATH = "lighting/probe_face_copy.comp.glsl";

    private ProbeFaceCopyShader() {
    }

    public static String source(int faceSize, int workgroupSize) {
        LoadedShader loaded = ShaderLoader.autoDetect().load(PATH);
        return SurfaceShaderComposer.injectDefineBlock(loaded, defines(faceSize, workgroupSize)).source();
    }

    private static String defines(int faceSize, int workgroupSize) {
        return "#define PROBE_FACE_SIZE " + faceSize + "\n"
                + "#define PROBE_WORKGROUP_SIZE " + workgroupSize + "\n";
    }
}
