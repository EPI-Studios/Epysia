package fr.epistudio.epysia.lang.python;

public final class PythonHost {

    private PythonHost() {
    }

    public static float toFloat(double value) {
        return (float) value;
    }

    public static int toInt(double value) {
        return (int) value;
    }
}
