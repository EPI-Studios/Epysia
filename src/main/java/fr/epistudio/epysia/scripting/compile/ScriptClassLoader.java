package fr.epistudio.epysia.scripting.compile;

import java.net.URL;
import java.net.URLClassLoader;

public final class ScriptClassLoader extends URLClassLoader {

    public ScriptClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }
}
