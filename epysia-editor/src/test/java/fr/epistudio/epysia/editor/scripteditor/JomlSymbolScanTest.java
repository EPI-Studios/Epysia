package fr.epistudio.epysia.editor.scripteditor;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JomlSymbolScanTest {

    @Test
    void theWholeMathPackageIsOfferedToScripts() {
        List<String> names = ClasspathTypeScanner.typesUnder(Vector3f.class, "org.joml").stream()
                .map(Class::getSimpleName)
                .toList();

        assertTrue(names.contains("Vector3fc"), "the read only views are what engine signatures use");
        assertTrue(names.contains("Quaternionfc"), "a script reading a rotation needs this one");
        assertTrue(names.size() > 20, "the whole package must be indexed, not a handful: " + names.size());
    }
}
