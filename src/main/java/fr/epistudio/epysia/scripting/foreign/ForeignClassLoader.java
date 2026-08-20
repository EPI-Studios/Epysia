package fr.epistudio.epysia.scripting.foreign;

public final class ForeignClassLoader extends ClassLoader {

    public ForeignClassLoader(ClassLoader parent) {
        super("epysia-foreign", parent);
    }

    public Class<?> define(String binaryName, byte[] bytes) {
        return defineClass(binaryName, bytes, 0, bytes.length);
    }
}
