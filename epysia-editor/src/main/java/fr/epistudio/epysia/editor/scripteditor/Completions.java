package fr.epistudio.epysia.editor.scripteditor;

import java.util.List;

public interface Completions {

    CompletionContext contextAt(String lineText, int cursorIndex);

    boolean shouldTrigger(CompletionContext context);

    List<CompletionSymbol> candidates(CompletionContext context, String fullText, ImportStyle style);
}
