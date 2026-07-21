package fr.epistudio.epysia.render.shader;

public final class ShaderComments {

    private ShaderComments() {
    }

    public static String mask(String source) {
        char[] characters = source.toCharArray();
        int index = 0;
        while (index < characters.length - 1) {
            if (characters[index] == '/' && characters[index + 1] == '/') {
                index = maskLineComment(characters, index);
            } else if (characters[index] == '/' && characters[index + 1] == '*') {
                index = maskBlockComment(characters, index);
            } else {
                index++;
            }
        }
        return new String(characters);
    }

    private static int maskLineComment(char[] characters, int start) {
        int index = start;
        while (index < characters.length && characters[index] != '\n') {
            characters[index] = ' ';
            index++;
        }
        return index;
    }

    private static int maskBlockComment(char[] characters, int start) {
        characters[start] = ' ';
        characters[start + 1] = ' ';
        int index = start + 2;
        while (index < characters.length - 1 && !(characters[index] == '*' && characters[index + 1] == '/')) {
            characters[index] = characters[index] == '\n' ? '\n' : ' ';
            index++;
        }
        return maskBlockTerminator(characters, index);
    }

    private static int maskBlockTerminator(char[] characters, int index) {
        if (index < characters.length - 1) {
            characters[index] = ' ';
            characters[index + 1] = ' ';
            return index + 2;
        }
        return characters.length;
    }
}
