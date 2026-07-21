package fr.epistudio.epysia.editor.importer;

import de.javagl.jgltf.model.AccessorByteData;
import de.javagl.jgltf.model.AccessorData;
import de.javagl.jgltf.model.AccessorFloatData;
import de.javagl.jgltf.model.AccessorIntData;
import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.AccessorShortData;
import de.javagl.jgltf.model.AnimationModel;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.ImageModel;
import de.javagl.jgltf.model.MaterialModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SkinModel;
import de.javagl.jgltf.model.TextureModel;
import de.javagl.jgltf.model.io.GltfAsset;
import de.javagl.jgltf.model.io.GltfAssetReader;
import de.javagl.jgltf.model.io.GltfModelReader;
import de.javagl.jgltf.model.v2.MaterialModelV2;
import de.javagl.jgltf.impl.v2.GlTF;
import de.javagl.jgltf.impl.v2.MaterialPbrMetallicRoughness;
import de.javagl.jgltf.impl.v2.TextureInfo;
import fr.epistudio.epysia.animation.Clip;
import fr.epistudio.epysia.animation.ClipChannel;
import fr.epistudio.epysia.animation.ClipInterpolation;
import fr.epistudio.epysia.animation.ClipProperty;
import fr.epistudio.epysia.animation.Joint;
import fr.epistudio.epysia.animation.Skeleton;
import fr.epistudio.epysia.assets.epyclip.EpyClipWriter;
import fr.epistudio.epysia.assets.epymesh.EpyMeshWriter;
import fr.epistudio.epysia.components.Animator;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.mesh.MeshData;
import fr.epistudio.epysia.render.mesh.Submesh;
import fr.epistudio.epysia.prefab.PrefabWriter;
import fr.epistudio.epysia.scene.serialization.MaterialJsonCodec;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GltfImporter {

    private static final int MODE_POINTS = 0;
    private static final int MODE_LINES = 1;
    private static final int MODE_LINE_LOOP = 2;
    private static final int MODE_LINE_STRIP = 3;
    private static final int MODE_TRIANGLES = 4;
    private static final int MODE_TRIANGLE_STRIP = 5;
    private static final int MODE_TRIANGLE_FAN = 6;
    private static final String TEXTURE_TRANSFORM = "KHR_texture_transform";

    private GltfImporter() {
    }

    public static GltfImportResult importFile(Path source, Path outputDirectory, ComponentRegistry componentRegistry) {
        GltfModel model = readModel(source);
        List<String> warnings = new ArrayList<>();
        Map<MaterialModel, MaterialUvHints> uvHints = buildMaterialUvHints(model, source, warnings);
        Map<SkinModel, SkeletonBuild> skeletonBuilds = buildSkeletonBuilds(model);
        List<Path> meshFiles = importMeshes(model, skeletonBuilds, uvHints, outputDirectory, warnings);
        List<Path> clipFiles = importAnimations(model, skeletonBuilds, outputDirectory, warnings);
        MaterialImport materials = importMaterials(model, outputDirectory, warnings);
        Optional<Path> prefabFile = buildPrefab(model, source, outputDirectory, meshFiles, clipFiles,
                materials.byModel(), componentRegistry, warnings);
        return new GltfImportResult(meshFiles, clipFiles, materials.files(), prefabFile, warnings);
    }

    private static GltfModel readModel(Path source) {
        try {
            return new GltfModelReader().read(source);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read glTF file " + source + ": " + exception.getMessage(), exception);
        }
    }

    private static Map<SkinModel, SkeletonBuild> buildSkeletonBuilds(GltfModel model) {
        Map<SkinModel, SkeletonBuild> skeletonBuilds = new IdentityHashMap<>();
        for (SkinModel skin : model.getSkinModels()) {
            skeletonBuilds.put(skin, buildSkeleton(skin));
        }
        return skeletonBuilds;
    }

    private static List<Path> importMeshes(GltfModel model, Map<SkinModel, SkeletonBuild> skeletonBuilds,
            Map<MaterialModel, MaterialUvHints> uvHints, Path outputDirectory, List<String> warnings) {
        List<Path> meshFiles = new ArrayList<>();
        List<MeshModel> meshModels = model.getMeshModels();
        for (int meshIndex = 0; meshIndex < meshModels.size(); meshIndex++) {
            meshFiles.add(importMesh(model, meshModels.get(meshIndex), meshIndex, skeletonBuilds, uvHints, outputDirectory, warnings));
        }
        return meshFiles;
    }

    private static Path importMesh(GltfModel model, MeshModel meshModel, int meshIndex,
            Map<SkinModel, SkeletonBuild> skeletonBuilds, Map<MaterialModel, MaterialUvHints> uvHints,
            Path outputDirectory, List<String> warnings) {
        String meshName = meshName(meshModel, meshIndex);
        Optional<SkinModel> skinModel = findSkinForMesh(model, meshModel);
        Optional<SkeletonBuild> skeletonBuild = skinModel.map(skeletonBuilds::get);
        List<PrimitiveVertexData> primitives = readPrimitives(meshModel, uvHints, meshName, skeletonBuild, warnings);
        MeshData meshData = mergePrimitives(primitives, meshName, warnings);
        Optional<Skeleton> skeleton = meshData.hasSkin() ? skeletonBuild.map(SkeletonBuild::skeleton) : Optional.empty();
        Path outputPath = outputDirectory.resolve(meshName + ".epymesh");
        EpyMeshWriter.writeToFile(outputPath, meshData, Optional.empty(), skeleton);
        return outputPath;
    }

    private static String meshName(MeshModel meshModel, int meshIndex) {
        return Optional.ofNullable(meshModel.getName()).orElse("mesh" + meshIndex);
    }

    private static Optional<SkinModel> findSkinForMesh(GltfModel model, MeshModel meshModel) {
        for (NodeModel node : model.getNodeModels()) {
            if (node.getMeshModels().contains(meshModel) && node.getSkinModel() != null) {
                return Optional.of(node.getSkinModel());
            }
        }
        return Optional.empty();
    }

    private static List<PrimitiveVertexData> readPrimitives(MeshModel meshModel, Map<MaterialModel, MaterialUvHints> uvHints,
            String meshName, Optional<SkeletonBuild> skeletonBuild, List<String> warnings) {
        List<PrimitiveVertexData> primitives = new ArrayList<>();
        List<MeshPrimitiveModel> primitiveModels = meshModel.getMeshPrimitiveModels();
        for (int primitiveIndex = 0; primitiveIndex < primitiveModels.size(); primitiveIndex++) {
            readPrimitive(primitiveModels.get(primitiveIndex), uvHints, meshName, primitiveIndex, skeletonBuild, warnings)
                    .ifPresent(primitives::add);
        }
        return primitives;
    }

    private static Optional<PrimitiveVertexData> readPrimitive(MeshPrimitiveModel primitive,
            Map<MaterialModel, MaterialUvHints> uvHints, String meshName, int primitiveIndex,
            Optional<SkeletonBuild> skeletonBuild, List<String> warnings) {
        Optional<int[]> indices = readTriangleIndices(primitive, meshName, primitiveIndex, warnings);
        if (indices.isEmpty()) {
            return Optional.empty();
        }
        Map<String, AccessorModel> attributes = primitive.getAttributes();
        float[] positions = readFloats(requireAttribute(attributes, "POSITION", meshName, primitiveIndex), 3);
        float[] normals = readNormals(attributes, meshName, primitiveIndex);
        MaterialUvHints hints = uvHints.getOrDefault(primitive.getMaterialModel(), MaterialUvHints.identity());
        float[] uvs = readUvs(attributes, hints, meshName, primitiveIndex, warnings);
        warnUnsupportedFeatures(primitive, attributes, meshName, primitiveIndex, warnings);
        SkinAttributeData skinData = readSkinAttributes(attributes, positions.length / 3, skeletonBuild, meshName, primitiveIndex, warnings);
        return Optional.of(new PrimitiveVertexData(positions, normals, uvs, skinData.jointIndices(), skinData.jointWeights(), indices.get()));
    }

    private static Optional<int[]> readTriangleIndices(MeshPrimitiveModel primitive, String meshName, int primitiveIndex,
            List<String> warnings) {
        int mode = primitive.getMode();
        if (!isTriangleMode(mode)) {
            warnings.add("Mesh " + meshName + " primitive " + primitiveIndex + " uses primitive mode " + modeName(mode)
                    + "; skipped because only triangle primitives are supported.");
            return Optional.empty();
        }
        return Optional.of(triangulate(mode, readIndices(primitive, meshName, primitiveIndex)));
    }

    private static int[] triangulate(int mode, int[] indices) {
        if (mode == MODE_TRIANGLE_STRIP) {
            return triangulateStrip(indices);
        }
        if (mode == MODE_TRIANGLE_FAN) {
            return triangulateFan(indices);
        }
        return indices;
    }

    private static int[] triangulateStrip(int[] indices) {
        int triangleCount = Math.max(0, indices.length - 2);
        int[] result = new int[triangleCount * 3];
        for (int triangle = 0; triangle < triangleCount; triangle++) {
            int base = triangle * 3;
            boolean even = (triangle & 1) == 0;
            result[base] = indices[triangle];
            result[base + 1] = indices[triangle + (even ? 1 : 2)];
            result[base + 2] = indices[triangle + (even ? 2 : 1)];
        }
        return result;
    }

    private static int[] triangulateFan(int[] indices) {
        int triangleCount = Math.max(0, indices.length - 2);
        int[] result = new int[triangleCount * 3];
        for (int triangle = 0; triangle < triangleCount; triangle++) {
            int base = triangle * 3;
            result[base] = indices[0];
            result[base + 1] = indices[triangle + 1];
            result[base + 2] = indices[triangle + 2];
        }
        return result;
    }

    private static boolean isTriangleMode(int mode) {
        return mode == MODE_TRIANGLES || mode == MODE_TRIANGLE_STRIP || mode == MODE_TRIANGLE_FAN;
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case MODE_POINTS -> "POINTS";
            case MODE_LINES -> "LINES";
            case MODE_LINE_LOOP -> "LINE_LOOP";
            case MODE_LINE_STRIP -> "LINE_STRIP";
            default -> "UNKNOWN(" + mode + ")";
        };
    }

    private static float[] readNormals(Map<String, AccessorModel> attributes, String meshName, int primitiveIndex) {
        AccessorModel accessor = attributes.get("NORMAL");
        if (accessor == null) {
            throw new EpysiaException("Mesh " + meshName + " primitive " + primitiveIndex + " has no NORMAL accessor.");
        }
        return readFloats(accessor, 3);
    }

    private static float[] readUvs(Map<String, AccessorModel> attributes, MaterialUvHints hints, String meshName,
            int primitiveIndex, List<String> warnings) {
        Optional<AccessorModel> accessor = selectUvAccessor(attributes, hints.baseColorTexCoord(), meshName, primitiveIndex, warnings);
        if (accessor.isEmpty()) {
            return new float[0];
        }
        float[] uvs = readFloats(accessor.get(), 2);
        return hints.baseColorTransform().map(transform -> transform.apply(uvs)).orElse(uvs);
    }

    private static Optional<AccessorModel> selectUvAccessor(Map<String, AccessorModel> attributes, int texCoordSet,
            String meshName, int primitiveIndex, List<String> warnings) {
        AccessorModel selected = attributes.get("TEXCOORD_" + texCoordSet);
        if (selected != null) {
            return Optional.of(selected);
        }
        AccessorModel fallback = attributes.get("TEXCOORD_0");
        if (fallback != null && texCoordSet != 0) {
            warnings.add("Mesh " + meshName + " primitive " + primitiveIndex + " has no TEXCOORD_" + texCoordSet
                    + "; using TEXCOORD_0 instead.");
        }
        return Optional.ofNullable(fallback);
    }

    private static int[] readIndices(MeshPrimitiveModel primitive, String meshName, int primitiveIndex) {
        AccessorModel accessor = primitive.getIndices();
        if (accessor == null) {
            throw new EpysiaException("Mesh " + meshName + " primitive " + primitiveIndex + " has no index accessor.");
        }
        return readUnsignedInts(accessor, 1);
    }

    private static void warnUnsupportedFeatures(MeshPrimitiveModel primitive, Map<String, AccessorModel> attributes,
            String meshName, int primitiveIndex, List<String> warnings) {
        if (!primitive.getTargets().isEmpty()) {
            warnings.add("Mesh " + meshName + " primitive " + primitiveIndex + " has morph targets; ignored.");
        }
        if (attributes.containsKey("WEIGHTS_1") || attributes.containsKey("JOINTS_1")) {
            warnings.add("Mesh " + meshName + " primitive " + primitiveIndex
                    + " has more than 4 joint influences; extra influences ignored.");
        }
    }

    private static SkinAttributeData readSkinAttributes(Map<String, AccessorModel> attributes, int vertexCount,
            Optional<SkeletonBuild> skeletonBuild, String meshName, int primitiveIndex, List<String> warnings) {
        if (skeletonBuild.isEmpty()) {
            return SkinAttributeData.empty();
        }
        AccessorModel jointsAccessor = attributes.get("JOINTS_0");
        AccessorModel weightsAccessor = attributes.get("WEIGHTS_0");
        if (jointsAccessor == null || weightsAccessor == null) {
            warnings.add("Mesh " + meshName + " primitive " + primitiveIndex
                    + " is referenced by a skinned node but has no skin data; imported without skin.");
            return SkinAttributeData.empty();
        }
        int[] rawJointIndices = readUnsignedInts(jointsAccessor, 4);
        short[] jointIndices = remapJointIndices(rawJointIndices, skeletonBuild.get().remap());
        float[] jointWeights = renormalizeWeights(readFloats(weightsAccessor, 4), vertexCount);
        return new SkinAttributeData(jointIndices, jointWeights);
    }

    private static AccessorModel requireAttribute(Map<String, AccessorModel> attributes, String name, String meshName, int primitiveIndex) {
        AccessorModel accessor = attributes.get(name);
        if (accessor == null) {
            throw new EpysiaException("Mesh " + meshName + " primitive " + primitiveIndex + " has no " + name + " accessor.");
        }
        return accessor;
    }

    private static short[] remapJointIndices(int[] rawJointIndices, int[] remap) {
        short[] remapped = new short[rawJointIndices.length];
        for (int index = 0; index < rawJointIndices.length; index++) {
            remapped[index] = (short) remap[rawJointIndices[index]];
        }
        return remapped;
    }

    private static float[] renormalizeWeights(float[] weights, int vertexCount) {
        float[] result = weights.clone();
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            renormalizeVertexWeights(result, vertex * MeshData.INFLUENCES_PER_VERTEX);
        }
        return result;
    }

    private static void renormalizeVertexWeights(float[] weights, int base) {
        float sum = weights[base] + weights[base + 1] + weights[base + 2] + weights[base + 3];
        if (sum <= 1.0e-8f) {
            return;
        }
        weights[base] /= sum;
        weights[base + 1] /= sum;
        weights[base + 2] /= sum;
        weights[base + 3] /= sum;
    }

    private static float[] readFloats(AccessorModel accessor, int componentsPerElement) {
        AccessorData data = accessor.getAccessorData();
        if (data instanceof AccessorFloatData floatData) {
            return flattenFloat(floatData, componentsPerElement);
        }
        if (data instanceof AccessorShortData shortData) {
            return flattenNormalizedShort(shortData, componentsPerElement, accessor.isNormalized());
        }
        if (data instanceof AccessorByteData byteData) {
            return flattenNormalizedByte(byteData, componentsPerElement, accessor.isNormalized());
        }
        throw new EpysiaException("Unsupported accessor component type for a float attribute.");
    }

    private static float[] flattenFloat(AccessorFloatData data, int componentsPerElement) {
        float[] values = new float[data.getNumElements() * componentsPerElement];
        for (int element = 0; element < data.getNumElements(); element++) {
            for (int component = 0; component < componentsPerElement; component++) {
                values[element * componentsPerElement + component] = data.get(element, component);
            }
        }
        return values;
    }

    private static float[] flattenNormalizedShort(AccessorShortData data, int componentsPerElement, boolean normalized) {
        float divisor = normalized ? 65535.0f : 1.0f;
        float[] values = new float[data.getNumElements() * componentsPerElement];
        for (int element = 0; element < data.getNumElements(); element++) {
            for (int component = 0; component < componentsPerElement; component++) {
                values[element * componentsPerElement + component] = data.getInt(element, component) / divisor;
            }
        }
        return values;
    }

    private static float[] flattenNormalizedByte(AccessorByteData data, int componentsPerElement, boolean normalized) {
        float divisor = normalized ? 255.0f : 1.0f;
        float[] values = new float[data.getNumElements() * componentsPerElement];
        for (int element = 0; element < data.getNumElements(); element++) {
            for (int component = 0; component < componentsPerElement; component++) {
                values[element * componentsPerElement + component] = data.getInt(element, component) / divisor;
            }
        }
        return values;
    }

    private static int[] readUnsignedInts(AccessorModel accessor, int componentsPerElement) {
        AccessorData data = accessor.getAccessorData();
        if (data instanceof AccessorShortData shortData) {
            return flattenInt(shortData.getNumElements(), componentsPerElement, shortData::getInt);
        }
        if (data instanceof AccessorByteData byteData) {
            return flattenInt(byteData.getNumElements(), componentsPerElement, byteData::getInt);
        }
        if (data instanceof AccessorIntData intData) {
            return flattenInt(intData.getNumElements(), componentsPerElement, intData::get);
        }
        throw new EpysiaException("Unsupported accessor component type for an integer attribute.");
    }

    private static int[] flattenInt(int numElements, int componentsPerElement, IntElementReader reader) {
        int[] values = new int[numElements * componentsPerElement];
        for (int element = 0; element < numElements; element++) {
            for (int component = 0; component < componentsPerElement; component++) {
                values[element * componentsPerElement + component] = reader.read(element, component);
            }
        }
        return values;
    }

    private static SkeletonBuild buildSkeleton(SkinModel skin) {
        List<NodeModel> jointNodes = skin.getJoints();
        int[] parentOriginalIndex = computeParentOriginalIndices(jointNodes);
        List<Integer> order = topologicalOrder(parentOriginalIndex);
        int[] remap = buildRemap(order, jointNodes.size());
        List<Joint> joints = buildJoints(skin, jointNodes, order, parentOriginalIndex, remap);
        return new SkeletonBuild(new Skeleton(joints), remap);
    }

    private static int[] computeParentOriginalIndices(List<NodeModel> jointNodes) {
        int[] parentOriginalIndex = new int[jointNodes.size()];
        for (int index = 0; index < jointNodes.size(); index++) {
            parentOriginalIndex[index] = jointNodes.indexOf(jointNodes.get(index).getParent());
        }
        return parentOriginalIndex;
    }

    private static List<Integer> topologicalOrder(int[] parentOriginalIndex) {
        List<Integer> order = new ArrayList<>();
        boolean[] visited = new boolean[parentOriginalIndex.length];
        for (int index = 0; index < parentOriginalIndex.length; index++) {
            if (parentOriginalIndex[index] == -1) {
                visitJoint(index, parentOriginalIndex, visited, order);
            }
        }
        return order;
    }

    private static void visitJoint(int index, int[] parentOriginalIndex, boolean[] visited, List<Integer> order) {
        if (visited[index]) {
            return;
        }
        visited[index] = true;
        order.add(index);
        for (int candidate = 0; candidate < parentOriginalIndex.length; candidate++) {
            if (parentOriginalIndex[candidate] == index) {
                visitJoint(candidate, parentOriginalIndex, visited, order);
            }
        }
    }

    private static int[] buildRemap(List<Integer> order, int jointCount) {
        int[] remap = new int[jointCount];
        for (int newIndex = 0; newIndex < order.size(); newIndex++) {
            remap[order.get(newIndex)] = newIndex;
        }
        return remap;
    }

    private static List<Joint> buildJoints(SkinModel skin, List<NodeModel> jointNodes, List<Integer> order,
            int[] parentOriginalIndex, int[] remap) {
        List<Joint> joints = new ArrayList<>();
        for (int originalIndex : order) {
            joints.add(buildJoint(skin, jointNodes, originalIndex, parentOriginalIndex, remap));
        }
        return joints;
    }

    private static Joint buildJoint(SkinModel skin, List<NodeModel> jointNodes, int originalIndex,
            int[] parentOriginalIndex, int[] remap) {
        NodeModel node = jointNodes.get(originalIndex);
        int parentOriginal = parentOriginalIndex[originalIndex];
        int parentNewIndex = parentOriginal == -1 ? -1 : remap[parentOriginal];
        String name = Optional.ofNullable(node.getName()).orElse("joint" + originalIndex);
        float[] localBindTransform = node.computeLocalTransform(null);
        float[] inverseBindMatrix = readInverseBindMatrix(skin, originalIndex);
        return new Joint(name, parentNewIndex, localBindTransform, inverseBindMatrix);
    }

    private static float[] readInverseBindMatrix(SkinModel skin, int jointIndex) {
        if (skin.getInverseBindMatrices() == null) {
            return identityMatrix();
        }
        return skin.getInverseBindMatrix(jointIndex, null);
    }

    private static float[] identityMatrix() {
        float[] matrix = new float[16];
        matrix[0] = 1.0f;
        matrix[5] = 1.0f;
        matrix[10] = 1.0f;
        matrix[15] = 1.0f;
        return matrix;
    }

    private static List<Path> importAnimations(GltfModel model, Map<SkinModel, SkeletonBuild> skeletonBuilds,
            Path outputDirectory, List<String> warnings) {
        Map<NodeModel, JointReference> jointReferences = buildJointReferences(model);
        List<Path> clipFiles = new ArrayList<>();
        List<AnimationModel> animationModels = model.getAnimationModels();
        for (int animationIndex = 0; animationIndex < animationModels.size(); animationIndex++) {
            importAnimation(animationModels.get(animationIndex), animationIndex, jointReferences, skeletonBuilds,
                    outputDirectory, warnings).ifPresent(clipFiles::add);
        }
        return clipFiles;
    }

    private static Map<NodeModel, JointReference> buildJointReferences(GltfModel model) {
        Map<NodeModel, JointReference> jointReferences = new IdentityHashMap<>();
        for (SkinModel skin : model.getSkinModels()) {
            addJointReferences(skin, jointReferences);
        }
        return jointReferences;
    }

    private static void addJointReferences(SkinModel skin, Map<NodeModel, JointReference> jointReferences) {
        List<NodeModel> joints = skin.getJoints();
        for (int jointIndex = 0; jointIndex < joints.size(); jointIndex++) {
            jointReferences.putIfAbsent(joints.get(jointIndex), new JointReference(skin, jointIndex));
        }
    }

    private static Optional<Path> importAnimation(AnimationModel animation, int animationIndex,
            Map<NodeModel, JointReference> jointReferences, Map<SkinModel, SkeletonBuild> skeletonBuilds,
            Path outputDirectory, List<String> warnings) {
        String animationName = Optional.ofNullable(animation.getName()).orElse("clip" + animationIndex);
        Optional<SkinModel> owningSkin = findOwningSkin(animation, jointReferences);
        if (owningSkin.isEmpty()) {
            warnings.add("Animation " + animationName + " has no channels targeting a skinned joint; skipped.");
            return Optional.empty();
        }
        SkeletonBuild skeletonBuild = skeletonBuilds.get(owningSkin.get());
        List<ClipChannel> channels = buildClipChannels(animation, animationName, owningSkin.get(), jointReferences,
                skeletonBuild.remap(), warnings);
        if (channels.isEmpty()) {
            warnings.add("Animation " + animationName + " has no valid channels; skipped.");
            return Optional.empty();
        }
        Clip clip = new Clip(animationName, computeDuration(channels), skeletonBuild.skeleton().nameChecksum(), channels);
        return Optional.of(writeClip(clip, animationName, animationIndex, outputDirectory));
    }

    private static Path writeClip(Clip clip, String animationName, int animationIndex, Path outputDirectory) {
        Path outputPath = outputDirectory.resolve(clipFileName(animationName, animationIndex) + ".epyclip");
        EpyClipWriter.writeToFile(outputPath, clip);
        return outputPath;
    }

    private static Optional<SkinModel> findOwningSkin(AnimationModel animation, Map<NodeModel, JointReference> jointReferences) {
        for (AnimationModel.Channel channel : animation.getChannels()) {
            JointReference reference = jointReferences.get(channel.getNodeModel());
            if (reference != null) {
                return Optional.of(reference.skin());
            }
        }
        return Optional.empty();
    }

    private static List<ClipChannel> buildClipChannels(AnimationModel animation, String animationName, SkinModel owningSkin,
            Map<NodeModel, JointReference> jointReferences, int[] remap, List<String> warnings) {
        List<ClipChannel> channels = new ArrayList<>();
        for (AnimationModel.Channel channel : animation.getChannels()) {
            buildClipChannel(channel, animationName, owningSkin, jointReferences, remap, warnings).ifPresent(channels::add);
        }
        return channels;
    }

    private static Optional<ClipChannel> buildClipChannel(AnimationModel.Channel channel, String animationName,
            SkinModel owningSkin, Map<NodeModel, JointReference> jointReferences, int[] remap, List<String> warnings) {
        Optional<ClipProperty> property = mapProperty(channel.getPath());
        if (property.isEmpty()) {
            warnings.add("Animation " + animationName + " channel targets morph target weights; dropped.");
            return Optional.empty();
        }
        JointReference reference = jointReferences.get(channel.getNodeModel());
        if (reference == null || reference.skin() != owningSkin) {
            warnings.add("Animation " + animationName + " channel targets a node outside the skin; dropped.");
            return Optional.empty();
        }
        return Optional.of(buildClipChannel(channel, property.get(), remap[reference.jointIndex()]));
    }

    private static ClipChannel buildClipChannel(AnimationModel.Channel channel, ClipProperty property, int jointIndex) {
        AnimationModel.Sampler sampler = channel.getSampler();
        ClipInterpolation interpolation = mapInterpolation(sampler.getInterpolation());
        float[] times = readFloats(sampler.getInput(), 1);
        float[] values = readFloats(sampler.getOutput(), property.componentCount());
        return new ClipChannel(jointIndex, property, interpolation, times, values);
    }

    private static Optional<ClipProperty> mapProperty(String path) {
        return switch (path) {
            case "translation" -> Optional.of(ClipProperty.TRANSLATION);
            case "rotation" -> Optional.of(ClipProperty.ROTATION);
            case "scale" -> Optional.of(ClipProperty.SCALE);
            default -> Optional.empty();
        };
    }

    private static ClipInterpolation mapInterpolation(AnimationModel.Interpolation interpolation) {
        return switch (interpolation) {
            case STEP -> ClipInterpolation.STEP;
            case LINEAR -> ClipInterpolation.LINEAR;
            case CUBICSPLINE -> ClipInterpolation.CUBIC_SPLINE;
        };
    }

    private static float computeDuration(List<ClipChannel> channels) {
        float duration = 0.0f;
        for (ClipChannel channel : channels) {
            float[] times = channel.times();
            duration = Math.max(duration, times[times.length - 1]);
        }
        return duration;
    }

    private static String clipFileName(String animationName, int animationIndex) {
        return sanitizeFileName(animationName, "clip" + animationIndex);
    }

    private static String sanitizeFileName(String name, String fallback) {
        String sanitized = name.replaceAll("[^A-Za-z0-9_-]", "");
        return sanitized.isEmpty() ? fallback : sanitized;
    }

    private static MaterialImport importMaterials(GltfModel model, Path outputDirectory, List<String> warnings) {
        Map<ImageModel, Integer> imageIndices = buildImageIndices(model);
        Map<ImageModel, String> writtenImages = new IdentityHashMap<>();
        FileNameAllocator materialFileNames = new FileNameAllocator();
        FileNameAllocator imageFileNames = new FileNameAllocator();
        List<Path> materialFiles = new ArrayList<>();
        Map<MaterialModel, Path> byModel = new IdentityHashMap<>();
        List<MaterialModel> materialModels = model.getMaterialModels();
        for (int materialIndex = 0; materialIndex < materialModels.size(); materialIndex++) {
            MaterialModel materialModel = materialModels.get(materialIndex);
            importMaterial(materialModel, materialIndex, outputDirectory, imageIndices,
                    writtenImages, materialFileNames, imageFileNames, warnings).ifPresent(path -> {
                        materialFiles.add(path);
                        byModel.put(materialModel, path);
                    });
        }
        return new MaterialImport(materialFiles, byModel);
    }

    private static Map<ImageModel, Integer> buildImageIndices(GltfModel model) {
        Map<ImageModel, Integer> imageIndices = new IdentityHashMap<>();
        List<ImageModel> images = model.getImageModels();
        for (int imageIndex = 0; imageIndex < images.size(); imageIndex++) {
            imageIndices.put(images.get(imageIndex), imageIndex);
        }
        return imageIndices;
    }

    private static Optional<Path> importMaterial(MaterialModel materialModel, int materialIndex, Path outputDirectory,
            Map<ImageModel, Integer> imageIndices, Map<ImageModel, String> writtenImages,
            FileNameAllocator materialFileNames, FileNameAllocator imageFileNames, List<String> warnings) {
        if (!(materialModel instanceof MaterialModelV2 material)) {
            warnings.add("Material " + materialIndex + " is not a PBR metallic-roughness material; skipped.");
            return Optional.empty();
        }
        String materialName = Optional.ofNullable(material.getName()).orElse("material" + materialIndex);
        LitMaterial litMaterial = buildLitMaterial(material, outputDirectory, imageIndices, writtenImages, imageFileNames,
                materialIndex, warnings);
        String fileName = materialFileNames.allocate(sanitizeFileName(materialName, "material" + materialIndex),
                ".epymaterial", materialName, "Material", warnings);
        Path outputPath = outputDirectory.resolve(fileName);
        writeMaterialFile(outputPath, litMaterial);
        return Optional.of(outputPath);
    }

    private static LitMaterial buildLitMaterial(MaterialModelV2 material, Path outputDirectory,
            Map<ImageModel, Integer> imageIndices, Map<ImageModel, String> writtenImages, FileNameAllocator imageFileNames,
            int materialIndex, List<String> warnings) {
        LitMaterial litMaterial = new LitMaterial();
        float[] baseColorFactor = material.getBaseColorFactor();
        litMaterial.setBaseColor(baseColorFactor[0], baseColorFactor[1], baseColorFactor[2]);
        litMaterial.setMetallic(material.getMetallicFactor());
        litMaterial.setRoughness(material.getRoughnessFactor());
        litMaterial.setDoubleSided(material.isDoubleSided());
        applyAlphaMode(litMaterial, material);
        applyEmissiveStrength(litMaterial, material);
        applyTexture(litMaterial, "albedo", material.getBaseColorTexture(), outputDirectory, imageIndices, writtenImages, imageFileNames, materialIndex, warnings);
        applyTexture(litMaterial, "normalMap", material.getNormalTexture(), outputDirectory, imageIndices, writtenImages, imageFileNames, materialIndex, warnings);
        applyTexture(litMaterial, "metallicRoughnessMap", material.getMetallicRoughnessTexture(), outputDirectory, imageIndices, writtenImages, imageFileNames, materialIndex, warnings);
        applyTexture(litMaterial, "occlusionMap", material.getOcclusionTexture(), outputDirectory, imageIndices, writtenImages, imageFileNames, materialIndex, warnings);
        applyTexture(litMaterial, "emissiveMap", material.getEmissiveTexture(), outputDirectory, imageIndices, writtenImages, imageFileNames, materialIndex, warnings);
        return litMaterial;
    }

    private static void applyEmissiveStrength(LitMaterial litMaterial, MaterialModelV2 material) {
        if (material.getEmissiveTexture() == null) {
            return;
        }
        float[] emissiveFactor = material.getEmissiveFactor();
        litMaterial.setEmissiveStrength(Math.max(emissiveFactor[0], Math.max(emissiveFactor[1], emissiveFactor[2])));
    }

    private static void applyAlphaMode(LitMaterial litMaterial, MaterialModelV2 material) {
        MaterialModelV2.AlphaMode alphaMode = material.getAlphaMode();
        if (alphaMode == MaterialModelV2.AlphaMode.BLEND) {
            litMaterial.setTransparent(true);
        } else if (alphaMode == MaterialModelV2.AlphaMode.MASK) {
            litMaterial.setAlphaCutoff(material.getAlphaCutoff());
        }
    }

    private static void applyTexture(LitMaterial litMaterial, String fieldName, TextureModel texture, Path outputDirectory,
            Map<ImageModel, Integer> imageIndices, Map<ImageModel, String> writtenImages, FileNameAllocator imageFileNames,
            int materialIndex, List<String> warnings) {
        if (texture == null) {
            return;
        }
        ImageModel image = texture.getImageModel();
        if (image == null) {
            warnings.add("Material " + materialIndex + " texture " + fieldName + " has no image; skipped.");
            return;
        }
        litMaterial.setTexturePath(fieldName, resolveImagePath(image, outputDirectory, imageIndices, writtenImages, imageFileNames, warnings));
    }

    private static String resolveImagePath(ImageModel image, Path outputDirectory,
            Map<ImageModel, Integer> imageIndices, Map<ImageModel, String> writtenImages, FileNameAllocator imageFileNames,
            List<String> warnings) {
        String cachedPath = writtenImages.get(image);
        if (cachedPath != null) {
            return cachedPath;
        }
        int imageIndex = imageIndices.getOrDefault(image, 0);
        String path = isEmbeddedImage(image) ? writeEmbeddedImage(image, outputDirectory, imageIndex, imageFileNames, warnings)
                : image.getUri();
        writtenImages.put(image, path);
        return path;
    }

    private static boolean isEmbeddedImage(ImageModel image) {
        String uri = image.getUri();
        return uri == null || uri.startsWith("data:");
    }

    private static String writeEmbeddedImage(ImageModel image, Path outputDirectory, int imageIndex,
            FileNameAllocator imageFileNames, List<String> warnings) {
        byte[] imageBytes = readImageBytes(image);
        String imageName = imageBaseName(image, imageIndex);
        String extension = sniffImageExtension(imageBytes);
        String fileName = imageFileNames.allocate(sanitizeFileName(imageName, "image" + imageIndex), extension,
                imageName, "Image", warnings);
        writeBytes(outputDirectory.resolve(fileName), imageBytes);
        return fileName;
    }

    private static byte[] readImageBytes(ImageModel image) {
        ByteBuffer data = image.getImageData().duplicate();
        byte[] imageBytes = new byte[data.remaining()];
        data.get(imageBytes);
        return imageBytes;
    }

    private static String imageBaseName(ImageModel image, int imageIndex) {
        String name = image.getName();
        if (name != null && !name.isEmpty()) {
            return name;
        }
        String uri = image.getUri();
        if (uri != null && !uri.startsWith("data:")) {
            return uriStem(uri);
        }
        return "image" + imageIndex;
    }

    private static String uriStem(String uri) {
        String fileName = uri.contains("/") ? uri.substring(uri.lastIndexOf('/') + 1) : uri;
        int extensionSeparator = fileName.lastIndexOf('.');
        return extensionSeparator > 0 ? fileName.substring(0, extensionSeparator) : fileName;
    }

    private static String sniffImageExtension(byte[] imageBytes) {
        boolean isPng = imageBytes.length >= 4 && imageBytes[0] == (byte) 0x89 && imageBytes[1] == 0x50
                && imageBytes[2] == 0x4E && imageBytes[3] == 0x47;
        return isPng ? ".png" : ".jpg";
    }

    private static void writeBytes(Path path, byte[] bytes) {
        try {
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to write texture image to " + path + ": " + exception.getMessage(), exception);
        }
    }

    private static void writeMaterialFile(Path path, LitMaterial litMaterial) {
        try {
            Files.writeString(path, new MaterialJsonCodec().writeSingle(litMaterial));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to write .epymaterial to " + path + ": " + exception.getMessage(), exception);
        }
    }

    private static Optional<Path> buildPrefab(GltfModel model, Path source, Path outputDirectory, List<Path> meshFiles,
            List<Path> clipFiles, Map<MaterialModel, Path> materialsByModel, ComponentRegistry componentRegistry,
            List<String> warnings) {
        List<MeshNodeBinding> bindings = collectMeshNodeBindings(model, meshFiles, materialsByModel);
        if (bindings.isEmpty()) {
            return Optional.empty();
        }
        String stem = fileStem(source);
        GameObject root = new GameObject(stem);
        Transform3D rootTransform = new Transform3D();
        root.addComponent(rootTransform);
        Optional<Path> firstClip = clipFiles.isEmpty() ? Optional.empty() : Optional.of(clipFiles.get(0));
        for (MeshNodeBinding binding : bindings) {
            addMeshChild(rootTransform, binding, firstClip);
        }
        return Optional.of(writePrefab(root, stem, outputDirectory, componentRegistry, warnings));
    }

    private static List<MeshNodeBinding> collectMeshNodeBindings(GltfModel model, List<Path> meshFiles,
            Map<MaterialModel, Path> materialsByModel) {
        Map<MeshModel, Integer> meshIndices = buildMeshIndices(model);
        List<MeshNodeBinding> bindings = new ArrayList<>();
        for (NodeModel node : model.getNodeModels()) {
            for (MeshModel meshModel : node.getMeshModels()) {
                bindings.add(bindMeshNode(node, meshModel, meshIndices, meshFiles, materialsByModel));
            }
        }
        return bindings;
    }

    private static Map<MeshModel, Integer> buildMeshIndices(GltfModel model) {
        Map<MeshModel, Integer> meshIndices = new IdentityHashMap<>();
        List<MeshModel> meshModels = model.getMeshModels();
        for (int meshIndex = 0; meshIndex < meshModels.size(); meshIndex++) {
            meshIndices.put(meshModels.get(meshIndex), meshIndex);
        }
        return meshIndices;
    }

    private static MeshNodeBinding bindMeshNode(NodeModel node, MeshModel meshModel, Map<MeshModel, Integer> meshIndices,
            List<Path> meshFiles, Map<MaterialModel, Path> materialsByModel) {
        Path meshFile = meshFiles.get(meshIndices.get(meshModel));
        List<Optional<Path>> materialPaths = materialPathsForMesh(meshModel, materialsByModel);
        String nodeName = Optional.ofNullable(node.getName()).orElse(meshFile.getFileName().toString());
        boolean skinned = node.getSkinModel() != null;
        return new MeshNodeBinding(nodeName, meshFile, materialPaths, node.computeGlobalTransform(null), skinned);
    }

    private static List<Optional<Path>> materialPathsForMesh(MeshModel meshModel, Map<MaterialModel, Path> materialsByModel) {
        List<Optional<Path>> materialPaths = new ArrayList<>();
        for (MeshPrimitiveModel primitive : meshModel.getMeshPrimitiveModels()) {
            if (isTriangleMode(primitive.getMode())) {
                materialPaths.add(Optional.ofNullable(materialsByModel.get(primitive.getMaterialModel())));
            }
        }
        return materialPaths;
    }

    private static void addMeshChild(Transform3D rootTransform, MeshNodeBinding binding, Optional<Path> firstClip) {
        GameObject child = new GameObject(binding.nodeName());
        Transform3D transform = new Transform3D();
        applyWorldTransform(transform, binding.worldTransform());
        child.addComponent(transform);
        transform.setParent(rootTransform);
        MeshRenderer renderer = new MeshRenderer().setMeshPath(binding.meshFile().toString());
        renderer.setMaterials(buildMaterialPlaceholders(binding.materialPaths()));
        child.addComponent(renderer);
        if (binding.skinned() && firstClip.isPresent()) {
            child.addComponent(new Animator().setClipPath(firstClip.get().toString()));
        }
    }

    private static void applyWorldTransform(Transform3D transform, float[] worldMatrix) {
        Matrix4f matrix = new Matrix4f().set(worldMatrix);
        Vector3f translation = matrix.getTranslation(new Vector3f());
        Quaternionf rotation = matrix.getNormalizedRotation(new Quaternionf());
        Vector3f scale = matrix.getScale(new Vector3f());
        transform.setPosition(translation.x, translation.y, translation.z);
        transform.setRotation(rotation);
        transform.setScale(scale.x, scale.y, scale.z);
    }

    private static List<Material> buildMaterialPlaceholders(List<Optional<Path>> materialPaths) {
        List<Material> materials = new ArrayList<>();
        for (Optional<Path> materialPath : materialPaths) {
            LitMaterial material = new LitMaterial();
            materialPath.ifPresent(path -> material.setAssetPath(path.toString()));
            materials.add(material);
        }
        return materials;
    }

    private static Path writePrefab(GameObject root, String stem, Path outputDirectory,
            ComponentRegistry componentRegistry, List<String> warnings) {
        Path outputPath = outputDirectory.resolve(stem + ".epyprefab");
        try {
            new PrefabWriter(componentRegistry).write(root, outputPath);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to write .epyprefab to " + outputPath + ": " + exception.getMessage(), exception);
        }
        if (componentRegistry.entries().isEmpty()) {
            warnings.add("Component registry is empty; the prefab was written without components.");
        }
        return outputPath;
    }

    private static String fileStem(Path source) {
        String fileName = source.getFileName().toString();
        int extensionSeparator = fileName.lastIndexOf('.');
        return extensionSeparator > 0 ? fileName.substring(0, extensionSeparator) : fileName;
    }

    private static MeshData mergePrimitives(List<PrimitiveVertexData> primitives, String meshName, List<String> warnings) {
        MergePlan plan = MergePlan.of(primitives);
        warnIfMixedSkin(primitives, plan, meshName, warnings);
        MergeBuffers buffers = new MergeBuffers(plan);
        List<Submesh> submeshes = buffers.copyAll(primitives);
        return new MeshData(buffers.positions(), buffers.normals(), buffers.uvs(), new float[0],
                buffers.jointIndices(), buffers.jointWeights(), buffers.indices(), submeshes);
    }

    private static void warnIfMixedSkin(List<PrimitiveVertexData> primitives, MergePlan plan, String meshName, List<String> warnings) {
        if (plan.hasSkin()) {
            return;
        }
        boolean anyPrimitiveSkinned = primitives.stream().anyMatch(primitive -> primitive.jointIndices().length > 0);
        if (anyPrimitiveSkinned) {
            warnings.add("Mesh " + meshName + " has mixed skinned and rigid primitives, imported as static");
        }
    }

    private static Map<MaterialModel, MaterialUvHints> buildMaterialUvHints(GltfModel model, Path source, List<String> warnings) {
        Map<MaterialModel, MaterialUvHints> hints = new IdentityHashMap<>();
        Optional<GlTF> rawGltf = readRawGltf(source);
        if (rawGltf.isEmpty() || rawGltf.get().getMaterials() == null) {
            return hints;
        }
        List<de.javagl.jgltf.impl.v2.Material> rawMaterials = rawGltf.get().getMaterials();
        List<MaterialModel> materialModels = model.getMaterialModels();
        for (int index = 0; index < materialModels.size() && index < rawMaterials.size(); index++) {
            hints.put(materialModels.get(index), analyzeMaterial(rawMaterials.get(index), index, warnings));
        }
        return hints;
    }

    private static Optional<GlTF> readRawGltf(Path source) {
        try {
            GltfAsset asset = new GltfAssetReader().readWithoutReferences(source.toUri());
            return asset.getGltf() instanceof GlTF gltf ? Optional.of(gltf) : Optional.empty();
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read glTF material extensions from " + source + ": " + exception.getMessage(), exception);
        }
    }

    private static MaterialUvHints analyzeMaterial(de.javagl.jgltf.impl.v2.Material material, int materialIndex, List<String> warnings) {
        MaterialPbrMetallicRoughness pbr = material.getPbrMetallicRoughness();
        Optional<TextureInfo> baseColor = Optional.ofNullable(pbr == null ? null : pbr.getBaseColorTexture());
        Optional<UvTransform> baseColorTransform = baseColor.flatMap(GltfImporter::readTextureTransform);
        int baseColorTexCoord = baseColor.map(GltfImporter::resolveTexCoord).orElse(0);
        warnUvDivergence(material, baseColorTransform, baseColorTexCoord, materialIndex, warnings);
        return new MaterialUvHints(baseColorTexCoord, baseColorTransform);
    }

    private static void warnUvDivergence(de.javagl.jgltf.impl.v2.Material material, Optional<UvTransform> baseColorTransform,
            int baseColorTexCoord, int materialIndex, List<String> warnings) {
        List<TextureInfo> otherSlots = otherTextureSlots(material);
        List<UvTransform> otherTransforms = otherSlots.stream().flatMap(info -> readTextureTransform(info).stream()).toList();
        if (baseColorTransform.isPresent() && otherTransforms.stream().anyMatch(transform -> !transform.equals(baseColorTransform.get()))) {
            warnings.add("Material " + materialIndex + " has a KHR_texture_transform on a non-baseColor texture that differs"
                    + " from baseColor; only the baseColor transform was baked into the mesh UVs.");
        } else if (baseColorTransform.isEmpty() && !otherTransforms.isEmpty()) {
            warnings.add("Material " + materialIndex + " has KHR_texture_transform only on non-baseColor textures;"
                    + " none was baked because the engine uses a single UV stream.");
        }
        if (otherSlots.stream().anyMatch(info -> resolveTexCoord(info) != baseColorTexCoord)) {
            warnings.add("Material " + materialIndex + " uses different texCoord sets across textures; the engine has a single"
                    + " UV stream, so TEXCOORD_" + baseColorTexCoord + " is used for every texture.");
        }
    }

    private static List<TextureInfo> otherTextureSlots(de.javagl.jgltf.impl.v2.Material material) {
        List<TextureInfo> slots = new ArrayList<>();
        MaterialPbrMetallicRoughness pbr = material.getPbrMetallicRoughness();
        addTextureSlot(slots, pbr == null ? null : pbr.getMetallicRoughnessTexture());
        addTextureSlot(slots, material.getNormalTexture());
        addTextureSlot(slots, material.getOcclusionTexture());
        addTextureSlot(slots, material.getEmissiveTexture());
        return slots;
    }

    private static void addTextureSlot(List<TextureInfo> slots, TextureInfo info) {
        if (info != null) {
            slots.add(info);
        }
    }

    private static int resolveTexCoord(TextureInfo info) {
        Object node = extensionNode(info);
        if (node instanceof Map<?, ?> map && map.get("texCoord") instanceof Number number) {
            return number.intValue();
        }
        return info.getTexCoord() == null ? 0 : info.getTexCoord();
    }

    private static Optional<UvTransform> readTextureTransform(TextureInfo info) {
        if (!(extensionNode(info) instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        float[] offset = readVector2(map.get("offset"), 0.0f, 0.0f);
        float[] scale = readVector2(map.get("scale"), 1.0f, 1.0f);
        float rotation = readScalar(map.get("rotation"), 0.0f);
        return Optional.of(new UvTransform(offset[0], offset[1], scale[0], scale[1], rotation));
    }

    private static Object extensionNode(TextureInfo info) {
        Map<String, Object> extensions = info.getExtensions();
        return extensions == null ? null : extensions.get(TEXTURE_TRANSFORM);
    }

    private static float[] readVector2(Object node, float defaultX, float defaultY) {
        if (node instanceof List<?> list && list.size() >= 2
                && list.get(0) instanceof Number first && list.get(1) instanceof Number second) {
            return new float[] {first.floatValue(), second.floatValue()};
        }
        return new float[] {defaultX, defaultY};
    }

    private static float readScalar(Object node, float defaultValue) {
        return node instanceof Number number ? number.floatValue() : defaultValue;
    }

    @FunctionalInterface
    private interface IntElementReader {
        int read(int element, int component);
    }

    private record UvTransform(float offsetX, float offsetY, float scaleX, float scaleY, float rotation) {
        float[] apply(float[] uvs) {
            float cos = (float) Math.cos(rotation);
            float sin = (float) Math.sin(rotation);
            float[] result = new float[uvs.length];
            for (int index = 0; index + 1 < uvs.length; index += 2) {
                float u = uvs[index];
                float v = uvs[index + 1];
                result[index] = offsetX + scaleX * u * cos - scaleY * v * sin;
                result[index + 1] = offsetY + scaleX * u * sin + scaleY * v * cos;
            }
            return result;
        }
    }

    private record MaterialUvHints(int baseColorTexCoord, Optional<UvTransform> baseColorTransform) {
        static MaterialUvHints identity() {
            return new MaterialUvHints(0, Optional.empty());
        }
    }

    private record PrimitiveVertexData(float[] positions, float[] normals, float[] uvs,
                                        short[] jointIndices, float[] jointWeights, int[] indices) {
        int vertexCount() {
            return positions.length / 3;
        }
    }

    private record SkinAttributeData(short[] jointIndices, float[] jointWeights) {
        static SkinAttributeData empty() {
            return new SkinAttributeData(new short[0], new float[0]);
        }
    }

    private record SkeletonBuild(Skeleton skeleton, int[] remap) {
    }

    private record JointReference(SkinModel skin, int jointIndex) {
    }

    private record MaterialImport(List<Path> files, Map<MaterialModel, Path> byModel) {
    }

    private record MeshNodeBinding(String nodeName, Path meshFile, List<Optional<Path>> materialPaths,
                                   float[] worldTransform, boolean skinned) {
    }

    private static final class FileNameAllocator {

        private final Map<String, Integer> occurrenceCounts = new HashMap<>();
        private final Map<String, String> firstDisplayNames = new HashMap<>();

        String allocate(String baseName, String extension, String displayName, String kind, List<String> warnings) {
            String key = baseName + extension;
            int occurrence = occurrenceCounts.merge(key, 1, Integer::sum) - 1;
            if (occurrence == 0) {
                firstDisplayNames.put(key, displayName);
                return key;
            }
            String uniqueName = baseName + "_" + occurrence + extension;
            warnings.add(kind + " \"" + firstDisplayNames.get(key) + "\" and " + kind.toLowerCase() + " \"" + displayName
                    + "\" both resolve to \"" + key + "\"; the second was renamed to \"" + uniqueName + "\".");
            return uniqueName;
        }
    }

    private record MergePlan(int vertexTotal, int indexTotal, boolean hasUv, boolean hasSkin) {
        static MergePlan of(List<PrimitiveVertexData> primitives) {
            int vertexTotal = 0;
            int indexTotal = 0;
            boolean hasUv = !primitives.isEmpty();
            boolean hasSkin = !primitives.isEmpty();
            for (PrimitiveVertexData primitive : primitives) {
                vertexTotal += primitive.vertexCount();
                indexTotal += primitive.indices().length;
                hasUv = hasUv && primitive.uvs().length > 0;
                hasSkin = hasSkin && primitive.jointIndices().length > 0;
            }
            return new MergePlan(vertexTotal, indexTotal, hasUv, hasSkin);
        }
    }

    private static final class MergeBuffers {

        private final float[] positions;
        private final float[] normals;
        private final float[] uvs;
        private final short[] jointIndices;
        private final float[] jointWeights;
        private final int[] indices;
        private int vertexOffset;
        private int indexOffset;

        MergeBuffers(MergePlan plan) {
            positions = new float[plan.vertexTotal() * MeshData.POSITION_COMPONENTS];
            normals = new float[plan.vertexTotal() * MeshData.NORMAL_COMPONENTS];
            uvs = plan.hasUv() ? new float[plan.vertexTotal() * MeshData.UV_COMPONENTS] : new float[0];
            jointIndices = plan.hasSkin() ? new short[plan.vertexTotal() * MeshData.INFLUENCES_PER_VERTEX] : new short[0];
            jointWeights = plan.hasSkin() ? new float[plan.vertexTotal() * MeshData.INFLUENCES_PER_VERTEX] : new float[0];
            indices = new int[plan.indexTotal()];
        }

        List<Submesh> copyAll(List<PrimitiveVertexData> primitives) {
            List<Submesh> submeshes = new ArrayList<>();
            for (int primitiveIndex = 0; primitiveIndex < primitives.size(); primitiveIndex++) {
                submeshes.add(copyOne(primitives.get(primitiveIndex), primitiveIndex));
            }
            return submeshes;
        }

        private Submesh copyOne(PrimitiveVertexData primitive, int primitiveIndex) {
            int vertexCount = primitive.vertexCount();
            System.arraycopy(primitive.positions(), 0, positions, vertexOffset * MeshData.POSITION_COMPONENTS, primitive.positions().length);
            System.arraycopy(primitive.normals(), 0, normals, vertexOffset * MeshData.NORMAL_COMPONENTS, primitive.normals().length);
            copyUvs(primitive, vertexCount);
            copySkin(primitive, vertexCount);
            Submesh submesh = copyIndices(primitive, primitiveIndex);
            vertexOffset += vertexCount;
            indexOffset += primitive.indices().length;
            return submesh;
        }

        private void copyUvs(PrimitiveVertexData primitive, int vertexCount) {
            if (uvs.length == 0) {
                return;
            }
            System.arraycopy(primitive.uvs(), 0, uvs, vertexOffset * MeshData.UV_COMPONENTS, vertexCount * MeshData.UV_COMPONENTS);
        }

        private void copySkin(PrimitiveVertexData primitive, int vertexCount) {
            if (jointIndices.length == 0) {
                return;
            }
            int influenceBase = vertexOffset * MeshData.INFLUENCES_PER_VERTEX;
            int influenceCount = vertexCount * MeshData.INFLUENCES_PER_VERTEX;
            System.arraycopy(primitive.jointIndices(), 0, jointIndices, influenceBase, influenceCount);
            System.arraycopy(primitive.jointWeights(), 0, jointWeights, influenceBase, influenceCount);
        }

        private Submesh copyIndices(PrimitiveVertexData primitive, int primitiveIndex) {
            int[] localIndices = primitive.indices();
            for (int index = 0; index < localIndices.length; index++) {
                indices[indexOffset + index] = localIndices[index] + vertexOffset;
            }
            return new Submesh(indexOffset, localIndices.length, primitiveIndex);
        }

        float[] positions() {
            return positions;
        }

        float[] normals() {
            return normals;
        }

        float[] uvs() {
            return uvs;
        }

        short[] jointIndices() {
            return jointIndices;
        }

        float[] jointWeights() {
            return jointWeights;
        }

        int[] indices() {
            return indices;
        }
    }
}
