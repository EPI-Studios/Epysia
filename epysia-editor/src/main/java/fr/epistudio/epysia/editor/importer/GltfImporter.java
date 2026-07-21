package fr.epistudio.epysia.editor.importer;

import de.javagl.jgltf.model.AccessorByteData;
import de.javagl.jgltf.model.AccessorData;
import de.javagl.jgltf.model.AccessorFloatData;
import de.javagl.jgltf.model.AccessorIntData;
import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.AccessorShortData;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SkinModel;
import de.javagl.jgltf.model.io.GltfModelReader;
import fr.epistudio.epysia.animation.Joint;
import fr.epistudio.epysia.animation.Skeleton;
import fr.epistudio.epysia.assets.epymesh.EpyMeshWriter;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.mesh.MeshData;
import fr.epistudio.epysia.render.mesh.Submesh;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GltfImporter {

    private GltfImporter() {
    }

    public static GltfImportResult importFile(Path source, Path outputDirectory) {
        GltfModel model = readModel(source);
        List<Path> meshFiles = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<MeshModel> meshModels = model.getMeshModels();
        for (int meshIndex = 0; meshIndex < meshModels.size(); meshIndex++) {
            meshFiles.add(importMesh(model, meshModels.get(meshIndex), meshIndex, outputDirectory, warnings));
        }
        return new GltfImportResult(meshFiles, List.of(), warnings);
    }

    private static GltfModel readModel(Path source) {
        try {
            return new GltfModelReader().read(source);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read glTF file " + source + ": " + exception.getMessage(), exception);
        }
    }

    private static Path importMesh(GltfModel model, MeshModel meshModel, int meshIndex, Path outputDirectory, List<String> warnings) {
        String meshName = meshName(meshModel, meshIndex);
        Optional<SkinModel> skinModel = findSkinForMesh(model, meshModel);
        Optional<SkeletonBuild> skeletonBuild = skinModel.map(GltfImporter::buildSkeleton);
        List<PrimitiveVertexData> primitives = readPrimitives(meshModel, meshName, skeletonBuild, warnings);
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

    private static List<PrimitiveVertexData> readPrimitives(MeshModel meshModel, String meshName,
            Optional<SkeletonBuild> skeletonBuild, List<String> warnings) {
        List<PrimitiveVertexData> primitives = new ArrayList<>();
        List<MeshPrimitiveModel> primitiveModels = meshModel.getMeshPrimitiveModels();
        for (int primitiveIndex = 0; primitiveIndex < primitiveModels.size(); primitiveIndex++) {
            primitives.add(readPrimitive(primitiveModels.get(primitiveIndex), meshName, primitiveIndex, skeletonBuild, warnings));
        }
        return primitives;
    }

    private static PrimitiveVertexData readPrimitive(MeshPrimitiveModel primitive, String meshName, int primitiveIndex,
            Optional<SkeletonBuild> skeletonBuild, List<String> warnings) {
        Map<String, AccessorModel> attributes = primitive.getAttributes();
        float[] positions = readFloats(requireAttribute(attributes, "POSITION", meshName, primitiveIndex), 3);
        float[] normals = readNormals(attributes, meshName, primitiveIndex);
        float[] uvs = readUvs(attributes, meshName, primitiveIndex, warnings);
        int[] indices = readIndices(primitive, meshName, primitiveIndex);
        warnUnsupportedFeatures(primitive, attributes, meshName, primitiveIndex, warnings);
        SkinAttributeData skinData = readSkinAttributes(attributes, positions.length / 3, skeletonBuild, meshName, primitiveIndex, warnings);
        return new PrimitiveVertexData(positions, normals, uvs, skinData.jointIndices(), skinData.jointWeights(), indices);
    }

    private static float[] readNormals(Map<String, AccessorModel> attributes, String meshName, int primitiveIndex) {
        AccessorModel accessor = attributes.get("NORMAL");
        if (accessor == null) {
            throw new EpysiaException("Mesh " + meshName + " primitive " + primitiveIndex + " has no NORMAL accessor.");
        }
        return readFloats(accessor, 3);
    }

    private static float[] readUvs(Map<String, AccessorModel> attributes, String meshName, int primitiveIndex, List<String> warnings) {
        AccessorModel accessor = attributes.get("TEXCOORD_0");
        if (accessor == null) {
            return new float[0];
        }
        if (attributes.containsKey("TEXCOORD_1")) {
            warnings.add("Mesh " + meshName + " primitive " + primitiveIndex + " has extra UV sets beyond TEXCOORD_0; ignored.");
        }
        return readFloats(accessor, 2);
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

    @FunctionalInterface
    private interface IntElementReader {
        int read(int element, int component);
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
