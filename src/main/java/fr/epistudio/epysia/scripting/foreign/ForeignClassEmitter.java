package fr.epistudio.epysia.scripting.foreign;

import fr.epistudio.epysia.components.IComponent;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Locale;

public final class ForeignClassEmitter {

    private static final String PACKAGE = "fr.epistudio.epysia.scripting.foreign.generated";
    private static final ClassDesc COMPONENT = ClassDesc.of(ForeignComponent.class.getName());
    private static final ClassDesc BOOTSTRAP = ClassDesc.of(ForeignComponentBootstrap.class.getName());
    private static final ClassDesc TYPE_DESC = ClassDesc.of(ForeignComponentType.class.getName());
    private static final ClassDesc INSTANCE_DESC = ClassDesc.of(ForeignInstance.class.getName());
    private static final ClassDesc STRING = ClassDesc.of(String.class.getName());

    private final ForeignClassLoader loader;

    public ForeignClassEmitter(ForeignClassLoader loader) {
        this.loader = loader;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends IComponent> define(String key, ForeignComponentType type) {
        ForeignComponentBootstrap.register(key, type);
        String binaryName = PACKAGE + "." + sanitized(key);
        ClassDesc self = ClassDesc.of(binaryName);
        byte[] bytes = ClassFile.of().build(self, builder -> builder
                .withSuperclass(COMPONENT)
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
                .withMethodBody("<init>", MethodTypeDesc.of(ClassDesc.ofDescriptor("V")),
                        ClassFile.ACC_PUBLIC, code -> code
                                .aload(0)
                                .ldc(key)
                                .invokestatic(BOOTSTRAP, "typeOf", MethodTypeDesc.of(TYPE_DESC, STRING))
                                .ldc(key)
                                .invokestatic(BOOTSTRAP, "instanceOf", MethodTypeDesc.of(INSTANCE_DESC, STRING))
                                .invokespecial(COMPONENT, "<init>",
                                        MethodTypeDesc.of(ClassDesc.ofDescriptor("V"), TYPE_DESC, INSTANCE_DESC))
                                .return_()));
        return (Class<? extends IComponent>) loader.define(binaryName, bytes);
    }

    private static String sanitized(String key) {
        StringBuilder name = new StringBuilder(key.length() + 1);
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            name.append(Character.isJavaIdentifierPart(character) ? character : '_');
        }
        String cleaned = name.toString();
        return cleaned.isEmpty() || Character.isDigit(cleaned.charAt(0))
                ? "Foreign" + cleaned.toUpperCase(Locale.ROOT)
                : cleaned;
    }
}
